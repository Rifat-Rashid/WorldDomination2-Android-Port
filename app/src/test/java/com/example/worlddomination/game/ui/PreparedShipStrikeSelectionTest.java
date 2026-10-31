package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class PreparedShipStrikeSelectionTest {
    @Test
    public void cityTapAddsAValidStrikeForThePreparedShip() {
        GameState state = readyShipAttack(3);

        assertEquals(PreparedShipStrikeSelection.Result.ADDED,
                PreparedShipStrikeSelection.tryAddTargetCity(state, 1, 2, "warhead1"));

        List<GameState.ShipStrike> strikes = state.getSelectedShipStrikes();
        assertEquals(1, strikes.size());
        assertEquals(1, strikes.get(0).targetPlayerId);
        assertEquals(2, strikes.get(0).cityIndex);
    }

    @Test
    public void cityTapCannotChangeNationAfterTheFirstShipStrike() {
        GameState state = readyShipAttack(3);
        assertEquals(PreparedShipStrikeSelection.Result.ADDED,
                PreparedShipStrikeSelection.tryAddTargetCity(state, 1, 0, "warhead1"));

        assertEquals(PreparedShipStrikeSelection.Result.WRONG_NATION,
                PreparedShipStrikeSelection.tryAddTargetCity(state, 2, 0, "warhead1"));
        assertEquals(PreparedShipStrikeSelection.Result.ADDED,
                PreparedShipStrikeSelection.tryAddTargetCity(state, 1, 1, "warhead1"));
        assertEquals(2, state.getSelectedShipStrikeCount());
        assertEquals(1, state.getSelectedShipStrikes().get(0).targetPlayerId);
    }

    @Test
    public void fourthCityTapReplacesTheOldestStrikeWithoutChangingNation() {
        GameState state = readyShipAttack(4);
        for (int cityIndex = 0; cityIndex < 3; cityIndex++) {
            assertEquals(PreparedShipStrikeSelection.Result.ADDED,
                    PreparedShipStrikeSelection.tryAddTargetCity(
                            state, 1, cityIndex, "warhead1"));
        }

        assertEquals(PreparedShipStrikeSelection.Result.ADDED,
                PreparedShipStrikeSelection.tryAddTargetCity(state, 1, 3, "warhead1"));
        List<GameState.ShipStrike> strikes = state.getSelectedShipStrikes();
        assertEquals(3, strikes.size());
        assertEquals(1, strikes.get(0).cityIndex);
        assertEquals(3, strikes.get(2).cityIndex);
    }

    @Test
    public void cityTapRequiresPreparedShipAndRejectsUnavailablePayload() {
        GameState unprepared = new GameState(1, 47L);
        assertEquals(PreparedShipStrikeSelection.Result.NOT_READY,
                PreparedShipStrikeSelection.tryAddTargetCity(
                        unprepared, 1, 0, "warhead1"));

        GameState state = readyShipAttack(1);
        assertEquals(PreparedShipStrikeSelection.Result.INVALID_SELECTION,
                PreparedShipStrikeSelection.tryAddTargetCity(
                        state, 1, 0, "warhead2"));
        assertEquals(0, state.getSelectedShipStrikeCount());
    }

    private static GameState readyShipAttack(int warheadStock) {
        GameState state = new GameState(1, 53L);
        GameState.PlayerState human = state.getHuman();
        GameState.WeaponState ship = human.getWeapons().get("ship");
        ship.active = true;
        ship.inStock = 1;
        GameState.WeaponState warhead = human.getWeapons().get("warhead1");
        warhead.active = true;
        warhead.inStock = warheadStock;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        if (!state.configurePreparedCarrier("ship") || !state.acceptSelectedAction()) {
            throw new AssertionError("Could not queue the prepared carrier");
        }
        state.selectAction(GameState.Action.RESOURCES);
        if (!state.acceptSelectedAction()) {
            throw new AssertionError("Could not fill the second action slot");
        }
        state.confirmTurn();
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        return state;
    }
}
