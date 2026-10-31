package com.example.worlddomination.game.persistence;

import com.badlogic.gdx.Preferences;

/** Adapts libGDX's Android-backed preferences to the gameplay persistence boundary. */
public final class LibGdxScorePreferenceBackend implements ScorePreferenceBackend {
    private final Preferences preferences;

    public LibGdxScorePreferenceBackend(Preferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("Preferences cannot be null");
        }
        this.preferences = preferences;
    }

    @Override
    public long getLong(String key, long fallback) {
        return preferences.getLong(key, fallback);
    }

    @Override
    public void putLong(String key, long value) {
        preferences.putLong(key, value);
    }

    @Override
    public void flush() {
        preferences.flush();
    }
}
