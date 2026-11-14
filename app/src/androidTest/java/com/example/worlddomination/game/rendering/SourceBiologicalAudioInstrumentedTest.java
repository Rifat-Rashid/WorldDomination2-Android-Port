package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.audio.Sound;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the legal source submarine turn's owned audio calls, not audible output. */
@RunWith(AndroidJUnit4.class)
public final class SourceBiologicalAudioInstrumentedTest {
    @Test public void legalSubmarineStopsItsLaunchVoiceAtBiologicalImpact()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceBiologicalPlaybackInstrumentedTest.gameReadyForSubmarine();
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();

            OriginalAudio audio = (OriginalAudio) field(WorldDominationGame.class,
                    "audio").get(app);
            boolean restoreMuted = !audio.isSoundEnabled();
            if (restoreMuted) audio.toggleSound();
            @SuppressWarnings("unchecked")
            Map<OriginalAudio.Cue, Sound> sounds = (Map<OriginalAudio.Cue, Sound>)
                    field(OriginalAudio.class, "sounds").get(audio);
            int[] tick = {0};
            Voice rocket = new Voice(tick, 77L);
            Voice biological = new Voice(tick, 88L);
            Sound previousRocket = sounds.put(OriginalAudio.Cue.WARROCKET, rocket.spy());
            Sound previousBiological = sounds.put(OriginalAudio.Cue.EXPLOSION_MEDIUM,
                    biological.spy());
            try {
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals("legal source submarine turn", 17, game.getTurn());
                assertTrue("resolved biological payload",
                        game.getLastTurnCombatEvents().get(0).bioload);
                for (int frame = 1; frame <= 641; frame++) {
                    tick[0] = frame;
                    advance(app);
                    assertEquals("rocket voice count at source tick " + frame,
                            frame < 461 ? 0 : 1, rocket.plays);
                    assertEquals("rocket stop count at source tick " + frame,
                            frame < 577 ? 0 : 1, rocket.stops);
                    assertEquals("impact voice count at source tick " + frame,
                            frame < 577 ? 0 : 1, biological.plays);
                }
                assertEquals("host SoundObject starts after Unit launch action", 461,
                        rocket.playTick);
                assertEquals("source launch default pan", 0f, rocket.pan, 0f);
                assertEquals("source effect volume", 0.8f, rocket.volume, 0f);
                assertEquals("source pitch", 1f, rocket.pitch, 0f);
                assertEquals("only the owned launch voice stops", 77L, rocket.stoppedId);
                assertEquals("source impact stops launch", 577, rocket.stopTick);
                assertEquals("source biological impact", 577, biological.playTick);
                assertEquals("source impact cue volume", 0.8f, biological.volume, 0f);
                assertEquals("source pre-move clip X pan", (212.72297985320708f - 400f) / 400f,
                        biological.pan, 0.00001f);
            } finally {
                restore(sounds, OriginalAudio.Cue.WARROCKET, previousRocket);
                restore(sounds, OriginalAudio.Cue.EXPLOSION_MEDIUM,
                        previousBiological);
                if (restoreMuted) audio.toggleSound();
            }
        });
    }

    private static final class Voice {
        private final int[] tick;
        private final long id;
        int plays;
        int stops;
        int playTick = -1;
        int stopTick = -1;
        long stoppedId = -1L;
        float volume = Float.NaN;
        float pitch = Float.NaN;
        float pan = Float.NaN;

        Voice(int[] tick, long id) { this.tick = tick; this.id = id; }

        Sound spy() {
            return (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),
                    new Class<?>[] {Sound.class}, (proxy, method, args) -> {
                        if ("play".equals(method.getName())) {
                            plays++;
                            playTick = tick[0];
                            assertEquals("volume/pitch/pan overload", 3, args.length);
                            volume = ((Number) args[0]).floatValue();
                            pitch = ((Number) args[1]).floatValue();
                            pan = ((Number) args[2]).floatValue();
                            return id;
                        }
                        if ("stop".equals(method.getName()) && args != null
                                && args.length == 1) {
                            stops++;
                            stopTick = tick[0];
                            stoppedId = ((Number) args[0]).longValue();
                        }
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                    });
        }
    }

    private static void restore(Map<OriginalAudio.Cue, Sound> sounds,
                                OriginalAudio.Cue cue, Sound original) {
        if (original == null) sounds.remove(cue);
        else sounds.put(cue, original);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }
}
