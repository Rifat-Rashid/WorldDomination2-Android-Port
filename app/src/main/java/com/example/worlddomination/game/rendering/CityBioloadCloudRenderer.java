package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/** Renders the original 17-cloud contamination field over each infected city. */
final class CityBioloadCloudRenderer {
    private static final int CITY_COUNT = BattlefieldCityLayout.PLAYER_COUNT
            * BattlefieldCityLayout.CITIES_PER_PLAYER;

    private final OriginalFlashAnimationAssets assets;
    private final CloudState canonicalState = new CloudState();
    private final CloudState battleState = new CloudState();
    private CloudState activeState = canonicalState;
    private BioloadCloudAnimation[] cityClouds = canonicalState.cityClouds;
    private int[] activationCounts = canonicalState.activationCounts;
    private ResolvedArtwork[] cloudArtwork;
    private TextureRegion[] cloudRegions;
    private FlashSpriteTransform[] cloudTransforms;

    static final class ResolvedArtwork {
        final int shapeId;
        final float a;
        final float b;
        final float c;
        final float d;
        final float tx;
        final float ty;
        final float alpha;

        private ResolvedArtwork(int shapeId, Matrix transform, float alpha) {
            this.shapeId = shapeId;
            a = transform.a;
            b = transform.b;
            c = transform.c;
            d = transform.d;
            tx = transform.tx;
            ty = transform.ty;
            this.alpha = alpha;
        }
    }

    private static final class Matrix {
        final float a;
        final float b;
        final float c;
        final float d;
        final float tx;
        final float ty;

        private Matrix(float a, float b, float c, float d, float tx, float ty) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.tx = tx;
            this.ty = ty;
        }

        static Matrix identity() {
            return new Matrix(1f, 0f, 0f, 1f, 0f, 0f);
        }

        Matrix multiply(FlashAnimationTimeline.Layer child) {
            return new Matrix(
                    a * child.a + c * child.b,
                    b * child.a + d * child.b,
                    a * child.c + c * child.d,
                    b * child.c + d * child.d,
                    a * child.tx + c * child.ty + tx,
                    b * child.tx + d * child.ty + ty);
        }
    }

    CityBioloadCloudRenderer(OriginalFlashAnimationAssets assets) {
        this.assets = assets;
    }

    private static final class CloudState {
        final BioloadCloudAnimation[] cityClouds = new BioloadCloudAnimation[CITY_COUNT];
        final int[] activationCounts = new int[CITY_COUNT];
        final boolean[] sourceControlled = new boolean[CITY_COUNT];
        final boolean[] sourceBioloaded = new boolean[CITY_COUNT];
        final boolean[] sourceAttached = new boolean[CITY_COUNT];
        final boolean[] sourceCreatedThisFrame = new boolean[CITY_COUNT];
        final int[] sourceDepth = new int[CITY_COUNT];
        final List<List<BioloadCloudAnimation>> retainedClouds = new ArrayList<>(CITY_COUNT);

        CloudState() {
            for (int city = 0; city < CITY_COUNT; city++) retainedClouds.add(new ArrayList<>());
        }
    }

    void beginBattle(GameState game) {
        // Freeze existing canonical clouds. Do not eagerly create clouds from
        // resolved effects: the legacy updateTargets path runs on the next tick.
        // Source projectile-owned biological callbacks are ported separately.
        activate(battleState);
        clear(battleState);
        if (game == null) return;
        for (int address = 0; address < CITY_COUNT; address++) {
            if (canonicalState.cityClouds[address] != null) {
                activationCounts[address] = 1;
                cityClouds[address] = new BioloadCloudAnimation(
                        sourceIndependentSeed(game.getTurn(), address, 1));
            }
        }
    }

    void endBattle() {
        clear(battleState);
        activate(canonicalState);
    }

    void resetBoth(GameState game) {
        clear(battleState);
        activate(canonicalState);
        reset(game);
    }

    private void activate(CloudState state) {
        activeState = state;
        cityClouds = state.cityClouds;
        activationCounts = state.activationCounts;
    }

    private static void clear(CloudState state) {
        for (int address = 0; address < CITY_COUNT; address++) {
            state.cityClouds[address] = null;
            state.activationCounts[address] = 0;
            state.sourceControlled[address] = false;
            state.sourceBioloaded[address] = false;
            state.sourceAttached[address] = false;
            state.sourceCreatedThisFrame[address] = false;
            state.sourceDepth[address] = -1;
            state.retainedClouds.get(address).clear();
        }
    }

    /** Source clouds are attached by unit callbacks, never by resolved model polling. */
    void useSourceCity(int address) {
        activeState.sourceControlled[address] = true;
        cityClouds[address] = null;
        activeState.sourceBioloaded[address] = false;
        activeState.sourceAttached[address] = false;
        activeState.sourceCreatedThisFrame[address] = false;
        activeState.sourceDepth[address] = -1;
        activeState.retainedClouds.get(address).clear();
    }

    void createSourceCloud(int address, int containerDepth, ReferenceRandom random,
                           boolean duringUnitVisit) {
        activeState.sourceBioloaded[address] = true;
        if (activeState.sourceAttached[address]) return;
        List<BioloadCloudAnimation> retained = activeState.retainedClouds.get(address);
        BioloadCloudAnimation attached = BioloadCloudAnimation.fromSource(random);
        // A frame seek drops the old container but does not clear city.bioCloud_arr.
        // View's reverse for-in spawns the new cohort, then every retained orphan.
        for (int index = retained.size() - 1; index >= 0; index--)
            retained.get(index).respawnSource();
        retained.add(attached);
        cityClouds[address] = attached;
        activeState.sourceAttached[address] = true;
        activeState.sourceCreatedThisFrame[address] = duringUnitVisit;
        activeState.sourceDepth[address] = containerDepth;
    }

    void detachSourceCloud(int address) {
        activeState.sourceAttached[address] = false;
        // The retained AS2 container reference keeps its old depth after removal.
    }

    void tickSourceCity(int address) {
        if (!activeState.sourceControlled[address] || !activeState.sourceAttached[address]) return;
        // MovieClipHost snapshots its callback traversal before a unit can
        // attach new children. Their first callback is on the next host frame.
        if (activeState.sourceCreatedThisFrame[address]) {
            activeState.sourceCreatedThisFrame[address] = false;
            return;
        }
        cityClouds[address].tick();
    }

    boolean isSourceAttached(int address) { return activeState.sourceAttached[address]; }
    boolean isSourceBioloaded(int address) { return activeState.sourceBioloaded[address]; }
    int getSourceDepth(int address) { return activeState.sourceDepth[address]; }
    int getSourceCloudCount(int address) {
        return activeState.retainedClouds.get(address).size() * BioloadCloudAnimation.CLOUD_COUNT;
    }
    BioloadCloudAnimation getSourceCohort(int address, int cohort) {
        return activeState.retainedClouds.get(address).get(cohort);
    }

    void reset(GameState game) {
        clear(activeState);
        updateTargets(game);
    }

    void updateTargets(GameState game) {
        if (game == null) {
            return;
        }
        for (int address = 0; address < CITY_COUNT; address++) {
            if (activeState.sourceControlled[address]) continue;
            int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
            int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
            GameState.PlayerState player = game.getPlayerState(playerId);
            CityState city = player == null ? null : player.getCity(cityIndex);
            if (city == null || !city.bioloaded) {
                cityClouds[address] = null;
            } else if (cityClouds[address] == null) {
                int activation = ++activationCounts[address];
                cityClouds[address] = new BioloadCloudAnimation(
                        sourceIndependentSeed(game.getTurn(), address, activation));
            }
        }
    }

    void tick() {
        for (int address = 0; address < CITY_COUNT; address++) {
            if (activeState.sourceControlled[address]) continue;
            BioloadCloudAnimation cloudField = cityClouds[address];
            if (cloudField != null) {
                cloudField.tick();
            }
        }
    }

    boolean isAnimating() {
        for (int address = 0; address < CITY_COUNT; address++) {
            if (activeState.sourceControlled[address] && !activeState.sourceAttached[address]) continue;
            BioloadCloudAnimation cloudField = cityClouds[address];
            if (cloudField != null) {
                return true;
            }
        }
        return false;
    }

    void drawCity(SpriteBatch batch, Rectangle mapBounds, int playerId, int cityIndex) {
        drawCity(batch, mapBounds, playerId, cityIndex, 1f, 1f);
    }

    void drawCity(SpriteBatch batch, Rectangle mapBounds, int playerId, int cityIndex,
                  float sourceParentAlpha, float sourceCityScale) {
        int address = playerId * BattlefieldCityLayout.CITIES_PER_PLAYER + cityIndex;
        if (address < 0 || address >= cityClouds.length) {
            return;
        }
        BioloadCloudAnimation cloudField = cityClouds[address];
        if (activeState.sourceControlled[address] && !activeState.sourceAttached[address]) return;
        if (cloudField == null || !prepareArtwork()) {
            return;
        }

        boolean source = activeState.sourceControlled[address];
        float stageScale = BattlefieldCityLayout.sourceScale(mapBounds);
        float mapScale = stageScale * (source ? sourceCityScale : 1f);
        float cityX = BattlefieldCityLayout.x(mapBounds, playerId, cityIndex);
        float cityY = BattlefieldCityLayout.y(mapBounds, playerId, cityIndex);
        Color previous = batch.getColor();
        float previousRed = previous.r;
        float previousGreen = previous.g;
        float previousBlue = previous.b;
        float previousAlpha = previous.a;
        for (int cloudIndex = 0; cloudIndex < BioloadCloudAnimation.CLOUD_COUNT;
             cloudIndex++) {
            float alpha = cloudField.getAlpha(cloudIndex) / 100f;
            if (alpha <= 0f) {
                continue;
            }
            float cloudScale = cloudField.getScalePercent(cloudIndex) / 100f;
            for (int layerIndex = 0; layerIndex < cloudArtwork.length; layerIndex++) {
                TextureRegion region = cloudRegions[layerIndex];
                ResolvedArtwork artwork = cloudArtwork[layerIndex];
                if (region == null) {
                    continue;
                }
                float width = region.getRegionWidth() * mapScale;
                float height = region.getRegionHeight() * mapScale;
                float sourceX = cloudField.getX(cloudIndex) + artwork.tx * cloudScale;
                float sourceY = cloudField.getY(cloudIndex) + artwork.ty * cloudScale;
                float x = cityX + sourceX * mapScale;
                float top = cityY - sourceY * mapScale;
                if (source) {
                    // Compose city-local coordinates before projecting the Flash
                    // stage; subtracting projected offsets changes edge pixels.
                    sourceX = cloudField.getX(cloudIndex) + cloudScale * (artwork.tx
                            + artwork.a * assets.getShapeOriginX(artwork.shapeId)
                            + artwork.c * assets.getShapeOriginY(artwork.shapeId));
                    sourceY = cloudField.getY(cloudIndex) + cloudScale * (artwork.ty
                            + artwork.b * assets.getShapeOriginX(artwork.shapeId)
                            + artwork.d * assets.getShapeOriginY(artwork.shapeId));
                    x = BattlefieldCityLayout.sourceX(mapBounds,
                            BattlefieldCityLayout.sourceCityX(playerId, cityIndex)
                                    + sourceX * sourceCityScale, playerId);
                    top = BattlefieldCityLayout.sourceY(mapBounds,
                            BattlefieldCityLayout.sourceCityY(playerId, cityIndex)
                                    + sourceY * sourceCityScale);
                    width = region.getRegionWidth() * sourceCityScale * stageScale;
                    height = region.getRegionHeight() * sourceCityScale * stageScale;
                }
                float layerAlpha = alpha * artwork.alpha;
                if (activeState.sourceControlled[address]) layerAlpha *= sourceParentAlpha;
                layerAlpha = Math.max(0f, Math.min(1f, layerAlpha));
                batch.setColor(1f, 1f, 1f, layerAlpha);
                FlashSpriteTransform transform = cloudTransforms[layerIndex];
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX * cloudScale,
                        transform.scaleY * cloudScale,
                        transform.rotationDegrees);
            }
        }
        batch.setColor(previousRed, previousGreen, previousBlue, previousAlpha);
    }

    private boolean prepareArtwork() {
        if (cloudArtwork != null) {
            return cloudArtwork.length > 0;
        }
        if (!assets.isLoaded()) {
            return false;
        }
        FlashAnimationTimeline timeline = assets.getTimeline("bioCloud");
        if (timeline == null) {
            return false;
        }

        ResolvedArtwork[] artwork = resolveArtwork(timeline,
                assets::getTimelineBySymbolId,
                shapeId -> assets.getShape(shapeId) != null);
        if (artwork.length == 0) {
            cloudArtwork = artwork;
            cloudRegions = new TextureRegion[0];
            cloudTransforms = new FlashSpriteTransform[0];
            return false;
        }
        TextureRegion[] regions = new TextureRegion[artwork.length];
        FlashSpriteTransform[] transforms = new FlashSpriteTransform[artwork.length];
        for (int index = 0; index < artwork.length; index++) {
            ResolvedArtwork layer = artwork[index];
            regions[index] = assets.getShape(layer.shapeId);
            transforms[index] = FlashSpriteTransform.fromMatrix(
                    layer.a, layer.b, layer.c, layer.d);
        }
        cloudArtwork = artwork;
        cloudRegions = regions;
        cloudTransforms = transforms;
        return true;
    }

    static ResolvedArtwork[] resolveArtwork(
            FlashAnimationTimeline root,
            IntFunction<FlashAnimationTimeline> nestedTimeline,
            IntPredicate hasShapeAsset) {
        if (root == null || nestedTimeline == null || hasShapeAsset == null) {
            return new ResolvedArtwork[0];
        }
        List<ResolvedArtwork> artwork = new ArrayList<>();
        Set<Integer> ancestors = new HashSet<>();
        ancestors.add(root.symbolId);
        appendFrame(root.getFrame(0), Matrix.identity(), 1f, nestedTimeline,
                hasShapeAsset, ancestors, artwork);
        return artwork.toArray(new ResolvedArtwork[0]);
    }

    private static void appendFrame(
            FlashAnimationTimeline.Frame frame,
            Matrix parentTransform,
            float parentAlpha,
            IntFunction<FlashAnimationTimeline> nestedTimeline,
            IntPredicate hasShapeAsset,
            Set<Integer> ancestors,
            List<ResolvedArtwork> artwork) {
        if (frame == null) {
            return;
        }
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            Matrix transform = parentTransform.multiply(layer);
            float alpha = parentAlpha * layer.alpha;
            FlashAnimationTimeline nested = nestedTimeline.apply(layer.shapeId);
            if (nested == null) {
                if (hasShapeAsset.test(layer.shapeId)) {
                    artwork.add(new ResolvedArtwork(layer.shapeId, transform, alpha));
                }
                continue;
            }
            if (!ancestors.add(nested.symbolId)) {
                continue;
            }
            appendFrame(nested.getFrame(0), transform, alpha, nestedTimeline,
                    hasShapeAsset, ancestors, artwork);
            ancestors.remove(nested.symbolId);
        }
    }

    private static long sourceIndependentSeed(int turn, int address, int activation) {
        long value = 0x42494f434c4f5544L;
        value ^= (turn + 1L) * 0x9E3779B97F4A7C15L;
        value ^= (address + 1L) * 0xBF58476D1CE4E5B9L;
        value ^= activation * 0x94D049BB133111EBL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
