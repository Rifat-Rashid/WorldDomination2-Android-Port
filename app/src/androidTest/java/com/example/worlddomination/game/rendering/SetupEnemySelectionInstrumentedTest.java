package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Actual setup drawing and viewport-routed carousel/upper-slot touches. */
@RunWith(AndroidJUnit4.class)
public final class SetupEnemySelectionInstrumentedTest {
    @Test public void selectedRivalsDimOnlyTheirCardsAndUpperIconsRemoveThem() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "startFlashAssetLoading");
                    OriginalFlashAnimationAssets artwork =
                            (OriginalFlashAnimationAssets) field("flashAnimationAssets").get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    while (!artwork.areSetupPortraitsLoaded() && System.nanoTime() < deadline) {
                        artwork.update();
                        Thread.sleep(10);
                    }
                    assertTrue("Original setup portraits must load", artwork.areSetupPortraitsLoaded());
                    assertNotNull("Original red removal shape must load", artwork.getShape(
                            OriginalFlashAnimationAssets.SETUP_REMOVE_LEADER_SHAPE_ID));
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameSetup setup = new GameSetup(0L);
                    field("setup").set(app, setup);
                    field("game").set(app, null);
                    field("setupLeaderListStart").setInt(app, 0);
                    field("modalOpen").setBoolean(app, false);
                    SetupScreenLayout layout = (SetupScreenLayout) field("setupLayout").get(app);

                    try (Frame baseline = frame(app)) {
                        tap(app, layout.leaderCards[0]);
                        tap(app, layout.leaderCards[4]);
                        assertEquals(Arrays.asList(0, 4), setup.getSelectedOpponentIds());
                        try (Frame selected = frame(app)) {
                            assertTrue("First chosen portrait should be visibly dimmer",
                                    difference(baseline, selected, app, layout.leaderCards[0]) > 10);
                            assertTrue("First chosen portrait should lose substantial brightness",
                                    luminance(selected, app, layout.leaderCards[0])
                                            < luminance(baseline, app, layout.leaderCards[0]) * 0.8);
                            assertTrue("Second chosen portrait should be visibly dimmer",
                                    difference(baseline, selected, app, layout.leaderCards[4]) > 10);
                            assertTrue("Second chosen portrait should lose substantial brightness",
                                    luminance(selected, app, layout.leaderCards[4])
                                            < luminance(baseline, app, layout.leaderCards[4]) * 0.8);
                            assertTrue("Unselected portrait must remain unchanged",
                                    difference(baseline, selected, app, layout.leaderCards[1]) < 2);
                            for (int slot = 0; slot < 2; slot++) {
                                int before = redPixels(baseline, app, layout.opponentRemoveIcons[slot]);
                                int after = redPixels(selected, app, layout.opponentRemoveIcons[slot]);
                                assertTrue("Original red X must appear on occupied slot " + slot,
                                        after > before + 3);
                            }
                            selected.save("source-enemies-selected.png");
                        }
                        tap(app, layout.leaderCards[0]);
                        tap(app, layout.leaderCards[4]);
                        assertEquals("A second carousel tap must not remove a rival",
                                Arrays.asList(0, 4), setup.getSelectedOpponentIds());
                        tap(app, layout.opponentRemoveIcons[0]);
                        assertEquals("Remaining rival shifts to the first upper slot",
                                Arrays.asList(4), setup.getSelectedOpponentIds());
                        try (Frame removed = frame(app)) {
                            assertTrue("Removed carousel portrait returns to baseline",
                                    difference(baseline, removed, app, layout.leaderCards[0]) < 2);
                            assertTrue("Remaining portrait stays dimmed",
                                    difference(baseline, removed, app, layout.leaderCards[4]) > 10);
                            removed.save("source-enemy-removed.png");
                        }
                        tap(app, layout.opponentRemoveIcons[0]);
                        assertTrue(setup.getSelectedOpponentIds().isEmpty());
                        try (Frame restored = frame(app)) {
                            assertTrue("Both restored portrait images match their baseline",
                                    difference(baseline, restored, app, layout.leaderCards[0]) < 2
                                            && difference(baseline, restored, app, layout.leaderCards[4]) < 2);
                        }
                    }

                    tap(app, layout.leaderPrevious);
                    assertEquals("Previous wraps to the final source leader", 18,
                            field("setupLeaderListStart").getInt(app));
                    try (Frame lastBaseline = frame(app)) {
                        tap(app, layout.leaderCards[0]);
                        assertEquals(Arrays.asList(18), setup.getSelectedOpponentIds());
                        try (Frame lastSelected = frame(app)) {
                            assertTrue("Wrapped source portrait should dim",
                                    difference(lastBaseline, lastSelected, app, layout.leaderCards[0]) > 10);
                            assertTrue("Wrapped source portrait should lose substantial brightness",
                                    luminance(lastSelected, app, layout.leaderCards[0])
                                            < luminance(lastBaseline, app, layout.leaderCards[0]) * 0.8);
                            lastSelected.save("source-last-enemy-selected.png");
                        }
                        tap(app, layout.opponentRemoveIcons[0]);
                        assertTrue(setup.getSelectedOpponentIds().isEmpty());
                        try (Frame lastRemoved = frame(app)) {
                            assertTrue("Wrapped source portrait returns to baseline",
                                    difference(lastBaseline, lastRemoved, app, layout.leaderCards[0]) < 2);
                        }
                    }
                    assertNull("Setup interaction must not instantiate a match", field("game").get(app));
                    assertEquals("Rendering must leave the sprite batch white", Color.WHITE,
                            ((SpriteBatch) field("batch").get(app)).getColor());
                    assertEquals("No GL errors after setup rendering", GL20.GL_NO_ERROR,
                            Gdx.gl.glGetError());
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue("Enemy selection Android test timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source enemy selection", failure.get());
        }
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        Vector2 point = ((Viewport) field("viewport").get(app)).project(new Vector2(
                bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f));
        int x = Math.round(point.x), y = Gdx.graphics.getHeight() - Math.round(point.y);
        app.touchDown(x, y, 0, 0);
        app.touchUp(x, y, 0, 0);
    }

    private static Frame frame(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true);
        app.render();
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixels.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        return new Frame(pixels);
    }

    private static int[] screenBounds(WorldDominationGame app, Rectangle bounds) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 lower = viewport.project(new Vector2(bounds.x, bounds.y));
        Vector2 upper = viewport.project(new Vector2(bounds.x + bounds.width,
                bounds.y + bounds.height));
        return new int[]{Math.round(lower.x), Gdx.graphics.getHeight() - Math.round(upper.y),
                Math.round(upper.x), Gdx.graphics.getHeight() - Math.round(lower.y)};
    }

    private static double difference(Frame before, Frame after, WorldDominationGame app,
                                     Rectangle card) throws Exception {
        int[] area = screenBounds(app, card);
        int insetX = Math.max(2, (area[2] - area[0]) / 8);
        int insetY = Math.max(2, (area[3] - area[1]) / 8);
        long total = 0;
        int samples = 0;
        for (int y = area[1] + insetY; y < area[3] - insetY; y += 2) {
            for (int x = area[0] + insetX; x < area[2] - insetX; x += 2) {
                int a = before.pixels.getPixel(x, y), b = after.pixels.getPixel(x, y);
                total += Math.abs((a >>> 24) - (b >>> 24));
                total += Math.abs(((a >>> 16) & 255) - ((b >>> 16) & 255));
                total += Math.abs(((a >>> 8) & 255) - ((b >>> 8) & 255));
                samples += 3;
            }
        }
        assertTrue("Portrait image sampling area must be nonempty", samples > 0);
        return (double) total / samples;
    }

    private static int redPixels(Frame frame, WorldDominationGame app, Rectangle icon)
            throws Exception {
        int[] area = screenBounds(app, icon);
        int red = 0;
        for (int y = area[1]; y < area[3]; y++) {
            for (int x = area[0]; x < area[2]; x++) {
                int pixel = frame.pixels.getPixel(x, y);
                int r = pixel >>> 24, g = (pixel >>> 16) & 255, b = (pixel >>> 8) & 255;
                if (r > 90 && r > g * 1.5f && r > b * 1.3f) red++;
            }
        }
        return red;
    }

    private static double luminance(Frame frame, WorldDominationGame app, Rectangle card)
            throws Exception {
        int[] area = screenBounds(app, card);
        int insetX = Math.max(2, (area[2] - area[0]) / 8);
        int insetY = Math.max(2, (area[3] - area[1]) / 8);
        double total = 0;
        int samples = 0;
        for (int y = area[1] + insetY; y < area[3] - insetY; y += 2) {
            for (int x = area[0] + insetX; x < area[2] - insetX; x += 2) {
                int pixel = frame.pixels.getPixel(x, y);
                total += 0.2126 * (pixel >>> 24)
                        + 0.7152 * ((pixel >>> 16) & 255)
                        + 0.0722 * ((pixel >>> 8) & 255);
                samples++;
            }
        }
        assertTrue("Portrait brightness sampling area must be nonempty", samples > 0);
        return total / samples;
    }

    private static final class Frame implements AutoCloseable {
        final Pixmap pixels;
        Frame(Pixmap pixels) { this.pixels = pixels; }
        void save(String name) {
            File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getExternalFilesDir("beta-ui-evidence");
            assertNotNull(directory);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), pixels);
        }
        @Override public void close() { pixels.dispose(); }
    }
}
