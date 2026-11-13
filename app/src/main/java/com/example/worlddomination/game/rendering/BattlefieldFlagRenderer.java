package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.BattlefieldFlagState;

/** Source flag_pole clips, below cities, with color applied only to tintMC. */
public final class BattlefieldFlagRenderer implements Disposable {
    private static final int MAX_DEPTH = 8;
    private final OriginalFlashAnimationAssets assets;
    private final FlashAffineTransform[] transforms = new FlashAffineTransform[MAX_DEPTH + 1];
    private final FlashColorTransform.Composition[] colors =
            new FlashColorTransform.Composition[MAX_DEPTH + 1];
    private final FlashColorTransform[] playerColors = new FlashColorTransform[6];
    private ShaderProgram shader;
    private int sourceTicks;

    public BattlefieldFlagRenderer(OriginalFlashAnimationAssets assets) {
        this.assets = assets;
        for (int i = 0; i <= MAX_DEPTH; i++) {
            transforms[i] = new FlashAffineTransform();
            colors[i] = new FlashColorTransform.Composition();
        }
        for (int i = 1; i <= 5; i++) playerColors[i] = FlashColorTransform.forPlayerColor(i);
    }

    public void reset() { sourceTicks = 0; }
    public void tick() { sourceTicks = BattlefieldFlagState.animationFrame(sourceTicks + 1L); }

    public void draw(SpriteBatch batch, Rectangle mapBounds, GameState game) {
        float scale = BattlefieldCityLayout.sourceScale(mapBounds);
        batch.setColor(1, 1, 1, 1);
        for (int island = 0; island < 5; island++) {
            float x = BattlefieldCityLayout.sourceX(mapBounds,
                    BattlefieldFlagState.sourceX(island), island);
            float y = BattlefieldCityLayout.sourceY(mapBounds,
                    BattlefieldFlagState.sourceY(island));
            if (!assets.areBattlefieldFlagsLoaded()) continue;
            int color = BattlefieldFlagState.color(game, island);
            if (color == 0) continue;
            FlashAnimationTimeline timeline = assets.getTimeline("flag_pole");
            if (timeline == null) continue;
            FlashAnimationTimeline.Frame frame = timeline.getFrameByLabel(
                    BattlefieldFlagState.religion(game, island));
            if (frame == null) continue;
            transforms[0].setIdentity();
            colors[0].identity();
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                drawLayer(batch, layer, x, y, scale, color, 0);
            }
        }
        batch.setColor(1, 1, 1, 1);
    }

    private void drawLayer(SpriteBatch batch, FlashAnimationTimeline.Layer layer,
                           float x, float y, float scale, int color, int depth) {
        if (depth >= MAX_DEPTH || layer.alpha <= 0) return;
        FlashAffineTransform transform = transforms[depth + 1];
        transform.setProduct(transforms[depth], layer.a, layer.b, layer.c, layer.d,
                layer.tx, layer.ty);
        FlashColorTransform tint = "tintMC".equals(layer.name)
                ? playerColors[color] : layer.colorTransform;
        colors[depth + 1].setProduct(colors[depth], tint);
        TextureRegion region = assets.getShape(layer.shapeId);
        if (region != null) {
            float sourceX = transform.tx + transform.a * assets.getShapeOriginX(layer.shapeId)
                    + transform.c * assets.getShapeOriginY(layer.shapeId);
            float sourceY = transform.ty + transform.b * assets.getShapeOriginX(layer.shapeId)
                    + transform.d * assets.getShapeOriginY(layer.shapeId);
            float width = region.getRegionWidth() * scale * Math.abs(transform.scaleX);
            float height = region.getRegionHeight() * scale * Math.abs(transform.scaleY);
            ShaderProgram previous = batch.getShader();
            ensureShader();
            batch.setShader(shader);
            FlashColorTransform.Composition c = colors[depth + 1];
            shader.setUniformf("u_multipliers", c.redMultiplier, c.greenMultiplier,
                    c.blueMultiplier, c.alphaMultiplier);
            shader.setUniformf("u_additions", c.redAdd, c.greenAdd, c.blueAdd, c.alphaAdd);
            batch.draw(region, x + sourceX * scale, y - sourceY * scale - height,
                    0, height, width, height, transform.scaleX < 0 ? -1 : 1,
                    transform.scaleY < 0 ? -1 : 1, transform.rotationDegrees);
            // Restoring also flushes the previous uniforms before another leaf.
            batch.setShader(previous);
            return;
        }
        FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
        if (nested == null) return;
        FlashAnimationTimeline.Frame frame = nested.getFrame(
                nested.getFrameCount() == BattlefieldFlagState.ANIMATION_FRAME_COUNT ? sourceTicks : 0);
        if (frame == null) return;
        for (FlashAnimationTimeline.Layer child : frame.layers) {
            drawLayer(batch, child, x, y, scale, color, depth + 1);
        }
    }

    private void ensureShader() {
        if (shader != null) return;
        shader = new ShaderProgram(
                "attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0;"
                        + "uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_texCoords;"
                        + "void main(){v_color=a_color;v_color.a*=255.0/254.0;"
                        + "v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}",
                "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
                        + "varying vec4 v_color; varying vec2 v_texCoords; uniform sampler2D u_texture;"
                        + "uniform vec4 u_multipliers; uniform vec4 u_additions;"
                        + "void main(){gl_FragColor=clamp(texture2D(u_texture,v_texCoords)"
                        + "*u_multipliers+u_additions,0.0,1.0)*v_color;}");
        if (!shader.isCompiled()) throw new IllegalStateException(shader.getLog());
    }

    @Override public void dispose() {
        if (shader != null) shader.dispose();
    }
}
