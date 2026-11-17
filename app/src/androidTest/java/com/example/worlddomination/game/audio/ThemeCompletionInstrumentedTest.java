package com.example.worlddomination.game.audio;

import android.media.MediaMetadataRetriever;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.android.AndroidMusic;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Uses the original theme MP3 and real Android completion callbacks, not a frame timer. */
@RunWith(AndroidJUnit4.class)
public final class ThemeCompletionInstrumentedTest {
    @Test public void originalThemeCompletesRepeatsAndSurvivesAndroidLifecycle() throws Exception {
        AtomicReference<RecordingMusic> recorded = new AtomicReference<>();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            AtomicReference<OriginalAudio> audioRef = new AtomicReference<>();
            AtomicReference<Float> durationRef = new AtomicReference<>();
            runOnGl(scenario, () -> {
                WorldDominationGame game = (WorldDominationGame) Gdx.app.getApplicationListener();
                OriginalAudio audio = (OriginalAudio) field(game, "audio");
                Music actual = (Music) field(audio, "theme");
                assertNotNull("Original theme was loaded by the real game", actual);
                assertTrue(actual.isPlaying());
                assertFalse("Source starts one play and repeats on completion", actual.isLooping());
                assertEquals("Source default volume is 100", 1f, actual.getVolume(), .001f);
                Method load = OriginalAudio.class.getDeclaredMethod("loadMusic", OriginalAudio.Cue.class);
                load.setAccessible(true);
                setField(audio, "themeFactory", (Supplier<Music>) () -> {
                    try {
                        RecordingMusic music = new RecordingMusic((Music)
                                load.invoke(audio, OriginalAudio.Cue.THEME));
                        recorded.set(music);
                        return music;
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Cannot load original theme", error);
                    }
                });
                assertFalse(audio.toggleMusic());
                audioRef.set(audio);
                assertTrue(audio.toggleMusic());
                RecordingMusic music = recorded.get();
                assertEquals(1, music.plays.get());
                MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                try {
                    metadata.setDataSource(Gdx.files.local(OriginalAudio.cachePath(
                            OriginalAudio.Cue.THEME)).file().getAbsolutePath());
                    String millis = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    assertNotNull(millis);
                    float duration = Long.parseLong(millis) / 1000f;
                    assertTrue("Original theme must have a usable duration", duration > 4f);
                    durationRef.set(duration);
                    android.util.Log.i("WD2Theme", "metadataDuration=" + duration
                            + " playerDuration=" + ((AndroidMusic) music.actual).getDuration());
                } finally {
                    metadata.release();
                }
            });
            RecordingMusic music = recorded.get();
            OriginalAudio audio = audioRef.get();
            float duration = durationRef.get();

            // Backend auto-pause happens before the game's lifecycle callback.
            // Its isPlaying() therefore cannot tell the game whether to resume.
            scenario.moveToState(Lifecycle.State.CREATED);
            assertFalse(music.isPlaying());
            float pausedPosition = music.getPosition();
            Thread.sleep(250); // Observe a stopped position; never drives game/audio timing.
            assertEquals(pausedPosition, music.getPosition(), .08f);
            scenario.moveToState(Lifecycle.State.RESUMED);
            runOnGl(scenario, () -> {
                assertTrue(music.isPlaying());
                assertEquals("Resume continues the same play", 2, music.plays.get());
                assertEquals("Resume must not rewind the track", pausedPosition,
                        music.positionBeforePlay, .20f);
                music.setPosition(duration - .4f);
            });
            boolean firstCompleted = music.firstCompletion.await(20, TimeUnit.SECONDS);
            assertTrue("Actual theme reaches its first completion: duration=" + duration
                    + " position=" + music.getPosition() + " playing=" + music.isPlaying()
                    + " looping=" + music.isLooping() + " plays=" + music.plays.get()
                    + " completions=" + music.completions.get(), firstCompleted);
            runOnGl(scenario, () -> {
                assertEquals("Completion starts exactly one new play", 3, music.plays.get());
                assertFalse(music.isLooping());
                assertTrue(music.isPlaying());
                assertTrue(audio.toggleOutputMuted());
                assertEquals(0f, music.getVolume(), .001f);
                music.setPosition(duration - .4f);
            });
            assertTrue("Muted theme still completes through the Android backend",
                    music.secondCompletion.await(20, TimeUnit.SECONDS));
            runOnGl(scenario, () -> {
                assertEquals(4, music.plays.get());
                assertTrue(music.isPlaying());
                assertEquals("A repeated muted voice remains muted", 0f, music.getVolume(), .001f);
                assertFalse(audio.toggleOutputMuted());
                assertEquals(1f, music.getVolume(), .001f);
                Music.OnCompletionListener previous = music.lastValidListener;
                assertNotNull(previous);
                // Exercise AndroidMusic's queued delivery, which looks up its CURRENT
                // listener later. A captured-listener fake alone cannot test this race.
                ((AndroidMusic) music.actual).onCompletion(null);
                assertFalse(audio.toggleMusic());
                assertFalse(music.isPlaying());
                assertEquals(1, music.disposals.get());
                previous.onCompletion(music);
                assertEquals("Stopped callbacks cannot restart", 4, music.plays.get());
                assertTrue(audio.toggleMusic());
                RecordingMusic replacement = recorded.get();
                assertNotSame(music, replacement);
                assertEquals(1, replacement.plays.get());
                audio.pause();
                previous.onCompletion(music);
                assertEquals("An old callback cannot restart its disposed owner", 4,
                        music.plays.get());
            });
            // The queued AndroidMusic completion runs before this next GL task.
            runOnGl(scenario, () -> {
                RecordingMusic replacement = recorded.get();
                assertEquals(1, replacement.seeks.get());
                audio.resume();
                assertEquals("Resume continues the new owner once", 2, replacement.plays.get());
                assertEquals("Old native event must not rewind the new paused owner", 1,
                        replacement.seeks.get());
                assertEquals(1f, replacement.getVolume(), .001f);
            });
        }
        RecordingMusic music = recorded.get();
        assertNotNull(music);
        assertEquals("Game disposal owns its theme once", 1, music.disposals.get());
        int plays = music.plays.get();
        music.lastValidListener.onCompletion(music);
        assertEquals("Disposed callbacks cannot resurrect playback", plays, music.plays.get());
    }

    private static void runOnGl(GameActivityScenario scenario, Throwing action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        }));
        assertTrue("GL task completed", done.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private interface Throwing { void run() throws Exception; }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingMusic implements Music {
        final Music actual;
        final AtomicInteger plays = new AtomicInteger();
        final AtomicInteger disposals = new AtomicInteger();
        final AtomicInteger seeks = new AtomicInteger();
        final AtomicInteger completions = new AtomicInteger();
        final CountDownLatch firstCompletion = new CountDownLatch(1);
        final CountDownLatch secondCompletion = new CountDownLatch(1);
        volatile OnCompletionListener lastValidListener;
        volatile float positionBeforePlay;

        RecordingMusic(Music actual) { this.actual = actual; }
        @Override public void play() {
            positionBeforePlay = actual.getPosition();
            plays.incrementAndGet();
            actual.play();
        }
        @Override public void pause() { actual.pause(); }
        @Override public void stop() { actual.stop(); }
        @Override public boolean isPlaying() { return actual.isPlaying(); }
        @Override public void setLooping(boolean value) { actual.setLooping(value); }
        @Override public boolean isLooping() { return actual.isLooping(); }
        @Override public void setVolume(float value) { actual.setVolume(value); }
        @Override public float getVolume() { return actual.getVolume(); }
        @Override public void setPan(float pan, float volume) { actual.setPan(pan, volume); }
        @Override public void setPosition(float value) { seeks.incrementAndGet(); actual.setPosition(value); }
        @Override public float getPosition() { return actual.getPosition(); }
        @Override public void dispose() { disposals.incrementAndGet(); actual.dispose(); }
        @Override public void setOnCompletionListener(OnCompletionListener callback) {
            if (callback != null) lastValidListener = callback;
            actual.setOnCompletionListener(callback == null ? null : music -> {
                callback.onCompletion(this);
                int count = completions.incrementAndGet();
                if (count == 1) firstCompletion.countDown();
                if (count == 2) secondCompletion.countDown();
            });
        }
    }
}
