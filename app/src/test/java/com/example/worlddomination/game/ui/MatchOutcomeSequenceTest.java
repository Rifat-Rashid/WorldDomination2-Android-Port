package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.FinalScoreBreakdown;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MatchOutcomeSequenceTest {
    @Test
    public void sourceDelaysTheEndgameByThreeSecondsAndScoreByTwoMore() {
        MatchOutcomeSequence sequence = new MatchOutcomeSequence(7L);
        sequence.begin(GameState.MatchStatus.DEFEAT,
                FinalScoreBreakdown.calculate(100L, 0, 0));

        for (int tick = 0; tick < MatchOutcomeSequence.TICKS_BEFORE_ENDGAME - 1; tick++) {
            assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        }
        assertEquals(MatchOutcomeSequence.Phase.BEFORE_ENDGAME, sequence.getPhase());
        assertFalse(sequence.isEndgameVisible());
        assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        assertEquals(MatchOutcomeSequence.Phase.INTRO, sequence.getPhase());
        assertTrue(sequence.isEndgameVisible());

        for (int tick = 0; tick < MatchOutcomeSequence.TICKS_BEFORE_SCORE - 1; tick++) {
            assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        }
        assertEquals(MatchOutcomeSequence.Phase.INTRO, sequence.getPhase());
        assertEquals(MatchOutcomeSequence.EVENT_START_SCORE_COUNTER
                        | MatchOutcomeSequence.EVENT_DEFEAT,
                sequence.tick());
        assertEquals(MatchOutcomeSequence.Phase.BASE_SCORE, sequence.getPhase());
    }

    @Test
    public void baseScoreUsesBoundedIncrementsAndStopsItsCounterAtTheSourceValue() {
        MatchOutcomeSequence sequence = sequenceAtScorePhase(
                GameState.MatchStatus.VICTORY, FinalScoreBreakdown.calculate(100L, 0, 0));

        for (int tick = 0; tick < 99; tick++) {
            assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        }
        assertEquals(99L, sequence.getScoreCount());
        assertEquals("99", sequence.getScoreText());
        assertEquals(MatchOutcomeSequence.EVENT_STOP_SCORE_COUNTER, sequence.tick());
        assertEquals(100L, sequence.getScoreCount());
        assertEquals("100", sequence.getScoreText());
        assertEquals(MatchOutcomeSequence.Phase.SATELLITE_BONUS, sequence.getPhase());
    }

    @Test
    public void zeroScoreAndNoBonusesCompleteWithoutRandomRangeOrCadenceErrors() {
        MatchOutcomeSequence sequence = sequenceAtScorePhase(
                GameState.MatchStatus.VICTORY, FinalScoreBreakdown.calculate(0L, 0, 0));

        assertEquals(MatchOutcomeSequence.EVENT_STOP_SCORE_COUNTER, sequence.tick());
        assertEquals(MatchOutcomeSequence.Phase.SATELLITE_BONUS, sequence.getPhase());
        assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        assertEquals(MatchOutcomeSequence.Phase.FLAG_BONUS, sequence.getPhase());
        assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        assertEquals(MatchOutcomeSequence.Phase.COMPLETE, sequence.getPhase());
        assertEquals(0L, sequence.getScoreCount());
        assertEquals(sequence.getFinalScore(), sequence.getScoreCount());
    }

    @Test
    public void satelliteDoublesAtTheSourceFrameAndPlaysItsCueAfterFortyOneTicks() {
        MatchOutcomeSequence sequence = sequenceAtSatellitePhase(
                GameState.MatchStatus.VICTORY, FinalScoreBreakdown.calculate(100L, 1, 0));

        int events = MatchOutcomeSequence.EVENT_NONE;
        for (int tick = 0; tick < 40; tick++) {
            events |= sequence.tick();
        }
        assertEquals(MatchOutcomeSequence.EVENT_NONE, events);
        assertEquals(MatchOutcomeSequence.EVENT_SATELLITE_BONUS, sequence.tick());
        assertEquals(1, sequence.getSatellitesRevealed());
        assertEquals(100L, sequence.getScoreCount());

        assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        assertEquals(200L, sequence.getScoreCount());
        assertFalse(sequence.isScoreVisible());
        for (int tick = 2; tick < 6; tick++) {
            assertEquals(MatchOutcomeSequence.EVENT_NONE, sequence.tick());
        }
        assertTrue(sequence.isScoreVisible());
        sequence.tick();
        sequence.tick();
        assertEquals(MatchOutcomeSequence.Phase.FLAG_BONUS, sequence.getPhase());
    }

    @Test
    public void eachRevealedSatelliteDoublesTheDisplayedBaseScoreOnce() {
        MatchOutcomeSequence sequence = sequenceAtSatellitePhase(
                GameState.MatchStatus.VICTORY, FinalScoreBreakdown.calculate(100L, 2, 0));

        tick(sequence, 41);
        assertEquals(1, sequence.getSatellitesRevealed());
        tick(sequence, 2);
        assertEquals(200L, sequence.getScoreCount());
        tick(sequence, 39);
        assertEquals(2, sequence.getSatellitesRevealed());
        tick(sequence, 2);
        assertEquals(400L, sequence.getScoreCount());
        tick(sequence, 6);
        assertEquals(MatchOutcomeSequence.Phase.FLAG_BONUS, sequence.getPhase());
        sequence.tick();
        assertEquals(MatchOutcomeSequence.Phase.COMPLETE, sequence.getPhase());
        assertEquals(sequence.getFinalScore(), sequence.getScoreCount());
    }

    @Test
    public void flagSequenceFinishesAtTheSameTriangularScoreAsTheSourceBreakdown() {
        FinalScoreBreakdown breakdown = FinalScoreBreakdown.calculate(100L, 1, 2);
        MatchOutcomeSequence sequence = sequenceAtSatellitePhase(
                GameState.MatchStatus.VICTORY, breakdown);
        for (int tick = 0; tick < 49; tick++) {
            sequence.tick();
        }
        assertEquals(MatchOutcomeSequence.Phase.FLAG_BONUS, sequence.getPhase());

        int firstFlagEvent = MatchOutcomeSequence.EVENT_NONE;
        for (int tick = 0; tick < 41; tick++) {
            firstFlagEvent |= sequence.tick();
        }
        assertEquals(MatchOutcomeSequence.EVENT_FLAG_BONUS, firstFlagEvent);
        assertEquals(1, sequence.getFlagsRevealed());
        for (int tick = 0; tick < 49; tick++) {
            sequence.tick();
        }

        assertEquals(MatchOutcomeSequence.Phase.COMPLETE, sequence.getPhase());
        assertEquals(2, sequence.getFlagsRevealed());
        assertEquals(750_200L, sequence.getScoreCount());
        assertEquals("750.200", sequence.getScoreText());
        assertEquals(breakdown.getFinalScore(), sequence.getScoreCount());
        assertTrue(sequence.isScoreVisible());
    }

    private static MatchOutcomeSequence sequenceAtScorePhase(
            GameState.MatchStatus outcome, FinalScoreBreakdown breakdown) {
        MatchOutcomeSequence sequence = new MatchOutcomeSequence(11L);
        sequence.begin(outcome, breakdown);
        tick(sequence, MatchOutcomeSequence.TICKS_BEFORE_ENDGAME);
        tick(sequence, MatchOutcomeSequence.TICKS_BEFORE_SCORE);
        assertEquals(MatchOutcomeSequence.Phase.BASE_SCORE, sequence.getPhase());
        return sequence;
    }

    private static MatchOutcomeSequence sequenceAtSatellitePhase(
            GameState.MatchStatus outcome, FinalScoreBreakdown breakdown) {
        MatchOutcomeSequence sequence = sequenceAtScorePhase(outcome, breakdown);
        tick(sequence, 100);
        assertEquals(MatchOutcomeSequence.Phase.SATELLITE_BONUS, sequence.getPhase());
        return sequence;
    }

    private static void tick(MatchOutcomeSequence sequence, int count) {
        for (int tick = 0; tick < count; tick++) {
            sequence.tick();
        }
    }
}
