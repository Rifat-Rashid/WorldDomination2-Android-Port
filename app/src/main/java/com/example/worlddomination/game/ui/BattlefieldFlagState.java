package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** Source flag ownership and placement, independent of graphics and gameplay randomness. */
public final class BattlefieldFlagState {
    public static final int ISLAND_COUNT = 5;
    public static final int ANIMATION_FRAME_COUNT = 25;

    // battleMC / flag{playerNum} registration points in the original 800 x 600 stage.
    private static final float[] SOURCE_X = {453.5f, 153.4f, 674.8f, 106.65f, 738.8f};
    private static final float[] SOURCE_Y = {341.95f, 136.05f, 132.05f, 554.6f, 544.2f};

    private BattlefieldFlagState() { }

    /**
     * View.update_flags displays a living nation's own flag and a defeated nation's
     * last attacker's flag. Color zero identifies a native slot absent from the
     * source playerArr; those islands must stay empty even after a conquest.
     */
    public static int ownerPlayerId(GameState game, int island) {
        if (game == null || island < 0 || island >= ISLAND_COUNT) {
            return -1;
        }
        GameState.PlayerState nation = game.getPlayerState(island);
        if (nation.getPlayerColor() == 0) {
            return -1;
        }
        int owner = nation.alive ? island : nation.getLastAttackedByPlayerId();
        if (owner < 0 || owner >= ISLAND_COUNT
                || game.getPlayerState(owner).getPlayerColor() == 0) {
            return -1;
        }
        // Do not walk an ownership chain here. Model.killPlayer transfers credit;
        // the source view reads the resulting lastAttackedBy_num directly.
        return owner;
    }

    /** Source pole frame label, or null when the island's flag is empty. */
    public static String religion(GameState game, int island) {
        int owner = ownerPlayerId(game, island);
        return owner < 0 ? null : game.getPlayerState(owner).getReligionCode();
    }

    /** Source player color id 1..5, or zero when the island's flag is empty. */
    public static int color(GameState game, int island) {
        int owner = ownerPlayerId(game, island);
        return owner < 0 ? 0 : game.getPlayerState(owner).getPlayerColor();
    }

    public static float sourceX(int island) {
        validateIsland(island);
        return SOURCE_X[island];
    }

    public static float sourceY(int island) {
        validateIsland(island);
        return SOURCE_Y[island];
    }

    /** Zero-based frame of the two 25-frame children, advanced by the 24 Hz clock. */
    public static int animationFrame(long sourceTicks) {
        return (int) Math.floorMod(sourceTicks, (long) ANIMATION_FRAME_COUNT);
    }

    private static void validateIsland(int island) {
        if (island < 0 || island >= ISLAND_COUNT) {
            throw new IllegalArgumentException("Invalid battlefield island");
        }
    }
}
