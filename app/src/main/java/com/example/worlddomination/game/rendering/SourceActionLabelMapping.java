package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

/** Labels selected by Sub_model's building_names_to_actions_arr, not by the order's action. */
public final class SourceActionLabelMapping {
    private SourceActionLabelMapping() { }

    /** Returns null when the source building has no matching action-name entry. */
    public static GameState.Action actionForFrame(String frameName) {
        if (frameName == null) return null;
        switch (frameName) {
            case "resources": return GameState.Action.RESOURCES;
            case "factory": return GameState.Action.PRODUCTION;
            case "tech":
            case "technology": return GameState.Action.RESEARCH;
            case "religion": return GameState.Action.MASS_MEDIA;
            case "sub":
            case "space":
            case "ship":
            case "silo":
            case "airfield": return GameState.Action.PREPARED_ATTACK;
            case "base":
            case "helipad":
            case "____":
            case "hatch": return GameState.Action.ATTACK;
            case "propaganda": return GameState.Action.PROPAGANDA;
            case "diplomacy": return GameState.Action.DIPLOMACY;
            case "antiAir_still":
            case "antiRocket_still": return GameState.Action.DEFENSE;
            default: return null;
        }
    }
}
