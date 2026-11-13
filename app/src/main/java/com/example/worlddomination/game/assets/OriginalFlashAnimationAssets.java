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
    private static final class ShapeOrigin {
        final float x;
        final float y;

        ShapeOrigin(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /** Body artwork nested in the source Flash rocket movie clip. */
    public static final int ROCKET_BODY_SHAPE_ID = 792;
    /** Smoke puff nested in the source Flash trail movie clip. */
    public static final int ROCKET_TRAIL_SHAPE_ID = 4936;
    /** Leaf artwork nested inside the source bioCloud and cloud movie clips. */
    public static final int BIO_CLOUD_SHAPE_ID = 2426;
    /** Root Flash timeline containing both action-building clips for each nation. */
    public static final int ACTION_BUILDINGS_SYMBOL_ID = 5658;
    public static final String ACTION_BUILDINGS_TIMELINE = "actionBuildings";
    /** Original city movie clips with named propaganda overlay placements. */
    private static final int[] CITY_SYMBOL_IDS = {1337, 1363, 1388, 1414, 1441};
    /** Flash movie clip attached to each city's two propaganda beam layers. */
    public static final int CITY_PROPAGANDA_BEAMS_SYMBOL_ID = 1309;
    /** Original Flash center-screen dialog containing research and mass-media states. */
    public static final int CENTER_UI_SYMBOL_ID = 4444;
    public static final String CENTER_UI_TIMELINE = "centerScreen";
    /** Alive portrait clip used by both the source setup and gameplay leader cards. */
    public static final int LEADER_PORTRAITS_SYMBOL_ID = 3191;
    public static final String LEADER_PORTRAITS_TIMELINE = "leaderPortraits";
    /** Visible second frame of the source setup btnCancel movie clip (5826). */
    public static final int SETUP_REMOVE_LEADER_SHAPE_ID = 5825;
    /** Small source city order icons are needed before the complete battle atlas finishes. */
    private static final String[] EARLY_CITY_MARKER_LINKAGES = {
            "map_icon_crosshair", "map_icon_soldier", "map_icon_rocket",
            "map_icon_plane", "map_icon_propaganda", "map_icon_special"
    };
    /** White source triangle used by each production investment slider. */
    public static final int PRODUCTION_SLIDER_HANDLE_SHAPE_ID = 3274;

    private static final String SCENE_PATH = "original/runtime/scene.json.gz";
    private static final FlashSceneMetadataCache SCENE_METADATA = new FlashSceneMetadataCache();
    private static final String SHAPE_PATH = "original/runtime/shapes/";
    private static final String TEXT_PATH = "original/runtime/texts/";
    private static final String WEBP_PREFIX = "data:image/webp;base64,";
    private static final String[] REQUIRED_LINKAGES = {
            "map_icon_crosshair", "map_icon_soldier", "map_icon_rocket",
            "map_icon_plane", "map_icon_propaganda", "map_icon_special",
            "flag_pole",
            "interface_center_slider_1",
            "buildings_antiAir",
            "bioCloud",
            "explosion_warhead_1", "explosion_warhead_2", "explosion_warhead_3",
            "explosion_warhead_4", "explosion_bioload_1", "explosion_bioload_2",
            "explosion_bioload_3", "explosion_bioload_4", "explosion001", "explosion002",
            "explosion003", "satellite",
            "rocket", "trail",
            "submarine",
            "ship_plane",
            "plane001", "plane001_tint", "plane001_shadow",
            "plane002", "plane002_tint", "plane002_shadow",
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
    private volatile Map<Integer, FlashAnimationTimeline> cityTimelines =
            Collections.emptyMap();
    private volatile FlashAnimationTimeline centerUiTimeline;
    private volatile Set<Integer> centerUiArtworkIds = Collections.emptySet();
    private volatile Set<Integer> earlyPackedShapeIds = Collections.emptySet();
    private volatile Map<Integer, TextureRegion> shapeRegions = Collections.emptyMap();
    private volatile Map<Integer, TextureRegion> setupPortraitRegions = Collections.emptyMap();
    private volatile Map<Integer, ShapeOrigin> shapeOrigins = Collections.emptyMap();
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
        battleShapeIds.addAll(centerUiArtworkIds);
        battleShapeIds = excludeAlreadyPackedShapes(battleShapeIds,
                earlyPackedShapeIds, Collections.emptySet());
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

    /** True when the early atlas is ready for portraits, initial dialogs, and map art. */
    public boolean areSetupPortraitsLoaded() {
        return setupPortraitsLoaded;
    }

    /** The source flag family and all its nested leaf shapes share the early atlas. */
    public boolean areBattlefieldFlagsLoaded() {
        return setupPortraitsLoaded;
    }

    /** True once the original center-screen dialog art is available for rendering. */
    public boolean areCenterUiAssetsLoaded() {
        return setupPortraitsLoaded;
    }

    public FlashAnimationTimeline getTimeline(String linkage) {
        if (CENTER_UI_TIMELINE.equals(linkage)) {
            return centerUiTimeline;
        }
        return timelines.get(linkage);
    }

    public FlashAnimationTimeline getCenterUiTimeline() {
        return centerUiTimeline;
    }

    /** Returns a nested source movie clip by its Flash character ID. */
    public FlashAnimationTimeline getTimelineBySymbolId(int symbolId) {
        return nestedTimelines.get(symbolId);
    }

    /** Returns the original city root clip for the selected nation's building style. */
    public FlashAnimationTimeline getCityTimeline(String religionCode) {
        return cityTimelines.get(OriginalCityAssets.characterIdForReligion(religionCode));
    }

    public TextureRegion getShape(int shapeId) {
        TextureRegion portrait = setupPortraitRegions.get(shapeId);
        return portrait != null ? portrait : shapeRegions.get(shapeId);
    }

    /** Horizontal source-space origin adjustment recovered from the SVG registration group. */
    public float getShapeOriginX(int shapeId) {
        ShapeOrigin origin = shapeOrigins.get(shapeId);
        return origin == null ? 0f : origin.x;
    }

    /** Vertical source-space origin adjustment recovered from the SVG registration group. */
    public float getShapeOriginY(int shapeId) {
        ShapeOrigin origin = shapeOrigins.get(shapeId);
        return origin == null ? 0f : origin.y;
    }

    private void loadInBackground() {
        PixmapPacker portraitPacker = new PixmapPacker(1024, 1024,
                Pixmap.Format.RGBA8888, 2, true);
        PixmapPacker packer = null;
        boolean portraitTransferred = false;
        boolean battleTransferred = false;
        try {
            JsonValue scene = loadSceneMetadata();
            if (disposed) {
                return;
            }
            Map<String, FlashAnimationTimeline> loadedTimelines = loadTimelines(scene);
            Map<Integer, FlashAnimationTimeline> loadedNestedTimelines = new LinkedHashMap<>();
            Map<Integer, FlashAnimationTimeline> loadedCityTimelines =
                    loadCityTimelines(scene);
            Map<Integer, FlashAnimationTimeline> cityPropagandaOverlays =
                    loadCityPropagandaOverlayTimelines(scene, loadedCityTimelines);
            FlashAnimationTimeline loadedCenterUi = loadTimelineBySymbolId(
                    scene, CENTER_UI_SYMBOL_ID);
            int researchFrame = loadedCenterUi.getFrameNumberForLabel("research") - 1;
            int massMediaFrame = loadedCenterUi.getFrameNumberForLabel("religion") - 1;
            int propagandaFrame = loadedCenterUi.getFrameNumberForLabel("propaganda2") - 1;
            int[] centerUiFrames = {researchFrame, massMediaFrame, propagandaFrame};
            Map<Integer, FlashAnimationTimeline> centerUiChildren = loadNestedTimelines(
                    scene, loadedCenterUi, centerUiFrames);
            Map<Integer, FlashAnimationTimeline> flagChildren = loadNestedTimelines(scene,
                    loadedTimelines.get("flag_pole"));
            Map<Integer, FlashAnimationTimeline> earlyChildren =
                    new LinkedHashMap<>(centerUiChildren);
            earlyChildren.putAll(flagChildren);
            Set<Integer> loadedCenterUiArt = collectCenterUiArtworkIds(loadedCenterUi,
                    centerUiChildren, centerUiFrames,
                    OriginalFlashAnimationAssets::hasPackagedCenterUiArt);
            Set<Integer> loadedEarlyCenterUiArt = collectInitialCenterUiArtworkIds(
                    loadedCenterUi, centerUiChildren,
                    centerUiFrames,
                    OriginalFlashAnimationAssets::hasPackagedCenterUiArt);

            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                timelines = Collections.unmodifiableMap(loadedTimelines);
                nestedTimelines = Collections.unmodifiableMap(earlyChildren);
                centerUiTimeline = loadedCenterUi;
                centerUiArtworkIds = Collections.unmodifiableSet(loadedCenterUiArt);
            }

            Set<Integer> earlyShapeIds = collectSetupPortraitShapeIds(loadedTimelines);
            earlyShapeIds.addAll(loadedEarlyCenterUiArt);
            earlyShapeIds.addAll(collectFlagFamilyShapeIds(loadedTimelines, flagChildren,
                    OriginalFlashAnimationAssets::hasPackagedShape));
            Map<Integer, ShapeOrigin> loadedShapeOrigins = new LinkedHashMap<>();
            packShapes(portraitPacker, earlyShapeIds, loadedShapeOrigins);
            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                shapeOrigins = Collections.unmodifiableMap(
                        new LinkedHashMap<>(loadedShapeOrigins));
                earlyPackedShapeIds = Collections.unmodifiableSet(
                        new LinkedHashSet<>(earlyShapeIds));
                pendingSetupPortraitPacker = portraitPacker;
                setupPortraitSourceReady = true;
                portraitTransferred = true;
            }
            Gdx.graphics.requestRendering();

            loadedNestedTimelines.putAll(earlyChildren);
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("buildings_antiAir")));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("bioCloud")));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("satellite")));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("submarine")));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("plane001_shadow")));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("plane002_shadow")));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get(ACTION_BUILDINGS_TIMELINE)));
            loadedNestedTimelines.putAll(loadNestedTimelines(scene,
                    loadedTimelines.get("interface_center_slider_1")));
            loadedNestedTimelines.putAll(cityPropagandaOverlays);
            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                timelines = Collections.unmodifiableMap(loadedTimelines);
                nestedTimelines = Collections.unmodifiableMap(loadedNestedTimelines);
                cityTimelines = Collections.unmodifiableMap(loadedCityTimelines);
            }

            packer = new PixmapPacker(1024, 1024,
                    Pixmap.Format.RGBA8888, 2, true);
            Set<Integer> battleShapeIds = collectShapeIds(loadedTimelines,
                    loadedNestedTimelines);
            battleShapeIds.addAll(loadedCenterUiArt);
            battleShapeIds.addAll(collectFrameShapeIds(loadedCenterUi,
                    new int[]{researchFrame, massMediaFrame},
                    OriginalFlashAnimationAssets::hasPackagedShape));
            battleShapeIds.removeAll(earlyShapeIds);
            packShapes(packer, battleShapeIds, loadedShapeOrigins);

            synchronized (loadLock) {
                if (disposed) {
                    return;
                }
                shapeOrigins = Collections.unmodifiableMap(
                        new LinkedHashMap<>(loadedShapeOrigins));
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
        for (int shapeId : earlyPackedShapeIds) {
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

    private void packShapes(PixmapPacker packer, Set<Integer> shapeIds,
                            Map<Integer, ShapeOrigin> origins) {
        float[] origin = new float[2];
        for (int shapeId : shapeIds) {
            if (disposed) {
                return;
            }
            Pixmap pixmap = decodeShape(shapeId, origin);
            origins.put(shapeId, new ShapeOrigin(origin[0], origin[1]));
            try {
                packer.pack(regionName(shapeId), pixmap);
            } finally {
                pixmap.dispose();
            }
        }
    }

    private static Set<Integer> collectSetupPortraitShapeIds(
            Map<String, FlashAnimationTimeline> timelines) {
        return collectSetupPortraitShapeIds(timelines, OriginalFlashAnimationAssets::hasPackagedShape);
    }

    static Set<Integer> collectSetupPortraitShapeIds(
            Map<String, FlashAnimationTimeline> timelines, IntPredicate hasShapeAsset) {
        FlashAnimationTimeline faces = timelines.get(LEADER_PORTRAITS_TIMELINE);
        Map<String, FlashAnimationTimeline> setupTimelines = new LinkedHashMap<>();
        if (faces != null) {
            setupTimelines.put(LEADER_PORTRAITS_TIMELINE, faces);
        }
        for (String linkage : EARLY_CITY_MARKER_LINKAGES) {
            FlashAnimationTimeline marker = timelines.get(linkage);
            if (marker != null) {
                setupTimelines.put(linkage, marker);
            }
        }
        Set<Integer> shapes = collectShapeIds(setupTimelines, Collections.emptyMap(), false,
                hasShapeAsset);
        if (hasShapeAsset.test(SETUP_REMOVE_LEADER_SHAPE_ID)) {
            shapes.add(SETUP_REMOVE_LEADER_SHAPE_ID);
        }
        return shapes;
    }

    static Set<Integer> collectFlagFamilyShapeIds(
            Map<String, FlashAnimationTimeline> timelines,
            Map<Integer, FlashAnimationTimeline> flagChildren,
            IntPredicate hasShapeAsset) {
        FlashAnimationTimeline flagPole = timelines.get("flag_pole");
        if (flagPole == null) {
            throw new IllegalStateException("Missing original flag_pole timeline");
        }
        return collectShapeIds(Collections.singletonMap("flag_pole", flagPole),
                flagChildren, false, hasShapeAsset);
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

    private static boolean hasPackagedCenterUiArt(int assetId) {
        return hasPackagedShape(assetId)
                || Gdx.files.internal(TEXT_PATH + assetId + ".svgz").exists();
    }

    /** One shared parse for concurrent loaders; derived timelines never mutate the JSON tree. */
    static JsonValue loadSceneMetadata() {
        return SCENE_METADATA.get(() -> new JsonReader().parse(readCompressedUtf8(SCENE_PATH)));
    }

    static Map<String, FlashAnimationTimeline> loadTimelines(JsonValue scene) {
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
            if (symbol.getInt("id", -1) == ACTION_BUILDINGS_SYMBOL_ID) {
                result.put(ACTION_BUILDINGS_TIMELINE,
                        FlashAnimationTimeline.fromSymbol(symbol, frameRate));
            }
        }
        for (String linkage : REQUIRED_LINKAGES) {
            if (!result.containsKey(linkage)) {
                throw new IllegalStateException("Missing original Flash animation " + linkage);
            }
        }
        if (!result.containsKey(ACTION_BUILDINGS_TIMELINE)) {
            throw new IllegalStateException("Missing original Flash action-building timeline");
        }
        result.put(LEADER_PORTRAITS_TIMELINE,
                loadTimelineBySymbolId(scene, LEADER_PORTRAITS_SYMBOL_ID));
        return result;
    }

    static Map<Integer, FlashAnimationTimeline> loadNestedTimelines(
            JsonValue scene, FlashAnimationTimeline rootTimeline) {
        return loadNestedTimelines(scene, rootTimeline, null);
    }

    static Map<Integer, FlashAnimationTimeline> loadNestedTimelines(
            JsonValue scene, FlashAnimationTimeline rootTimeline, int[] rootFrameIndexes) {
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
        enqueueChildSymbols(rootTimeline, pending, rootFrameIndexes);
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
        enqueueChildSymbols(timeline, pending, null);
    }

    private static void enqueueChildSymbols(FlashAnimationTimeline timeline,
                                            Deque<Integer> pending, int[] frameIndexes) {
        int frameCount = frameIndexes == null ? timeline.getFrameCount() : frameIndexes.length;
        for (int index = 0; index < frameCount; index++) {
            int frameIndex = frameIndexes == null ? index : frameIndexes[index];
            FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
            if (frame == null) {
                continue;
            }
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                pending.addLast(layer.shapeId);
            }
        }
    }

    static FlashAnimationTimeline loadTimelineBySymbolId(JsonValue scene, int symbolId) {
        JsonValue symbols = scene.get("symbols");
        for (JsonValue symbol = symbols == null ? null : symbols.child;
             symbol != null; symbol = symbol.next) {
            if (symbol.getInt("id", -1) == symbolId) {
                return FlashAnimationTimeline.fromSymbol(symbol, scene.getFloat("fps"));
            }
        }
        throw new IllegalStateException("Missing original Flash symbol " + symbolId);
    }

    static Map<Integer, FlashAnimationTimeline> loadCityTimelines(JsonValue scene) {
        Map<Integer, FlashAnimationTimeline> result = new LinkedHashMap<>();
        for (int symbolId : CITY_SYMBOL_IDS) {
            result.put(symbolId, loadTimelineBySymbolId(scene, symbolId));
        }
        return result;
    }

    static Map<Integer, FlashAnimationTimeline> loadCityPropagandaOverlayTimelines(
            JsonValue scene, Map<Integer, FlashAnimationTimeline> cityTimelines) {
        Map<Integer, FlashAnimationTimeline> result = new LinkedHashMap<>();
        for (FlashAnimationTimeline city : cityTimelines.values()) {
            FlashAnimationTimeline.Frame firstFrame = city.getFrame(0);
            if (firstFrame == null) {
                continue;
            }
            for (FlashAnimationTimeline.Layer layer : firstFrame.layers) {
                if (!"propagandaBeams".equals(layer.name)
                        && !"propagandaBeams2".equals(layer.name)) {
                    continue;
                }
                if (!result.containsKey(layer.shapeId)) {
                    FlashAnimationTimeline overlay = loadTimelineBySymbolId(
                            scene, layer.shapeId);
                    result.put(layer.shapeId, overlay);
                    result.putAll(loadNestedTimelines(scene, overlay));
                }
            }
        }
        return result;
    }

    private static Set<Integer> collectFrameShapeIds(FlashAnimationTimeline timeline,
                                                      int[] frameIndexes,
                                                      IntPredicate hasShapeAsset) {
        Set<Integer> shapeIds = new LinkedHashSet<>();
        for (int frameIndex : frameIndexes) {
            FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
            if (frame == null) {
                continue;
            }
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                if (hasShapeAsset.test(layer.shapeId)) {
                    shapeIds.add(layer.shapeId);
                }
            }
        }
        return shapeIds;
    }

    static Set<Integer> collectCenterUiArtworkIds(
            FlashAnimationTimeline centerUi,
            Map<Integer, FlashAnimationTimeline> nestedTimelines,
            int[] frameIndexes, IntPredicate hasArtwork) {
        Set<Integer> shapeIds = collectShapeIds(Collections.emptyMap(), nestedTimelines,
                false, hasArtwork);
        shapeIds.addAll(collectFrameShapeIds(centerUi, frameIndexes, hasArtwork));
        return shapeIds;
    }

    static Set<Integer> excludeAlreadyPackedShapes(Set<Integer> atlasShapeIds,
                                                    Set<Integer> setupPortraitShapeIds,
                                                    Set<Integer> earlyCenterUiShapeIds) {
        Set<Integer> remaining = new LinkedHashSet<>(atlasShapeIds);
        remaining.removeAll(setupPortraitShapeIds);
        remaining.removeAll(earlyCenterUiShapeIds);
        return remaining;
    }

    static Set<Integer> collectInitialCenterUiArtworkIds(
            FlashAnimationTimeline centerUi,
            Map<Integer, FlashAnimationTimeline> nestedTimelines,
            int[] frameIndexes, IntPredicate hasArtwork) {
        Set<Integer> shapeIds = new LinkedHashSet<>();
        Set<Long> visitedFrames = new HashSet<>();
        for (int frameIndex : frameIndexes) {
            collectInitialCenterUiFrame(centerUi, frameIndex, nestedTimelines,
                    hasArtwork, shapeIds, visitedFrames, 0);
        }
        return shapeIds;
    }

    private static void collectInitialCenterUiFrame(
            FlashAnimationTimeline timeline, int frameIndex,
            Map<Integer, FlashAnimationTimeline> nestedTimelines,
            IntPredicate hasArtwork, Set<Integer> shapeIds, Set<Long> visitedFrames,
            int depth) {
        if (timeline == null || depth > 20) {
            return;
        }
        FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
        if (frame == null) {
            return;
        }
        long visitKey = ((long) timeline.symbolId << 32) | (frameIndex & 0xffffffffL);
        if (!visitedFrames.add(visitKey)) {
            return;
        }
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            if (isDynamicCenterUiText(layer.name)) {
                continue;
            }
            if (hasArtwork.test(layer.shapeId)) {
                shapeIds.add(layer.shapeId);
            }
            FlashAnimationTimeline nested = nestedTimelines.get(layer.shapeId);
            if (nested == null || nested.getFrameCount() == 0) {
                continue;
            }
            if (isResearchLayer(layer.name)) {
                for (int level = 0; level < 3; level++) {
                    collectInitialCenterUiFrame(nested, level, nestedTimelines,
                            hasArtwork, shapeIds, visitedFrames, depth + 1);
                    int selectedFrame = level + 4;
                    if (selectedFrame < nested.getFrameCount()) {
                        collectInitialCenterUiFrame(nested, selectedFrame, nestedTimelines,
                                hasArtwork, shapeIds, visitedFrames, depth + 1);
                    }
                }
                int completedFrame = nested.getFrameNumberForLabel("allInvented") - 1;
                if (completedFrame >= 0 && completedFrame > 6) {
                    collectInitialCenterUiFrame(nested, completedFrame, nestedTimelines,
                            hasArtwork, shapeIds, visitedFrames, depth + 1);
                }
            } else if ("MC1".equals(layer.name)) {
                for (int nestedIndex = 0; nestedIndex < nested.getFrameCount(); nestedIndex++) {
                    collectInitialCenterUiFrame(nested, nestedIndex, nestedTimelines,
                            hasArtwork, shapeIds, visitedFrames, depth + 1);
                }
            } else {
                collectInitialCenterUiFrame(nested, 0, nestedTimelines, hasArtwork,
                        shapeIds, visitedFrames, depth + 1);
            }
        }
    }

    private static boolean isDynamicCenterUiText(String name) {
        return name != null && (name.startsWith("text") || name.startsWith("txt"));
    }

    private static boolean isResearchLayer(String name) {
        return name != null && name.length() == 5
                && name.startsWith("tech") && name.charAt(4) >= '1' && name.charAt(4) <= '7';
    }

    private static Pixmap decodeShape(int shapeId, float[] origin) {
        String path = Gdx.files.internal(SHAPE_PATH + shapeId + ".svgz").exists()
                ? SHAPE_PATH : TEXT_PATH;
        String svg = readCompressedUtf8(path + shapeId + ".svgz");
        parseRegistrationOrigin(svg, origin);
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

    static void parseRegistrationOrigin(String svg, float[] output) {
        if (output == null || output.length < 2) {
            throw new IllegalArgumentException("Two registration-origin values are required");
        }
        output[0] = 0f;
        output[1] = 0f;
        if (svg == null) {
            return;
        }
        int groupStart = svg.indexOf("<g");
        int groupEnd = groupStart < 0 ? -1 : svg.indexOf('>', groupStart);
        int transformStart = groupStart < 0 ? -1 : svg.indexOf("transform", groupStart);
        if (transformStart < 0 || groupEnd < transformStart) {
            return;
        }
        int matrixStart = svg.indexOf("matrix(", transformStart);
        int matrixEnd = matrixStart < 0 ? -1 : svg.indexOf(')', matrixStart);
        if (matrixEnd < 0 || matrixEnd > groupEnd) {
            return;
        }
        String[] components = svg.substring(matrixStart + 7, matrixEnd)
                .trim().split("[,\\s]+");
        if (components.length != 6) {
            return;
        }
        try {
            output[0] = -Float.parseFloat(components[4]);
            output[1] = -Float.parseFloat(components[5]);
        } catch (NumberFormatException ignored) {
            output[0] = 0f;
            output[1] = 0f;
        }
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
            earlyPackedShapeIds = Collections.emptySet();
            shapeOrigins = Collections.emptyMap();
            nestedTimelines = Collections.emptyMap();
            cityTimelines = Collections.emptyMap();
            setupPortraitsLoaded = false;
            timelines = Collections.emptyMap();
        }
        loaderThread.interrupt();
    }
}
