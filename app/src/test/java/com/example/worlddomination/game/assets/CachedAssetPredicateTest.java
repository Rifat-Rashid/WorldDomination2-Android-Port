package com.example.worlddomination.game.assets;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public final class CachedAssetPredicateTest {
    @Test public void cachesBothPresentAndMissingAssets() {
        AtomicInteger calls = new AtomicInteger();
        CachedAssetPredicate cache = new CachedAssetPredicate(id -> {
            calls.incrementAndGet();
            return id == 7;
        });
        assertTrue(cache.test(7));
        assertTrue(cache.test(7));
        assertFalse(cache.test(8));
        assertFalse(cache.test(8));
        assertEquals(2, calls.get());
    }

    @Test public void loaderInstancesKeepIndependentResults() {
        CachedAssetPredicate first = new CachedAssetPredicate(id -> true);
        CachedAssetPredicate second = new CachedAssetPredicate(id -> false);
        assertTrue(first.test(42));
        assertFalse(second.test(42));
    }

    @Test public void exceptionsDoNotCacheFailedProbes() {
        AtomicInteger calls = new AtomicInteger();
        CachedAssetPredicate cache = new CachedAssetPredicate(id -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("probe");
            return true;
        });
        try {
            cache.test(1);
            fail("Expected probe failure");
        } catch (IllegalStateException expected) {
            assertEquals("probe", expected.getMessage());
        }
        assertTrue(cache.test(1));
        assertTrue(cache.test(1));
        assertEquals(2, calls.get());
    }

    @Test public void concurrentRequestsForSameIdProbeOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CachedAssetPredicate cache = new CachedAssetPredicate(id -> {
            calls.incrementAndGet();
            return false;
        });
        int count = 12;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return cache.test(9);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Boolean> result : results) assertFalse(result.get(10, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
