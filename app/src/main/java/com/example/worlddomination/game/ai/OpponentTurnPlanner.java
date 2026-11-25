package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TurnPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owns one pending turn. Observations, validation, and settlement stay on the game thread. */
public final class OpponentTurnPlanner implements AutoCloseable {
    private static final long OPPONENT_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final String PREFERENCE = "Preserve citizens, exploit threats, and build sustainable fighting strength.";

    private final LocalOpponentRuntime runtime;
    private final Consumer<Runnable> gameThread;
    private final Consumer<String> log;
    private final LongSupplier now;
    private PendingTurn pending;
    private volatile boolean closed;

    public OpponentTurnPlanner(LocalOpponentRuntime runtime, Consumer<Runnable> gameThread,
                               Consumer<String> log) {
        this(runtime, gameThread, log, System::nanoTime);
    }

    OpponentTurnPlanner(LocalOpponentRuntime runtime, Consumer<Runnable> gameThread,
                        Consumer<String> log, LongSupplier now) {
        this.runtime = runtime;
        this.gameThread = gameThread;
        this.log = log;
        this.now = now;
    }

    /** Returns false when the caller should use the original synchronous confirmation path. */
    public boolean begin(GameState game) {
        if (isPending(game)) return true;
        if (closed) return false;
        LocalOpponentRuntime.Snapshot model = runtime.snapshot();
        if (!model.requested || model.stage != LocalOpponentRuntime.Stage.READY) {
            for (GameState.PlayerState actor : game.getPlayers()) {
                if (actor.alive && !actor.human) {
                    log.accept("turn=" + game.getTurn() + " actor=" + actor.id
                            + " source=baseline runtime_stage=" + model.stage);
                }
            }
            return false;
        }

        PendingTurn turn = new PendingTurn(game, model.modelSha256, now.getAsLong());
        game.beginOpponentPlanning();
        pending = turn;
        for (GameState.PlayerState actor : game.getPlayers()) {
            if (!actor.alive || actor.human) continue;
            ActorDecision decision = new ActorDecision(actor.id);
            turn.decisions.add(decision);
            decision.request = DecisionRequest.capture(game, actor.id, PREFERENCE);
            if (decision.request.planIds.size() < 2) decision.reason = "no-alternative-plan";
        }
        requestNext(turn);
        return true;
    }

    public boolean isPending(GameState game) {
        return pending != null && pending.game == game;
    }

    /** Returns true on the frame that commits the turn and can start battle presentation. */
    public boolean update(GameState game, boolean canSettle) {
        if (!isPending(game)) return false;
        PendingTurn turn = pending;
        ActorDecision active = turn.active;
        if (active != null && now.getAsLong() - active.deadline >= 0) {
            turn.active = null;
            active.reason = "timeout";
            active.latencyMillis = TimeUnit.NANOSECONDS.toMillis(OPPONENT_BUDGET_NANOS);
            runtime.cancelDecisions();
            requestNext(turn);
        }
        if (!canSettle || turn.active != null) return false;

        pending = null;
        Map<Integer, TurnPlan> plans = new LinkedHashMap<>();
        for (ActorDecision decision : turn.decisions) {
            if (decision.plan != null) plans.put(decision.actorId, decision.plan);
        }
        Set<Integer> applied = game.finishOpponentPlanning(plans);
        for (ActorDecision decision : turn.decisions) {
            boolean usedModel = applied.contains(decision.actorId);
            String reason = decision.reason;
            if (decision.plan != null) reason = usedModel ? "accepted" : "revalidation";
            log.accept("turn=" + turn.number + " actor=" + decision.actorId
                    + " source=" + (usedModel ? "qwen" : "baseline") + " plan_id=" + decision.planId
                    + " model_sha256=" + turn.modelSha256 + " latency_ms=" + decision.latencyMillis
                    + " reason=" + reason);
        }
        log.accept("turn=" + turn.number + " opponents=" + turn.decisions.size() + " model_commits="
                + applied.size() + " round_ms=" + TimeUnit.NANOSECONDS.toMillis(now.getAsLong() - turn.startedNanos));
        return true;
    }

    /** Invalidate the worker before permitting the original AI to settle this uncommitted turn. */
    public void useBaseline(String reason) {
        if (pending == null || pending.baselineOnly) return;
        pending.baselineOnly = true;
        pending.active = null;
        pending.next = pending.decisions.size();
        for (ActorDecision decision : pending.decisions) {
            decision.plan = null;
            decision.reason = reason;
        }
        runtime.cancelDecisions();
    }

    /** Replacing a match discards its pending turn along with its GameState. */
    public void abandonMatch() {
        if (pending == null) return;
        pending = null;
        runtime.cancelDecisions();
    }

    private void requestNext(PendingTurn turn) {
        while (turn.next < turn.decisions.size()) {
            ActorDecision decision = turn.decisions.get(turn.next++);
            if (decision.reason != null) continue;
            turn.active = decision;
            decision.deadline = now.getAsLong() + OPPONENT_BUDGET_NANOS;
            runtime.selectAsync(DecisionRequest.SYSTEM, decision.request.user, decision.request.planIds,
                    decision.deadline, result -> {
                        if (!closed) gameThread.accept(() -> receive(turn, decision, result));
                    });
            return;
        }
        turn.active = null;
    }

    private void receive(PendingTurn turn, ActorDecision decision, LocalOpponentRuntime.Selection result) {
        if (pending != turn || turn.active != decision) return;
        turn.active = null;
        if (now.getAsLong() - decision.deadline >= 0) {
            decision.reason = "timeout";
            decision.latencyMillis = TimeUnit.NANOSECONDS.toMillis(OPPONENT_BUDGET_NANOS);
        } else if (result == null) {
            decision.reason = "model-unavailable";
        } else {
            decision.latencyMillis = result.latencyMillis;
            decision.reason = result.detail == null || result.detail.isEmpty() ? "no-result" : result.detail;
            if (result.planId != null) {
                decision.planId = result.planId;
                if (Objects.equals(turn.modelSha256, result.modelSha256)) {
                    decision.plan = decision.request.selectId(result.planId);
                    if (decision.plan == null) decision.reason = "invalid-plan-id";
                } else {
                    decision.reason = "stale-model";
                }
            }
        }
        requestNext(turn);
    }

    @Override public void close() {
        closed = true;
        abandonMatch();
        runtime.close();
    }

    private static final class PendingTurn {
        final GameState game;
        final int number;
        final String modelSha256;
        final long startedNanos;
        final List<ActorDecision> decisions = new ArrayList<>();
        boolean baselineOnly;
        int next;
        ActorDecision active;

        PendingTurn(GameState game, String modelSha256, long startedNanos) {
            this.game = game;
            this.number = game.getTurn();
            this.modelSha256 = modelSha256;
            this.startedNanos = startedNanos;
        }
    }

    private static final class ActorDecision {
        final int actorId;
        DecisionRequest request;
        long deadline;
        long latencyMillis;
        String reason;
        String planId;
        TurnPlan plan;

        ActorDecision(int actorId) { this.actorId = actorId; }
    }
}
