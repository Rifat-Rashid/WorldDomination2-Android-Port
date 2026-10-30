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
    public void eachUsedSatelliteDoublesTheScoreAndFlagsAreAddedAfterwards() {
        FinalScoreBreakdown score = FinalScoreBreakdown.calculate(125, 2, 3);

        assertEquals(125L, score.getBaseScore());
        assertEquals(2, score.getSatelliteCount());
        assertEquals(3, score.getFlagBonusCount());
        assertEquals(750_500L, score.getFinalScore());
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
