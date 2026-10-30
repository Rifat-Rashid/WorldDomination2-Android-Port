package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashCarrierPlaneAnimationTest {
    @Test
    public void honorsSourceTickDelayAndAcceleratesAlongTheSourceRoute() {
        FlashBattleRoutes.CarrierPlaneStrike route =
                FlashBattleRoutes.createCarrierPlaneStrike(0, 2, 0, 0, false);
        FlashCarrierPlaneAnimation animation = new FlashCarrierPlaneAnimation(route, 2,
                "explosion_warhead_1", OriginalAudio.Cue.EXPLOSION_SMALL3);

        assertFalse(animation.tick());
        assertFalse(animation.getActor().visible);
        assertFalse(animation.takeTakeoffCue());
        assertFalse(animation.tick());
        assertFalse(animation.getActor().visible);
        assertFalse(animation.takeTakeoffCue());

        assertFalse(animation.tick());
        assertTrue(animation.getActor().visible);
        assertEquals(0, animation.getActor().sourceFrame);
        assertEquals(route.start.x - 1f, animation.getActor().sourceX, 0.001f);
        assertEquals(route.start.y + 2f, animation.getActor().sourceY, 0.001f);
        assertTrue(animation.takeTakeoffCue());
        assertFalse(animation.takeTakeoffCue());
        assertFalse(animation.tick());
        assertEquals(1, animation.getActor().sourceFrame);
    }

    @Test
    public void playsTheOriginalCarrierFlightCueAfterItsTakeoffAndCyclesAt24Hz() {
        FlashBattleRoutes.CarrierPlaneStrike route =
                FlashBattleRoutes.createCarrierPlaneStrike(0, 2, 0, 1, false);
        FlashCarrierPlaneAnimation animation = new FlashCarrierPlaneAnimation(route, 0,
                "explosion_bioload_2", OriginalAudio.Cue.EXPLOSION_MEDIUM3);

        assertFalse(animation.tick());
        assertTrue(animation.takeTakeoffCue());
        for (int tick = 1; tick < 24; tick++) {
            assertFalse(animation.tick());
        }
        assertEquals(23, animation.getActor().sourceFrame);
        assertFalse(animation.takeFlightCue());
        assertFalse(animation.tick());
        assertEquals(0, animation.getActor().sourceFrame);
        for (int tick = 25; tick < 37; tick++) {
            assertFalse(animation.tick());
        }
        assertEquals(12, animation.getActor().sourceFrame);
        assertTrue("Original carrier takeoff audio lasts 37 source ticks",
                animation.takeFlightCue());
        assertFalse(animation.takeFlightCue());
        assertFalse(animation.tick());
        assertEquals(13, animation.getActor().sourceFrame);
        assertFalse("Flight-loop cue must not be emitted on every later tick",
                animation.takeFlightCue());
    }

    @Test
    public void reportsOnePayloadImpactAtTheSourceBombWaypoint() {
        FlashBattleRoutes.CarrierPlaneStrike route =
                FlashBattleRoutes.createCarrierPlaneStrike(3, 1, 4, 2, true);
        FlashCarrierPlaneAnimation animation = new FlashCarrierPlaneAnimation(route, 0,
                "explosion003", OriginalAudio.Cue.EXPLOSION_SMALL3);
        int impactCount = 0;
        for (int tick = 0; tick < 500; tick++) {
            if (animation.tick()) {
                impactCount++;
                assertEquals("explosion003", animation.getImpactLinkage());
                assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3, animation.getImpactCue());
                FlashCarrierPlaneAnimation.Actor actor = animation.getActor();
                FlashBattleRoutes.Point impact = route.waypoints[route.waypoints.length - 1];
                assertEquals(impact.x, actor.sourceX, 0f);
                assertEquals(impact.y, actor.sourceY, 0f);
                assertEquals(1f, actor.routeProgress, 0f);
            }
        }
        assertEquals(1, impactCount);
        assertTrue(animation.isFinished());
        assertFalse(animation.getActor().visible);
    }

    @Test
    public void rejectsMissingRoutesAndNegativeSourceDelays() {
        FlashBattleRoutes.CarrierPlaneStrike route =
                FlashBattleRoutes.createCarrierPlaneStrike(0, 1, 0, 0, false);
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new FlashCarrierPlaneAnimation(null, 0, null, null));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new FlashCarrierPlaneAnimation(route, -1, null, null));
    }
}
