package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.ui.BattlefieldCityOrderMarkers;
import com.example.worlddomination.game.ui.BattlefieldCityOrderMarkers.Icon;
import com.example.worlddomination.game.ui.BattlefieldCitySelection;
import org.junit.Test;

import static org.junit.Assert.*;

public final class BattlefieldCityOrderMarkersTest {
    @Test public void changingEquipmentClearsOnlyTheActiveCityMarker() {
        GameState game = new GameState(1, 64L, false);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier1"));
        assertTrue(BattlefieldCityOrderMarkers.isMarked(game, 1, 0));
        game.clearBattlefieldCitySelection(); // Source stock buttons clear userSelectedCity.
        assertFalse(BattlefieldCityOrderMarkers.isMarked(game, 1, 0));
        assertFalse(game.hasSelectedActionCityTarget());
    }

    @Test public void pythonAdapterRetainsPropagandaAlongsidePreparedMarker() {
        GameState game = new GameState(1, 70L, false);
        game.getHuman().getWeapons().get("warhead1").inStock = 2;
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("rocket1"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 0, "warhead1"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(game.selectBattlefieldCity(1, 0));
        // Executed SetupMenu(3) trace: marker signature ('rocket', 'propaganda').
        assertEquals(2, BattlefieldCityOrderMarkers.iconCount(game, 1, 0));
        assertEquals(Icon.ROCKET, BattlefieldCityOrderMarkers.iconAt(game, 1, 0, 0));
        assertEquals(Icon.PROPAGANDA, BattlefieldCityOrderMarkers.iconAt(game, 1, 0, 1));
        game.cancelSelection();
        assertEquals(1, BattlefieldCityOrderMarkers.iconCount(game, 1, 0));
        assertEquals(Icon.ROCKET, BattlefieldCityOrderMarkers.iconAt(game, 1, 0, 0));
    }

    @Test public void queuedAttackSurvivesOtherSelectionAndItsCancellation() {
        GameState game = new GameState(1, 65L, false);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.selectBattlefieldCity(1, 0));
        assertTrue(game.configureAttack(1, 0, "soldier1"));
        assertEquals(Icon.SOLDIER, BattlefieldCityOrderMarkers.iconAt(game, 1, 0, 0));
        assertTrue(game.acceptSelectedAction());
        assertTrue(BattlefieldCityOrderMarkers.isMarked(game, 1, 0));

        game.selectAction(GameState.Action.PROPAGANDA);
        assertFalse(BattlefieldCityOrderMarkers.isMarked(game, 1, 1));
        assertTrue(game.selectBattlefieldCity(1, 1));
        assertEquals(Icon.PROPAGANDA, BattlefieldCityOrderMarkers.iconAt(game, 1, 1, 0));
        assertEquals(BattlefieldCitySelection.Tint.DEFAULT,
                BattlefieldCitySelection.tint(game, 1, 0, null));
        assertEquals(BattlefieldCitySelection.Tint.GREEN,
                BattlefieldCitySelection.tint(game, 1, 2, null));
        game.cancelSelection();
        assertFalse(BattlefieldCityOrderMarkers.isMarked(game, 1, 1));
        assertTrue(BattlefieldCityOrderMarkers.isMarked(game, 1, 0));
        game.removeQueuedAction(0);
        assertFalse(BattlefieldCityOrderMarkers.isMarked(game, 1, 0));
    }

    @Test public void attackAndPropagandaOnSameCityKeepSourceIconOrder() {
        GameState game = new GameState(1, 66L, false);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.selectBattlefieldCity(1, 0));
        assertTrue(game.configureAttack(1, 0, "soldier1"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(game.selectBattlefieldCity(1, 0));
        assertEquals(2, BattlefieldCityOrderMarkers.iconCount(game, 1, 0));
        assertEquals(Icon.SOLDIER, BattlefieldCityOrderMarkers.iconAt(game, 1, 0, 0));
        assertEquals(Icon.PROPAGANDA, BattlefieldCityOrderMarkers.iconAt(game, 1, 0, 1));
        assertTrue(game.acceptSelectedAction());
        game.removeQueuedAction(0);
        assertEquals(Icon.PROPAGANDA, BattlefieldCityOrderMarkers.iconAt(game, 1, 0, 0));
        game.removeQueuedAction(1);
        assertFalse(BattlefieldCityOrderMarkers.isMarked(game, 1, 0));
    }

    @Test public void preparedRocketAndShipMarkersTrackCarrierAndStrikeMultiplicity() {
        GameState rocket = new GameState(1, 67L, false);
        rocket.getHuman().getWeapons().get("warhead1").inStock = 1;
        rocket.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(rocket.configurePreparedCarrier("rocket1"));
        assertTrue(rocket.acceptSelectedAction());
        assertFalse(BattlefieldCityOrderMarkers.isMarked(rocket, 1, 0));
        rocket.selectAction(GameState.Action.RESOURCES);
        assertTrue(rocket.acceptSelectedAction());
        rocket.confirmTurn();
        rocket.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(rocket.selectBattlefieldCity(1, 0));
        assertTrue(rocket.configurePreparedLaunch(1, 0, "warhead1"));
        assertEquals(Icon.ROCKET, BattlefieldCityOrderMarkers.iconAt(rocket, 1, 0, 0));
        assertTrue(rocket.acceptSelectedAction());
        assertEquals(Icon.ROCKET, BattlefieldCityOrderMarkers.iconAt(rocket, 1, 0, 0));

        GameState ship = new GameState(1, 68L, false);
        ship.getHuman().getWeapons().get("ship").active = true;
        ship.getHuman().getWeapons().get("ship").inStock = 1;
        ship.getHuman().getWeapons().get("warhead1").active = true;
        ship.getHuman().getWeapons().get("warhead1").inStock = 3;
        ship.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(ship.configurePreparedCarrier("ship"));
        assertTrue(ship.acceptSelectedAction());
        ship.selectAction(GameState.Action.SKIP);
        assertTrue(ship.acceptSelectedAction());
        ship.confirmTurn();
        ship.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(ship.configurePreparedShipStrike(1, 0, "warhead1"));
        assertTrue(ship.configurePreparedShipStrike(1, 0, "warhead1"));
        assertTrue(ship.configurePreparedShipStrike(1, 0, "warhead1"));
        assertEquals(3, BattlefieldCityOrderMarkers.iconCount(ship, 1, 0));
        for (int i = 0; i < 3; i++)
            assertEquals(Icon.SPECIAL, BattlefieldCityOrderMarkers.iconAt(ship, 1, 0, i));
        assertTrue(ship.acceptSelectedAction());
        assertEquals(3, BattlefieldCityOrderMarkers.iconCount(ship, 1, 0));
        ship.removeQueuedAction(0);
        assertFalse(BattlefieldCityOrderMarkers.isMarked(ship, 1, 0));
    }

    @Test public void emptyCityAndInvalidAddressHaveNoMarker() {
        GameState game = new GameState(1, 69L, false);
        game.getPlayerState(1).getCity(0).setPopulationMillions(0);
        game.selectAction(GameState.Action.ATTACK);
        assertFalse(game.selectBattlefieldCity(1, 0));
        assertFalse(BattlefieldCityOrderMarkers.isMarked(game, 1, 0));
        assertNull(BattlefieldCityOrderMarkers.iconAt(game, -1, 0, 0));
    }
}
