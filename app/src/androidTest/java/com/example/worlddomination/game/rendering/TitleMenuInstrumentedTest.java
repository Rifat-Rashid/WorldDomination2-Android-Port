package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.persistence.GameSaveStore;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.SplashScreenState;
import com.example.worlddomination.game.ui.TitleMenuLayout;
import com.example.worlddomination.platform.persistence.AndroidGameSaveStore;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.createStoreDirectory;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.game;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.newMatch;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.queue;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.removeStoreDirectory;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.render;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.tap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class TitleMenuInstrumentedTest {
    @Test
    public void continueLoadsLatestCompatibleSaveThroughTitleTouch() throws Exception {
        withFreshTitle((app, directory) -> {
            GameSaveStore store = AndroidGameSaveStore.create(directory);
            app.setGameSaveStore(store);
            render(app);
            TitleMenuLayout title = (TitleMenuLayout) field("titleMenu").get(app);
            assertTrue("Continue has an onscreen control", title.continueGame.width > 0f);
            tap(app, title.logo);
            tap(app, title.continueGame);
            assertOnTitle(app);

            long oldest = System.currentTimeMillis() - 60_000;
            File invalidSave = new File(directory, "manual-game-slot-3.wd2");
            corrupt(invalidSave);
            assertTrue(invalidSave.setLastModified(oldest + 20_000));
            app.setGameSaveStore(AndroidGameSaveStore.create(directory));
            render(app);
            tap(app, title.continueGame);
            assertOnTitle(app);

            GameState older = checkpoint(2);
            GameState latest = checkpoint(1);
            store.save(1, older);
            store.save(2, latest);
            assertTrue(new File(directory, "manual-game.wd2").setLastModified(oldest));
            assertTrue(new File(directory, "manual-game-slot-2.wd2").setLastModified(oldest + 10_000));
            app.setGameSaveStore(AndroidGameSaveStore.create(directory));
            render(app);
            tap(app, title.logo);
            assertOnTitle(app);
            tap(app, title.continueGame);

            assertFalse(splash(app).isVisible());
            GameState loaded = game(app);
            assertNotNull(loaded);
            assertArrayEquals(GameSaveCodec.encode(latest), GameSaveCodec.encode(loaded));
            tap(app, title.continueGame);
            assertSame("A second touch cannot reload the title checkpoint", loaded, game(app));
        });
    }

    @Test
    public void continueReadFailureStaysOnTitleAndRechecksRemainingSaves() throws Exception {
        withFreshTitle((app, directory) -> {
            GameSaveStore store = AndroidGameSaveStore.create(directory);
            GameState fallback = checkpoint(1);
            store.save(1, fallback);
            store.save(2, checkpoint(2));
            long oldest = System.currentTimeMillis() - 60_000;
            assertTrue(new File(directory, "manual-game.wd2").setLastModified(oldest));
            File newest = new File(directory, "manual-game-slot-2.wd2");
            assertTrue(newest.setLastModified(oldest + 10_000));

            app.setGameSaveStore(store);
            render(app);
            TitleMenuLayout title = (TitleMenuLayout) field("titleMenu").get(app);
            corrupt(newest); // Change the selected save after the title has drawn it.
            tap(app, title.continueGame);
            assertOnTitle(app);

            render(app);
            tap(app, title.continueGame);
            GameState loaded = game(app);
            assertFalse(splash(app).isVisible());
            assertNotNull(loaded);
            assertArrayEquals(GameSaveCodec.encode(fallback), GameSaveCodec.encode(loaded));
        });
    }

    private static GameState checkpoint(int turns) {
        GameState match = newMatch();
        for (int i = 0; i < turns; i++) {
            queue(match, GameState.Action.RESOURCES);
            queue(match, GameState.Action.MASS_MEDIA);
            match.confirmTurn();
        }
        queue(match, GameState.Action.RESOURCES);
        return match;
    }

    private static void corrupt(File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[]{0x57, 0x44, 0x32, 0x53});
        }
    }

    private static void assertOnTitle(WorldDominationGame app) throws Exception {
        assertTrue(splash(app).isVisible());
        assertNull(game(app));
    }

    private static SplashScreenState splash(WorldDominationGame app) throws Exception {
        return (SplashScreenState) field("splashState").get(app);
    }

    private static Field field(String name) throws Exception {
        return SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class, name);
    }

    private interface TitleCheck {
        void run(WorldDominationGame app, File directory) throws Exception;
    }

    private static void withFreshTitle(TitleCheck check) throws Exception {
        File directory = createStoreDirectory();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    check.run(app, directory);
                } catch (Throwable problem) {
                    failure.set(problem);
                } finally {
                    done.countDown();
                }
            }));
            assertTrue("Title menu GL test timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Title menu GL test failed", failure.get());
        } finally {
            removeStoreDirectory(directory);
        }
    }
}
