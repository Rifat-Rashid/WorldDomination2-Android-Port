package com.example.worlddomination.platform.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real SoundPool load/stream integration; SoundPool exposes no decoded completion callback. */
@RunWith(AndroidJUnit4.class)
public final class SourceAndroidAudioInstrumentedTest {
    @Test public void finiteRepeatsUseRealAndroidSoundPoolAndKeepOpaqueStreamStable()
            throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            AtomicReference<SourceAndroidAudio> backendRef = new AtomicReference<>();
            AtomicReference<SourceAndroidSound> soundRef = new AtomicReference<>();
            try {
                onGl(scenario, () -> {
                    assertTrue(Gdx.audio instanceof SourceAndroidAudio);
                    SourceAndroidAudio backend = (SourceAndroidAudio) Gdx.audio;
                    backendRef.set(backend);
                    // Reuse the first actual app sample, already queued at startup.
                    // Enqueuing a 46th decode would measure the entire startup backlog
                    // rather than this native repeat and lifecycle contract.
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    OriginalAudio audio = (OriginalAudio) member(app, "audio");
                    Map<?, ?> loadedSounds = (Map<?, ?>) member(audio, "sounds");
                    Object sound = loadedSounds.get(OriginalAudio.Cue.STARTUP);
                    assertTrue(sound instanceof SourceAndroidSound);
                    soundRef.set((SourceAndroidSound) sound);
                });
                SourceAndroidSound sound = soundRef.get();
                awaitLoaded(sound);

                onGl(scenario, () -> {
                    assertSame(backendRef.get(), Gdx.audio);
                    long longHandle = sound.playFinite(.21f, 1f, 0f, 9999);
                    assertTrue("Native SoundPool accepted a 9999-play voice", longHandle > 0L);
                    Object longVoice = voice(sound, longHandle);
                    int longStream = (int) member(longVoice, "streamId");
                    assertTrue("SoundPool returned a real stream ID", longStream > 0);
                    assertEquals(9998, member(longVoice, "repeats"));
                    assertEquals(.21f, (float) member(longVoice, "volume"), 0f);

                    sound.setVolume(longHandle, 0f);
                    assertEquals(0f, (float) member(longVoice, "volume"), 0f);
                    sound.setVolume(longHandle, .21f);
                    assertEquals(.21f, (float) member(longVoice, "volume"), 0f);
                    sound.pause(longHandle);
                    assertEquals(longStream, member(longVoice, "streamId"));
                    sound.resume(longHandle);
                    assertEquals(longStream, member(longVoice, "streamId"));

                    long once = sound.playFinite(.21f, 1f, 0f, 1);
                    long thrice = sound.playFinite(.21f, 1f, 0f, 3);
                    assertTrue("One play allocated", once > 0L);
                    assertTrue("Three plays allocated", thrice > 0L);
                    assertNotEquals(longHandle, once);
                    assertEquals(0, member(voice(sound, once), "repeats"));
                    assertEquals(2, member(voice(sound, thrice), "repeats"));
                    assertTrue((int) member(voice(sound, once), "streamId") > 0);
                    assertTrue((int) member(voice(sound, thrice), "streamId") > 0);

                    sound.stop(longHandle);
                    sound.stop(once);
                    sound.stop(thrice);
                    assertFalse(voices(sound).containsKey(longHandle));
                    assertFalse(voices(sound).containsKey(once));
                    assertFalse(voices(sound).containsKey(thrice));
                    int sampleId = (int) member(sound, "sampleId");
                    sound.dispose();
                    assertFalse(samples(backendRef.get()).containsKey(sampleId));
                    // A late load notification cannot restart a disposed sample.
                    sound.loaded();
                    assertEquals(-1L, sound.playFinite(.21f, 1f, 0f, 9999));
                    assertTrue(voices(sound).isEmpty());
                });
            } finally {
                SourceAndroidSound sound = soundRef.get();
                if (sound != null) onGl(scenario, sound::dispose);
            }
        }
    }

    private static void awaitLoaded(SourceAndroidSound sound) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            synchronized (sound) {
                if ((boolean) member(sound, "loaded")) return;
                assertFalse("Native sample load failed", (boolean) member(sound, "loadFailed"));
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("Native SoundPool did not load the packaged MP3 within 10 seconds");
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Object> voices(SourceAndroidSound sound) throws Exception {
        return (Map<Long, Object>) member(sound, "voices");
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, SourceAndroidSound> samples(SourceAndroidAudio audio)
            throws Exception {
        return (Map<Integer, SourceAndroidSound>) member(audio, "samples");
    }

    private static Object voice(SourceAndroidSound sound, long handle) throws Exception {
        Object result = voices(sound).get(handle);
        assertTrue("Opaque handle remains registered", result != null);
        return result;
    }

    private static Object member(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void onGl(GameActivityScenario scenario, Throwing action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
            finally { completed.countDown(); }
        }));
        assertTrue("GL task completed", completed.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private interface Throwing { void run() throws Exception; }
}
