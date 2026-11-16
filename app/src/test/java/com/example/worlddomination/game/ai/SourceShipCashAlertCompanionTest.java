package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

/** Synthetic source getTarget/doTurn boundaries; not a legal campaign replay. */
public final class SourceShipCashAlertCompanionTest {
    @Test public void secondSlotShipRetainsCashAlertSpyAndTargetsHuman() throws Exception {
        Scenario s = new Scenario(1000, true);
        List<OpponentAI.Decision> decisions = s.plan(GameState.Action.RESOURCES, GameState.Action.ATTACK);
        assertEquals(2, decisions.size());
        assertEquals(GameState.Action.ATTACK, decisions.get(0).action);
        assertEquals("spy3", decisions.get(0).weaponName);
        assertEquals(0, decisions.get(0).targetPlayerId);
        assertEquals(4, decisions.get(0).targetCityIndex);
        assertEquals("tech", decisions.get(0).spyBuilding);
        assertShip(decisions.get(1));
        assertEquals(2, decisions.get(1).targetPlayerId);
        assertEquals(Arrays.asList(3, 3, 3, 61, 5, 5), s.random.bounds);
    }

    @Test public void equalAndBelowCashThresholdKeepConventionalCompanion() throws Exception {
        for (int money : new int[]{208, 207}) {
            Scenario s = new Scenario(money, true);
            List<OpponentAI.Decision> decisions = s.plan(GameState.Action.RESOURCES, GameState.Action.ATTACK);
            assertEquals(2, decisions.size());
            assertEquals(GameState.Action.ATTACK, decisions.get(0).action);
            assertNull(decisions.get(0).weaponName);
            assertEquals(2, decisions.get(0).targetPlayerId);
            assertEquals(4, decisions.get(0).targetCityIndex);
            assertShip(decisions.get(1));
            assertEquals(Arrays.asList(3, 3, 3, 61), s.random.bounds);
        }
    }

    @Test public void inactiveStockedSpyDoesNotDrawCashAlert() throws Exception {
        Scenario s = new Scenario(1000, false);
        List<OpponentAI.Decision> decisions = s.plan(GameState.Action.RESOURCES, GameState.Action.ATTACK);
        assertEquals(2, decisions.size());
        assertEquals(GameState.Action.ATTACK, decisions.get(0).action);
        assertNull(decisions.get(0).weaponName);
        assertShip(decisions.get(1));
        assertEquals(Arrays.asList(3, 3, 3), s.random.bounds);
    }

    @Test public void firstSlotShipKeepsRankedResourcesAfterSpySlotRejected() throws Exception {
        Scenario s = new Scenario(1000, true);
        List<OpponentAI.Decision> decisions = s.plan(GameState.Action.ATTACK, GameState.Action.RESOURCES);
        assertEquals(2, decisions.size());
        assertShip(decisions.get(0));
        assertEquals(GameState.Action.RESOURCES, decisions.get(1).action);
        assertEquals(Arrays.asList(3, 3, 3, 61, 5, 5), s.random.bounds);
    }

    @Test public void overwrittenResearchKeepsItsReservedCash() {
        verifyReservation(GameState.Action.RESEARCH, 0.9546913652695023, false);
    }

    @Test public void overwrittenProductionKeepsItsInvalidSourceBudget() {
        verifyReservation(GameState.Action.PRODUCTION, 0.5474497043698071, true);
    }

    // Legal UI-equivalent seed-11 opening, followed by synthetic source
    // score, cash and spy overrides from ship-spy-reservation-doturn.json.
    private static void verifyReservation(GameState.Action first, double expectedNext,
                                          boolean invalidBudget) {
        GameSetup setup = new GameSetup(11L);
        setup.setDifficulty(0);
        for (int leader : new int[]{5, 9, 13, 18}) assertTrue(setup.toggleOpponent(leader));
        GameState game = new GameState(setup);
        for (int turn = 1; turn <= 20; turn++) {
            while (game.getPendingHumanDiplomacyProposal() != null)
                assertTrue(game.respondToDiplomacyProposal(false));
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue(game.acceptSelectedAction());
            game.confirmTurn();
        }
        GameState.PlayerState actor = game.getPlayerState(1);
        assertEquals("ship", actor.getPreparedCarrierName());
        assertEquals(79, actor.turnMoneyMillions);
        game.getHuman().moneyMillions = 1000;
        for (int level = 1; level <= 4; level++) {
            GameState.WeaponState spy = actor.getWeapons().get("spy" + level);
            spy.active = level == 3;
            spy.inStock = level == 3 ? 2 : 0;
        }
        actor.spyReadiness = 0;
        ReferenceRandom random = game.createPresentationRandomCopy();
        assertEquals(0.9084965058244013, random.copy().nextDouble(), 0d);
        for (int id = 4; id >= 1; id--) {
            GameState.PlayerState player = game.getPlayerState(id);
            if (player.alive) OpponentAI.scoreActions(game, player, random);
        }
        assertEquals(0.11408895711129363, random.copy().nextDouble(), 0d);
        List<OpponentAI.Decision> decisions = OpponentAI.planScoredActions(game, actor, random,
                Arrays.asList(new OpponentAI.ScoredAction(first, 100),
                        new OpponentAI.ScoredAction(GameState.Action.ATTACK, 90)));
        assertEquals(invalidBudget, actor.hasInvalidAiTurnMoney());
        if (!invalidBudget) assertEquals(29, actor.turnMoneyMillions);
        assertEquals(expectedNext, random.copy().nextDouble(), 0d);
        assertEquals(2, decisions.size());
        assertEquals(GameState.Action.ATTACK, decisions.get(0).action);
        assertEquals("spy3", decisions.get(0).weaponName);
        assertEquals(0, decisions.get(0).targetPlayerId);
        assertEquals(2, decisions.get(0).targetCityIndex);
        assertEquals("tech", decisions.get(0).spyBuilding);
        assertEquals(GameState.Action.PREPARED_ATTACK, decisions.get(1).action);
        assertEquals("ship", decisions.get(1).weaponName);
        assertEquals(2, decisions.get(1).targetPlayerId);
    }

    private static void assertShip(OpponentAI.Decision decision) {
        assertEquals(GameState.Action.PREPARED_ATTACK, decision.action);
        assertEquals("ship", decision.weaponName);
        assertFalse(decision.prepareCarrier);
        assertEquals(3, decision.shipStrikes.size());
        int[] sourceCityIndices = {1, 3, 3};
        for (int i = 0; i < sourceCityIndices.length; i++) {
            assertEquals(2, decision.shipStrikes.get(i).targetPlayerId);
            assertEquals(sourceCityIndices[i], decision.shipStrikes.get(i).cityIndex);
            assertEquals("warhead3", decision.shipStrikes.get(i).payloadName);
        }
    }
    private static final class Scenario {
        final GameState game = new GameState(0, 0L);
        final GameState.PlayerState actor = game.getPlayerState(1);
        final ScriptedRandom random = new ScriptedRandom();
        Scenario(int money, boolean spyActive) throws Exception {
            for (GameState.PlayerState player : game.getPlayers()) {
                player.alive = player.id <= 2;
                for (GameState.WeaponState weapon : player.getWeapons().values()) weapon.inStock = 0;
            }
            game.getHuman().moneyMillions = money;
            // Captured living target cities are IDs 11, 13 and 14.
            game.getPlayerState(2).getCity(0).populationMillions = 0;
            game.getPlayerState(2).getCity(2).populationMillions = 0;
            Field enemy = GameState.PlayerState.class.getDeclaredField("primaryEnemyId");
            enemy.setAccessible(true);
            enemy.setInt(actor, 2);
            actor.spyReadiness = 0;
            actor.getWeapons().get("spy1").active = true;
            actor.getWeapons().get("spy1").inStock = spyActive ? 1 : 0;
            actor.getWeapons().get("spy3").active = spyActive;
            actor.getWeapons().get("spy3").inStock = 2;
            actor.getWeapons().get("warhead3").active = true;
            actor.getWeapons().get("warhead3").inStock = 3;
            actor.getWeapons().get("ship").active = true;
            Field field = GameState.PlayerState.class.getDeclaredField("preparedCarrierName");
            field.setAccessible(true);
            field.set(actor, "ship");
        }
        List<OpponentAI.Decision> plan(GameState.Action first, GameState.Action second) {
            return OpponentAI.planScoredActions(game, actor, random, Arrays.asList(
                    new OpponentAI.ScoredAction(first, 100),
                    new OpponentAI.ScoredAction(second, 90)));
        }
    }
    private static final class ScriptedRandom extends Random {
        final List<Integer> bounds = new ArrayList<>();
        final int[] values = {2, 1, 1, 28, 0, 1};
        @Override public int nextInt(int bound) {
            int index = bounds.size();
            bounds.add(bound);
            assertTrue("unexpected RNG draw", index < values.length);
            assertTrue("scripted RNG value outside bound", values[index] < bound);
            return values[index];
        }
    }
}
