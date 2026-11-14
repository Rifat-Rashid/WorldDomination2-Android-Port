package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.AntiDefenseAudioPlayback;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Synthetic method-boundary ordering checks; no mixed-aircraft legal parity claim. */
public final class SourceAirDefenseControllerTest {
    private static final int DEFENDER = 1;
    private static final int SLOT = 2;

    @Test public void firstVisitedEligibleUnitWinsInsteadOfFirstConstructedUnit()
            throws Exception {
        AntiDefenseAudioPlayback sounds = silentOwners();
        FlashAntiDefenseTrackingAnimation.TargetLock locks =
                new FlashAntiDefenseTrackingAnimation.TargetLock();
        SourceAirDefenseController controller = controller(locks);
        FlashStandalonePlaneAnimation constructedFirst = plane(11);
        FlashStandalonePlaneAnimation constructedSecond = plane(12);
        SourceAirDefenseController.UnitVisit first = controller.register(
                constructedFirst, sounds.createOwner(AntiDefenseAudioPlayback.Type.AIR));
        SourceAirDefenseController.UnitVisit second = controller.register(
                constructedSecond, sounds.createOwner(AntiDefenseAudioPlayback.Type.AIR));

        controller.afterUnitVisit(first, false);
        controller.afterUnitVisit(second, false);
        assertNull(controller.getLockedTarget());
        assertFalse(locks.isLockedTo(DEFENDER, first));
        assertEquals(50, first.state.getCountdown());
        assertEquals(50, second.state.getCountdown());
        assertFalse(controller.takeTrackerChange());

        // Controlled source View order is second construction first. These are
        // real aircraft cores and real source-mode tickSourceFrame calls.
        tickUntilMoving(constructedSecond);
        controller.afterUnitVisit(second, false);
        assertSame(constructedSecond, controller.getLockedTarget());
        assertTrue(locks.isLockedTo(DEFENDER, second));
        assertEquals(49, second.state.getCountdown());
        assertEquals(50, first.state.getCountdown());
        assertTrue(controller.takeTrackerChange());
        assertTrue(controller.getTracker().isSourceDriven());

        tickUntilMoving(constructedFirst);
        controller.afterUnitVisit(first, false);
        assertSame(constructedSecond, controller.getLockedTarget());
        assertEquals(50, first.state.getCountdown());
        assertFalse(controller.takeTrackerChange());
        sounds.finish();
    }

    @Test public void nonownerDeathClearsLockThenDeadUnassignedUnitCanReelect()
            throws Exception {
        AntiDefenseAudioPlayback sounds = silentOwners();
        FlashAntiDefenseTrackingAnimation.TargetLock locks =
                new FlashAntiDefenseTrackingAnimation.TargetLock();
        SourceAirDefenseController controller = controller(locks);
        FlashStandalonePlaneAnimation holder = plane(21);
        FlashStandalonePlaneAnimation dying = plane(22);
        SourceAirDefenseController.UnitVisit holderVisit = controller.register(
                holder, sounds.createOwner(AntiDefenseAudioPlayback.Type.AIR));
        SourceAirDefenseController.UnitVisit dyingVisit = controller.register(
                dying, sounds.createOwner(AntiDefenseAudioPlayback.Type.AIR));

        SourceAntiDefensePlayback expected = SourceAntiDefensePlayback.forReligion("chr");
        SourceAntiDefensePlayback.UnitState expectedHolder =
                new SourceAntiDefensePlayback.UnitState();
        SourceAntiDefensePlayback.UnitState expectedDying =
                new SourceAntiDefensePlayback.UnitState();
        FlashBattleRoutes.Point anti = FlashBattleRoutes.actionBuildingPosition(DEFENDER, SLOT);
        tickUntilMoving(holder);
        controller.afterUnitVisit(holderVisit, false);
        expected.invoke(expectedHolder, true, holder.getSourcePlayback().getX(),
                holder.getSourcePlayback().getY(), anti.x, anti.y, false);
        assertSame(holder, controller.getLockedTarget());
        assertTrue(locks.isLockedTo(DEFENDER, holderVisit));
        assertEquals(0d, dyingVisit.getAntiX(), 0d);
        controller.takeTrackerChange();

        // Keep holder's core unvisited as an explicit synthetic boundary. A
        // legal mixed-unit turn may visit both; the source rules do not require
        // ownership for Unit.speedup_die or its terminal updateAnti callback.
        boolean killed = false;
        for (int visit = 0; visit < 600; visit++) {
            controller.advanceHostFrame();
            expected.advanceHostFrame();
            boolean wasAttached = dying.getSourcePlayback().isBodyAttached();
            dying.tickSourceFrame(true);
            SourceAircraftPlayback core = dying.getSourcePlayback();
            killed = wasAttached && !core.isBodyAttached();
            controller.afterUnitVisit(dyingVisit, killed);
            if (killed) {
                // Independent source callback replay: pre-move Unit.killUnit
                // uses the unassigned (0,0) anti_pos; View's generic air block
                // then elects the dead Unit and calls at its post-move XY.
                expectedDying.markKilled();
                expected.invoke(expectedDying, true,
                        core.getDeathEffectX(), core.getDeathEffectY(), 0d, 0d, true);
                expected.invoke(expectedDying, true,
                        core.getX(), core.getY(), anti.x, anti.y, true);
                assertEquals("terminal plus generic callbacks", 48,
                        dyingVisit.state.getCountdown());
                assertEquals(expectedDying.getCountdown(), dyingVisit.state.getCountdown());
                assertEquals(expectedDying.getOffset(), dyingVisit.state.getOffset());
                assertEquals(expectedDying.getRawBearingDegrees(),
                        dyingVisit.state.getRawBearingDegrees());
                assertEquals(expected.getTurretFrame(),
                        controller.getPlayback().getTurretFrame());
                assertEquals(expected.getTurretAlphaPercent(),
                        controller.getPlayback().getTurretAlphaPercent(), 0d);
                assertSame(dying, controller.getLockedTarget());
                assertFalse(locks.isLockedTo(DEFENDER, holderVisit));
                assertTrue(locks.isLockedTo(DEFENDER, dyingVisit));
                assertTrue(controller.takeTrackerChange());
                assertTrue(controller.getTracker().isSourceDriven());
                assertEquals(anti.x, dyingVisit.getAntiX(), 0d);
                assertEquals(anti.y, dyingVisit.getAntiY(), 0d);
                break;
            }
            assertSame("holder owns the lock before dying's death", holder,
                    controller.getLockedTarget());
            assertEquals(0d, dyingVisit.getAntiX(), 0d);
        }
        assertTrue("real intercepted core reached speedup_die", killed);
        sounds.finish();
    }

    @Test public void targetLockUsesOwnerIdentityPerDefenderAndLegacyCallStaysExclusive() {
        FlashAntiDefenseTrackingAnimation.TargetLock locks =
                new FlashAntiDefenseTrackingAnimation.TargetLock();
        Object first = new Object();
        Object second = new Object();
        assertTrue(locks.tryLock(1, first));
        assertFalse(locks.tryLock(1, second));
        assertTrue(locks.tryLock(2, second));
        assertTrue(locks.isLockedTo(1, first));
        assertFalse(locks.isLockedTo(1, second));
        assertTrue(locks.isLockedTo(2, second));
        locks.clear(1);
        assertFalse(locks.isLockedTo(1, first));
        assertTrue(locks.tryLock(1));
        assertFalse(locks.tryLock(1));
        assertFalse(locks.tryLock(1, first));
        assertTrue(locks.isLockedTo(2, second));
    }

    private static AntiDefenseAudioPlayback silentOwners() {
        return new AntiDefenseAudioPlayback((cue, repeatCount) -> null, Runnable::run);
    }

    private static FlashStandalonePlaneAnimation plane(int randomSeed) {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0, DEFENDER, 0, 1,
                        "plane1", true);
        return new FlashStandalonePlaneAnimation(route, 1, 0, null, null,
                new ReferenceRandom(randomSeed));
    }

    private static void tickUntilMoving(FlashStandalonePlaneAnimation plane) {
        for (int frame = 0; frame < 200; frame++) {
            plane.tickSourceFrame(true);
            if (plane.getSourcePlayback().isActiveAndMoving()) return;
        }
        throw new AssertionError("Source aircraft never activated");
    }

    private static SourceAirDefenseController controller(
            FlashAntiDefenseTrackingAnimation.TargetLock locks) throws Exception {
        Constructor<GameState.ActionBuildingEvent> constructor =
                GameState.ActionBuildingEvent.class.getDeclaredConstructor(int.class,
                        GameState.Action.class, int.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        GameState.ActionBuildingEvent event = constructor.newInstance(DEFENDER,
                GameState.Action.PREPARED_ATTACK, SLOT, "antiAir_fire", "chr", 0);
        return new SourceAirDefenseController(DEFENDER, SLOT, "chr",
                new FlashActionBuildingAnimation(event), locks);
    }
}
