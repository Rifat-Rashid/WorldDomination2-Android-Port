package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class TurnPlaybackSequenceTest {
    @Test
    public void labelsFollowSourceOrderAndCollapseRepeatedActions() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Arrays.asList(GameState.Action.DIPLOMACY,
                GameState.Action.ATTACK, GameState.Action.PREPARED_ATTACK,
                GameState.Action.RESOURCES, GameState.Action.ATTACK,
                GameState.Action.PRODUCTION));

        assertEquals(GameState.Action.RESOURCES, sequence.advance(1.6f));
        assertEquals(GameState.Action.PRODUCTION, sequence.advance(1.6f));
        assertEquals(GameState.Action.PREPARED_ATTACK, sequence.advance(1.6f));
        assertEquals(GameState.Action.ATTACK, sequence.advance(1.6f));
        assertEquals(GameState.Action.DIPLOMACY, sequence.advance(1.6f));
        assertNull(sequence.advance(1.6f));
        assertTrue(sequence.isReadyForBattle());
    }

    @Test
    public void firstLabelAppearsAt1600MillisecondsAndKeepsItsSlotUntilNextInterval() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Collections.singletonList(GameState.Action.MASS_MEDIA));

        assertNull(sequence.advance(1.599f));
        assertEquals(GameState.Action.MASS_MEDIA, sequence.advance(0.001f));
        assertEquals(GameState.Action.MASS_MEDIA, sequence.getCurrentAction());
        assertNull(sequence.advance(1.599f));
        assertEquals(GameState.Action.MASS_MEDIA, sequence.getCurrentAction());
        assertNull(sequence.advance(0.001f));
        assertNull(sequence.getCurrentAction());
        assertTrue(sequence.isReadyForBattle());
    }

    @Test
    public void emptyActionListStillWaitsForTheSourcePassBeforeBattle() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Collections.<GameState.Action>emptyList());

        assertTrue(sequence.isWaitingForActionLabels());
        assertNull(sequence.advance(1.599f));
        assertFalse(sequence.isReadyForBattle());
        assertNull(sequence.advance(0.001f));
        assertTrue(sequence.isReadyForBattle());
    }

    @Test
    public void inputRemainsBlockedForSixtyCleanupTicksAndOneHandoffTick() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Collections.<GameState.Action>emptyList());
        sequence.advance(1.6f);
        assertTrue(sequence.isReadyForBattle());

        sequence.completeBattlePlayback();
        assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, sequence.getPhase());
        assertTrue(sequence.isCleaningUp());
        assertTrue(sequence.isBlockingInput());
        for (int tick = 1; tick < 60; tick++) {
            sequence.advance(1f / 24f);
            assertEquals("Cleanup cannot end on tick " + tick,
                    TurnPlaybackSequence.Phase.RESET_BARRIER, sequence.getPhase());
        }
        sequence.advance(1f / 24f);
        assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, sequence.getPhase());
        assertTrue(sequence.isCleaningUp());
        assertTrue(sequence.isBlockingInput());
        sequence.advance(1f / 24f);
        assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
        assertFalse(sequence.isCleaningUp());
        assertFalse(sequence.isBlockingInput());
    }

    @Test
    public void actionLabelsKeepRevealingDuringCleanupWithoutRestartingTheWait() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Arrays.asList(GameState.Action.ATTACK,
                GameState.Action.RESOURCES, GameState.Action.DIPLOMACY));
        sequence.completeBattlePlayback();
        assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, sequence.getPhase());
        assertTrue(sequence.isWaitingForActionLabels());
        assertFalse(sequence.isReadyForBattle());
        assertNull(sequence.advance(1.599f));
        assertEquals(GameState.Action.RESOURCES, sequence.advance(0.001f));
        assertEquals(GameState.Action.RESOURCES, sequence.getCurrentAction());
        assertTrue(sequence.isWaitingForActionLabels());
        assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, sequence.getPhase());
        sequence.advance(0.9f);
        assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, sequence.getPhase());
        assertTrue(sequence.isBlockingInput());
        sequence.advance(1f / 24f);
        assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
        assertFalse(sequence.isWaitingForActionLabels());
        assertNull(sequence.getCurrentAction());
    }

    @Test
    public void duplicateCompletionDoesNotResetCleanupTimer() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Collections.<GameState.Action>emptyList());
        sequence.completeBattlePlayback();
        sequence.advance(1.25f);
        sequence.completeBattlePlayback();
        sequence.advance(1.25f);
        assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, sequence.getPhase());
        sequence.completeBattlePlayback();
        assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, sequence.getPhase());
        sequence.advance(1f / 24f);
        assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
    }

    @Test
    public void longDeltaCannotConsumeCleanupAndHandoffInOneCall() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Collections.<GameState.Action>emptyList());
        sequence.completeBattlePlayback();
        sequence.advance(10f);
        assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, sequence.getPhase());
        assertTrue(sequence.isBlockingInput());
        sequence.advance(10f);
        assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
    }

    @Test
    public void beginningANewTurnClearsPriorLabelsAndBarrierState() {
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        sequence.begin(Collections.singletonList(GameState.Action.ATTACK));
        assertEquals(GameState.Action.ATTACK, sequence.advance(1.6f));
        sequence.completeBattlePlayback();
        assertTrue(sequence.isCleaningUp());

        sequence.begin(Collections.singletonList(GameState.Action.RESOURCES));

        assertNull(sequence.getCurrentAction());
        assertFalse(sequence.isCleaningUp());
        assertEquals(GameState.Action.RESOURCES, sequence.advance(1.6f));
    }
}
