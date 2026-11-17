package com.example.worlddomination.game.assets;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.File;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import com.example.worlddomination.game.ui.SplashScreenState;
import com.example.worlddomination.testing.GameActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real World setup waits for the early atlas, independent of the later battle atlas. */
@RunWith(AndroidJUnit4.class)
public final class SetupPortraitReadinessInstrumentedTest {
    @Test public void tapWaitsForPortraitUpload() throws Exception { verifyTransition(false); }
    @Test public void backWaitsForPortraitUpload() throws Exception { verifyTransition(true); }

    private void verifyTransition(boolean useBack) throws Exception {
        CountDownLatch beforePublication = new CountDownLatch(1);
        CountDownLatch allowPortrait = new CountDownLatch(1);
        CountDownLatch afterPublication = new CountDownLatch(1);
        CountDownLatch allowLater = new CountDownLatch(1);
        AtomicReference<OriginalFlashAnimationAssets> held = new AtomicReference<>();
        AtomicReference<Double> beforeLoading = new AtomicReference<>();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            try {
                onGl(scenario, () -> {
                    WorldDominationGame app = app();
                    assertNull(field("flashAnimationAssets").get(app));
                    held.set(new OriginalFlashAnimationAssets(
                            () -> await(beforePublication, allowPortrait),
                            () -> await(afterPublication, allowLater)));
                    field("flashAnimationAssets").set(app, held.get());
                    GameSetup setup = new GameSetup(0L);
                    field("setup").set(app, setup);
                    assertTrue(splash(app).isVisible());
                    beforeLoading.set(setupRng(setup));
                    if (useBack) {
                        assertTrue(app.keyDown(Input.Keys.BACK));
                        assertTrue(app.keyDown(Input.Keys.BACK));
                    } else {
                        app.touchDown(1, 1, 0, 0); app.touchUp(1, 1, 0, 0);
                        app.touchDown(1, 1, 0, 0); app.touchUp(1, 1, 0, 0);
                    }
                    assertTrue(splash(app).isVisible());
                    assertTrue(setup.getSelectedOpponentIds().isEmpty());
                    assertEquals(beforeLoading.get(), setupRng(setup), 0d);
                    return null;
                });
                assertTrue("early loader did not reach publication", beforePublication.await(60, TimeUnit.SECONDS));
                onGl(scenario, () -> {
                    WorldDominationGame app = app();
                    app.render();
                    assertTrue(splash(app).isVisible());
                    screenshot(useBack ? "setup-wait-back.png" : "setup-wait-tap.png");
                    SetupScreenLayout layout = (SetupScreenLayout) field("setupLayout").get(app);
                    tap(app, layout.leaderCards[4]);
                    assertTrue(splash(app).isVisible());
                    assertTrue(((GameSetup) field("setup").get(app)).getSelectedOpponentIds().isEmpty());
                    return null;
                });
                allowPortrait.countDown();
                assertTrue("portrait stage did not publish", afterPublication.await(60, TimeUnit.SECONDS));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                boolean ready = false;
                while (!ready && System.nanoTime() < deadline) {
                    ready = onGl(scenario, () -> !splash(app()).isVisible());
                    if (!ready) Thread.sleep(20);
                }
                // onGl posts work and can itself request a frame; this checks
                // eventual transition, not the worker wake in isolation.
                assertTrue("portrait upload must advance World", ready);
                onGl(scenario, () -> {
                    WorldDominationGame app = app();
                    OriginalFlashAnimationAssets assets = held.get();
                    assertTrue(assets.areSetupPortraitsLoaded());
                    assertFalse("later atlas remains blocked", assets.isLoaded());
                    FlashAnimationTimeline portraits = assets.getTimeline(
                            OriginalFlashAnimationAssets.LEADER_PORTRAITS_TIMELINE);
                    assertNotNull(portraits);
                    for (int i = 0; i < 6; i++) {
                        FlashAnimationTimeline.Frame frame = portraits.getFrame(
                                GameSetup.leader(i).portraitFrame - 1);
                        assertNotNull("source portrait frame " + i, frame);
                        assertNotNull("drawable portrait " + i, assets.getShape(frame.shapeId));
                    }
                    GameSetup setup = (GameSetup) field("setup").get(app);
                    assertEquals("loading preserves original setup RNG", beforeLoading.get(), setupRng(setup), 0d);
                    app.render();
                    screenshot(useBack ? "setup-ready-back.png" : "setup-ready-tap.png");
                    SetupScreenLayout layout = (SetupScreenLayout) field("setupLayout").get(app);
                    for (int i = 0; i < 6; i++) {
                        tap(app, layout.leaderCards[i]);
                        assertEquals(Collections.singletonList(i), setup.getSelectedOpponentIds());
                        tap(app, layout.opponentRemoveIcons[0]);
                        assertTrue(setup.getSelectedOpponentIds().isEmpty());
                    }
                    assertEquals("loading and selection do not consume setup RNG", beforeLoading.get(), setupRng(setup), 0d);
                    return null;
                });
            } finally {
                allowPortrait.countDown(); allowLater.countDown();
            }
        }
    }
    private static void screenshot(String name) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = ScreenUtils.getFrameBufferPixmap(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try { PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image, -1, true); }
        finally { image.dispose(); }
    }
    private static void await(CountDownLatch reached, CountDownLatch release) {
        reached.countDown();
        try { if (!release.await(90, TimeUnit.SECONDS)) throw new IllegalStateException("loader test timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private static double setupRng(GameSetup setup) throws Exception {
        Method m = GameSetup.class.getDeclaredMethod("copyGameplayRandom"); m.setAccessible(true);
        return ((ReferenceRandom) m.invoke(setup)).nextDouble();
    }
    private static WorldDominationGame app() { return (WorldDominationGame) Gdx.app.getApplicationListener(); }
    private static SplashScreenState splash(WorldDominationGame app) throws Exception {
        return (SplashScreenState) field("splashState").get(app);
    }
    private static Field field(String name) throws Exception {
        Field f = WorldDominationGame.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void tap(WorldDominationGame app, Rectangle rect) throws Exception {
        Vector2 p = ((Viewport) field("viewport").get(app)).project(new Vector2(rect.x + rect.width / 2, rect.y + rect.height / 2));
        int x = Math.round(p.x), y = Gdx.graphics.getHeight() - Math.round(p.y);
        app.touchDown(x, y, 0, 0); app.touchUp(x, y, 0, 0);
    }
    private static <T> T onGl(GameActivityScenario scenario, Callable<T> work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>(); AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try { result.set(work.call()); } catch (Throwable e) { failure.set(e); } finally { done.countDown(); }
        }));
        assertTrue("GL task timed out", done.await(90, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("GL task failed", failure.get());
        return result.get();
    }
}
