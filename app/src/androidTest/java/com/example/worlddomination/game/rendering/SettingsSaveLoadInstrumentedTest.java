package com.example.worlddomination.game.rendering;

import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLSurfaceView;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.persistence.GameSaveStore;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.GameSettingsMenu;
import com.example.worlddomination.game.ui.ScrollPaneState;
import com.example.worlddomination.game.ui.SourceActionInstruction;
import com.example.worlddomination.platform.persistence.AndroidGameSaveStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Uses the real libGDX input path and Android's app-private replacement backend. */
@RunWith(AndroidJUnit4.class)
public final class SettingsSaveLoadInstrumentedTest {
    static final String SAVE_FILENAME = "manual-game.wd2";

    @Test
    public void setupDisablesSaveAndLoadsAStoredMatch() throws Exception {
        GameSettingsMenu[] loadedMenu = new GameSettingsMenu[1];
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            File directory = createStoreDirectory();
            GameSaveStore previous = (GameSaveStore) field("gameSaveStore").get(app);
            try {
                GameSaveStore empty = AndroidGameSaveStore.create(directory);
                app.setGameSaveStore(empty);
                render(app);
                assertNull(game(app));
                GameSettingsMenu menu = openSettings(app);
                assertFalse(menu.canSave());
                assertFalse(menu.canLoad());
                tap(app, menu.save);
                assertTrue(menu.isOpen());
                assertFalse(empty.hasSave());
                assertNull(game(app));
                assertTrue(app.keyDown(Input.Keys.BACK));
                assertFalse(menu.isOpen());
                assertNull(game(app));

                GameState saved = newMatch();
                queue(saved, GameState.Action.RESOURCES);
                saved.cancelSelection();
                empty.save(2, saved);
                GameSaveStore reopened = AndroidGameSaveStore.create(directory);
                app.setGameSaveStore(reopened);
                openSettings(app);
                assertFalse(menu.canSave());
                assertEquals(1, menu.getSelectedSlot());
                assertFalse(menu.canLoad());
                tap(app, menu.load);
                assertFalse(menu.isConfirmingLoad());

                tap(app, menu.slots[1]);
                assertEquals(2, menu.getSelectedSlot());
                assertTrue(menu.canLoad());
                tap(app, menu.load);
                assertTrue(menu.isConfirmingLoad());
                assertTrue(app.keyDown(Input.Keys.BACK));
                assertTrue(menu.isOpen());
                assertFalse(menu.isConfirmingLoad());
                assertEquals(2, menu.getSelectedSlot());
                assertNull(game(app));

                tap(app, menu.load);
                tap(app, menu.confirm);
                assertTrue(menu.canSave());
                assertEquals(2, menu.getSelectedSlot());
                assertNotSame(saved, game(app));
                assertArrayEquals(GameSaveCodec.encode(saved), GameSaveCodec.encode(game(app)));
                assertNull("Load from setup immediately shows saved history", actionInstruction(app).getText());
                loadedMenu[0] = menu;
            } finally {
                app.setGameSaveStore(previous);
                removeStoreDirectory(directory);
            }
        }, scenario -> {
            CountDownLatch dispatched = new CountDownLatch(1);
            AtomicBoolean menuClosed = new AtomicBoolean();
            scenario.onActivity(activity -> {
                assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
                assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
                activity.postRunnable(() -> {
                    menuClosed.set(!loadedMenu[0].isOpen());
                    dispatched.countDown();
                });
            });
            assertTrue("Activity Back returns to the render thread", dispatched.await(5, TimeUnit.SECONDS));
            assertTrue("Activity Back closes Settings", menuClosed.get());
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
        });
    }

    @Test
    public void gameplaySaveAndReloadConsistentlyRestoreQueuedOrdersAndRandomStream()
            throws Exception {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String immersiveConfirmation = shell(automation,
                "settings get secure immersive_mode_confirmations").trim();
        // Fresh emulators show Android's immersive-mode tutorial over the game.
        shell(automation, "settings put secure immersive_mode_confirmations confirmed");
        try {
            checkGameplaySaveAndReload(automation);
        } finally {
            if ("null".equals(immersiveConfirmation)) {
                shell(automation, "settings delete secure immersive_mode_confirmations");
            } else {
                shell(automation, "settings put secure immersive_mode_confirmations '"
                        + immersiveConfirmation.replace("'", "'\\''") + "'");
            }
        }
    }

    private void checkGameplaySaveAndReload(UiAutomation automation) throws Exception {
        Vector2 panelSampleFraction = new Vector2();
        int[] panelColor = new int[1];
        EGLDisplay[] display = new EGLDisplay[1];
        EGLContext[] context = new EGLContext[1];
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            File directory = createStoreDirectory();
            GameSaveStore previous = (GameSaveStore) field("gameSaveStore").get(app);
            try {
                app.setGameSaveStore(AndroidGameSaveStore.create(directory));
                GameState current = newMatch();
                installMatch(app, current);
                assertEquals(SourceActionInstruction.WELCOME, actionInstruction(app).getText());
                render(app);
                assertTrue("The battlefield animates when visible", Gdx.graphics.isContinuousRendering());
                GameSettingsMenu menu = openSettings(app);
                render(app);
                assertFalse("Open Settings renders only on demand", Gdx.graphics.isContinuousRendering());
                assertTrue(menu.canSave());
                assertFalse(menu.canLoad());
                assertUnderlyingNavigationBlocked(app, current);
                tap(app, menu.close);
                assertFalse(menu.isOpen());
                render(app);
                assertTrue("Close resumes battlefield animation", Gdx.graphics.isContinuousRendering());

                queue(current, GameState.Action.RESOURCES);
                current.selectAction(GameState.Action.RESEARCH);
                assertTrue(current.selectResearchTechnology("soldier2"));
                assertTrue(current.acceptSelectedAction());
                current.cancelSelection();
                assertEquals(55, current.getTurnMoneyMillions());
                byte[] expected = GameSaveCodec.encode(current);
                double nextRandom = current.createPresentationRandomCopy().nextDouble();
                openSettings(app);
                tap(app, menu.save);
                assertTrue(menu.canLoad());
                assertEquals("Game saved. Turn 0.", menu.getStatus());
                capture(app, "settings-save-load-menu.png");
                tap(app, menu.load);
                assertTrue(menu.isConfirmingLoad());
                capture(app, "settings-save-load-confirm.png");
                assertFalse("Load confirmation stays on demand", Gdx.graphics.isContinuousRendering());
                tap(app, menu.cancel);
                assertTrue(menu.isOpen());
                assertFalse(menu.isConfirmingLoad());
                assertSame(current, game(app));
                assertArrayEquals(expected, GameSaveCodec.encode(current));
                assertTrue(app.keyDown(Input.Keys.BACK));
                assertFalse(menu.isOpen());
                render(app);
                assertTrue("Back resumes battlefield animation", Gdx.graphics.isContinuousRendering());

                // Settle a divergent match without waiting for battle presentation.
                current.confirmTurn();
                assertEquals(1, current.getTurn());
                assertEquals(0, current.getQueuedActionCount());
                byte[] secondSlot = GameSaveCodec.encode(current);
                openSettings(app);
                tap(app, menu.slots[1]);
                assertFalse(menu.canLoad());
                tap(app, menu.save);
                assertEquals("Game saved. Turn 1.", menu.getStatus());
                tap(app, menu.close);

                // Same turn, count and latest message, but a different first order.
                GameState alternate = newMatch();
                queue(alternate, GameState.Action.MASS_MEDIA);
                alternate.selectAction(GameState.Action.RESEARCH);
                assertTrue(alternate.selectResearchTechnology("soldier2"));
                assertTrue(alternate.acceptSelectedAction());
                assertEquals(0, alternate.getTurn());
                GameState saved = GameSaveCodec.decode(expected);
                assertEquals(saved.getMessages().size(), alternate.getMessages().size());
                assertEquals(saved.getLatestMessage(), alternate.getLatestMessage());
                installMatch(app, alternate);
                render(app);
                String alternateHistory = (String) field("cachedMessageText").get(app);
                assertTrue(alternateHistory.contains(GameState.Action.MASS_MEDIA.label + " queued."));

                GameSaveStore reopened = AndroidGameSaveStore.create(directory);
                assertTrue(reopened.hasSave());
                assertArrayEquals(expected, GameSaveCodec.encode(reopened.load()));
                assertArrayEquals(secondSlot, GameSaveCodec.encode(reopened.load(2)));
                assertFalse(reopened.hasSave(3));
                app.setGameSaveStore(reopened);
                openSettings(app);
                tap(app, menu.slots[1]);
                assertTrue(menu.canLoad());
                tap(app, menu.save);
                assertArrayEquals(GameSaveCodec.encode(alternate), GameSaveCodec.encode(reopened.load(2)));
                assertArrayEquals(expected, GameSaveCodec.encode(reopened.load(1)));
                assertFalse(reopened.hasSave(3));
                tap(app, menu.slots[0]);
                tap(app, menu.load);
                tap(app, menu.confirm);
                GameState loaded = game(app);
                assertNotNull(loaded);
                assertNotSame(alternate, loaded);
                assertEquals("Loaded turn 0.", menu.getStatus());
                render(app);
                assertNull("Transient instructions must not cover saved history", actionInstruction(app).getText());
                assertFalse("Loaded match stays paused behind Settings", Gdx.graphics.isContinuousRendering());
                String loadedHistory = (String) field("cachedMessageText").get(app);
                assertTrue(loadedHistory.contains(GameState.Action.RESOURCES.label + " queued."));
                assertFalse(loadedHistory.contains(GameState.Action.MASS_MEDIA.label + " queued."));
                assertEquals(GameState.Action.RESOURCES, loaded.getQueuedAction(0));
                assertEquals(GameState.Action.RESEARCH, loaded.getQueuedAction(1));
                assertEquals(55, loaded.getTurnMoneyMillions());
                assertEquals(nextRandom, loaded.createPresentationRandomCopy().nextDouble(), 0d);
                assertArrayEquals(expected, GameSaveCodec.encode(loaded));

                File save = new File(directory, SAVE_FILENAME);
                try (FileOutputStream output = new FileOutputStream(save)) {
                    output.write(new byte[]{0x57, 0x44, 0x32, 0x53});
                }
                tap(app, menu.load);
                assertTrue(menu.isConfirmingLoad());
                tap(app, menu.confirm);
                assertTrue(menu.isOpen());
                assertFalse(menu.isConfirmingLoad());
                assertEquals("Cannot load this save. Game unchanged.", menu.getStatus());
                assertSame(loaded, game(app));
                assertArrayEquals(expected, GameSaveCodec.encode(loaded));
                display[0] = EGL14.eglGetCurrentDisplay();
                context[0] = EGL14.eglGetCurrentContext();
                assertTrue("Settings EGL context is queryable", contextValid(display[0], context[0]));
                render(app);
                // Sample the solid part of Save, clear of its text and rounded edge.
                Vector2 panelSample = project(app, menu.save.x + menu.save.width * .14f,
                        menu.save.y + menu.save.height / 2f);
                panelColor[0] = readFramebufferColor(panelSample);
                assertTrue("The save button provides a distinct solid swatch",
                        Color.red(panelColor[0]) > 220 && Color.green(panelColor[0]) > 100
                                && Color.blue(panelColor[0]) < 85);
                panelSampleFraction.set(panelSample.x / Gdx.graphics.getWidth(),
                        panelSample.y / Gdx.graphics.getHeight());
            } finally {
                app.setGameSaveStore(previous);
                removeStoreDirectory(directory);
            }
        }, scenario -> {
            // This Activity belongs to the test and is disposed by runInGame.
            scenario.onActivity(activity -> ((GLSurfaceView) ((AndroidGraphics) activity.getGraphics())
                    .getView()).setPreserveEGLContextOnPause(false));
            scenario.moveToState(Lifecycle.State.CREATED);
            long deadline = SystemClock.uptimeMillis() + 5_000L;
            while (contextValid(display[0], context[0]) && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(20);
            }
            assertFalse("Pause destroys the original Settings EGL context",
                    contextValid(display[0], context[0]));
            scenario.moveToState(Lifecycle.State.RESUMED);
            int[] viewOrigin = new int[2];
            int[] viewSize = new int[2];
            scenario.onActivity(activity -> {
                android.view.View view = ((AndroidGraphics) activity.getGraphics()).getView();
                view.getLocationOnScreen(viewOrigin);
                viewSize[0] = view.getWidth();
                viewSize[1] = view.getHeight();
            });
            assertSystemBackDismissesSettings(automation,
                    viewOrigin[0] + Math.round(panelSampleFraction.x * viewSize[0]),
                    viewOrigin[1] + Math.round(panelSampleFraction.y * viewSize[1]),
                    panelColor[0]);
            scenario.onActivity(activity -> assertTrue("Back keeps the match in front",
                    activity.getWindow().getDecorView().hasWindowFocus()));
        });
    }

    private static boolean contextValid(EGLDisplay display, EGLContext context) {
        return !EGL14.EGL_NO_CONTEXT.equals(context) && EGL14.eglQueryContext(display, context,
                EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0);
    }

    private static int readFramebufferColor(Vector2 inputPixel) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        int x = Math.round(inputPixel.x * width / Gdx.graphics.getWidth());
        int y = height - 1 - Math.round(inputPixel.y * height / Gdx.graphics.getHeight());
        byte[] rgba = ScreenUtils.getFrameBufferPixels(x, y, 1, 1, false);
        return Color.rgb(rgba[0] & 255, rgba[1] & 255, rgba[2] & 255);
    }

    private static void assertSystemBackDismissesSettings(UiAutomation automation, int x, int y,
                                                          int panelColor) throws Exception {
        assertTrue("Settings is visible before the Android Back event",
                awaitSettingsVisible(automation, x, y, panelColor, true));
        // Another touch or a GL runnable would wake an on-demand surface and hide the regression.
        shell(automation, "input keyevent KEYCODE_BACK");
        assertTrue("Android Back closes Settings without another input or forced frame",
                awaitSettingsVisible(automation, x, y, panelColor, false));
    }

    private static String shell(UiAutomation automation, String command) throws Exception {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand(command))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static boolean awaitSettingsVisible(UiAutomation automation, int x, int y,
                                                int panelColor, boolean visible) {
        long deadline = SystemClock.uptimeMillis() + 5_000L;
        int lastWidth = 0;
        int lastHeight = 0;
        do {
            Bitmap screen = automation.takeScreenshot();
            assertNotNull("Android window screenshot", screen);
            try {
                lastWidth = screen.getWidth();
                lastHeight = screen.getHeight();
                // Android can briefly return a portrait frame while restoring the landscape activity.
                if (lastWidth <= lastHeight || x < 2 || x + 2 >= lastWidth
                        || y < 2 || y + 2 >= lastHeight) {
                    SystemClock.sleep(50);
                    continue;
                }
                int matchingPixels = 0;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int pixel = screen.getPixel(x + dx, y + dy);
                        if (Math.abs(Color.red(pixel) - Color.red(panelColor)) <= 5
                                && Math.abs(Color.green(pixel) - Color.green(panelColor)) <= 5
                                && Math.abs(Color.blue(pixel) - Color.blue(panelColor)) <= 5) {
                            matchingPixels++;
                        }
                    }
                }
                if (visible ? matchingPixels >= 21 : matchingPixels <= 4) return true;
            } finally {
                screen.recycle();
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        System.out.println("Settings swatch unavailable at " + x + "," + y
                + " in the last " + lastWidth + "x" + lastHeight + " screenshot");
        return false;
    }

    static GameState newMatch() {
        GameSetup setup = new GameSetup(0L);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }

    static void installMatch(WorldDominationGame app, GameState next) throws Exception {
        Method activate = WorldDominationGame.class.getDeclaredMethod("activateGame", GameState.class);
        activate.setAccessible(true);
        activate.invoke(app, next);
    }

    static GameSettingsMenu openSettings(WorldDominationGame app) throws Exception {
        tap(app, (Rectangle) field("settingsButton").get(app));
        GameSettingsMenu menu = (GameSettingsMenu) field("settingsMenu").get(app);
        assertTrue("Gear opens Settings", menu.isOpen());
        return menu;
    }

    private static void assertUnderlyingNavigationBlocked(WorldDominationGame app, GameState game)
            throws Exception {
        Rectangle[] rows = (Rectangle[]) field("navRows").get(app);
        Rectangle viewport = (Rectangle) field("navViewport").get(app);
        ScrollPaneState scroll = (ScrollPaneState) field("navigationScroll").get(app);
        float original = scroll.getOffset();
        assertTrue("Navigation can normally scroll this direction", original < scroll.getLimit());
        assertTrue("Resources row is visible", rows[0].width > 0f);
        tap(app, rows[0]);
        assertNull("Settings blocks navigation taps", game.getSelectedAction());
        float x = viewport.x + viewport.width / 2f;
        Vector2 start = project(app, x, viewport.y + viewport.height * .2f);
        Vector2 end = project(app, x, viewport.y + viewport.height * .8f);
        app.touchDown(Math.round(start.x), Math.round(start.y), 0, Input.Buttons.LEFT);
        app.touchDragged(Math.round(end.x), Math.round(end.y), 0);
        app.touchUp(Math.round(end.x), Math.round(end.y), 0, Input.Buttons.LEFT);
        assertEquals("Settings blocks navigation drags", original, scroll.getOffset(), 0f);
        assertNull(game.getSelectedAction());
    }

    static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        Vector2 point = project(app, bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f);
        int x = Math.round(point.x);
        int y = Math.round(point.y);
        assertTrue(app.touchDown(x, y, 0, Input.Buttons.LEFT));
        assertTrue(app.touchUp(x, y, 0, Input.Buttons.LEFT));
    }

    private static Vector2 project(WorldDominationGame app, float x, float y) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 screen = viewport.project(new Vector2(x, y));
        screen.y = Gdx.graphics.getHeight() - screen.y;
        return screen;
    }

    static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true);
        app.render();
    }

    private static void capture(WorldDominationGame app, String name) throws Exception {
        render(app);
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        File directory = context().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(pixels).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image);
        } finally {
            image.dispose();
        }
    }

    static File createStoreDirectory() throws Exception {
        File directory = File.createTempFile("settings-save-test-", ".tmp", context().getCacheDir());
        assertTrue(directory.delete());
        assertTrue(directory.mkdir());
        return directory;
    }

    static void removeStoreDirectory(File directory) {
        File[] files = directory.listFiles();
        assertNotNull(files);
        for (File file : files) assertTrue(file.delete());
        assertTrue(directory.delete());
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static Field field(String name) throws Exception {
        return SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class, name);
    }

    private static SourceActionInstruction actionInstruction(WorldDominationGame app) throws Exception {
        return (SourceActionInstruction) field("sourceActionInstruction").get(app);
    }

    static GameState game(WorldDominationGame app) throws Exception {
        return (GameState) field("game").get(app);
    }

    static void queue(GameState game, GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }
}
