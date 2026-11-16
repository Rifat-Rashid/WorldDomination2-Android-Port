package com.example.worlddomination.game.simulation;

import org.junit.Test;
import static org.junit.Assert.*;

/** Source preparation location survives a later launch's independently ordered actions. */
public final class PreparedRocketLocationTest {
    @Test public void legalSoldierAndPreparationReserveSlotTwoAcrossLaunch() {
        for (boolean prepareFirst : new boolean[]{false, true}) {
            GameState game = legalRocketStock();
            reject(game);
            if (prepareFirst) prepare(game);
            attack(game);
            if (!prepareFirst) prepare(game);
            game.confirmTurn();
            assertEquals(2, building(game, "silo"));
            reject(game);
            launch(game); resources(game); game.confirmTurn();
            assertEquals(2, building(game, "silo"));
            assertEquals(1, building(game, "resources"));
            assertEquals(2, rocketEvent(game).actionBuildingSlot);
        }
    }

    @Test public void legalResourcesAndPreparationKeepSlotOneAndCancelReprepares() {
        GameState game = legalRocketStock();
        reject(game); resources(game); prepare(game); game.confirmTurn();
        assertEquals(1, building(game, "silo"));
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.cancelPreparedAttack());
        // Early split production supplies multiple rockets, so another legal
        // preparation can immediately select a different physical slot.
        reject(game); attack(game); prepare(game); game.confirmTurn();
        assertEquals(2, building(game, "silo"));
        reject(game); launch(game); resources(game); game.confirmTurn();
        assertEquals(2, rocketEvent(game).actionBuildingSlot);
    }

    @Test public void legalResourcesAndPreparationLaunchFromSlotOne() {
        GameState game = legalRocketStock();
        reject(game); prepare(game); resources(game); game.confirmTurn();
        assertEquals(1, building(game, "silo"));
        reject(game); launch(game); resources(game); game.confirmTurn();
        assertEquals(1, rocketEvent(game).actionBuildingSlot);
        assertEquals(2, building(game, "resources"));
    }

    @Test public void syntheticInvalidPreparationDoesNotRetainSlot() {
        GameState game = new GameState(1, 0L, false);
        GameState.WeaponState rocket = game.getHuman().getWeapons().get("rocket1");
        GameState.WeaponState payload = game.getHuman().getWeapons().get("bioload1");
        GameState.WeaponState soldier = game.getHuman().getWeapons().get("soldier1");
        rocket.active = true; rocket.inStock = 1;
        payload.active = true; payload.inStock = 1;
        soldier.active = true; soldier.inStock = 1;
        attack(game); prepare(game);
        // Explicit negative boundary: the queued carrier becomes unavailable before resolution.
        rocket.inStock = 0;
        game.confirmTurn();
        assertNull(game.getHuman().getPreparedCarrierName());
        rocket.inStock = 1;
        prepare(game); resources(game); game.confirmTurn();
        assertEquals(1, building(game, "silo"));
        launch(game); resources(game); game.confirmTurn();
        assertEquals(1, rocketEvent(game).actionBuildingSlot);
    }

    private static GameState legalRocketStock() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        // Only level-one weapons are active, so normal production cannot
        // distribute the budget to higher levels.
        reject(game);
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 50));
        assertTrue(game.configureProduction("rockets", 50));
        assertTrue(game.acceptSelectedAction());
        resources(game); game.confirmTurn();
        turnProduction(game, "bioload");
        assertTrue(game.getHuman().getWeapons().get("rocket1").inStock > 0);
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
        assertTrue(game.configurePreparedCarrier("rocket1"));
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
    private static GameState.CombatEvent rocketEvent(GameState game) {
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents())
            if (event.attackerPlayerId == 0 && "rocket1".equals(event.weaponName)) return event;
        throw new AssertionError("Missing human rocket event");
    }
}
