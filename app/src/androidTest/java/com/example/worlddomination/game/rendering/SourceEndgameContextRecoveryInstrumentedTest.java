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
import com.example.worlddomination.game.assets.OriginalEndgameAssets;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Proves the managed endgame atlas survives destruction of its actual EGL context. */
@RunWith(AndroidJUnit4.class)
public final class SourceEndgameContextRecoveryInstrumentedTest {
    private static final int[] LEAVES = {103, 3253, 4117, 4484, 4580, 4582, 4584,
            5884, 5886, 5888, 5891, 5894, 5902};

    @Test public void allEndgameLeavesRestoreAfterEglContextLoss() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            boolean[] previousPreserve = new boolean[1];
            OriginalEndgameAssets assets = new OriginalEndgameAssets();
            try {
                scenario.onActivity(activity -> {
                    GLSurfaceView view = (GLSurfaceView) ((AndroidGraphics)
                            activity.getGraphics()).getView();
                    previousPreserve[0] = view.getPreserveEGLContextOnPause();
                    view.setPreserveEGLContextOnPause(false);
                });
                Snapshot before = onGlThread(scenario, () -> {
                    assets.start();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!assets.update() && System.nanoTime() < deadline)
                        Thread.sleep(10);
                    assertTrue("Endgame atlas did not finish loading", assets.isReady());
                    TextureRegion[] regions = new TextureRegion[LEAVES.length];
                    for (int i = 0; i < LEAVES.length; i++) {
                        regions[i] = assets.getShape(LEAVES[i]);
                        assertNotNull("Missing endgame leaf " + LEAVES[i], regions[i]);
                        assertTrue("Endgame texture must be managed",
                                regions[i].getTexture().isManaged());
                    }
                    EGLContext context = EGL14.eglGetCurrentContext();
                    EGLDisplay display = EGL14.eglGetCurrentDisplay();
                    assertFalse(EGL14.EGL_NO_CONTEXT.equals(context));
                    assertFalse(EGL14.EGL_NO_DISPLAY.equals(display));
                    int[] version = new int[1];
                    assertTrue(EGL14.eglQueryContext(display, context,
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, version, 0));
                    assertTrue(version[0] >= 2);
                    long hash = render(regions);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    return new Snapshot(assets, regions, display, context, hash);
                });

                assertTrue(EGL14.eglQueryContext(before.display, before.context,
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0));
                scenario.moveToState(Lifecycle.State.CREATED);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean oldContextValid;
                do {
                    oldContextValid = EGL14.eglQueryContext(before.display,
                            before.context, EGL14.EGL_CONTEXT_CLIENT_VERSION,
                            new int[1], 0);
                    if (oldContextValid && System.nanoTime() < deadline)
                        Thread.sleep(20);
                } while (oldContextValid && System.nanoTime() < deadline);
                assertFalse("Pause must destroy the original EGL context", oldContextValid);
                scenario.moveToState(Lifecycle.State.RESUMED);
                scenario.onActivity(activity -> {
                    GLSurfaceView view = (GLSurfaceView) ((AndroidGraphics)
                            activity.getGraphics()).getView();
                    view.setPreserveEGLContextOnPause(previousPreserve[0]);
                });

                onGlThread(scenario, () -> {
                    EGLContext restored = EGL14.eglGetCurrentContext();
                    EGLDisplay display = EGL14.eglGetCurrentDisplay();
                    assertFalse(EGL14.EGL_NO_CONTEXT.equals(restored));
                    assertTrue(EGL14.eglQueryContext(display, restored,
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0));
                    assertSame("The same atlas owner must survive", before.assets, assets);
                    assertTrue(assets.isReady());
                    for (int i = 0; i < LEAVES.length; i++) {
                        assertSame("The same managed region must survive: " + LEAVES[i],
                                before.regions[i], assets.getShape(LEAVES[i]));
                        assertTrue(before.regions[i].getTexture().isManaged());
                    }
                    assertEquals("Every restored leaf must render identically",
                            before.pixelHash, render(before.regions));
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    return null;
                });
            } finally {
                // Own no player state or persistent preferences; release our atlas on GL.
                scenario.moveToState(Lifecycle.State.RESUMED);
                scenario.onActivity(activity -> ((GLSurfaceView) ((AndroidGraphics)
                        activity.getGraphics()).getView())
                        .setPreserveEGLContextOnPause(previousPreserve[0]));
                onGlThread(scenario, () -> { assets.dispose(); return null; });
            }
        }
    }

    private static <T> T onGlThread(GameActivityScenario scenario,
                                    Callable<T> work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try { result.set(work.call()); }
            catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        }));
        assertTrue("Endgame atlas GL task timed out", done.await(90, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Endgame atlas GL task", failure.get());
        return result.get();
    }

    private static long render(TextureRegion[] regions) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 320, 256, false);
        SpriteBatch batch = new SpriteBatch();
        Pixmap pixels = null;
        boolean bufferStarted = false;
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 320, 256));
            buffer.begin();
            bufferStarted = true;
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            for (int i = 0; i < regions.length; i++)
                batch.draw(regions[i], 4 + (i % 4) * 80,
                        4 + (i / 4) * 64, 72, 56);
            batch.end();
            pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 320, 256);
            buffer.end();
            bufferStarted = false;
            long hash = 0xcbf29ce484222325L;
            int visible = 0;
            for (int y = 0; y < pixels.getHeight(); y++) {
                for (int x = 0; x < pixels.getWidth(); x++) {
                    int color = pixels.getPixel(x, y);
                    if ((color & 255) != 0) visible++;
                    hash = (hash ^ color) * 0x100000001b3L;
                }
            }
            assertTrue("Restored endgame atlas cannot be blank", visible > 0);
            return hash;
        } finally {
            if (bufferStarted) buffer.end();
            if (pixels != null) pixels.dispose();
            batch.dispose();
            buffer.dispose();
        }
    }

    private static final class Snapshot {
        final OriginalEndgameAssets assets;
        final TextureRegion[] regions;
        final EGLDisplay display;
        final EGLContext context;
        final long pixelHash;
        Snapshot(OriginalEndgameAssets assets, TextureRegion[] regions,
                 EGLDisplay display, EGLContext context, long pixelHash) {
            this.assets = assets;
            this.regions = regions;
            this.display = display;
            this.context = context;
            this.pixelHash = pixelHash;
        }
    }
}
