package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Boundary tests for the source AI's incoming diplomacy decisions. */
public final class AiDiplomacyResponseRulesTest {
    @Test
    public void offersUseStrictRegardAndStrengthComparisonsAndLowCashBonus() {
        assertFalse("regard 50 and equal strength do not meet the strict offer threshold",
                decide(0, 50, 100, 100, 100, 100, 100, 100, 50));
        assertTrue("regard above 50, weaker strength, and larger population favor acceptance",
                decide(0, 50.01, 99, 100, 99, 100, 100, 0, 90));
        assertTrue("the source low-cash bonus can turn a tied offer into acceptance",
                decide(0, 50, 101, 100, 100, 100, 29, 0, 90));
    }

    @Test
    public void offersAreNotRejectedForRecipientTurnBudget() {
        assertTrue(decide(2, 50, 100, 100, 100, 100, 100, 0, 90));
    }

    @Test
    public void demandsRequireAWinningSourceScoreAndEnoughTurnFunds() {
        assertTrue("funds equal to the demand amount are sufficient",
                decide(3, 51, 100, 150, 100, 101, 100, 10, 10));
        assertFalse("one million below the demand amount is insufficient",
                decide(3, 51, 100, 150, 100, 101, 100, 9, 10));
        assertTrue("the middle demand tier accepts at its exact 50 million cost",
                decide(4, 51, 100, 150, 100, 101, 100, 50, 50));
        assertFalse("the middle demand tier rejects below its 50 million cost",
                decide(4, 51, 100, 150, 100, 101, 100, 49, 50));
    }

    @Test
    public void demandOptionsUseRoundedPenaltyAndStrictPopulationStrengthEdges() {
        assertFalse("option five rounds its penalty up and equal population adds rejection",
                decide(5, 50, 50, 101, 100, 100, 100, 90, 90));
        assertFalse("equal strength and regard 50 do not favor a demand",
                decide(3, 50, 150, 150, 100, 100, 100, 90, 10));
    }

    private static boolean decide(int optionIndex,
                                  double regard,
                                  int receiverStrength,
                                  int proposerStrength,
                                  int receiverPopulation,
                                  int proposerPopulation,
                                  int receiverMoney,
                                  int receiverTurnMoney,
                                  int amountMillions) {
        return AiDiplomacyResponseRules.shouldAccept(regard, receiverStrength,
                proposerStrength, receiverPopulation, proposerPopulation,
                receiverMoney, receiverTurnMoney, optionIndex, amountMillions);
    }
}
