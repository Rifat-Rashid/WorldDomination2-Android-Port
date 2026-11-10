package com.example.worlddomination.game.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ConfirmTurnAnimationTest {
    private static final float STEP = 1f / 24f;

    @Test public void startsClosedAndNeitherEmptyNorSingleActionCanOpenIt() {
        ConfirmTurnAnimation animation = new ConfirmTurnAnimation();
        assertEquals(1, animation.getFrame());
        assertFalse(animation.synchronize(0, false));
        assertFalse(animation.press(0, false));
        assertFalse(animation.synchronize(1, false));
        assertFalse(animation.press(1, false));
        assertFalse(animation.isAnimating());
    }

    @Test public void secondAcceptedActionOpensOneOriginalFramePerSourceTick() {
        ConfirmTurnAnimation animation = new ConfirmTurnAnimation();
        assertTrue(animation.synchronize(2, false));
        assertFalse(animation.synchronize(2, false));
        for (int expected = 2; expected <= 11; expected++) {
            assertFalse(animation.tick(STEP));
            assertEquals(expected, animation.getFrame());
        }
        assertFalse(animation.isAnimating());
        assertTrue(animation.isOpening());
    }

    @Test public void pressAcknowledgesThenClosesBeforeTheSingleDelayedTurnSignal() {
        ConfirmTurnAnimation animation = open();
        assertTrue(animation.press(2, false));
        assertEquals(12, animation.getFrame());
        assertTrue(animation.isPending());
        assertFalse(animation.press(2, false));
        for (int tick = 1; tick <= 19; tick++) {
            assertFalse("Turn resolved too early at " + tick, animation.tick(STEP));
            assertEquals(Math.max(1, 12 - tick), animation.getFrame());
        }
        assertTrue(animation.tick(STEP));
        assertFalse(animation.isPending());
        assertEquals(1, animation.getFrame());
        assertFalse(animation.tick(STEP));
    }

    @Test public void removingAnActionReversesOpeningAndClosesWithoutAdvancingTheTurn() {
        ConfirmTurnAnimation animation = new ConfirmTurnAnimation();
        animation.synchronize(2, false);
        for (int i = 0; i < 4; i++) animation.tick(STEP);
        assertEquals(5, animation.getFrame());
        assertTrue(animation.synchronize(1, false));
        for (int expected = 4; expected >= 1; expected--) {
            assertFalse(animation.tick(STEP));
            assertEquals(expected, animation.getFrame());
        }
    }

    @Test public void battleBlockingCannotReopenOrRetriggerConfirm() {
        ConfirmTurnAnimation animation = open();
        assertTrue(animation.synchronize(2, true));
        assertFalse(animation.press(2, true));
        for (int i = 0; i < 10; i++) animation.tick(STEP);
        assertEquals(1, animation.getFrame());
        assertFalse(animation.synchronize(2, true));
    }

    @Test public void restartingAMatchClearsPendingResolutionAndAnimation() {
        ConfirmTurnAnimation animation = open();
        animation.press(2, false);
        animation.tick(STEP);
        animation.reset();
        assertFalse(animation.isPending());
        assertFalse(animation.isAnimating());
        assertEquals(1, animation.getFrame());
        for (int i = 0; i < 30; i++) assertFalse(animation.tick(STEP));
    }

    @Test public void interruptedOrInvalidClockStepsDoNotAdvanceConfirmation() {
        ConfirmTurnAnimation animation = open();
        animation.press(2, false);
        for (float seconds : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertFalse(animation.tick(seconds));
            assertEquals(12, animation.getFrame());
            assertEquals(0f, animation.getElapsedSeconds(), 0f);
        }
        assertTrue(animation.isPending());
    }

    private static ConfirmTurnAnimation open() {
        ConfirmTurnAnimation animation = new ConfirmTurnAnimation();
        animation.synchronize(2, false);
        for (int i = 0; i < 10; i++) animation.tick(STEP);
        return animation;
    }
}
