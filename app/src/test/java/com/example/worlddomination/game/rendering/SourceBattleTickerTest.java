package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static com.example.worlddomination.game.rendering.SourceBattleTicker.FrameDecision.ADVANCE;
import static com.example.worlddomination.game.rendering.SourceBattleTicker.FrameDecision.CLEANUP;
import static com.example.worlddomination.game.rendering.SourceBattleTicker.FrameDecision.SUPPRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class SourceBattleTickerTest {
    @Test
    public void firstEmptyFrameCleansUpAfterIncrementingCount() {
        SourceBattleTicker ticker = new SourceBattleTicker();
        ticker.begin();
        assertEquals(CLEANUP, ticker.advanceFrame(false, false));
        assertEquals(1L, ticker.getFrameCount());
        assertFalse(ticker.isRunning());
    }

    @Test
    public void activeBattleAdvancesThroughFrameEightHundredButCleansUpAtEightHundredOne() {
        SourceBattleTicker ticker = new SourceBattleTicker();
        ticker.begin();
        for (int frame = 1; frame <= 800; frame++) {
            assertEquals(ADVANCE, ticker.advanceFrame(true, false));
        }
        assertEquals(800L, ticker.getFrameCount());
        assertTrue(ticker.isRunning());
        assertEquals(CLEANUP, ticker.advanceFrame(true, false));
        assertEquals(801L, ticker.getFrameCount());
        assertFalse(ticker.isRunning());
    }

    @Test
    public void killAllCheatSuppressesTriggeredFrameWithoutAdvancingUnits() {
        SourceBattleTicker ticker = new SourceBattleTicker();
        ticker.begin();
        assertEquals(SUPPRESS, ticker.advanceFrame(false, true));
        assertEquals(1L, ticker.getFrameCount());
        assertTrue(ticker.isRunning());
        assertEquals(ADVANCE, ticker.advanceFrame(true, true));
        assertEquals(SUPPRESS, ticker.advanceFrame(false, true));
        assertEquals(3L, ticker.getFrameCount());
        assertEquals(CLEANUP, ticker.advanceFrame(false, false));
        assertFalse(ticker.isRunning());
    }

    @Test
    public void killAllCheatSuppressesOverLimitUntilDisabled() {
        SourceBattleTicker ticker = new SourceBattleTicker();
        ticker.begin();
        for (int frame = 1; frame <= 800; frame++) {
            ticker.advanceFrame(true, false);
        }
        assertEquals(SUPPRESS, ticker.advanceFrame(true, true));
        assertTrue(ticker.isRunning());
        assertEquals(SUPPRESS, ticker.advanceFrame(true, true));
        assertEquals(802L, ticker.getFrameCount());
        assertEquals(CLEANUP, ticker.advanceFrame(true, false));
        assertEquals(803L, ticker.getFrameCount());
    }

    @Test
    public void labelFramesCountBeforeUnitGateAndBeginRestartsClock() {
        SourceBattleTicker ticker = new SourceBattleTicker();
        ticker.begin();
        for (int labelFrame = 0; labelFrame < 38; labelFrame++) {
            assertEquals(ADVANCE, ticker.advanceFrame(true, false));
        }
        assertEquals(38L, ticker.getFrameCount());
        assertEquals(ADVANCE, ticker.advanceFrame(true, false));
        assertEquals(39L, ticker.getFrameCount());
        ticker.begin();
        assertEquals(0L, ticker.getFrameCount());
        assertEquals(ADVANCE, ticker.advanceFrame(true, false));
        assertEquals(1L, ticker.getFrameCount());
        ticker.reset();
        assertFalse(ticker.isRunning());
        assertEquals(0L, ticker.getFrameCount());
    }

    @Test
    public void stoppedTickerRejectsAdvance() {
        SourceBattleTicker ticker = new SourceBattleTicker();
        expectStopped(ticker);
        ticker.begin();
        ticker.reset();
        expectStopped(ticker);
        ticker.begin();
        ticker.advanceFrame(false, false);
        expectStopped(ticker);
    }

    private static void expectStopped(SourceBattleTicker ticker) {
        try {
            ticker.advanceFrame(true, false);
            fail("Expected stopped ticker to reject advance");
        } catch (IllegalStateException expected) {
            // Expected lifecycle failure.
        }
    }
}
