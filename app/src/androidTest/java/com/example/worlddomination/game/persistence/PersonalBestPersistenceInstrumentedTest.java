package com.example.worlddomination.game.persistence;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.worlddomination.platform.persistence.AndroidScorePreferenceBackend;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real disk backend; the host probe runs each phase in a fresh Android process. */
@RunWith(AndroidJUnit4.class)
public final class PersonalBestPersistenceInstrumentedTest {
    private static final long FIRST = 3_000_000_000L;
    private static final long HIGHER = 5_000_000_000L;

    @Test public void realAndroidPreferencesRetainOnlyImprovedScores() throws Exception {
        String phase = InstrumentationRegistry.getArguments()
                .getString("wd2ScorePhase", "roundtrip");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Never touch world_domination_2_scores or the installed public QA package.
        String name = "wd2_test_score_" + (phase.equals("roundtrip") ? "roundtrip" : "restart");
        PersonalBestStore store = new PersonalBestStore(new AndroidScorePreferenceBackend(
                context.getSharedPreferences(name, Context.MODE_PRIVATE)));
        if (phase.equals("seed") || phase.equals("roundtrip")) {
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit());
        }
        long before = store.getBestScore();
        switch (phase) {
            case "seed":
                assertEquals(0L, before);
                assertTrue(store.recordFinalScore(FIRST));
                assertEquals(FIRST, store.getBestScore());
                break;
            case "lower":
                assertEquals(FIRST, before);
                assertFalse(store.recordFinalScore(FIRST - 1));
                assertFalse(store.recordFinalScore(FIRST));
                assertFalse(store.recordFinalScore(-1));
                assertEquals(FIRST, store.getBestScore());
                break;
            case "higher":
                assertEquals(FIRST, before);
                assertTrue(store.recordFinalScore(HIGHER));
                assertEquals(HIGHER, store.getBestScore());
                break;
            case "verify":
                assertEquals(HIGHER, before);
                assertFalse(store.recordFinalScore(FIRST));
                assertEquals(HIGHER, store.getBestScore());
                break;
            case "roundtrip":
                assertEquals(0L, before);
                assertTrue(store.recordFinalScore(FIRST));
                assertFalse(store.recordFinalScore(-1));
                assertFalse(store.recordFinalScore(FIRST));
                assertFalse(store.recordFinalScore(FIRST - 1));
                assertTrue(store.recordFinalScore(HIGHER));
                PersonalBestStore reopened = new PersonalBestStore(new AndroidScorePreferenceBackend(
                        context.getSharedPreferences(name, Context.MODE_PRIVATE)));
                assertEquals(HIGHER, reopened.getBestScore());
                break;
            default:
                fail("Unknown persistence phase: " + phase);
        }
        JSONObject receipt = new JSONObject();
        receipt.put("phase", phase);
        receipt.put("pid", Process.myPid());
        receipt.put("processToken", Process.myPid() + ":" + Process.getStartElapsedRealtime());
        receipt.put("before", before);
        receipt.put("after", store.getBestScore());
        Bundle status = new Bundle();
        status.putString("wd2_score_receipt", receipt.toString());
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        if (phase.equals("verify") || phase.equals("roundtrip")) {
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit());
        }
    }
}
