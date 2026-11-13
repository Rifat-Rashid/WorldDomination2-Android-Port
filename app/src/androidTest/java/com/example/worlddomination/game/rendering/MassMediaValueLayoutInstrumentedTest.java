package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** GPU ink check against the original caption art and a pre-fix control. */
@RunWith(AndroidJUnit4.class)
public final class MassMediaValueLayoutInstrumentedTest {
    private static final String ART = "original/ui/action-menus/religion-chr.png";

    @Test public void valueKeepsSourceSizeAndCaptionGapAtBothWidths() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            float captionBottom = captionBottomFromPackagedArt();
            assertEquals("Source caption ends at 149.75", 149.75f, captionBottom, 0.26f);
            SourceActionMenuRenderer renderer = (SourceActionMenuRenderer)
                    SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class,
                            "sourceActionMenuRenderer").get(app);
            OriginalUiAssets ui = (OriginalUiAssets)
                    SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class,
                            "uiAssets").get(app);
            TurnControlRenderer controls = (TurnControlRenderer)
                    SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class,
                            "turnControlRenderer").get(app);
            GameState game = new GameState(1, 0L);
            assertEquals("chr", game.getHuman().getReligionCode());
            assertEquals(20, game.getMassMediaPreviewGain());
            game.selectAction(GameState.Action.MASS_MEDIA);
            SourceActionMenuState state = new SourceActionMenuState();
            state.begin(game);
            assertEquals("religion-chr", state.getKey());

            SpriteBatch batch = new SpriteBatch();
            Texture sourceArt = new Texture(Gdx.files.internal(ART));
            BitmapFont oldFont = new BitmapFont(
                    Gdx.files.internal("original/ui/fonts/resource-arial-48.fnt"));
            oldFont.setUseIntegerPositions(false);
            oldFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear,
                    Texture.TextureFilter.Linear);
            try {
                for (int[] dimensions : new int[][]{{800, 600}, {1194, 552}}) {
                    int width = dimensions[0], height = dimensions[1];
                    Rectangle container = width == 800
                            ? new Rectangle(205, 135, 365, 275)
                            : new Rectangle(372, 106, 450, 339);
                    Rectangle fit = SourceActionMenuRenderer.fit(container, new Rectangle());
                    FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888,
                            width, height, false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(
                                0, 0, width, height));
                        byte[] current = renderCurrent(buffer, batch, renderer,
                                container, game, state, ui, controls);
                        save(current, width, height,
                                "source-mass-media-value-" + width + ".png");
                        Ink value = valueInk(current, width, height, fit, captionBottom);
                        assertTrue("value appears below original caption at " + width,
                                value.top >= captionBottom + 1f);
                        assertTrue("source-sized ink width at " + width + ": " + value.width,
                                value.width >= 41f && value.width <= 54f);
                        assertTrue("source right alignment at " + width,
                                value.right >= 336f && value.right <= 343f);

                        byte[] old = renderOld(buffer, batch, sourceArt, oldFont, fit);
                        save(old, width, height,
                                "source-mass-media-value-old-control-" + width + ".png");
                        Ink oldValue = valueInk(old, width, height, fit, captionBottom);
                        assertTrue("old 22px baseline reproduces collision at " + width,
                                oldValue.top <= captionBottom);
                        assertTrue("new value is visibly narrower than old control at " + width,
                                value.width + 7f < oldValue.width);
                    } finally {
                        buffer.dispose();
                    }
                }
            } finally {
                oldFont.dispose();
                sourceArt.dispose();
                batch.dispose();
            }
        });
    }

    private static byte[] renderCurrent(FrameBuffer buffer, SpriteBatch batch,
                                        SourceActionMenuRenderer renderer, Rectangle container,
                                        GameState game, SourceActionMenuState state,
                                        OriginalUiAssets ui, TurnControlRenderer controls) {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setTransformMatrix(new Matrix4());
            batch.begin();
            renderer.draw(batch, container, game, state, (id, x, y, w, h) -> { },
                    controls, ui.propagandaFrame,
                    ui.propagandaStrengthFont);
            batch.end();
            return ScreenUtils.getFrameBufferPixels(0, 0,
                    buffer.getWidth(), buffer.getHeight(), true);
        } finally {
            if (batch.isDrawing()) batch.end();
            buffer.end();
        }
    }

    private static byte[] renderOld(FrameBuffer buffer, SpriteBatch batch,
                                    Texture art, BitmapFont font, Rectangle fit) {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setTransformMatrix(new Matrix4());
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(art, fit.x, fit.y, fit.width, fit.height);
            Matrix4 transform = new Matrix4().translate(fit.x, fit.y + fit.height, 0)
                    .scale(fit.width / 365f, fit.width / 365f, 1f);
            batch.setTransformMatrix(transform);
            font.getData().setScale(22f / 48f);
            GlyphLayout glyph = new GlyphLayout(font, "+20 %",
                    new Color(1f, 120f / 255f, 0f, 1f), 72f, Align.right, false);
            font.draw(batch, glyph, 267.55f, -144.2f);
            batch.setTransformMatrix(new Matrix4());
            batch.end();
            return ScreenUtils.getFrameBufferPixels(0, 0,
                    buffer.getWidth(), buffer.getHeight(), true);
        } finally {
            if (batch.isDrawing()) batch.end();
            buffer.end();
        }
    }

    private static float captionBottomFromPackagedArt() {
        Pixmap art = new Pixmap(Gdx.files.internal(ART));
        try {
            int bottom = -1;
            // Four-times exported source image, within the caption's second line.
            for (int y = 520; y < 640; y++) {
                for (int x = 900; x < 1210; x++) {
                    if (orange(art.getPixel(x, y))) bottom = Math.max(bottom, y);
                }
            }
            assertTrue("Original caption ink found", bottom >= 0);
            return bottom / 4f;
        } finally { art.dispose(); }
    }

    private static Ink valueInk(byte[] pixels, int width, int height,
                                Rectangle fit, float captionBottom) {
        float scale = fit.width / 365f;
        float top = height - (fit.y + fit.height);
        int left = width, right = -1, first = height;
        int xStart = Math.max(0, Math.round(fit.x + 255f * scale));
        int xEnd = Math.min(width, Math.round(fit.x + 344f * scale));
        int yStart = Math.max(0, Math.round(top + 135f * scale));
        int yEnd = Math.min(height, Math.round(top + 165f * scale));
        for (int y = yStart; y < yEnd; y++) {
            for (int x = xStart; x < xEnd; x++) {
                int index = (y * width + x) * 4;
                int red = pixels[index] & 255;
                int green = pixels[index + 1] & 255;
                int blue = pixels[index + 2] & 255;
                if (red > 150 && green > 50 && green < 180 && blue < 90
                        && red > green * 1.4f) {
                    // The caption art ends before x=301. Its right-hand strip
                    // therefore isolates the earliest dynamic-value pixel.
                    if (x >= fit.x + 301f * scale) first = Math.min(first, y);
                    if (y >= top + (captionBottom + 1f) * scale) {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
                }
            }
        }
        assertTrue("Mass-media value ink found", right >= left && first < height);
        return new Ink((first - top) / scale,
                (right - fit.x) / scale, (right - left + 1) / scale);
    }

    private static boolean orange(int rgba) {
        int red = rgba >>> 24 & 255;
        int green = rgba >>> 16 & 255;
        int blue = rgba >>> 8 & 255;
        return red > 150 && green > 50 && green < 180 && blue < 90
                && red > green * 1.4f;
    }

    private static void save(byte[] pixels, int width, int height, String name) {
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(pixels);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image);
        } finally { image.dispose(); }
    }

    private static final class Ink {
        final float top, right, width;
        Ink(float top, float right, float width) {
            this.top = top;
            this.right = right;
            this.width = width;
        }
    }
}
