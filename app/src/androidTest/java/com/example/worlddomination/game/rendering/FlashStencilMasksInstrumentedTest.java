package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Executes the native mask shader against an Android GLES stencil buffer. */
@RunWith(AndroidJUnit4.class)
public final class FlashStencilMasksInstrumentedTest {
    @Test
    public void coverageIsInvisibleAndClipsOnlyTheFollowingArtwork() throws Exception {
        renderTest(false);
    }

    @Test
    public void nestedMasksIntersectAndPoppingRestoresTheParentAndUnmaskedDraws() throws Exception {
        renderTest(true);
    }

    private static void renderTest(boolean nested) throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                FrameBuffer buffer = null;
                SpriteBatch batch = null;
                Texture texture = null;
                FlashStencilMasks masks = new FlashStencilMasks();
                try {
                    buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 64, 64, false, true);
                    batch = new SpriteBatch();
                    Pixmap pixels = new Pixmap(2, 1, Pixmap.Format.RGBA8888);
                    pixels.setColor(1, 1, 1, 1);
                    pixels.drawPixel(0, 0); // Other texel remains transparent.
                    texture = new Texture(pixels);
                    pixels.dispose();
                    buffer.begin();
                    Gdx.gl.glClearColor(1, 0, 0, 1);
                    Gdx.gl.glStencilMask(0xff);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 64, 64));
                    batch.begin();
                    masks.beginCoverage(batch);
                    batch.draw(texture, 0, 0, 64, 64);
                    masks.endCoverage(batch);
                    if (nested) {
                        masks.beginCoverage(batch);
                        batch.draw(texture, 0, 0, 64, 32);
                        masks.endCoverage(batch);
                    }
                    batch.setColor(0, 1, 0, 1);
                    // Draw using the opaque texel so only stencil limits this fill.
                    batch.draw(texture, 0, 0, 64, 64, 0, 0, 1, 1, false, false);
                    if (nested) {
                        masks.pop(batch);
                        batch.setColor(0, 0, 1, 1);
                        batch.draw(texture, 0, 32, 64, 32, 0, 0, 1, 1, false, false);
                    }
                    masks.pop(batch);
                    batch.setColor(1, 1, 0, 1);
                    batch.draw(texture, 48, 48, 8, 8, 0, 0, 1, 1, false, false);
                    batch.end();
                    byte[] result = ScreenUtils.getFrameBufferPixels(0, 0, 64, 64, false);
                    assertPixel(result, 8, 8, 0, 255, 0);
                    assertPixel(result, 48, 8, 255, 0, 0); // Transparent mask texel.
                    assertPixel(result, 52, 52, 255, 255, 0); // State restored after pop.
                    if (nested) assertPixel(result, 8, 48, 0, 0, 255);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    if (buffer != null) buffer.end();
                    if (batch != null) batch.dispose();
                    if (texture != null) texture.dispose();
                    if (buffer != null) buffer.dispose();
                    masks.dispose();
                    done.countDown();
                }
            }));
            assertTrue("Android GL thread did not execute the mask test", done.await(30, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Native GLES mask regression", failure.get());
        }
    }

    private static void assertPixel(byte[] pixels, int x, int y, int red, int green, int blue) {
        int offset = (y * 64 + x) * 4;
        assertEquals("red at " + x + "," + y, red, pixels[offset] & 255);
        assertEquals("green at " + x + "," + y, green, pixels[offset + 1] & 255);
        assertEquals("blue at " + x + "," + y, blue, pixels[offset + 2] & 255);
    }
}
