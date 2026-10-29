package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        try {
            state.confirmTurn();
            fail("A source turn needs two action slots");
        } catch (IllegalStateException expected) {
            assertEquals(0, state.getTurn());
        }
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
}
