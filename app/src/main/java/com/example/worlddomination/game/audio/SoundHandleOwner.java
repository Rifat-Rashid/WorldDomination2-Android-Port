package com.example.worlddomination.game.audio;

/** Optional backend ownership query for asynchronously loaded Sound instances. */
public interface SoundHandleOwner {
    /** Includes pending/paused playback; false only after stop, failure or safe expiry. */
    boolean isVoiceRetained(long handle);
}
