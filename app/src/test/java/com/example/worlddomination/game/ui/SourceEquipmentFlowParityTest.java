package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Boundary cases verified through the reference's actual stock buttons and turn resolver. */
public final class SourceEquipmentFlowParityTest {
    private GameState game;
    private SourceActionMenuState menu;
    @Before public void create() { game = new GameState(1, 17L); menu = new SourceActionMenuState(); }
    private void begin(GameState.Action action) { game.selectAction(action); menu.begin(game); }
    private void stock(String name, int count) {
        GameState.WeaponState weapon = game.getHuman().getWeapons().get(name);
        weapon.active = true; weapon.inStock = count;
    }
    @Test public void tappingSelectedEquipmentAgainClearsWeaponAndTarget() {
        stock("soldier1", 2); begin(GameState.Action.ATTACK);
        assertTrue(menu.chooseEquipment(game, "soldier1")); assertTrue(menu.chooseCity(game, 1, 0));
        assertTrue(menu.chooseEquipment(game, "soldier1"));
        assertEquals("attackSelect", menu.getKey()); assertNull(menu.getSelectedEquipment());
        assertFalse(menu.accept(game)); assertEquals(-1, game.getSelectedMapCityIndex());
    }
    @Test public void switchingEquipmentRequiresSelectingTheTargetAgain() {
        stock("soldier1", 1); stock("soldier2", 1); begin(GameState.Action.ATTACK);
        assertTrue(menu.chooseEquipment(game, "soldier1")); assertTrue(menu.chooseCity(game, 1, 0));
        assertTrue(menu.chooseEquipment(game, "soldier2")); assertEquals("attack1", menu.getKey());
        assertFalse(menu.isAcceptVisible()); assertTrue(menu.chooseCity(game, 2, 1));
        assertEquals("attack2-soldier2", menu.getKey());
    }
    @Test public void carrierCanBePreparedBeforePayloadStockExists() {
        for (GameState.WeaponState weapon : game.getHuman().getWeapons().values()) weapon.inStock = 0;
        stock("rocket1", 1); begin(GameState.Action.PREPARED_ATTACK);
        assertTrue(menu.chooseEquipment(game, "rocket1")); assertTrue(menu.accept(game));
        game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction());
        game.confirmTurn(); assertEquals("rocket1", game.getPreparedCarrierName(0));
        assertEquals(0, game.getWeaponStock(0, "rocket1"));
    }
    private void readyShip(int payloadStock) {
        stock("ship", 1); stock("warhead1", payloadStock);
        begin(GameState.Action.PREPARED_ATTACK); assertTrue(menu.chooseEquipment(game, "ship"));
        assertTrue(menu.accept(game)); game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction()); game.confirmTurn();
        begin(GameState.Action.PREPARED_ATTACK);
    }
    @Test public void oneOrTwoShipPayloadsCanBeAcceptedAndResolvedLikeSource() {
        for (int count = 1; count <= 2; count++) {
            create(); readyShip(3);
            for (int city = 0; city < count; city++) {
                assertTrue(menu.chooseEquipment(game, "warhead1"));
                assertTrue(menu.chooseCity(game, 1, city));
            }
            assertEquals("ship1-" + count, menu.getKey()); assertTrue(menu.isAcceptVisible());
            assertTrue(menu.accept(game));
            game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction());
            game.confirmTurn(); assertNull(game.getPreparedCarrierName(0));
            assertEquals(3 - count, game.getWeaponStock(0, "warhead1"));
        }
    }
    @Test public void shipCannotSelectPayloadStockAlreadyAssignedToItsOtherCities() {
        readyShip(1); assertTrue(menu.chooseEquipment(game, "warhead1"));
        assertTrue(menu.chooseCity(game, 1, 0));
        assertFalse(menu.chooseEquipment(game, "warhead1"));
        assertEquals("ship1-1", menu.getKey()); assertTrue(menu.isAcceptVisible());
    }
    @Test public void idleEquipmentSelectsOnlyItsRelevantSourceAction() {
        stock("soldier1", 1); stock("rocket1", 1);
        assertEquals(GameState.Action.ATTACK, SourceActionMenuState.actionForEquipment(game, "soldier1"));
        assertEquals(GameState.Action.PREPARED_ATTACK, SourceActionMenuState.actionForEquipment(game, "rocket1"));
        assertEquals(GameState.Action.DEFENSE, SourceActionMenuState.actionForEquipment(game, "antiAir"));
        assertNull(SourceActionMenuState.actionForEquipment(game, "warhead1"));
        begin(GameState.Action.RESOURCES);
        assertNull(SourceActionMenuState.actionForEquipment(game, "soldier1"));
    }
    @Test public void lastSelectedUnitCannotBeToggledOffAsAnAdditionalAvailableUnit() {
        stock("soldier1", 1); begin(GameState.Action.ATTACK);
        assertTrue(menu.chooseEquipment(game, "soldier1")); assertTrue(menu.chooseCity(game, 1, 0));
        assertEquals(0, menu.availableStock(game, "soldier1"));
        assertFalse(menu.chooseEquipment(game, "soldier1"));
        assertEquals("soldier1", menu.getSelectedEquipment()); assertFalse(menu.accept(game));
    }
    @Test public void visibleStockMovesFromPreviewToQueuedReservationWithoutDoubleCounting() {
        stock("soldier1", 2); begin(GameState.Action.ATTACK);
        assertEquals(2, menu.availableStock(game, "soldier1"));
        assertTrue(menu.chooseEquipment(game, "soldier1"));
        assertEquals(1, menu.availableStock(game, "soldier1"));
        assertTrue(menu.chooseCity(game, 1, 0)); assertTrue(menu.accept(game));
        assertEquals(1, menu.availableStock(game, "soldier1"));
        assertEquals(2, game.getWeaponStock(0, "soldier1"));
        game.removeQueuedAction(0); assertEquals(2, menu.availableStock(game, "soldier1"));
    }

}
