package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Captures the real World impact cue call without asserting audible output. */
@RunWith(AndroidJUnit4.class)
public final class SourceSuicideAudioInstrumentedTest {
    private static final String SOURCE_ASSET =
            "original/runtime/audio/9_unit_Suicide explode.wav.mp3.gz";

    @Test public void legalSuicideImpactUsesOriginalCueOnceAtPreMovePan()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceSuicideDepthsInstrumentedTest.gameReadyForAttack();
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();

            OriginalAudio audio = (OriginalAudio) field(WorldDominationGame.class,
                    "audio").get(app);
            @SuppressWarnings("unchecked")
            Map<OriginalAudio.Cue, Sound> sounds = (Map<OriginalAudio.Cue, Sound>)
                    field(OriginalAudio.class, "sounds").get(audio);
            assertEquals("source cue asset", SOURCE_ASSET,
                    "original/runtime/audio/" + field(OriginalAudio.Cue.class,
                            "compressedAsset").get(OriginalAudio.Cue.SUICIDE_EXPLODE));
            Method internalPath = OriginalAudio.class.getDeclaredMethod("internalPath",
                    OriginalAudio.Cue.class);
            internalPath.setAccessible(true);
            String packagedAsset = (String) internalPath.invoke(null,
                    OriginalAudio.Cue.SUICIDE_EXPLODE);
            assertEquals("build-preprocessed original MP3",
                    "original/runtime/audio/9_unit_Suicide explode.wav.mp3", packagedAsset);
            assertTrue("packaged original suicide impact audio",
                    Gdx.files.internal(packagedAsset).exists());

            final int[] sourceTick = {0};
            final int[] plays = {0};
            final int[] playedAt = {-1};
            final int[] argumentCount = {-1};
            final float[] values = {Float.NaN, Float.NaN, Float.NaN};
            Sound probe = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),
                    new Class<?>[] {Sound.class}, (proxy, method, args) -> {
                        if ("play".equals(method.getName())) {
                            plays[0]++;
                            playedAt[0] = sourceTick[0];
                            argumentCount[0] = args == null ? 0 : args.length;
                            if (argumentCount[0] == 3) {
                                values[0] = ((Number) args[0]).floatValue();
                                values[1] = ((Number) args[1]).floatValue();
                                values[2] = ((Number) args[2]).floatValue();
                            }
                            return 77L;
                        }
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                    });
            Sound original = sounds.put(OriginalAudio.Cue.SUICIDE_EXPLODE, probe);
            boolean restoreMuted = !audio.isSoundEnabled();
            if (restoreMuted) audio.toggleSound();
            try {
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals("legal source attack turn", 4, game.getTurn());
                for (int tick = 1; tick <= 363; tick++) {
                    sourceTick[0] = tick;
                    advance(app);
                    if (tick < 300) {
                        assertEquals("no suicide impact sound before source tick 300",
                                0, plays[0]);
                    } else {
                        assertEquals("one suicide impact sound through source cleanup",
                                1, plays[0]);
                    }
                    if (tick == 300) {
                        @SuppressWarnings("unchecked")
                        List<FlashSuicideBomberAnimation> animations =
                                (List<FlashSuicideBomberAnimation>) field(
                                        WorldDominationGame.class,
                                        "suicideBomberAnimations").get(app);
                        assertEquals(1, animations.size());
                        FlashSuicideBomberAnimation.Actor explosion =
                                animations.get(0).getActor(2);
                        assertEquals("source pre-move explosion clip X", 206f,
                                explosion.renderX, 0.02f);
                        assertEquals("logical position advanced after killUnit", 209f,
                                explosion.sourceX, 0.02f);
                    }
                }
                assertEquals("impact invoked on source tick", 300, playedAt[0]);
                assertEquals("libGDX volume, pitch, pan overload", 3, argumentCount[0]);
                assertEquals("effect volume", 0.8f, values[0], 0f);
                assertEquals("source pitch", 1f, values[1], 0f);
                assertEquals("source getPanBy_x on pre-move X=206", -0.485f,
                        values[2], 0.00001f);
                assertNotEquals("post-move X=209 would use wrong pan", -0.4775f,
                        values[2], 0.00001f);
            } finally {
                if (original == null) sounds.remove(OriginalAudio.Cue.SUICIDE_EXPLODE);
                else sounds.put(OriginalAudio.Cue.SUICIDE_EXPLODE, original);
                if (restoreMuted) audio.toggleSound();
            }
        });
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
