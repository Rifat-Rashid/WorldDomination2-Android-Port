package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SourceSetupButtonsInstrumentedTest {
    @Test public void earlySourceFramesUseRegistrationAtBothSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            CountDownLatch published = new CountDownLatch(1), release = new CountDownLatch(1);
            Constructor<OriginalFlashAnimationAssets> ctor = OriginalFlashAnimationAssets.class
                    .getDeclaredConstructor(Runnable.class);
            ctor.setAccessible(true);
            OriginalFlashAnimationAssets assets = ctor.newInstance((Runnable) () -> {
                published.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            Field field = WorldDominationGame.class.getDeclaredField("flashAnimationAssets");
            field.setAccessible(true);
            Field batchField = WorldDominationGame.class.getDeclaredField("batch");
            batchField.setAccessible(true);
            SpriteBatch batch = (SpriteBatch) batchField.get(app);
            Object previous = field.get(app);
            Matrix4 projection = new Matrix4(batch.getProjectionMatrix());
            float color = batch.getPackedColor();
            Method draw = WorldDominationGame.class.getDeclaredMethod("drawSourceSetupButton", Rectangle.class, boolean.class);
            draw.setAccessible(true);
            try {
                assertTrue(published.await(60, TimeUnit.SECONDS));
                assets.update();
                assertTrue(assets.areSetupPortraitsLoaded());
                assertFalse(assets.isLoaded());
                for (int id : new int[]{4580, 4582}) {
                    TextureRegion region = assets.getShape(id);
                    assertNotNull(region);
                    assertEquals(135, region.getRegionWidth());
                    assertEquals(28, region.getRegionHeight());
                    assertEquals(id == 4580 ? -67.5f : 0f, assets.getShapeOriginX(id), 0f);
                    assertEquals(id == 4580 ? -14f : 0f, assets.getShapeOriginY(id), 0f);
                }
                field.set(app, assets);
                for (int[] size : new int[][]{{800, 600}, {1194, 552}}) {
                    int w = size[0], h = size[1];
                    SetupScreenLayout layout = new SetupScreenLayout();
                    layout.update(w, h, 0, 0, 0, 0);
                    Rectangle[] boxes = {layout.difficultyButtons[0], layout.startButton};
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                        fb.begin(); clear(); batch.begin(); batch.setColor(.2f, .4f, .6f, .8f);
                        float saved = batch.getPackedColor();
                        for (int i = 0; i < 2; i++) {
                            draw.invoke(app, boxes[i], i == 1);
                            assertEquals(saved, batch.getPackedColor(), 0f);
                        }
                        batch.end(); byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true); fb.end();
                        byte[] expected = reference(fb, batch, assets, boxes, w, h, false);
                        byte[] wrong = reference(fb, batch, assets, boxes, w, h, true);
                        evidence("source-setup-buttons-" + w + "x" + h + "-actual.png", actual, w, h);
                        evidence("source-setup-buttons-" + w + "x" + h + "-expected.png", expected, w, h);
                        evidence("source-setup-buttons-" + w + "x" + h + "-wrong.png", wrong, w, h);
                        assertArrayEquals(expected, actual);
                        int changed = 0, painted = 0;
                        for (int i = 0; i < actual.length; i += 4) {
                            if ((actual[i + 3] & 255) != 0) painted++;
                            if (actual[i] != wrong[i] || actual[i+1] != wrong[i+1]
                                    || actual[i+2] != wrong[i+2] || actual[i+3] != wrong[i+3]) changed++;
                        }
                        assertTrue("source art painted", painted > 0);
                        assertTrue("swapped source frames differ", changed > 0);
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    } finally { fb.dispose(); }
                }
            } finally {
                field.set(app, previous); batch.setProjectionMatrix(projection); batch.setPackedColor(color);
                release.countDown(); assets.dispose();
            }
        });
    }
    private static byte[] reference(FrameBuffer fb, SpriteBatch batch, OriginalFlashAnimationAssets assets,
                                    Rectangle[] boxes, int w, int h, boolean swap) {
        fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
        for (int i = 0; i < 2; i++) {
            Rectangle box = boxes[i];
            // Both source frames occupy (-67.5,-14) to (67.5,14) in 4586.
            float scale = Math.min(box.width / 135f, box.height / 28f);
            float cx = box.x + box.width / 2f, cy = box.y + box.height / 2f;
            batch.draw(assets.getShape((i == 1) != swap ? 4582 : 4580),
                    cx - 67.5f * scale, cy - 14f * scale, 135f * scale, 28f * scale);
        }
        batch.end(); byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true); fb.end();
        return pixels;
    }
    private static void clear() { Gdx.gl.glClearColor(0, 0, 0, 0); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT); }
    private static void evidence(String name, byte[] rgba, int w, int h) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir); assertTrue(dir.exists() || dir.mkdirs());
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        try { pixmap.getPixels().put(rgba); pixmap.getPixels().position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), pixmap);
        } finally { pixmap.dispose(); }
    }
}
