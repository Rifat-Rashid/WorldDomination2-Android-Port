package com.example.worlddomination.game.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PanelGestureVelocityTest {
    @Test public void sourceEightMillisecondFloorAndSpeedLimitApply() {
        assertEquals(1250f, PanelGestureVelocity.sample(10f, 0L, 1_000_000L), .001f);
        assertEquals(1250f, PanelGestureVelocity.sample(10f, 0L, 8_000_000L), .001f);
        assertEquals(1800f, PanelGestureVelocity.sample(40f, 0L, 16_000_000L), 0f);
        assertEquals(-1800f, PanelGestureVelocity.sample(-40f, 0L, 16_000_000L), 0f);
    }

    @Test public void sampledVelocityDoesNotDependOnDeviceUptimeOrClockOrigin() {
        long[] origins = {0L, 86_400_000_000_000L, 604_800_000_000_000L,
                8_640_000_000_000_000L, -604_800_000_000_000L};
        for (long origin : origins) {
            assertEquals(1200f, PanelGestureVelocity.sample(20f, origin,
                    origin + 16_666_667L), .001f);
        }
    }

    @Test public void equalAndReversedSamplesUseTheSourceMinimumInterval() {
        assertEquals(-125f, PanelGestureVelocity.sample(-1f, 20L, 20L), .001f);
        assertEquals(125f, PanelGestureVelocity.sample(1f, 20L, 10L), .001f);
        assertEquals(0f, PanelGestureVelocity.sample(0f, 20L, 20L), 0f);
    }

    @Test public void nanoClockWrapPreservesShortElapsedIntervals() {
        long beforeWrap = Long.MAX_VALUE - 8_000_000L;
        assertEquals(1200f, PanelGestureVelocity.sample(20f, beforeWrap,
                beforeWrap + 16_666_667L), .001f);
    }

    @Test public void touchingOnePanelStopsOnlyItsExistingFling() {
        ScrollPaneState left = new ScrollPaneState(), right = new ScrollPaneState();
        left.setRange(1000f, 200f); right.setRange(1000f, 200f);
        left.moveBy(100f); right.moveBy(100f);
        left.endGesture(-600f); right.endGesture(-900f);
        left.beginGesture();
        assertFalse(left.hasMotion()); assertTrue(right.hasMotion());
        left.tick(1f / 24f); right.tick(1f / 24f);
        assertEquals(100f, left.getOffset(), 0f);
        assertTrue(right.getOffset() > 100f);
    }
}
