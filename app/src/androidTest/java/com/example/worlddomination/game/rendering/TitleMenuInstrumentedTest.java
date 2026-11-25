package com.example.worlddomination.game.rendering;

import android.view.KeyEvent;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.persistence.GameSaveStore;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import com.example.worlddomination.game.ui.SplashScreenState;
import com.example.worlddomination.game.ui.TitleMenuLayout;
import com.example.worlddomination.platform.persistence.AndroidGameSaveStore;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.capture;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.createStoreDirectory;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.game;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.newMatch;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.queue;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.removeStoreDirectory;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.render;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.tap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class TitleMenuInstrumentedTest {
    @Test
    public void continueOpensEmptyPickerAndBothBackRoutesReturnToTitle() throws Exception {
        withFreshTitle((scenario, directory) -> {
            GameSaveStore store = AndroidGameSaveStore.create(directory);
            onGl(scenario, app -> {
                app.setGameSaveStore(store);
                capture(app, "title-menu-empty.png");
                TitleMenuLayout title = title(app);
                tap(app, title.logo);
                assertOnTitle(app, false);
                tap(app, title.continueGame);
                assertOnTitle(app, true);
                capture(app, "title-slot-picker-empty.png");

                for (int i = 0; i < GameSaveStore.SLOT_COUNT; i++) {
                    tap(app, title.slots[i]);
                    assertOnTitle(app, true);
                    assertFalse(store.hasSave(i + 1));
                }
                tap(app, title.back);
                assertOnTitle(app, false);
                tap(app, title.continueGame);
                assertOnTitle(app, true);
            });

            scenario.onActivity(activity -> {
                assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
                assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
            });
            onGl(scenario, app -> {
                assertOnTitle(app, false);
                tap(app, title(app).newGame);
            });
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            awaitSetup(scenario);
            onGl(scenario, app -> {
                assertFalse(splash(app).isVisible());
                assertNull(game(app));
                SetupScreenLayout setupLayout = (SetupScreenLayout) field("setupLayout").get(app);
                GameSetup setup = (GameSetup) field("setup").get(app);
                tap(app, setupLayout.leaderCards[0]);
                assertEquals(Collections.singletonList(0), setup.getSelectedOpponentIds());
            });
        });
    }

    @Test
    public void continueLetsPlayerChooseAnOlderSlotAfterAStaleSlotFails() throws Exception {
        withFreshTitle((scenario, directory) -> onGl(scenario, app -> {
            GameSaveStore store = AndroidGameSaveStore.create(directory);
            GameState older = checkpoint(2);
            GameState newer = checkpoint(1);
            store.save(1, older);
            store.save(2, newer);
            File olderFile = new File(directory, "manual-game.wd2");
            File newerFile = new File(directory, "manual-game-slot-2.wd2");
            File staleFile = new File(directory, "manual-game-slot-3.wd2");
            long oldest = System.currentTimeMillis() - 60_000;
            assertTrue(olderFile.setLastModified(oldest));
            assertTrue(newerFile.setLastModified(oldest + 10_000));
            long olderModified = olderFile.lastModified();
            long newerModified = newerFile.lastModified();
            assertTrue(olderModified < newerModified);
            byte[] olderBytes = read(olderFile);
            byte[] newerBytes = read(newerFile);

            app.setGameSaveStore(store);
            capture(app, "title-menu-continue.png");
            TitleMenuLayout title = title(app);
            tap(app, title.continueGame);
            assertOnTitle(app, true);
            capture(app, "title-slot-picker-mixed.png");
            tap(app, title.slots[2]);
            assertOnTitle(app, true);
            assertFalse(store.hasSave(3));

            tap(app, title.back);
            assertOnTitle(app, false);
            store.save(3, checkpoint(0));
            tap(app, title.continueGame);
            assertOnTitle(app, true);
            render(app);
            corrupt(staleFile);
            byte[] staleBytes = read(staleFile);
            tap(app, title.slots[2]);
            assertOnTitle(app, true);
            assertEquals("Slot 3 is unavailable.", field("titleMessage").get(app));
            tap(app, title.slots[2]);
            assertOnTitle(app, true);
            assertEquals(2, store.latestCompatible().slot);

            tap(app, title.slots[0]);
            GameState loaded = game(app);
            assertFalse(splash(app).isVisible());
            assertNotNull(loaded);
            assertRestoredMatch(store, 1, older, loaded);
            assertArrayEquals(olderBytes, read(olderFile));
            assertArrayEquals(newerBytes, read(newerFile));
            assertArrayEquals(staleBytes, read(staleFile));
            assertEquals(olderModified, olderFile.lastModified());
            assertEquals(newerModified, newerFile.lastModified());
            tap(app, title.continueGame);
            assertSame("A second touch cannot reload the title checkpoint", loaded, game(app));
        }));
    }

    private static byte[] read(File checkpoint) {
        return Gdx.files.absolute(checkpoint.getAbsolutePath()).readBytes();
    }

    private static void assertRestoredMatch(GameSaveStore store, int slot, GameState checkpoint,
                                            GameState loaded) throws Exception {
        assertArrayEquals(GameSaveCodec.encode(checkpoint), GameSaveCodec.encode(store.load(slot)));
        // A load returns to the battlefield, retaining accepted orders and closing any draft.
        checkpoint.cancelSelection();
        assertNull(loaded.getSelectedAction());
        assertEquals(GameState.Action.RESOURCES, loaded.getQueuedAction(0));
        assertArrayEquals(GameSaveCodec.encode(checkpoint), GameSaveCodec.encode(loaded));
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

    private static void assertOnTitle(WorldDominationGame app, boolean pickingSlots) throws Exception {
        assertTrue(splash(app).isVisible());
        assertNull(game(app));
        assertEquals(pickingSlots, field("titlePickingSlots").getBoolean(app));
    }

    private static TitleMenuLayout title(WorldDominationGame app) throws Exception {
        return (TitleMenuLayout) field("titleMenu").get(app);
    }

    private static SplashScreenState splash(WorldDominationGame app) throws Exception {
        return (SplashScreenState) field("splashState").get(app);
    }

    private static Field field(String name) throws Exception {
        return SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class, name);
    }

    private interface TitleCheck {
        void run(GameActivityScenario scenario, File directory) throws Exception;
    }

    private interface GlCheck {
        void run(WorldDominationGame app) throws Exception;
    }

    private static void withFreshTitle(TitleCheck check) throws Exception {
        File directory = createStoreDirectory();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            check.run(scenario, directory);
        } finally {
            removeStoreDirectory(directory);
        }
    }

    private static void onGl(GameActivityScenario scenario, GlCheck check) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> activity.postRunnable(() -> {
            try {
                check.run((WorldDominationGame) Gdx.app.getApplicationListener());
            } catch (Throwable problem) {
                failure.set(problem);
            } finally {
                done.countDown();
            }
        }));
        assertTrue("Title menu GL test timed out", done.await(90, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Title menu GL test failed", failure.get());
    }

    private static void awaitSetup(GameActivityScenario scenario) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (!ready.get() && System.nanoTime() < deadline) {
            onGl(scenario, app -> ready.set(!splash(app).isVisible()));
            if (!ready.get()) Thread.sleep(20);
        }
        assertTrue("New Game must open Setup after the picker closes", ready.get());
    }
}
