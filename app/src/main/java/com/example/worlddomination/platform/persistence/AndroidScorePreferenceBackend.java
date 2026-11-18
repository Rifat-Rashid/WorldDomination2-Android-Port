package com.example.worlddomination.platform.persistence;

import android.content.SharedPreferences;
import com.example.worlddomination.game.persistence.ScorePreferenceBackend;
import com.example.worlddomination.game.persistence.ScorePersistenceException;
import java.util.HashMap;
import java.util.Map;

/** Commits the occasional completed-match score before acknowledging persistence. */
public final class AndroidScorePreferenceBackend implements ScorePreferenceBackend {
    private final SharedPreferences preferences;
    private final Map<String, Long> pending = new HashMap<>();
    private final Map<String, Long> previous = new HashMap<>();

    public AndroidScorePreferenceBackend(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("Preferences cannot be null");
        this.preferences = preferences;
    }

    @Override public long getLong(String key, long fallback) {
        // Android commit(false) can still update its in-memory values. Until a
        // successful retry, never present that unpersisted value as the best.
        if (previous.containsKey(key)) {
            Long value = previous.get(key);
            return value == null ? fallback : value;
        }
        return preferences.getLong(key, fallback);
    }

    @Override public void putLong(String key, long value) {
        if (!previous.containsKey(key)) {
            previous.put(key, preferences.contains(key) ? preferences.getLong(key, 0L) : null);
        }
        pending.put(key, value);
    }

    @Override public void flush() {
        if (pending.isEmpty()) return;
        // libGDX AndroidPreferences.flush uses apply(): its queued disk write can
        // be lost if the process exits immediately after the endgame handoff.
        // This tiny write occurs only for an improved completed-match score.
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Long> value : pending.entrySet()) {
            editor.putLong(value.getKey(), value.getValue());
        }
        if (!editor.commit()) throw new ScorePersistenceException("Could not persist personal best");
        pending.clear();
        previous.clear();
    }
}
