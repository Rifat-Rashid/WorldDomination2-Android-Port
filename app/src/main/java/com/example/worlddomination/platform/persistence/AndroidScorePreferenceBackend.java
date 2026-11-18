package com.example.worlddomination.platform.persistence;

import android.content.SharedPreferences;
import com.example.worlddomination.game.persistence.ScorePreferenceBackend;
import com.example.worlddomination.game.persistence.ScorePersistenceException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Commits the occasional completed-match score before acknowledging persistence. */
public final class AndroidScorePreferenceBackend implements ScorePreferenceBackend {
    // Android shares a preferences instance across Activities. A failed commit
    // changes that instance's memory, so its confirmed/pending state must also
    // survive backend recreation. Values do not retain the weak preference key.
    private static final Map<SharedPreferences, PendingState> STATES = new WeakHashMap<>();
    private final SharedPreferences preferences;
    private final PendingState state;

    private static final class PendingState {
        final Map<String, Long> pending = new HashMap<>();
        final Map<String, Long> previous = new HashMap<>();
    }

    public AndroidScorePreferenceBackend(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("Preferences cannot be null");
        this.preferences = preferences;
        synchronized (STATES) {
            state = STATES.computeIfAbsent(preferences, ignored -> new PendingState());
        }
    }

    @Override public long getLong(String key, long fallback) {
        // Android commit(false) can still update its in-memory values. Until a
        // successful retry, never present that unpersisted value as the best.
        synchronized (state) {
            if (state.previous.containsKey(key)) {
                Long value = state.previous.get(key);
                return value == null ? fallback : value;
            }
            return preferences.getLong(key, fallback);
        }
    }

    @Override public void putLong(String key, long value) {
        synchronized (state) {
            if (!state.previous.containsKey(key)) {
                state.previous.put(key, preferences.contains(key) ? preferences.getLong(key, 0L) : null);
            }
            state.pending.put(key, value);
        }
    }

    @Override public void flush() {
        synchronized (state) {
            if (state.pending.isEmpty()) return;
            // libGDX AndroidPreferences.flush uses apply(): its queued disk write can
            // be lost if the process exits immediately after the endgame handoff.
            // This tiny write occurs only for an improved completed-match score.
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, Long> value : state.pending.entrySet()) {
                editor.putLong(value.getKey(), value.getValue());
            }
            if (!editor.commit()) throw new ScorePersistenceException("Could not persist personal best");
            state.pending.clear();
            state.previous.clear();
        }
    }
}
