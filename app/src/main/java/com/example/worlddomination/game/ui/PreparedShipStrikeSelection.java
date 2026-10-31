package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

import java.util.List;

/** Applies a city-tap ship strike while enforcing the source's same-nation selection rule. */
public final class PreparedShipStrikeSelection {
    public enum Result {
        ADDED,
        NOT_READY,
        WRONG_NATION,
        INVALID_SELECTION
    }

    private PreparedShipStrikeSelection() {
    }

    public static Result tryAddTargetCity(GameState game, int targetPlayerId, int cityIndex,
                                          String payloadName) {
        if (game == null || game.getSelectedAction() != GameState.Action.PREPARED_ATTACK) {
            return Result.NOT_READY;
        }
        String carrierName = game.getPreparedCarrierName(0);
        GameState.WeaponState carrier = game.getHuman().getWeapons().get(carrierName);
        if (carrier == null || !"ship".equals(carrier.definition.type)) {
            return Result.NOT_READY;
        }

        List<GameState.ShipStrike> selected = game.getSelectedShipStrikes();
        if (!selected.isEmpty() && selected.get(0).targetPlayerId != targetPlayerId) {
            return Result.WRONG_NATION;
        }
        return game.configurePreparedShipStrike(targetPlayerId, cityIndex, payloadName)
                ? Result.ADDED : Result.INVALID_SELECTION;
    }
}
