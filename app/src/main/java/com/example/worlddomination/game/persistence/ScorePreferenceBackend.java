package com.example.worlddomination.game.persistence;

/** Small storage boundary so score persistence stays independent of Android APIs. */
public interface ScorePreferenceBackend {
    long getLong(String key, long fallback);

    void putLong(String key, long value);

    void flush();
}
