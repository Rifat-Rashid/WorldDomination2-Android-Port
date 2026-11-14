package com.example.worlddomination.game.audio;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.audio.Music;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Real AndroidMusic callbacks with original compressed fighter cues, without frame timers. */
@RunWith(AndroidJUnit4.class)
public final class AircraftRealCompletionInstrumentedTest {
    private static final int CALLBACK_TIMEOUT_SECONDS = 25;

    @Test public void fighterCompletionSurvivesLifecyclePauseMuteAndOwnerStop()
            throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            AtomicReference<AircraftAudioPlayback> playbackRef = new AtomicReference<>();
            AtomicReference<AircraftAudioPlayback.Owner> ownerRef = new AtomicReference<>();
            AtomicReference<LifecycleListener> lifecycleRef = new AtomicReference<>();
            AtomicReference<RecordingMusic> takeoffRef = new AtomicReference<>();
            AtomicReference<RecordingMusic> flightRef = new AtomicReference<>();
            try {
                runOnGl(scenario, () -> {
                    WorldDominationGame game = (WorldDominationGame)
                            Gdx.app.getApplicationListener();
                    OriginalAudio original = (OriginalAudio) field(game, "audio");
                    Method load = OriginalAudio.class.getDeclaredMethod(
                            "loadMusic", OriginalAudio.Cue.class);
                    load.setAccessible(true);
                    Map<OriginalAudio.Cue, RecordingMusic> loaded =
                            new EnumMap<>(OriginalAudio.Cue.class);
                    AircraftAudioPlayback playback = new AircraftAudioPlayback(cue -> {
                        try {
                            Music actual = (Music) load.invoke(original, cue);
                            if (actual == null) return null;
                            RecordingMusic recording = new RecordingMusic(actual);
                            loaded.put(cue, recording);
                            return recording;
                        } catch (ReflectiveOperationException error) {
                            throw new IllegalStateException(error);
                        }
                    }, .8f, Gdx.app::postRunnable);
                    AircraftAudioPlayback.Owner owner = playback.createOwner(
                            AircraftAudioPlayback.Kind.FIGHTER);
                    LifecycleListener listener = new LifecycleListener() {
                        @Override public void pause() { playback.pause(); }
                        @Override public void resume() { playback.resume(); }
                        @Override public void dispose() { playback.dispose(); }
                    };
                    Gdx.app.addLifecycleListener(listener);
                    lifecycleRef.set(listener);
                    assertTrue("original takeoff begins", owner.requestTakeoff());
                    assertEquals(2, loaded.size());
                    playbackRef.set(playback);
                    ownerRef.set(owner);
                    takeoffRef.set(loaded.get(OriginalAudio.Cue.FIGHTER_TAKEOFF));
                    flightRef.set(loaded.get(OriginalAudio.Cue.FIGHTER_FLIGHT));
                });
                AircraftAudioPlayback playback = playbackRef.get();
                AircraftAudioPlayback.Owner owner = ownerRef.get();
                RecordingMusic takeoff = takeoffRef.get(), flight = flightRef.get();
                assertNotNull(playback);
                assertNotNull(owner);
                assertNotNull(takeoff);
                assertNotNull(flight);
                assertEquals(1, takeoff.plays.get());
                assertEquals(0, flight.plays.get());

                // AndroidApplication pauses Music before the game's lifecycle callback.
                // Both waits and ActivityScenario transitions occur on this test thread.
                scenario.moveToState(Lifecycle.State.CREATED);
                assertFalse("takeoff is paused by Android", takeoff.isPlaying());
                float pausedPosition = takeoff.getPosition();
                Thread.sleep(250); // Position observation only, never gameplay timing.
                assertEquals("paused media position holds", pausedPosition,
                        takeoff.getPosition(), .08f);
                assertEquals("no flight before actual takeoff completion", 0, flight.plays.get());
                scenario.moveToState(Lifecycle.State.RESUMED);
                assertTrue("takeoff completion from actual MP3",
                        takeoff.completed.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertEquals("the same takeoff Music was resumed", 2, takeoff.plays.get());
                assertEquals("resume continues its media position", pausedPosition,
                        takeoff.lastPlayPositionBefore, .20f);
                assertEquals("completion starts flight", 1, flight.plays.get());
                assertTrue("first real flight completion",
                        flight.completed.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertEquals("retained source callback starts the next flight", 2,
                        flight.plays.get());

                runOnGl(scenario, () -> {
                    playback.setMuted(true);
                    assertEquals(0f, flight.getVolume(), .001f);
                });
                assertTrue("muted real flight still completes and repeats",
                        flight.secondCompleted.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertEquals(3, flight.plays.get());
                runOnGl(scenario, () -> {
                    playback.setMuted(false);
                    assertEquals(.8f, flight.getVolume(), .01f);
                    owner.stop();
                    int stoppedPlays = flight.plays.get();
                    flight.deliverLateCallback();
                    takeoff.deliverLateCallback();
                    assertEquals("late native callback cannot resurrect owner",
                            stoppedPlays, flight.plays.get());
                    assertEquals(1, takeoff.disposals.get());
                    assertEquals(1, flight.disposals.get());
                });
            } finally {
                if (scenario.getState() != Lifecycle.State.RESUMED)
                    scenario.moveToState(Lifecycle.State.RESUMED);
                AircraftAudioPlayback playback = playbackRef.get();
                LifecycleListener listener = lifecycleRef.get();
                if (playback != null) runOnGl(scenario, () -> {
                    if (listener != null) Gdx.app.removeLifecycleListener(listener);
                    playback.dispose();
                });
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

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private interface Throwing { void run() throws Exception; }

    private static final class RecordingMusic implements Music {
        final Music actual;
        final AtomicInteger plays = new AtomicInteger();
        final AtomicInteger disposals = new AtomicInteger();
        final CountDownLatch completed = new CountDownLatch(1);
        final CountDownLatch secondCompleted = new CountDownLatch(1);
        final AtomicInteger completionCount = new AtomicInteger();
        volatile OnCompletionListener listener;
        volatile float lastPlayPositionBefore;

        RecordingMusic(Music actual) { this.actual = actual; }
        void deliverLateCallback() {
            OnCompletionListener callback = listener;
            if (callback != null) callback.onCompletion(this);
        }
        @Override public void play() {
            lastPlayPositionBefore = actual.getPosition();
            plays.incrementAndGet();
            actual.play();
        }
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
                int count = completionCount.incrementAndGet();
                if (count == 1) completed.countDown();
                if (count == 2) secondCompleted.countDown();
            });
        }
    }
}
