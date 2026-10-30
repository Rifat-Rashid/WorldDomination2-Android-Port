package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import java.util.List;

/** Draws the source Flash explosion frames above the affected battlefield city. */
public final class BattleExplosionRenderer {
    private final OriginalFlashAnimationAssets assets;

    public BattleExplosionRenderer(OriginalFlashAnimationAssets assets) {
        this.assets = assets;
    }

    public void draw(SpriteBatch batch, Rectangle mapBounds,
                     List<BattleAnimationSequence> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (BattleAnimationSequence animation : animations) {
            FlashAnimationTimeline.Frame frame = animation.getCurrentFrame();
            if (frame == null) {
                continue;
            }
            if (frame.b != 0f || frame.c != 0f) {
                throw new IllegalStateException("Unsupported rotated Flash effect frame in "
                        + animation.timeline.linkage);
            }
            TextureRegion region = assets.getShape(frame.shapeId);
            if (region == null) {
                throw new IllegalStateException("No texture for Flash shape " + frame.shapeId);
            }
            float width = region.getRegionWidth() * mapScale;
            float height = region.getRegionHeight() * mapScale;
            float x = BattlefieldCityLayout.x(mapBounds, animation.targetPlayerId,
                    animation.cityIndex) + frame.tx * mapScale;
            float top = BattlefieldCityLayout.y(mapBounds, animation.targetPlayerId,
                    animation.cityIndex) - frame.ty * mapScale;
            batch.draw(region, x, top - height, 0f, 0f, width, height,
                    frame.a, frame.d, 0f);
        }
    }
}
