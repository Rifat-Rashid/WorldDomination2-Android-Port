package com.example.worlddomination.game.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compares Android rendering with captures of actual Python MobileSkin.panel. */
@RunWith(AndroidJUnit4.class)
public final class SourcePhonePanelInstrumentedTest {
    private static final int WIDTH = 800, HEIGHT = 1000;
    private static final String[] NAMES = {"logo", "header", "toolbar", "stock", "message",
            "action", "threshold-42", "threshold-43", "portrait"};
    private static final int[][] CASES = {{255,62,5}, {607,62,72}, {314,34,139},
            {300,435,178}, {407,64,618}, {314,64,687}, {160,42,756},
            {160,43,803}, {81,51,851}};

    @Test public void liveWorldPanelArtStaysInsideItsBoundsAtBothAspectRatios() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L);
            for (int leader : new int[]{0, 1, 2, 4}) assertTrue(setup.toggleOpponent(leader));
            GameState game = new GameState(setup);
            field("game").set(app, game);
            field("modalOpen").setBoolean(app, false);
            ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
            Field rendererField = field("phonePanelRenderer");
            Object original = rendererField.get(app);
            Texture[] transparent = new Texture[3];
            int[][] sizes = {{160,14}, {160,42}, {160,43}};
            try {
                for (int i = 0; i < sizes.length; i++) {
                    Pixmap p = new Pixmap(sizes[i][0], sizes[i][1], Pixmap.Format.RGBA8888);
                    transparent[i] = new Texture(p); p.dispose();
                }
                SourcePhonePanel missing = new SourcePhonePanel(new TextureRegion(transparent[0]),
                        new TextureRegion(transparent[1]), new TextureRegion(transparent[2]));
                for (int[] size : new int[][]{{800,600}, {1194,552}}) {
                    int w = size[0], h = size[1];
                    app.resize(w, h);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        rendererField.set(app, original);
                        byte[] actual = renderWorld(app, fb, w, h);
                        Rectangle map = new Rectangle((Rectangle) field("mapBounds").get(app));
                        Rectangle nav = new Rectangle((Rectangle) field("navViewport").get(app));
                        Rectangle stock = new Rectangle((Rectangle) field("equipmentViewport").get(app));
                        rendererField.set(app, missing);
                        byte[] absent = renderWorld(app, fb, w, h);
                        assertEquals(map, field("mapBounds").get(app));
                        assertEquals(nav, field("navViewport").get(app));
                        assertEquals(stock, field("equipmentViewport").get(app));
                        Viewport viewport = (Viewport) field("viewport").get(app);
                        String[] panels = {"logoPanel", "centerHeader", "toolbarPanel",
                                "stockPanel", "messagePanel", "actionPanel"};
                        Rectangle[] screenBounds = new Rectangle[panels.length];
                        for (int i = 0; i < panels.length; i++) {
                            Rectangle p = (Rectangle) field(panels[i]).get(app);
                            Vector2 lower = viewport.project(new Vector2(p.x, p.y));
                            Vector2 upper = viewport.project(new Vector2(p.x + p.width, p.y + p.height));
                            screenBounds[i] = new Rectangle(lower.x - 2, lower.y - 2,
                                    upper.x - lower.x + 4, upper.y - lower.y + 4);
                        }
                        int changed = 0;
                        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                            int k = ((h - 1 - y) * w + x) * 4;
                            boolean different = false;
                            for (int c = 0; c < 4; c++) different |= actual[k+c] != absent[k+c];
                            if (!different) continue;
                            changed++;
                            boolean inside = false;
                            for (Rectangle p : screenBounds) inside |= p.contains(x + .5f, y + .5f);
                            assertTrue("panel change must not alter map or other controls", inside);
                        }
                        assertTrue("production world uses source panels", changed > 1000);
                        evidenceSized("source-phone-panels-world-"+w+"x"+h+".png", actual, w, h);
                        evidenceSized("source-phone-panels-world-missing-"+w+"x"+h+".png", absent, w, h);
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    } finally { fb.dispose(); }
                }
            } finally {
                rendererField.set(app, original);
                app.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                for (Texture texture : transparent) if (texture != null) texture.dispose();
            }
        });
    }

    private static byte[] renderWorld(WorldDominationGame app, FrameBuffer fb, int w, int h)
            throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true);
        fb.begin();
        try { app.render(); return ScreenUtils.getFrameBufferPixels(0, 0, w, h, true); }
        finally { fb.end(); }
    }

    private static Field field(String name) throws Exception {
        Field f = WorldDominationGame.class.getDeclaredField(name);
        f.setAccessible(true); return f;
    }

    private static void evidenceSized(String name, byte[] data, int width, int height) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir); assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try { image.getPixels().put(data).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }

    @Test public void sourcePanelsMatchIndependentCapturesAtIntegerSize() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Field field = WorldDominationGame.class.getDeclaredField("batch");
            field.setAccessible(true);
            SpriteBatch batch = (SpriteBatch) field.get(app);
            Texture tile = load("interior-tile.png"), shortChrome = load("chrome-42.png"),
                    tallChrome = load("chrome-43.png");
            Texture[] expected = new Texture[NAMES.length];
            Pixmap blankPixmap = new Pixmap(160, 14, Pixmap.Format.RGBA8888);
            blankPixmap.setColor(.1f, .1f, .1f, 1f);
            blankPixmap.fill();
            Texture blank = new Texture(blankPixmap);
            blankPixmap.dispose();
            Pixmap whitePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            whitePixmap.setColor(1, 1, 1, 1); whitePixmap.fill();
            Texture white = new Texture(whitePixmap);
            whitePixmap.dispose();
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            try {
                for (int i = 0; i < NAMES.length; i++) expected[i] = sourceCapture(NAMES[i]);
                SourcePhonePanel actual = new SourcePhonePanel(new TextureRegion(tile),
                        new TextureRegion(shortChrome), new TextureRegion(tallChrome));
                SourcePhonePanel missing = new SourcePhonePanel(new TextureRegion(blank),
                        new TextureRegion(shortChrome), new TextureRegion(tallChrome));
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
                byte[] actualPixels = render(fb, batch, actual, null, null);
                byte[] expectedPixels = render(fb, batch, null, expected, null);
                byte[] missingPixels = render(fb, batch, missing, null, null);
                byte[] oldPixels = render(fb, batch, null, null, white);
                evidence("source-phone-panels-actual.png", actualPixels);
                evidence("source-phone-panels-expected.png", expectedPixels);
                evidence("source-phone-panels-missing-tile.png", missingPixels);
                evidence("source-phone-panels-square-outline.png", oldPixels);
                int missingDifferences = 0, oldDifferences = 0;
                for (int i = 0; i < expectedPixels.length; i += 4) {
                    for (int c = 0; c < 4; c++)
                        assertEquals("pixel " + (i / 4) + " channel " + c,
                                expectedPixels[i + c], actualPixels[i + c]);
                    if (expectedPixels[i] != missingPixels[i] || expectedPixels[i+1] != missingPixels[i+1]
                            || expectedPixels[i+2] != missingPixels[i+2]) missingDifferences++;
                    if (expectedPixels[i] != oldPixels[i] || expectedPixels[i+1] != oldPixels[i+1]
                            || expectedPixels[i+2] != oldPixels[i+2]) oldDifferences++;
                }
                assertTrue("missing source tile is detected", missingDifferences > 1000);
                assertTrue("old square outlines are detected", oldDifferences > 1000);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                fb.dispose(); batch.setProjectionMatrix(oldProjection); batch.setPackedColor(oldColor);
                for (Texture texture : expected) if (texture != null) texture.dispose();
                white.dispose(); blank.dispose(); tile.dispose(); shortChrome.dispose(); tallChrome.dispose();
            }
        });
    }

    private static byte[] render(FrameBuffer fb, SpriteBatch batch, SourcePhonePanel renderer,
            Texture[] captures, Texture square) {
        fb.begin(); Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClearColor(0, 0, 0, 0); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin(); batch.setColor(.2f, .4f, .6f, .8f);
        float original = batch.getPackedColor();
        for (int i = 0; i < CASES.length; i++) {
            int w = CASES[i][0], h = CASES[i][1], y = HEIGHT - CASES[i][2] - h;
            if (renderer != null) {
                renderer.draw(batch, new Rectangle(10, y, w, h));
                assertEquals("caller color", original, batch.getPackedColor(), 0f);
            } else if (captures != null) {
                batch.setColor(1, 1, 1, 1); batch.draw(captures[i], 10, y, w, h);
            } else {
                batch.setColor(.37f, .51f, .57f, 1f);
                batch.draw(square, 10, y, w, 1); batch.draw(square, 10, y + h - 1, w, 1);
                batch.draw(square, 10, y, 1, h); batch.draw(square, 10 + w - 1, y, 1, h);
            }
        }
        batch.end();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
        fb.end(); return pixels;
    }

    private static Texture load(String name) {
        Texture texture = new Texture(Gdx.files.internal("original/ui/phone-panel/" + name));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static Texture sourceCapture(String name) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("source-phone-panel/" + name + ".png")) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inPremultiplied = false;
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            assertNotNull(bitmap);
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            try {
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int argb = pixels[y * width + x];
                    pixmap.drawPixel(x, y, (argb << 8) | (argb >>> 24));
                }
                Texture texture = new Texture(pixmap);
                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                return texture;
            } finally { pixmap.dispose(); bitmap.recycle(); }
        }
    }

    private static void evidence(String name, byte[] data) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir); assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        try { image.getPixels().put(data).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }
}
