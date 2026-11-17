package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Sound;

/** A SoundPool voice with a finite native repeat count and no Java restart callback. */
public interface FiniteRepeatSound extends Sound {
    /** Starts {@code totalPlays} complete plays; one means no repetition. */
    long playFinite(float volume, float pitch, float pan, int totalPlays);
}
