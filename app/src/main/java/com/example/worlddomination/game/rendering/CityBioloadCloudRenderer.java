package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
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
    private final BioloadCloudAnimation[] cityClouds =
            new BioloadCloudAnimation[CITY_COUNT];
    private final int[] activationCounts = new int[CITY_COUNT];
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

    void reset(GameState game) {
        for (int address = 0; address < CITY_COUNT; address++) {
            cityClouds[address] = null;
            activationCounts[address] = 0;
        }
        updateTargets(game);
    }

    void updateTargets(GameState game) {
        if (game == null) {
            return;
        }
        for (int address = 0; address < CITY_COUNT; address++) {
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
        for (BioloadCloudAnimation cloudField : cityClouds) {
            if (cloudField != null) {
                cloudField.tick();
            }
        }
    }

    boolean isAnimating() {
        for (BioloadCloudAnimation cloudField : cityClouds) {
            if (cloudField != null) {
                return true;
            }
        }
        return false;
    }

    void drawCity(SpriteBatch batch, Rectangle mapBounds, int playerId, int cityIndex) {
        int address = playerId * BattlefieldCityLayout.CITIES_PER_PLAYER + cityIndex;
        if (address < 0 || address >= cityClouds.length) {
            return;
        }
        BioloadCloudAnimation cloudField = cityClouds[address];
        if (cloudField == null || !prepareArtwork()) {
            return;
        }

        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
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
                float layerAlpha = alpha * artwork.alpha;
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
