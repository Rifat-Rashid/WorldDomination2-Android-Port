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
            // Body/tint/dot handles are installed by SourceInfantryDisplayDepths.
            parameter += 20;
        } else if (FlashBattleRoutes.supportsChinookTank(event)) {
            // Install the original top/base/carrier handles immediately after
            // route construction, including the recovered native tank alias.
            parameter += 15;
        } else if (FlashBattleRoutes.supportsSuicideBomber(event)) {
            // SourceSuicideDisplayDepths installs the real body/tint/dot and
            // both constructor swaps before the following Unit is constructed.
            parameter += 5;
        } else if (FlashBattleRoutes.supportsCarrierPlaneStrike(event)) {
            reserve(true, true, event.shipPayloadIndex);
        } else if (FlashBattleRoutes.supportsStandalonePlaneStrike(event)) {
            reserve(true, true, 0);
        } else if (FlashBattleRoutes.supportsRocketStrike(event)
                || FlashBattleRoutes.supportsSubmarineRocketStrike(event)
                || FlashBattleRoutes.supportsSatelliteStrike(event)) {
            reserve(false, false, 0);
        }
        return preceding;
    }

    private void reserve(boolean shadow, boolean tint, int formationNumber) {
        parameter += 5;
        String name = "unit" + parameter / 5;
        int depth = formationNumber > 0
                ? 800000 + parameter + formationNumber : state.nextDepth();
        occupant(name, depth);
        if (shadow) occupant("shadow_" + name, state.nextDepth());
        if (tint) occupant("tint" + name, state.nextDepth());
        occupant("targetDot" + name, state.nextDepth());
    }

    private SourceDisplayList.Entry<BattlefieldDisplayState.Item> occupant(String name,
                                                                           int depth) {
        return state.attach(BattlefieldDisplayState.Kind.AUTHORED, parameter / 5,
                name, null, depth);
    }
}
