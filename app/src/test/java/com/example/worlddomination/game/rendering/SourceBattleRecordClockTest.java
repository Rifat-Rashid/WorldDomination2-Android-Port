package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class SourceBattleRecordClockTest {
    @Test
    public void firstBattleActivatesConsecutiveRecordsOnFirstEligibleTicks() {
        SourceBattleRecordClock clock = new SourceBattleRecordClock();
        clock.reset();
        clock.begin(4, true);
        assertEquals(0, clock.delayForRecord(1));
        assertEquals(3, clock.delayForRecord(4));
        for (int startNum = 1; startNum <= 4; startNum++) {
            assertTrue(clock.hasPendingRecords());
            assertEquals(startNum, clock.tick());
            assertEquals(startNum, clock.getElapsedTicks());
        }
        assertFalse(clock.hasPendingRecords());
        assertEquals(0, clock.tick());
        clock.finishBattle();
    }

    @Test
    public void carriedTwentyWithoutBuildingsDelaysFirstRecordByTwentyTicks() {
        SourceBattleRecordClock clock = completedFirstBattle();
        clock.begin(2, false);
        assertEquals(20, clock.delayForRecord(1));
        assertEquals(21, clock.delayForRecord(2));
        for (int tick = 0; tick < 20; tick++) {
            assertEquals(0, clock.tick());
        }
        assertTrue(clock.hasPendingRecords());
        assertEquals(1, clock.tick());
        assertEquals(2, clock.tick());
        assertFalse(clock.hasPendingRecords());
    }

    @Test
    public void pendingBuildingResetsCarriedCounterOnItsFirstScan() {
        SourceBattleRecordClock clock = completedFirstBattle();
        clock.begin(3, true);
        assertEquals(1, clock.delayForRecord(1));
        assertEquals(2, clock.delayForRecord(2));
        assertEquals(3, clock.delayForRecord(3));
        assertEquals(0, clock.tick());
        assertEquals(1, clock.tick());
        assertEquals(2, clock.tick());
        assertEquals(3, clock.tick());
        assertFalse(clock.hasPendingRecords());
    }

    @Test
    public void emptyBattleCarriesTwentyAndResetStartsFreshMatch() {
        SourceBattleRecordClock clock = new SourceBattleRecordClock();
        clock.begin(0, false);
        assertFalse(clock.hasPendingRecords());
        assertEquals(0, clock.tick());
        clock.finishBattle();
        clock.begin(1, false);
        assertEquals(20, clock.delayForRecord(1));
        clock.reset();
        assertFalse(clock.hasPendingRecords());
        clock.begin(1, false);
        assertEquals(0, clock.delayForRecord(1));
        assertEquals(1, clock.tick());
    }

    @Test
    public void invalidLifecycleAndRecordNumbersAreRejected() {
        SourceBattleRecordClock clock = new SourceBattleRecordClock();
        expectIllegalState(clock::tick);
        expectIllegalState(() -> clock.delayForRecord(1));
        expectIllegalState(clock::finishBattle);
        expectIllegalArgument(() -> clock.begin(-1, false));
        expectIllegalArgument(() -> clock.begin(0, true));
        expectIllegalArgument(() -> clock.begin(Integer.MAX_VALUE, false));
        clock.begin(1, false);
        expectIllegalState(() -> clock.begin(1, false));
        expectIllegalArgument(() -> clock.delayForRecord(0));
        expectIllegalArgument(() -> clock.delayForRecord(2));
        assertEquals(1, clock.tick());
        clock.finishBattle();
        expectIllegalState(clock::tick);
    }

    @Test
    public void sourceCompletionCanDiscardSilentOrWatchdogAbortedRecordSlots() {
        SourceBattleRecordClock clock = new SourceBattleRecordClock();
        clock.begin(1, false); // Ship preparation has a record but no active wrapper.
        assertTrue(clock.hasPendingRecords());
        assertEquals(0, clock.getActivatedRecordCount());
        clock.finishBattle();
        assertFalse(clock.hasPendingRecords());
        clock.begin(2, false);
        assertEquals(20, clock.delayForRecord(1));
        clock.reset();
    }

    private static SourceBattleRecordClock completedFirstBattle() {
        SourceBattleRecordClock clock = new SourceBattleRecordClock();
        clock.begin(1, false);
        assertEquals(1, clock.tick());
        clock.finishBattle();
        return clock;
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected lifecycle failure.
        }
    }
}
