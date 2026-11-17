package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Sound;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Retains instance gain/pan without stopping sounds to implement output mute. */
final class SoundVoiceRegistry {
    private final List<Voice> voices = new ArrayList<>();
    private final LongSupplier clock;
    private boolean muted;
    private boolean paused;
    private long pauseStarted;
    private long pausedNanos;

    SoundVoiceRegistry() { this(System::nanoTime); }
    SoundVoiceRegistry(LongSupplier clock) { this.clock = clock; }

    void register(Sound sound, long id, float volume, float pan, boolean loop, long lifetime) {
        if (id < 0) return;
        prune();
        forget(sound, id);
        voices.add(new Voice(sound, id, volume, pan, loop, now(), lifetime));
        if (paused) sound.pause(id);
    }

    void setMuted(boolean value) {
        prune();
        muted = value;
        for (int i = 0; i < voices.size(); i++) apply(voices.get(i));
    }

    void pan(Sound sound, long id, float pan) {
        prune();
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            if (voice.sound == sound && voice.id == id) {
                voice.pan = pan;
                apply(voice);
                return;
            }
        }
    }

    void forget(Sound sound, long id) {
        for (int i = voices.size() - 1; i >= 0; i--)
            if (voices.get(i).sound == sound && voices.get(i).id == id) voices.remove(i);
    }

    void pause() {
        if (paused) return;
        prune();
        pauseStarted = clock.getAsLong();
        paused = true;
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            voice.sound.pause(voice.id);
        }
    }

    void resume() {
        if (!paused) return;
        pausedNanos += clock.getAsLong() - pauseStarted;
        paused = false;
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            apply(voice);
            voice.sound.resume(voice.id);
        }
    }

    void clear() { voices.clear(); }

    private long now() { return (paused ? pauseStarted : clock.getAsLong()) - pausedNanos; }

    private void prune() {
        long time = now();
        // Sound has no completion callback. This only retires handles after an upper
        // bound on native playback duration; it never drives source completion or stops audio.
        for (int i = voices.size() - 1; i >= 0; i--) {
            Voice voice = voices.get(i);
            // The Android backend knows when its asynchronous load actually started
            // playback. Its lifetime clock also accounts for global/native pauses.
            boolean retired = voice.sound instanceof SoundHandleOwner
                    ? !((SoundHandleOwner) voice.sound).isVoiceRetained(voice.id)
                    : !voice.loop && time - voice.started > voice.lifetime;
            if (retired) voices.remove(i);
        }
    }

    private void apply(Voice voice) {
        voice.sound.setPan(voice.id, voice.pan, muted ? 0f : voice.volume);
    }

    private static final class Voice {
        final Sound sound;
        final long id, started, lifetime;
        final float volume;
        final boolean loop;
        float pan;
        Voice(Sound sound, long id, float volume, float pan, boolean loop,
              long started, long lifetime) {
            this.sound = sound;
            this.id = id;
            this.volume = volume;
            this.pan = pan;
            this.loop = loop;
            this.started = started;
            this.lifetime = lifetime;
        }
    }
}
