package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Lazy original four-frame endgame art, independent of the setup and battle atlases. */
public final class OriginalEndgameAssets implements Disposable {
    public static final int SYMBOL_ID = 5903;
    private static final String SCENE_PATH = "original/runtime/scene.json.gz";
    private static final String SHAPE_PATH = "original/runtime/shapes/";
    private final Object lock = new Object();
    private volatile boolean disposed;
    private volatile boolean ready;
    private volatile RuntimeException failure;
    private volatile FlashAnimationTimeline timeline;
    private volatile Map<Integer, FlashAnimationTimeline> nested = Collections.emptyMap();
    private volatile Map<Integer, TextureRegion> regions = Collections.emptyMap();
    private volatile Map<Integer, float[]> origins = Collections.emptyMap();
    private PixmapPacker pendingPacker;
    private TextureAtlas atlas;
    private Thread loader;

    /** Call once when a match finishes; bitmap decoding then runs off the GL thread. */
    public void start() {
        synchronized (lock) {
            if (disposed || loader != null) return;
            loader = new Thread(this::load, "wd2-endgame-assets");
            loader.setDaemon(true);
            loader.setPriority(Thread.MIN_PRIORITY);
            loader.start();
        }
    }

    private void load() {
        PixmapPacker packer = new PixmapPacker(1024, 1024,
                Pixmap.Format.RGBA8888, 2, true);
        boolean transferred = false;
        try {
            JsonValue scene = new JsonReader().parse(
                    OriginalFlashAnimationAssets.readCompressedUtf8(SCENE_PATH));
            FlashAnimationTimeline root = OriginalFlashAnimationAssets.loadTimelineBySymbolId(
                    scene, SYMBOL_ID);
            Map<Integer, FlashAnimationTimeline> children =
                    OriginalFlashAnimationAssets.loadNestedTimelines(scene, root);
            Set<Integer> leaves = collectLeafShapeIds(root, children,
                    id -> Gdx.files.internal(SHAPE_PATH + id + ".svgz").exists());
            Map<Integer, float[]> loadedOrigins = new LinkedHashMap<>();
            for (int id : leaves) {
                if (disposed) return;
                float[] origin = new float[2];
                Pixmap shape = id == 5891 || id == 5894
                        ? FlashBitmapPatternComposer.decode(id, origin)
                        : OriginalFlashAnimationAssets.decodeShape(id, origin);
                loadedOrigins.put(id, origin);
                try { packer.pack(regionName(id), shape); }
                finally { shape.dispose(); }
            }
            synchronized (lock) {
                if (disposed) return;
                timeline = root;
                nested = Collections.unmodifiableMap(new LinkedHashMap<>(children));
                origins = Collections.unmodifiableMap(loadedOrigins);
                pendingPacker = packer;
                transferred = true;
            }
            Gdx.graphics.requestRendering();
        } catch (RuntimeException error) {
            if (!disposed) failure = error;
        } finally {
            if (!transferred) packer.dispose();
        }
    }

    /** Uploads decoded art on the GL thread. Returns true once every leaf is drawable. */
    public boolean update() {
        if (disposed) return false;
        if (failure != null) throw failure;
        if (ready) return true;
        PixmapPacker packer;
        synchronized (lock) {
            packer = pendingPacker;
            pendingPacker = null;
        }
        if (packer == null) return false;
        TextureAtlas uploaded;
        try { uploaded = packer.generateTextureAtlas(TextureFilter.Linear, TextureFilter.Linear, false); }
        finally { packer.dispose(); }
        Map<Integer, TextureRegion> loadedRegions = new LinkedHashMap<>();
        for (int id : origins.keySet()) {
            TextureRegion region = uploaded.findRegion(regionName(id));
            if (region == null) {
                uploaded.dispose();
                throw new IllegalStateException("Missing endgame shape " + id);
            }
            loadedRegions.put(id, region);
        }
        synchronized (lock) {
            if (disposed) { uploaded.dispose(); return false; }
            atlas = uploaded;
            regions = Collections.unmodifiableMap(loadedRegions);
            ready = true;
        }
        return true;
    }

    public boolean isReady() { return ready; }
    public FlashAnimationTimeline getTimeline() { return timeline; }
    public FlashAnimationTimeline getNested(int id) { return nested.get(id); }
    public TextureRegion getShape(int id) { return regions.get(id); }
    public float getOriginX(int id) { float[] value = origins.get(id); return value == null ? 0 : value[0]; }
    public float getOriginY(int id) { float[] value = origins.get(id); return value == null ? 0 : value[1]; }

    static Set<Integer> collectLeafShapeIds(FlashAnimationTimeline root,
            Map<Integer, FlashAnimationTimeline> children, java.util.function.IntPredicate hasShape) {
        Set<Integer> leaves = new LinkedHashSet<>();
        collectLeaves(root, children, hasShape, leaves);
        for (FlashAnimationTimeline child : children.values())
            collectLeaves(child, children, hasShape, leaves);
        return leaves;
    }

    private static void collectLeaves(FlashAnimationTimeline timeline,
            Map<Integer, FlashAnimationTimeline> children,
            java.util.function.IntPredicate hasShape, Set<Integer> leaves) {
        for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
            FlashAnimationTimeline.Frame source = timeline.getFrame(frame);
            if (source == null) continue;
            for (FlashAnimationTimeline.Layer layer : source.layers)
                if (!children.containsKey(layer.shapeId) && hasShape.test(layer.shapeId))
                    leaves.add(layer.shapeId);
        }
    }

    private static String regionName(int id) { return "endgameshape" + id; }

    @Override public void dispose() {
        synchronized (lock) {
            disposed = true;
            ready = false;
            if (pendingPacker != null) { pendingPacker.dispose(); pendingPacker = null; }
            if (atlas != null) { atlas.dispose(); atlas = null; }
            regions = Collections.emptyMap();
            origins = Collections.emptyMap();
            nested = Collections.emptyMap();
            timeline = null;
        }
        if (loader != null) loader.interrupt();
    }
}
