package com.example.worlddomination.game.persistence;

/** Stores the best completed match score on the device for offline play. */
public final class PersonalBestStore {
    private static final String KEY = "personal_best_score_v1";

    private final ScorePreferenceBackend backend;

    public PersonalBestStore(ScorePreferenceBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Score preference backend cannot be null");
        }
        this.backend = backend;
    }

    public long getBestScore() {
        return Math.max(0L, backend.getLong(KEY, 0L));
    }

    /** Saves a score only when it improves the stored personal best. */
    public boolean recordFinalScore(long finalScore) {
        long candidate = Math.max(0L, finalScore);
        if (candidate <= getBestScore()) {
            return false;
        }
        backend.putLong(KEY, candidate);
        backend.flush();
        return true;
    }
}
