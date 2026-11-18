package com.example.worlddomination.game.rendering;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLSurfaceView;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ScorePanelLayout;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import com.example.worlddomination.testing.GameActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Captures actual first resumed app drawing, before AndroidGraphics swaps the EGL surface. */
@RunWith(AndroidJUnit4.class)
public final class ResourceModalContextRecoveryInstrumentedTest {
    @Test
    public void resourceModalRestoresAfterEglContextLoss() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            boolean[] preserve = new boolean[1];
            Observer[] installed = new Observer[1];
            try {
                scenario.onActivity(activity -> {
                    GLSurfaceView surface = surface(activity);
                    preserve[0] = surface.getPreserveEGLContextOnPause();
                    surface.setPreserveEGLContextOnPause(false);
                });
                Observer observer = onGlThread(scenario, activity -> {
                    WorldDominationGame app = (WorldDominationGame) activity.getApplicationListener();
                    openResourcesByTouch(app);
                    Observer result = new Observer(app);
                    result.before = capture(app, "resource-modal-context-before.png");
                    result.before.assertVisible();
                    assertTrue("Original EGL context is queryable", contextValid(result.display, result.context));
                    installed[0] = result;
                    listenerField().set(activity, result);
                    assertSame("AndroidApplication dynamically exposes the test observer", result,
                            activity.getApplicationListener());
                    return result;
                });

                scenario.moveToState(Lifecycle.State.CREATED);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (contextValid(observer.display, observer.context) && System.nanoTime() < deadline) {
                    Thread.sleep(20);
                }
                assertFalse("Forced pause invalidates the old EGL context",
                        contextValid(observer.display, observer.context));
                scenario.moveToState(Lifecycle.State.RESUMED);
                assertTrue("Actual first resumed render was observed", observer.firstFrame.await(15, TimeUnit.SECONDS));
                if (observer.failure.get() != null) {
                    throw new AssertionError("First resumed Resources frame", observer.failure.get());
                }
                assertEquals("One delegated application resume", 1, observer.resumes);
                assertNotNull("First resumed framebuffer captured", observer.after);
                observer.before.assertSamePixels(observer.after);

                onGlThread(scenario, activity -> {
                    observer.assertRetainedState();
                    Rectangle cancel = (Rectangle) field("modalCancel").get(observer.app);
                    tap(observer.app, cancel.x + cancel.width / 2f, cancel.y + cancel.height / 2f);
                    assertNull("Restored Cancel clears selection", observer.game.getSelectedAction());
                    assertFalse("Restored Cancel closes the modal", field("modalOpen").getBoolean(observer.app));
                    assertEquals("Cancel does not queue Resources", 0, observer.game.getQueuedActionCount());
                    assertEquals("Cancel preserves funds", observer.money, observer.game.getHuman().moneyMillions);
                    assertEquals("Cancel preserves canonical RNG", observer.random,
                            observer.game.createPresentationRandomCopy().nextDouble(), 0d);
                    return null;
                });
            } finally {
                // Restore the delegate on the GL thread before GameActivityScenario disposes it.
                try {
                    if (installed[0] != null && scenario.getState() != Lifecycle.State.DESTROYED) {
                        scenario.moveToState(Lifecycle.State.RESUMED);
                        onGlThread(scenario, activity -> {
                            if (activity.getApplicationListener() == installed[0]) {
                                listenerField().set(activity, installed[0].app);
                            }
                            return null;
                        });
                    }
                } finally {
                    scenario.onActivity(activity -> surface(activity).setPreserveEGLContextOnPause(preserve[0]));
                }
            }
        }
    }

    private static void openResourcesByTouch(WorldDominationGame app) throws Exception {
        GameSetup setup = new GameSetup(7127L);
        setup.setDifficulty(1);
        field("setup").set(app, setup);
        app.render();
        Viewport viewport = (Viewport) field("viewport").get(app);
        tap(app, viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        OriginalFlashAnimationAssets assets;
        do {
            app.render();
            assets = (OriginalFlashAnimationAssets) field("flashAnimationAssets").get(app);
            if (assets != null && assets.areSetupPortraitsLoaded()) break;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        assertNotNull("Setup atlas requested by splash touch", assets);
        assertTrue("Setup portraits ready", assets.areSetupPortraitsLoaded());
        app.render();
        SetupScreenLayout layout = (SetupScreenLayout) field("setupLayout").get(app);
        Rectangle leader = layout.leaderCards[0];
        tap(app, leader.x + leader.width / 2f, leader.y + leader.height / 2f);
        assertTrue("Rival selected through setup touch", setup.canStart());
        tap(app, layout.startButton.x + layout.startButton.width / 2f,
                layout.startButton.y + layout.startButton.height / 2f);
        assertNotNull("Normal start touch constructs the match", field("game").get(app));
        while (!assets.isLoaded() && System.nanoTime() < deadline) {
            assets.update();
            Thread.sleep(10);
        }
        assertTrue("Battle artwork warmed before the baseline", assets.isLoaded());
        app.render();
        Rectangle resource = ((Rectangle[]) field("navRows").get(app))[GameState.Action.RESOURCES.ordinal()];
        tap(app, resource.x + resource.width / 2f, resource.y + resource.height / 2f);
        GameState game = (GameState) field("game").get(app);
        assertEquals("Resources selected by navigation touch", GameState.Action.RESOURCES, game.getSelectedAction());
        assertTrue("Resources modal open", field("modalOpen").getBoolean(app));
        assertEquals("No action accepted for this recovery test", 0, game.getQueuedActionCount());
        app.render();
    }

    private static final class Observer implements ApplicationListener {
        final WorldDominationGame app;
        final GameState game;
        final int money, budget, turn;
        final double random;
        final EGLDisplay display = EGL14.eglGetCurrentDisplay();
        final EGLContext context = EGL14.eglGetCurrentContext();
        final CountDownLatch firstFrame = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Snapshot before, after;
        boolean captureNextRender;
        int resumes;

        Observer(WorldDominationGame app) throws Exception {
            this.app = app;
            game = (GameState) field("game").get(app);
            money = game.getHuman().moneyMillions;
            budget = game.getHuman().turnMoneyMillions;
            turn = game.getTurn();
            random = game.createPresentationRandomCopy().nextDouble();
        }

        void assertRetainedState() throws Exception {
            assertSame("Retained canonical match", game, field("game").get(app));
            assertEquals("Retained Resources action", GameState.Action.RESOURCES, game.getSelectedAction());
            assertTrue("Retained open modal", field("modalOpen").getBoolean(app));
            assertEquals("Retained funds", money, game.getHuman().moneyMillions);
            assertEquals("Retained spending budget", budget, game.getHuman().turnMoneyMillions);
            assertEquals("No turn advanced", turn, game.getTurn());
            assertEquals("No accepted action", 0, game.getQueuedActionCount());
            assertEquals("Retained canonical random stream", random,
                    game.createPresentationRandomCopy().nextDouble(), 0d);
        }

        @Override public void create() { app.create(); }
        @Override public void resize(int width, int height) { app.resize(width, height); }
        @Override public void pause() { app.pause(); }
        @Override public void dispose() { app.dispose(); }
        @Override public void resume() {
            app.resume();
            resumes++;
            captureNextRender = true;
        }
        @Override public void render() {
            boolean capture = captureNextRender;
            captureNextRender = false;
            try {
                app.render();
                if (capture) {
                    assertRetainedState();
                    assertTrue("Restored EGL context is valid",
                            contextValid(EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentContext()));
                    after = capture(app, "resource-modal-context-after.png");
                    after.assertVisible();
                    before.assertSamePixels(after);
                }
            } catch (Throwable error) {
                if (!capture) throw new AssertionError("Delegated app render", error);
                failure.set(error);
            } finally {
                if (capture) firstFrame.countDown();
            }
        }
    }

    private static Snapshot capture(WorldDominationGame app, String filename) throws Exception {
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull("Evidence directory", directory);
        assertTrue("Evidence directory exists", directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        PixmapIO.PNG writer = new PixmapIO.PNG();
        try {
            image.getPixels().put(pixels).position(0);
            writer.setFlipY(false);
            writer.write(Gdx.files.absolute(new File(directory, filename).getAbsolutePath()), image);
        } finally {
            writer.dispose();
            image.dispose();
        }
        Rectangle modal = (Rectangle) field("modal").get(app);
        Rectangle score = new Rectangle();
        ScorePanelLayout.valueBounds((Rectangle) field("scorePanel").get(app), score);
        Rectangle[] bounds = {
                sourceBounds(modal, 8, 5, 190, 20),
                sourceBounds(modal, 237, 65, 120, 30),
                sourceBounds(modal, 33, 251, 45, 13),
                sourceBounds(modal, 284, 251, 43, 13),
                score
        };
        String[] names = {"header", "resource information", "Cancel glyphs", "Accept glyphs", "HUD score"};
        Crop[] crops = new Crop[bounds.length];
        Viewport viewport = (Viewport) field("viewport").get(app);
        for (int i = 0; i < bounds.length; i++) {
            Rectangle b = bounds[i];
            Vector2 lower = viewport.project(new Vector2(b.x, b.y));
            Vector2 upper = viewport.project(new Vector2(b.x + b.width, b.y + b.height));
            int left = Math.max(0, (int) Math.floor(lower.x));
            int top = Math.max(0, height - (int) Math.ceil(upper.y));
            int right = Math.min(width, (int) Math.ceil(upper.x));
            int bottom = Math.min(height, height - (int) Math.floor(lower.y));
            assertTrue(names[i] + " ROI has area", right > left && bottom > top);
            byte[] rgba = new byte[(right - left) * (bottom - top) * 4];
            for (int row = top; row < bottom; row++) {
                System.arraycopy(pixels, (row * width + left) * 4, rgba,
                        (row - top) * (right - left) * 4, (right - left) * 4);
            }
            crops[i] = new Crop(names[i], left, top, right - left, bottom - top, rgba);
        }
        return new Snapshot(width, height, crops);
    }

    private static Rectangle sourceBounds(Rectangle modal, float x, float top, float width, float height) {
        return new Rectangle(modal.x + x * modal.width / 365f,
                modal.y + modal.height - (top + height) * modal.height / 275f,
                width * modal.width / 365f, height * modal.height / 275f);
    }

    private static final class Crop {
        final String name;
        final int x, y, width, height;
        final byte[] rgba;
        Crop(String name, int x, int y, int width, int height, byte[] rgba) {
            this.name = name; this.x = x; this.y = y; this.width = width; this.height = height; this.rgba = rgba;
        }
        void assertVisible() {
            int white = 0;
            for (int i = 0; i < rgba.length; i += 4) {
                if ((rgba[i] & 255) > 220 && (rgba[i + 1] & 255) > 220
                        && (rgba[i + 2] & 255) > 220 && (rgba[i + 3] & 255) > 220) white++;
            }
            assertTrue(name + " contains visible white glyph ink, count=" + white, white > 12);
        }
    }

    private static final class Snapshot {
        final int width, height;
        final Crop[] crops;
        Snapshot(int width, int height, Crop[] crops) { this.width = width; this.height = height; this.crops = crops; }
        void assertVisible() { for (Crop crop : crops) crop.assertVisible(); }
        void assertSamePixels(Snapshot actual) {
            assertEquals("Framebuffer width", width, actual.width);
            assertEquals("Framebuffer height", height, actual.height);
            for (int i = 0; i < crops.length; i++) {
                Crop expected = crops[i], restored = actual.crops[i];
                assertEquals(expected.name + " x", expected.x, restored.x);
                assertEquals(expected.name + " y", expected.y, restored.y);
                assertEquals(expected.name + " width", expected.width, restored.width);
                assertEquals(expected.name + " height", expected.height, restored.height);
                assertArrayEquals("First resumed " + expected.name + " pixels", expected.rgba, restored.rgba);
            }
        }
    }

    private static boolean contextValid(EGLDisplay display, EGLContext context) {
        return !EGL14.EGL_NO_CONTEXT.equals(context) && EGL14.eglQueryContext(display, context,
                EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0);
    }

    private static GLSurfaceView surface(MainActivity activity) {
        return (GLSurfaceView) ((AndroidGraphics) activity.getGraphics()).getView();
    }

    private static void tap(WorldDominationGame app, float x, float y) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 screen = viewport.project(new Vector2(x, y));
        int screenX = Math.round(screen.x), screenY = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(screenX, screenY, 0, 0);
        app.touchUp(screenX, screenY, 0, 0);
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static Field listenerField() throws Exception {
        Field result = AndroidApplication.class.getDeclaredField("listener");
        result.setAccessible(true);
        return result;
    }

    private interface GlWork<T> { T call(MainActivity activity) throws Exception; }

    private static <T> T onGlThread(GameActivityScenario scenario, GlWork<T> work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try { result.set(work.call(activity)); }
            catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        }));
        assertTrue("Resources GL task timed out", done.await(90, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Resources GL task", failure.get());
        return result.get();
    }
}
