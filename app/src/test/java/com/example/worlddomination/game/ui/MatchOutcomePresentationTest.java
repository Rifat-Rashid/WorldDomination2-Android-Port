package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MatchOutcomePresentationTest {
    @Test
    public void onlyTerminalMatchesShowAnOutcome() {
        assertFalse(MatchOutcomePresentation.isFinished(GameState.MatchStatus.ONGOING));
        assertFalse(MatchOutcomePresentation.isFinished(null));
        assertTrue(MatchOutcomePresentation.isFinished(GameState.MatchStatus.VICTORY));
        assertTrue(MatchOutcomePresentation.isFinished(GameState.MatchStatus.DEFEAT));
    }

    @Test
    public void victoryAndDefeatHaveDistinctOriginalStyleCopy() {
        assertEquals("WORLD DOMINATION",
                MatchOutcomePresentation.title(GameState.MatchStatus.VICTORY));
        assertEquals("NATION DEFEATED",
                MatchOutcomePresentation.title(GameState.MatchStatus.DEFEAT));
        assertEquals("After killing most of the world's population, your country stands "
                        + "victorious in its quest for world domination.",
                MatchOutcomePresentation.message(GameState.MatchStatus.VICTORY));
        assertEquals("Your country was destroyed. You lost the war for World Domination.",
                MatchOutcomePresentation.message(GameState.MatchStatus.DEFEAT));
    }

    @Test
    public void finalScoreUsesPeriodGroupedThousandsAndNeverGoesNegative() {
        assertEquals("0", MatchOutcomePresentation.formatScore(0));
        assertEquals("124.562", MatchOutcomePresentation.formatScore(124562));
        assertEquals("1.000.000.250",
                MatchOutcomePresentation.formatScore(1_000_000_250L));
        assertEquals("0", MatchOutcomePresentation.formatScore(-100));
    }
}
