package com.example.worlddomination.game.rendering;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLSurfaceView;

import androidx.lifecycle.Lifecycle;
import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Proves the separate early portrait and marker atlases survive EGL context loss. */
@RunWith(AndroidJUnit4.class)
public final class FlashAtlasContextRecoveryInstrumentedTest {
    private static final int[] MARKERS = {2218, 2221, 2224, 2227, 2230, 2233};
    private static final int[] PORTRAITS = {3158, 3160};

    @Test
    public void earlyFlashAtlasRestoresAfterEglContextLoss() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            boolean[] preserveContext = new boolean[1];
            scenario.onActivity(activity -> {
                GLSurfaceView view = (GLSurfaceView) ((AndroidGraphics) activity.getGraphics()).getView();
                preserveContext[0] = view.getPreserveEGLContextOnPause();
                view.setPreserveEGLContextOnPause(false);
            });

            Snapshot before = onGlThread(scenario, () -> {
                WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                Method initialize = WorldDominationGame.class.getDeclaredMethod("initializeGameplayAssets");
                initialize.setAccessible(true);
                initialize.invoke(app);
                Field field = WorldDominationGame.class.getDeclaredField("flashAnimationAssets");
                field.setAccessible(true);
                OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field.get(app);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                while (!(assets.areSetupPortraitsLoaded()
                        && assets.areBattlefieldFlagsLoaded()
                        && assets.areCenterUiAssetsLoaded()) && System.nanoTime() < deadline) {
                    assets.update();
                    Thread.sleep(10);
                }
                assertTrue("Portrait atlas did not finish loading",
                        assets.areSetupPortraitsLoaded());
                assertTrue("Early marker/dialog atlas did not finish loading",
                        assets.areBattlefieldFlagsLoaded()
                                && assets.areCenterUiAssetsLoaded());
                TextureRegion[] regions = new TextureRegion[MARKERS.length + PORTRAITS.length];
                for (int index = 0; index < MARKERS.length; index++) {
                    regions[index] = assets.getShape(MARKERS[index]);
                    assertNotNull("Missing original marker " + MARKERS[index], regions[index]);
                    assertTrue("Generated atlas texture must be managed",
                            regions[index].getTexture().isManaged());
                }
                for (int index = 0; index < PORTRAITS.length; index++) {
                    regions[MARKERS.length + index] = assets.getShape(PORTRAITS[index]);
                    assertNotNull("Missing original portrait " + PORTRAITS[index],
                            regions[MARKERS.length + index]);
                    assertTrue("Portrait atlas texture must be managed",
                            regions[MARKERS.length + index].getTexture().isManaged());
                }
                assertFalse("Portraits and markers must occupy separate staged atlases",
                        regions[0].getTexture() == regions[MARKERS.length].getTexture());
                EGLContext context = EGL14.eglGetCurrentContext();
                EGLDisplay display = EGL14.eglGetCurrentDisplay();
                assertFalse("No EGL context on render thread", EGL14.EGL_NO_CONTEXT.equals(context));
                assertFalse("No EGL display on render thread", EGL14.EGL_NO_DISPLAY.equals(display));
                int[] clientVersion = new int[1];
                assertTrue("Original EGL context must be valid before pause",
                        EGL14.eglQueryContext(display, context,
                                EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, 0));
                assertTrue("Expected an OpenGL ES context", clientVersion[0] >= 2);
                return new Snapshot(app, assets, regions, display, context, render(regions));
            });

            // ActivityScenario lifecycle calls run outside the GL runnable. Disabling
            // preservation above must destroy the old context before resume.
            assertTrue("The instrumentation thread must be able to query the live context",
                    EGL14.eglQueryContext(before.display, before.context,
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0));
            scenario.moveToState(Lifecycle.State.CREATED);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int[] clientVersion = new int[1];
            boolean oldContextValid;
            do {
                oldContextValid = EGL14.eglQueryContext(before.display, before.context,
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, 0);
                if (oldContextValid && System.nanoTime() < deadline) Thread.sleep(20);
            } while (oldContextValid && System.nanoTime() < deadline);
            assertFalse("Pausing must invalidate the original EGL context", oldContextValid);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                GLSurfaceView view = (GLSurfaceView) ((AndroidGraphics) activity.getGraphics()).getView();
                view.setPreserveEGLContextOnPause(preserveContext[0]);
            });

            onGlThread(scenario, () -> {
                EGLContext restoredContext = EGL14.eglGetCurrentContext();
                assertFalse(EGL14.EGL_NO_CONTEXT.equals(restoredContext));
                EGLDisplay restoredDisplay = EGL14.eglGetCurrentDisplay();
                assertTrue("Restored EGL context must be valid",
                        EGL14.eglQueryContext(restoredDisplay, restoredContext,
                                EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0));
                assertSame("The application must survive the pause/resume", before.app,
                        Gdx.app.getApplicationListener());
                Field field = WorldDominationGame.class.getDeclaredField("flashAnimationAssets");
                field.setAccessible(true);
                assertSame("The existing atlas owner must survive", before.assets,
                        field.get(before.app));
                for (int index = 0; index < MARKERS.length; index++) {
                    assertSame("The same source region must be reused", before.regions[index],
                            before.assets.getShape(MARKERS[index]));
                    assertTrue(before.regions[index].getTexture().isManaged());
                }
                for (int index = 0; index < PORTRAITS.length; index++) {
                    int regionIndex = MARKERS.length + index;
                    assertSame("The same portrait region must be reused",
                            before.regions[regionIndex], before.assets.getShape(PORTRAITS[index]));
                    assertTrue(before.regions[regionIndex].getTexture().isManaged());
                }
                assertEquals("Restored source artwork must render identically",
                        before.pixelHash, render(before.regions));
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                return null;
            });
        }
    }

    private static <T> T onGlThread(GameActivityScenario scenario,
                                    Callable<T> work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try {
                result.set(work.call());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        }));
        assertTrue("Flash atlas GL task timed out", done.await(90, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Flash atlas GL task", failure.get());
        return result.get();
    }

    private static long render(TextureRegion[] regions) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 352, 128, false);
        SpriteBatch batch = new SpriteBatch();
        Pixmap pixels = null;
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 352, 128));
            buffer.begin();
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            for (int index = 0; index < regions.length; index++) {
                batch.draw(regions[index], 12 + index * 40, 40, 32, 32);
            }
            batch.end();
            pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 352, 128);
            buffer.end();
            long hash = 0xcbf29ce484222325L;
            int[] paintedByRegion = new int[regions.length];
            for (int y = 0; y < pixels.getHeight(); y++) {
                for (int x = 0; x < pixels.getWidth(); x++) {
                    int color = pixels.getPixel(x, y);
                    if ((color & 0xff) != 0) {
                        for (int index = 0; index < regions.length; index++) {
                            if (x >= 12 + index * 40 && x < 44 + index * 40)
                                paintedByRegion[index]++;
                        }
                    }
                    hash = (hash ^ color) * 0x100000001b3L;
                }
            }
            for (int index = 0; index < regions.length; index++) {
                assertTrue("Early atlas region " + index + " rendered no visible pixels",
                        paintedByRegion[index] > 0);
            }
            return hash;
        } finally {
            if (pixels != null) pixels.dispose();
            batch.dispose();
            buffer.dispose();
        }
    }

    private static final class Snapshot {
        final WorldDominationGame app;
        final OriginalFlashAnimationAssets assets;
        final TextureRegion[] regions;
        final EGLDisplay display;
        final EGLContext context;
        final long pixelHash;

        Snapshot(WorldDominationGame app, OriginalFlashAnimationAssets assets,
                 TextureRegion[] regions, EGLDisplay display, EGLContext context,
                 long pixelHash) {
            this.app = app;
            this.assets = assets;
            this.regions = regions;
            this.display = display;
            this.context = context;
            this.pixelHash = pixelHash;
        }
    }
}
