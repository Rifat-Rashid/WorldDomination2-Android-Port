package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FinalScoreBreakdownTest {
    @Test
    public void noBonusKeepsTheActionScore() {
        FinalScoreBreakdown score = FinalScoreBreakdown.calculate(124_562, 0, 0);

        assertEquals(124_562L, score.getFinalScore());
    }

    @Test
    public void eachSatelliteDoublesAndFlagAwardsUseTheSourceRunningMultiplier() {
        FinalScoreBreakdown score = FinalScoreBreakdown.calculate(125, 2, 3);

        assertEquals(125L, score.getBaseScore());
        assertEquals(2, score.getSatelliteCount());
        assertEquals(3, score.getFlagBonusCount());
        assertEquals(1_500_500L, score.getFinalScore());
    }

    @Test
    public void flagBonusIsTriangularBecauseEachRevealAddsItsCurrentMultiplier() {
        assertEquals(250_000L, FinalScoreBreakdown.calculateFlagBonusPoints(1));
        assertEquals(750_000L, FinalScoreBreakdown.calculateFlagBonusPoints(2));
        assertEquals(1_500_000L, FinalScoreBreakdown.calculateFlagBonusPoints(3));
        assertEquals(2_500_000L, FinalScoreBreakdown.calculateFlagBonusPoints(4));
        assertEquals(0L, FinalScoreBreakdown.calculateFlagBonusPoints(-1));
        assertEquals(Long.MAX_VALUE, FinalScoreBreakdown.calculateFlagBonusPoints(
                Integer.MAX_VALUE));
    }

    @Test
    public void negativeInputsCannotCreateNegativeBonuses() {
        FinalScoreBreakdown score = FinalScoreBreakdown.calculate(-12, -3, -1);

        assertEquals(0L, score.getBaseScore());
        assertEquals(0, score.getSatelliteCount());
        assertEquals(0, score.getFlagBonusCount());
        assertEquals(0L, score.getFinalScore());
    }

    @Test
    public void exceptionallyLargeMultiplierSaturatesInsteadOfWrapping() {
        FinalScoreBreakdown score = FinalScoreBreakdown.calculate(Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, score.getFinalScore());
    }
}
