package com.example.worlddomination.game.assets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Loads original combat timelines in the background and caches their WebP shapes in an atlas. */
public final class OriginalFlashAnimationAssets implements Disposable {
    /** Body artwork nested in the source Flash rocket movie clip. */
    public static final int ROCKET_BODY_SHAPE_ID = 792;
    /** Smoke puff nested in the source Flash trail movie clip. */
    public static final int ROCKET_TRAIL_SHAPE_ID = 4936;

    private static final String SCENE_PATH = "original/runtime/scene.json.gz";
    private static final String SHAPE_PATH = "original/runtime/shapes/";
    private static final String WEBP_PREFIX = "data:image/webp;base64,";
    private static final String[] REQUIRED_LINKAGES = {
            "explosion_warhead_1", "explosion_warhead_2", "explosion_warhead_3",
            "explosion_warhead_4", "explosion_bioload_1", "explosion_bioload_2",
            "explosion_bioload_3", "explosion_bioload_4", "explosion001", "explosion002",
            "explosion003",
            "rocket", "trail",
            "boat001", "soldier", "soldier001_shoot", "chinook", "chinook_shadow", "apache",
            "apache_shadow", "apache_tint", "marine", "soldier001_tint",
            "soldier001_shoot_tint", "suicide_bomber", "suicide_bomber_tint",
            "tank_noshade", "tankNoShade_shadow", "tankBase", "tankTop_blue", "tankTop_red",
            "tankTop_orange", "tankTop_yellow", "tankTop_green"
    };

    private final Object loadLock = new Object();
    private volatile Map<String, FlashAnimationTimeline> timelines = Collections.emptyMap();
    private volatile Map<Integer, TextureRegion> shapeRegions = Collections.emptyMap();
    private volatile RuntimeException loadFailure;
    private volatile boolean disposed;
    private volatile boolean loaded;
    private boolean sourceReady;
    private PixmapPacker pendingPacker;
    private TextureAtlas atlas;
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
        for (int shapeId : collectShapeIds(timelines)) {
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

    public FlashAnimationTimeline getTimeline(String linkage) {
        return timelines.get(linkage);
    }

    public TextureRegion getShape(int shapeId) {
        return shapeRegions.get(shapeId);
    }

    private void loadInBackground() {
        PixmapPacker packer = new PixmapPacker(1024, 1024,
                Pixmap.Format.RGBA8888, 2, true);
        boolean transferred = false;
        try {
            Map<String, FlashAnimationTimeline> loadedTimelines = loadTimelines();
            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                timelines = Collections.unmodifiableMap(loadedTimelines);
            }

            Set<Integer> shapeIds = collectShapeIds(loadedTimelines);
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

            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                pendingPacker = packer;
                sourceReady = true;
                transferred = true;
            }
        } catch (RuntimeException error) {
            synchronized (loadLock) {
                if (!disposed) {
                    loadFailure = error;
                }
            }
        } finally {
            if (!transferred) {
                packer.dispose();
            }
        }
    }

    private static Map<String, FlashAnimationTimeline> loadTimelines() {
        JsonValue scene = new JsonReader().parse(readCompressedUtf8(SCENE_PATH));
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

    private static Set<Integer> collectShapeIds(
            Map<String, FlashAnimationTimeline> timelines) {
        Set<Integer> shapeIds = new LinkedHashSet<>();
        shapeIds.add(ROCKET_TRAIL_SHAPE_ID);
        for (FlashAnimationTimeline timeline : timelines.values()) {
            for (int i = 0; i < timeline.getFrameCount(); i++) {
                FlashAnimationTimeline.Frame frame = timeline.getFrame(i);
                if (frame != null) {
                    for (FlashAnimationTimeline.Layer layer : frame.layers) {
                        String shapePath = SHAPE_PATH + layer.shapeId + ".svgz";
                        if (Gdx.files.internal(shapePath).exists()) {
                            shapeIds.add(layer.shapeId);
                        }
                    }
                }
            }
        }
        return shapeIds;
    }

    private static Pixmap decodeShape(int shapeId) {
        String svg = readCompressedUtf8(SHAPE_PATH + shapeId + ".svgz");
        int dataStart = svg.indexOf(WEBP_PREFIX);
        if (dataStart < 0) {
            throw new IllegalStateException("Flash shape has no embedded WebP: " + shapeId);
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
        } finally {
            bitmap.recycle();
        }
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
            if (atlas != null) {
                atlas.dispose();
                atlas = null;
            }
            shapeRegions = Collections.emptyMap();
            timelines = Collections.emptyMap();
        }
        loaderThread.interrupt();
    }
}
