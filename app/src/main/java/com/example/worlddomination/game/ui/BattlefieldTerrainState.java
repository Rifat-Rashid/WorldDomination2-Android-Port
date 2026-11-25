package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** Source phone terrain retains default land frames beneath unoccupied player slots. */
public final class BattlefieldTerrainState {
    public static final int ISLAND_COUNT = 5;

    private BattlefieldTerrainState() { }

    /** Original terrain frame label, or null outside a match/valid island slot. */
    public static String religion(GameState game, int island) {
        if (game == null || island < 0 || island >= ISLAND_COUNT) {
            return null;
        }
        GameState.PlayerState originalNation = game.getPlayerState(island);
        // View.setMapFrames resets every land clip to frame 1 (chr), then applies
        // player religions. LandscapeUI.draw_map paints all land clips and omits
        // the water overlays which would conceal unused slots on the Flash stage.
        return originalNation.getPlayerColor() == 0 ? "chr" : originalNation.getReligionCode();
    }

    public static boolean isPresent(GameState game, int island) {
        return religion(game, island) != null;
    }
}
