package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

/** Unit constructor depth accounting for the native recovered linkages. */
public final class SourceBattleUnitAllocation {
    private final BattlefieldDisplayState state;
    private int parameter;

    public SourceBattleUnitAllocation(BattlefieldDisplayState state) {
        this.state = state;
    }

    /** Returns the parameter preceding this source record's reverse Unit expansion. */
    public int reserveBeforeEvent(GameState.CombatEvent event) {
        int preceding = parameter;
        if (FlashBattleRoutes.supportsApacheMarine(event)) {
            // Actual handles are installed by SourceApacheDisplayDepths immediately
            // after route construction, before the next source record is visited.
            parameter += 20;
        } else if (FlashBattleRoutes.supports(event)) {
            for (int i = 0; i < 4; i++) reserve(false, true, false, 0, false);
        } else if (FlashBattleRoutes.supportsChinookTank(event)) {
            reserve(false, false, true, 0, false); // tankTop at param_depth.
            // Native playback resolves the source tank_noShade typo to the
            // recovered tank_noshade artwork, so its body occupies a real depth.
            reserve(false, false, false, 0, false);
            reserve(true, false, false, 0, false);
        } else if (FlashBattleRoutes.supportsSuicideBomber(event)) {
            reserve(false, true, false, 0, true);
        } else if (FlashBattleRoutes.supportsCarrierPlaneStrike(event)) {
            reserve(true, true, false, event.shipPayloadIndex, false);
        } else if (FlashBattleRoutes.supportsStandalonePlaneStrike(event)) {
            reserve(true, true, false, 0, false);
        } else if (FlashBattleRoutes.supportsRocketStrike(event)
                || FlashBattleRoutes.supportsSubmarineRocketStrike(event)
                || FlashBattleRoutes.supportsSatelliteStrike(event)) {
            reserve(false, false, false, 0, false);
        }
        return preceding;
    }

    private void reserve(boolean shadow, boolean tint, boolean tankTop,
                           int formationNumber, boolean suicide) {
        parameter += 5;
        String name = "unit" + parameter / 5;
        int depth = tankTop ? parameter : formationNumber > 0
                ? 800000 + parameter + formationNumber : state.nextDepth();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> body = occupant(name, depth);
        if (suicide) state.clips().swapDepths(body, state.nextDepth());
        if (shadow) occupant("shadow_" + name, state.nextDepth());
        if (tint) occupant("tint" + name, state.nextDepth());
        occupant("targetDot" + name, state.nextDepth());
        // Unit's constructor raises suicide_bomber again after tint/dot setup.
        if (suicide) state.clips().swapDepths(body, state.nextDepth());
    }

    private SourceDisplayList.Entry<BattlefieldDisplayState.Item> occupant(String name,
                                                                           int depth) {
        return state.attach(BattlefieldDisplayState.Kind.AUTHORED, parameter / 5,
                name, null, depth);
    }
}
