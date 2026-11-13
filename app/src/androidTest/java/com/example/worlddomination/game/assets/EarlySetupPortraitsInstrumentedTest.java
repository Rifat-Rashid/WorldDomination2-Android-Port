package com.example.worlddomination.game.assets;

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
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies real source portraits are drawable before unrelated Flash art is ready. */
@RunWith(AndroidJUnit4.class)
public final class EarlySetupPortraitsInstrumentedTest {
    @Test
    public void everySourceLeaderPortraitPaintsBeforeFlagDialogAndBattleStages() throws Exception {
        CountDownLatch portraitPublished = new CountDownLatch(1);
        CountDownLatch allowLaterStages = new CountDownLatch(1);
        AtomicReference<OriginalFlashAnimationAssets> owned = new AtomicReference<>();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            try {
                onGlThread(scenario, () -> {
                    owned.set(new OriginalFlashAnimationAssets(() -> {
                        portraitPublished.countDown();
                        try {
                            if (!allowLaterStages.await(90, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Later Flash stages were not released");
                            }
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Flash loader interrupted", error);
                        }
                    }));
                    return null;
                });
                assertTrue("Portrait stage was not published",
                        portraitPublished.await(60, TimeUnit.SECONDS));

                onGlThread(scenario, () -> {
                    OriginalFlashAnimationAssets assets = owned.get();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!assets.areSetupPortraitsLoaded() && System.nanoTime() < deadline) {
                        assets.update();
                        Thread.sleep(10);
                    }
                    assertTrue("Stage-one portrait atlas was not uploaded",
                            assets.areSetupPortraitsLoaded());
                    assertFalse("Flag shapes must remain in the later atlas",
                            assets.areBattlefieldFlagsLoaded());
                    assertFalse("Center dialog art must remain in the later atlas",
                            assets.areCenterUiAssetsLoaded());
                    assertFalse("Full battle atlas must still be pending", assets.isLoaded());

                    FlashAnimationTimeline timeline = assets.getTimeline(
                            OriginalFlashAnimationAssets.LEADER_PORTRAITS_TIMELINE);
                    assertNotNull("Source leader timeline must be published first", timeline);
                    assertEquals("Source setup has 19 leaders", 19, GameSetup.leaders().size());
                    TextureRegion[] portraits = new TextureRegion[GameSetup.leaders().size()];
                    for (GameSetup.Leader leader : GameSetup.leaders()) {
                        FlashAnimationTimeline.Frame frame = timeline.getFrame(
                                leader.portraitFrame - 1);
                        assertNotNull("Missing source frame for " + leader.name, frame);
                        assertEquals("Wrong source portrait for " + leader.name,
                                3150 + leader.id * 2, frame.shapeId);
                        TextureRegion region = assets.getShape(frame.shapeId);
                        assertNotNull("Source portrait not cached early: " + leader.name,
                                region);
                        assertTrue("Portrait texture must survive EGL loss",
                                region.getTexture().isManaged());
                        portraits[leader.id] = region;
                    }
                    for (TextureRegion region : portraits) {
                        assertSame("All selectable portraits share one small GPU page",
                                portraits[0].getTexture(), region.getTexture());
                    }
                    assertTrue("Portrait page must fit within one MiB of RGBA pixels",
                            (long) portraits[0].getTexture().getWidth()
                                    * portraits[0].getTexture().getHeight() * 4 <= 1024 * 1024);
                    assertNotNull("Remove-leader art must share portrait stage",
                            assets.getShape(OriginalFlashAnimationAssets.SETUP_REMOVE_LEADER_SHAPE_ID));
                    assertAllPortraitsPaint(portraits);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    return null;
                });

                allowLaterStages.countDown();
                onGlThread(scenario, () -> {
                    OriginalFlashAnimationAssets assets = owned.get();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
                    while (!(assets.areBattlefieldFlagsLoaded()
                            && assets.areCenterUiAssetsLoaded() && assets.isLoaded())
                            && System.nanoTime() < deadline) {
                        assets.update();
                        Thread.sleep(10);
                    }
                    assertTrue("Flag atlas did not finish", assets.areBattlefieldFlagsLoaded());
                    assertTrue("Dialog atlas did not finish", assets.areCenterUiAssetsLoaded());
                    assertTrue("Battle atlas did not finish", assets.isLoaded());
                    assertNotNull("Portrait must remain cached after later stages",
                            assets.getShape(3158));
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    return null;
                });
            } finally {
                allowLaterStages.countDown();
                if (owned.get() != null) {
                    onGlThread(scenario, () -> {
                        OriginalFlashAnimationAssets assets = owned.get();
                        assets.dispose();
                        assets.dispose();
                        assertFalse(assets.areSetupPortraitsLoaded());
                        assertFalse(assets.areBattlefieldFlagsLoaded());
                        assertFalse(assets.isLoaded());
                        assertNull(assets.getShape(3158));
                        return null;
                    });
                }
            }
        }
    }

    private static void assertAllPortraitsPaint(TextureRegion[] regions) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 512, 96, false);
        SpriteBatch batch = new SpriteBatch();
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 512, 96));
            buffer.begin();
            try {
                Gdx.gl.glClearColor(0, 0, 0, 0);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                batch.begin();
                for (int index = 0; index < regions.length; index++) {
                    batch.draw(regions[index], 4 + index * 26, 20, 20, 52);
                }
                batch.end();
                Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 512, 96);
                try {
                    for (int index = 0; index < regions.length; index++) {
                        int painted = 0;
                        for (int y = 0; y < pixels.getHeight(); y++) {
                            for (int x = 4 + index * 26; x < 24 + index * 26; x++) {
                                if ((pixels.getPixel(x, y) & 0xff) != 0) painted++;
                            }
                        }
                        assertTrue("Original leader " + index + " rendered blank", painted > 10);
                    }
                    File directory = InstrumentationRegistry.getInstrumentation()
                            .getTargetContext().getExternalFilesDir("beta-ui-evidence");
                    assertNotNull("Screenshot evidence directory unavailable", directory);
                    PixmapIO.writePNG(Gdx.files.absolute(new File(directory,
                            "early-setup-all-19-portraits.png").getAbsolutePath()), pixels);
                } finally {
                    pixels.dispose();
                }
            } finally {
                if (batch.isDrawing()) batch.end();
                buffer.end();
            }
        } finally {
            batch.dispose();
            buffer.dispose();
        }
    }

    private static <T> T onGlThread(GameActivityScenario scenario, Callable<T> work)
            throws Exception {
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
        assertTrue("Early portrait GL work timed out", done.await(120, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Early portrait GL work", failure.get());
        return result.get();
    }
}
