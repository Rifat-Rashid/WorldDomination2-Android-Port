package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;

/** Draws the source Flash center-screen dialog from its original vector symbols. */
public final class FlashCenterUiRenderer {
    public static final String RESEARCH_FRAME = "research";
    public static final String MASS_MEDIA_FRAME = "religion";
    public static final float ART_WIDTH = 365f;
    public static final float ART_HEIGHT = 275f;
    private static final int MAX_DEPTH = 20;
    private final FlashAffineTransform[] transformScratch = createTransformScratch();

    public boolean draw(SpriteBatch batch, OriginalFlashAnimationAssets assets,
                        Rectangle bounds, String frameLabel, String religionCode,
                        int[] researchFrames) {
        FlashAnimationTimeline centerScreen = assets.getCenterUiTimeline();
        if (centerScreen == null || !assets.areCenterUiAssetsLoaded()) {
            return false;
        }
        FlashAnimationTimeline.Frame frame = centerScreen.getFrameByLabel(frameLabel);
        if (frame == null) {
            return false;
        }
        float scaleX = bounds.width / ART_WIDTH;
        float scaleY = bounds.height / ART_HEIGHT;
        transformScratch[0].setIdentity();
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            drawLayer(batch, assets, layer, bounds, transformScratch[0], 1f,
                    religionCode, researchFrames, scaleX, scaleY, 0);
        }
        batch.setColor(1f, 1f, 1f, 1f);
        return true;
    }

    private void drawLayer(SpriteBatch batch, OriginalFlashAnimationAssets assets,
                           FlashAnimationTimeline.Layer layer, Rectangle bounds,
                           FlashAffineTransform parentTransform, float parentAlpha,
                           String religionCode, int[] researchFrames, float sourceScaleX,
                           float sourceScaleY, int depth) {
        if (depth >= MAX_DEPTH || layer.alpha <= 0f) {
            return;
        }
        if (isDynamicTextLayer(layer.name)) {
            return;
        }
        FlashAffineTransform transform = transformScratch[depth + 1];
        transform.setProduct(parentTransform, layer.a, layer.b, layer.c, layer.d,
                layer.tx, layer.ty);
        TextureRegion region = assets.getShape(layer.shapeId);
        if (region != null) {
            if (transform.scaleX == 0f || transform.scaleY == 0f) {
                return;
            }
            float originX = assets.getShapeOriginX(layer.shapeId);
            float originY = assets.getShapeOriginY(layer.shapeId);
            float sourceX = transform.tx + transform.a * originX + transform.c * originY;
            float sourceY = transform.ty + transform.b * originX + transform.d * originY;
            float x = bounds.x + sourceX * sourceScaleX;
            float top = bounds.y + bounds.height - sourceY * sourceScaleY;
            float drawScaleX = sourceScaleX * transform.scaleX;
            float drawScaleY = sourceScaleY * transform.scaleY;
            float width = region.getRegionWidth() * Math.abs(drawScaleX);
            float height = region.getRegionHeight() * Math.abs(drawScaleY);
            batch.setColor(1f, 1f, 1f, Math.max(0f,
                    Math.min(1f, parentAlpha * layer.alpha)));
            batch.draw(region, x, top - height, 0f, height, width, height,
                    drawScaleX < 0f ? -1f : 1f,
                    drawScaleY < 0f ? -1f : 1f, transform.rotationDegrees);
            return;
        }

        FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
        if (nested == null) {
            return;
        }
        FlashAnimationTimeline.Frame nestedFrame = frameForLayer(
                nested, layer.name, religionCode, researchFrames);
        if (nestedFrame == null) {
            return;
        }
        float childAlpha = parentAlpha * layer.alpha;
        for (FlashAnimationTimeline.Layer child : nestedFrame.layers) {
            drawLayer(batch, assets, child, bounds, transform,
                    childAlpha, religionCode, researchFrames, sourceScaleX,
                    sourceScaleY, depth + 1);
        }
    }

    private static FlashAffineTransform[] createTransformScratch() {
        FlashAffineTransform[] transforms = new FlashAffineTransform[MAX_DEPTH + 1];
        for (int index = 0; index < transforms.length; index++) {
            transforms[index] = new FlashAffineTransform();
        }
        return transforms;
    }

    private static FlashAnimationTimeline.Frame frameForLayer(
            FlashAnimationTimeline timeline, String layerName, String religionCode,
            int[] researchFrames) {
        if (isResearchLayer(layerName) && researchFrames != null) {
            int slot = layerName.charAt(4) - '1';
            if (slot >= 0 && slot < researchFrames.length) {
                int index = researchFrames[slot];
                if (index < 0) {
                    FlashAnimationTimeline.Frame completed =
                            timeline.getFrameByLabel("allInvented");
                    if (completed != null) {
                        return completed;
                    }
                } else {
                    FlashAnimationTimeline.Frame selected = timeline.getFrame(index);
                    if (selected != null) {
                        return selected;
                    }
                }
            }
        }
        if ("MC1".equals(layerName) && religionCode != null) {
            FlashAnimationTimeline.Frame religion = timeline.getFrameByLabel(religionCode);
            if (religion != null) {
                return religion;
            }
        }
        return timeline.getFrame(0);
    }

    private static boolean isResearchLayer(String name) {
        return name != null && name.length() == 5
                && name.startsWith("tech") && name.charAt(4) >= '1' && name.charAt(4) <= '7';
    }

    private static boolean isDynamicTextLayer(String name) {
        return name != null && (name.startsWith("text") || name.startsWith("txt"));
    }
}
