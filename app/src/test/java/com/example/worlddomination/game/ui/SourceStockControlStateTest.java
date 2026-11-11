package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source Btn_stock state and visible count at selection boundaries. */
public final class SourceStockControlStateTest {
    private GameState game;
    private SourceActionMenuState menu;

    @Before public void create() {
        game = new GameState(1, 17L);
        menu = new SourceActionMenuState();
    }

    @Test public void idleUnresearchedAndBioloadedStockKeepSourceStates() {
        assertEquals(SourceStockControlState.UNAVAILABLE, state("soldier1"));
        assertEquals(SourceStockControlState.UNAVAILABLE,
                SourceStockControlState.state(game, menu, "not-a-weapon"));
        assertEquals(-1, count("not-a-weapon"));
        GameState.WeaponState unresearched = game.getHuman().getWeapons().get("soldier4");
        unresearched.active = false;
        unresearched.inStock = 1;
        assertEquals(SourceStockControlState.UNAVAILABLE, state("soldier4"));
        assertEquals(-1, count("soldier4"));
        stock("soldier1", 2);
        game.getHuman().getWeapons().get("soldier1").bioloaded = true;
        begin(GameState.Action.ATTACK);
        assertEquals("Biological marker does not replace Btn_stock's active/stock predicates",
                SourceStockControlState.ELIGIBLE, state("soldier1"));
        assertEquals(2, count("soldier1"));
        assertEquals(SourceStockControlState.INACTIVE, state("soldier4"));
        assertTrue(SourceStockControlState.usesGrayLabel(state("soldier4")));
        assertEquals(SourceStockControlState.UNAVAILABLE_SHAPE,
                SourceStockControlState.shapeId(SourceStockControlState.INACTIVE));
    }

    @Test public void attackEligibilitySpyGateAndPreviewUse() {
        stock("soldier1", 2);
        stock("spy2", 1);
        begin(GameState.Action.ATTACK);
        assertEquals(SourceStockControlState.ELIGIBLE, state("soldier1"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("spy2"));
        game.getHuman().spyReadiness = 50;
        assertEquals(SourceStockControlState.ELIGIBLE, state("spy2"));
        assertTrue(menu.chooseEquipment(game, "soldier1"));
        assertEquals(SourceStockControlState.SELECTED, state("soldier1"));
        assertEquals(SourceStockControlState.SELECTED_SHAPE,
                SourceStockControlState.shapeId(state("soldier1")));
        assertEquals(1, count("soldier1"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("warhead1"));
        assertEquals(SourceStockControlState.ELIGIBLE_SHAPE,
                SourceStockControlState.shapeId(state("spy2")));
    }

    @Test public void defenseUsesAntiStockAndGrayInactiveLabel() {
        stock("antiAir", 1);
        GameState.WeaponState antiRocket = game.getHuman().getWeapons().get("antiRocket");
        antiRocket.active = false;
        antiRocket.inStock = 1;
        begin(GameState.Action.DEFENSE);
        assertEquals(SourceStockControlState.ELIGIBLE, state("antiAir"));
        assertEquals(SourceStockControlState.INACTIVE, state("antiRocket"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("soldier1"));
        assertTrue(menu.chooseEquipment(game, "antiAir"));
        assertEquals(SourceStockControlState.SELECTED, state("antiAir"));
        assertEquals(0, count("antiAir"));
    }

    @Test public void queuedExhaustionChangesCountButSourceRawStockPredicateRemains() {
        stock("soldier1", 1);
        begin(GameState.Action.ATTACK);
        assertTrue(menu.chooseEquipment(game, "soldier1"));
        assertTrue(menu.chooseCity(game, 1, 0));
        assertTrue(menu.accept(game));
        assertEquals(0, game.getAvailableWeaponStock(0, "soldier1"));
        assertEquals(0, count("soldier1"));
        begin(GameState.Action.ATTACK);
        assertEquals(SourceStockControlState.ELIGIBLE, state("soldier1"));
        assertFalse("Native selection still rejects the exhausted stock",
                menu.chooseEquipment(game, "soldier1"));
        assertEquals(0, count("soldier1"));
    }

    @Test public void preparedCarrierPayloadLevelAndSpecialLoadFollowSource() {
        stock("rocket2", 1);
        stock("warhead1", 1);
        stock("warhead3", 1);
        stock("specialLoad", 1);
        begin(GameState.Action.PREPARED_ATTACK);
        assertEquals(SourceStockControlState.ELIGIBLE, state("rocket2"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("warhead1"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("specialLoad"));
        assertTrue(menu.chooseEquipment(game, "rocket2"));
        assertEquals(SourceStockControlState.SELECTED, state("rocket2"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("specialLoad"));
        assertTrue(menu.accept(game));
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertEquals("rocket2", game.getPreparedCarrierName(0));
        begin(GameState.Action.PREPARED_ATTACK);
        assertEquals(SourceStockControlState.ELIGIBLE, state("warhead1"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("warhead3"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("rocket2"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("specialLoad"));
    }

    @Test public void preparedSpacePermitsSatelliteOnlyAfterCarrierReady() {
        stock("space", 1);
        stock("specialLoad", 1);
        stock("warhead1", 1);
        begin(GameState.Action.PREPARED_ATTACK);
        assertEquals(SourceStockControlState.UNAVAILABLE, state("specialLoad"));
        assertTrue(menu.chooseEquipment(game, "space"));
        assertEquals(SourceStockControlState.UNAVAILABLE, state("specialLoad"));
        assertTrue(menu.accept(game));
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        begin(GameState.Action.PREPARED_ATTACK);
        assertEquals("space", game.getPreparedCarrierName(0));
        assertEquals(SourceStockControlState.ELIGIBLE, state("specialLoad"));
        assertEquals("Source switch checks payload level, not carrier compatibility",
                SourceStockControlState.ELIGIBLE, state("warhead1"));
        assertFalse("Native action still rejects an incompatible Space payload",
                menu.chooseEquipment(game, "warhead1"));
    }

    @Test public void shipPayloadCountSubtractsChosenStrikesWithoutAffectingRawState() {
        stock("ship", 1);
        stock("warhead1", 2);
        begin(GameState.Action.PREPARED_ATTACK);
        assertTrue(menu.chooseEquipment(game, "ship"));
        assertTrue(menu.accept(game));
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        begin(GameState.Action.PREPARED_ATTACK);
        assertEquals("ship", game.getPreparedCarrierName(0));
        assertEquals(2, count("warhead1"));
        assertTrue(menu.chooseEquipment(game, "warhead1"));
        assertTrue(menu.chooseCity(game, 1, 0));
        assertEquals(1, count("warhead1"));
        assertEquals(SourceStockControlState.ELIGIBLE, state("warhead1"));
        assertTrue(menu.chooseEquipment(game, "warhead1"));
        assertTrue(menu.chooseCity(game, 1, 1));
        assertEquals(0, count("warhead1"));
        assertEquals(SourceStockControlState.ELIGIBLE, state("warhead1"));
        assertFalse(menu.chooseEquipment(game, "warhead1"));
    }

    private void begin(GameState.Action action) {
        game.selectAction(action);
        menu.begin(game);
    }

    private void stock(String name, int quantity) {
        GameState.WeaponState weapon = game.getHuman().getWeapons().get(name);
        weapon.active = true;
        weapon.inStock = quantity;
    }

    private int state(String name) { return SourceStockControlState.state(game, menu, name); }
    private int count(String name) { return SourceStockControlState.displayedCount(game, menu, name); }
}
