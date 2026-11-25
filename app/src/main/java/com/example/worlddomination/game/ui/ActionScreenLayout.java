package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** Keeps map-first actions on the battlefield and opens details only when needed. */
public final class ActionScreenLayout {
    private ActionScreenLayout() { }

    public static boolean opensCenterDialog(GameState.Action action) {
        return action == GameState.Action.RESOURCES || action == GameState.Action.PRODUCTION
                || action == GameState.Action.RESEARCH || action == GameState.Action.DIPLOMACY;
    }

    public static boolean hasInlineActionControls(GameState.Action action) {
        return action != null && action != GameState.Action.RESOURCES;
    }

    public static boolean selectsTargetsOnMap(GameState.Action action) {
        return action == GameState.Action.ATTACK || action == GameState.Action.PROPAGANDA;
    }
}
