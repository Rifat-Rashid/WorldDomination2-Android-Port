package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class FlashSceneMetadataCacheTest {
    @Test
    public void concurrentReadersWaitForOneParseAndShareTheTree() throws Exception {
        FlashSceneMetadataCache cache = new FlashSceneMetadataCache();
        JsonValue scene = new JsonReader().parse("{fps:24,symbols:[]}");
        AtomicInteger parses = new AtomicInteger();
        CountDownLatch parserEntered = new CountDownLatch(1);
        CountDownLatch releaseParser = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(6);
        try {
            List<Future<JsonValue>> results = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                results.add(workers.submit(() -> cache.get(() -> {
                    parses.incrementAndGet();
                    parserEntered.countDown();
                    try {
                        if (!releaseParser.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release parser");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    return scene;
                })));
            }
            assertTrue(parserEntered.await(5, TimeUnit.SECONDS));
            releaseParser.countDown();
            for (Future<JsonValue> result : results) {
                assertSame(scene, result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, parses.get());
        } finally {
            releaseParser.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    public void liveCachedTreeDoesNotParseAgain() {
        FlashSceneMetadataCache cache = new FlashSceneMetadataCache();
        JsonValue scene = new JsonReader().parse("{fps:24,symbols:[]}");
        assertSame(scene, cache.get(() -> scene));
        assertSame(scene, cache.get(() -> {
            throw new AssertionError("A live cached scene must not be reparsed");
        }));
    }

    @Test
    public void failedParserCanBeRetried() {
        FlashSceneMetadataCache cache = new FlashSceneMetadataCache();
        JsonValue scene = new JsonReader().parse("{fps:24,symbols:[]}");
        AtomicInteger parses = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> cache.get(() -> {
            parses.incrementAndGet();
            throw new IllegalStateException("Corrupt scene");
        }));
        assertSame(scene, cache.get(() -> {
            parses.incrementAndGet();
            return scene;
        }));
        assertEquals(2, parses.get());
    }
}
