package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class FlashSpyBuildingAnimationTest {
    @Test
    public void fadesByFivePercentPerSourceTickAndExpiresAtTheBuildingInterval() {
        FlashSpyBuildingAnimation animation = create(0);
        assertEquals(24, FlashSpyBuildingAnimation.SOURCE_FRAME_RATE);
        assertEquals(20, FlashSpyBuildingAnimation.FADE_TICKS);
        assertEquals(38, FlashSpyBuildingAnimation.DISPLAY_TICKS);
        assertEquals(0f, animation.getAlpha(), 0f);

        for (int tick = 0; tick < FlashSpyBuildingAnimation.FADE_TICKS - 1; tick++) {
            assertFalse(animation.tick());
        }
        assertEquals(0.95f, animation.getAlpha(), 0.0001f);
        assertFalse(animation.tick());
        assertEquals(1f, animation.getAlpha(), 0f);

        for (int tick = FlashSpyBuildingAnimation.FADE_TICKS;
             tick < FlashSpyBuildingAnimation.DISPLAY_TICKS - 1; tick++) {
            assertFalse(animation.tick());
        }
        assertTrue(animation.tick());
        assertTrue(animation.isFinished());
        assertEquals(0f, animation.getAlpha(), 0f);
    }

    @Test
    public void sourceBuildingEventsArePresentedSequentiallyAtTheFlashInterval() {
        FlashSpyBuildingAnimation second = create(1);
        for (int tick = 0; tick < FlashSpyBuildingAnimation.DISPLAY_TICKS - 1; tick++) {
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
    public void remainsVisibleUntilConcurrentBattlePlaybackCompletes() {
        FlashSpyBuildingAnimation animation = create(0);
        for (int tick = 0; tick < FlashSpyBuildingAnimation.DISPLAY_TICKS + 5; tick++) {
            assertFalse(animation.tick(true));
        }
        assertEquals(1f, animation.getAlpha(), 0f);
        assertTrue(animation.tick(false));
        assertTrue(animation.isFinished());
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
