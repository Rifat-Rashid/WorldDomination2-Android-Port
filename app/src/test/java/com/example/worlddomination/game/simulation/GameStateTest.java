package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class GameStateTest {
    @Test
    public void normalResourcesAndMassMediaResolveOneTurn() {
        GameState state = new GameState(1);
        assertEquals(65, state.getHuman().moneyMillions);
        assertEquals(450, state.getHuman().populationMillions);
        assertEquals(60, state.getHuman().religion);
        assertEquals(41, state.getResourceIncomeMillions());
        assertEquals(30, state.getMassMediaGain());

        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(true, state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertEquals(true, state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(1, state.getTurn());
        assertEquals(106, state.getHuman().moneyMillions);
        assertEquals(90, state.getHuman().religion);
        assertEquals(0, state.getQueuedActionCount());
        assertNull(state.getSelectedAction());
    }

    @Test
    public void resourceYieldUsesDifficultyDividerAndSourceRounding() {
        assertEquals(56, new GameState(0).getResourceIncomeMillions());
        assertEquals(41, new GameState(1).getResourceIncomeMillions());
        assertEquals(31, new GameState(2).getResourceIncomeMillions());
    }

    @Test
    public void sourceTurnRequiresTwoActions() {
        GameState state = new GameState(1);
        int funds = state.getHuman().moneyMillions;
        int influence = state.getHuman().religion;
        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        try {
            state.confirmTurn();
            fail("A source turn needs two action slots");
        } catch (IllegalStateException expected) {
            assertEquals(0, state.getTurn());
        }
        assertEquals(funds, state.getHuman().moneyMillions);
        assertEquals(influence, state.getHuman().religion);
        assertEquals(1, state.getQueuedActionCount());
    }

    @Test
    public void tappingAQueuedResourcesActionRemovesItForRequeue() {
        GameState state = new GameState(1);
        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        assertEquals(1, state.getQueuedActionCount());

        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(0, state.getQueuedActionCount());
        assertNull(state.getSelectedAction());

        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(true, state.acceptSelectedAction());
        assertEquals(1, state.getQueuedActionCount());
    }

    @Test
    public void unportedActionsCannotBeSelectedOrQueued() {
        GameState state = new GameState(1);
        state.selectAction(GameState.Action.ATTACK);
        assertNull(state.getSelectedAction());
        assertEquals(0, state.getQueuedActionCount());

        state.selectAction(GameState.Action.DIPLOMACY);
        assertNull(state.getSelectedAction());
        assertEquals(0, state.getQueuedActionCount());
    }

    @Test
    public void cancellingCurrentSelectionPreservesAcceptedAction() {
        GameState state = new GameState(1);
        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(true, state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);

        state.cancelSelection();

        assertNull(state.getSelectedAction());
        assertEquals(1, state.getQueuedActionCount());
        assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
    }

    @Test
    public void hardMassMediaCannotRaiseInfluenceAboveOneHundred() {
        GameState state = new GameState(2);
        for (int turn = 0; turn < 5; turn++) {
            state.selectAction(GameState.Action.MASS_MEDIA);
            assertEquals(true, state.acceptSelectedAction());
            state.selectAction(GameState.Action.RESOURCES);
            assertEquals(true, state.acceptSelectedAction());
            state.confirmTurn();
        }

        assertEquals(100, state.getHuman().religion);
        assertEquals(210, state.getHuman().moneyMillions);
        assertEquals(5, state.getTurn());
    }

    @Test
    public void confirmingHumanActionsDoesNotChangeOpponentStartingState() {
        GameState state = new GameState(1);
        GameState.PlayerState opponent = state.getPlayers()[1];
        int opponentMoney = opponent.moneyMillions;
        int opponentReligion = opponent.religion;

        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        state.selectAction(GameState.Action.MASS_MEDIA);
        state.acceptSelectedAction();
        state.confirmTurn();

        assertEquals(opponentMoney, opponent.moneyMillions);
        assertEquals(opponentReligion, opponent.religion);
    }

    @Test
    public void sourceDifficultyTablesInitializeHumanAndOpponent() {
        int[] humanMoney = {80, 65, 55};
        int[] humanPopulation = {500, 450, 400};
        int[] humanReligion = {80, 60, 20};
        int[] enemyMoney = {50, 65, 60};
        int[] enemyReligion = {30, 40, 60};

        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameState state = new GameState(difficulty);
            GameState.PlayerState[] players = state.getPlayers();
            assertEquals(humanMoney[difficulty], players[0].moneyMillions);
            assertEquals(humanPopulation[difficulty], players[0].populationMillions);
            assertEquals(humanReligion[difficulty], players[0].religion);
            assertEquals(enemyMoney[difficulty], players[1].moneyMillions);
            assertEquals(450, players[1].populationMillions);
            assertEquals(enemyReligion[difficulty], players[1].religion);
        }
    }

    @Test
    public void difficultyOutsideSourceTableIsRejected() {
        for (int difficulty : new int[]{-1, 3}) {
            try {
                new GameState(difficulty);
                fail("Unsupported difficulty should be rejected: " + difficulty);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Difficulty"));
            }
        }
    }

    @Test
    public void queuedResourcesDoNotChangeFundsBeforeTurnConfirmation() {
        GameState state = new GameState(1);
        int startingFunds = state.getHuman().moneyMillions;

        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        assertEquals(startingFunds, state.getHuman().moneyMillions);
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertEquals(startingFunds, state.getHuman().moneyMillions);
    }

    @Test
    public void clearingTurnActionsRemovesQueuedAndPendingActions() {
        GameState state = new GameState(1);
        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        state.selectAction(GameState.Action.MASS_MEDIA);

        state.clearQueuedActions();

        assertEquals(0, state.getQueuedActionCount());
        assertNull(state.getSelectedAction());
        assertEquals(65, state.getHuman().moneyMillions);
        assertEquals(60, state.getHuman().religion);
    }
}
