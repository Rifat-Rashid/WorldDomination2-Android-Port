package com.example.worlddomination.platform.qwen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.example.worlddomination.game.ai.LocalOpponentRuntime;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class QwenRuntime implements LocalOpponentRuntime {
    private static final String TAG = "WD2Qwen";
    private static final String PREFS = "opponent_ai";
    private static final String PREF_ENABLED = "qwen3_0_6b";
    private static final int CONTEXT_TOKENS = 2_048;
    private static final Pattern PLAN_ID = Pattern.compile("P[0-9]{2}");
    private static final String SELECTION_PREFIX = "ok:{\"plan_id\":\"";
    private static final String SELECTION_SUFFIX = "\"}";

    // Activity recreation may leave an old worker unwinding its native operation.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "WD2-Qwen");
        thread.setDaemon(true);
        return thread;
    });

    private final Context application;
    private final SharedPreferences preferences;
    private final String unsupportedReason;
    private final Set<Work> decisions = new HashSet<>();
    private boolean foreground = true;
    private boolean closed;
    private Work loading;
    private volatile Snapshot status;
    // Native ownership belongs exclusively to WORKER, including release.
    private long nativeSession;

    public QwenRuntime(Context context) {
        application = context.getApplicationContext();
        preferences = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        unsupportedReason = deviceLimitation();
        boolean requested = preferences.getBoolean(PREF_ENABLED, false);
        if (unsupportedReason != null) {
            status = new Snapshot(false, requested, Stage.UNSUPPORTED, -1, unsupportedReason, "");
        } else {
            status = new Snapshot(true, requested, Stage.OFF, -1, "", "");
            if (requested) {
                startLoading();
            }
        }
    }

    @Override
    public Snapshot snapshot() {
        return status;
    }

    @Override
    public synchronized void setRequested(boolean enabled) {
        if (closed || unsupportedReason != null) {
            return;
        }
        if (enabled == status.requested) {
            if (enabled && foreground && status.stage == Stage.ERROR) {
                startLoading();
            }
            return;
        }
        preferences.edit().putBoolean(PREF_ENABLED, enabled).apply();
        if (enabled) {
            status = snapshot(Stage.PAUSED, -1, "");
            if (foreground) {
                startLoading();
            }
        } else {
            status = snapshot(Stage.OFF, -1, "");
            cancelLoad();
            cancelDecisions();
            WORKER.execute(this::releaseSession);
        }
    }

    @Override
    public synchronized void setForeground(boolean active) {
        if (foreground == active || closed) {
            return;
        }
        foreground = active;
        if (unsupportedReason != null || !status.requested) {
            return;
        }
        if (active) {
            startLoading();
        } else {
            status = snapshot(Stage.PAUSED, -1, "");
            cancelLoad();
            cancelDecisions();
        }
    }

    @Override
    public synchronized void selectAsync(String system, String user, List<String> planIds,
                                         long deadlineNanos, Consumer<Selection> callback) {
        long startedNanos = System.nanoTime();
        if (closed || status.stage != Stage.READY) {
            WORKER.execute(() -> callback.accept(new Selection(null, "", elapsedMillis(startedNanos),
                    "Model is unavailable")));
            return;
        }
        List<String> ids = new ArrayList<>(planIds);
        Work work = new Work();
        decisions.add(work);
        WORKER.execute(() -> selectOnWorker(system, user, ids, deadlineNanos, startedNanos, work, callback));
    }

    @Override
    public synchronized void cancelDecisions() {
        for (Work decision : decisions) {
            decision.cancel();
        }
        decisions.clear();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancelLoad();
        cancelDecisions();
        if (unsupportedReason == null) {
            status = snapshot(status.requested ? Stage.PAUSED : Stage.OFF, -1, "");
            WORKER.execute(this::releaseSession);
        }
    }

    private synchronized void startLoading() {
        Work work = new Work();
        loading = work;
        status = snapshot(Stage.VERIFYING, -1, "");
        WORKER.execute(() -> loadOnWorker(work));
    }

    private void cancelLoad() {
        if (loading != null) {
            loading.cancel();
            loading = null;
        }
    }

    private void loadOnWorker(Work work) {
        long startedNanos = System.nanoTime();
        long session = 0;
        try {
            if (work.canceled) {
                return;
            }
            if (nativeSession != 0) {
                completeLoad(work, Stage.READY, "");
                return;
            }
            long operation = work.beginNative(-1);
            if (operation == 0) {
                return;
            }
            String path = QwenModel.acquire(application, () -> work.canceled,
                    (stage, fraction) -> reportLoad(work, stage, fraction)).getAbsolutePath();
            if (!reportLoad(work, Stage.LOADING, 0)) {
                return;
            }
            int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
            session = QwenNative.load(utf8(path), CONTEXT_TOKENS, threads, operation, fraction -> {
                if (fraction < 0) {
                    return reportLoad(work, Stage.PREPARING, -1);
                }
                return reportLoad(work, Stage.LOADING, Math.max(0, Math.min(1, fraction)));
            });
            synchronized (this) {
                if (loading == work && !work.canceled && session != 0) {
                    nativeSession = session;
                    session = 0;
                    completeLoad(work, Stage.READY, "");
                    Log.i(TAG, "Model ready in " + elapsedMillis(startedNanos) + " ms. SHA-256 " + QwenModel.SHA256);
                } else {
                    completeLoad(work, Stage.ERROR, "Qwen could not load the model. Retry to load it again.");
                }
            }
        } catch (IOException failure) {
            completeLoad(work, Stage.ERROR, failure.getMessage());
        } catch (LinkageError failure) {
            Log.e(TAG, "Qwen native runtime unavailable", failure);
            completeLoad(work, Stage.ERROR, "The local AI runtime is unavailable on this device");
        } catch (OutOfMemoryError failure) {
            completeLoad(work, Stage.ERROR, "Insufficient memory to load Qwen");
        } catch (RuntimeException failure) {
            Log.e(TAG, "Qwen initialization failed", failure);
            completeLoad(work, Stage.ERROR, "Qwen initialization failed");
        } finally {
            if (session != 0) {
                QwenNative.release(session);
            }
            work.finishNative();
        }
    }

    private synchronized boolean reportLoad(Work work, Stage stage, float fraction) {
        if (loading != work || work.canceled) {
            return false;
        }
        Snapshot previous = status;
        status = snapshot(stage, fraction, "");
        if (stage != previous.stage || stage == Stage.LOADING && (int) (fraction * 10) > (int) (previous.progress * 10)) {
            Log.d(TAG, "Model " + stage + (fraction >= 0 ? " " + fraction : ""));
        }
        return true;
    }

    private synchronized void completeLoad(Work work, Stage stage, String detail) {
        if (loading == work && !work.canceled) {
            loading = null;
            status = snapshot(stage, -1, detail == null ? "Model transfer failed" : detail);
        }
    }

    private void selectOnWorker(String system, String user, List<String> ids, long deadlineNanos,
                                long startedNanos, Work work, Consumer<Selection> callback) {
        String planId = null;
        String detail;
        try {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (work.canceled || nativeSession == 0) {
                detail = "Decision canceled";
            } else if (remainingNanos <= 0) {
                detail = "Decision deadline exceeded";
            } else {
                long operation = work.beginNative(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                if (operation == 0) {
                    detail = "Decision canceled";
                } else {
                    String result = QwenNative.select(nativeSession, operation, utf8(system), utf8(user),
                            utf8(grammar(ids)));
                    String selected = selectedId(result);
                    if (ids.contains(selected)) {
                        planId = selected;
                        detail = "";
                    } else {
                        detail = result == null || result.startsWith("ok:") ? "Invalid model selection" : result;
                    }
                }
            }
        } catch (LinkageError | RuntimeException failure) {
            Log.e(TAG, "Qwen decision failed", failure);
            detail = "Local AI decision failed";
        } catch (OutOfMemoryError failure) {
            detail = "Insufficient memory for a Qwen decision";
        } finally {
            work.finishNative();
        }
        synchronized (this) {
            if (!decisions.remove(work) || work.canceled || status.stage != Stage.READY) {
                planId = null;
                detail = "Decision canceled";
            } else if (deadlineNanos - System.nanoTime() <= 0) {
                planId = null;
                detail = "Decision deadline exceeded";
            }
        }
        callback.accept(new Selection(planId, QwenModel.SHA256, elapsedMillis(startedNanos), detail));
    }

    private void releaseSession() {
        if (nativeSession != 0) {
            QwenNative.release(nativeSession);
            nativeSession = 0;
        }
    }

    private static String grammar(List<String> ids) {
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("At least one plan is required");
        }
        for (String id : ids) {
            if (!PLAN_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Invalid plan ID");
            }
        }
        return "root ::= \"{\\\"plan_id\\\":\\\"\" id \"\\\"}\"\nid ::= \"" + String.join("\" | \"", ids) + "\"";
    }

    private Snapshot snapshot(Stage stage, float fraction, String detail) {
        return new Snapshot(true, stage != Stage.OFF, stage, fraction, detail,
                stage == Stage.READY ? QwenModel.SHA256 : "");
    }

    private static String deviceLimitation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "Requires Android 13 or newer";
        }
        if (Process.is64Bit() && Build.SUPPORTED_64_BIT_ABIS.length != 0) {
            String abi = Build.SUPPORTED_64_BIT_ABIS[0];
            if ("arm64-v8a".equals(abi)) {
                return null;
            }
            if ("x86_64".equals(abi)) {
                return hasX86InferenceInstructions() ? null : "Requires AVX2, FMA, and F16C on x86-64";
            }
        }
        return "Requires a supported 64-bit device";
    }

    private static boolean hasX86InferenceInstructions() {
        boolean hasFlags = false;
        // The kernel flags also account for operating system support for the AVX registers.
        try (BufferedReader cpuInfo = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = cpuInfo.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator >= 0 && line.substring(0, separator).trim().equals("flags")) {
                    List<String> flags = Arrays.asList(line.substring(separator + 1).trim().split("\\s+"));
                    if (!flags.containsAll(Arrays.asList("avx", "avx2", "fma", "f16c"))) {
                        return false;
                    }
                    hasFlags = true;
                }
            }
        } catch (IOException | SecurityException failure) {
            return false;
        }
        return hasFlags;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String selectedId(String result) {
        if (result != null && result.length() == SELECTION_PREFIX.length() + 3 + SELECTION_SUFFIX.length()
                && result.startsWith(SELECTION_PREFIX) && result.endsWith(SELECTION_SUFFIX)) {
            return result.substring(SELECTION_PREFIX.length(), result.length() - SELECTION_SUFFIX.length());
        }
        return null;
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static final class Work {
        private volatile boolean canceled;
        private long nativeOperation;

        synchronized long beginNative(long timeoutMillis) {
            if (!canceled) {
                nativeOperation = QwenNative.newOperation(timeoutMillis);
                if (nativeOperation == 0) {
                    throw new OutOfMemoryError("Unable to allocate native cancellation state");
                }
            }
            return nativeOperation;
        }

        synchronized void finishNative() {
            if (nativeOperation != 0) {
                QwenNative.freeOperation(nativeOperation);
                nativeOperation = 0;
            }
        }

        synchronized void cancel() {
            canceled = true;
            if (nativeOperation != 0) {
                QwenNative.cancelOperation(nativeOperation);
            }
        }
    }
}
