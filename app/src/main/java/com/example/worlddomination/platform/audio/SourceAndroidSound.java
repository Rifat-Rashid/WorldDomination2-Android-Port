package com.example.worlddomination.platform.audio;

import com.example.worlddomination.game.audio.FiniteRepeatSound;
import com.example.worlddomination.game.audio.SoundHandleOwner;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sound handles remain stable while SoundPool finishes loading asynchronously. */
public final class SourceAndroidSound implements FiniteRepeatSound, SoundHandleOwner {
    /** Narrow native boundary, also used by JVM tests without an Android runtime. */
    interface Driver {
        /** Positive stream, zero native failure, or -1 while the pool is paused. */
        int play(int sampleId, float left, float right, int repeats, float pitch);
        void stop(int streamId);
        void pause(int streamId);
        /** False means global pause deferred this per-stream resume. */
        boolean resume(int streamId);
        void pauseAll();
        void resumeAll();
        void setRate(int streamId, float pitch);
        void setVolume(int streamId, float left, float right);
        void setLoop(int streamId, int repeats);
        void unload(int sampleId);
        long activeNowNanos();
    }

    private static final class Voice {
        final long handle;
        float volume;
        float pitch;
        float pan;
        int repeats;
        int streamId;
        boolean paused;
        boolean pendingResume;
        long started;
        long pauseStarted;
        long pausedNanos;

        Voice(long handle, float volume, float pitch, float pan, int repeats) {
            this.handle = handle;
            this.volume = volume;
            this.pitch = pitch;
            this.pan = pan;
            this.repeats = repeats;
        }
    }

    private final Driver driver;
    private final int sampleId;
    private final long onePlayUpperBoundNanos;
    private final Map<Long, Voice> voices = new LinkedHashMap<>();
    private long nextHandle = 1L;
    private boolean loaded;
    private boolean loadFailed;
    private volatile boolean disposed;

    SourceAndroidSound(Driver driver, int sampleId, long onePlayUpperBoundNanos) {
        this.driver = driver;
        this.sampleId = sampleId;
        this.onePlayUpperBoundNanos = onePlayUpperBoundNanos;
    }

    synchronized void loaded() {
        if (disposed || loadFailed || loaded) return;
        loaded = true;
        startPending();
    }

    synchronized void loadFailed() {
        if (disposed || loaded) return;
        loadFailed = true;
        voices.clear();
    }

    synchronized void poolResumed() {
        if (!loaded || disposed) return;
        for (Voice voice : voices.values()) {
            if (voice.pendingResume && driver.resume(voice.streamId)) {
                voice.pausedNanos += driver.activeNowNanos() - voice.pauseStarted;
                voice.pendingResume = false;
            }
        }
        startPending();
    }

    private void startPending() {
        voices.values().removeIf(voice -> !voice.paused && voice.streamId == 0
                && start(voice) == 0);
    }

    @Override public synchronized long play() { return play(1f); }
    @Override public synchronized long play(float volume) { return play(volume, 1f, 0f); }
    @Override public synchronized long play(float volume, float pitch, float pan) {
        return enqueue(volume, pitch, pan, 0);
    }
    @Override public synchronized long loop() { return loop(1f); }
    @Override public synchronized long loop(float volume) { return loop(volume, 1f, 0f); }
    @Override public synchronized long loop(float volume, float pitch, float pan) {
        return enqueue(volume, pitch, pan, -1);
    }
    @Override public synchronized long playFinite(float volume, float pitch, float pan, int totalPlays) {
        if (totalPlays < 1) throw new IllegalArgumentException("totalPlays must be positive");
        return enqueue(volume, pitch, pan, totalPlays - 1);
    }

    private long enqueue(float volume, float pitch, float pan, int repeats) {
        if (disposed || loadFailed) return -1L;
        if (nextHandle == Long.MAX_VALUE) throw new IllegalStateException("Sound handle exhausted");
        pruneFinished();
        Voice voice = new Voice(nextHandle++, volume, pitch, pan, repeats);
        voices.put(voice.handle, voice);
        if (loaded && start(voice) == 0) {
            voices.remove(voice.handle);
            return -1L;
        }
        return voice.handle;
    }

    private int start(Voice voice) {
        float left = voice.pan > 0f ? voice.volume * (1f - Math.abs(voice.pan)) : voice.volume;
        float right = voice.pan < 0f ? voice.volume * (1f - Math.abs(voice.pan)) : voice.volume;
        int streamId = driver.play(sampleId, left, right, voice.repeats, voice.pitch);
        if (streamId <= 0) return streamId;
        voice.streamId = streamId;
        voice.started = driver.activeNowNanos();
        return streamId;
    }

    @Override public synchronized boolean isVoiceRetained(long handle) {
        pruneFinished();
        return voices.containsKey(handle);
    }

    private void pruneFinished() {
        long now = driver.activeNowNanos();
        voices.values().removeIf(voice -> {
            if (voice.streamId == 0 || voice.repeats < 0) return false;
            long plays = (long) voice.repeats + 1L;
            long upper = saturatingMultiply(onePlayUpperBoundNanos, plays);
            long current = voice.paused || voice.pendingResume ? voice.pauseStarted : now;
            return current >= voice.started
                    && current - voice.started >= voice.pausedNanos
                    && current - voice.started - voice.pausedNanos > upper;
        });
    }

    private static long saturatingMultiply(long first, long second) {
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    @Override public synchronized void stop() {
        for (Voice voice : voices.values()) if (voice.streamId != 0) driver.stop(voice.streamId);
        voices.clear();
    }
    @Override public synchronized void stop(long handle) {
        Voice voice = voices.remove(handle);
        if (voice != null && voice.streamId != 0) driver.stop(voice.streamId);
    }
    @Override public void pause() { if (!disposed) driver.pauseAll(); }
    @Override public synchronized void pause(long handle) {
        Voice voice = voices.get(handle);
        if (voice == null) return;
        if (voice.paused) return;
        voice.paused = true;
        if (voice.pendingResume) {
            voice.pendingResume = false;
            return;
        }
        if (voice.streamId != 0) {
            voice.pauseStarted = driver.activeNowNanos();
            driver.pause(voice.streamId);
        }
    }
    @Override public void resume() { if (!disposed) driver.resumeAll(); }
    @Override public synchronized void resume(long handle) {
        Voice voice = voices.get(handle);
        if (voice == null) return;
        if (!voice.paused) return;
        voice.paused = false;
        if (voice.streamId != 0) {
            if (driver.resume(voice.streamId)) {
                voice.pausedNanos += driver.activeNowNanos() - voice.pauseStarted;
            } else voice.pendingResume = true;
        } else if (loaded && start(voice) == 0) voices.remove(handle);
    }
    @Override public synchronized void setPitch(long handle, float pitch) {
        Voice voice = voices.get(handle);
        if (voice == null) return;
        voice.pitch = pitch;
        if (voice.streamId != 0) driver.setRate(voice.streamId, pitch);
    }
    @Override public synchronized void setVolume(long handle, float volume) {
        Voice voice = voices.get(handle);
        if (voice == null) return;
        voice.volume = volume;
        if (voice.streamId != 0) updateVolume(voice);
    }
    @Override public synchronized void setPan(long handle, float pan, float volume) {
        Voice voice = voices.get(handle);
        if (voice == null) return;
        voice.pan = pan;
        voice.volume = volume;
        if (voice.streamId != 0) updateVolume(voice);
    }
    private void updateVolume(Voice voice) {
        float left = voice.pan > 0f ? voice.volume * (1f - Math.abs(voice.pan)) : voice.volume;
        float right = voice.pan < 0f ? voice.volume * (1f - Math.abs(voice.pan)) : voice.volume;
        driver.setVolume(voice.streamId, left, right);
    }
    @Override public synchronized void setLooping(long handle, boolean looping) {
        Voice voice = voices.get(handle);
        if (voice == null) return;
        voice.repeats = looping ? -1 : 0;
        if (voice.streamId != 0) {
            driver.setLoop(voice.streamId, voice.repeats);
            // Disabling a loop still allows its current pass to finish.
            if (!looping) {
                voice.started = driver.activeNowNanos();
                voice.pausedNanos = 0L;
                if (voice.paused || voice.pendingResume) voice.pauseStarted = voice.started;
            }
        }
    }
    @Override public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        stop();
        driver.unload(sampleId);
    }
}
