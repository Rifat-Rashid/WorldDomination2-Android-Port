package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.MessageHistoryScrollState;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Whole-row drawing against direct, independently selected font text. */
@RunWith(AndroidJUnit4.class)
public final class MessageWholeLineInstrumentedTest {
    private static final String[] ROWS = {
            "gypsy descenders remain readable",
            "First message continues through the well",
            "",
            "poppy qj reaches its complete baseline",
            "Another complete history line",
            "Middle line with a swinging g",
            "The penultimate message remains available",
            "Last line can be reached by scrolling"
    };

    @Test public void completeRowsMatchIndependentVisibleTextAtBothSizes() throws Exception {
        runInWorld(app -> {
            SpriteBatch batch = (SpriteBatch) field(app, "batch");
            MessagePanelRenderer renderer = (MessagePanelRenderer) field(app, "messagePanelRenderer");
            BitmapFont font = renderer.getFont();
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldBatchColor = batch.getPackedColor();
            Color oldFontColor = new Color(font.getColor());
            String history = String.join("\n", ROWS);
            try {
                for (int[] dimensions : new int[][]{{800, 600}, {1194, 552}}) {
                    int width = dimensions[0], height = dimensions[1];
                    android.util.Log.i("WD2HudCheck", "pixel checks " + width + "x" + height);
                    Rectangle viewport = new Rectangle(100, 80, width == 800 ? 240 : 360, 46);
                    assertEquals("fixture must fit exactly three whole source-style rows", 3,
                            (int) Math.floor(viewport.height / font.getLineHeight()));
                    GlyphLayout full = new GlyphLayout();
                    full.setText(font, history, MessagePanelRenderer.TEXT_COLOR,
                            viewport.width, Align.left, true);
                    assertTrue("fixture must retain all eight line positions",
                            full.height >= 7f * font.getLineHeight());
                    assertTrue("fixture rows must not wrap unpredictably",
                            full.height < 9f * font.getLineHeight());
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888,
                            width, height, false);
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
                    try {
                        for (int first : new int[]{0, 2, ROWS.length - 3}) {
                            byte[] actual = render(fb, batch, renderer, font, full, ROWS,
                                    viewport, first, 0, width, height);
                            byte[] expected = render(fb, batch, renderer, font, full, ROWS,
                                    viewport, first, 1, width, height);
                            assertArrayEquals("complete rows at " + width + "x" + height
                                    + " scroll " + first, expected, actual);
                            if (first == 0) {
                                byte[] unrestricted = render(fb, batch, renderer, font, full, ROWS,
                                        viewport, first, 2, width, height);
                                assertTrue("old whole-layout draw exposes part of row four",
                                        changedInside(expected, unrestricted, viewport,
                                                width, height) > 10);
                                String size = width + "x" + height;
                                evidence("source-message-whole-lines-" + size + "-actual.png",
                                        actual, width, height);
                                evidence("source-message-whole-lines-" + size + "-expected.png",
                                        expected, width, height);
                                evidence("source-message-whole-lines-" + size
                                                + "-unrestricted.png", unrestricted, width, height);
                            }
                        }
                        // One independently measured word fits each row; two
                        // cannot. LibGDX must wrap the paragraph at spaces.
                        String word = wrappedWord(font, viewport.width);
                        String[] wrappedRows = {word, word, word, word, "",
                                word, word, word, word};
                        String paragraph = String.join(" ", word, word, word, word)
                                + "\n\n" + String.join(" ", word, word, word, word);
                        GlyphLayout wrapped = new GlyphLayout();
                        wrapped.setText(font, paragraph, MessagePanelRenderer.TEXT_COLOR,
                                viewport.width, Align.left, true);
                        assertTrue("paragraph wraps into nine line positions",
                                wrapped.height >= 8f * font.getLineHeight()
                                        && wrapped.height < 10f * font.getLineHeight());
                        for (int first : new int[]{0, 3, wrappedRows.length - 3}) {
                            byte[] actual = render(fb, batch, renderer, font, wrapped,
                                    wrappedRows, viewport, first, 0, width, height);
                            byte[] expected = render(fb, batch, renderer, font, wrapped,
                                    wrappedRows, viewport, first, 1, width, height);
                            assertArrayEquals("automatic word wrap at " + width + "x" + height
                                    + " scroll " + first, expected, actual);
                        }
                    } finally { fb.dispose(); }
                }
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                batch.setProjectionMatrix(oldProjection);
                batch.setPackedColor(oldBatchColor);
                font.setColor(oldFontColor);
            }
        });
    }

    @Test public void liveWorldRetainsFullHistoryAndReachableLastRow() throws Exception {
        runInWorld(app -> {
            GameSetup setup = new GameSetup(0L);
            assertTrue(setup.toggleOpponent(0));
            assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup);
            Field gameField = WorldDominationGame.class.getDeclaredField("game");
            gameField.setAccessible(true);
            Object previousGame = gameField.get(app);
            try {
                gameField.set(app, game);
                Method addMessage = GameState.class.getDeclaredMethod("addMessage", String.class);
                addMessage.setAccessible(true);
                for (String row : ROWS) addMessage.invoke(game, row.isEmpty() ? "<br />" : row);
                List<String> retained = game.getMessages();
                assertTrue("history retains every source entry", retained.size() >= ROWS.length);
                for (String row : ROWS) if (!row.isEmpty()) assertTrue(retained.contains(row));
                Method update = WorldDominationGame.class.getDeclaredMethod("updateMessageHistoryLayout");
                update.setAccessible(true);
                update.invoke(app);
                MessageHistoryScrollState scroll = (MessageHistoryScrollState)
                        field(app, "messageHistoryScroll");
                assertTrue("long history has later reachable lines", scroll.getMaxScroll() > 1);
                assertTrue(scroll.scrollTo(scroll.getMaxScroll()));
                assertEquals(scroll.getMaxScroll() - 1, scroll.getFirstVisibleLine());
                assertTrue("first lines remain reachable", scroll.scrollTo(1));
                assertEquals(0, scroll.getFirstVisibleLine());
                Rectangle viewport = (Rectangle) field(app, "messageViewport");
                Method pointArea = WorldDominationGame.class.getDeclaredMethod(
                        "pointArea", float.class, float.class);
                pointArea.setAccessible(true);
                Field gesture = WorldDominationGame.class.getDeclaredField("GESTURE_MESSAGE");
                gesture.setAccessible(true);
                assertEquals("live well still routes message gestures", gesture.getInt(null),
                        pointArea.invoke(app, viewport.x + viewport.width / 2f,
                                viewport.y + viewport.height / 2f));
                Viewport worldViewport = (Viewport) field(app, "viewport");
                float centerX = viewport.x + viewport.width / 2f;
                float centerY = viewport.y + viewport.height / 2f;
                int[] start = screenPoint(worldViewport, centerX, centerY);
                int[] upward = screenPoint(worldViewport, centerX,
                        centerY + (scroll.getMaxScroll() + 3f)
                                * ((MessagePanelRenderer) field(app, "messagePanelRenderer"))
                                .getFont().getLineHeight());
                assertTrue(app.touchDown(start[0], start[1], 0, Input.Buttons.LEFT));
                assertTrue(app.touchDragged(upward[0], upward[1], 0));
                assertTrue(app.touchUp(upward[0], upward[1], 0, Input.Buttons.LEFT));
                assertEquals("upward touch drag reaches final history row",
                        scroll.getMaxScroll(), scroll.getScroll());
                int[] downward = screenPoint(worldViewport, centerX,
                        centerY - (scroll.getMaxScroll() + 3f)
                                * ((MessagePanelRenderer) field(app, "messagePanelRenderer"))
                                .getFont().getLineHeight());
                assertTrue(app.touchDown(start[0], start[1], 0, Input.Buttons.LEFT));
                assertTrue(app.touchDragged(downward[0], downward[1], 0));
                assertTrue(app.touchUp(downward[0], downward[1], 0, Input.Buttons.LEFT));
                assertEquals("reverse drag recovers first history row", 1, scroll.getScroll());
            } finally { gameField.set(app, previousGame); }
        });
    }

    private static byte[] render(FrameBuffer fb, SpriteBatch batch,
                                 MessagePanelRenderer renderer, BitmapFont font,
                                 GlyphLayout full, String[] rows, Rectangle viewport, int first,
                                 int mode, int width, int height) {
        fb.begin();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        batch.setColor(Color.WHITE);
        if (mode == 0) {
            renderer.drawHistory(batch, full, viewport, first);
        } else if (mode == 1) {
            // The visible rows are chosen explicitly by the test, without the
            // renderer's glyph-index range or an OpenGL scissor.
            String visible = String.join("\n", rows[first], rows[first + 1], rows[first + 2]);
            font.draw(batch, visible, viewport.x,
                    viewport.y + viewport.height - 3f);
        } else {
            font.draw(batch, full, viewport.x,
                    viewport.y + viewport.height - 3f + first * font.getLineHeight());
        }
        batch.end();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        fb.end();
        return pixels;
    }

    private static String wrappedWord(BitmapFont font, float width) {
        StringBuilder word = new StringBuilder("gypsyqj");
        GlyphLayout measured = new GlyphLayout();
        measured.setText(font, word);
        while (measured.width < width * .6f) {
            word.append("gypsyqj");
            measured.setText(font, word);
        }
        assertTrue("one word fits the viewport", measured.width < width);
        GlyphLayout doubled = new GlyphLayout();
        doubled.setText(font, word + " " + word);
        assertTrue("two words force a wrap", doubled.width > width);
        return word.toString();
    }

    private static int[] screenPoint(Viewport viewport, float worldX, float worldY) {
        Vector3 point = viewport.project(new Vector3(worldX, worldY, 0f));
        int x = Math.round(point.x);
        int y = Gdx.graphics.getHeight() - 1 - Math.round(point.y);
        return new int[]{x, y};
    }

    private static int changedInside(byte[] expected, byte[] actual,
                                     Rectangle viewport, int width, int height) {
        int changed = 0;
        for (int y = (int) viewport.y; y < viewport.y + viewport.height; y++)
            for (int x = (int) viewport.x; x < viewport.x + viewport.width; x++) {
                int k = ((height - 1 - y) * width + x) * 4;
                if (expected[k] != actual[k] || expected[k + 1] != actual[k + 1]
                        || expected[k + 2] != actual[k + 2] || expected[k + 3] != actual[k + 3])
                    changed++;
            }
        return changed;
    }

    private static Object field(WorldDominationGame app, String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result.get(app);
    }

    private interface WorldCheck { void run(WorldDominationGame app) throws Exception; }

    /** The message font is created with World; battle atlases are irrelevant here. */
    private static void runInWorld(WorldCheck check) throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch created = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<WorldDominationGame> world = new AtomicReference<>();
            // Activity RESUMED precedes libGDX create(), including native audio
            // initialization. Wait for that event separately from the pixel check.
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    assertNotNull("World created its message renderer", field(app, "messagePanelRenderer"));
                    world.set(app);
                    android.util.Log.i("WD2HudCheck", "World created");
                } catch (Throwable error) {
                    failure.set(error);
                } finally { created.countDown(); }
            }));
            assertTrue("World creation did not finish", created.await(150, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("World creation", failure.get());
            Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = world.get();
                    Object splash = field(app, "splashState");
                    Field visible = splash.getClass().getDeclaredField("visible");
                    visible.setAccessible(true);
                    boolean wasVisible = visible.getBoolean(splash);
                    try {
                        splash.getClass().getMethod("dismiss").invoke(splash);
                        android.util.Log.i("WD2HudCheck", "check started");
                        check.run(app);
                        android.util.Log.i("WD2HudCheck", "check completed");
                    } finally {
                        // No setup assets were requested by this HUD-only fixture.
                        // Restore the title before the next normal render frame.
                        visible.setBoolean(splash, wasVisible);
                    }
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            });
            assertTrue("message HUD GL check timed out", done.await(30, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("message HUD GL check", failure.get());
        }
    }

    private static void evidence(String name, byte[] bytes, int width, int height) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(bytes).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }
}
