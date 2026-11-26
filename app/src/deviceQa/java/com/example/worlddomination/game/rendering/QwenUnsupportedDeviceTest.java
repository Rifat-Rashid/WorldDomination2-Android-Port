package com.example.worlddomination.game.rendering;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.ai.LocalOpponentRuntime;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.GameSettingsMenu;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.capture;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.game;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.installMatch;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.openSettings;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.queue;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.tap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Run explicitly on Android 12L or earlier, with no model installed. */
@RunWith(AndroidJUnit4.class)
public final class QwenUnsupportedDeviceTest {
    @Test
    public void unavailableSwitchLeavesNormalOpponentTurnAvailable() throws Exception {
        assertTrue(Build.VERSION.SDK_INT < 33);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSettingsMenu menu = openSettings(app);
            assertFalse(menu.getLocalModel().supported);
            assertEquals(LocalOpponentRuntime.Stage.UNSUPPORTED, menu.getLocalModel().stage);
            assertTrue(menu.getLocalModel().detail.contains("Android 13"));
            tap(app, menu.qwen);
            assertFalse(menu.getLocalModel().requested);
            capture(app, "qwen-unsupported.png");
            tap(app, menu.close);

            GameSetup setup = new GameSetup(24L);
            assertTrue(setup.toggleOpponent(0));
            GameState match = new GameState(setup);
            queue(match, GameState.Action.RESOURCES);
            queue(match, GameState.Action.SKIP);
            GameState baseline = GameSaveCodec.decode(GameSaveCodec.encode(match));
            baseline.confirmTurn();
            installMatch(app, match);
            Method confirm = WorldDominationGame.class.getDeclaredMethod("confirmTurnAndPreparePlayback");
            confirm.setAccessible(true);
            confirm.invoke(app);
            assertFalse(game(app).isOpponentPlanningPending());
            assertArrayEquals(GameSaveCodec.encode(baseline), GameSaveCodec.encode(game(app)));
        });
    }
}
