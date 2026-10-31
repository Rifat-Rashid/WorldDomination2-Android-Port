package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import java.util.List;

/** Draws cached original Flash unit frames in the fitted battlefield coordinate space. */
public final class FlashBattleUnitRenderer {
    private static final String[] SATELLITE_BASE_COLOR_HEX = {
            "0xFFFFFF", "0x0000FF", "0xFF0000", "0xFF9900", "0xFF000", "0x00FF00"
    };
    private static final int[][] SATELLITE_LASER_COLORS = buildSatelliteLaserColors();
    private static final int MAX_SATELLITE_CLIP_DEPTH = 8;
    private final OriginalFlashAnimationAssets assets;
    private final float[] satelliteLaserOriginScratch = new float[4];

    public FlashBattleUnitRenderer(OriginalFlashAnimationAssets assets) {
        this.assets = assets;
    }

    public void draw(SpriteBatch batch, Rectangle mapBounds,
                     List<FlashSoldierOneAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashSoldierOneAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashSoldierOneAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        1f, 1f, actor.visible);
            }
        }
    }

    public void drawApacheMarine(SpriteBatch batch, Rectangle mapBounds,
                                 List<FlashApacheMarineAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashApacheMarineAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashApacheMarineAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        actor.scale, actor.alpha, actor.visible);
            }
        }
    }

    public void drawSuicideBomber(SpriteBatch batch, Rectangle mapBounds,
                                  List<FlashSuicideBomberAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashSuicideBomberAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashSuicideBomberAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        1f, actor.alpha, actor.visible);
            }
        }
    }

    public void drawChinookTank(SpriteBatch batch, Rectangle mapBounds,
                                List<FlashChinookTankAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashChinookTankAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashChinookTankAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        1f, 1f, actor.visible);
            }
        }
    }

    /** Draws the recovered Flash rocket sprite and its original fading smoke artwork. */
    public void drawRocket(SpriteBatch batch, Rectangle mapBounds,
                           List<FlashRocketAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        TextureRegion rocketShape = assets.getShape(
                OriginalFlashAnimationAssets.ROCKET_BODY_SHAPE_ID);
        TextureRegion smokeShape = assets.getShape(
                OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID);
        if (rocketShape == null || smokeShape == null) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashRocketAnimation animation : animations) {
            FlashBattleRoutes.RocketStrike route = animation.getRoute();
            for (int trail = 0; trail < animation.getTrailCount(); trail++) {
                float trailX = animation.getTrailX(trail);
                drawRocketSprite(batch, mapBounds, smokeShape,
                        trailX, animation.getTrailY(trail), route.attackerPlayerId,
                        route.targetPlayerId, rocketProgress(route, trailX), 0f,
                        animation.getTrailAlpha(trail), mapScale, 1f);
            }
            FlashRocketAnimation.Actor rocket = animation.getRocket();
            if (rocket.visible) {
                drawRocketSprite(batch, mapBounds, rocketShape, rocket.sourceX, rocket.sourceY,
                        route.attackerPlayerId, route.targetPlayerId,
                        rocketProgress(route, rocket.sourceX), rocket.rotationDegrees, 1f,
                        mapScale, rocket.scaleY);
            }
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** Draws ship-launched aircraft from the original 24-frame Flash linkage. */
    public void drawCarrierPlanes(SpriteBatch batch, Rectangle mapBounds,
                                  List<FlashCarrierPlaneAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline("ship_plane");
        if (timeline == null) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashCarrierPlaneAnimation animation : animations) {
            FlashCarrierPlaneAnimation.Actor actor = animation.getActor();
            if (!actor.visible) {
                continue;
            }
            FlashAnimationTimeline.Frame frame = timeline.getFrame(actor.sourceFrame);
            if (frame == null) {
                continue;
            }
            FlashBattleRoutes.CarrierPlaneStrike route = animation.getRoute();
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                TextureRegion region = assets.getShape(layer.shapeId);
                if (region == null) {
                    continue;
                }
                float width = region.getRegionWidth() * mapScale;
                float height = region.getRegionHeight() * mapScale;
                float x = BattlefieldCityLayout.sourceXBetween(mapBounds,
                        actor.sourceX + layer.tx, route.attackerPlayerId,
                        route.targetPlayerId, actor.routeProgress);
                float top = BattlefieldCityLayout.sourceY(mapBounds, actor.sourceY + layer.ty);
                FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                        layer.a, layer.b, layer.c, layer.d);
                float direction = actor.flippedHorizontally ? -1f : 1f;
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX * direction, transform.scaleY,
                        transform.rotationDegrees);
            }
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** Draws the original religion-specific satellite symbol while it approaches orbit. */
    public void drawSatellites(SpriteBatch batch, Rectangle mapBounds,
                               List<FlashSatelliteAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline("satellite");
        if (timeline == null) {
            return;
        }
        for (FlashSatelliteAnimation animation : animations) {
            if (!animation.isStarted() || animation.isFinished()) {
                continue;
            }
            FlashBattleRoutes.SatelliteStrike route = animation.getRoute();
            drawSatelliteTimelineFrame(batch, mapBounds,
                    timeline.getFrameByLabel(route.religionCode),
                    animation.getSourceX(), animation.getSourceY(),
                    route.attackerPlayerId, animation.getSourceClipFrameNumber() - 1,
                    1f, 0);
        }
    }

    /** Draws the source-colored multi-stroke laser between satellite and target city. */
    public void drawSatelliteBeams(ShapeRenderer renderer, Rectangle mapBounds,
                                   List<FlashSatelliteAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashSatelliteAnimation animation : animations) {
            float alpha = Math.max(0f, Math.min(1f, animation.getLaserAlpha()));
            if (!animation.isFiring() || alpha <= 0f) {
                continue;
            }
            FlashBattleRoutes.SatelliteStrike route = animation.getRoute();
            if (!findSatelliteLaserOrigin(animation, satelliteLaserOriginScratch)) {
                continue;
            }
            float sourceX = animation.getSourceX() + satelliteLaserOriginScratch[0]
                    + satelliteLaserOriginScratch[2];
            float sourceY = animation.getSourceY() + satelliteLaserOriginScratch[1]
                    + satelliteLaserOriginScratch[3];
            float startX = BattlefieldCityLayout.sourceX(mapBounds, sourceX,
                    route.attackerPlayerId);
            int[] colors = SATELLITE_LASER_COLORS[route.attackerColor];
            for (int offset = 0; offset < 4; offset++) {
                int color = colors[6 - offset];
                renderer.setColor(((color >>> 16) & 0xFF) / 255f,
                        ((color >>> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, alpha);
                float firstStartY = BattlefieldCityLayout.sourceY(mapBounds,
                        sourceY - offset);
                float firstEndX = BattlefieldCityLayout.sourceX(mapBounds,
                        route.targetCity.x + offset, route.targetPlayerId);
                float firstEndY = BattlefieldCityLayout.sourceY(mapBounds,
                        route.targetCity.y - offset);
                renderer.line(startX, firstStartY, firstEndX, firstEndY);

                float secondStartY = BattlefieldCityLayout.sourceY(mapBounds,
                        sourceY + offset);
                float secondEndX = BattlefieldCityLayout.sourceX(mapBounds,
                        route.targetCity.x, route.targetPlayerId);
                float secondEndY = BattlefieldCityLayout.sourceY(mapBounds,
                        route.targetCity.y + offset);
                renderer.line(startX, secondStartY, secondEndX, secondEndY);
            }
        }
        renderer.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private void drawSatelliteTimelineFrame(SpriteBatch batch, Rectangle mapBounds,
                                            FlashAnimationTimeline.Frame frame,
                                            float sourceX, float sourceY,
                                            int islandPlayerId, int clipFrameIndex,
                                            float parentAlpha, int depth) {
        if (frame == null || parentAlpha <= 0f || depth > MAX_SATELLITE_CLIP_DEPTH) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float layerAlpha = parentAlpha * layer.alpha;
            if (layerAlpha <= 0f) {
                continue;
            }
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region != null) {
                float width = region.getRegionWidth() * mapScale;
                float height = region.getRegionHeight() * mapScale;
                float x = BattlefieldCityLayout.sourceX(mapBounds,
                        sourceX + layer.tx, islandPlayerId);
                float top = BattlefieldCityLayout.sourceY(mapBounds, sourceY + layer.ty);
                FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                        layer.a, layer.b, layer.c, layer.d);
                batch.setColor(1f, 1f, 1f, layerAlpha);
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX, transform.scaleY, transform.rotationDegrees);
                continue;
            }

            FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
            if (nested == null || nested.getFrameCount() == 0) {
                continue;
            }
            int nestedFrameIndex = Math.min(clipFrameIndex, nested.getFrameCount() - 1);
            drawSatelliteTimelineFrame(batch, mapBounds, nested.getFrame(nestedFrameIndex),
                    sourceX + layer.tx, sourceY + layer.ty, islandPlayerId,
                    clipFrameIndex, layerAlpha, depth + 1);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private boolean findSatelliteLaserOrigin(FlashSatelliteAnimation animation,
                                             float[] origin) {
        FlashBattleRoutes.SatelliteStrike route = animation.getRoute();
        FlashAnimationTimeline satellite = assets.getTimeline("satellite");
        if (satellite == null) {
            return false;
        }
        FlashAnimationTimeline.Frame religionFrame =
                satellite.getFrameByLabel(route.religionCode);
        if (religionFrame == null) {
            return false;
        }
        for (FlashAnimationTimeline.Layer clipLayer : religionFrame.layers) {
            FlashAnimationTimeline clip = assets.getTimelineBySymbolId(clipLayer.shapeId);
            if (clip == null || clip.getFrameCount() == 0) {
                continue;
            }
            int clipFrameIndex = Math.min(animation.getSourceClipFrameNumber() - 1,
                    clip.getFrameCount() - 1);
            FlashAnimationTimeline.Frame frame = clip.getFrame(clipFrameIndex);
            if (frame == null) {
                continue;
            }
            for (FlashAnimationTimeline.Layer marker : frame.layers) {
                if ("laserStartMC".equals(marker.name)) {
                    origin[0] = clipLayer.tx;
                    origin[1] = clipLayer.ty;
                    origin[2] = marker.tx;
                    origin[3] = marker.ty;
                    return true;
                }
            }
        }
        return false;
    }

    private static int[][] buildSatelliteLaserColors() {
        int[][] colorsByPlayer = new int[SATELLITE_BASE_COLOR_HEX.length][8];
        for (int playerColor = 0; playerColor < colorsByPlayer.length; playerColor++) {
            String firstHex = brightenHexBySteps(SATELLITE_BASE_COLOR_HEX[playerColor], 2);
            String secondHex = brightenHexBySteps(firstHex, 2);
            String thirdHex = brightenHexBySteps(secondHex, 3);
            int first = rgbFromFlashHex(firstHex);
            int second = rgbFromFlashHex(secondHex);
            int third = rgbFromFlashHex(thirdHex);
            colorsByPlayer[playerColor] = new int[]{first, first, second, third,
                    third, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF};
        }
        return colorsByPlayer;
    }

    private static String brightenHexBySteps(String color, int steps) {
        String result = color;
        for (int step = 0; step < steps; step++) {
            StringBuilder brighter = new StringBuilder("0x");
            for (int position = 2; position < 9; position++) {
                char digit = position < result.length() ? result.charAt(position) : '\0';
                if (digit == '\0') {
                    // ActionScript Number("") is zero; the source appends "1" here.
                    brighter.append('1');
                } else if (digit >= '0' && digit < '9') {
                    brighter.append((char) (digit + 1));
                } else if (digit == '9') {
                    brighter.append('A');
                } else if (digit >= 'A' && digit < 'F') {
                    brighter.append((char) (digit + 1));
                } else {
                    brighter.append(digit);
                }
            }
            result = brighter.toString();
        }
        return result;
    }

    static int sourceSatelliteLaserColor(int playerColor, int paletteIndex) {
        if (playerColor < 1 || playerColor >= SATELLITE_LASER_COLORS.length
                || paletteIndex < 0 || paletteIndex >= 8) {
            throw new IllegalArgumentException("Invalid source satellite laser color index");
        }
        return SATELLITE_LASER_COLORS[playerColor][paletteIndex];
    }

    private static int rgbFromFlashHex(String color) {
        return (int) (Long.parseLong(color.substring(2), 16) & 0xFFFFFFL);
    }

    private static void drawRocketSprite(SpriteBatch batch, Rectangle mapBounds,
                                         TextureRegion region, float sourceX,
                                         float sourceY, int fromPlayerId, int toPlayerId,
                                         float progress, float rotationDegrees, float alpha,
                                         float mapScale, float scaleY) {
        float width = region.getRegionWidth() * mapScale;
        float height = region.getRegionHeight() * mapScale;
        float centerX = BattlefieldCityLayout.sourceXBetween(mapBounds, sourceX,
                fromPlayerId, toPlayerId, progress);
        float centerY = BattlefieldCityLayout.sourceY(mapBounds, sourceY);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(region, centerX - width / 2f, centerY - height / 2f,
                width / 2f, height / 2f, width, height, 1f, scaleY, rotationDegrees);
    }

    private static float rocketProgress(FlashBattleRoutes.RocketStrike route,
                                        float sourceX) {
        float horizontalDistance = route.target.x - route.launch.x;
        if (horizontalDistance == 0f) {
            return 0f;
        }
        return (sourceX - route.launch.x) / horizontalDistance;
    }

    private void drawActor(SpriteBatch batch, Rectangle mapBounds, String linkage,
                           int sourceFrame, float sourceX, float sourceY,
                           int islandPlayerId, float actorScale, float actorAlpha,
                           boolean visible) {
        if (!visible) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline(linkage);
        if (timeline == null) {
            return;
        }
        drawTimelineFrame(batch, mapBounds, timeline.getFrame(sourceFrame), sourceX,
                sourceY, islandPlayerId, actorScale, actorAlpha);
    }

    private void drawTimelineFrame(SpriteBatch batch, Rectangle mapBounds,
                                   FlashAnimationTimeline.Frame frame,
                                   float sourceX, float sourceY, int islandPlayerId,
                                   float actorScale, float actorAlpha) {
        if (frame == null) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        batch.setColor(1f, 1f, 1f, actorAlpha);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region == null) {
                continue;
            }
            float width = region.getRegionWidth() * mapScale;
            float height = region.getRegionHeight() * mapScale;
            float x = BattlefieldCityLayout.sourceX(mapBounds,
                    sourceX + layer.tx, islandPlayerId);
            float top = BattlefieldCityLayout.sourceY(mapBounds, sourceY + layer.ty);
            FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                    layer.a, layer.b, layer.c, layer.d);
            batch.draw(region, x, top - height, 0f, height, width, height,
                    transform.scaleX * actorScale, transform.scaleY * actorScale,
                    transform.rotationDegrees);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

}
