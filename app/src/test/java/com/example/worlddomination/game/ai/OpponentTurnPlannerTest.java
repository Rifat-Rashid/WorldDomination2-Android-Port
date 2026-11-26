package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OpponentTurnPlannerTest {
    private final FakeRuntime runtime = new FakeRuntime();
    private final Queue<Runnable> gameThread = new ArrayDeque<>();
    private final List<String> diagnostics = new ArrayList<>();
    private long now;
    private final OpponentTurnPlanner planner = new OpponentTurnPlanner(runtime, gameThread::add,
            diagnostics::add, () -> now);

    @Test
    public void disabledOrLoadingUsesTheSynchronousBaselinePath() {
        GameState game = queuedGame(1);
        for (LocalOpponentRuntime.Stage stage : new LocalOpponentRuntime.Stage[]{
                LocalOpponentRuntime.Stage.OFF, LocalOpponentRuntime.Stage.LOADING,
                LocalOpponentRuntime.Stage.UNSUPPORTED}) {
            runtime.stage = stage;
            assertFalse(planner.begin(game));
            assertFalse(game.isOpponentPlanningPending());
        }
        assertEquals(0, runtime.callbacks.size());
        assertEquals(0, game.getTurn());
        assertEquals(2, game.getQueuedActionCount());
    }

    @Test
    public void commitsARealCandidateAfterCallbacksReachTheGameThread() {
        GameState game = queuedGame(2);
        assertTrue(planner.begin(game));
        assertTrue(planner.begin(game));
        assertEquals(1, runtime.callbacks.size());
        assertTrue(game.isOpponentPlanningPending());

        runtime.replyWithCandidate(0);
        assertFalse(planner.update(game, true));
        dispatchCallbacks();
        assertEquals(2, runtime.callbacks.size());
        runtime.replyWithCandidate(1);
        dispatchCallbacks();

        assertTrue(planner.update(game, true));
        assertEquals(1, game.getTurn());
        assertFalse(game.isOpponentPlanningPending());
        assertEquals(3, diagnostics.size());
        for (String record : diagnostics.subList(0, 2)) assertTrue(record, record.contains("source=qwen"));
        assertTrue(diagnostics.get(2).contains("model_commits=2"));
        runtime.replyWithCandidate(1);
        dispatchCallbacks();
        assertFalse(planner.update(game, true));
        assertEquals(1, game.getTurn());
    }

    @Test
    public void disablingAfterAnUncommittedChoiceRestoresSeededBaselineOnce() throws Exception {
        GameState game = queuedGame(2);
        GameState baseline = queuedGame(2);
        baseline.confirmTurn();
        assertTrue(planner.begin(game));
        runtime.replyWithCandidate(0);
        dispatchCallbacks();
        runtime.replyWithCandidate(1);

        planner.useBaseline("disabled");
        dispatchCallbacks();
        assertFalse(planner.update(game, false));
        assertEquals(0, game.getTurn());
        assertTrue(planner.update(game, true));
        assertFalse(planner.update(game, true));
        assertEquals(1, runtime.cancellations);
        assertArrayEquals(GameSaveCodec.encode(baseline), GameSaveCodec.encode(game));
        for (String record : diagnostics.subList(0, 2)) assertTrue(record, record.contains("reason=disabled"));
    }

    @Test
    public void timeoutAndInvalidIdFallbackOnceAndIgnoreLateReplies() throws Exception {
        GameState game = queuedGame(2);
        GameState baseline = queuedGame(2);
        baseline.confirmTurn();
        assertTrue(planner.begin(game));
        now = runtime.deadlines.get(0);
        assertFalse(planner.update(game, true));
        runtime.replyWithCandidate(0);
        runtime.callbacks.get(1).accept(new LocalOpponentRuntime.Selection("other-request", "sha", 5, ""));
        dispatchCallbacks();
        assertTrue(planner.update(game, true));
        assertArrayEquals(GameSaveCodec.encode(baseline), GameSaveCodec.encode(game));
        assertTrue(diagnostics.get(0), diagnostics.get(0).contains("reason=timeout"));
        assertTrue(diagnostics.get(1), diagnostics.get(1).contains("reason=invalid-plan-id"));
    }

    @Test
    public void abandonedMatchOrDisposedPlannerCannotReceiveAStaleDecision() {
        GameState old = queuedGame(1);
        assertTrue(planner.begin(old));
        planner.abandonMatch();
        GameState loaded = queuedGame(1);
        assertTrue(planner.begin(loaded));
        runtime.replyWithCandidate(0);
        dispatchCallbacks();
        assertFalse(planner.update(loaded, true));
        assertEquals(0, loaded.getTurn());

        planner.close();
        runtime.replyWithCandidate(1);
        assertTrue(gameThread.isEmpty());
        assertFalse(planner.update(loaded, true));
        assertTrue(runtime.closed);
        assertEquals(0, old.getTurn());
        assertEquals(0, loaded.getTurn());
    }

    private void dispatchCallbacks() {
        while (!gameThread.isEmpty()) gameThread.remove().run();
    }

    private static GameState queuedGame(int opponents) {
        GameSetup setup = new GameSetup(3L);
        for (int opponent = 0; opponent < opponents; opponent++) assertTrue(setup.toggleOpponent(opponent));
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.SKIP);
        assertTrue(game.acceptSelectedAction());
        return game;
    }

    private static final class FakeRuntime implements LocalOpponentRuntime {
        final List<Consumer<Selection>> callbacks = new ArrayList<>();
        final List<List<String>> choices = new ArrayList<>();
        final List<Long> deadlines = new ArrayList<>();
        Stage stage = Stage.READY;
        int cancellations;
        boolean closed;

        @Override public Snapshot snapshot() {
            return new Snapshot(stage != Stage.UNSUPPORTED, stage != Stage.OFF, stage, -1, "", "sha");
        }
        @Override public void setRequested(boolean enabled) { stage = enabled ? Stage.READY : Stage.OFF; }
        @Override public void setForeground(boolean foreground) { }
        @Override public void selectAsync(String system, String user, List<String> planIds, long deadlineNanos,
                                          Consumer<Selection> callback) {
            choices.add(planIds);
            deadlines.add(deadlineNanos);
            callbacks.add(callback);
        }
        @Override public void cancelDecisions() { cancellations++; }
        @Override public void close() { closed = true; }

        void replyWithCandidate(int index) {
            callbacks.get(index).accept(new Selection(choices.get(index).get(0), "sha", 5, "accepted"));
        }
    }
}
