package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Exercises the actual city pass and source additive colors on Android GLES. */
@RunWith(AndroidJUnit4.class)
public final class BattlefieldCitySelectionInstrumentedTest {
    private static final int WIDTH = 986, HEIGHT = 600;

    @Test public void sourceCityColorsUseAdditionsAlphaAndRestoreBatchAfterCancellation() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                OriginalCityAssets assets = null;
                BattlefieldCityRenderer renderer = null;
                FrameBuffer buffer = null;
                SpriteBatch batch = null;
                Pixmap original = null, targeting = null, restored = null, spy = null;
                try {
                    assets = new OriginalCityAssets();
                    renderer = new BattlefieldCityRenderer(assets, null);
                    GameSetup setup = new GameSetup(); setup.toggleOpponent(0); setup.toggleOpponent(4);
                    setup.setStartingBuilding(5);
                    GameState game = new GameState(setup, 701L);
                    renderer.reset(game);
                    buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
                    batch = new SpriteBatch();
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
                    Rectangle bounds = new Rectangle(0, 0, WIDTH, HEIGHT);
                    original = render(buffer, batch, renderer, game, bounds, null);
                    game.selectAction(GameState.Action.PROPAGANDA);
                    double nextRandom = game.createPresentationRandomCopy().nextDouble();
                    targeting = render(buffer, batch, renderer, game, bounds, null);
                    assertSourceColor(original, targeting, game, assets, bounds, 0, FlashColorTransform.CITY_RED);
                    assertSourceColor(original, targeting, game, assets, bounds, 1, FlashColorTransform.CITY_GREEN);
                    assertSourceColor(original, targeting, game, assets, bounds, 2, FlashColorTransform.CITY_GREEN);
                    assertEquals(nextRandom, game.createPresentationRandomCopy().nextDouble(), 0);
                    capture(targeting, "source-city-target-colors.png");
                    game.cancelSelection();
                    restored = render(buffer, batch, renderer, game, bounds, null);
                    assertEquals(signature(original), signature(restored));
                    Field ceasefires = GameState.PlayerState.class.getDeclaredField("ceasefireTurns");
                    ceasefires.setAccessible(true);
                    ((int[]) ceasefires.get(game.getHuman()))[1] = 2;
                    game.selectAction(GameState.Action.ATTACK);
                    spy = render(buffer, batch, renderer, game, bounds, "spy1");
                    assertSourceColor(original, spy, game, assets, bounds, 1, FlashColorTransform.CITY_GREEN);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                } catch (Throwable error) { failure.set(error); }
                finally {
                    if (original != null) original.dispose();
                    if (targeting != null) targeting.dispose();
                    if (restored != null) restored.dispose();
                    if (spy != null) spy.dispose();
                    if (batch != null) batch.dispose();
                    if (buffer != null) buffer.dispose();
                    if (renderer != null) renderer.dispose();
                    if (assets != null) assets.dispose();
                    done.countDown();
                }
            }));
            assertTrue("Native city color test timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Native city colors", failure.get());
        }
    }

    private static Pixmap render(FrameBuffer buffer, SpriteBatch batch,
                                 BattlefieldCityRenderer renderer, GameState game,
                                 Rectangle bounds, String equipment) {
        buffer.begin(); Gdx.gl.glClearColor(0, 0, 0, 0); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setColor(Color.WHITE);
        ShaderProgram shader = batch.getShader();
        batch.begin(); renderer.draw(batch, bounds, game, equipment);
        assertTrue(batch.isDrawing()); assertSame(shader, batch.getShader());
        assertEquals(Color.WHITE, batch.getColor());
        batch.end();
        Pixmap result = ScreenUtils.getFrameBufferPixmap(0, 0, WIDTH, HEIGHT);
        buffer.end(); return result;
    }

    private static void assertSourceColor(Pixmap original, Pixmap tinted, GameState game,
                                          OriginalCityAssets assets, Rectangle bounds,
                                          int owner, FlashColorTransform color) {
        int samples = 0;
        float scale = CitySpriteLayout.scale(bounds, assets.getArtworkScale());
        OriginalCityAssets.Frame frame = assets.getFrame(game.getPlayerState(owner).getReligionCode(),
                game.getPlayerState(owner).getCity(0).getArtworkFrame());
        int left = (int) Math.ceil(BattlefieldCityLayout.x(bounds, owner, 0) + frame.offsetX * scale);
        int bottom = (int) Math.ceil(BattlefieldCityLayout.y(bounds, owner, 0)
                - frame.offsetY * scale - frame.region.getRegionHeight() * scale);
        int right = Math.min(WIDTH, left + (int) (frame.region.getRegionWidth() * scale) - 1);
        int top = Math.min(HEIGHT, bottom + (int) (frame.region.getRegionHeight() * scale) - 1);
        for (int y = Math.max(0, bottom); y < top; y++) {
            for (int x = Math.max(0, left); x < right; x++) {
                int before = original.getPixel(x, y);
                if ((before & 255) != 255) continue;
                int after = tinted.getPixel(x, y);
                assertChannel(before >>> 24 & 255, after >>> 24 & 255, color.redMultiplier, color.redAdd);
                assertChannel(before >>> 16 & 255, after >>> 16 & 255, color.greenMultiplier, color.greenAdd);
                assertChannel(before >>> 8 & 255, after >>> 8 & 255, color.blueMultiplier, color.blueAdd);
                samples++;
            }
        }
        assertTrue("Need opaque city samples for owner " + owner, samples > 10);
    }

    private static void assertChannel(int before, int after, float multiplier, float addition) {
        float expected = Math.min(255, before * multiplier + addition * 255) * .75f;
        assertEquals("Source additive city transform", expected, after, 2f);
    }

    private static long signature(Pixmap pixmap) {
        long hash = 0xcbf29ce484222325L;
        for (int y = 0; y < pixmap.getHeight(); y++)
            for (int x = 0; x < pixmap.getWidth(); x++)
                hash = (hash ^ pixmap.getPixel(x, y)) * 0x100000001b3L;
        return hash;
    }

    private static void capture(Pixmap image, String name) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image);
    }
}
