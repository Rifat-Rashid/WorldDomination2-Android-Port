package com.example.worlddomination.game.rendering;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import com.example.worlddomination.platform.persistence.AndroidGameSaveStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
    private static final String SAVE_FILENAME = "manual-game.wd2";

    @Test
    public void setupDisablesSaveAndLoadsAStoredMatch() throws Exception {
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
                empty.save(saved);
                GameSaveStore reopened = AndroidGameSaveStore.create(directory);
                app.setGameSaveStore(reopened);
                openSettings(app);
                assertFalse(menu.canSave());
                assertTrue(menu.canLoad());
                tap(app, menu.load);
                assertTrue(menu.isConfirmingLoad());
                assertTrue(app.keyDown(Input.Keys.BACK));
                assertTrue(menu.isOpen());
                assertFalse(menu.isConfirmingLoad());
                assertNull(game(app));
                tap(app, menu.load);
                tap(app, menu.confirm);
                assertTrue(menu.canSave());
                assertNotSame(saved, game(app));
                assertArrayEquals(GameSaveCodec.encode(saved), GameSaveCodec.encode(game(app)));
            } finally {
                app.setGameSaveStore(previous);
                removeStoreDirectory(directory);
            }
        });
    }

    @Test
    public void gameplaySaveAndReloadConsistentlyRestoreQueuedOrdersAndRandomStream()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            File directory = createStoreDirectory();
            GameSaveStore previous = (GameSaveStore) field("gameSaveStore").get(app);
            try {
                app.setGameSaveStore(AndroidGameSaveStore.create(directory));
                GameState current = newMatch();
                installMatch(app, current);
                render(app);
                GameSettingsMenu menu = openSettings(app);
                assertTrue(menu.canSave());
                assertFalse(menu.canLoad());
                assertUnderlyingNavigationBlocked(app, current);
                tap(app, menu.close);
                assertFalse(menu.isOpen());

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
                tap(app, menu.cancel);
                assertTrue(menu.isOpen());
                assertFalse(menu.isConfirmingLoad());
                assertSame(current, game(app));
                assertArrayEquals(expected, GameSaveCodec.encode(current));
                assertTrue(app.keyDown(Input.Keys.BACK));
                assertFalse(menu.isOpen());

                // Settle a divergent match without waiting for battle presentation.
                current.confirmTurn();
                assertEquals(1, current.getTurn());
                assertEquals(0, current.getQueuedActionCount());

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
                app.setGameSaveStore(reopened);
                openSettings(app);
                tap(app, menu.load);
                tap(app, menu.confirm);
                GameState loaded = game(app);
                assertNotNull(loaded);
                assertNotSame(alternate, loaded);
                assertEquals("Loaded turn 0.", menu.getStatus());
                render(app);
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
            } finally {
                app.setGameSaveStore(previous);
                removeStoreDirectory(directory);
            }
        });
    }

    private static GameState newMatch() {
        GameSetup setup = new GameSetup(0L);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }

    private static void installMatch(WorldDominationGame app, GameState next) throws Exception {
        Method activate = WorldDominationGame.class.getDeclaredMethod("activateGame", GameState.class);
        activate.setAccessible(true);
        activate.invoke(app, next);
    }

    private static GameSettingsMenu openSettings(WorldDominationGame app) throws Exception {
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

    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
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

    private static void render(WorldDominationGame app) throws Exception {
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

    private static File createStoreDirectory() throws Exception {
        File directory = File.createTempFile("settings-save-test-", ".tmp", context().getCacheDir());
        assertTrue(directory.delete());
        assertTrue(directory.mkdir());
        return directory;
    }

    private static void removeStoreDirectory(File directory) {
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

    private static GameState game(WorldDominationGame app) throws Exception {
        return (GameState) field("game").get(app);
    }

    private static void queue(GameState game, GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }
}
