package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** View.setMapFrames uses original playerArr membership and faith, not flag ownership. */
public final class BattlefieldTerrainState {
    public static final int ISLAND_COUNT = 5;

    private BattlefieldTerrainState() { }

    /** Original terrain frame label, or null for an unoccupied island slot. */
    public static String religion(GameState game, int island) {
        if (game == null || island < 0 || island >= ISLAND_COUNT) {
            return null;
        }
        GameState.PlayerState originalNation = game.getPlayerState(island);
        // Native placeholders have color zero and no corresponding source player.
        return originalNation.getPlayerColor() == 0 ? null : originalNation.getReligionCode();
    }

    public static boolean isPresent(GameState game, int island) {
        return religion(game, island) != null;
    }
}
