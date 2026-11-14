package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SourceInterceptedAircraftDisplayDepthsTest {
    @Test public void deathReleasesAircraftPeersAndRetainsStoppedExplosionUntilCleanup() {
        BattlefieldDisplayState root = new BattlefieldDisplayState();
        SourceStoppedClipQueue stopped = new SourceStoppedClipQueue();
        String[] names = {"unit1", "shadow_unit1", "tintunit1", "targetDotunit1"};
        for (int index = 0; index < names.length; index++)
            root.attach(BattlefieldDisplayState.Kind.AUTHORED, index,
                    names[index], null, 22509 + index);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> sibling = root.attach(
                BattlefieldDisplayState.Kind.EFFECT, 0, "anotherUnitEffect", null, 22517);
        FlashStandalonePlaneAnimation plane = new FlashStandalonePlaneAnimation(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 0, 1, "plane1", true),
                1, 0, "explosion003", null, new ReferenceRandom(0));
        SourceInterceptedAircraftDisplayDepths depths = new SourceInterceptedAircraftDisplayDepths(
                root, plane, 0, stopped);
        SourceAircraftTrailDisplay smoke = new SourceAircraftTrailDisplay(root,
                plane.getSourceTrail(), 0, 0);
        assertEquals(22509, depths.body().getDepth());
        assertEquals(22510, depths.shadow().getDepth());
        assertEquals(22511, depths.tint().getDepth());
        assertEquals(22512, depths.dot().getDepth());
        assertEquals("binding keeps reserved occupancy", 5, root.clips().size());
        for (int tick = 1; tick <= 195; tick++) plane.tickSourceFrame(false);
        for (int tick = 196; tick <= 313; tick++) {
            depths.advanceAttachedTimelines();
            plane.tickSourceFrame(true);
            smoke.afterUnitFrame();
            depths.afterUnitFrame();
        }
        assertEquals("speedup_die", plane.getSourcePlayback().getAction());
        assertTrue(depths.body().isAttached());
        assertNull(depths.explosion());
        plane.tickSourceFrame(true);
        smoke.afterUnitFrame();
        depths.afterUnitFrame();
        assertTrue(plane.getSourcePlayback().takeDeath());
        assertFalse("kill hides body before wrapper removal", plane.isVisible());
        assertFalse(plane.isFinished());
        assertFalse(depths.body().isAttached());
        assertFalse(depths.shadow().isAttached());
        assertFalse(depths.tint().isAttached());
        assertTrue(depths.dot().isAttached());
        assertEquals(0, plane.getSourceTrail().getAttachedCount());
        assertEquals("depth reuses released smoke space above sibling", 22518,
                depths.explosion().getDepth());
        assertEquals(0, depths.getExplosionFrameIndex());
        assertEquals(48.27275153669018, depths.getExplosionX(), .001);
        assertEquals(254.7482852266179, depths.getExplosionY(), .001);
        assertEquals(0, stopped.size());
        plane.tickSourceFrame(true);
        depths.afterUnitFrame();
        assertTrue(plane.isFinished());
        assertEquals(1, stopped.size());
        depths.afterUnitFrame();
        assertEquals("queue a removed wrapper only once", 1, stopped.size());
        for (int tick = 0; tick < 80; tick++) depths.advanceAttachedTimelines();
        assertEquals("authored frame22 stops", 21, depths.getExplosionFrameIndex());
        assertTrue("stopping does not remove effect", depths.explosion().isAttached());
        assertEquals(1, stopped.removeAtAllUnitsDone());
        assertFalse(depths.explosion().isAttached());
        assertTrue("source target dot survives cleanup", depths.dot().isAttached());
        assertTrue("cleanup does not affect other Units", sibling.isAttached());
    }

    @Test public void missingReservationCannotCreateAnUnaccountedUnit() {
        FlashStandalonePlaneAnimation plane = new FlashStandalonePlaneAnimation(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 0, 1, "plane1", true),
                1, 0, "explosion003", null, new ReferenceRandom(0));
        assertThrows(IllegalStateException.class, () -> new SourceInterceptedAircraftDisplayDepths(
                new BattlefieldDisplayState(), plane, 0, new SourceStoppedClipQueue()));
    }
}
