package com.example.worlddomination.game.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ScrollPaneState;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compares navigation chrome with the bitmap embedded in original shape 4450. */
@RunWith(AndroidJUnit4.class)
public final class SourceNavigationPanelInstrumentedTest {
    @Test public void uneditedChromeMatchesIndependentSourceAtEffectiveNativeSize() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            Texture packaged = new Texture(Gdx.files.internal("original/ui/navigation-panel.png"));
            packaged.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            Texture original = decodedOriginal();
            Pixmap blankPixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            blankPixel.drawPixel(0, 0, 0x1c2730ff);
            Texture missingArt = new Texture(blankPixel);
            blankPixel.dispose();
            SourceNavigationPanel renderer = new SourceNavigationPanel(new TextureRegion(packaged));
            Matrix4 previousProjection = new Matrix4(batch.getProjectionMatrix());
            float previousColor = batch.getPackedColor();
            int width = 800, height = 600;
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
            Rectangle panel = new Rectangle(50, 50, 90, 150.5f);
            try {
                assertEquals(180, packaged.getWidth());
                assertEquals(301, packaged.getHeight());
                // 2x projection turns the renderer's 8-point cap into its
                // authored 16 pixels and the entire panel into 180x301 pixels.
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0,
                        width / 2f, height / 2f));
                fb.begin(); clear(); batch.begin(); batch.setColor(.2f, .4f, .6f, .8f);
                float savedColor = batch.getPackedColor();
                renderer.draw(batch, panel);
                assertEquals("caller batch color restored", savedColor, batch.getPackedColor(), 0f);
                batch.end();
                byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
                fb.end();
                fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
                // The SVG bitmap is 297x768. Its authored navigation crop is
                // x113..293, y153..454; this bypasses the packaged PNG.
                batch.draw(new TextureRegion(original, 113, 153, 180, 301),
                        panel.x, panel.y, panel.width, panel.height);
                batch.end();
                byte[] expected = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
                fb.end();
                fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
                batch.draw(missingArt, panel.x, panel.y, panel.width, panel.height);
                batch.end();
                byte[] wrong = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
                fb.end();
                evidence("source-navigation-panel-native-actual.png", actual, width, height);
                evidence("source-navigation-panel-native-expected.png", expected, width, height);
                evidence("source-navigation-panel-native-missing-art.png", wrong, width, height);
                int compared = 0, wrongDifferences = 0;
                for (int sourceY = 0; sourceY < 301; sourceY++)
                    for (int sourceX = 0; sourceX < 180; sourceX++) {
                        // The shipped PNG intentionally replaces only these
                        // title pixels with blank rail artwork.
                        if (sourceX >= 60 && sourceX < 130
                                && (sourceY < 16 || sourceY >= 285)) continue;
                        int x = 100 + sourceX, y = 100 + 300 - sourceY;
                        int k = ((height - 1 - y) * width + x) * 4;
                        for (int channel = 0; channel < 4; channel++)
                            assertEquals("source chrome at " + sourceX + "," + sourceY
                                    + " channel " + channel,
                                    expected[k + channel], actual[k + channel]);
                        if (expected[k] != wrong[k] || expected[k + 1] != wrong[k + 1]
                                || expected[k + 2] != wrong[k + 2]) wrongDifferences++;
                        compared++;
                    }
                assertEquals("all unedited source pixels compared", 180 * 301 - 70 * 32, compared);
                assertTrue("flat panel omits original chrome", wrongDifferences > 1000);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                fb.dispose();
                batch.setProjectionMatrix(previousProjection);
                batch.setPackedColor(previousColor);
                missingArt.dispose();
                original.dispose();
                packaged.dispose();
            }
        });
    }

    @Test public void responsivePanelCoversFrameAndWorldKeepsScrollGeometry() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            Texture packaged = new Texture(Gdx.files.internal("original/ui/navigation-panel.png"));
            packaged.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            SourceNavigationPanel renderer = new SourceNavigationPanel(new TextureRegion(packaged));
            Matrix4 previousProjection = new Matrix4(batch.getProjectionMatrix());
            float previousColor = batch.getPackedColor();
            try {
                for (int[] size : new int[][]{{800, 600}, {1194, 552}}) {
                    int w = size[0], h = size[1];
                    // Matches the navigation frame dimensions from the world
                    // layout with zero safe-area insets at each aspect ratio.
                    Rectangle panel = new Rectangle(4, 90, (w - 8) * .215f, 244);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                        fb.begin(); clear(); batch.begin(); batch.setColor(.3f, .5f, .7f, .9f);
                        float savedColor = batch.getPackedColor();
                        renderer.draw(batch, panel);
                        assertEquals(savedColor, batch.getPackedColor(), 0f);
                        batch.end();
                        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        evidence("source-navigation-panel-responsive-" + w + "x" + h + ".png",
                                pixels, w, h);
                        for (int y = (int)panel.y + 9; y < panel.y + panel.height - 9; y++)
                            for (int x = (int)panel.x + 9; x < panel.x + panel.width - 9; x++)
                                assertEquals("navigation frame interior coverage", 255,
                                        pixels[((h - 1 - y) * w + x) * 4 + 3] & 255);
                        Rectangle narrow = new Rectangle(w - 40, 20, 12, 12);
                        fb.begin(); clear(); batch.begin(); batch.setColor(.3f, .5f, .7f, .9f);
                        float narrowColor = batch.getPackedColor();
                        renderer.draw(batch, narrow);
                        assertEquals("narrow draw restores caller color", narrowColor,
                                batch.getPackedColor(), 0f);
                        batch.end();
                        byte[] narrowPixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        for (int y = 24; y < 28; y++) for (int x = w - 36; x < w - 32; x++)
                            assertEquals("narrow panel center coverage", 255,
                                    narrowPixels[((h - 1 - y) * w + x) * 4 + 3] & 255);
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    } finally { fb.dispose(); }
                }

                GameSetup setup = new GameSetup(0L);
                setup.setDifficulty(1);
                assertTrue(setup.toggleOpponent(0));
                assertTrue(setup.toggleOpponent(1));
                GameState game = new GameState(setup);
                field("game").set(app, game);
                field("modalOpen").setBoolean(app, false);
                ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
                ScrollPaneState scroll = (ScrollPaneState) field("navigationScroll").get(app);
                scroll.moveBy(20f);
                float beforeOffset = scroll.getOffset();
                assertTrue("navigation has scroll range", beforeOffset > 0f);
                field("skipNextFrameDelta").setBoolean(app, true);
                app.render();
                Rectangle frame = (Rectangle) field("navPanel").get(app);
                Rectangle viewport = (Rectangle) field("navViewport").get(app);
                Rectangle[] rows = (Rectangle[]) field("navRows").get(app);
                assertEquals(frame.x + 8f, viewport.x, 0f);
                assertEquals(frame.y + 8f, viewport.y, 0f);
                assertEquals(frame.width - 16f, viewport.width, 0f);
                assertEquals(frame.height - 16f, viewport.height, 0f);
                assertEquals(viewport.x, rows[0].x, 0f);
                assertEquals(viewport.width, rows[0].width, 0f);
                assertEquals(viewport.y + viewport.height - 40f + beforeOffset,
                        rows[0].y, 0f);
                assertEquals(rows[0].y - 44f, rows[1].y, 0f);
                assertEquals(beforeOffset, scroll.getOffset(), 0f);
                int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
                byte[] world = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                evidence("source-navigation-panel-world.png", world, w, h);
                Field rendererField = field("navigationPanelRenderer");
                SourceNavigationPanel originalRenderer = (SourceNavigationPanel) rendererField.get(app);
                assertNotNull(originalRenderer);
                Pixmap empty = new Pixmap(180, 301, Pixmap.Format.RGBA8888);
                empty.setColor(0, 0, 0, 0);
                empty.fill();
                Texture missingTexture = new Texture(empty);
                empty.dispose();
                byte[] missingWorld;
                try {
                    rendererField.set(app, new SourceNavigationPanel(new TextureRegion(missingTexture)));
                    field("skipNextFrameDelta").setBoolean(app, true);
                    app.render();
                    missingWorld = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                } finally {
                    rendererField.set(app, originalRenderer);
                    missingTexture.dispose();
                }
                evidence("source-navigation-panel-world-missing.png", missingWorld, w, h);
                Viewport worldViewport = (Viewport) field("viewport").get(app);
                Vector2 lower = worldViewport.project(new Vector2(frame.x, frame.y));
                Vector2 upper = worldViewport.project(new Vector2(
                        frame.x + frame.width, frame.y + frame.height));
                int left = (int)Math.floor(lower.x), right = (int)Math.ceil(upper.x);
                int bottom = (int)Math.floor(lower.y), top = (int)Math.ceil(upper.y);
                int borderDifferences = 0, outsideDifferences = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int k = ((h - 1 - y) * w + x) * 4;
                    if (world[k] == missingWorld[k] && world[k + 1] == missingWorld[k + 1]
                            && world[k + 2] == missingWorld[k + 2]
                            && world[k + 3] == missingWorld[k + 3]) continue;
                    if (x < left || x >= right || y < bottom || y >= top)
                        outsideDifferences++;
                    else if (x < left + 16 || x >= right - 16
                            || y < bottom + 16 || y >= top - 16)
                        borderDifferences++;
                }
                assertTrue("missing world chrome changes the navigation border",
                        borderDifferences > 0);
                assertEquals("world pixels outside navigation frame remain frozen",
                        0, outsideDifferences);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                batch.setProjectionMatrix(previousProjection);
                batch.setPackedColor(previousColor);
                packaged.dispose();
            }
        });
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
        Texture texture = new Texture(pixels);
        pixels.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
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

    private static void evidence(String name, byte[] bytes, int width, int height) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(bytes).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }
}
