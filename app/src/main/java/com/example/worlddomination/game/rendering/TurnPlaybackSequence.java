package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

import java.util.Arrays;
import java.util.List;

/** Source-timed action labels and the interaction barrier around turn playback. */
public final class TurnPlaybackSequence {
    public enum Phase {
        IDLE,
        ACTION_LABELS,
        BATTLE,
        RESET_BARRIER,
        CLEANUP_HANDOFF
    }

    public static final float ACTION_LABEL_INTERVAL_SECONDS = 1.6f;
    public static final float RESET_BARRIER_SECONDS = 2.5f;
    public static final float HANDOFF_SECONDS = 1f / 24f;
    private static final double TIMING_EPSILON_SECONDS = 0.000001d;

    private static final GameState.Action[] SOURCE_ACTION_ORDER = {
            GameState.Action.RESOURCES,
            GameState.Action.PRODUCTION,
            GameState.Action.RESEARCH,
            GameState.Action.MASS_MEDIA,
            GameState.Action.PREPARED_ATTACK,
            GameState.Action.ATTACK,
            GameState.Action.DEFENSE,
            GameState.Action.PROPAGANDA,
            GameState.Action.DIPLOMACY
    };

    private final GameState.Action[] actions = new GameState.Action[SOURCE_ACTION_ORDER.length];
    private Phase phase = Phase.IDLE;
    private int actionCount;
    private int actionIndex;
    private double secondsUntilNextLabel;
    private double secondsUntilCleanupStep;
    private boolean labelPassActive;
    private GameState.Action currentAction;

    /** Starts the original 1.6-second action-label pass for one resolved turn. */
    public void begin(List<GameState.Action> resolvedActions) {
        Arrays.fill(actions, null);
        actionCount = 0;
        actionIndex = 0;
        currentAction = null;
        for (GameState.Action sourceAction : SOURCE_ACTION_ORDER) {
            if (resolvedActions != null && resolvedActions.contains(sourceAction)) {
                actions[actionCount++] = sourceAction;
            }
        }
        secondsUntilNextLabel = ACTION_LABEL_INTERVAL_SECONDS;
        secondsUntilCleanupStep = 0d;
        labelPassActive = true;
        phase = Phase.ACTION_LABELS;
    }

    /** Advances with the fixed presentation clock and returns a newly selected source action. */
    public GameState.Action advance(float elapsedSeconds) {
        if (elapsedSeconds <= 0f) {
            return null;
        }
        GameState.Action revealedAction = null;
        // Flash's action-building interval keeps running after the unit ticker stops.
        if (labelPassActive) {
            secondsUntilNextLabel -= elapsedSeconds;
            if (secondsUntilNextLabel <= TIMING_EPSILON_SECONDS) {
                secondsUntilNextLabel += ACTION_LABEL_INTERVAL_SECONDS;
                if (actionIndex < actionCount) {
                    currentAction = actions[actionIndex++];
                    revealedAction = currentAction;
                } else {
                    currentAction = null;
                    labelPassActive = false;
                    if (phase == Phase.ACTION_LABELS) phase = Phase.BATTLE;
                }
            }
        }
        // One transition per call: an oversized frame never consumes the handoff tick.
        if (phase == Phase.RESET_BARRIER) {
            secondsUntilCleanupStep -= elapsedSeconds;
            if (secondsUntilCleanupStep <= TIMING_EPSILON_SECONDS) {
                phase = Phase.CLEANUP_HANDOFF;
                secondsUntilCleanupStep = HANDOFF_SECONDS;
            }
        } else if (phase == Phase.CLEANUP_HANDOFF) {
            secondsUntilCleanupStep -= elapsedSeconds;
            if (secondsUntilCleanupStep <= TIMING_EPSILON_SECONDS) reset();
        }
        return revealedAction;
    }

    /** Starts the source post-empty wait, including when action labels are still running. */
    public void completeBattlePlayback() {
        if (phase == Phase.ACTION_LABELS || phase == Phase.BATTLE) {
            secondsUntilCleanupStep = RESET_BARRIER_SECONDS;
            phase = Phase.RESET_BARRIER;
        }
    }

    public void reset() {
        Arrays.fill(actions, null);
        phase = Phase.IDLE;
        actionCount = 0;
        actionIndex = 0;
        secondsUntilNextLabel = 0d;
        secondsUntilCleanupStep = 0d;
        labelPassActive = false;
        currentAction = null;
    }

    public Phase getPhase() {
        return phase;
    }

    public GameState.Action getCurrentAction() {
        return currentAction;
    }

    public boolean isWaitingForActionLabels() {
        return labelPassActive;
    }

    public boolean isReadyForBattle() {
        return phase == Phase.BATTLE;
    }

    public boolean isCleaningUp() {
        return phase == Phase.RESET_BARRIER || phase == Phase.CLEANUP_HANDOFF;
    }

    public boolean isBlockingInput() {
        return phase != Phase.IDLE;
    }
}
