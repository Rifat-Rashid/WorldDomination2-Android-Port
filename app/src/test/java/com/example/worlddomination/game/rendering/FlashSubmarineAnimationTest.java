package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashSubmarineAnimationTest {
    @Test
    public void waitsItsLaunchOrderDelayThenPlaysAllSourceHullFramesOnce() {
        FlashBattleRoutes.RocketStrike route =
                FlashBattleRoutes.createSubmarineRocketStrike(0, 2, 3);
        FlashSubmarineAnimation animation = new FlashSubmarineAnimation(route, 1, 2);

        assertFalse(animation.tick());
        assertFalse(animation.isStarted());
        assertFalse(animation.tick());
        assertFalse(animation.isStarted());
        assertTrue(animation.tick());
        assertEquals(0, animation.getSourceFrameIndex());
        assertTrue(animation.takeSurfaceCue());
        assertFalse(animation.takeSurfaceCue());

        for (int frame = 1; frame < FlashSubmarineAnimation.SOURCE_FRAME_COUNT; frame++) {
            assertFalse(animation.tick());
            assertEquals(frame, animation.getSourceFrameIndex());
        }
        assertFalse(animation.isFinished());
        assertFalse(animation.tick());
        assertTrue(animation.isFinished());
        assertEquals(-1, animation.getSourceFrameIndex());
        assertFalse(animation.tick());
    }

    @Test
    public void queuedSubmarineSurfacingUsesTheOriginalEightSecondInterval() {
        FlashSubmarineAnimation animation = new FlashSubmarineAnimation(
                FlashBattleRoutes.createSubmarineRocketStrike(0, 2, 3), 1,
                FlashSubmarineAnimation.delayForSequenceIndex(1));
        for (int tick = 0; tick < 8 * 24 - 1; tick++) {
            assertFalse(animation.tick());
        }
        assertFalse(animation.isStarted());
        assertTrue(animation.tick());
        assertEquals(0, animation.getSourceFrameIndex());
    }

    @Test
    public void splashPanUsesEachOriginalNestedTintClipPosition() {
        float[] sourceX = {402f, 79.4f, 351.4f, 343.45f, 546f};
        for (int targetPlayer = 0; targetPlayer < sourceX.length; targetPlayer++) {
            FlashBattleRoutes.RocketStrike route =
                    FlashBattleRoutes.createSubmarineRocketStrike(
                            (targetPlayer + 1) % sourceX.length, targetPlayer, 0);
            FlashSubmarineAnimation animation = new FlashSubmarineAnimation(route, 3, 0);
            float expectedPan = (sourceX[targetPlayer] - 6.15f) / 400f - 1f;
            assertEquals(sourceX[targetPlayer], route.silo.x, 0f);
            assertEquals(expectedPan, animation.getSurfaceSoundPan(), 0.0001f);
            assertTrue(animation.getSurfaceSoundPan() >= -1f);
            assertTrue(animation.getSurfaceSoundPan() <= 1f);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsColorIdsOutsideTheSourcePalette() {
        new FlashSubmarineAnimation(
                FlashBattleRoutes.createSubmarineRocketStrike(0, 1, 0), 6, 0);
    }
}
