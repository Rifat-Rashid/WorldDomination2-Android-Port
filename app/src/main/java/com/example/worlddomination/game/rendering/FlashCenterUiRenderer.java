package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.assets.TechnologyArtworkAssets;

/** Draws the source Flash center-screen dialog from its original vector symbols. */
public final class FlashCenterUiRenderer implements Disposable {
    public static final String RESEARCH_FRAME = "research";
    public static final String MASS_MEDIA_FRAME = "religion";
    public static final String PROPAGANDA_FRAME = "propaganda2";
    public static final float ART_WIDTH = 365f;
    public static final float ART_HEIGHT = 275f;
    private static final int MAX_DEPTH = 20;
    private final FlashAffineTransform[] transformScratch = createTransformScratch();
    private TechnologyArtworkAssets technologyArtwork;
    private final FlashColorTransform.Composition[] colors = createColorScratch();
    private ShaderProgram colorShader;


    public void setTechnologyArtwork(TechnologyArtworkAssets artwork) {
        technologyArtwork = artwork;
    }

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
        if (RESEARCH_FRAME.equals(frameLabel)) {
            transformScratch[0].tx = com.example.worlddomination.game.ui.TechnologyScreenLayout.SOURCE_ORIGIN_X;
            transformScratch[0].ty = com.example.worlddomination.game.ui.TechnologyScreenLayout.SOURCE_ORIGIN_Y;
        }
        colors[0].identity();
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            drawLayer(batch, assets, layer, bounds, transformScratch[0],
                    religionCode, researchFrames, scaleX, scaleY, 0);
        }
        batch.setColor(1f, 1f, 1f, 1f);
        return true;
    }

    private void drawLayer(SpriteBatch batch, OriginalFlashAnimationAssets assets,
                           FlashAnimationTimeline.Layer layer, Rectangle bounds,
                           FlashAffineTransform parentTransform,
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
        FlashColorTransform.Composition tint = colors[depth + 1]
                .setProduct(colors[depth], layer.colorTransform);
        TechnologyArtworkAssets.Artwork sharp = researchFrames == null || technologyArtwork == null
                ? null : technologyArtwork.get(layer.shapeId);
        TextureRegion region = sharp == null ? assets.getShape(layer.shapeId) : sharp.region;
        if (region != null) {
            if (transform.scaleX == 0f || transform.scaleY == 0f) {
                return;
            }
            float originX = sharp == null ? assets.getShapeOriginX(layer.shapeId) : sharp.originX;
            float originY = sharp == null ? assets.getShapeOriginY(layer.shapeId) : sharp.originY;
            float sourceX = transform.tx + transform.a * originX + transform.c * originY;
            float sourceY = transform.ty + transform.b * originX + transform.d * originY;
            float x = bounds.x + sourceX * sourceScaleX;
            float top = bounds.y + bounds.height - sourceY * sourceScaleY;
            float drawScaleX = sourceScaleX * transform.scaleX;
            float drawScaleY = sourceScaleY * transform.scaleY;
            float density = sharp == null ? 1f : TechnologyArtworkAssets.RASTER_SCALE;
            float width = region.getRegionWidth() / density * Math.abs(drawScaleX);
            float height = region.getRegionHeight() / density * Math.abs(drawScaleY);
            ShaderProgram previous = batch.getShader();
            boolean transformed = !tint.isIdentity();
            if (transformed) {
                ensureColorShader();
                batch.setShader(colorShader);
                colorShader.setUniformf("u_multipliers", tint.redMultiplier,
                        tint.greenMultiplier, tint.blueMultiplier, tint.alphaMultiplier);
                colorShader.setUniformf("u_additions", tint.redAdd,
                        tint.greenAdd, tint.blueAdd, tint.alphaAdd);
            }
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(region, x, top - height, 0f, height, width, height,
                    drawScaleX < 0f ? -1f : 1f,
                    drawScaleY < 0f ? -1f : 1f, transform.rotationDegrees);
            if (transformed) batch.setShader(previous);
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
        for (FlashAnimationTimeline.Layer child : nestedFrame.layers) {
            drawLayer(batch, assets, child, bounds, transform,
                    religionCode, researchFrames, sourceScaleX,
                    sourceScaleY, depth + 1);
        }
    }

    private static FlashColorTransform.Composition[] createColorScratch() {
        FlashColorTransform.Composition[] result = new FlashColorTransform.Composition[MAX_DEPTH + 1];
        for (int i = 0; i < result.length; i++) result[i] = new FlashColorTransform.Composition();
        return result;
    }

    private void ensureColorShader() {
        if (colorShader != null) return;
        colorShader = new ShaderProgram(
                "attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0;"
                        + "uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_texCoords;"
                        + "void main(){v_color=a_color;v_color.a*=255.0/254.0;"
                        + "v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}",
                "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
                        + "varying vec4 v_color; varying vec2 v_texCoords; uniform sampler2D u_texture;"
                        + "uniform vec4 u_multipliers; uniform vec4 u_additions;"
                        + "void main(){gl_FragColor=clamp(texture2D(u_texture,v_texCoords)"
                        + "*u_multipliers+u_additions,0.0,1.0)*v_color;}");
        if (!colorShader.isCompiled()) {
            String log = colorShader.getLog(); colorShader.dispose(); colorShader = null;
            throw new IllegalStateException("Source center-screen color shader: " + log);
        }
    }

    @Override public void dispose() {
        if (colorShader != null) { colorShader.dispose(); colorShader = null; }
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
