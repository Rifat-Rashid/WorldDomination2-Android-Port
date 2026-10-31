package com.example.worlddomination.game.persistence;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PersonalBestStoreTest {
    @Test
    public void recordsFinalScoreAndRestoresItFromTheSameBackend() {
        MemoryBackend backend = new MemoryBackend();
        PersonalBestStore firstSession = new PersonalBestStore(backend);

        assertEquals(0L, firstSession.getBestScore());
        assertTrue(firstSession.recordFinalScore(1_500_500L));
        assertEquals(1_500_500L, firstSession.getBestScore());

        PersonalBestStore nextSession = new PersonalBestStore(backend);
        assertEquals(1_500_500L, nextSession.getBestScore());
    }

    @Test
    public void lowerEqualAndNegativeScoresDoNotReplaceTheBestOrFlush() {
        MemoryBackend backend = new MemoryBackend();
        PersonalBestStore store = new PersonalBestStore(backend);

        assertTrue(store.recordFinalScore(900L));
        assertFalse(store.recordFinalScore(899L));
        assertFalse(store.recordFinalScore(900L));
        assertFalse(store.recordFinalScore(-1L));

        assertEquals(900L, store.getBestScore());
        assertEquals(1, backend.flushCount);
    }

    @Test
    public void storesLargeFinalScoresWithoutNarrowingToAnInt() {
        MemoryBackend backend = new MemoryBackend();
        PersonalBestStore store = new PersonalBestStore(backend);
        long score = 5_000_000_000L;

        assertTrue(store.recordFinalScore(score));

        assertEquals(score, store.getBestScore());
    }

    @Test
    public void ignoresNegativeLegacyValuesWhenReading() {
        MemoryBackend backend = new MemoryBackend();
        backend.values.put("personal_best_score_v1", -5L);

        assertEquals(0L, new PersonalBestStore(backend).getBestScore());
    }

    private static final class MemoryBackend implements ScorePreferenceBackend {
        final Map<String, Long> values = new HashMap<>();
        int flushCount;

        @Override
        public long getLong(String key, long fallback) {
            Long value = values.get(key);
            return value == null ? fallback : value;
        }

        @Override
        public void putLong(String key, long value) {
            values.put(key, value);
        }

        @Override
        public void flush() {
            flushCount++;
        }
    }
}
