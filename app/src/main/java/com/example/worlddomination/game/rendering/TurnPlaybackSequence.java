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
        RESET_BARRIER
    }

    public static final float ACTION_LABEL_INTERVAL_SECONDS = 1.6f;
    public static final float RESET_BARRIER_SECONDS = 2f;
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
    private double secondsUntilNextStep;
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
        secondsUntilNextStep = ACTION_LABEL_INTERVAL_SECONDS;
        phase = Phase.ACTION_LABELS;
    }

    /** Advances with the fixed presentation clock and returns a newly selected source action. */
    public GameState.Action advance(float elapsedSeconds) {
        if (elapsedSeconds <= 0f) {
            return null;
        }
        if (phase == Phase.ACTION_LABELS) {
            secondsUntilNextStep -= elapsedSeconds;
            if (secondsUntilNextStep > TIMING_EPSILON_SECONDS) {
                return null;
            }
            secondsUntilNextStep += ACTION_LABEL_INTERVAL_SECONDS;
            if (actionIndex < actionCount) {
                currentAction = actions[actionIndex++];
                return currentAction;
            }
            currentAction = null;
            phase = Phase.BATTLE;
        } else if (phase == Phase.RESET_BARRIER) {
            secondsUntilNextStep -= elapsedSeconds;
            if (secondsUntilNextStep <= TIMING_EPSILON_SECONDS) {
                reset();
            }
        }
        return null;
    }

    /** Opens the two-second end-of-battle barrier after every combat animation has cleared. */
    public void completeBattlePlayback() {
        if (phase == Phase.BATTLE) {
            currentAction = null;
            secondsUntilNextStep = RESET_BARRIER_SECONDS;
            phase = Phase.RESET_BARRIER;
        }
    }

    public void reset() {
        Arrays.fill(actions, null);
        phase = Phase.IDLE;
        actionCount = 0;
        actionIndex = 0;
        secondsUntilNextStep = 0d;
        currentAction = null;
    }

    public Phase getPhase() {
        return phase;
    }

    public GameState.Action getCurrentAction() {
        return currentAction;
    }

    public boolean isWaitingForActionLabels() {
        return phase == Phase.ACTION_LABELS;
    }

    public boolean isReadyForBattle() {
        return phase == Phase.BATTLE;
    }

    public boolean isBlockingInput() {
        return phase != Phase.IDLE;
    }
}
