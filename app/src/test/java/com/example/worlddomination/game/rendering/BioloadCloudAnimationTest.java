package com.example.worlddomination.game.rendering;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class BioloadCloudAnimationTest {
    @Test
    public void createsSeventeenCloudsInsideTheSourceSpawnRanges() {
        BioloadCloudAnimation animation = new BioloadCloudAnimation(2401L);

        assertEquals(17, BioloadCloudAnimation.CLOUD_COUNT);
        for (int cloud = 0; cloud < BioloadCloudAnimation.CLOUD_COUNT; cloud++) {
            assertTrue(animation.getX(cloud) >= -25f && animation.getX(cloud) <= 25f);
            assertTrue(animation.getY(cloud) >= -20f && animation.getY(cloud) <= 0f);
            assertTrue(animation.getAlpha(cloud) >= 30f && animation.getAlpha(cloud) <= 50f);
            assertTrue(animation.getScalePercent(cloud) >= 40f
                    && animation.getScalePercent(cloud) <= 80f);
        }
    }

    @Test
    public void sourceTickFloatsRisesFadesAndExpandsTheCloud() {
        BioloadCloudAnimation animation = new BioloadCloudAnimation(new MaximumRandom());

        animation.tick();

        assertEquals(25.3f, animation.getX(0), 0.0001f);
        assertEquals(-2f, animation.getY(0), 0.0001f);
        assertEquals(47f, animation.getAlpha(0), 0.0001f);
        assertEquals(95f, animation.getScalePercent(0), 0.0001f);
    }

    @Test
    public void horizontalDriftReversesAtTheSourceTenTickBoundary() {
        BioloadCloudAnimation animation = new BioloadCloudAnimation(new MaximumRandom());

        for (int tick = 0; tick < 10; tick++) {
            animation.tick();
        }
        assertEquals(28f, animation.getX(0), 0.0001f);

        animation.tick();
        assertEquals(27.7f, animation.getX(0), 0.0001f);
    }

    @Test
    public void expiredCloudRespawnsFromItsVisualRandomStream() {
        BioloadCloudAnimation animation = new BioloadCloudAnimation(new MaximumRandom());

        for (int tick = 0; tick < 17; tick++) {
            animation.tick();
        }
        assertTrue(animation.getAlpha(0) <= 0f);

        animation.tick();
        assertEquals(25f, animation.getX(0), 0.0001f);
        assertEquals(0f, animation.getY(0), 0.0001f);
        assertEquals(50f, animation.getAlpha(0), 0.0001f);
        assertEquals(80f, animation.getScalePercent(0), 0.0001f);
    }

    @Test
    public void visualAnimationIsDeterministicForItsSeed() {
        BioloadCloudAnimation first = new BioloadCloudAnimation(4911L);
        BioloadCloudAnimation second = new BioloadCloudAnimation(4911L);

        for (int tick = 0; tick < 80; tick++) {
            first.tick();
            second.tick();
        }
        for (int cloud = 0; cloud < BioloadCloudAnimation.CLOUD_COUNT; cloud++) {
            assertEquals(first.getX(cloud), second.getX(cloud), 0f);
            assertEquals(first.getY(cloud), second.getY(cloud), 0f);
            assertEquals(first.getAlpha(cloud), second.getAlpha(cloud), 0f);
            assertEquals(first.getScalePercent(cloud), second.getScalePercent(cloud), 0f);
        }
    }

    private static final class MaximumRandom extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }
}
