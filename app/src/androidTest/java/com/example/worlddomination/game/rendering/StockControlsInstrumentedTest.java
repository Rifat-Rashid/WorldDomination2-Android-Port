package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
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
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Captures the real equipment row in each native stock state. */
@RunWith(AndroidJUnit4.class)
public final class StockControlsInstrumentedTest {
    @Test public void equipmentRowShowsThreeSourceSwitchFramesAndPreviewCount() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
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

    @Test public void groupedArtworkMatchesCurrentRowOrder() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    Method initialize = app.getClass().getDeclaredMethod("initializeGameplayAssets");
                    initialize.setAccessible(true);
                    initialize.invoke(app);
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
                    OriginalUiAssets assets = (OriginalUiAssets) field("uiAssets").get(app);
                    SpriteBatch batch = (SpriteBatch) field("batch").get(app);
                    Matrix4 projection = ((com.badlogic.gdx.graphics.OrthographicCamera)
                            field("camera").get(app)).combined;
                    Method candidate = app.getClass().getDeclaredMethod("drawStockControlArtwork");
                    candidate.setAccessible(true);
                    int width = Gdx.graphics.getBackBufferWidth();
                    int height = Gdx.graphics.getBackBufferHeight();
                    FrameBuffer framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888,
                            width, height, false);
                    try {
                        // Four viewport positions include a fractional clip boundary.
                        float[] offsets = {0f, scroll.getLimit() * 0.5f,
                                Math.min(scroll.getLimit(), scroll.getLimit() * 0.5f + 0.375f),
                                scroll.getLimit()};
                        int comparisons = 0;
                        int maxVisible = 0;
                        Set<Integer> visibleStates = new HashSet<>();
                        for (int selection = 0; selection < 3; selection++) {
                            if (selection == 1) {
                                game.selectAction(GameState.Action.ATTACK);
                                menu.begin(game);
                            } else if (selection == 2) {
                                assertTrue(menu.chooseEquipment(game, "soldier1"));
                            }
                            for (float offset : offsets) {
                                scroll.moveBy(offset - scroll.getOffset());
                                render(app); // Establish original row geometry and viewport.
                                int visible = assertDisjointAndCount(rows, viewport, names);
                                for (int rowIndex = 0; rowIndex < names.length; rowIndex++) {
                                    Rectangle row = rows[rowIndex];
                                    if (names[rowIndex] != null && row.y + row.height >= viewport.y
                                            && row.y <= viewport.y + viewport.height) {
                                        visibleStates.add(SourceStockControlState.state(
                                                game, menu, names[rowIndex]));
                                    }
                                }
                                maxVisible = Math.max(maxVisible, visible);
                                if (visible == 0) continue;
                                Result original = paint(framebuffer, app, batch, projection,
                                        rows, viewport, names, assets, game, menu, null, false);
                                Result grouped = paint(framebuffer, app, batch, projection,
                                        rows, viewport, names, assets, game, menu, candidate, false);
                                assertArrayEquals("selection=" + selection + " offset=" + offset,
                                        original.rgba, grouped.rgba);
                                assertEquals(Float.floatToRawIntBits(original.packedColor),
                                        Float.floatToRawIntBits(grouped.packedColor));
                                assertTrue(original.scissorEnabled);
                                assertTrue(grouped.scissorEnabled);
                                assertEquals(0, original.glError);
                                assertEquals(0, grouped.glError);
                                assertTrue("No stock artwork pixels", original.inkPixels > 0);
                                if (visible >= 3) {
                                    assertTrue("Current row loop should switch textures repeatedly",
                                            original.renderCalls == 2 * visible);
                                    assertTrue("Grouped switches + counter should use <=4 flushes",
                                            grouped.renderCalls <= 4
                                                    && grouped.renderCalls < original.renderCalls);
                                }
                                if (comparisons == 0) {
                                    Result shifted = paint(framebuffer, app, batch, projection,
                                            rows, viewport, names, assets, game, menu, null, true);
                                    assertFalse("Shifted switch negative control",
                                            Arrays.equals(original.rgba, shifted.rgba));
                                }
                                comparisons++;
                            }
                        }
                        assertEquals(12, comparisons);
                        assertTrue(maxVisible >= 3);
                        assertEquals(new HashSet<>(Arrays.asList(
                                SourceStockControlState.UNAVAILABLE, SourceStockControlState.ELIGIBLE,
                                SourceStockControlState.SELECTED, SourceStockControlState.INACTIVE)),
                                visibleStates);
                    } finally {
                        framebuffer.dispose();
                    }
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue(done.await(45, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Stock grouping regression", failure.get());
        }
    }

    private static Result paint(FrameBuffer fbo, WorldDominationGame app, SpriteBatch batch,
            Matrix4 projection, Rectangle[] rows, Rectangle viewport, String[] names,
            OriginalUiAssets assets, GameState game, SourceActionMenuState menu,
            Method candidate, boolean shifted) throws Exception {
        boolean oldScissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        IntBuffer oldBox = BufferUtils.newIntBuffer(4);
        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, oldBox);
        float oldColor = batch.getPackedColor();
        Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
        int width = fbo.getWidth(), height = fbo.getHeight();
        fbo.begin();
        try {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            // Matches WorldDominationGame.setScissor for its current viewport.
            com.badlogic.gdx.utils.viewport.Viewport gameViewport =
                    (com.badlogic.gdx.utils.viewport.Viewport) field("viewport").get(app);
            float sx = gameViewport.getScreenWidth() / (float) field("worldWidth").getFloat(app);
            float sy = gameViewport.getScreenHeight() / (float) field("worldHeight").getFloat(app);
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(gameViewport.getScreenX() + Math.round(viewport.x * sx),
                    gameViewport.getScreenY() + Math.round(viewport.y * sy),
                    Math.round(viewport.width * sx), Math.round(viewport.height * sy));
            batch.setProjectionMatrix(projection);
            batch.setColor(Color.WHITE);
            int callsBefore = batch.totalRenderCalls;
            batch.begin();
            if (candidate != null) candidate.invoke(app);
            else drawOriginalRowOrder(batch, rows, viewport, names, assets, game, menu, shifted);
            batch.end();
            int calls = batch.totalRenderCalls - callsBefore;
            byte[] rgba = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
            int ink = 0;
            for (int i = 3; i < rgba.length; i += 4) if (rgba[i] != 0) ink++;
            int error = Gdx.gl.glGetError();
            return new Result(rgba, ink, calls, batch.getPackedColor(),
                    Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST), error);
        } finally {
            batch.setProjectionMatrix(oldProjection);
            batch.setPackedColor(oldColor);
            fbo.end();
            if (oldScissor) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(oldBox.get(0), oldBox.get(1), oldBox.get(2), oldBox.get(3));
        }
    }

    // Intentionally retained from the pre-refactor WorldDominationGame loop.
    private static void drawOriginalRowOrder(SpriteBatch batch, Rectangle[] rows,
            Rectangle viewport, String[] names, OriginalUiAssets assets, GameState game,
            SourceActionMenuState menu, boolean shiftSwitch) {
        for (int rowIndex = 0; rowIndex < names.length; rowIndex++) {
            if (names[rowIndex] == null) continue;
            Rectangle row = rows[rowIndex];
            if (row.y + row.height < viewport.y || row.y > viewport.y + viewport.height) continue;
            float controlHeight = Math.min(19f, row.height - 5f);
            float controlY = row.y + (row.height - controlHeight) / 2f;
            float counterWidth = controlHeight * 32f / 26f;
            float switchWidth = controlHeight * 30f / 26f;
            float counterX = row.x + row.width - counterWidth - switchWidth - 5f;
            int state = SourceStockControlState.state(game, menu, names[rowIndex]);
            batch.draw(assets.stockSwitches[SourceStockControlState.switchIndex(state)],
                    counterX + counterWidth + 2f + (shiftSwitch ? 1f : 0f),
                    controlY, switchWidth, controlHeight);
            batch.draw(assets.stockCounter, counterX, controlY, counterWidth, controlHeight);
        }
    }

    private static int assertDisjointAndCount(Rectangle[] rows, Rectangle viewport,
            String[] names) {
        Rectangle[] controls = new Rectangle[names.length * 2];
        int count = 0;
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) continue;
            Rectangle row = rows[i];
            if (row.y + row.height < viewport.y || row.y > viewport.y + viewport.height) continue;
            float h = Math.min(19f, row.height - 5f);
            float y = row.y + (row.height - h) / 2f;
            float cw = h * 32f / 26f, sw = h * 30f / 26f;
            float x = row.x + row.width - cw - sw - 5f;
            controls[count++] = new Rectangle(x, y, cw, h);
            controls[count++] = new Rectangle(x + cw + 2f, y, sw, h);
        }
        for (int i = 0; i < count; i++) for (int j = i + 1; j < count; j++)
            assertFalse("Overlapping stock controls " + i + "/" + j,
                    controls[i].overlaps(controls[j]));
        return count / 2;
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
    private static final class Result {
        final byte[] rgba;
        final int inkPixels, renderCalls, glError;
        final float packedColor;
        final boolean scissorEnabled;
        Result(byte[] rgba, int inkPixels, int renderCalls, float packedColor,
                boolean scissorEnabled, int glError) {
            this.rgba = rgba;
            this.inkPixels = inkPixels;
            this.renderCalls = renderCalls;
            this.packedColor = packedColor;
            this.scissorEnabled = scissorEnabled;
            this.glError = glError;
        }
    }
}
