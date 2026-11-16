package com.example.worlddomination.game.simulation;

import org.junit.Test;
import static org.junit.Assert.*;

/** Source preparation location survives a later launch's independently ordered actions. */
public final class PreparedAirfieldLocationTest {
    @Test public void legalSoldierAndPreparationReserveSlotTwoAcrossLaunch() {
        for (boolean prepareFirst : new boolean[]{false, true}) {
            GameState game = legalPlaneStock();
            reject(game);
            if (prepareFirst) prepare(game);
            attack(game);
            if (!prepareFirst) prepare(game);
            game.confirmTurn();
            assertEquals(2, building(game, "airfield"));
            reject(game);
            launch(game); resources(game); game.confirmTurn();
            assertEquals(2, building(game, "airfield"));
            assertEquals(1, building(game, "resources"));
            assertEquals(2, planeEvent(game).actionBuildingSlot);
        }
    }

    @Test public void legalResourcesAndPreparationKeepSlotOneAndCancelReprepares() {
        GameState game = legalPlaneStock();
        reject(game); resources(game); prepare(game); game.confirmTurn();
        assertEquals(1, building(game, "airfield"));
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.cancelPreparedAttack());
        // Normal production supplies multiple planes, so another legal
        // preparation can immediately select a different physical slot.
        reject(game); attack(game); prepare(game); game.confirmTurn();
        assertEquals(2, building(game, "airfield"));
        reject(game); launch(game); resources(game); game.confirmTurn();
        assertEquals(2, planeEvent(game).actionBuildingSlot);
    }

    @Test public void legalResourcesAndPreparationLaunchFromSlotOne() {
        GameState game = legalPlaneStock();
        reject(game); prepare(game); resources(game); game.confirmTurn();
        assertEquals(1, building(game, "airfield"));
        reject(game); launch(game); resources(game); game.confirmTurn();
        assertEquals(1, planeEvent(game).actionBuildingSlot);
        assertEquals(2, building(game, "resources"));
    }

    @Test public void syntheticInvalidPreparationDoesNotRetainSlot() {
        GameState game = new GameState(1, 0L, false);
        GameState.WeaponState plane = game.getHuman().getWeapons().get("plane1");
        GameState.WeaponState payload = game.getHuman().getWeapons().get("bioload1");
        GameState.WeaponState soldier = game.getHuman().getWeapons().get("soldier1");
        plane.active = true; plane.inStock = 1;
        payload.active = true; payload.inStock = 1;
        soldier.active = true; soldier.inStock = 1;
        attack(game); prepare(game);
        // Explicit negative boundary: the queued carrier becomes unavailable before resolution.
        plane.inStock = 0;
        game.confirmTurn();
        assertNull(game.getHuman().getPreparedCarrierName());
        plane.inStock = 1;
        prepare(game); resources(game); game.confirmTurn();
        assertEquals(1, building(game, "airfield"));
        launch(game); resources(game); game.confirmTurn();
        assertEquals(1, planeEvent(game).actionBuildingSlot);
    }

    @Test public void syntheticPlaneTwoUsesSameFixedAirfield() {
        GameState game = new GameState(1, 0L, false);
        GameState.WeaponState plane = game.getHuman().getWeapons().get("plane2");
        GameState.WeaponState payload = game.getHuman().getWeapons().get("bioload1");
        GameState.WeaponState soldier = game.getHuman().getWeapons().get("soldier1");
        plane.active = true; plane.inStock = 1;
        payload.active = true; payload.inStock = 1;
        soldier.active = true; soldier.inStock = 1;
        attack(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("plane2"));
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertEquals(2, building(game, "airfield"));
        launch(game); resources(game); game.confirmTurn();
        assertEquals(2, building(game, "airfield"));
        assertEquals(1, building(game, "resources"));
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            if (event.attackerPlayerId == 0 && "plane2".equals(event.weaponName)) {
                assertEquals(2, event.actionBuildingSlot);
                return;
            }
        }
        fail("Missing human plane2 event");
    }

    @Test public void unusedPreparationExpiresBeforeFreshPreparation() {
        GameState game = legalPlaneStock();
        reject(game); attack(game); prepare(game); game.confirmTurn();
        assertEquals(2, building(game, "airfield"));
        reject(game); resources(game); resources(game); game.confirmTurn();
        assertNull(game.getHuman().getPreparedCarrierName());
        reject(game); prepare(game); resources(game); game.confirmTurn();
        assertEquals(1, building(game, "airfield"));
        reject(game); launch(game); resources(game); game.confirmTurn();
        assertEquals(1, planeEvent(game).actionBuildingSlot);
    }

    private static GameState legalPlaneStock() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        // Produce each stock with the normal interface while only level-one
        // technologies are active, then prepare on a later turn.
        reject(game);
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 50));
        assertTrue(game.acceptSelectedAction());
        resources(game); game.confirmTurn();
        turnProduction(game, "planes");
        turnProduction(game, "bioload");
        assertTrue(game.getHuman().getWeapons().get("plane1").inStock > 0);
        assertTrue(game.getHuman().getWeapons().get("bioload1").inStock > 0);
        assertTrue(game.getHuman().getWeapons().get("soldier1").inStock > 0);
        return game;
    }
    private static void reject(GameState game) {
        while (game.getPendingHumanDiplomacyProposal() != null)
            assertTrue(game.respondToDiplomacyProposal(false));
    }
    private static void turnProduction(GameState game, String category) {
        reject(game); production(game, category); resources(game); game.confirmTurn();
    }
    private static void production(GameState game, String category) {
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction(category, 100));
        assertTrue(game.acceptSelectedAction());
    }
    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction());
    }
    private static void prepare(GameState game) {
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("plane1"));
        assertTrue(game.acceptSelectedAction());
    }
    private static void launch(GameState game) {
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 2, "bioload1"));
        assertTrue(game.acceptSelectedAction());
    }
    private static void attack(GameState game) {
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 2, "soldier1"));
        assertTrue(game.acceptSelectedAction());
    }
    private static int building(GameState game, String frame) {
        for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings())
            if (event.playerId == 0 && frame.equals(event.frameName)) return event.slot;
        throw new AssertionError("Missing human " + frame + " building");
    }
    private static GameState.CombatEvent planeEvent(GameState game) {
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents())
            if (event.attackerPlayerId == 0 && "plane1".equals(event.weaponName)) return event;
        throw new AssertionError("Missing human plane event");
    }
}
