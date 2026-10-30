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
