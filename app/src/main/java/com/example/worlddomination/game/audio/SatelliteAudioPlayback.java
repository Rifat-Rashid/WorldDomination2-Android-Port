package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Owns one independent laser-to-attack music chain per satellite reveal. */
public final class SatelliteAudioPlayback {
    private final Function<OriginalAudio.Cue, Music> factory;
    private final List<Voice> voices = new ArrayList<>();
    private final float volume;
    private boolean muted;
    private boolean paused;
    private boolean prepared;
    private boolean disposed;
    private int nextVoice;

    public SatelliteAudioPlayback(Function<OriginalAudio.Cue, Music> factory, float volume) {
        this.factory = Objects.requireNonNull(factory, "factory");
        if (!Float.isFinite(volume) || volume < 0f || volume > 1f)
            throw new IllegalArgumentException("volume must be in [0, 1]");
        this.volume = volume;
    }

    /** Warms distinct Music pairs without starting either cue. */
    public synchronized void prepare(int count) {
        if (disposed) throw new IllegalStateException("Satellite audio has been disposed");
        if (count < 0) throw new IllegalArgumentException("Negative voice count");
        boolean wasPaused = paused;
        finish();
        paused = wasPaused;
        prepared = true;
        for (int i = 0; i < count; i++) {
            Voice voice = createVoice();
            if (voice != null) voices.add(voice);
        }
    }

    /** Starts a previously unused laser; its completion starts its own attack cue. */
    public synchronized boolean play() {
        if (!prepared || disposed || paused) return false;
        Voice voice;
        if (nextVoice < voices.size()) {
            voice = voices.get(nextVoice++);
        } else {
            voice = createVoice();
            if (voice == null) return false;
            voices.add(voice);
            nextVoice++;
        }
        voice.used = true;
        voice.laser.play();
        return true;
    }

    /** Keeps in-flight chains alive while changing audible volume. */
    public synchronized void setMuted(boolean muted) {
        this.muted = muted;
        for (Voice voice : voices) voice.setVolume(muted ? 0f : volume);
    }

    public synchronized void pause() {
        if (disposed || paused) return;
        paused = true;
        for (Voice voice : voices) {
            if (!voice.used || voice.closed) continue;
            if (!voice.laserComplete) voice.laser.pause();
            if (voice.attackStarted && !voice.attackComplete) voice.attack.pause();
        }
    }

    public synchronized void resume() {
        if (disposed || !paused) return;
        paused = false;
        for (Voice voice : voices) {
            if (!voice.used || voice.closed) continue;
            if (voice.laserComplete) {
                if (!voice.attackStarted) voice.startAttack();
                else if (!voice.attackComplete) voice.attack.play();
            } else {
                voice.laser.play();
            }
        }
    }

    /** Closes callbacks before stopping and disposing the current battle's voices. */
    public synchronized void finish() {
        prepared = false;
        paused = false;
        nextVoice = 0;
        for (Voice voice : voices) voice.closed = true;
        for (Voice voice : voices) {
            voice.laser.stop();
            voice.attack.stop();
            voice.laser.dispose();
            voice.attack.dispose();
        }
        voices.clear();
    }

    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        finish();
    }

    private Voice createVoice() {
        Music laser = null;
        Music attack = null;
        try {
            laser = factory.apply(OriginalAudio.Cue.SATELLITE_LASER);
            attack = factory.apply(OriginalAudio.Cue.SATELLITE_ATTACK);
            if (laser == null || attack == null) {
                if (laser != null) laser.dispose();
                if (attack != null) attack.dispose();
                return null;
            }
            Voice voice = new Voice(laser, attack);
            laser.setLooping(false);
            attack.setLooping(false);
            voice.setVolume(muted ? 0f : volume);
            laser.setOnCompletionListener(music -> voice.laserFinished());
            attack.setOnCompletionListener(music -> voice.attackFinished());
            return voice;
        } catch (RuntimeException failure) {
            if (laser != null) laser.dispose();
            if (attack != null) attack.dispose();
            return null;
        }
    }

    private final class Voice {
        private final Music laser;
        private final Music attack;
        private boolean used;
        private boolean closed;
        private boolean laserComplete;
        private boolean attackStarted;
        private boolean attackComplete;

        Voice(Music laser, Music attack) {
            this.laser = laser;
            this.attack = attack;
        }

        void setVolume(float value) {
            laser.setVolume(value);
            attack.setVolume(value);
        }

        void laserFinished() {
            synchronized (SatelliteAudioPlayback.this) {
                if (closed || !used || laserComplete) return;
                laserComplete = true;
                if (!paused) startAttack();
            }
        }

        void startAttack() {
            if (closed || !used || attackStarted) return;
            attackStarted = true;
            attack.play();
        }

        void attackFinished() {
            synchronized (SatelliteAudioPlayback.this) {
                if (closed || !attackStarted || attackComplete) return;
                attackComplete = true;
            }
        }
    }
}
