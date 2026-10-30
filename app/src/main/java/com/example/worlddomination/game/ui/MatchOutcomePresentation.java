package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

import java.util.Locale;

/** Text and score formatting for the native match-ending screen. */
public final class MatchOutcomePresentation {
    private MatchOutcomePresentation() { }

    public static boolean isFinished(GameState.MatchStatus status) {
        return status != null && status != GameState.MatchStatus.ONGOING;
    }

    public static String title(GameState.MatchStatus status) {
        if (status == GameState.MatchStatus.VICTORY) {
            return "WORLD DOMINATION";
        }
        if (status == GameState.MatchStatus.DEFEAT) {
            return "NATION DEFEATED";
        }
        return "";
    }

    public static String message(GameState.MatchStatus status) {
        if (status == GameState.MatchStatus.VICTORY) {
            return "Your rivals have fallen. Your nation rules the world.";
        }
        if (status == GameState.MatchStatus.DEFEAT) {
            return "Your nation was destroyed and the war is lost.";
        }
        return "";
    }

    /** The reference end screen formats score groups with periods. */
    public static String formatScore(int score) {
        return formatScore((long) score);
    }

    public static String formatScore(long score) {
        return String.format(Locale.US, "%,d", Math.max(0L, score)).replace(',', '.');
    }
}
