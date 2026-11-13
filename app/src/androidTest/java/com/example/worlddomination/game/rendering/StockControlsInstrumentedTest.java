package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ScrollPaneState;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import com.example.worlddomination.game.ui.SourceStockControlState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Captures the real equipment row in each native stock state. */
@RunWith(AndroidJUnit4.class)
public final class StockControlsInstrumentedTest {
    @Test public void equipmentRowShowsThreeSourceSwitchFramesAndPreviewCount() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    Method init = app.getClass().getDeclaredMethod("initializeGameplayAssets");
                    init.setAccessible(true); init.invoke(app);
                    GameState game = new GameState(1, 17L);
                    game.getHuman().getWeapons().get("soldier1").active = true;
                    game.getHuman().getWeapons().get("soldier1").inStock = 3;
                    field("game").set(app, game);
                    SourceActionMenuState menu = (SourceActionMenuState) field("sourceActionMenu").get(app);
                    ScrollPaneState scroll = (ScrollPaneState) field("equipmentScroll").get(app);
                    render(app);
                    Rectangle viewport = (Rectangle) field("equipmentViewport").get(app);
                    Rectangle[] rows = (Rectangle[]) field("equipmentRows").get(app);
                    String[] names = (String[]) field("EQUIPMENT_WEAPON_NAMES").get(null);
                    int index = -1;
                    for (int i = 0; i < names.length; i++) if ("soldier1".equals(names[i])) index = i;
                    assertTrue(index >= 0);
                    scroll.moveBy(viewport.y + viewport.height / 2f
                            - (rows[index].y + rows[index].height / 2f));
                    render(app);
                    Rectangle row = rows[index];
                    assertTrue(viewport.contains(row.x + row.width / 2f, row.y + row.height / 2f));
                    long[] switchPixels = new long[3], countPixels = new long[3];
                    int[] states = new int[3], counts = new int[3];
                    boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
                    for (int step = 0; step < 3; step++) {
                        if (step == 1) { game.selectAction(GameState.Action.ATTACK); menu.begin(game); }
                        if (step == 2) assertTrue(menu.chooseEquipment(game, "soldier1"));
                        states[step] = SourceStockControlState.state(game, menu, "soldier1");
                        counts[step] = SourceStockControlState.displayedCount(game, menu, "soldier1");
                        render(app);
                        capture("stock-control-state-" + states[step] + ".png");
                        float height = Math.min(19f, row.height - 5f);
                        float cw = height * 32f / 26f, sw = height * 30f / 26f;
                        float cx = row.x + row.width - cw - sw - 5f;
                        float cy = row.y + (row.height - height) / 2f;
                        countPixels[step] = hash(app, cx + cw * 0.30f, cy + height * 0.20f,
                                cw * 0.4f, height * 0.6f);
                        switchPixels[step] = hash(app, cx + cw + 2f, cy, sw, height);
                        assertEquals(scissor, Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    }
                    assertArrayEquals(new int[]{1, 2, 3}, states);
                    assertArrayEquals(new int[]{3, 3, 2}, counts);
                    assertNotEquals(switchPixels[0], switchPixels[1]);
                    assertNotEquals(switchPixels[1], switchPixels[2]);
                    assertEquals(countPixels[0], countPixels[1]);
                    assertNotEquals(countPixels[1], countPixels[2]);
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue(done.await(45, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Stock row framebuffer regression", failure.get());
        }
    }

    private static long hash(WorldDominationGame app, float x, float y, float w, float h)
            throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 start = viewport.project(new Vector2(x, y));
        Vector2 end = viewport.project(new Vector2(x + w, y + h));
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] image = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
        long value = 1469598103934665603L;
        for (int py = Math.max(0, Math.round(start.y)); py < Math.min(height, Math.round(end.y)); py++)
            for (int px = Math.max(0, Math.round(start.x)); px < Math.min(width, Math.round(end.x)); px++)
                for (int channel = 0; channel < 4; channel++)
                    value = (value ^ (image[(py * width + px) * 4 + channel] & 255))
                            * 1099511628211L;
        return value;
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true); return result;
    }

    private static void capture(String name) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image);
        } finally {
            image.dispose();
        }
    }

    private static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true); app.render();
    }
}
