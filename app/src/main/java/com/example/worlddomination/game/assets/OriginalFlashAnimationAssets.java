package com.example.worlddomination.game.assets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.Base64;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.zip.GZIPInputStream;

/** Loads original Flash timelines and caches their shapes, prioritizing setup portraits. */
public final class OriginalFlashAnimationAssets implements Disposable {
    /** Body artwork nested in the source Flash rocket movie clip. */
    public static final int ROCKET_BODY_SHAPE_ID = 792;
    /** Smoke puff nested in the source Flash trail movie clip. */
    public static final int ROCKET_TRAIL_SHAPE_ID = 4936;

    private static final String SCENE_PATH = "original/runtime/scene.json.gz";
    private static final String SHAPE_PATH = "original/runtime/shapes/";
    private static final String WEBP_PREFIX = "data:image/webp;base64,";
    private static final String[] REQUIRED_LINKAGES = {
            "faces",
            "explosion_warhead_1", "explosion_warhead_2", "explosion_warhead_3",
            "explosion_warhead_4", "explosion_bioload_1", "explosion_bioload_2",
            "explosion_bioload_3", "explosion_bioload_4", "explosion001", "explosion002",
            "explosion003", "satellite",
            "rocket", "trail",
            "submarine",
            "ship_plane",
            "boat001", "soldier", "soldier001_shoot", "chinook", "chinook_shadow", "apache",
            "apache_shadow", "apache_tint", "marine", "soldier001_tint",
            "soldier001_shoot_tint", "suicide_bomber", "suicide_bomber_tint",
            "tank_noshade", "tankNoShade_shadow", "tankBase", "tankTop_blue", "tankTop_red",
            "tankTop_orange", "tankTop_yellow", "tankTop_green"
    };

    private final Object loadLock = new Object();
    private volatile Map<String, FlashAnimationTimeline> timelines = Collections.emptyMap();
    private volatile Map<Integer, FlashAnimationTimeline> nestedTimelines =
            Collections.emptyMap();
    private volatile Map<Integer, TextureRegion> shapeRegions = Collections.emptyMap();
    private volatile Map<Integer, TextureRegion> setupPortraitRegions = Collections.emptyMap();
    private volatile RuntimeException loadFailure;
    private volatile boolean disposed;
    private volatile boolean loaded;
    private volatile boolean setupPortraitsLoaded;
    private boolean sourceReady;
    private boolean setupPortraitSourceReady;
    private PixmapPacker pendingPacker;
    private PixmapPacker pendingSetupPortraitPacker;
    private TextureAtlas atlas;
    private TextureAtlas setupPortraitAtlas;
    private final Thread loaderThread;

    public OriginalFlashAnimationAssets() {
        loaderThread = new Thread(this::loadInBackground, "wd2-flash-assets");
        loaderThread.setDaemon(true);
        loaderThread.setPriority(Thread.MIN_PRIORITY);
        loaderThread.start();
    }

    /** Performs the final atlas texture upload on the GL thread. */
    public boolean update() {
        if (disposed) {
            return false;
        }
        RuntimeException failure = loadFailure;
        if (failure != null) {
            throw failure;
        }
        uploadSetupPortraitAtlas();
        if (loaded) {
            return true;
        }

        PixmapPacker packer;
        synchronized (loadLock) {
            if (!sourceReady || pendingPacker == null || disposed) {
                return false;
            }
            packer = pendingPacker;
            pendingPacker = null;
            sourceReady = false;
        }

        TextureAtlas generatedAtlas;
        try {
            generatedAtlas = packer.generateTextureAtlas(TextureFilter.Linear,
                    TextureFilter.Linear, false);
        } finally {
            packer.dispose();
        }

        Map<Integer, TextureRegion> generatedRegions = new LinkedHashMap<>();
        Set<Integer> battleShapeIds = collectShapeIds(timelines, nestedTimelines);
        battleShapeIds.removeAll(collectSetupPortraitShapeIds(timelines));
        for (int shapeId : battleShapeIds) {
            TextureRegion region = generatedAtlas.findRegion(regionName(shapeId));
            if (region == null) {
                generatedAtlas.dispose();
                throw new IllegalStateException("Missing atlas region for Flash shape "
                        + shapeId);
            }
            generatedRegions.put(shapeId, region);
        }

        synchronized (loadLock) {
            if (disposed) {
                generatedAtlas.dispose();
                return false;
            }
            atlas = generatedAtlas;
            shapeRegions = Collections.unmodifiableMap(generatedRegions);
            loaded = true;
        }
        return true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** True when the small setup-only portrait atlas is ready for rendering. */
    public boolean areSetupPortraitsLoaded() {
        return setupPortraitsLoaded;
    }

    public FlashAnimationTimeline getTimeline(String linkage) {
        return timelines.get(linkage);
    }

    /** Returns a nested source movie clip by its Flash character ID. */
    public FlashAnimationTimeline getTimelineBySymbolId(int symbolId) {
        return nestedTimelines.get(symbolId);
    }

    public TextureRegion getShape(int shapeId) {
        TextureRegion portrait = setupPortraitRegions.get(shapeId);
        return portrait != null ? portrait : shapeRegions.get(shapeId);
    }

    private void loadInBackground() {
        PixmapPacker portraitPacker = new PixmapPacker(1024, 1024,
                Pixmap.Format.RGBA8888, 2, true);
        PixmapPacker packer = null;
        boolean portraitTransferred = false;
        boolean battleTransferred = false;
        try {
            JsonValue scene = new JsonReader().parse(readCompressedUtf8(SCENE_PATH));
            Map<String, FlashAnimationTimeline> loadedTimelines = loadTimelines(scene);
            Map<Integer, FlashAnimationTimeline> loadedNestedTimelines = new LinkedHashMap<>();
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("satellite")));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("submarine")));
            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                timelines = Collections.unmodifiableMap(loadedTimelines);
                nestedTimelines = Collections.unmodifiableMap(loadedNestedTimelines);
            }

            Set<Integer> portraitShapeIds = collectSetupPortraitShapeIds(loadedTimelines);
            packShapes(portraitPacker, portraitShapeIds);
            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                pendingSetupPortraitPacker = portraitPacker;
                setupPortraitSourceReady = true;
                portraitTransferred = true;
            }
            Gdx.graphics.requestRendering();

            packer = new PixmapPacker(1024, 1024,
                    Pixmap.Format.RGBA8888, 2, true);
            Set<Integer> battleShapeIds = collectShapeIds(loadedTimelines,
                    loadedNestedTimelines);
            battleShapeIds.removeAll(portraitShapeIds);
            packShapes(packer, battleShapeIds);

            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                pendingPacker = packer;
                sourceReady = true;
                battleTransferred = true;
            }
        } catch (RuntimeException error) {
            synchronized (loadLock) {
                if (!disposed) {
                    loadFailure = error;
                }
            }
        } finally {
            if (!portraitTransferred) {
                portraitPacker.dispose();
            }
            if (packer != null && !battleTransferred) {
                packer.dispose();
            }
        }
    }

    private void uploadSetupPortraitAtlas() {
        PixmapPacker packer;
        synchronized (loadLock) {
            if (!setupPortraitSourceReady || pendingSetupPortraitPacker == null || disposed) {
                return;
            }
            packer = pendingSetupPortraitPacker;
            pendingSetupPortraitPacker = null;
            setupPortraitSourceReady = false;
        }

        TextureAtlas generatedAtlas;
        try {
            generatedAtlas = packer.generateTextureAtlas(TextureFilter.Linear,
                    TextureFilter.Linear, false);
        } finally {
            packer.dispose();
        }

        Map<Integer, TextureRegion> generatedRegions = new LinkedHashMap<>();
        for (int shapeId : collectSetupPortraitShapeIds(timelines)) {
            TextureRegion region = generatedAtlas.findRegion(regionName(shapeId));
            if (region == null) {
                generatedAtlas.dispose();
                throw new IllegalStateException("Missing setup portrait atlas region for Flash shape "
                        + shapeId);
            }
            generatedRegions.put(shapeId, region);
        }

        synchronized (loadLock) {
            if (disposed) {
                generatedAtlas.dispose();
                return;
            }
            setupPortraitAtlas = generatedAtlas;
            setupPortraitRegions = Collections.unmodifiableMap(generatedRegions);
            setupPortraitsLoaded = true;
        }
    }

    private void packShapes(PixmapPacker packer, Set<Integer> shapeIds) {
        for (int shapeId : shapeIds) {
            if (disposed) {
                return;
            }
            Pixmap pixmap = decodeShape(shapeId);
            try {
                packer.pack(regionName(shapeId), pixmap);
            } finally {
                pixmap.dispose();
            }
        }
    }

    private static Set<Integer> collectSetupPortraitShapeIds(
            Map<String, FlashAnimationTimeline> timelines) {
        FlashAnimationTimeline faces = timelines.get("faces");
        Map<String, FlashAnimationTimeline> setupTimelines = new LinkedHashMap<>();
        if (faces != null) {
            setupTimelines.put("faces", faces);
        }
        return collectShapeIds(setupTimelines, false);
    }

    private static Set<Integer> collectShapeIds(
            Map<String, FlashAnimationTimeline> timelines) {
        return collectShapeIds(timelines, true);
    }

    private static Set<Integer> collectShapeIds(
            Map<String, FlashAnimationTimeline> timelines, boolean includeRocketTrail) {
        return collectShapeIds(timelines, Collections.emptyMap(), includeRocketTrail);
    }

    private static Set<Integer> collectShapeIds(
            Map<String, FlashAnimationTimeline> timelines,
            Map<Integer, FlashAnimationTimeline> nestedTimelines) {
        return collectShapeIds(timelines, nestedTimelines, true);
    }

    private static Set<Integer> collectShapeIds(
            Map<String, FlashAnimationTimeline> timelines,
            Map<Integer, FlashAnimationTimeline> nestedTimelines,
            boolean includeRocketTrail) {
        return collectShapeIds(timelines, nestedTimelines, includeRocketTrail,
                OriginalFlashAnimationAssets::hasPackagedShape);
    }

    static Set<Integer> collectShapeIds(
            Map<String, FlashAnimationTimeline> timelines,
            Map<Integer, FlashAnimationTimeline> nestedTimelines,
            IntPredicate hasShapeAsset) {
        return collectShapeIds(timelines, nestedTimelines, true, hasShapeAsset);
    }

    private static Set<Integer> collectShapeIds(
            Map<String, FlashAnimationTimeline> timelines,
            Map<Integer, FlashAnimationTimeline> nestedTimelines,
            boolean includeRocketTrail, IntPredicate hasShapeAsset) {
        Set<Integer> shapeIds = new LinkedHashSet<>();
        if (includeRocketTrail) {
            shapeIds.add(ROCKET_TRAIL_SHAPE_ID);
        }
        for (FlashAnimationTimeline timeline : timelines.values()) {
            addShapeIds(shapeIds, timeline, hasShapeAsset);
        }
        for (FlashAnimationTimeline timeline : nestedTimelines.values()) {
            addShapeIds(shapeIds, timeline, hasShapeAsset);
        }
        return shapeIds;
    }

    private static void addShapeIds(Set<Integer> shapeIds,
                                    FlashAnimationTimeline timeline,
                                    IntPredicate hasShapeAsset) {
        for (int i = 0; i < timeline.getFrameCount(); i++) {
            FlashAnimationTimeline.Frame frame = timeline.getFrame(i);
            if (frame != null) {
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    if (hasShapeAsset.test(layer.shapeId)) {
                        shapeIds.add(layer.shapeId);
                    }
                }
            }
        }
    }

    private static boolean hasPackagedShape(int shapeId) {
        return Gdx.files.internal(SHAPE_PATH + shapeId + ".svgz").exists();
    }

    private static Map<String, FlashAnimationTimeline> loadTimelines(JsonValue scene) {
        float frameRate = scene.getFloat("fps");
        JsonValue symbols = scene.get("symbols");
        if (symbols == null || !symbols.isArray()) {
            throw new IllegalStateException("Original Flash scene has no symbols array");
        }

        Map<String, FlashAnimationTimeline> result = new LinkedHashMap<>();
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            String linkage = symbol.getString("linkage", "");
            if (isRequiredLinkage(linkage)) {
                result.put(linkage, FlashAnimationTimeline.fromSymbol(symbol, frameRate));
            }
        }
        for (String linkage : REQUIRED_LINKAGES) {
            if (!result.containsKey(linkage)) {
                throw new IllegalStateException("Missing original Flash animation " + linkage);
            }
        }
        return result;
    }

    static Map<Integer, FlashAnimationTimeline> loadNestedTimelines(
            JsonValue scene, FlashAnimationTimeline rootTimeline) {
        if (rootTimeline == null) {
            throw new IllegalStateException("Original Flash root timeline is missing");
        }
        JsonValue symbols = scene.get("symbols");
        Map<Integer, JsonValue> symbolDefinitions = new LinkedHashMap<>();
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            symbolDefinitions.put(symbol.getInt("id"), symbol);
        }

        Map<Integer, FlashAnimationTimeline> result = new LinkedHashMap<>();
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> pending = new ArrayDeque<>();
        enqueueChildSymbols(rootTimeline, pending);
        float frameRate = scene.getFloat("fps");
        while (!pending.isEmpty()) {
            int symbolId = pending.removeFirst();
            if (!visited.add(symbolId)) {
                continue;
            }
            JsonValue definition = symbolDefinitions.get(symbolId);
            JsonValue sourceTimeline = definition == null ? null : definition.get("timeline");
            JsonValue frames = sourceTimeline == null ? null : sourceTimeline.get("frames");
            if (frames == null || !frames.isArray()) {
                continue;
            }
            FlashAnimationTimeline nested = FlashAnimationTimeline.fromSymbol(
                    definition, frameRate);
            result.put(symbolId, nested);
            enqueueChildSymbols(nested, pending);
        }
        return result;
    }

    private static void enqueueChildSymbols(FlashAnimationTimeline timeline,
                                            Deque<Integer> pending) {
        for (int frameIndex = 0; frameIndex < timeline.getFrameCount(); frameIndex++) {
            FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
            if (frame == null) {
                continue;
            }
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                pending.addLast(layer.shapeId);
            }
        }
    }

    private static Pixmap decodeShape(int shapeId) {
        String svg = readCompressedUtf8(SHAPE_PATH + shapeId + ".svgz");
        int dataStart = svg.indexOf(WEBP_PREFIX);
        if (dataStart < 0) {
            return decodeVectorShape(svg, shapeId);
        }
        dataStart += WEBP_PREFIX.length();
        int dataEnd = svg.indexOf('"', dataStart);
        if (dataEnd < 0) {
            throw new IllegalStateException("Flash shape has a malformed WebP URI: " + shapeId);
        }

        byte[] webp = Base64.decode(svg.substring(dataStart, dataEnd), Base64.NO_WRAP);
        Bitmap bitmap = BitmapFactory.decodeByteArray(webp, 0, webp.length);
        if (bitmap == null) {
            throw new IllegalStateException("Android could not decode Flash shape " + shapeId);
        }
        try {
            return copyBitmapToPixmap(bitmap);
        } finally {
            bitmap.recycle();
        }
    }

    private static Pixmap decodeVectorShape(String source, int shapeId) {
        final SVG svg;
        try {
            svg = SVG.getFromString(source);
        } catch (SVGParseException error) {
            throw new IllegalStateException("Could not parse original Flash vector shape "
                    + shapeId, error);
        }

        float documentWidth = svg.getDocumentWidth();
        float documentHeight = svg.getDocumentHeight();
        if (documentWidth <= 0f || documentHeight <= 0f
                || Float.isNaN(documentWidth) || Float.isNaN(documentHeight)
                || Float.isInfinite(documentWidth) || Float.isInfinite(documentHeight)) {
            throw new IllegalStateException("Original Flash vector shape has invalid dimensions: "
                    + shapeId);
        }

        int width = Math.max(1, (int) Math.ceil(documentWidth));
        int height = Math.max(1, (int) Math.ceil(documentHeight));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            svg.renderToCanvas(new Canvas(bitmap));
            return copyBitmapToPixmap(bitmap);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Could not rasterize original Flash vector shape "
                    + shapeId, error);
        } finally {
            bitmap.recycle();
        }
    }

    private static Pixmap copyBitmapToPixmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        int[] argbPixels = new int[width * height];
        bitmap.getPixels(argbPixels, 0, width, 0, 0, width, height);
        ByteBuffer rgbaPixels = pixmap.getPixels();
        for (int argb : argbPixels) {
            rgbaPixels.put((byte) (argb >>> 16));
            rgbaPixels.put((byte) (argb >>> 8));
            rgbaPixels.put((byte) argb);
            rgbaPixels.put((byte) (argb >>> 24));
        }
        rgbaPixels.position(0);
        return pixmap;
    }

    private static String readCompressedUtf8(String path) {
        String unpackedPath = path.substring(0, path.length() - 3);
        FileHandle unpackedAsset = Gdx.files.internal(unpackedPath);
        if (unpackedAsset.exists()) {
            return unpackedAsset.readString("UTF-8");
        }
        try (InputStream asset = Gdx.files.internal(path).read();
             InputStream gzip = new GZIPInputStream(asset);
             Reader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            char[] chars = new char[8192];
            StringBuilder text = new StringBuilder();
            int count;
            while ((count = reader.read(chars)) != -1) {
                text.append(chars, 0, count);
            }
            return text.toString();
        } catch (IOException error) {
            throw new IllegalStateException("Could not read original Flash asset " + path, error);
        }
    }

    private static boolean isRequiredLinkage(String linkage) {
        for (String required : REQUIRED_LINKAGES) {
            if (required.equals(linkage)) {
                return true;
            }
        }
        return false;
    }

    private static String regionName(int shapeId) {
        // PixmapPacker interprets a trailing _<number> as an atlas index by default.
        return "flashshape" + shapeId;
    }

    @Override
    public void dispose() {
        synchronized (loadLock) {
            disposed = true;
            if (pendingPacker != null) {
                pendingPacker.dispose();
                pendingPacker = null;
            }
            if (pendingSetupPortraitPacker != null) {
                pendingSetupPortraitPacker.dispose();
                pendingSetupPortraitPacker = null;
            }
            if (atlas != null) {
                atlas.dispose();
                atlas = null;
            }
            if (setupPortraitAtlas != null) {
                setupPortraitAtlas.dispose();
                setupPortraitAtlas = null;
            }
            shapeRegions = Collections.emptyMap();
            setupPortraitRegions = Collections.emptyMap();
            nestedTimelines = Collections.emptyMap();
            setupPortraitsLoaded = false;
            timelines = Collections.emptyMap();
        }
        loaderThread.interrupt();
    }
}
