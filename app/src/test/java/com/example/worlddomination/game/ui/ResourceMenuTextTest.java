package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ResourceMenuTextTest {
    @Test
    public void dialogDisplaysTheSourceStartingValuesForEveryDifficulty() {
        int[] funds = {80, 65, 55};
        int[] population = {500, 450, 400};
        int[] income = {56, 41, 31};
        ResourceMenuText text = new ResourceMenuText();
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            text.update(new GameState(difficulty, 17L));
            assertEquals("Budget: " + funds[difficulty] + ".000.000", text.getBudget());
            assertEquals(population[difficulty] + ".000.000", text.getPopulation());
            assertEquals(income[difficulty] + ".000.000", text.getIncome());
        }
    }

    @Test
    public void dialogBudgetUsesRemainingTurnFundsAfterProductionReservationAndRemoval() {
        GameState state = new GameState(1, 0L);
        ResourceMenuText text = new ResourceMenuText();
        state.selectAction(GameState.Action.PRODUCTION);
        state.configureProduction("soldier", 50);
        assertTrue(state.acceptSelectedAction());
        text.update(state);
        assertEquals("Budget: 32.000.000", text.getBudget());
        assertEquals("450.000.000", text.getPopulation());
        assertEquals("41.000.000", text.getIncome());
        state.clearQueuedActions();
        text.update(state);
        assertEquals("Budget: 65.000.000", text.getBudget());
        assertEquals(0, state.getQueuedActionCount());
    }
}
