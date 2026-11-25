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
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import com.badlogic.gdx.utils.JsonValue;
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
            assertMuslimSourceDialog(renderer, ui, controls);
        });
    }

    private static void assertMuslimSourceDialog(SourceActionMenuRenderer renderer,
                                                 OriginalUiAssets ui, TurnControlRenderer controls)
            throws Exception {
        GameSetup setup = new GameSetup(0L);
        setup.setStartingBuilding(5);
        setup.toggleOpponent(0);
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.MASS_MEDIA);
        SourceActionMenuState state = new SourceActionMenuState();
        state.begin(game);
        assertEquals("religion-mus", state.getKey());
        assertEquals(450, game.getHuman().populationMillions);
        assertEquals(20, game.getMassMediaPreviewGain());
        SpriteBatch batch = new SpriteBatch();
        Texture oracle = sourceModal();
        GameState wrong = new GameState(1, 0L);
        wrong.selectAction(GameState.Action.MASS_MEDIA);
        SourceActionMenuState wrongState = new SourceActionMenuState();
        wrongState.begin(wrong);
        assertEquals("religion-chr", wrongState.getKey());
        try {
            for (int[] dimensions : new int[][]{{800, 600}, {1194, 552}}) {
                int width = dimensions[0], height = dimensions[1];
                Rectangle container = width == 800 ? new Rectangle(205, 135, 365, 275)
                        : new Rectangle(372, 106, 450, 339);
                Rectangle fit = SourceActionMenuRenderer.fit(container, new Rectangle());
                FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
                try {
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
                    byte[] actual = renderCurrent(buffer, batch, renderer, container, game, state, ui, controls);
                    assertEquals("Source presenter rounds population times 0.1", "45.000.000",
                            renderedText(renderer, "text1"));
                    assertEquals("65%", renderedText(renderer, "text2"));
                    assertEquals("+20 %", renderedText(renderer, "text3"));
                    assertWhiteFieldBounds(actual, width, height, fit,
                            290, 64, 344, 79, 297, 68, 342, 75);
                    assertWhiteFieldBounds(actual, width, height, fit,
                            310, 79, 342, 94, 322, 83, 341, 90);
                    Ink value = valueInk(actual, width, height, fit, 149.75f);
                    assertTrue("Muslim caption and value remain separated", value.top >= 150.75f);
                    assertTrue("Source value size", value.width >= 41f && value.width <= 54f);
                    assertTrue("Source value right alignment", value.right >= 336f && value.right <= 343f);
                    byte[] expected = renderOracle(buffer, batch, oracle, fit);
                    byte[] otherReligion = renderCurrent(buffer, batch, renderer, container,
                            wrong, wrongState, ui, controls);
                    save(actual, width, height, "source-mass-media-mus-" + width + ".png");
                    save(expected, width, height, "source-mass-media-mus-oracle-" + width + ".png");
                    save(otherReligion, width, height, "source-mass-media-wrong-religion-" + width + ".png");
                    double actualError = artworkError(actual, expected, width, height, fit);
                    double wrongError = artworkError(otherReligion, expected, width, height, fit);
                    assertTrue("Independent source Muslim artwork error: " + actualError, actualError < 12);
                    assertTrue("Wrong religion must fail the same oracle: " + wrongError, wrongError > 12);
                    assertTrue("Source oracle discriminates religion artwork", wrongError > actualError * 3);
                } finally { buffer.dispose(); }
            }
        } finally { oracle.dispose(); batch.dispose(); }
    }

    private static String renderedText(SourceActionMenuRenderer renderer, String name) throws Exception {
        Field fields = SourceActionMenuRenderer.class.getDeclaredField("fields");
        fields.setAccessible(true);
        for (Object field : (Object[]) fields.get(renderer)) {
            Field source = field.getClass().getDeclaredField("source"); source.setAccessible(true);
            if (!name.equals(((JsonValue) source.get(field)).getString("name"))) continue;
            Field glyph = field.getClass().getDeclaredField("glyph"); glyph.setAccessible(true);
            StringBuilder text = new StringBuilder();
            for (GlyphLayout.GlyphRun run : ((GlyphLayout) glyph.get(field)).runs)
                for (BitmapFont.Glyph character : run.glyphs) text.append((char) character.id);
            return text.toString();
        }
        throw new AssertionError("Missing rendered source field " + name);
    }

    private static Texture sourceModal() throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("source-mass-media/mus-modal.png")) {
            byte[] bytes = input.readAllBytes();
            Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
            try {
                Texture texture = new Texture(pixmap);
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                return texture;
            } finally { pixmap.dispose(); }
        }
    }

    private static byte[] renderOracle(FrameBuffer buffer, SpriteBatch batch, Texture oracle, Rectangle fit) {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(0, 0, 0, 0); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setTransformMatrix(new Matrix4()); batch.setColor(Color.WHITE);
            batch.begin(); batch.draw(oracle, fit.x, fit.y, fit.width, fit.height); batch.end();
            return ScreenUtils.getFrameBufferPixels(0, 0, buffer.getWidth(), buffer.getHeight(), true);
        } finally { if (batch.isDrawing()) batch.end(); buffer.end(); }
    }

    private static double artworkError(byte[] actual, byte[] expected, int width, int height, Rectangle fit) {
        float scale = fit.width / 365f;
        float top = height - fit.y - fit.height;
        long error = 0, channels = 0;
        // Whole source MC1 building and shadow; excludes the independent dynamic fields.
        for (int y = (int) Math.ceil(top + 35 * scale); y < top + 240 * scale; y++)
            for (int x = (int) Math.ceil(fit.x + 20 * scale); x < fit.x + 210 * scale; x++) {
                int pixel = (y * width + x) * 4;
                for (int channel = 0; channel < 3; channel++) {
                    error += Math.abs((actual[pixel + channel] & 255) - (expected[pixel + channel] & 255));
                    channels++;
                }
            }
        return error / (double) channels;
    }

    private static void assertWhiteFieldBounds(byte[] pixels, int width, int height, Rectangle fit,
            float left, float top, float right, float bottom,
            float expectedLeft, float expectedTop, float expectedRight, float expectedBottom) {
        float scale = fit.width / 365f, modalTop = height - fit.y - fit.height;
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = (int) Math.ceil(modalTop + top * scale); y < modalTop + bottom * scale; y++)
            for (int x = (int) Math.ceil(fit.x + left * scale); x < fit.x + right * scale; x++) {
                int offset = (y * width + x) * 4;
                if ((pixels[offset] & 255) > 120 && (pixels[offset + 1] & 255) > 120
                        && (pixels[offset + 2] & 255) > 120) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x + 1); maxY = Math.max(maxY, y + 1);
                }
            }
        assertTrue("Source dynamic field ink is visible", maxX >= 0);
        // Independent source stage field ink, translated from its (205,135) crop.
        float tolerance = Math.max(2f, 2f * scale);
        assertEquals("Source field left", fit.x + expectedLeft * scale, minX, tolerance);
        assertEquals("Source field top", modalTop + expectedTop * scale, minY, tolerance);
        assertEquals("Source field right", fit.x + expectedRight * scale, maxX, tolerance);
        assertEquals("Source field bottom", modalTop + expectedBottom * scale, maxY, tolerance);
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
            renderer.draw(batch, container, game, state, null,
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
