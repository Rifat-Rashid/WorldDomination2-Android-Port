package com.example.worlddomination.game.audio;

import java.util.Objects;
import java.util.function.Function;

/** Source battle-owned ambient slots; the supplied sounds remain owned by the caller. */
public final class BattleAmbientPlayback {
    private static final int SLOT_COUNT = 10;
    private static final int SOURCE_PLAYS = 9999;
    private static final float AMBIENT_GAIN = 0.21f;

    private final Function<OriginalAudio.Cue, FiniteRepeatSound> factory;
    private final FiniteRepeatSound[] sounds = new FiniteRepeatSound[SLOT_COUNT];
    private final long[] ids = new long[SLOT_COUNT];
    private int nextSlot;
    private boolean soundEnabled = true;
    private boolean outputMuted;
    private boolean paused;
    private boolean disposed;

    public BattleAmbientPlayback(Function<OriginalAudio.Cue, FiniteRepeatSound> factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    /** A fresh BattlePlayback owns a fresh index and no earlier ambient voices. */
    public void beginBattle() {
        if (disposed) return;
        stopAll();
        nextSlot = 0;
    }

    /** Returns whether a finite source voice was successfully started. */
    public boolean startForBuilding(String frame) {
        if (disposed || !soundEnabled) return false;
        OriginalAudio.Cue cue = cueForFrame(frame);
        if (cue == null) return false;
        int slot = nextSlot;
        stopSlot(slot);
        // Source slot rotation belongs to the logical building request, not to
        // native device allocation. A failed sample leaves this slot empty.
        nextSlot = (nextSlot + 1) % SLOT_COUNT;
        FiniteRepeatSound sound = factory.apply(cue);
        if (sound == null) return false;
        long id = sound.playFinite(outputMuted ? 0f : 1f, 1f, 0f, SOURCE_PLAYS);
        if (id < 0) return false;
        sounds[slot] = sound;
        ids[slot] = id;
        sound.setVolume(id, outputMuted ? 0f : AMBIENT_GAIN);
        if (paused) sound.pause(id);
        return true;
    }

    /** Source cleanup stops voices but does not reset the rotating slot index. */
    public void stopAll() {
        if (disposed) return;
        for (int i = 0; i < SLOT_COUNT; i++) stopSlot(i);
    }

    public void setSoundEnabled(boolean enabled) { soundEnabled = enabled; }

    public void setOutputMuted(boolean muted) {
        if (disposed) return;
        outputMuted = muted;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (sounds[i] != null) sounds[i].setVolume(ids[i], muted ? 0f : AMBIENT_GAIN);
        }
    }

    public void pause() {
        if (disposed || paused) return;
        paused = true;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (sounds[i] != null) sounds[i].pause(ids[i]);
        }
    }

    public void resume() {
        if (disposed || !paused) return;
        paused = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (sounds[i] != null) sounds[i].resume(ids[i]);
        }
    }

    public void dispose() {
        if (disposed) return;
        stopAll();
        disposed = true;
    }

    private void stopSlot(int slot) {
        if (sounds[slot] == null) return;
        sounds[slot].stop(ids[slot]);
        sounds[slot] = null;
    }

    static OriginalAudio.Cue cueForFrame(String frame) {
        if (frame == null) return null;
        switch (frame) {
            case "resources": return OriginalAudio.Cue.RESOURCES_AMBIENT;
            case "production": return OriginalAudio.Cue.PRODUCTION_AMBIENT;
            case "diplomacy": return OriginalAudio.Cue.DIPLOMACY_AMBIENT;
            case "defense": return OriginalAudio.Cue.DEFENSE_AMBIENT;
            case "religion": return OriginalAudio.Cue.MASS_MEDIA_AMBIENT;
            case "research": return OriginalAudio.Cue.TECHNOLOGY_AMBIENT;
            case "game": return OriginalAudio.Cue.GAME_AMBIENT;
            default: return null;
        }
    }
}
