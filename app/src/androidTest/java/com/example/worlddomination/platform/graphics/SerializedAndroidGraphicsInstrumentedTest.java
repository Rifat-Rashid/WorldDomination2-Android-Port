package com.example.worlddomination.platform.graphics;

import android.os.Looper;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Array;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.testing.GameActivityScenario;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Forces the finishing-thread/GL-pause interleaving that previously crashed FBO disposal. */
@RunWith(AndroidJUnit4.class)
public final class SerializedAndroidGraphicsInstrumentedTest {
    @Test public void finishingCacheClearWaitsForFramebufferDisposalInPause() throws Exception {
        CountDownLatch prepared = new CountDownLatch(1);
        CountDownLatch pauseEntered = new CountDownLatch(1);
        CountDownLatch releasePause = new CountDownLatch(1);
        CountDownLatch pauseDisposed = new CountDownLatch(1);
        CountDownLatch destroyed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<MainActivity> owner = new AtomicReference<>();
        AtomicReference<FrameBuffer> buffer = new AtomicReference<>();
        AtomicReference<Thread> glThread = new AtomicReference<>();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            try {
                scenario.onActivity(activity -> {
                    owner.set(activity);
                    assertTrue(activity.getGraphics() instanceof SerializedAndroidGraphics);
                    activity.postRunnable(() -> {
                        try {
                            glThread.set(Thread.currentThread());
                            FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, 8, 8, false);
                            buffer.set(target);
                            assertTrue(isRegistered(activity, target));
                            activity.addLifecycleListener(new LifecycleListener() {
                                @Override public void resume() { }
                                @Override public void pause() {
                                    pauseEntered.countDown();
                                    try {
                                        assertTrue("Test must release the delayed pause",
                                                releasePause.await(4, TimeUnit.SECONDS));
                                        assertSame(glThread.get(), Thread.currentThread());
                                        assertTrue("Finishing must not clear the live FBO registration",
                                                isRegistered(activity, target));
                                        target.dispose();
                                        assertFalse(isRegistered(activity, target));
                                        assertEquals(0, target.getColorBufferTexture()
                                                .getTextureObjectHandle());
                                    } catch (Throwable error) {
                                        failure.compareAndSet(null, error);
                                    } finally {
                                        pauseDisposed.countDown();
                                    }
                                }
                                @Override public void dispose() {
                                    destroyed.countDown();
                                }
                            });
                        } catch (Throwable error) {
                            failure.compareAndSet(null, error);
                        } finally {
                            prepared.countDown();
                        }
                    });
                    activity.getGraphics().requestRendering();
                });
                assertTrue("GL setup timed out", prepared.await(15, TimeUnit.SECONDS));
                rethrow(failure);
                // Asynchronous: the main thread must be free to enter AndroidApplication.onPause.
                owner.get().runOnUiThread(owner.get()::finish);
                assertTrue("Finish never reached the real GL pause callback",
                        pauseEntered.await(10, TimeUnit.SECONDS));
                assertTrue("Cache clear must block behind the unfinished GL pause callback",
                        awaitsCacheClearMonitor(Looper.getMainLooper().getThread()));
                assertEquals("Disposal must remain pending until the latch is released",
                        1L, pauseDisposed.getCount());
                assertTrue(isRegistered(owner.get(), buffer.get()));
            } finally {
                // Always unblock Android's main/GL threads, including assertion failures.
                releasePause.countDown();
            }
            assertTrue("GL pause disposal timed out", pauseDisposed.await(10, TimeUnit.SECONDS));
            assertTrue("Finish did not continue to listener disposal",
                    destroyed.await(10, TimeUnit.SECONDS));
            rethrow(failure);
            assertFalse("Finishing must clear the managed FBO registry",
                    isRegistered(owner.get(), buffer.get()));
            // Synchronizes with the actual destroyed Activity; close remains safe and idempotent.
            scenario.moveToState(Lifecycle.State.DESTROYED);
        }
    }

    private static boolean awaitsCacheClearMonitor(Thread mainThread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            if (mainThread.getState() == Thread.State.BLOCKED) {
                for (StackTraceElement frame : mainThread.getStackTrace()) {
                    if (frame.getClassName().equals(SerializedAndroidGraphics.class.getName())
                            && frame.getMethodName().equals("clearManagedCaches")) return true;
                }
            }
            Thread.yield();
        } while (System.nanoTime() < deadline);
        return false;
    }

    private static boolean isRegistered(Application owner, FrameBuffer target) throws Exception {
        Field field = GLFrameBuffer.class.getDeclaredField("buffers");
        field.setAccessible(true);
        Object value = ((Map<?, ?>) field.get(null)).get(owner);
        if (!(value instanceof Array<?>)) return false;
        for (Object candidate : (Array<?>) value) if (candidate == target) return true;
        return false;
    }

    private static void rethrow(AtomicReference<Throwable> failure) {
        if (failure.get() != null) throw new AssertionError("Finish lifecycle regression", failure.get());
    }
}
