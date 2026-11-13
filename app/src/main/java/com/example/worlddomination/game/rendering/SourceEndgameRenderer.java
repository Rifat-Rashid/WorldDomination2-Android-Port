package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.example.worlddomination.game.assets.OriginalEndgameAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.EndgameScreenLayout;
import com.example.worlddomination.game.ui.MatchOutcomePresentation;
import com.example.worlddomination.game.ui.MatchOutcomeSequence;

/** Draws the authored 800×600 Flash endgame timeline and its live score fields. */
public final class SourceEndgameRenderer implements Disposable {
    private static final int MAX_DEPTH = 16;
    private static final Color SCORE_GREEN = new Color(0f, 0.8f, 0f, 1f);
    private final OriginalEndgameAssets assets;
    private final EndgameScreenLayout layout = new EndgameScreenLayout();
    private final FlashAffineTransform[] transforms = new FlashAffineTransform[MAX_DEPTH + 1];
    private final FlashColorTransform.Composition[] colors =
            new FlashColorTransform.Composition[MAX_DEPTH + 1];
    private final GlyphLayout bodyLayout = new GlyphLayout();
    private final GlyphLayout scoreLayout = new GlyphLayout();
    private final GlyphLayout bonusLayout = new GlyphLayout();
    private final GlyphLayout newGameLayout = new GlyphLayout();
    private final GlyphLayout websiteLayout = new GlyphLayout();
    private ShaderProgram colorShader;
    private String priorBody = "", priorScore = "", priorBonus = "";
    private String priorSatellite = "", priorFlag = "", priorBest = "";
    private boolean priorComplete;
    private float priorScale = -1f;

    public SourceEndgameRenderer(OriginalEndgameAssets assets) {
        this.assets = assets;
        for (int i = 0; i < transforms.length; i++) {
            transforms[i] = new FlashAffineTransform();
            colors[i] = new FlashColorTransform.Composition();
        }
    }

    public EndgameScreenLayout layout() { return layout; }
    public boolean isReady() { return assets.isReady(); }
    public void start() { assets.start(); }
    /** Upload on the GL thread before beginning the caller's SpriteBatch. */
    public boolean update() { return assets.update(); }

    /** Caller owns batch, font, and content bounds; this method neither begins nor ends a batch. */
    public boolean draw(SpriteBatch batch, Rectangle contentBounds,
                        MatchOutcomeSequence sequence, String personalBestText,
                        BitmapFont font) {
        if (!assets.isReady()) return false;
        layout.fit(contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height);
        String frameLabel = frameLabel(sequence);
        if (frameLabel == null) return false;
        FlashAnimationTimeline timeline = assets.getTimeline();
        FlashAnimationTimeline.Frame frame = timeline.getFrameByLabel(frameLabel);
        if (frame == null) return false;
        transforms[0].setIdentity();
        transforms[0].tx = EndgameScreenLayout.ROOT_OFFSET_X;
        colors[0].identity();
        for (FlashAnimationTimeline.Layer layer : frame.layers)
            drawLayer(batch, layer, 0);
        batch.setColor(Color.WHITE);
        if (sequence.isScoreStarted()) drawLiveText(batch, font, sequence, personalBestText);
        return true;
    }

    private void drawLayer(SpriteBatch batch, FlashAnimationTimeline.Layer layer, int depth) {
        if (depth >= MAX_DEPTH || layer.alpha <= 0f || isHiddenLayer(layer)) return;
        FlashAffineTransform transform = transforms[depth + 1];
        transform.setProduct(transforms[depth], layer.a, layer.b, layer.c, layer.d,
                layer.tx, layer.ty);
        FlashColorTransform.Composition tint = colors[depth + 1]
                .setProduct(colors[depth], layer.colorTransform);
        TextureRegion region = assets.getShape(layer.shapeId);
        if (region != null) {
            if (transform.scaleX == 0f || transform.scaleY == 0f) return;
            float originX = assets.getOriginX(layer.shapeId);
            float originY = assets.getOriginY(layer.shapeId);
            float sourceX = transform.tx + transform.a * originX + transform.c * originY;
            float sourceY = transform.ty + transform.b * originX + transform.d * originY;
            float scale = layout.scale();
            float x = layout.left() + sourceX * scale;
            float top = layout.bottom() + layout.height() - sourceY * scale;
            float width = region.getRegionWidth() * scale * Math.abs(transform.scaleX);
            float height = region.getRegionHeight() * scale * Math.abs(transform.scaleY);
            ShaderProgram previous = batch.getShader();
            if (!tint.isIdentity()) {
                ensureShader();
                batch.setShader(colorShader);
                colorShader.setUniformf("u_multipliers", tint.redMultiplier,
                        tint.greenMultiplier, tint.blueMultiplier, tint.alphaMultiplier);
                colorShader.setUniformf("u_additions", tint.redAdd, tint.greenAdd,
                        tint.blueAdd, tint.alphaAdd);
            }
            batch.setColor(Color.WHITE);
            batch.draw(region, x, top - height, 0, height, width, height,
                    transform.scaleX < 0 ? -1 : 1, transform.scaleY < 0 ? -1 : 1,
                    transform.rotationDegrees);
            if (!tint.isIdentity()) batch.setShader(previous);
            return;
        }
        FlashAnimationTimeline child = assets.getNested(layer.shapeId);
        if (child == null) return;
        // The source initializes endgame controls and bioMCs at their stopped
        // first frame (4120, 4586, 4486); idle score presentation never cycles them.
        FlashAnimationTimeline.Frame childFrame = child.getFrame(0);
        if (childFrame != null) for (FlashAnimationTimeline.Layer nested : childFrame.layers)
            drawLayer(batch, nested, depth + 1);
    }

    private static boolean isHiddenLayer(FlashAnimationTimeline.Layer layer) {
        return layer.shapeId == 5897 || layer.shapeId == 5898 || layer.shapeId == 5899
                || layer.shapeId == 4627 || "demo_MC".equals(layer.name);
    }

    private void drawLiveText(SpriteBatch batch, BitmapFont font,
                              MatchOutcomeSequence sequence, String personalBestText) {
        float scale = layout.scale();
        String body = MatchOutcomePresentation.message(sequence.getOutcome());
        String score = sequence.isScoreVisible() ? sequence.getScoreText() : "";
        String satellite = sequence.getSatelliteBonusText();
        String flag = sequence.getFlagBonusText();
        String best = personalBestText == null ? "" : personalBestText;
        boolean complete = sequence.isComplete();
        boolean bonusChanged = !satellite.equals(priorSatellite) || !flag.equals(priorFlag)
                || !best.equals(priorBest) || complete != priorComplete;
        if (bonusChanged) {
            priorBonus = satellite + "\n" + flag + (complete ? "\n" + best : "");
            priorSatellite = satellite;
            priorFlag = flag;
            priorBest = best;
            priorComplete = complete;
        }
        float oldScale = font.getScaleX();
        if (scale != priorScale || !body.equals(priorBody)) {
            font.getData().setScale(11f / 18f * scale);
            bodyLayout.setText(font, body, Color.GREEN, 369.4f * scale, Align.center, true);
            priorBody = body;
        }
        if (scale != priorScale || !score.equals(priorScore)) {
            font.getData().setScale(30f / 18f * scale);
            scoreLayout.setText(font, score, SCORE_GREEN, 369.4f * scale, Align.center, false);
            priorScore = score;
        }
        if (scale != priorScale || bonusChanged) {
            font.getData().setScale(11f / 18f * scale);
            bonusLayout.setText(font, priorBonus, Color.GREEN, 289.55f * scale, Align.center, true);
            newGameLayout.setText(font, "New Game", Color.WHITE, 135f * scale,
                    Align.center, false);
            websiteLayout.setText(font, "Website", Color.WHITE, 135f * scale,
                    Align.center, false);
        }
        priorScale = scale;
        font.setColor(Color.WHITE);
        font.getData().setScale(11f / 18f * scale);
        font.draw(batch, bodyLayout, layout.x(53f), layout.y(206f));
        if (sequence.isScoreVisible()) {
            font.getData().setScale(30f / 18f * scale);
            font.draw(batch, scoreLayout, layout.x(53f), layout.y(368f));
            font.getData().setScale(11f / 18f * scale);
        }
        font.draw(batch, bonusLayout, layout.x(463f), layout.y(371f));
        font.setColor(Color.WHITE);
        font.draw(batch, newGameLayout, layout.x(493f), layout.y(458f));
        font.draw(batch, websiteLayout, layout.x(635f), layout.y(458f));
        font.getData().setScale(oldScale);
        font.setColor(Color.WHITE);
    }

    private static String frameLabel(MatchOutcomeSequence sequence) {
        GameState.MatchStatus outcome = sequence.getOutcome();
        if (outcome != GameState.MatchStatus.VICTORY && outcome != GameState.MatchStatus.DEFEAT)
            return null;
        if (outcome == GameState.MatchStatus.VICTORY)
            return sequence.isScoreStarted() ? "win2" : "win";
        return sequence.isScoreStarted() ? "lost2" : "lost";
    }

    private void ensureShader() {
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
            throw new IllegalStateException("Source endgame shader: " + log);
        }
    }

    @Override public void dispose() {
        if (colorShader != null) { colorShader.dispose(); colorShader = null; }
    }
}
