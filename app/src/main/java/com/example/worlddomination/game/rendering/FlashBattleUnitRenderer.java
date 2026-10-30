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
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashSoldierOneAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashSoldierOneAnimation.Actor actor = animation.getActor(index);
                if (!actor.visible) {
                    continue;
                }
                FlashAnimationTimeline timeline = assets.getTimeline(actor.linkage);
                if (timeline == null) {
                    continue;
                }
                FlashAnimationTimeline.Frame frame = timeline.getFrame(actor.sourceFrame);
                if (frame == null) {
                    continue;
                }
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    TextureRegion region = assets.getShape(layer.shapeId);
                    if (region == null) {
                        continue;
                    }
                    float width = region.getRegionWidth() * mapScale;
                    float height = region.getRegionHeight() * mapScale;
                    float x = BattlefieldCityLayout.sourceX(mapBounds,
                            actor.sourceX + layer.tx, actor.islandPlayerId);
                    float top = BattlefieldCityLayout.sourceY(mapBounds,
                            actor.sourceY + layer.ty);
                    batch.draw(region, x, top - height, 0f, 0f, width, height,
                            layer.a, layer.d, 0f);
                }
            }
        }
    }
}
