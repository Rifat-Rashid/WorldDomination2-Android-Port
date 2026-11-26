package com.example.worlddomination.platform.qwen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.worlddomination.game.ai.LocalOpponentRuntime.Selection;
import com.example.worlddomination.game.ai.LocalOpponentRuntime.Snapshot;
import com.example.worlddomination.game.ai.LocalOpponentRuntime.Stage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class QwenRuntimeDeviceTest {
    private static final long LOAD_TIMEOUT_MS = 90_000;
    private static final String PREFS = "opponent_ai";
    private static final String PREF_ENABLED = "qwen3_0_6b";
    private static final String SYSTEM = "Choose one WD2 plan. Return only a JSON object with plan_id.";
    private static final String USER = "Two legal plans: P03 produces two soldiers and passes. "
            + "P09 researches ground defense then produces one soldier. Enemy armies pose an immediate threat.";

    @Test
    public void stockModelCleansUpAndReturnsOnlyCurrentConstrainedSelections() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File model = new File(context.getNoBackupFilesDir(), "qwen3/" + QwenModel.NAME);
        assertTrue("Run scripts/provision-qwen-model.py for the test application ID", model.isFile());
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean originallyPresent = preferences.contains(PREF_ENABLED);
        boolean original = preferences.getBoolean(PREF_ENABLED, false);
        preferences.edit().remove(PREF_ENABLED).apply();
        try (QwenRuntime runtime = new QwenRuntime(context)) {
            assertFalse(runtime.snapshot().requested);
            assertTrue(runtime.snapshot().detail, runtime.snapshot().supported);
            assertEquals(Stage.OFF, runtime.snapshot().stage);
            runtime.setRequested(true);
            awaitStage(runtime, Stage.READY);
            assertEquals(QwenModel.SHA256, runtime.snapshot().modelSha256);
            Selection first = select(runtime, USER, Arrays.asList("P03", "P09"), 90);
            assertTrue(first.detail, Arrays.asList("P03", "P09").contains(first.planId));
            assertEquals(QwenModel.SHA256, first.modelSha256);
            Selection second = select(runtime, USER.replace("P03", "P11").replace("P09", "P19"),
                    Arrays.asList("P11", "P19"), 90);
            assertTrue(second.detail, Arrays.asList("P11", "P19").contains(second.planId));
            Log.i("WD2QwenTest", "Stock decisions in " + first.latencyMillis + " / " + second.latencyMillis + " ms");

            Selection expired = select(runtime, USER, Arrays.asList("P03", "P09"), -1);
            assertNull(expired.planId);
            assertEquals("Decision deadline exceeded", expired.detail);
            Selection oversize = select(runtime, repeatedPrompt(3_100), Arrays.asList("P03", "P09"), 30);
            assertNull(oversize.planId);
            assertTrue(oversize.detail, oversize.detail.startsWith("Decision exceeds context budget"));

            ArrayBlockingQueue<Selection> canceled = new ArrayBlockingQueue<>(2);
            runtime.selectAsync(SYSTEM, repeatedPrompt(1_650), Arrays.asList("P03", "P09"),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(90), canceled::add);
            SystemClock.sleep(125);
            runtime.setRequested(false);
            assertEquals(Stage.OFF, runtime.snapshot().stage);
            runtime.setRequested(true);
            runtime.setRequested(false);
            runtime.setRequested(true);
            Selection obsolete = canceled.poll(10, TimeUnit.SECONDS);
            assertNotNull(obsolete);
            assertNull(obsolete.planId);
            assertEquals("Decision canceled", obsolete.detail);
            awaitStage(runtime, Stage.READY);
            assertNull(canceled.poll());

            runtime.setForeground(false);
            assertEquals(Stage.PAUSED, runtime.snapshot().stage);
            assertTrue(runtime.snapshot().requested);
            runtime.setForeground(true);
            awaitStage(runtime, Stage.READY);
            assertTrue(model.isFile());
        } finally {
            restore(preferences, original, originallyPresent);
        }
    }

    @Test
    public void damagedCacheReportsRetryAndNeverReachesNativeReady() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File model = new File(context.getNoBackupFilesDir(), "qwen3/" + QwenModel.NAME);
        assertTrue("Run scripts/provision-qwen-model.py for the test application ID", model.isFile());
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean originallyPresent = preferences.contains(PREF_ENABLED);
        boolean original = preferences.getBoolean(PREF_ENABLED, false);
        preferences.edit().remove(PREF_ENABLED).apply();
        File intact = new File(model.getParentFile(), model.getName() + ".test-backup");
        assertFalse("A previous test left a backup of the model", intact.exists());
        assertTrue("Preserve the real model during the corruption probe", model.renameTo(intact));
        try (QwenRuntime runtime = new QwenRuntime(context)) {
            assertTrue(runtime.snapshot().detail, runtime.snapshot().supported);
            try (FileOutputStream output = new FileOutputStream(model)) {
                output.write(new byte[] {'G', 'G', 'U', 'F', 3, 0, 0, 0});
            }
            runtime.setRequested(true);
            awaitStage(runtime, Stage.ERROR);
            assertTrue(runtime.snapshot().requested);
            assertTrue(runtime.snapshot().detail, runtime.snapshot().detail.contains("failed verification"));
            assertFalse(model.exists());
            assertTrue(intact.renameTo(model));
            runtime.setRequested(true);
            awaitStage(runtime, Stage.READY);
        } finally {
            if (intact.exists()) {
                assertTrue(intact.renameTo(model));
            }
            restore(preferences, original, originallyPresent);
        }
    }

    @Test
    public void sharedDecisionFixturesReturnLegalPlanIds() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = new File(context.getNoBackupFilesDir(), "qwen3/decision-fixtures.json");
        File model = new File(context.getNoBackupFilesDir(), "qwen3/" + QwenModel.NAME);
        assertTrue("Provision a model and shared decision fixtures", input.isFile() && model.isFile());
        JSONArray fixtures;
        try (Scanner scanner = new Scanner(input, "UTF-8")) {
            fixtures = new JSONArray(scanner.useDelimiter("\\A").next());
        }
        assertTrue("At least one decision fixture is required", fixtures.length() > 0);
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean originallyPresent = preferences.contains(PREF_ENABLED);
        boolean original = preferences.getBoolean(PREF_ENABLED, false);
        long started = SystemClock.elapsedRealtime();
        try (QwenRuntime runtime = new QwenRuntime(context)) {
            assertTrue(runtime.snapshot().detail, runtime.snapshot().supported);
            runtime.setRequested(true);
            awaitStage(runtime, Stage.READY);
            started = SystemClock.elapsedRealtime();
            for (int index = 0; index < fixtures.length(); index++) {
                JSONObject fixture = fixtures.getJSONObject(index);
                JSONArray planIds = fixture.getJSONArray("planIds");
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < planIds.length(); i++) {
                    ids.add(planIds.getString(i));
                }
                Selection selection = select(runtime, fixture.getString("system"), fixture.getString("user"), ids, 90);
                assertTrue(selection.detail, ids.contains(selection.planId));
                Log.i("WD2QwenTest", "Shared decision " + index + " selected " + selection.planId
                        + " in " + selection.latencyMillis + " ms");
            }
            Log.i("WD2QwenTest", fixtures.length() + " shared decisions in "
                    + (SystemClock.elapsedRealtime() - started) + " ms");
        } finally {
            restore(preferences, original, originallyPresent);
        }
    }

    private static void awaitStage(QwenRuntime runtime, Stage expected) {
        long deadline = SystemClock.elapsedRealtime() + LOAD_TIMEOUT_MS;
        do {
            Snapshot current = runtime.snapshot();
            if (current.stage == expected) {
                return;
            }
            if (current.stage == Stage.ERROR) {
                fail(current.detail);
            }
            SystemClock.sleep(10);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Waiting for " + expected + " returned " + runtime.snapshot().stage);
    }

    private static Selection select(QwenRuntime runtime, String prompt, List<String> planIds, int seconds)
            throws InterruptedException {
        return select(runtime, SYSTEM, prompt, planIds, seconds);
    }

    private static Selection select(QwenRuntime runtime, String system, String prompt, List<String> planIds,
                                    int seconds) throws InterruptedException {
        ArrayBlockingQueue<Selection> result = new ArrayBlockingQueue<>(1);
        runtime.selectAsync(system, prompt, planIds,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds), result::add);
        Selection selection = result.poll(95, TimeUnit.SECONDS);
        assertNotNull(selection);
        return selection;
    }

    private static String repeatedPrompt(int repetitions) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < repetitions; i++) {
            result.append(" island");
        }
        return result.append(USER).toString();
    }

    private static void restore(SharedPreferences prefs, boolean value, boolean existed) {
        SharedPreferences.Editor edit = prefs.edit();
        if (existed) {
            edit.putBoolean(PREF_ENABLED, value);
        } else {
            edit.remove(PREF_ENABLED);
        }
        edit.apply();
    }
}
