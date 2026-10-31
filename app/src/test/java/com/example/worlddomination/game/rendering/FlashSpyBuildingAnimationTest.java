package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class FlashSpyBuildingAnimationTest {
    @Test
    public void fadesByFivePercentPerSourceTickAndRemainsAtFullAlphaAfterInterval() {
        FlashSpyBuildingAnimation animation = create(0);
        assertEquals(24, FlashSpyBuildingAnimation.SOURCE_FRAME_RATE);
        assertEquals(20, FlashSpyBuildingAnimation.FADE_TICKS);
        assertEquals(38, FlashSpyBuildingAnimation.SEQUENCE_INTERVAL_TICKS);
        assertEquals(0f, animation.getAlpha(), 0f);

        for (int tick = 0; tick < FlashSpyBuildingAnimation.FADE_TICKS - 1; tick++) {
            assertFalse(animation.tick());
        }
        assertEquals(0.95f, animation.getAlpha(), 0.0001f);
        assertTrue(animation.tick());
        assertEquals(1f, animation.getAlpha(), 0f);

        assertTrue(animation.isFadeComplete());
        assertFalse(animation.isAnimating());
        for (int tick = 0; tick < FlashSpyBuildingAnimation.SEQUENCE_INTERVAL_TICKS + 100; tick++) {
            animation.tick();
        }
        assertEquals(1f, animation.getAlpha(), 0f);
    }

    @Test
    public void sourceBuildingEventsArePresentedSequentiallyAtTheFlashInterval() {
        FlashSpyBuildingAnimation second = create(1);
        for (int tick = 0; tick < FlashSpyBuildingAnimation.SEQUENCE_INTERVAL_TICKS - 1; tick++) {
            second.tick();
        }
        assertFalse(second.isStarted());
        assertEquals(0f, second.getAlpha(), 0f);

        second.tick();
        assertTrue(second.isStarted());
        assertEquals(0f, second.getAlpha(), 0f);
        second.tick();
        assertEquals(0.05f, second.getAlpha(), 0.0001f);
    }

    @Test
    public void remainsVisibleAfterItsFadeCompletesUntilTheSlotIsReplaced() {
        FlashSpyBuildingAnimation animation = create(0);
        for (int tick = 0; tick < FlashSpyBuildingAnimation.FADE_TICKS; tick++) {
            animation.tick();
        }
        assertTrue(animation.isFadeComplete());
        assertEquals(1f, animation.getAlpha(), 0f);
        for (int tick = 0; tick < FlashSpyBuildingAnimation.SEQUENCE_INTERVAL_TICKS * 2; tick++) {
            animation.tick();
        }
        assertTrue(animation.isStarted());
        assertEquals(1f, animation.getAlpha(), 0f);
    }

    @Test
    public void requiresValidSourcePlayerBuildingAndReligionData() {
        try {
            new FlashSpyBuildingAnimation(0, 1, 3, "tech", "chr", 0);
            fail("Expected an invalid action-building slot to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static FlashSpyBuildingAnimation create(int sequenceIndex) {
        return new FlashSpyBuildingAnimation(0, 1, 2, "tech", "chr", sequenceIndex);
    }
}
