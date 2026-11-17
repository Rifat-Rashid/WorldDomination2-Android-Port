package com.example.worlddomination.game.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.InformationPanelLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Shape 4450 artwork and unclipped information content in the live World. */
@RunWith(AndroidJUnit4.class)
public final class SourceInformationPanelInstrumentedTest {
    @Test public void uneditedChromeMatchesIndependentSourceAtEffectiveNativeSize() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            Texture packaged = packaged();
            Texture original = decodedOriginal();
            Pixmap solid = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            solid.drawPixel(0, 0, 0x1c2730ff);
            Texture missingArt = new Texture(solid);
            solid.dispose();
            SourceInformationPanel renderer = new SourceInformationPanel(new TextureRegion(packaged));
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, 800, 600, false);
            Rectangle panel = new Rectangle(40, 40, 72, 40.8f);
            try {
                assertEquals(180, packaged.getWidth());
                assertEquals(102, packaged.getHeight());
                // Source 20-pixel caps become 8 logical points. The 2.5x
                // projection restores exactly 180x102 physical source pixels.
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 320, 240));
                fb.begin(); clear(); batch.begin(); batch.setColor(.2f, .4f, .6f, .8f);
                float saved = batch.getPackedColor();
                renderer.draw(batch, panel);
                assertEquals("caller color restored", saved, batch.getPackedColor(), 0f);
                batch.end();
                byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, true);
                fb.end();
                fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
                batch.draw(new TextureRegion(original, 113, 455, 180, 102),
                        panel.x, panel.y, panel.width, panel.height);
                batch.end();
                byte[] expected = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, true);
                fb.end();
                fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
                batch.draw(missingArt, panel.x, panel.y, panel.width, panel.height);
                batch.end();
                byte[] wrong = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, true);
                fb.end();
                evidence("source-information-panel-native-actual.png", actual, 800, 600);
                evidence("source-information-panel-native-expected.png", expected, 800, 600);
                evidence("source-information-panel-native-missing-art.png", wrong, 800, 600);
                int compared = 0, wrongDifferences = 0;
                for (int sourceY = 0; sourceY < 102; sourceY++)
                    for (int sourceX = 0; sourceX < 180; sourceX++) {
                        // Only two authored title rectangles were replaced:
                        // half-open (60,0,130,10) and (60,91,130,102).
                        if (sourceX >= 60 && sourceX < 130
                                && (sourceY < 10 || sourceY >= 91)) continue;
                        int x = 100 + sourceX, y = 100 + 101 - sourceY;
                        int k = ((599 - y) * 800 + x) * 4;
                        for (int channel = 0; channel < 4; channel++)
                            assertEquals("source information chrome " + sourceX + "," + sourceY
                                    + " channel " + channel,
                                    expected[k + channel], actual[k + channel]);
                        if (expected[k] != wrong[k] || expected[k + 1] != wrong[k + 1]
                                || expected[k + 2] != wrong[k + 2]) wrongDifferences++;
                        compared++;
                    }
                assertEquals(180 * 102 - 70 * 21, compared);
                assertTrue("flat fill omits original artwork", wrongDifferences > 1000);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                fb.dispose();
                batch.setProjectionMatrix(oldProjection);
                batch.setPackedColor(oldColor);
                missingArt.dispose();
                original.dispose();
                packaged.dispose();
            }
        });
    }

    @Test public void responsiveChromeAndWorldReplacementStayInsideInformationPanel() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            Texture packaged = packaged();
            SourceInformationPanel renderer = new SourceInformationPanel(new TextureRegion(packaged));
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            try {
                for (int[] size : new int[][]{{800, 600}, {1194, 552}}) {
                    int w = size[0], h = size[1];
                    Rectangle panel = new Rectangle(4, 4, (w - 8) * .215f, 82);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                        fb.begin(); clear(); batch.begin(); batch.setColor(.3f, .5f, .7f, .9f);
                        float saved = batch.getPackedColor();
                        renderer.draw(batch, panel);
                        assertEquals(saved, batch.getPackedColor(), 0f);
                        batch.end();
                        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        evidence("source-information-panel-responsive-" + w + "x" + h + ".png",
                                pixels, w, h);
                        for (int y = 13; y < 77; y++)
                            for (int x = 13; x < panel.x + panel.width - 9; x++)
                                assertEquals("information interior coverage", 255,
                                        pixels[((h - 1 - y) * w + x) * 4 + 3] & 255);
                        Rectangle tiny = new Rectangle(w - 40, 20, 12, 12);
                        fb.begin(); clear(); batch.begin(); batch.setColor(.3f, .5f, .7f, .9f);
                        float tinyColor = batch.getPackedColor();
                        renderer.draw(batch, tiny);
                        assertEquals("small resize restores caller color", tinyColor,
                                batch.getPackedColor(), 0f);
                        batch.end();
                        byte[] small = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        for (int y = 24; y < 28; y++) for (int x = w - 36; x < w - 32; x++)
                            assertEquals("small information center painted", 255,
                                    small[((h - 1 - y) * w + x) * 4 + 3] & 255);
                    } finally { fb.dispose(); }
                }

                GameState game = sampleGame();
                field("game").set(app, game);
                field("modalOpen").setBoolean(app, false);
                ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
                field("skipNextFrameDelta").setBoolean(app, true);
                app.render();
                Rectangle panel = (Rectangle) field("infoPanel").get(app);
                assertEquals(82f, panel.height, 0f);
                int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
                byte[] world = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                evidence("source-information-panel-world.png", world, w, h);
                Field rendererField = field("informationPanelRenderer");
                SourceInformationPanel production = (SourceInformationPanel) rendererField.get(app);
                assertNotNull(production);
                Pixmap empty = new Pixmap(180, 102, Pixmap.Format.RGBA8888);
                empty.setColor(0, 0, 0, 0);
                empty.fill();
                Texture transparent = new Texture(empty);
                empty.dispose();
                byte[] missing;
                try {
                    rendererField.set(app, new SourceInformationPanel(new TextureRegion(transparent)));
                    field("skipNextFrameDelta").setBoolean(app, true);
                    app.render();
                    missing = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                } finally {
                    rendererField.set(app, production);
                    transparent.dispose();
                }
                evidence("source-information-panel-world-missing.png", missing, w, h);
                Viewport viewport = (Viewport) field("viewport").get(app);
                Vector2 lower = viewport.project(new Vector2(panel.x, panel.y));
                Vector2 upper = viewport.project(new Vector2(
                        panel.x + panel.width, panel.y + panel.height));
                int left = (int)Math.floor(lower.x), right = (int)Math.ceil(upper.x);
                int bottom = (int)Math.floor(lower.y), top = (int)Math.ceil(upper.y);
                int borderChanges = 0, outsideChanges = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int k = ((h - 1 - y) * w + x) * 4;
                    if (world[k] == missing[k] && world[k + 1] == missing[k + 1]
                            && world[k + 2] == missing[k + 2] && world[k + 3] == missing[k + 3])
                        continue;
                    if (x < left || x >= right || y < bottom || y >= top) outsideChanges++;
                    else if (x < left + 12 || x >= right - 12
                            || y < bottom + 12 || y >= top - 12) borderChanges++;
                }
                assertTrue("removing chrome changes information border", borderChanges > 0);
                assertEquals("only information panel changes", 0, outsideChanges);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                batch.setProjectionMatrix(oldProjection);
                batch.setPackedColor(oldColor);
                packaged.dispose();
            }
        });
    }

    @Test public void fiveRowsAndWideFundsPaintFullyWithoutClippingAtBothSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = sampleGame();
            game.getHuman().turnMoneyMillions = Integer.MAX_VALUE;
            field("game").set(app, game);
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            ShapeRenderer shapes = (ShapeRenderer) field("shapes").get(app);
            BitmapFont font = (BitmapFont) field("font").get(app);
            Matrix4 oldBatchProjection = new Matrix4(batch.getProjectionMatrix());
            Matrix4 oldShapeProjection = new Matrix4(shapes.getProjectionMatrix());
            float oldBatchColor = batch.getPackedColor();
            Color oldFontColor = new Color(font.getColor());
            Rectangle panel = (Rectangle) field("infoPanel").get(app);
            Rectangle oldPanel = new Rectangle(panel);
            Method text = method("drawStatistics");
            Method bars = method("drawStatisticsBars");
            try {
                for (int[] size : new int[][]{{800, 600}, {1194, 552}}) {
                    int w = size[0], h = size[1];
                    panel.set(50, 100, (w - 8) * .215f, 82);
                    Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, w, h);
                    batch.setProjectionMatrix(projection);
                    shapes.setProjectionMatrix(projection);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        Texture chromeTexture = packaged();
                        try {
                            SourceInformationPanel chrome = new SourceInformationPanel(
                                    new TextureRegion(chromeTexture));
                            for (int funds : new int[]{65, Integer.MAX_VALUE}) {
                                game.getHuman().turnMoneyMillions = funds;
                                fb.begin(); clear(); batch.begin(); text.invoke(app); batch.end();
                                byte[] textOnly = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                                fb.end();
                                assertTextRows(textOnly, panel, w, h);
                                fb.begin(); clear();
                                shapes.begin(ShapeRenderer.ShapeType.Filled);
                                bars.invoke(app);
                                shapes.end();
                                byte[] barsOnly = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                                fb.end();
                                assertContentInsideSourceWell(textOnly, barsOnly, panel, w, h);
                                fb.begin(); clear(); batch.begin();
                                chrome.draw(batch, panel);
                                text.invoke(app);
                                batch.end();
                                shapes.begin(ShapeRenderer.ShapeType.Filled);
                                bars.invoke(app);
                                shapes.end();
                                byte[] composite = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                                fb.end();
                                evidence("source-information-panel-text-bars-" + w + "x" + h
                                        + (funds == 65 ? "-normal.png" : "-wide.png"),
                                        composite, w, h);
                                float barX = panel.x + panel.width * .70f;
                                for (int row = 1; row < 5; row++) {
                                    int x = (int)barX;
                                    int y = (int)InformationPanelLayout.rowCenter(panel, row);
                                    assertEquals("all four statistic bars visible", 255,
                                            barsOnly[((h - 1 - y) * w + x) * 4 + 3] & 255);
                                }
                            }
                        } finally {
                            chromeTexture.dispose();
                        }
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    } finally { fb.dispose(); }
                }
            } finally {
                panel.set(oldPanel);
                batch.setProjectionMatrix(oldBatchProjection);
                shapes.setProjectionMatrix(oldShapeProjection);
                batch.setPackedColor(oldBatchColor);
                font.setColor(oldFontColor);
            }
        });
    }

    private static void assertTextRows(byte[] image, Rectangle panel, int w, int h) {
        int[] ink = new int[5];
        int fundsLabel = 0, fundsValue = 0;
        float rowHeight = InformationPanelLayout.rowHeight(panel);
        float fundsX = panel.x + 9f + (panel.width - 18f) * .43f;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if ((image[((h - 1 - y) * w + x) * 4 + 3] & 255) == 0) continue;
            assertTrue("unclipped glyph inside information panel x=" + x,
                    x >= panel.x + 8 && x < panel.x + panel.width - 8);
            assertTrue("unclipped glyph inside information panel y=" + y,
                    y >= panel.y + 8 && y < panel.y + panel.height - 8);
            int row = (int)((panel.y + panel.height - 8f - y) / rowHeight);
            assertTrue("glyph belongs to one of five rows", row >= 0 && row < 5);
            ink[row]++;
            if (row == 0) {
                if (x < fundsX) fundsLabel++;
                else fundsValue++;
            }
        }
        for (int row = 0; row < 5; row++)
            assertTrue("full visible information row " + row, ink[row] > 10);
        assertTrue("FUNDS label visible", fundsLabel > 5);
        assertTrue("wide funds value visible", fundsValue > 20);
    }

    private static void assertContentInsideSourceWell(byte[] text, byte[] bars,
            Rectangle panel, int w, int h) {
        int textPixels = 0, barPixels = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int k = ((h - 1 - y) * w + x) * 4 + 3;
            boolean glyph = (text[k] & 255) != 0;
            boolean bar = (bars[k] & 255) != 0;
            if (!glyph && !bar) continue;
            if (glyph) textPixels++;
            if (bar) barPixels++;
            assertFalse("glyph overlaps statistic bar at " + x + "," + y, glyph && bar);
            // Inverse of the source 20-pixel caps drawn at 8 logical points.
            // Source x20..160 and y20..82 are the recessed rectangular well;
            // all four rounded frame curves lie outside this region.
            float offsetX = x + .5f - panel.x;
            float offsetTop = panel.y + panel.height - (y + .5f);
            float sourceX = sourceCoordinate(offsetX, panel.width, 140f);
            float sourceY = sourceCoordinate(offsetTop, panel.height, 62f);
            assertTrue("content escapes source well at " + x + "," + y
                            + " source " + sourceX + "," + sourceY,
                    sourceX >= 20f && sourceX < 160f && sourceY >= 20f && sourceY < 82f);
        }
        assertTrue("five-row glyph ink present", textPixels > 100);
        assertTrue("statistic bar pixels present", barPixels > 100);
    }

    private static float sourceCoordinate(float destination, float span, float sourceMiddle) {
        if (destination < 8f) return destination * 20f / 8f;
        if (destination < span - 8f)
            return 20f + (destination - 8f) * sourceMiddle / (span - 16f);
        return 20f + sourceMiddle + (destination - (span - 8f)) * 20f / 8f;
    }

    private static Texture packaged() {
        Texture result = new Texture(Gdx.files.internal("original/ui/information-panel.png"));
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return result;
    }

    private static Texture decodedOriginal() throws Exception {
        byte[] xml;
        try (InputStream zipped = new GZIPInputStream(Gdx.files.internal(
                "original/runtime/shapes/4450.svgz").read());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = zipped.read(buffer)) != -1) out.write(buffer, 0, count);
            xml = out.toByteArray();
        }
        Matcher image = Pattern.compile("data:image/webp;base64,([^\"]+)")
                .matcher(new String(xml, StandardCharsets.UTF_8));
        assertTrue("source 4450 WebP", image.find());
        String encoded = image.group(1);
        assertFalse("one bitmap fill", image.find());
        byte[] webp = Base64.decode(encoded, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(webp, 0, webp.length);
        assertNotNull(bitmap);
        assertEquals(297, bitmap.getWidth());
        assertEquals(768, bitmap.getHeight());
        Pixmap pixels = new Pixmap(297, 768, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 768; y++) for (int x = 0; x < 297; x++) {
            int argb = bitmap.getPixel(x, y);
            pixels.drawPixel(x, y, (argb << 8) | (argb >>> 24));
        }
        bitmap.recycle();
        Texture result = new Texture(pixels);
        pixels.dispose();
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return result;
    }

    private static GameState sampleGame() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }

    private static Method method(String name) throws Exception {
        Method result = WorldDominationGame.class.getDeclaredMethod(name);
        result.setAccessible(true);
        return result;
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void clear() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private static void evidence(String name, byte[] bytes, int w, int h) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(bytes).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }
}
