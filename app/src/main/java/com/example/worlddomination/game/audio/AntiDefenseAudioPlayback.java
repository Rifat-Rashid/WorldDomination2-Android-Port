package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/** Completion-driven source Anti_sound owners, independent of turret frame timing. */
public final class AntiDefenseAudioPlayback {
    public enum Type { AIR, ROCKET }

    private static final int AIR_GUN_PLAYS = 8;
    private static final float SOURCE_VOLUME = 1f;
    private static final float SOURCE_PAN = 0f;

    /** Factory receives source cue and the finite Sound.start repeat count. */
    private final BiFunction<OriginalAudio.Cue, Integer, Music> factory;
    private final Consumer<Runnable> dispatcher;
    private final float masterGain;
    private final List<Owner> owners = new ArrayList<>();
    private boolean muted;
    private boolean paused;
    private boolean disposed;
    private boolean resumeQueued;
    private long resumeEpoch;

    /** The dispatcher must serialize callbacks and resume onto the game audio thread. */
    public AntiDefenseAudioPlayback(BiFunction<OriginalAudio.Cue, Integer, Music> factory,
                                    Consumer<Runnable> dispatcher) {
        this(factory, 1f, dispatcher);
    }

    /** Master gain changes only the native mix; source Anti_sound remains at 100%. */
    public AntiDefenseAudioPlayback(BiFunction<OriginalAudio.Cue, Integer, Music> factory,
                                    float masterGain, Consumer<Runnable> dispatcher) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        if (!Float.isFinite(masterGain) || masterGain < 0f || masterGain > 1f)
            throw new IllegalArgumentException("masterGain must be in [0, 1]");
        this.masterGain = masterGain;
    }

    public synchronized Owner createOwner(Type type) {
        if (disposed) throw new IllegalStateException("Anti-defense audio disposed");
        Owner owner = new Owner(Objects.requireNonNull(type, "type"));
        owners.add(owner);
        return owner;
    }

    /** Existing source Sounds continue their completion chains at zero volume. */
    public synchronized void setMuted(boolean muted) {
        if (disposed) return;
        this.muted = muted;
        for (Owner owner : owners) owner.applyVolume();
    }

    public synchronized void pause() {
        if (disposed) return;
        resumeQueued = false;
        resumeEpoch++;
        if (paused) return;
        paused = true;
        for (Owner owner : owners) owner.pauseVoices();
    }

    public synchronized void resume() {
        if (disposed || !paused || resumeQueued) return;
        long epoch = ++resumeEpoch;
        resumeQueued = true;
        try {
            dispatcher.accept(() -> completeResume(epoch));
        } catch (RuntimeException failure) {
            resumeQueued = false;
            resumeEpoch++;
            throw failure;
        }
    }

    private synchronized void completeResume(long epoch) {
        if (disposed || !paused || !resumeQueued || epoch != resumeEpoch) return;
        resumeQueued = false;
        paused = false;
        for (Owner owner : owners) owner.resumeVoices();
    }

    /** Source battle handoff: close old owners before stop/dispose can post callbacks. */
    public synchronized void finish() {
        resumeQueued = false;
        resumeEpoch++;
        for (Owner owner : owners) owner.closed = true;
        for (Owner owner : owners) owner.releaseVoices();
        owners.clear();
    }

    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        finish();
    }

    private Voice prepareVoice(Owner owner, boolean shot, OriginalAudio.Cue cue, int repeats) {
        Music music = null;
        try {
            music = factory.apply(cue, repeats);
            if (music == null) return null;
            Voice voice = new Voice(music);
            music.setLooping(false);
            music.setPan(SOURCE_PAN, muted ? 0f : SOURCE_VOLUME * masterGain);
            music.setOnCompletionListener(completed ->
                    dispatcher.accept(() -> completeVoice(owner, shot, voice)));
            return voice;
        } catch (RuntimeException failure) {
            if (music != null) music.dispose();
            return null;
        }
    }

    private synchronized void completeVoice(Owner owner, boolean shot, Voice voice) {
        if (disposed || owner.closed || (shot ? owner.shotVoice : owner.rotationVoice) != voice)
            return;
        if (shot) {
            owner.shotVoice = null;
            voice.music.dispose();
            owner.shotBusy = false;
        } else {
            owner.rotationVoice = null;
            voice.music.dispose();
        }
    }

    private static final class Voice {
        final Music music;
        Voice(Music music) { this.music = music; }
    }

    /** One source Unit's distinct rotation and firing Anti_sound objects. */
    public final class Owner {
        private final Type type;
        private Voice rotationVoice;
        private Voice shotVoice;
        private boolean shotBusy;
        private boolean closed;

        private Owner(Type type) { this.type = type; }

        /** Unit.sound_anti: muted requests do not create a rotation owner. */
        public boolean requestRotation() {
            synchronized (AntiDefenseAudioPlayback.this) {
                if (disposed || closed || muted || paused || rotationVoice != null) return false;
                Voice voice = prepareVoice(this, false, OriginalAudio.Cue.ANTI_AIR_ROTATE, 1);
                if (voice == null) return false;
                rotationVoice = voice;
                try {
                    voice.music.play();
                } catch (RuntimeException failure) {
                    rotationVoice = null;
                    voice.music.dispose();
                    return false;
                }
                return true;
            }
        }

        /** Unit.sound_anti_shoot: one Music completes the entire finite source group. */
        public boolean requestShot() {
            synchronized (AntiDefenseAudioPlayback.this) {
                if (disposed || closed || paused || shotBusy) return false;
                shotBusy = true;
                if (muted) return false; // Source installs no completion callback.
                OriginalAudio.Cue cue = type == Type.AIR ? OriginalAudio.Cue.ANTI_AIR_GUN
                        : OriginalAudio.Cue.ANTI_ROCKET_FIRE;
                Voice voice = prepareVoice(this, true, cue,
                        type == Type.AIR ? AIR_GUN_PLAYS : 1);
                if (voice == null) return false; // Source latch remains without completion.
                shotVoice = voice;
                try {
                    voice.music.play();
                } catch (RuntimeException failure) {
                    shotVoice = null;
                    voice.music.dispose();
                    return false;
                }
                return true;
            }
        }

        private void applyVolume() {
            float audible = muted ? 0f : SOURCE_VOLUME * masterGain;
            if (rotationVoice != null) rotationVoice.music.setPan(SOURCE_PAN, audible);
            if (shotVoice != null) shotVoice.music.setPan(SOURCE_PAN, audible);
        }

        private void pauseVoices() {
            if (closed) return;
            if (rotationVoice != null) rotationVoice.music.pause();
            if (shotVoice != null) shotVoice.music.pause();
        }

        private void resumeVoices() {
            if (closed) return;
            if (rotationVoice != null) rotationVoice.music.play();
            if (shotVoice != null) shotVoice.music.play();
        }

        /** Explicit disposal only; source Unit death does not close these owners. */
        public void close() {
            synchronized (AntiDefenseAudioPlayback.this) {
                if (closed) return;
                closed = true;
                releaseVoices();
                owners.remove(this);
            }
        }

        private void releaseVoices() {
            if (rotationVoice != null) {
                rotationVoice.music.stop();
                rotationVoice.music.dispose();
                rotationVoice = null;
            }
            if (shotVoice != null) {
                shotVoice.music.stop();
                shotVoice.music.dispose();
                shotVoice = null;
            }
        }
    }
}
