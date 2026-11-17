package com.example.worlddomination.game.assets;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntPredicate;

/** Per-loader cache, including missing assets; failed probes are retried. */
final class CachedAssetPredicate implements IntPredicate {
    private final ConcurrentHashMap<Integer, Boolean> results = new ConcurrentHashMap<>();
    private final Function<Integer, Boolean> probe;

    CachedAssetPredicate(IntPredicate predicate) {
        this.probe = predicate::test;
    }

    @Override
    public boolean test(int id) {
        return results.computeIfAbsent(id, probe);
    }
}
