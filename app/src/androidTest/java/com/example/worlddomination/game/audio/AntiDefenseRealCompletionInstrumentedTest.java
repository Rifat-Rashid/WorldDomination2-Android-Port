package com.example.worlddomination.game.audio;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.android.AndroidMusic;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Real AndroidMusic playback of committed source-trimmed WAVs; no frame timer. */
@RunWith(AndroidJUnit4.class)
public final class AntiDefenseRealCompletionInstrumentedTest {
    private static final int CALLBACK_TIMEOUT_SECONDS = 25;

    @Test public void eightRepeatAirGroupCompletesOnceAcrossPauseMuteAndHandoff()
            throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            AtomicReference<AntiDefenseAudioPlayback> playbackRef = new AtomicReference<>();
            AtomicReference<AntiDefenseAudioPlayback.Owner> ownerRef = new AtomicReference<>();
            AtomicReference<LifecycleListener> lifecycleRef = new AtomicReference<>();
            AtomicReference<Factory> factoryRef = new AtomicReference<>();
            AtomicReference<RecordingMusic> firstRef = new AtomicReference<>();
            AtomicReference<RecordingMusic> secondRef = new AtomicReference<>();
            try {
                runOnGl(scenario, () -> {
                    Factory factory = new Factory();
                    factoryRef.set(factory);
                    AntiDefenseAudioPlayback playback = new AntiDefenseAudioPlayback(
                            factory::create, .8f, Gdx.app::postRunnable);
                    AntiDefenseAudioPlayback.Owner owner = playback.createOwner(
                            AntiDefenseAudioPlayback.Type.AIR);
                    LifecycleListener lifecycle = new LifecycleListener() {
                        @Override public void pause() { playback.pause(); }
                        @Override public void resume() { playback.resume(); }
                        @Override public void dispose() { playback.dispose(); }
                    };
                    Gdx.app.addLifecycleListener(lifecycle);
                    playbackRef.set(playback);
                    ownerRef.set(owner);
                    lifecycleRef.set(lifecycle);
                    assertTrue(owner.requestShot());
                    assertEquals(1, factory.created.size());
                    assertEquals(OriginalAudio.Cue.ANTI_AIR_GUN, factory.cues.get(0));
                    assertEquals(8, (int) factory.repeats.get(0));
                    RecordingMusic first = factory.created.get(0);
                    firstRef.set(first);
                    assertEquals(1, first.plays.get());
                    assertEquals(0.5961f, first.durationSeconds(), .04f);
                    // Stop in the same GL turn, before the short original cue can finish.
                    playback.pause();
                    playback.setMuted(true);
                    assertEquals(0f, first.getVolume(), .001f);
                });
                RecordingMusic first = firstRef.get();
                assertNotNull(first);
                scenario.moveToState(Lifecycle.State.CREATED);
                float pausedPosition = first.getPosition();
                Thread.sleep(250); // Observes media pause, never drives source timing.
                assertEquals(pausedPosition, first.getPosition(), .08f);
                assertEquals(0, first.completions.get());
                scenario.moveToState(Lifecycle.State.RESUMED);
                assertTrue("actual generated WAV reaches its final completion",
                        first.completed.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                runOnGl(scenario, () -> {
                    AntiDefenseAudioPlayback playback = playbackRef.get();
                    AntiDefenseAudioPlayback.Owner owner = ownerRef.get();
                    assertEquals("initial start and lifecycle resume share one Music", 2,
                            first.plays.get());
                    assertEquals("one Music for all eight source periods", 1,
                            factoryRef.get().created.size());
                    assertEquals("one final native callback", 1, first.completions.get());
                    assertEquals(1, first.disposals.get());
                    playback.setMuted(false);
                    assertTrue("final completion rearms the source group", owner.requestShot());
                    RecordingMusic second = factoryRef.get().created.get(1);
                    assertNotNull(second);
                    secondRef.set(second);
                    assertEquals(1, second.plays.get());
                    assertEquals(0, second.disposals.get());
                    first.deliverLateCallback(); // Deliberately synthetic stale group callback.
                });
                runOnGl(scenario, () -> {
                    AntiDefenseAudioPlayback.Owner owner = ownerRef.get();
                    RecordingMusic second = secondRef.get();
                    assertFalse("stale first group cannot finish the second", owner.requestShot());
                    assertEquals(0, second.disposals.get());
                    owner.close();
                    assertEquals(1, second.disposals.get());
                    second.deliverLateCallback();
                    playbackRef.get().finish();
                });
                runOnGl(scenario, () -> assertFalse(ownerRef.get().requestShot()));
            } finally {
                if (scenario.getState() != Lifecycle.State.RESUMED)
                    scenario.moveToState(Lifecycle.State.RESUMED);
                AntiDefenseAudioPlayback playback = playbackRef.get();
                LifecycleListener lifecycle = lifecycleRef.get();
                if (playback != null) runOnGl(scenario, () -> {
                    if (lifecycle != null) Gdx.app.removeLifecycleListener(lifecycle);
                    playback.dispose();
                });
            }
        }
    }

    @Test public void trimmedRotationAndRocketHaveOneRealCompletionEach()
            throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            AtomicReference<AntiDefenseAudioPlayback> playbackRef = new AtomicReference<>();
            AtomicReference<RecordingMusic> rotateRef = new AtomicReference<>();
            AtomicReference<RecordingMusic> rocketRef = new AtomicReference<>();
            try {
                runOnGl(scenario, () -> {
                    Factory factory = new Factory();
                    AntiDefenseAudioPlayback playback = new AntiDefenseAudioPlayback(
                            factory::create, Gdx.app::postRunnable);
                    AntiDefenseAudioPlayback.Owner owner = playback.createOwner(
                            AntiDefenseAudioPlayback.Type.ROCKET);
                    playbackRef.set(playback);
                    assertTrue(owner.requestRotation());
                    assertTrue(owner.requestShot());
                    assertEquals(2, factory.created.size());
                    assertEquals(1, (int) factory.repeats.get(0));
                    assertEquals(1, (int) factory.repeats.get(1));
                    assertEquals(OriginalAudio.Cue.ANTI_AIR_ROTATE, factory.cues.get(0));
                    assertEquals(OriginalAudio.Cue.ANTI_ROCKET_FIRE, factory.cues.get(1));
                    RecordingMusic rotate = factory.created.get(0);
                    RecordingMusic rocket = factory.created.get(1);
                    rotateRef.set(rotate);
                    rocketRef.set(rocket);
                    assertEquals(2.0009f, rotate.durationSeconds(), .04f);
                    assertEquals(1.3124f, rocket.durationSeconds(), .04f);
                });
                assertTrue(rocketRef.get().completed.await(CALLBACK_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS));
                assertTrue(rotateRef.get().completed.await(CALLBACK_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS));
                runOnGl(scenario, () -> {
                    assertEquals(1, rocketRef.get().completions.get());
                    assertEquals(1, rotateRef.get().completions.get());
                    assertEquals(1, rocketRef.get().disposals.get());
                    assertEquals(1, rotateRef.get().disposals.get());
                });
            } finally {
                AntiDefenseAudioPlayback playback = playbackRef.get();
                if (playback != null) runOnGl(scenario, playback::dispose);
            }
        }
    }

    private static void runOnGl(GameActivityScenario scenario, Throwing action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        }));
        assertTrue("GL task completed", done.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private interface Throwing { void run() throws Exception; }

    private static final class Factory {
        final List<OriginalAudio.Cue> cues = new ArrayList<>();
        final List<Integer> repeats = new ArrayList<>();
        final List<RecordingMusic> created = new ArrayList<>();

        Music create(OriginalAudio.Cue cue, int repeatCount) {
            String file;
            if (cue == OriginalAudio.Cue.ANTI_AIR_GUN && repeatCount == 8)
                file = "generated/audio/anti-air-gun-eight.wav";
            else if (cue == OriginalAudio.Cue.ANTI_AIR_ROTATE && repeatCount == 1)
                file = "generated/audio/anti-air-rotate.wav";
            else if (cue == OriginalAudio.Cue.ANTI_ROCKET_FIRE && repeatCount == 1)
                file = "generated/audio/anti-rocket-fire.wav";
            else throw new IllegalArgumentException("Unexpected source finite sound group");
            RecordingMusic recording = new RecordingMusic(
                    Gdx.audio.newMusic(Gdx.files.internal(file)));
            cues.add(cue);
            repeats.add(repeatCount);
            created.add(recording);
            return recording;
        }
    }

    private static final class RecordingMusic implements Music {
        final Music actual;
        final AtomicInteger plays = new AtomicInteger();
        final AtomicInteger disposals = new AtomicInteger();
        final AtomicInteger completions = new AtomicInteger();
        final CountDownLatch completed = new CountDownLatch(1);
        volatile OnCompletionListener listener;

        RecordingMusic(Music actual) { this.actual = actual; }
        float durationSeconds() {
            assertTrue("Android backend is exercised", actual instanceof AndroidMusic);
            return ((AndroidMusic) actual).getDuration();
        }
        void deliverLateCallback() {
            OnCompletionListener callback = listener;
            if (callback != null) callback.onCompletion(this);
        }
        @Override public void play() { plays.incrementAndGet(); actual.play(); }
        @Override public void pause() { actual.pause(); }
        @Override public void stop() { actual.stop(); }
        @Override public boolean isPlaying() { return actual.isPlaying(); }
        @Override public void setLooping(boolean looping) { actual.setLooping(looping); }
        @Override public boolean isLooping() { return actual.isLooping(); }
        @Override public void setVolume(float volume) { actual.setVolume(volume); }
        @Override public float getVolume() { return actual.getVolume(); }
        @Override public void setPan(float pan, float volume) { actual.setPan(pan, volume); }
        @Override public void setPosition(float position) { actual.setPosition(position); }
        @Override public float getPosition() { return actual.getPosition(); }
        @Override public void dispose() { disposals.incrementAndGet(); actual.dispose(); }
        @Override public void setOnCompletionListener(OnCompletionListener callback) {
            listener = callback;
            actual.setOnCompletionListener(music -> {
                OnCompletionListener current = listener;
                if (current != null) current.onCompletion(this);
                completions.incrementAndGet();
                completed.countDown();
            });
        }
    }
}
