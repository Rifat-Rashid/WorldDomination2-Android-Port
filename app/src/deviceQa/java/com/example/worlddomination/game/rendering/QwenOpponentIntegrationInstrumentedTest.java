package com.example.worlddomination.game.rendering;

import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.ai.LocalOpponentRuntime;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.GameSettingsMenu;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.capture;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.captureFrame;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.game;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.installMatch;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.openSettings;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.queue;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.render;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.tap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Run on a supported QA install with the pinned model provisioned, while the device is offline. */
@RunWith(AndroidJUnit4.class)
public final class QwenOpponentIntegrationInstrumentedTest {
    @Test
    public void persistsTheSwitchAndUsesStockQwenThenReturnsToSeededBaseline() throws Exception {
        assertTrue("Run with wd2QwenOffline=true on a fresh QA app with a provisioned model", "true".equals(
                InstrumentationRegistry.getArguments().getString("wd2QwenOffline")));
        int rivals = Integer.parseInt(InstrumentationRegistry.getArguments().getString("wd2QwenRivals", "1"));
        assertTrue(rivals >= 1 && rivals <= 4);

        AtomicReference<String> modelHash = new AtomicReference<>();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            enterSetup(scenario);
            onGameThread(scenario, app -> {
                GameSettingsMenu menu = openSettings(app);
                assertEquals(LocalOpponentRuntime.Stage.OFF, menu.getLocalModel().stage);
                assertFalse(menu.getLocalModel().requested);
                capture(app, "qwen-off-fresh.png");
                tap(app, menu.qwen);
                assertTrue(runtime(app).snapshot().requested);
            });
            awaitReady(scenario, true);
            onGameThread(scenario, app -> {
                capture(app, "qwen-ready.png");
                modelHash.set(runtime(app).snapshot().modelSha256);
                assertTrue(modelHash.get().matches("[a-f0-9]{64}"));
            });
        }

        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            onGameThread(scenario, app -> assertTrue(runtime(app).snapshot().requested));
            awaitReady(scenario, false);
            enterSetup(scenario);
            try {
                onGameThread(scenario, app -> installMatch(app, queuedMatch(rivals)));
                awaitGameplayAssets(scenario);
                onGameThread(scenario, QwenOpponentIntegrationInstrumentedTest::startPlanning);
                awaitSettled(scenario, rivals);
                String log = decisionLog();
                assertTrue(log, log.contains("source=qwen plan_id="));
                assertTrue(log, log.contains("model_sha256=" + modelHash.get()));

                GameState baseline = queuedMatch(1);
                baseline.confirmTurn();
                onGameThread(scenario, app -> {
                    installMatch(app, queuedMatch(1));
                    startPlanning(app);
                });
                onGameThread(scenario, app -> {
                    GameSettingsMenu menu = openSettings(app);
                    assertFalse(menu.canSave());
                    tap(app, menu.qwen);
                    assertEquals(LocalOpponentRuntime.Stage.OFF, runtime(app).snapshot().stage);
                    assertEquals(0, game(app).getTurn());
                    capture(app, "qwen-off-after-cancel.png");
                    tap(app, menu.close);
                });
                awaitSettled(scenario, 1);
                onGameThread(scenario, app ->
                        assertArrayEquals(GameSaveCodec.encode(baseline), GameSaveCodec.encode(game(app))));
                assertTrue(decisionLog().contains("reason=disabled"));
            } finally {
                onGameThread(scenario, app -> runtime(app).setRequested(false));
            }
        }
    }

    private static GameState queuedMatch(int rivals) {
        GameSetup setup = new GameSetup(24L);
        for (int rival = 0; rival < rivals; rival++) assertTrue(setup.toggleOpponent(rival));
        GameState game = new GameState(setup);
        queue(game, GameState.Action.RESOURCES);
        queue(game, GameState.Action.SKIP);
        return game;
    }

    private static void startPlanning(WorldDominationGame app) throws Exception {
        GameState game = game(app);
        Method confirm = WorldDominationGame.class.getDeclaredMethod("confirmTurnAndPreparePlayback");
        confirm.setAccessible(true);
        confirm.invoke(app);
        assertTrue(game.isOpponentPlanningPending());
        assertEquals(0, game.getTurn());
    }

    private static void enterSetup(GameActivityScenario scenario) throws Exception {
        onGameThread(scenario, app -> {
            Method next = WorldDominationGame.class.getDeclaredMethod("requestSplashContinue");
            next.setAccessible(true);
            next.invoke(app);
        });
        long deadline = SystemClock.uptimeMillis() + 60_000;
        AtomicReference<Boolean> visible = new AtomicReference<>();
        do {
            onGameThread(scenario, app -> {
                Object splash = SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class, "splashState").get(app);
                visible.set((Boolean) splash.getClass().getMethod("isVisible").invoke(splash));
            });
            if (!visible.get()) return;
            SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Setup assets did not load");
    }

    private static void awaitGameplayAssets(GameActivityScenario scenario) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 60_000;
        AtomicReference<Boolean> ready = new AtomicReference<>();
        do {
            onGameThread(scenario, app -> {
                OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) SourceBattleCleanupInstrumentedTest
                        .field(WorldDominationGame.class, "flashAnimationAssets").get(app);
                ready.set(assets.isLoaded());
            });
            if (ready.get()) return;
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Gameplay assets did not load");
    }

    private static LocalOpponentRuntime runtime(WorldDominationGame app) throws Exception {
        return (LocalOpponentRuntime) SourceBattleCleanupInstrumentedTest
                .field(WorldDominationGame.class, "localOpponentRuntime").get(app);
    }

    private static void awaitReady(GameActivityScenario scenario, boolean saveFrames) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 90_000;
        AtomicReference<LocalOpponentRuntime.Snapshot> model = new AtomicReference<>();
        boolean loadingCaptured = false;
        do {
            boolean captureLoading = saveFrames && !loadingCaptured;
            onGameThread(scenario, app -> {
                LocalOpponentRuntime.Snapshot snapshot;
                if (captureLoading) {
                    render(app);
                    GameSettingsMenu menu = (GameSettingsMenu) SourceBattleCleanupInstrumentedTest
                            .field(WorldDominationGame.class, "settingsMenu").get(app);
                    snapshot = menu.getLocalModel();
                } else {
                    snapshot = runtime(app).snapshot();
                }
                model.set(snapshot);
                if (captureLoading && snapshot.stage == LocalOpponentRuntime.Stage.LOADING && snapshot.progress > 0f) {
                    captureFrame("qwen-native-loading.png");
                }
            });
            LocalOpponentRuntime.Snapshot snapshot = model.get();
            if (snapshot.stage == LocalOpponentRuntime.Stage.READY) return;
            if (snapshot.stage == LocalOpponentRuntime.Stage.ERROR) throw new AssertionError(snapshot.detail);
            loadingCaptured |= snapshot.stage == LocalOpponentRuntime.Stage.LOADING && snapshot.progress > 0f;
            SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Qwen stayed at " + model.get().stage + ": " + model.get().detail);
    }

    private static void awaitSettled(GameActivityScenario scenario, int rivals) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 65_000L * rivals;
        AtomicInteger turn = new AtomicInteger();
        do {
            onGameThread(scenario, app -> turn.set(game(app).getTurn()));
            if (turn.get() == 1) return;
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("The turn did not settle");
    }

    private static String decisionLog() throws Exception {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("logcat -d --pid " + Process.myPid() + " -s WD2Qwen:I");
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void onGameThread(GameActivityScenario scenario, SourceBattleCleanupInstrumentedTest.GameCheck check)
            throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> activity.postRunnable(() -> {
            try {
                check.run((WorldDominationGame) activity.getApplicationListener());
            } catch (Throwable problem) {
                failure.set(problem);
            } finally {
                done.countDown();
            }
        }));
        if (!done.await(30, TimeUnit.SECONDS)) throw new AssertionError("The game thread did not respond");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
