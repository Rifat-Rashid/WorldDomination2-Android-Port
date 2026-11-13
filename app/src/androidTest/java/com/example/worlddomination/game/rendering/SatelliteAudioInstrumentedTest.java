package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.Lifecycle;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.audio.SatelliteAudioPlayback;
import com.example.worlddomination.testing.GameActivityScenario;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises Android MediaPlayer completion through real libGDX Music instances. */
@RunWith(AndroidJUnit4.class)
public final class SatelliteAudioInstrumentedTest {
    private static final String AUDIO_ROOT = "original/runtime/audio/";
    private static final String LASER = "19_unit_Sattelite Lasershot.wav.mp3";
    private static final String ATTACK = "32_Sattelite attack.wav.mp3";

    @Test public void productionAudioResumesMutedSatelliteChainAfterActivityPause()
            throws Exception {
        AtomicReference<OriginalAudio> audioRef = new AtomicReference<>();
        AtomicReference<SatelliteAudioPlayback> ownerRef = new AtomicReference<>();
        AtomicReference<Object> voiceRef = new AtomicReference<>();
        AtomicReference<Music> laserRef = new AtomicReference<>();
        AtomicReference<Music> attackRef = new AtomicReference<>();
        AtomicReference<Music> themeRef = new AtomicReference<>();
        AtomicReference<Float> themeVolume = new AtomicReference<>();

        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            try {
                onGl(scenario, () -> {
                    WorldDominationGame app =
                            (WorldDominationGame) Gdx.app.getApplicationListener();
                    OriginalAudio audio = (OriginalAudio) field(
                            WorldDominationGame.class, "audio").get(app);
                    audioRef.set(audio);
                    assertTrue("OriginalAudio sound gate must allow the laser",
                            audio.isSoundEnabled());
                    Music theme = (Music) field(OriginalAudio.class, "theme").get(audio);
                    if (theme != null) {
                        themeRef.set(theme);
                        themeVolume.set(theme.getVolume());
                        theme.setVolume(0f);
                    }
                    audio.prepareSatelliteAudio(1);
                    SatelliteAudioPlayback owner = (SatelliteAudioPlayback) field(
                            OriginalAudio.class, "satellitePlayback").get(audio);
                    ownerRef.set(owner);
                    owner.setMuted(true);
                    Object voice = onlyVoice(owner);
                    voiceRef.set(voice);
                    Music laser = (Music) field(voice.getClass(), "laser").get(voice);
                    Music attack = (Music) field(voice.getClass(), "attack").get(voice);
                    laserRef.set(laser);
                    attackRef.set(attack);
                    assertFalse("ID19 is one-shot", laser.isLooping());
                    assertFalse("ID32 is one-shot", attack.isLooping());
                    audio.play(OriginalAudio.Cue.SATELLITE_LASER);
                    assertTrue("Real ID19 Music started", laser.isPlaying());
                });

                scenario.moveToState(Lifecycle.State.STARTED);
                Music laser = laserRef.get();
                Music attack = attackRef.get();
                assertFalse("ID19 paused with Activity", laser.isPlaying());
                assertFalse("ID32 paused with Activity", attack.isPlaying());
                float pausedPosition = laser.getPosition();
                // The instrumentation thread may sleep; the libGDX GL thread must not.
                Thread.sleep(1600L);
                assertFalse("ID19 remains paused", laser.isPlaying());
                assertFalse("ID32 remains paused", attack.isPlaying());
                assertEquals("ID19 position remains stable", pausedPosition,
                        laser.getPosition(), 0.05f);
                assertFalse("Chain has not completed during pause",
                        field(voiceRef.get().getClass(), "attackComplete")
                                .getBoolean(voiceRef.get()));

                scenario.moveToState(Lifecycle.State.RESUMED);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
                boolean complete = false;
                while (System.nanoTime() < deadline && !complete) {
                    AtomicReference<Boolean> observed = new AtomicReference<>(false);
                    onGl(scenario, () -> {
                        SatelliteAudioPlayback owner = ownerRef.get();
                        Object voice = onlyVoice(owner);
                        assertSame("Resume keeps the same voice", voiceRef.get(), voice);
                        assertSame("Resume keeps the laser", laserRef.get(),
                                field(voice.getClass(), "laser").get(voice));
                        assertSame("Resume keeps the attack", attackRef.get(),
                                field(voice.getClass(), "attack").get(voice));
                        assertFalse("Voice remains attached until battle finish",
                                field(voice.getClass(), "closed").getBoolean(voice));
                        if (field(voice.getClass(), "attackComplete").getBoolean(voice)) {
                            assertTrue("ID32 started from the ID19 completion callback",
                                    field(voice.getClass(), "attackStarted")
                                            .getBoolean(voice));
                        }
                        observed.set(field(voice.getClass(), "attackComplete")
                                .getBoolean(voice));
                    });
                    complete = observed.get();
                    if (!complete) Thread.sleep(50L);
                }
                assertTrue("Real ID19 completion must start and finish ID32 after resume",
                        complete);
            } finally {
                if (scenario.getState() == Lifecycle.State.STARTED) {
                    scenario.moveToState(Lifecycle.State.RESUMED);
                }
                onGl(scenario, () -> {
                    OriginalAudio audio = audioRef.get();
                    if (audio != null) audio.finishSatelliteAudio();
                    Music theme = themeRef.get();
                    if (theme != null && themeVolume.get() != null) {
                        theme.setVolume(themeVolume.get());
                    }
                });
            }
        }
    }

    @Test public void realMusicCompletionStartsAttackOnceOnTheGlThread() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        List<String> sourceFiles = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Thread> glThread = new AtomicReference<>();
        AtomicReference<SatelliteAudioPlayback> owner = new AtomicReference<>();
        AtomicReference<Music> theme = new AtomicReference<>();
        AtomicReference<Float> themeVolume = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch setupDone = new CountDownLatch(1);
        CountDownLatch attackDone = new CountDownLatch(1);

        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            try {
                scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                    try {
                        glThread.set(Thread.currentThread());
                        WorldDominationGame app =
                                (WorldDominationGame) Gdx.app.getApplicationListener();
                        OriginalAudio audio = (OriginalAudio) field(
                                WorldDominationGame.class, "audio").get(app);
                        Music themeMusic = (Music) field(OriginalAudio.class,
                                "theme").get(audio);
                        if (themeMusic != null) {
                            theme.set(themeMusic);
                            themeVolume.set(themeMusic.getVolume());
                            themeMusic.setVolume(0f);
                        }
                        SatelliteAudioPlayback playback = new SatelliteAudioPlayback(cue -> {
                            String file;
                            if (cue == OriginalAudio.Cue.SATELLITE_LASER) file = LASER;
                            else if (cue == OriginalAudio.Cue.SATELLITE_ATTACK) file = ATTACK;
                            else throw new AssertionError("Unexpected cue " + cue);
                            FileHandle source = Gdx.files.internal(AUDIO_ROOT + file);
                            assertTrue("Packaged source audio: " + source.path(),
                                    source.exists());
                            sourceFiles.add(source.path());
                            Music delegate = Gdx.audio.newMusic(source);
                            assertNotNull("Android music: " + file, delegate);
                            assertFalse("One-shot source cue: " + file,
                                    delegate.isLooping());
                            return new RecordingMusic(delegate,
                                    LASER.equals(file) ? "laser" : "attack",
                                    events, glThread, failure, attackDone);
                        }, 0f);
                        owner.set(playback);
                        playback.prepare(1);
                        assertTrue("Laser starts", playback.play());
                    } catch (Throwable error) {
                        failure.set(error);
                        attackDone.countDown();
                    } finally {
                        setupDone.countDown();
                    }
                }));
                assertTrue("GL setup timed out", setupDone.await(15, TimeUnit.SECONDS));
                if (failure.get() != null) throw new AssertionError("GL audio setup", failure.get());
                // AndroidMusic posts completion to Gdx.app. Do not block its GL thread.
                assertTrue("Real ID19 then ID32 completion timed out",
                        attackDone.await(25, TimeUnit.SECONDS));
                if (failure.get() != null) throw new AssertionError("Music callback", failure.get());
                assertEquals(Arrays.asList("laser:play", "laser:complete",
                        "attack:play", "attack:complete"), events);
                assertEquals(Arrays.asList(AUDIO_ROOT + LASER, AUDIO_ROOT + ATTACK),
                        sourceFiles);
            } finally {
                CountDownLatch cleaned = new CountDownLatch(1);
                scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                    try {
                        SatelliteAudioPlayback playback = owner.get();
                        if (playback != null) {
                            playback.finish();
                            playback.dispose();
                        }
                        Music themeMusic = theme.get();
                        if (themeMusic != null && themeVolume.get() != null) {
                            themeMusic.setVolume(themeVolume.get());
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    } finally {
                        cleaned.countDown();
                    }
                }));
                assertTrue("GL audio cleanup timed out", cleaned.await(10, TimeUnit.SECONDS));
                if (failure.get() != null) throw new AssertionError("Audio cleanup", failure.get());
            }
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object onlyVoice(SatelliteAudioPlayback owner) throws Exception {
        List<Object> voices = (List<Object>) field(
                SatelliteAudioPlayback.class, "voices").get(owner);
        assertEquals("One source satellite creates one Music pair", 1, voices.size());
        return voices.get(0);
    }

    private static void onGl(GameActivityScenario scenario, GlCheck check) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try {
                check.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
            }
        }));
        assertTrue("GL task timed out", finished.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("GL task failed", failure.get());
    }

    private interface GlCheck {
        void run() throws Exception;
    }

    private static final class RecordingMusic implements Music {
        private final Music delegate;
        private final String name;
        private final List<String> events;
        private final AtomicReference<Thread> glThread;
        private final AtomicReference<Throwable> failure;
        private final CountDownLatch attackDone;

        RecordingMusic(Music delegate, String name, List<String> events,
                       AtomicReference<Thread> glThread,
                       AtomicReference<Throwable> failure, CountDownLatch attackDone) {
            this.delegate = delegate;
            this.name = name;
            this.events = events;
            this.glThread = glThread;
            this.failure = failure;
            this.attackDone = attackDone;
        }

        @Override public void play() {
            assertSame("Music.play GL thread", glThread.get(), Thread.currentThread());
            assertFalse("One-shot " + name, delegate.isLooping());
            events.add(name + ":play");
            delegate.play();
        }

        @Override public void setOnCompletionListener(OnCompletionListener listener) {
            delegate.setOnCompletionListener(ignored -> {
                try {
                    assertSame("Music completion GL thread", glThread.get(),
                            Thread.currentThread());
                    events.add(name + ":complete");
                    listener.onCompletion(this);
                } catch (Throwable error) {
                    // An assertion on the GL thread must reach the instrumentation thread.
                    failure.compareAndSet(null, error);
                    attackDone.countDown();
                } finally {
                    if ("attack".equals(name)) attackDone.countDown();
                }
            });
        }

        @Override public void pause() { delegate.pause(); }
        @Override public void stop() { delegate.stop(); }
        @Override public boolean isPlaying() { return delegate.isPlaying(); }
        @Override public void setLooping(boolean looping) { delegate.setLooping(looping); }
        @Override public boolean isLooping() { return delegate.isLooping(); }
        @Override public void setVolume(float volume) { delegate.setVolume(volume); }
        @Override public float getVolume() { return delegate.getVolume(); }
        @Override public void setPan(float pan, float volume) {
            delegate.setPan(pan, volume);
        }
        @Override public void setPosition(float position) { delegate.setPosition(position); }
        @Override public float getPosition() { return delegate.getPosition(); }
        @Override public void dispose() { delegate.dispose(); }
    }
}
