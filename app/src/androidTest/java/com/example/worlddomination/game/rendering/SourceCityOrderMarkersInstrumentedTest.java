package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Literal marker transforms from an executed SetupMenu(3) source trace. */
@RunWith(AndroidJUnit4.class)
public final class SourceCityOrderMarkersInstrumentedTest {
    @Test public void queuedAndOverlappingIconsMatchOriginalTransformsAndRestoreAfterCancel()
            throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                SpriteBatch batch = null;
                FrameBuffer buffer = null;
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    Method initialize = WorldDominationGame.class.getDeclaredMethod("initializeGameplayAssets");
                    initialize.setAccessible(true); initialize.invoke(app);
                    Field field = WorldDominationGame.class.getDeclaredField("flashAnimationAssets");
                    field.setAccessible(true);
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field.get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!(assets.areBattlefieldFlagsLoaded()
                            && assets.areCenterUiAssetsLoaded()) && System.nanoTime() < deadline) {
                        assets.update(); Thread.sleep(10);
                    }
                    assertTrue("Early original marker artwork must be cached",
                            assets.areBattlefieldFlagsLoaded()
                                    && assets.areCenterUiAssetsLoaded());
                    for (int shape : new int[]{2218, 2221, 2224, 2227, 2230, 2233})
                        assertNotNull("Missing original shape " + shape, assets.getShape(shape));
                    GameState game = new GameState(1, 65L);
                    game.selectAction(GameState.Action.ATTACK);
                    assertTrue(game.configureAttack(1, 0, "soldier1"));
                    assertTrue(game.acceptSelectedAction());
                    double random = game.createPresentationRandomCopy().nextDouble();
                    buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 986, 600, false);
                    batch = new SpriteBatch();
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 986, 600));
                    Rectangle bounds = new Rectangle(0, 0, 986, 600);
                    BattlefieldCityOrderMarkerRenderer renderer = new BattlefieldCityOrderMarkerRenderer(assets);
                    long single = render(buffer, batch, renderer, assets, bounds, game, false, false);
                    assertEquals("Source soldier at child(-12.5,25), parent(29,237), scale200%",
                            render(buffer, batch, renderer, assets, bounds, game, true, false), single);
                    game.selectAction(GameState.Action.PROPAGANDA);
                    assertTrue(game.selectBattlefieldCity(1, 0));
                    long overlap = render(buffer, batch, renderer, assets, bounds, game, false, true);
                    assertNotEquals(single, overlap);
                    assertEquals("Source overlap children(-25,25) and(0,25)",
                            render(buffer, batch, renderer, assets, bounds, game, true, true), overlap);
                    game.cancelSelection();
                    assertEquals(single, render(buffer, batch, renderer, assets, bounds, game, false, false));
                    game.removeQueuedAction(0);
                    assertEquals(0, render(buffer, batch, renderer, assets, bounds, game, false, false));
                    assertEquals(random, game.createPresentationRandomCopy().nextDouble(), 0);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                } catch (Throwable error) { failure.set(error); }
                finally {
                    if (batch != null) batch.dispose();
                    if (buffer != null) buffer.dispose();
                    done.countDown();
                }
            }));
            assertTrue("Native city marker test timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source city markers", failure.get());
        }
    }

    private static long render(FrameBuffer buffer, SpriteBatch batch,
                               BattlefieldCityOrderMarkerRenderer renderer,
                               OriginalFlashAnimationAssets assets, Rectangle bounds,
                               GameState game, boolean expected, boolean overlap) {
        buffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setColor(Color.WHITE);
        batch.begin();
        if (!expected) renderer.drawCity(batch, bounds, game, 5);
        else {
            // Python action_entry.sync_cities, measured from actual source clips:
            // parent (29,237), scale 2; all icon widths 25; children y=25.
            sourceShape(batch, assets, bounds, 2218, 29, 237);
            sourceShape(batch, assets, bounds, 2233, overlap ? -21 : 4, 287);
            if (overlap) sourceShape(batch, assets, bounds, 2224, 29, 287);
        }
        assertTrue(batch.isDrawing());
        assertEquals(Color.WHITE, batch.getColor());
        batch.end();
        Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 986, 600);
        buffer.end();
        long signature = 0;
        try {
            for (int y = 0; y < pixels.getHeight(); y++)
                for (int x = 0; x < pixels.getWidth(); x++)
                    signature = signature * 31 + pixels.getPixel(x, y);
            if (!expected && overlap) {
                File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getExternalFilesDir("beta-ui-evidence");
                assertNotNull(directory);
                PixmapIO.writePNG(Gdx.files.absolute(new File(directory,
                        "source-city-overlapping-order-markers.png").getAbsolutePath()), pixels);
            }
        } finally { pixels.dispose(); }
        return signature;
    }

    private static void sourceShape(SpriteBatch batch, OriginalFlashAnimationAssets assets,
                                    Rectangle bounds, int shape, float x, float y) {
        TextureRegion region = assets.getShape(shape);
        batch.draw(region, BattlefieldCityLayout.sourceX(bounds, x, 1)
                        + assets.getShapeOriginX(shape) * 2,
                BattlefieldCityLayout.sourceY(bounds, y)
                        - (assets.getShapeOriginY(shape) + region.getRegionHeight()) * 2,
                region.getRegionWidth() * 2, region.getRegionHeight() * 2);
    }
}
