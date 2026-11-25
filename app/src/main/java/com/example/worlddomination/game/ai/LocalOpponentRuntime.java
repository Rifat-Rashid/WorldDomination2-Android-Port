package com.example.worlddomination.game.ai;

import java.util.List;
import java.util.function.Consumer;

/** Optional local model service. Loading and inference run asynchronously. */
public interface LocalOpponentRuntime extends AutoCloseable {
    enum Stage { UNSUPPORTED, OFF, DOWNLOADING, COPYING, VERIFYING, LOADING, PREPARING, READY, PAUSED, ERROR }

    final class Snapshot {
        public final boolean supported;
        public final boolean requested;
        public final Stage stage;
        /** A fraction from 0 to 1, or -1 for an indeterminate stage. */
        public final float progress;
        public final String detail;
        public final String modelSha256;

        public Snapshot(boolean supported, boolean requested, Stage stage, float progress,
                        String detail, String modelSha256) {
            this.supported = supported;
            this.requested = requested;
            this.stage = stage;
            this.progress = progress;
            this.detail = detail;
            this.modelSha256 = modelSha256;
        }
    }

    final class Selection {
        /** Null when the original opponent planner needs to make this decision. */
        public final String planId;
        public final String modelSha256;
        public final long latencyMillis;
        public final String detail;

        public Selection(String planId, String modelSha256, long latencyMillis, String detail) {
            this.planId = planId;
            this.modelSha256 = modelSha256;
            this.latencyMillis = latencyMillis;
            this.detail = detail;
        }
    }

    Snapshot snapshot();

    /** Persists the preference. An explicit true may also retry a failed load. */
    void setRequested(boolean enabled);

    void setForeground(boolean foreground);

    /** Delivers one result asynchronously. The game must check that the result is still current. */
    void selectAsync(String system, String user, List<String> planIds, long deadlineNanos,
                     Consumer<Selection> callback);

    void cancelDecisions();

    @Override
    void close();
}
