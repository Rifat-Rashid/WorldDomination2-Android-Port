package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OpponentAITest {
    @Test
    public void specialOpponentUsesItsPrimaryEnemyToBreakEqualHostilityTies() {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            if (candidate.getPlayerState(1).isSpecialPerson()) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should find a special first opponent", state);

        GameState.PlayerState special = state.getPlayerState(1);
        assertEquals(70, special.getRegardToward(0));
        assertEquals(special.getPrimaryEnemyId(),
                OpponentAI.mostHostileTarget(state, special, false).id);
    }

    @Test
    public void everyLivingOpponentPlansTwoActionsDuringTurnResolution() {
        GameState state = new GameState(1, 901L);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(1, state.getTurn());
        boolean opponentUsedAnotherSystem = false;
        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState opponent = state.getPlayerState(playerId);
            assertEquals("nation " + playerId, 2, opponent.getLastTurnActions().size());
            assertFalse(opponent.getLastTurnActions().contains(null));
            assertTrue(opponent.turnMoneyMillions >= 0);
            for (GameState.Action action : opponent.getLastTurnActions()) {
                opponentUsedAnotherSystem |= action != GameState.Action.MASS_MEDIA;
            }
        }
        assertTrue(opponentUsedAnotherSystem);
    }

    @Test
    public void seededOpponentTurnsAreReproducibleAcrossActionAndInventoryState() {
        GameState first = completeOneHumanTurn(712L);
        GameState second = completeOneHumanTurn(712L);

        for (int playerId = 0; playerId < 5; playerId++) {
            GameState.PlayerState left = first.getPlayerState(playerId);
            GameState.PlayerState right = second.getPlayerState(playerId);
            assertEquals(left.getLastTurnActions(), right.getLastTurnActions());
            assertEquals(left.moneyMillions, right.moneyMillions);
            assertEquals(left.religion, right.religion);
            assertEquals(left.populationMillions, right.populationMillions);
            for (String weaponName : left.getWeapons().keySet()) {
                assertEquals(left.getWeaponStockByName(weaponName),
                        right.getWeaponStockByName(weaponName));
            }
            assertEquals(left.getInventedTechnologies(), right.getInventedTechnologies());
        }
    }

    @Test
    public void plannedSpendingMatchesSelectedProductionAndResearchOrders() {
        GameState state = new GameState(1, 52L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        int startingFunds = opponent.turnMoneyMillions;
        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, opponent,
                new Random(22L));
        int reserved = 0;
        for (OpponentAI.Decision decision : decisions) {
            reserved += decision.reservedMillions;
            int productionBudget = 0;
            for (int budget : decision.productionBudgets.values()) {
                productionBudget += budget;
            }
            if (decision.action == GameState.Action.PRODUCTION) {
                assertEquals(decision.reservedMillions, productionBudget);
            }
            if (decision.action == GameState.Action.RESEARCH) {
                int researchCost = 0;
                for (String technology : decision.technologies) {
                    researchCost += TechnologyCatalog.byName(technology).costMillions;
                }
                assertEquals(decision.reservedMillions, researchCost);
            }
        }
        assertEquals(2, decisions.size());
        assertEquals(startingFunds - reserved, opponent.turnMoneyMillions);
        assertTrue(opponent.turnMoneyMillions >= 0);
    }

    @Test
    public void plannerCanPrepareACompatibleCarrierWithoutDuplicatingTheSlot() {
        GameState state = new GameState(1, 53L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        opponent.moneyMillions = 0;
        opponent.turnMoneyMillions = 0;
        opponent.getWeapons().get("rocket1").inStock = 1;
        opponent.getWeapons().get("warhead1").inStock = 1;

        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, opponent,
                new Random(22L));
        int preparedActions = 0;
        for (OpponentAI.Decision decision : decisions) {
            if (decision.action == GameState.Action.PREPARED_ATTACK) {
                preparedActions++;
                assertTrue(decision.prepareCarrier);
                assertEquals("rocket1", decision.weaponName);
                assertNull(decision.payloadName);
            }
        }
        assertEquals(2, decisions.size());
        assertTrue("planner should use the available strike option", preparedActions > 0);
        assertEquals(1, preparedActions);
    }

    @Test
    public void opponentPreparesAndLaunchesAStockedRocketAcrossTurns() {
        GameState state = new GameState(1, 54L);
        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState opponent = state.getPlayerState(playerId);
            opponent.moneyMillions = 0;
            opponent.turnMoneyMillions = 0;
            opponent.getWeapons().get("rocket1").inStock = 1;
            opponent.getWeapons().get("warhead1").inStock = 1;
        }

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState opponent = state.getPlayerState(playerId);
            assertEquals("rocket1", opponent.getPreparedCarrierName());
            assertTrue(opponent.getLastTurnActions().contains(
                    GameState.Action.PREPARED_ATTACK));
            assertEquals(0, state.getWeaponStock(playerId, "rocket1"));
            assertEquals(1, state.getWeaponStock(playerId, "warhead1"));
        }

        int antiRocketBefore = state.getWeaponStock(0, "antiRocket");
        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiRocket"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState opponent = state.getPlayerState(playerId);
            assertTrue(opponent.getLastTurnActions().contains(
                    GameState.Action.PREPARED_ATTACK));
            assertNull(opponent.getPreparedCarrierName());
            assertEquals(0, state.getWeaponStock(playerId, "warhead1"));
        }
        assertEquals(antiRocketBefore - 1, state.getWeaponStock(0, "antiRocket"));
    }

    @Test
    public void opponentShipLaunchConsumesThreePayloadUnitsAcrossItsStrikeArray() {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            GameState.PlayerState firstOpponent = candidate.getPlayerState(1);
            if (!firstOpponent.isSpecialPerson() && !firstOpponent.isSuicidePerson()) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should find a regular first opponent", state);
        GameState.PlayerState attacker = state.getPlayerState(1);
        attacker.getWeapons().get("ship").active = true;
        attacker.getWeapons().get("ship").inStock = 1;
        attacker.getWeapons().get("warhead1").inStock = 2;
        attacker.getWeapons().get("bioload1").inStock = 1;

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals("ship", attacker.getPreparedCarrierName());

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertNull(attacker.getPreparedCarrierName());
        assertEquals(0, attacker.getWeaponStockByName("warhead1"));
        assertEquals(0, attacker.getWeaponStockByName("bioload1"));
    }

    @Test
    public void biologicalStrikeBlocksHumanActionsAndRestoresTemporaryWeaponLocks() {
        GameState state = new GameState(1, 55L);
        GameState.PlayerState attacker = state.getPlayerState(1);
        attacker.moneyMillions = 0;
        attacker.turnMoneyMillions = 0;
        attacker.getWeapons().get("rocket1").inStock = 1;
        attacker.getWeapons().get("warhead1").inStock = 0;
        attacker.getWeapons().get("bioload1").inStock = 1;
        for (GameState.WeaponState weapon : state.getHuman().getWeapons().values()) {
            if (weapon.definition.productionGroup != 8) {
                weapon.active = true;
            }
        }

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals("rocket1", attacker.getPreparedCarrierName());

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        GameState.PlayerState human = state.getHuman();
        assertTrue(human.isBioloaded());
        boolean contaminatedCityFound = false;
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            contaminatedCityFound |= human.getCity(cityIndex).bioloaded;
        }
        assertTrue(contaminatedCityFound);
        assertFalse(human.getBioloadedActions().isEmpty());
        GameState.Action blocked = human.getBioloadedActions().iterator().next();
        state.selectAction(blocked);
        assertNull(state.getSelectedAction());
        assertTrue(state.getLatestMessage().contains("biological contamination"));
        String disabledWeapon = null;
        for (GameState.WeaponState weapon : human.getWeapons().values()) {
            if (weapon.bioloaded) {
                assertFalse(weapon.active);
                disabledWeapon = weapon.definition.name;
                break;
            }
        }
        assertTrue("source hazard attempts should disable active weapon stock",
                disabledWeapon != null);

        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiAir"));
        assertTrue(state.acceptSelectedAction());
        queueAvailableActions(state);
        state.confirmTurn();

        assertFalse(human.isBioloaded());
        assertTrue(human.getBioloadedActions().isEmpty());
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            assertFalse(human.getCity(cityIndex).bioloaded);
        }
        assertFalse(human.getWeapons().get(disabledWeapon).bioloaded);
        assertTrue(human.getWeapons().get(disabledWeapon).active);
    }

    @Test
    public void biologicalEffectsAlsoKeepContaminatedOpponentFromChoosingDisabledActions() {
        GameState state = new GameState(1, 552L);
        GameState.PlayerState human = state.getHuman();
        GameState.PlayerState target = state.getPlayerState(1);
        human.getWeapons().get("rocket1").inStock = 1;
        human.getWeapons().get("bioload1").inStock = 1;
        target.getWeapons().get("antiRocket").inStock = 0;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("rocket1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedLaunch(1, 0, "bioload1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(target.isBioloaded());
        assertFalse(target.getBioloadedActions().isEmpty());
        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, target, new Random(553L));
        for (OpponentAI.Decision decision : decisions) {
            assertFalse("AI selected bio-disabled action " + decision.action,
                    target.isActionBioloaded(decision.action));
        }
    }

    @Test
    public void repeatedTurnsKeepOpponentFundsAndCitiesWithinValidRanges() {
        GameState state = new GameState(2, 119L);
        for (int turn = 0; turn < 15 && state.getMatchStatus() == GameState.MatchStatus.ONGOING;
             turn++) {
            queueAvailableActions(state);
            state.confirmTurn();

            for (int playerId = 0; playerId < 5; playerId++) {
                GameState.PlayerState player = state.getPlayerState(playerId);
                assertTrue(player.moneyMillions >= 0);
                int cityPopulation = 0;
                for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
                    assertTrue(player.getCity(cityIndex).populationMillions >= 0);
                    cityPopulation += player.getCity(cityIndex).populationMillions;
                }
                assertEquals(cityPopulation, player.populationMillions);
            }
        }
    }

    private static void queueAvailableActions(GameState state) {
        GameState.Action[] candidates = {
                GameState.Action.RESOURCES,
                GameState.Action.MASS_MEDIA,
                GameState.Action.PRODUCTION,
                GameState.Action.PROPAGANDA,
                GameState.Action.DIPLOMACY,
                GameState.Action.RESEARCH
        };
        for (GameState.Action action : candidates) {
            if (state.getQueuedActionCount() >= 2) {
                return;
            }
            if (state.isActionBioloaded(0, action)) {
                continue;
            }
            state.selectAction(action);
            if (action == GameState.Action.PRODUCTION) {
                state.configureProduction("soldier", 0);
            } else if (action == GameState.Action.PROPAGANDA) {
                boolean configured = false;
                for (int targetId = 1; targetId < 5 && !configured; targetId++) {
                    configured = state.configurePropaganda(targetId, 0);
                }
                if (!configured) {
                    state.cancelSelection();
                    continue;
                }
            } else if (action == GameState.Action.DIPLOMACY) {
                // The default proposal is affordable and targets the selected living island.
            } else if (action == GameState.Action.RESEARCH) {
                List<String> available = state.getAvailableResearch();
                if (available.isEmpty() || !state.selectResearchTechnology(available.get(0))) {
                    state.cancelSelection();
                    continue;
                }
            }
            if (!state.acceptSelectedAction()) {
                state.cancelSelection();
            }
        }
    }

    private static GameState completeOneHumanTurn(long seed) {
        GameState state = new GameState(1, seed);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        return state;
    }
}
