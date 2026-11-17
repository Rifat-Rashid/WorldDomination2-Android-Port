package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.FiniteRepeatSound;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** World integration with fake finite voices; this does not test audible native output. */
@RunWith(AndroidJUnit4.class)
public final class BattleAmbientInstrumentedTest {
    @Test public void battleOwnsBuildingAmbienceThroughLabelPassAndFreshBattle() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalAudio audio = (OriginalAudio) field(WorldDominationGame.class, "audio").get(app);
            audio.initialize();
            Music theme = (Music) field(OriginalAudio.class, "theme").get(audio);
            assertNotNull(theme);
            boolean themePlaying = theme.isPlaying();
            float themeVolume = theme.getVolume();
            @SuppressWarnings("unchecked") Map<OriginalAudio.Cue, Sound> sounds =
                    (Map<OriginalAudio.Cue, Sound>) field(OriginalAudio.class, "sounds").get(audio);
            Voice voice = new Voice();
            OriginalAudio.Cue[] cues = {OriginalAudio.Cue.RESOURCES_AMBIENT,
                    OriginalAudio.Cue.PRODUCTION_AMBIENT, OriginalAudio.Cue.DIPLOMACY_AMBIENT,
                    OriginalAudio.Cue.DEFENSE_AMBIENT, OriginalAudio.Cue.MASS_MEDIA_AMBIENT,
                    OriginalAudio.Cue.TECHNOLOGY_AMBIENT, OriginalAudio.Cue.GAME_AMBIENT};
            Sound[] previous = new Sound[cues.length];
            for (int i = 0; i < cues.length; i++) previous[i] = sounds.put(cues[i], voice.sound);
            try {
                for (int battle = 0; battle < 2; battle++) {
                    GameState game = newGame();
                    field(WorldDominationGame.class, "game").set(app, game);
                    ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                            "cityRenderer").get(app)).reset(game);
                    ((SourceBattleRecordClock) field(WorldDominationGame.class,
                            "battleRecordClock").get(app)).reset();
                    field(WorldDominationGame.class, "modalOpen").setBoolean(app, false);
                    field(WorldDominationGame.class, "skipNextFrameDelta").setBoolean(app, true);
                    app.render();
                    Rectangle row = ((Rectangle[]) field(WorldDominationGame.class, "navRows")
                            .get(app))[GameState.Action.RESOURCES.ordinal()];
                    Method activate = WorldDominationGame.class.getDeclaredMethod(
                            "activateNavigationRow", float.class, float.class);
                    activate.setAccessible(true);
                    activate.invoke(app, row.x + row.width / 2f, row.y + row.height / 2f);
                    assertEquals(GameState.Action.RESOURCES, game.getSelectedAction());
                    assertTrue(field(WorldDominationGame.class, "modalOpen").getBoolean(app));
                    assertTrue("Opening source resources menu is silent", voice.active.isEmpty());
                    app.keyDown(Input.Keys.BACK);
                    assertTrue("Cancelling source resources menu is silent", voice.active.isEmpty());
                    queue(game, GameState.Action.RESOURCES);
                    queue(game, GameState.Action.MASS_MEDIA);
                    Object owner = field(OriginalAudio.class, "battleAmbient").get(audio);
                    Field index = field(owner.getClass(), "nextSlot");
                    if (battle == 1) index.setInt(owner, 7);
                    int before = voice.starts;
                    assertEquals("Selecting and accepting menus produces no ambient", 0, voice.active.size());
                    invoke(app, "confirmTurnAndPreparePlayback");
                    TurnPlaybackSequence sequence = (TurnPlaybackSequence)
                            field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
                    assertTrue("Building voices begin before labels finish", sequence.isWaitingForActionLabels());
                    int expected = 0;
                    for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings()) {
                        if (isSourceAmbientFrame(event.frameName)) expected++;
                    }
                    assertTrue("Fixture must produce source ambient buildings", expected > 0);
                    assertEquals(expected, voice.starts - before);
                    assertEquals(expected, voice.active.size());
                    assertEquals(expected % 10, index.getInt(owner));
                    assertEquals(expected, voice.gainUpdates);
                    int turn = game.getTurn(), money = game.getTurnMoneyMillions();
                    int guard = 0;
                    while (sequence.isWaitingForActionLabels() && guard++ < 500) {
                        assertEquals("Voices remain active through the last label tick", expected, voice.active.size());
                        advance(app);
                        if (sequence.isWaitingForActionLabels()) assertEquals(expected, voice.active.size());
                    }
                    assertTrue("Label pass must end", guard < 500);
                    assertTrue("All battle ambient voices stopped", voice.active.isEmpty());
                    assertEquals(turn, game.getTurn());
                    assertEquals(money, game.getTurnMoneyMillions());
                    assertEquals(themePlaying, theme.isPlaying());
                    assertEquals(themeVolume, theme.getVolume(), 0f);
                    int handoffTicks = 0;
                    while (sequence.getPhase() != TurnPlaybackSequence.Phase.IDLE
                            && handoffTicks++ < 500) {
                        advance(app);
                    }
                    assertEquals("Battle must finish before installing another fixture",
                            TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
                    assertTrue("Battle handoff retains no ambient voices", voice.active.isEmpty());
                    voice.gainUpdates = 0;
                }
            } finally {
                audio.finishBattleAmbient();
                for (int i = 0; i < cues.length; i++) {
                    if (previous[i] == null) sounds.remove(cues[i]);
                    else sounds.put(cues[i], previous[i]);
                }
            }
        });
    }

    private static boolean isSourceAmbientFrame(String frame) {
        return "resources".equals(frame) || "production".equals(frame)
                || "diplomacy".equals(frame) || "defense".equals(frame)
                || "religion".equals(frame) || "research".equals(frame) || "game".equals(frame);
    }
    private static GameState newGame() throws Exception {
        Constructor<GameState> constructor = GameState.class.getDeclaredConstructor(int.class, long.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, 120L, false);
    }
    private static void queue(GameState game, GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(app);
    }
    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true); method.invoke(app, 1f / 24f);
    }
    private static final class Voice {
        long next;
        int starts, gainUpdates;
        final Set<Long> active = new HashSet<>();
        final FiniteRepeatSound sound = (FiniteRepeatSound) Proxy.newProxyInstance(
                FiniteRepeatSound.class.getClassLoader(), new Class<?>[]{FiniteRepeatSound.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "play": case "loop":
                            throw new AssertionError("Ambience must use battle-owned finite playback");
                        case "playFinite":
                            assertEquals(1f, (Float) args[0], 0f);
                            assertEquals(1f, (Float) args[1], 0f);
                            assertEquals(0f, (Float) args[2], 0f);
                            assertEquals(9999, (int) args[3]);
                            long id = ++next; starts++; active.add(id); return id;
                        case "setVolume":
                            assertTrue(active.contains((Long) args[0]));
                            assertEquals(.21f, (Float) args[1], 0f);
                            gainUpdates++; return null;
                        case "stop":
                            if (args != null && args.length == 1) assertTrue(active.remove((Long) args[0]));
                            return null;
                        default: return method.getReturnType() == long.class ? -1L : null;
                    }
                });
    }
}
