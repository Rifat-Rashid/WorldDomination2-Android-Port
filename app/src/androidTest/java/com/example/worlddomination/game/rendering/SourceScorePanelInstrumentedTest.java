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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ScorePanelLayout;
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

/** Independently decoded score artwork, responsive ink and live World wiring. */
@RunWith(AndroidJUnit4.class)
public final class SourceScorePanelInstrumentedTest {
    private static final int[] DOT_IDS = {4613, 4615, 4617, 4619, 4621};

    @Test public void fiveColorsMatchIndependentSourceLayersAtNativeSize() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            Texture[] packaged = packaged();
            Texture[] source = source();
            assertSourcePixelsMatchPackaged();
            SourceScorePanel renderer = renderer(packaged);
            Matrix4 projection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, 800, 600, false);
            Pixmap emptyTitle = new Pixmap(148, 40, Pixmap.Format.RGBA8888);
            emptyTitle.setColor(0, 0, 0, 0); emptyTitle.fill();
            Texture blankTitle = new Texture(emptyTitle);
            emptyTitle.dispose();
            try {
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 400, 300));
                byte[] actual = atlas(fb, batch, renderer, source, 0);
                byte[] expected = atlas(fb, batch, renderer, source, 1);
                byte[] missingWindow = atlas(fb, batch, renderer, source, 2);
                byte[] wrongDots = atlas(fb, batch, renderer, source, 3);
                Texture[] titleAbsentArt = packaged.clone();
                titleAbsentArt[2] = blankTitle;
                byte[] missingTitle = atlas(fb, batch, renderer(titleAbsentArt), source, 0);
                evidence("source-score-panel-native-actual.png", actual, 800, 600);
                evidence("source-score-panel-native-expected.png", expected, 800, 600);
                evidence("source-score-panel-native-missing-window.png", missingWindow, 800, 600);
                evidence("source-score-panel-native-wrong-dots.png", wrongDots, 800, 600);
                evidence("source-score-panel-native-missing-title.png", missingTitle, 800, 600);
                for (int frame = 0; frame < 5; frame++) {
                    int x0 = 80, top = 600 - (40 + 70 * frame) - 49;
                    int compared = 0, missing = 0, wrong = 0, caption = 0;
                    for (int sy = 0; sy < 49; sy++) for (int sx = 0; sx < 180; sx++) {
                        // Text 4629 is a separately rasterized 4x source glyph.
                        // Only its original 37x10 source field is omitted.
                        int k = ((top + sy) * 800 + x0 + sx) * 4;
                        if (sx >= 71 && sx < 108 && sy >= 9 && sy < 19) {
                            if (different(actual, missingTitle, k)) caption++;
                            continue;
                        }
                        assertFalse("caption stays inside its source field",
                                different(actual, missingTitle, k));
                        // Identical input RGBA is asserted before GL. A split
                        // nine-slice quad and one full quad interpolate UVs at
                        // slightly different precision at this fractional
                        // window placement. Only its RGB may round by one;
                        // alpha, the backing and both dots remain exact.
                        boolean windowPixel = sx + .5f >= 24.55f && sx + .5f < 159.55f
                                && sy + .5f >= 20.85f && sy + .5f < 48.85f;
                        for (int channel = 0; channel < 4; channel++) {
                            int delta = Math.abs((expected[k + channel] & 255)
                                    - (actual[k + channel] & 255));
                            assertTrue("source score frame " + (frame + 1) + " at "
                                            + sx + "," + sy + " channel " + channel,
                                    delta <= (windowPixel && channel < 3 ? 1 : 0));
                        }
                        if (different(expected, missingWindow, k)) missing++;
                        if (different(expected, wrongDots, k)) wrong++;
                        compared++;
                    }
                    assertEquals(180 * 49 - 37 * 10, compared);
                    assertTrue("separate source window contributes pixels", missing > 100);
                    assertTrue("player color selects its source dot", wrong > 20);
                    assertTrue("original score caption contributes pixels", caption > 20);
                }
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                fb.dispose();
                blankTitle.dispose();
                batch.setProjectionMatrix(projection);
                batch.setPackedColor(oldColor);
                dispose(packaged);
                dispose(source);
            }
        });
    }

    @Test public void resizedCornersAndCompleteScoreInkFitBothLayouts() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            BitmapFont font = (BitmapFont) field("font").get(app);
            Texture[] packaged = packaged();
            Texture[] source = source();
            SourceScorePanel renderer = renderer(packaged);
            GameState game = sampleGame();
            field("game").set(app, game);
            Method drawValue = WorldDominationGame.class.getDeclaredMethod("drawScoreValue");
            drawValue.setAccessible(true);
            Rectangle panel = (Rectangle) field("scorePanel").get(app);
            Rectangle previousPanel = new Rectangle(panel);
            Matrix4 previousProjection = new Matrix4(batch.getProjectionMatrix());
            float previousColor = batch.getPackedColor();
            float oldScaleX = font.getScaleX(), oldScaleY = font.getScaleY();
            Color oldFontColor = new Color(font.getColor());
            try {
                font.getData().setScale(.77f, 1.13f);
                font.setColor(.2f, .3f, .4f, .8f);
                for (int[] dimensions : new int[][]{{800, 600}, {1194, 552}}) {
                    int w = dimensions[0], h = dimensions[1];
                    // Representative compact and wide score footer bounds.
                    panel.set(120, 100, w == 800 ? 105 : 161, 64);
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        fb.begin(); clear(); batch.begin();
                        batch.setColor(.2f, .4f, .6f, .8f);
                        float saved = batch.getPackedColor();
                        renderer.draw(batch, panel, 3);
                        assertEquals("score renderer restores caller color", saved,
                                batch.getPackedColor(), 0f);
                        batch.end();
                        byte[] chrome = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        // The four backing corners are source crops. They are
                        // unaffected by the inset window, dots or title.
                        fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
                        for (int cy = 0; cy < 2; cy++) for (int cx = 0; cx < 2; cx++) {
                            int sx = cx == 0 ? 0 : 164, sy = cy == 0 ? 0 : 33;
                            float dx = cx == 0 ? panel.x : panel.x + panel.width - 8;
                            float dy = cy == 0 ? panel.y + panel.height - 8 : panel.y;
                            batch.draw(new TextureRegion(source[0], sx, sy, 16, 16),
                                    dx, dy, 8, 8);
                        }
                        batch.end();
                        byte[] corners = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        for (int cy = 0; cy < 2; cy++) for (int cx = 0; cx < 2; cx++) {
                            int left = (int)(cx == 0 ? panel.x : panel.x + panel.width - 8);
                            int bottom = (int)(cy == 0 ? panel.y + panel.height - 8 : panel.y);
                            for (int y = bottom; y < bottom + 8; y++)
                                for (int x = left; x < left + 8; x++) {
                                    int k = ((h - 1 - y) * w + x) * 4;
                                    for (int c = 0; c < 4; c++)
                                        assertEquals("score source corner", corners[k + c], chrome[k + c]);
                                }
                        }
                        for (long value : new long[]{0L, Long.MAX_VALUE}) {
                            game.getHuman().score = value;
                            fb.begin(); clear(); batch.begin();
                            batch.setColor(.3f, .5f, .7f, .9f);
                            float savedBatch = batch.getPackedColor();
                            float savedX = font.getScaleX(), savedY = font.getScaleY();
                            Color savedFont = new Color(font.getColor());
                            drawValue.invoke(app);
                            assertEquals("score text restores batch color", savedBatch,
                                    batch.getPackedColor(), 0f);
                            assertEquals("score text restores font X scale", savedX,
                                    font.getScaleX(), 0f);
                            assertEquals("score text restores font Y scale", savedY,
                                    font.getScaleY(), 0f);
                            assertEquals("score text restores font color", savedFont,
                                    font.getColor());
                            batch.end();
                            byte[] ink = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                            fb.end();
                            Rectangle bounds = new Rectangle();
                            ScorePanelLayout.valueBounds(panel, bounds);
                            int count = 0, left = w, right = 0, bottom = h, top = 0;
                            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                                int k = ((h - 1 - y) * w + x) * 4;
                                if ((ink[k + 3] & 255) == 0) continue;
                                assertTrue("unclipped score ink inside source window",
                                        x + .5f >= bounds.x && x + .5f < bounds.x + bounds.width
                                                && y + .5f >= bounds.y && y + .5f < bounds.y + bounds.height);
                                left = Math.min(left, x); right = Math.max(right, x);
                                bottom = Math.min(bottom, y); top = Math.max(top, y);
                                count++;
                            }
                            assertTrue("score glyph visible", count > 10);
                            assertEquals("score remains horizontally centered",
                                    bounds.x + bounds.width / 2f,
                                    (left + right + 1) / 2f, 2f);
                            assertEquals("score remains vertically centered",
                                    bounds.y + bounds.height / 2f,
                                    (bottom + top + 1) / 2f, 3f);
                            fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
                            renderer.draw(batch, panel, 3);
                            drawValue.invoke(app);
                            batch.end();
                            byte[] composite = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                            fb.end();
                            evidence("source-score-panel-responsive-" + w + "x" + h
                                    + (value == 0 ? "-zero.png" : "-wide.png"), composite, w, h);
                        }
                        Rectangle tiny = new Rectangle(w - 28, 20, 12, 12);
                        fb.begin(); clear(); batch.begin(); batch.setColor(.3f, .5f, .7f, .9f);
                        float tinyColor = batch.getPackedColor();
                        renderer.draw(batch, tiny, 3);
                        assertEquals("small score frame restores caller color", tinyColor,
                                batch.getPackedColor(), 0f);
                        batch.end();
                        byte[] small = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        assertTrue("small score frame painted",
                                (small[((h - 1 - 26) * w + (w - 22)) * 4 + 3] & 255) > 0);
                    } finally { fb.dispose(); }
                }
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                panel.set(previousPanel);
                batch.setProjectionMatrix(previousProjection);
                batch.setPackedColor(previousColor);
                font.getData().setScale(oldScaleX, oldScaleY);
                font.setColor(oldFontColor);
                dispose(packaged);
                dispose(source);
            }
        });
    }

    @Test public void worldScoreArtChangesOnlyItsOwnPanel() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = sampleGame();
            game.getHuman().score = 123456789L;
            field("game").set(app, game);
            field("modalOpen").setBoolean(app, false);
            ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
            field("skipNextFrameDelta").setBoolean(app, true);
            app.render();
            int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
            byte[] world = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
            evidence("source-score-panel-world.png", world, w, h);
            Field rendererField = field("scorePanelRenderer");
            SourceScorePanel original = (SourceScorePanel) rendererField.get(app);
            assertNotNull(original);
            Texture[] blanks = new Texture[8];
            TextureRegion[] blankDots = new TextureRegion[5];
            try {
                int[][] sizes = {{180, 49}, {135, 28}, {148, 40},
                        {18, 18}, {18, 18}, {18, 18}, {18, 18}, {18, 18}};
                for (int i = 0; i < sizes.length; i++) {
                    Pixmap pixels = new Pixmap(sizes[i][0], sizes[i][1], Pixmap.Format.RGBA8888);
                    pixels.setColor(0, 0, 0, 0); pixels.fill();
                    blanks[i] = new Texture(pixels); pixels.dispose();
                    if (i >= 3) blankDots[i - 3] = new TextureRegion(blanks[i]);
                }
                rendererField.set(app, new SourceScorePanel(new TextureRegion(blanks[0]),
                        new TextureRegion(blanks[1]), blankDots, new TextureRegion(blanks[2])));
                field("skipNextFrameDelta").setBoolean(app, true);
                app.render();
                byte[] missing = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                evidence("source-score-panel-world-missing.png", missing, w, h);
                Rectangle panel = (Rectangle) field("scorePanel").get(app);
                Viewport viewport = (Viewport) field("viewport").get(app);
                Vector2 lower = viewport.project(new Vector2(panel.x, panel.y));
                Vector2 upper = viewport.project(new Vector2(
                        panel.x + panel.width, panel.y + panel.height));
                int left = (int)Math.floor(lower.x), right = (int)Math.ceil(upper.x);
                int bottom = (int)Math.floor(lower.y), top = (int)Math.ceil(upper.y);
                int border = 0, outside = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int k = ((h - 1 - y) * w + x) * 4;
                    if (!different(world, missing, k)) continue;
                    if (x < left || x >= right || y < bottom || y >= top) outside++;
                    else if (x < left + 8 || x >= right - 8
                            || y < bottom + 8 || y >= top - 8) border++;
                }
                assertTrue("source score backing changes visible border", border > 0);
                assertEquals("score art confined to panel", 0, outside);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                rendererField.set(app, original);
                dispose(blanks);
            }
        });
    }

    private static byte[] atlas(FrameBuffer fb, SpriteBatch batch, SourceScorePanel renderer,
            Texture[] source, int mode) {
        fb.begin(); clear(); batch.begin(); batch.setColor(.2f, .4f, .6f, .8f);
        float saved = batch.getPackedColor();
        for (int frame = 1; frame <= 5; frame++) {
            Rectangle panel = new Rectangle(40, 20 + 35 * (frame - 1), 90, 24.5f);
            if (mode == 0) {
                renderer.draw(batch, panel, frame);
                assertEquals("caller batch color", saved, batch.getPackedColor(), 0f);
            } else {
                batch.setColor(1, 1, 1, 1);
                batch.draw(source[0], panel.x, panel.y, 90, 24.5f);
                if (mode != 2)
                    batch.draw(source[1], panel.x + 24.55f / 2f,
                            panel.y + (49f - 48.85f) / 2f, 67.5f, 14f);
                int dot = mode == 3 ? frame % 5 : frame - 1;
                batch.draw(source[2 + dot], panel.x + 23.55f / 2f,
                        panel.y + (49f - 21.85f) / 2f, 9, 9);
                batch.draw(source[2 + dot], panel.x + 143.55f / 2f,
                        panel.y + (49f - 20.85f) / 2f, 9, 9);
                batch.setPackedColor(saved);
            }
        }
        batch.end();
        byte[] result = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, true);
        fb.end();
        return result;
    }

    private static Texture[] packaged() {
        String root = "original/ui/score-panel/";
        String[] names = {"backing.png", "window.png", "title.png",
                "dot-1.png", "dot-2.png", "dot-3.png", "dot-4.png", "dot-5.png"};
        Texture[] result = new Texture[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = new Texture(Gdx.files.internal(root + names[i]));
            result[i].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        assertEquals(180, result[0].getWidth()); assertEquals(49, result[0].getHeight());
        assertEquals(135, result[1].getWidth()); assertEquals(28, result[1].getHeight());
        assertEquals(148, result[2].getWidth()); assertEquals(40, result[2].getHeight());
        for (int i = 3; i < 8; i++) {
            assertEquals(18, result[i].getWidth()); assertEquals(18, result[i].getHeight());
        }
        return result;
    }

    private static SourceScorePanel renderer(Texture[] art) {
        TextureRegion[] dots = new TextureRegion[5];
        for (int i = 0; i < 5; i++) dots[i] = new TextureRegion(art[i + 3]);
        return new SourceScorePanel(new TextureRegion(art[0]), new TextureRegion(art[1]),
                dots, new TextureRegion(art[2]));
    }

    private static Texture[] source() throws Exception {
        Texture[] result = new Texture[7];
        Bitmap whole = webp(4450, 297, 768);
        Bitmap crop = Bitmap.createBitmap(whole, 113, 555, 180, 49);
        result[0] = texture(crop); crop.recycle(); whole.recycle();
        result[1] = decoded(4580, 135, 28);
        for (int i = 0; i < 5; i++) result[i + 2] = decoded(DOT_IDS[i], 18, 18);
        return result;
    }

    private static void assertSourcePixelsMatchPackaged() throws Exception {
        int[] ids = {4450, 4580, 4613, 4615, 4617, 4619, 4621};
        String[] names = {"backing.png", "window.png", "dot-1.png", "dot-2.png",
                "dot-3.png", "dot-4.png", "dot-5.png"};
        for (int index = 0; index < ids.length; index++) {
            Bitmap whole = webp(ids[index], index == 0 ? 297
                    : index == 1 ? 135 : 18, index == 0 ? 768
                    : index == 1 ? 28 : 18);
            Bitmap source = index == 0 ? Bitmap.createBitmap(whole, 113, 555, 180, 49) : whole;
            Pixmap packaged = new Pixmap(Gdx.files.internal(
                    "original/ui/score-panel/" + names[index]));
            try {
                assertEquals(source.getWidth(), packaged.getWidth());
                assertEquals(source.getHeight(), packaged.getHeight());
                for (int y = 0; y < source.getHeight(); y++)
                    for (int x = 0; x < source.getWidth(); x++) {
                        int argb = source.getPixel(x, y);
                        if ((argb >>> 24) == 0) argb = 0;
                        int originalRgba = (argb << 8) | (argb >>> 24);
                        assertEquals("pre-GL source/packaged " + names[index]
                                        + " pixel " + x + "," + y,
                                originalRgba, packaged.getPixel(x, y));
                    }
            } finally {
                packaged.dispose();
                if (source != whole) source.recycle();
                whole.recycle();
            }
        }
    }

    private static Texture decoded(int id, int width, int height) throws Exception {
        Bitmap bitmap = webp(id, width, height);
        Texture result = texture(bitmap);
        bitmap.recycle();
        return result;
    }

    private static Bitmap webp(int id, int width, int height) throws Exception {
        byte[] xml;
        try (InputStream zipped = new GZIPInputStream(Gdx.files.internal(
                "original/runtime/shapes/" + id + ".svgz").read());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = zipped.read(buffer)) != -1) output.write(buffer, 0, count);
            xml = output.toByteArray();
        }
        String svg = new String(xml, StandardCharsets.UTF_8);
        Matcher fill = Pattern.compile("data:image/webp;base64,([^\"]+)").matcher(svg);
        assertTrue("shape " + id + " has embedded WebP", fill.find());
        byte[] bytes = Base64.decode(fill.group(1), Base64.DEFAULT);
        assertFalse("one bitmap fill in shape " + id, fill.find());
        BitmapFactory.Options decoding = new BitmapFactory.Options();
        // The SVG bitmap fill is straight-alpha WebP. Android's default
        // premultiply/unpremultiply roundtrip changes low-alpha RGB before
        // this independent source texture reaches the GPU.
        decoding.inPremultiplied = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decoding);
        assertNotNull(bitmap);
        assertEquals(width, bitmap.getWidth()); assertEquals(height, bitmap.getHeight());
        if (id != 4450) {
            if (id == 4580) {
                assertTrue(svg.contains("<g transform=\"matrix(1.0, 0.0, 0.0, 1.0, 67.5, 14.0)\""));
                assertTrue(svg.contains("patternTransform=\"matrix(1.0, 0.0, 0.0, 1.0, -67.5, -14.0)\""));
                assertTrue(svg.contains("<path d=\"M67.5 14.0 L-67.5 14.0 -67.5 -14.0 67.5 -14.0 67.5 14.0\""));
            } else {
                assertTrue(svg.contains("<g transform=\"matrix(1.0, 0.0, 0.0, 1.0, 12.75, -1.0)\""));
                assertTrue(svg.contains("patternTransform=\"matrix(1.0, 0.0, 0.0, 1.0, -12.75, 1.0)\""));
                assertTrue(svg.contains("<path d=\"M5.25 19.0 L-12.75 19.0 -12.75 1.0 5.25 1.0 5.25 19.0\""));
            }
        }
        return bitmap;
    }

    private static Texture texture(Bitmap bitmap) {
        Pixmap pixels = new Pixmap(bitmap.getWidth(), bitmap.getHeight(), Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int argb = bitmap.getPixel(x, y);
            // SVG compositing defines no visible color where alpha is zero.
            // Shape 4580's WebP carries arbitrary RGB in fully clear pixels;
            // the source SVG rasterizer clears these before linear sampling.
            if ((argb >>> 24) == 0) argb = 0;
            pixels.drawPixel(x, y, (argb << 8) | (argb >>> 24));
        }
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

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static boolean different(byte[] a, byte[] b, int k) {
        return a[k] != b[k] || a[k + 1] != b[k + 1]
                || a[k + 2] != b[k + 2] || a[k + 3] != b[k + 3];
    }

    private static void dispose(Texture[] textures) {
        for (Texture texture : textures) if (texture != null) texture.dispose();
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
