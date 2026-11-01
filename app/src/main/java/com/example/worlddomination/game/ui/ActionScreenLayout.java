package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** Keeps map-first actions on the battlefield and opens details only when needed. */
public final class ActionScreenLayout {
    private ActionScreenLayout() { }

    public static boolean opensCenterDialog(GameState.Action action) {
        return action != null && action != GameState.Action.MASS_MEDIA
                && action != GameState.Action.ATTACK;
    }

    public static boolean hasInlineActionControls(GameState.Action action) {
        return action == GameState.Action.MASS_MEDIA || action == GameState.Action.ATTACK;
    }

    public static boolean selectsTargetsOnMap(GameState.Action action) {
        return action == GameState.Action.ATTACK;
    }
}
