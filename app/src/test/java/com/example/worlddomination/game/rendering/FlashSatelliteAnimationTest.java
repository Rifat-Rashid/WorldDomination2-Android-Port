package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashSatelliteAnimationTest {
    @Test
    public void selectsTheSourceNestedFlashFrameFromSatelliteXOnEveryIsland() {
        float[] orbitX = {368f, 268f, 466f, 121f, 667f};
        int[] expectedSourceFrames = {14, 10, 18, 5, 26};
        for (int attacker = 0; attacker < orbitX.length; attacker++) {
            FlashSatelliteAnimation animation = new FlashSatelliteAnimation(
                    FlashBattleRoutes.createSatelliteStrike(attacker,
                            (attacker + 1) % orbitX.length, 0, attacker + 1,
                            new String[]{"mus", "jew", "com", "chr", "bhu"}[attacker]),
                    0);
            assertEquals(1, animation.getSourceClipFrameNumber());
            while (animation.getSourceX() < orbitX[attacker]) {
                animation.tick();
            }
            assertEquals(expectedSourceFrames[attacker],
                    animation.getSourceClipFrameNumber());
        }
    }

    @Test
    public void followsThe24HzSourceStepThenImpactsAndFadesItsLaser() {
        FlashBattleRoutes.SatelliteStrike route = FlashBattleRoutes
                .createSatelliteStrike(0, 1, 0, 1, "chr");
        FlashSatelliteAnimation animation = new FlashSatelliteAnimation(route, 2);

        assertFalse(animation.tick());
        assertFalse(animation.tick());
        assertFalse(animation.isStarted());
        assertFalse(animation.tick());
        assertTrue(animation.isStarted());
        assertTrue(animation.takeFlightCue());
        assertEquals(route.launch.x + 3f, animation.getSourceX(), 0f);
        assertEquals(route.launch.y + 1f, animation.getSourceY(), 0f);
        assertFalse(animation.takeImpact());

        int approachTicks = 0;
        while (animation.getSourceX() < route.orbit.x && approachTicks < 200) {
            animation.tick();
            animation.takeFlightCue();
            approachTicks++;
        }
        assertTrue("The source satellite must reach its orbital x-coordinate",
                animation.getSourceX() >= route.orbit.x);
        assertFalse(animation.isFiring());
        animation.tick();
        assertTrue(animation.isFiring());
        assertTrue(animation.takeImpact());
        assertFalse(animation.takeImpact());

        animation.tick();
        assertTrue(animation.takeLaserCue());
        assertEquals(0f, animation.getLaserAlpha(), 0f);
        animation.tick();
        assertEquals(0.15f, animation.getLaserAlpha(), 0.001f);

        int firingTicks = 2;
        while (!animation.isFinished() && firingTicks < 40) {
            animation.tick();
            animation.takeLaserCue();
            firingTicks++;
        }
        assertTrue("The source laser fade should complete in under 40 source frames",
                animation.isFinished());
        assertTrue(firingTicks >= 25);
        assertTrue(animation.tick());
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresRouteAndNonNegativeActionDelay() {
        new FlashSatelliteAnimation(null, 0);
    }
}
