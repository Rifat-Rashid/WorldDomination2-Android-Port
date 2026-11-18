package com.example.worlddomination.platform.persistence;

import android.content.SharedPreferences;
import com.example.worlddomination.game.persistence.ScorePersistenceException;
import com.example.worlddomination.game.persistence.PersonalBestStore;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AndroidScorePreferenceBackendTest {
    @Test public void writesOnceSynchronouslyAndKeepsLongPrecision() {
        FakePreferences fake = new FakePreferences();
        AndroidScorePreferenceBackend backend = new AndroidScorePreferenceBackend(fake.preferences);
        backend.flush();
        assertEquals(0, fake.commits);
        backend.putLong("best", 5_000_000_000L);
        assertEquals(0, backend.getLong("best", 0));
        backend.flush();
        assertEquals(5_000_000_000L, backend.getLong("best", 0));
        backend.flush();
        assertEquals(1, fake.commits);
    }

    @Test public void failedDiskCommitIsReportedAndPendingValueCanBeRetried() {
        FakePreferences fake = new FakePreferences();
        AndroidScorePreferenceBackend backend = new AndroidScorePreferenceBackend(fake.preferences);
        backend.putLong("best", 3_000_000_000L);
        fake.succeeds = false;
        assertThrows(ScorePersistenceException.class, backend::flush);
        assertEquals("Android memory changed even though disk commit failed",
                Long.valueOf(3_000_000_000L), fake.memory.get("best"));
        assertEquals("Unpersisted candidate is not reported as a saved best", 0L,
                backend.getLong("best", 0L));
        assertEquals(1, fake.commits);
        fake.succeeds = true;
        backend.flush();
        assertEquals(2, fake.commits);
        assertEquals(3_000_000_000L, backend.getLong("best", 0));
    }

    @Test public void rejectsMissingPreferences() {
        assertThrows(IllegalArgumentException.class, () -> new AndroidScorePreferenceBackend(null));
    }

    @Test public void laterMatchCanImproveTheLastSavedBestAfterDiskFailure() {
        FakePreferences fake = new FakePreferences();
        PersonalBestStore store = new PersonalBestStore(new AndroidScorePreferenceBackend(fake.preferences));
        assertTrue(store.recordFinalScore(900));
        fake.succeeds = false;
        assertThrows(ScorePersistenceException.class, () -> store.recordFinalScore(3000));
        assertEquals(900, store.getBestScore());
        fake.succeeds = true;
        assertTrue(store.recordFinalScore(1000));
        assertEquals(1000, store.getBestScore());
    }

    @Test public void recreatedBackendKeepsConfirmedBestAfterFailedCommit() {
        FakePreferences fake = new FakePreferences();
        PersonalBestStore first = new PersonalBestStore(new AndroidScorePreferenceBackend(fake.preferences));
        assertTrue(first.recordFinalScore(900));
        fake.succeeds = false;
        assertThrows(ScorePersistenceException.class, () -> first.recordFinalScore(3000));
        assertEquals(Long.valueOf(3000), fake.memory.get("personal_best_score_v1"));
        assertEquals(Long.valueOf(900), fake.disk.get("personal_best_score_v1"));

        // Android reuses the same cached preferences when recreating the Activity.
        PersonalBestStore recreated = new PersonalBestStore(
                new AndroidScorePreferenceBackend(fake.preferences));
        assertEquals(900, recreated.getBestScore());
        fake.succeeds = true;
        assertTrue(recreated.recordFinalScore(1000));
        assertEquals(Long.valueOf(1000), fake.disk.get("personal_best_score_v1"));
        assertEquals(1000, recreated.getBestScore());
        assertEquals("The previous backend observes the confirmed retry", 1000, first.getBestScore());
        assertEquals(3, fake.commits);
    }

    @Test public void recreatedBackendCanRetryPendingWriteWithoutAffectingAnotherStore() {
        FakePreferences failed = new FakePreferences();
        AndroidScorePreferenceBackend original = new AndroidScorePreferenceBackend(failed.preferences);
        original.putLong("best", 3000);
        failed.succeeds = false;
        assertThrows(ScorePersistenceException.class, original::flush);

        FakePreferences separate = new FakePreferences();
        AndroidScorePreferenceBackend other = new AndroidScorePreferenceBackend(separate.preferences);
        assertEquals(0, other.getLong("best", 0));
        other.putLong("best", 800);
        other.flush();
        assertEquals(0, original.getLong("best", 0));

        failed.succeeds = true;
        new AndroidScorePreferenceBackend(failed.preferences).flush();
        assertEquals(Long.valueOf(3000), failed.disk.get("best"));
        assertEquals(3000, original.getLong("best", 0));
        assertEquals(800, other.getLong("best", 0));
        assertEquals(Long.valueOf(800), separate.disk.get("best"));
    }

    /** Fail on unexpected methods, especially asynchronous apply(). */
    private static final class FakePreferences {
        final Map<String, Long> memory = new HashMap<>();
        final Map<String, Long> disk = new HashMap<>();
        final Map<String, Long> pending = new HashMap<>();
        boolean succeeds = true;
        int commits;
        final SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "putLong": pending.put((String) args[0], (Long) args[1]); return proxy;
                        case "commit":
                            commits++;
                            memory.putAll(pending);
                            if (succeeds) disk.putAll(pending);
                            pending.clear();
                            return succeeds;
                        default: throw new AssertionError("Unexpected editor call: " + method);
                    }
                });
        final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "edit": return editor;
                        case "getLong": return memory.getOrDefault((String) args[0], (Long) args[1]);
                        case "contains": return memory.containsKey(args[0]);
                        default: throw new AssertionError("Unexpected preference call: " + method);
                    }
                });
    }
}
