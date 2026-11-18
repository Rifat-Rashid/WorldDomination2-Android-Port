package com.example.worlddomination.game.audio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Exercises the actual World toolbar controls and Android theme voice. */
@RunWith(AndroidJUnit4.class)
public final class PhoneToolbarAudioInstrumentedTest {
    @Test public void worldToolbarMuteAndGearPreserveGameAndTheme() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field(app, "splashState");
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    CountingRandom random = new CountingRandom(1234L);
                    GameState game = new GameState(0, random);
                    setField(app, "game", game);
                    setField(app, "modalOpen", false);
                    OriginalAudio audio = (OriginalAudio) field(app, "audio");
                    audio.initialize();
                    Music theme = (Music) field(audio, "theme");
                    assertNotNull("Theme must be loaded", theme);
                    assertTrue("Theme must be playing", theme.isPlaying());
                    assertFalse(audio.isOutputMuted());
                    assertTrue(audio.isMusicEnabled());
                    assertTrue(audio.isSoundEnabled());
                    float themeVolume = theme.getVolume();
                    assertTrue("Theme must be audible before mute", themeVolume > 0f);
                    render(app);
                    Rectangle mute = (Rectangle) field(app, "muteButton");
                    Rectangle gear = (Rectangle) field(app, "settingsButton");
                    int turn = game.getTurn(), money = game.getTurnMoneyMillions();
                    int queued = game.getQueuedActionCount(), draws = random.draws;
                    GameState.Action selected = game.getSelectedAction();
                    GameState.Action first = game.getQueuedAction(0), second = game.getQueuedAction(1);
                    byte[] before = capture(app, mute, "phone-toolbar-before.png");
                    tap(app, mute);
                    assertTrue(audio.isOutputMuted());
                    assertTrue(audio.isSoundEnabled());
                    assertTrue(audio.isMusicEnabled());
                    assertEquals(0f, theme.getVolume(), 0.0001f);
                    assertTrue("Mute must not stop the theme", theme.isPlaying());
                    byte[] muted = capture(app, mute, "phone-toolbar-muted.png");
                    assertFalse("MUTE label region changes to UNMUTE", Arrays.equals(before, muted));
                    tap(app, mute);
                    assertFalse(audio.isOutputMuted());
                    assertTrue(audio.isSoundEnabled());
                    assertTrue(audio.isMusicEnabled());
                    assertEquals(themeVolume, theme.getVolume(), 0.0001f);
                    assertTrue(theme.isPlaying());
                    byte[] restored = capture(app, mute, "phone-toolbar-restored.png");
                    assertArrayEquals("Unmute restores the original label region", before, restored);
                    tap(app, gear);
                    assertFalse("Gear changes the sound option", audio.isSoundEnabled());
                    assertTrue("Gear leaves music enabled", audio.isMusicEnabled());
                    assertFalse(audio.isOutputMuted());
                    assertEquals("Gear leaves theme gain alone", themeVolume, theme.getVolume(), 0.0001f);
                    assertTrue(theme.isPlaying());
                    byte[] gearImage = capture(app, mute, "phone-toolbar-gear.png");
                    assertArrayEquals("Gear does not change the mute label", restored, gearImage);
                    verifyGearMatchesSource(app, gear);
                    assertEquals(turn, game.getTurn());
                    assertEquals(money, game.getTurnMoneyMillions());
                    assertEquals(queued, game.getQueuedActionCount());
                    assertEquals(selected, game.getSelectedAction());
                    assertEquals(first, game.getQueuedAction(0));
                    assertEquals(second, game.getQueuedAction(1));
                    assertEquals("Toolbar controls must not consume gameplay randomness", draws, random.draws);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            }));
            assertTrue("Toolbar test timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Phone toolbar audio", failure.get());
        }
    }

    private static final class CountingRandom extends Random {
        int draws;
        CountingRandom(long seed) { super(seed); }
        @Override protected int next(int bits) { draws++; return super.next(bits); }
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        Viewport viewport = (Viewport) field(app, "viewport");
        Vector2 p = viewport.project(new Vector2(bounds.x + bounds.width / 2f,
                bounds.y + bounds.height / 2f));
        int x = Math.round(p.x), y = Gdx.graphics.getHeight() - Math.round(p.y);
        app.touchDown(x, y, 0, 0);
        app.touchUp(x, y, 0, 0);
    }

    private static void render(WorldDominationGame app) throws Exception {
        setField(app, "skipNextFrameDelta", true);
        app.render();
    }

    private static byte[] capture(WorldDominationGame app, Rectangle bounds, String filename) throws Exception {
        render(app);
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        Pixmap frame = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        frame.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, w, h, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, filename).getAbsolutePath()), frame);
            Viewport viewport = (Viewport) field(app, "viewport");
            Vector2 lo = viewport.project(new Vector2(bounds.x + bounds.width * .16f,
                    bounds.y + bounds.height * .25f));
            Vector2 hi = viewport.project(new Vector2(bounds.x + bounds.width * .84f,
                    bounds.y + bounds.height * .78f));
            int x0 = Math.max(0, Math.round(lo.x)), x1 = Math.min(w, Math.round(hi.x));
            int y0 = Math.max(0, h - Math.round(hi.y)), y1 = Math.min(h, h - Math.round(lo.y));
            assertTrue("Label crop must be visible", x1 > x0 && y1 > y0);
            byte[] pixels = new byte[(x1 - x0) * (y1 - y0) * 4];
            int i = 0;
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                int pixel = frame.getPixel(x, y);
                pixels[i++] = (byte) (pixel >>> 24); pixels[i++] = (byte) (pixel >>> 16);
                pixels[i++] = (byte) (pixel >>> 8); pixels[i++] = (byte) pixel;
            }
            return pixels;
        } finally {
            frame.dispose();
        }
    }

    /** Compare the actual gear draw against an independently generated 23px phone oracle. */
    private static void verifyGearMatchesSource(WorldDominationGame app, Rectangle gear)
            throws Exception {
        OriginalUiAssets assets = (OriginalUiAssets) field(app, "uiAssets");
        assertTrue("Gear uses a managed file texture",
                assets.phoneGear.getTexture().isManaged());
        OrthographicCamera camera = (OrthographicCamera) field(app, "camera");
        SpriteBatch batch = (SpriteBatch) field(app, "batch");
        ShapeRenderer shapes = (ShapeRenderer) field(app, "shapes");
        float oldWidth = camera.viewportWidth, oldHeight = camera.viewportHeight;
        float oldZoom = camera.zoom;
        Vector3 oldPosition = new Vector3(camera.position);
        Rectangle oldGear = new Rectangle(gear);
        float oldBatchColor = batch.getPackedColor();
        com.badlogic.gdx.graphics.Color oldShapeColor = new com.badlogic.gdx.graphics.Color(shapes.getColor());
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 23, 23, false);
        byte[] actual;
        byte[] thinWire;
        try {
            camera.setToOrtho(false, 23, 23);
            gear.set(0, 0, 23, 23);
            batch.setColor(.35f, .55f, .75f, 1f);
            int tintBits = Float.floatToRawIntBits(batch.getPackedColor());
            buffer.begin();
            try {
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                invoke(app, "drawSettingsGear");
                assertEquals("Gear draw restores the caller's batch tint", tintBits,
                        Float.floatToRawIntBits(batch.getPackedColor()));
                actual = ScreenUtils.getFrameBufferPixels(0, 0, 23, 23, true);
                Pixmap image = new Pixmap(23, 23, Pixmap.Format.RGBA8888);
                try {
                    image.getPixels().put(actual);
                    File directory = InstrumentationRegistry.getInstrumentation()
                            .getTargetContext().getExternalFilesDir("beta-ui-evidence");
                    assertNotNull(directory);
                    PixmapIO.writePNG(Gdx.files.absolute(new File(directory,
                            "phone-toolbar-gear-silhouette.png").getAbsolutePath()), image);
                } finally {
                    image.dispose();
                }
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                shapes.setProjectionMatrix(camera.combined);
                shapes.begin(ShapeRenderer.ShapeType.Line);
                shapes.setColor(225f / 255f, 233f / 255f, 235f / 255f, 1f);
                shapes.circle(11.5f, 11.5f, 6.5f, 16);
                shapes.circle(11.5f, 11.5f, 2.6f, 12);
                for (int i = 0; i < 8; i++) {
                    float angle = (float) (Math.PI * i / 4f);
                    shapes.line(11.5f + (float) Math.cos(angle) * 6f,
                            11.5f + (float) Math.sin(angle) * 6f,
                            11.5f + (float) Math.cos(angle) * 9f,
                            11.5f + (float) Math.sin(angle) * 9f);
                }
                shapes.end();
                thinWire = ScreenUtils.getFrameBufferPixels(0, 0, 23, 23, true);
            } finally {
                buffer.end();
            }
        } finally {
            buffer.dispose();
            batch.setPackedColor(oldBatchColor);
            shapes.setColor(oldShapeColor);
            gear.set(oldGear);
            camera.setToOrtho(false, oldWidth, oldHeight);
            camera.position.set(oldPosition);
            camera.zoom = oldZoom;
            camera.update();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPremultiplied = false;
        Bitmap source;
        try (InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("source-phone-gear-23.png")) {
            source = BitmapFactory.decodeStream(stream, null, options);
        }
        assertNotNull(source);
        try {
            assertEquals(23, source.getWidth());
            assertEquals(23, source.getHeight());
            double actualError = gearErrorAgainstSource(actual, source);
            double thinError = gearErrorAgainstSource(thinWire, source);
            assertTrue("Source silhouette beats thin wire: actual=" + actualError
                            + " thin=" + thinError,
                    actualError < 25d && actualError * 2d < thinError);
        } finally {
            source.recycle();
        }
    }

    private static double gearErrorAgainstSource(byte[] pixels, Bitmap source) {
        long error = 0;
        for (int y = 0; y < 23; y++) for (int x = 0; x < 23; x++) {
            int sourcePixel = source.getPixel(x, y);
            int alpha = sourcePixel >>> 24;
            int offset = (y * 23 + x) * 4;
            for (int channel = 0; channel < 3; channel++) {
                int expected = (((sourcePixel >>> (16 - channel * 8)) & 255) * alpha + 127) / 255;
                error += Math.abs((pixels[offset + channel] & 255) - expected);
            }
        }
        return error / (23d * 23d * 3d);
    }
}
