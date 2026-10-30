package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import java.util.List;

/** Draws cached original Flash unit frames in the fitted battlefield coordinate space. */
public final class FlashBattleUnitRenderer {
    private final OriginalFlashAnimationAssets assets;

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
        FlashAnimationTimeline.Frame frame = timeline.getFrame(sourceFrame);
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
