package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashSatelliteAnimationTest {
    @Test public void waitingClipPublishesItsPoseBeforeTheActivationGate() {
        FlashSatelliteAnimation animation = new FlashSatelliteAnimation(
                FlashBattleRoutes.createSatelliteStrike(0, 1, 0, 1, "chr"), 2);
        assertTrue(animation.isClipVisible());
        assertEquals(0f, animation.getDisplaySourceX(), 0f);
        assertEquals(0f, animation.getDisplaySourceY(), 0f);
        animation.presentWaitingFrame();
        assertFalse(animation.isStarted());
        assertEquals(-120f, animation.getDisplaySourceX(), 0f);
        assertEquals(158f, animation.getDisplaySourceY(), 0f);
        assertFalse(animation.tick());
        assertFalse(animation.tick());
        assertFalse(animation.isStarted());
        animation.tick();
        assertEquals(animation.getSourceX(), animation.getDisplaySourceX(), 0f);
        assertEquals(animation.getSourceY(), animation.getDisplaySourceY(), 0f);
    }

    @Test public void nestedFrameSeekUsesThePreviousDisplayX() {
        FlashSatelliteAnimation animation = new FlashSatelliteAnimation(
                FlashBattleRoutes.createSatelliteStrike(0, 1, 0, 1, "chr"), 0);
        while (animation.getSourceX() < 63f) animation.tick();
        assertEquals(2, animation.getSourceClipFrameNumber());
        animation.tick();
        assertEquals(66f, animation.getSourceX(), 0f);
        assertEquals("Source seeks with old x63 before publishing x66", 2,
                animation.getSourceClipFrameNumber());
        animation.tick();
        assertEquals(3, animation.getSourceClipFrameNumber());
    }

    @Test public void stopEndsTheFrameThenRemovalRetainsTheBodyUntilCleanup() {
        FlashSatelliteAnimation animation = new FlashSatelliteAnimation(
                FlashBattleRoutes.createSatelliteStrike(0, 1, 0, 1, "chr"), 0);
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        int impacts = 0, lasers = 0, flights = 0, ticks = 0;
        while (!animation.didStopSourceFrame() && ticks++ < 250) {
            animation.tick();
            animation.registerStoppedClip(queue);
            if (animation.takeImpact()) impacts++;
            if (animation.takeLaserCue()) lasers++;
            if (animation.takeFlightCue()) flights++;
        }
        assertTrue(ticks < 250);
        assertFalse("Stop still occupies the source active array", animation.isFinished());
        assertFalse("View clears before breaking; alpha5 is not a drawn beam", animation.isFiring());
        assertEquals(0.05f, animation.getLaserAlpha(), 0f);
        assertTrue(animation.isClipVisible());
        assertEquals(0, queue.size());
        assertTrue(animation.tick());
        animation.registerStoppedClip(queue);
        assertTrue(animation.isFinished());
        assertFalse(animation.didStopSourceFrame());
        assertTrue(animation.isClipVisible());
        assertFalse(animation.takeImpact());
        for (int tick = 0; tick < 60; tick++) {
            assertTrue(animation.tick());
            animation.registerStoppedClip(queue);
            assertTrue(animation.isClipVisible());
            assertFalse(animation.isFiring());
        }
        assertEquals(1, queue.size());
        assertEquals(1, queue.removeAtAllUnitsDone());
        assertFalse(animation.isClipVisible());
        assertEquals(1, impacts);
        assertEquals(1, lasers);
        assertEquals(1, flights);
    }

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
