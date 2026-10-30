package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OpponentAITest {
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
    public void repeatedTurnsKeepOpponentFundsAndCitiesWithinValidRanges() {
        GameState state = new GameState(2, 119L);
        for (int turn = 0; turn < 15 && state.getMatchStatus() == GameState.MatchStatus.ONGOING;
             turn++) {
            state.selectAction(GameState.Action.RESOURCES);
            assertTrue(state.acceptSelectedAction());
            state.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue(state.acceptSelectedAction());
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
