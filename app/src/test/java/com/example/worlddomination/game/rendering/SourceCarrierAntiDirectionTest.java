package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Clean, legal ship captures in analysis/carrier-anti-angle-next/ship-probe-0-{1,2}.json. */
public final class SourceCarrierAntiDirectionTest {
    private static final double DEFENSE_X = 130.75d;
    private static final double DEFENSE_Y = 91.75d;

    @Test public void waitingClipOnlyBecomesTrackableOnItsFirstActiveRecordVisit() {
        ReferenceRandom random = new ReferenceRandom(0L);
        FlashCarrierPlaneAnimation adapter = animation(1, true, random);
        SourceCarrierPlanePlayback core = adapter.getSourcePlayback();
        assertFalse(core.isActiveAndMoving());
        core.applySourceAntiDirection(DEFENSE_X, DEFENSE_Y);
        assertEquals(0d, core.getAngle(), 0d);

        for (int tick = 1; tick < 195; tick++) {
            adapter.tickSourceFrame(false);
            assertTrue("the source waiting clip is drawable", adapter.getActor().visible);
            assertFalse("a waiting record is not a defense target", adapter.isVisible());
            assertFalse(core.isActiveAndMoving());
        }
        adapter.tickSourceFrame(true); // Source record activation at global tick 195.
        assertEquals("waitForLiftOff", core.getAction());
        assertEquals(0, core.getTrail().getFramesActive());
        assertTrue(core.isActiveAndMoving());
        assertTrue(adapter.isVisible());
        int bodyFrame = core.getSourceFrame();
        double randomNext = random.copy().nextDouble();
        core.applySourceAntiDirection(DEFENSE_X, DEFENSE_Y);
        assertEquals("source stored angle after View.updateAnti at 195", 185d,
                core.getAngle(), 0d);
        assertEquals(bodyFrame, core.getSourceFrame());
        assertEquals(randomNext, random.copy().nextDouble(), 0d);
    }

    @Test public void interceptedUnitThreeUsesRetainedAntiBearingForFirstSmoke() {
        ReferenceRandom random = new ReferenceRandom(0L);
        SourceCarrierPlanePlayback core = animation(1, true, random).getSourcePlayback();
        for (int tick = 1; tick <= 239; tick++) {
            core.tickSourceFrame(tick >= 195);
            if (tick == 239) {
                assertEquals(3, core.getTrail().getCreatedCount());
                assertEquals(423.52740930490853d, core.getTrail().getX(0), 1e-10d);
                assertEquals(92.79671912485887d, core.getTrail().getY(0), 1e-10d);
            }
            if (core.isActiveAndMoving()) {
                int bodyFrame = core.getSourceFrame();
                double next = random.copy().nextDouble();
                core.applySourceAntiDirection(DEFENSE_X, DEFENSE_Y);
                assertEquals("anti callback does not seek the body", bodyFrame,
                        core.getSourceFrame());
                assertEquals("anti callback does not draw random", next,
                        random.copy().nextDouble(), 0d);
                if (tick == 238) assertEquals("pre-smoke retained bearing", 183d,
                        core.getAngle(), 0d);
            }
        }
        for (int tick = 240; tick <= 270; tick++) {
            core.tickSourceFrame(true);
            if (core.isActiveAndMoving()) core.applySourceAntiDirection(DEFENSE_X, DEFENSE_Y);
        }
        assertTrue("source unit3 wrapper removed after its intercepted impact", core.isRemoved());
        double finalAngle = core.getAngle();
        core.applySourceAntiDirection(DEFENSE_X, DEFENSE_Y);
        assertEquals("removed wrappers cannot acquire new bearings", finalAngle,
                core.getAngle(), 0d);
    }

    @Test public void successfulUnitThreeRetainsOrdinaryDirectionWithoutAntiCallback() {
        ReferenceRandom random = new ReferenceRandom(0L);
        SourceCarrierPlanePlayback core = animation(2, false, random).getSourcePlayback();
        for (int tick = 1; tick <= 278; tick++) {
            core.tickSourceFrame(tick >= 234);
        }
        assertEquals(3, core.getTrail().getCreatedCount());
        assertEquals(457.39316740917997d, core.getTrail().getX(0), 1e-10d);
        assertEquals(77.36320399322697d, core.getTrail().getY(0), 1e-10d);
    }

    private static FlashCarrierPlaneAnimation animation(int defender, boolean intercepted,
                                                        ReferenceRandom random) {
        // Native physical route index 2 is source unit3 after the constructor's
        // carrier-offset reversal (SourceCarrierPlanePlayback.java:48-59).
        return new FlashCarrierPlaneAnimation(
                FlashBattleRoutes.createCarrierPlaneStrike(0, defender, 2, 2, intercepted),
                0, "explosion003", OriginalAudio.Cue.EXPLOSION_SMALL3, random);
    }
}
