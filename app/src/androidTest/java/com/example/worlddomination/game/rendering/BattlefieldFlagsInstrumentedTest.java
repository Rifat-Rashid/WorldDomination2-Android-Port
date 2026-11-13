package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.BattlefieldFlagState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real GLES coverage for flag tint, nested animation, terrain cleanup and state restoration. */
@RunWith(AndroidJUnit4.class)
public final class BattlefieldFlagsInstrumentedTest {
    @Test public void flagsUseSelectedColorsAnimateAndRemoveUnusedBakedFlags() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                FrameBuffer buffer = null;
                SpriteBatch batch = null;
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    Method init = WorldDominationGame.class.getDeclaredMethod("initializeGameplayAssets");
                    init.setAccessible(true); init.invoke(app);
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field("flashAnimationAssets").get(app);
                    long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
                    while (!assets.areBattlefieldFlagsLoaded() && System.nanoTime() < timeout) {
                        assets.update(); Thread.sleep(10);
                    }
                    assertTrue("Original flag leaves must be cached early",
                            assets.areBattlefieldFlagsLoaded());
                    FlashAnimationTimeline flagPole = assets.getTimeline("flag_pole");
                    assertNotNull("Missing original flag timeline", flagPole);
                    Set<Integer> flagLeaves = new HashSet<>();
                    assertFlagLeaves(assets, flagPole, new HashSet<>(), flagLeaves);
                    assertEquals("All original flag leaves must be cached", 49,
                            flagLeaves.size());
                    BattlefieldFlagRenderer flags = (BattlefieldFlagRenderer) field("flagRenderer").get(app);
                    OriginalUiAssets ui = (OriginalUiAssets) field("uiAssets").get(app);
                    buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 986, 600, false);
                    batch = new SpriteBatch();
                    OrthographicCamera camera = new OrthographicCamera();
                    camera.setToOrtho(false, 986, 600); batch.setProjectionMatrix(camera.combined);
                    Rectangle bounds = new Rectangle(0, 0, 986, 600);
                    Set<Long> colors = new HashSet<>();
                    long first = 0;
                    for (int color = 1; color <= 5; color++) {
                        GameSetup setup = new GameSetup(0);
                        setup.toggleOpponent(0); setup.setPlayerColor(color);
                        GameState game = new GameState(setup, 7L);
                        flags.reset();
                        Pixmap image = render(buffer, batch, flags, ui, bounds, game);
                        try {
                            int black = 0;
                            for (int yy = 12; yy < image.getHeight() - 12; yy++)
                                for (int xx = 12; xx < image.getWidth() - 12; xx++)
                                    if ((image.getPixel(xx, yy) >>> 8) == 0) black++;
                            assertTrue("Widened ocean cannot contain uncovered black strips: " + black,
                                    black < 200);
                            long signature = signature(image, bounds, 0);
                            capture(image, "source-flag-color-" + color + ".png");
                            assertTrue("Each source player tint must differ: color " + color
                                    + " signature " + signature, colors.add(signature));
                            if (color == 2) {
                                assertTrue("Selected red must replace baked green", redPixels(image, bounds, 0) > 20);
                                first = signature;
                                capture(image, "source-battlefield-red-flags.png");
                            }
                            for (int island = 2; island < 5; island++) {
                                assertEquals("Unused island must have no colored flag", 0,
                                        saturatedPixels(image, bounds, island));
                            }
                        } finally { image.dispose(); }
                        if (color == 2) {
                            for (int tick = 0; tick < 8; tick++) flags.tick();
                            Pixmap moved = render(buffer, batch, flags, ui, bounds, game);
                            try { assertNotEquals("Source cloth must animate", first, signature(moved, bounds, 0)); }
                            finally { moved.dispose(); }
                            for (int tick = 8; tick < 25; tick++) flags.tick();
                            Pixmap looped = render(buffer, batch, flags, ui, bounds, game);
                            try { assertEquals("25 source ticks restore frame one", first, signature(looped, bounds, 0)); }
                            finally { looped.dispose(); }
                        }
                    }
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                } catch (Throwable error) { failure.set(error); }
                finally {
                    if (batch != null) batch.dispose();
                    if (buffer != null) buffer.dispose();
                    done.countDown();
                }
            }));
            assertTrue("Flag rendering timed out", done.await(65, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Native source flags", failure.get());
        }
    }

    private static Pixmap render(FrameBuffer buffer, SpriteBatch batch, BattlefieldFlagRenderer flags,
                                 OriginalUiAssets ui, Rectangle bounds, GameState game) {
        buffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin(); batch.draw(ui.battleMap, 0, 0, bounds.width, bounds.height);
        Object previous = batch.getShader(); flags.draw(batch, bounds, game);
        assertSame("Flag rendering restores caller shader", previous, batch.getShader());
        batch.end();
        Pixmap image = ScreenUtils.getFrameBufferPixmap(0, 0, 986, 600);
        buffer.end(); return image;
    }

    private static void assertFlagLeaves(OriginalFlashAnimationAssets assets,
                                         FlashAnimationTimeline timeline, Set<Integer> visited,
                                         Set<Integer> leaves) {
        if (!visited.add(timeline.symbolId)) return;
        for (int index = 0; index < timeline.getFrameCount(); index++) {
            FlashAnimationTimeline.Frame frame = timeline.getFrame(index);
            if (frame == null) continue;
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
                if (nested != null) {
                    assertFlagLeaves(assets, nested, visited, leaves);
                } else {
                    assertNotNull("Missing original flag shape " + layer.shapeId,
                            assets.getShape(layer.shapeId));
                    leaves.add(layer.shapeId);
                }
            }
        }
    }

    private static long signature(Pixmap image, Rectangle bounds, int island) {
        int x = Math.round(BattlefieldCityLayout.sourceX(bounds, BattlefieldFlagState.sourceX(island), island));
        int y = Math.round(BattlefieldCityLayout.sourceY(bounds, BattlefieldFlagState.sourceY(island)));
        long result = 1;
        for (int yy = y + 30; yy < y + 52; yy++)
            for (int xx = x - 24; xx < x + 12; xx++) result = 31 * result + image.getPixel(xx, yy);
        return result;
    }

    private static int redPixels(Pixmap image, Rectangle bounds, int island) {
        return count(image, bounds, island, true);
    }

    private static int saturatedPixels(Pixmap image, Rectangle bounds, int island) {
        return count(image, bounds, island, false);
    }

    private static int count(Pixmap image, Rectangle bounds, int island, boolean redOnly) {
        int x = Math.round(BattlefieldCityLayout.sourceX(bounds, BattlefieldFlagState.sourceX(island), island));
        int y = Math.round(BattlefieldCityLayout.sourceY(bounds, BattlefieldFlagState.sourceY(island)));
        int count = 0;
        for (int yy = y + 32; yy < y + 50; yy++) for (int xx = x - 22; xx < x + 10; xx++) {
            int pixel = image.getPixel(xx, yy), r = pixel >>> 24 & 255;
            int g = pixel >>> 16 & 255, b = pixel >>> 8 & 255;
            if (redOnly ? r > 180 && r > g + 90 && r > b + 90
                    : Math.max(r, Math.max(g, b)) > 190 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 140) count++;
        }
        return count;
    }

    private static Field field(String name) throws Exception {
        Field f = WorldDominationGame.class.getDeclaredField(name); f.setAccessible(true); return f;
    }

    private static void capture(Pixmap image, String name) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image);
    }
}
