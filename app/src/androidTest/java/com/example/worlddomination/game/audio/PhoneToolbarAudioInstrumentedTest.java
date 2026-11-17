package com.example.worlddomination.game.audio;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
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
}
