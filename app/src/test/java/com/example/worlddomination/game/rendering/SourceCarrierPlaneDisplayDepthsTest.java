package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import static org.junit.Assert.*;

/** Root display occupancy from the legal intercepted and successful ship captures. */
public final class SourceCarrierPlaneDisplayDepthsTest {
    @Test public void interceptedShipAttachesExplosion001BeforeThirdPlaneSmoke()
            throws Exception {
        Fixture fixture = fixture(1, true, "explosion003", 800125);
        SourceCarrierPlaneDisplayDepths binding = fixture.binding;
        assertEquals(22509, binding.body().getDepth());
        assertEquals(22510, binding.shadow().getDepth());
        assertEquals(22511, binding.tint().getDepth());
        assertEquals(22512, binding.dot().getDepth());
        for (int tick = 1; tick <= 238; tick++) {
            binding.advanceAttachedTimelines();
            boolean impact = fixture.animation.tickSourceFrame(tick >= 195);
            binding.afterUnitFrame(impact);
            assertEquals("source unit1 impact only at tick238", tick == 238, impact);
        }
        assertFalse("killUnit removed body", binding.body().isAttached());
        assertFalse("killUnit removed shadow", binding.shadow().isAttached());
        assertFalse("killUnit removed tint", binding.tint().isAttached());
        assertTrue("target dot retained", binding.dot().isAttached());
        assertEquals("ship_plane explode uses killUnit(default)", "explosion001",
                binding.getExplosionLinkage());
        assertEquals(800126, binding.explosion().getDepth());
        assertEquals("t239 unit3 next root child", 800127, fixture.display.nextDepth());
        assertEquals("birth is authored frame1", 0, binding.getExplosionFrameIndex());
        assertEquals(58.5d, binding.getExplosionX(), 0d);
        assertEquals(165.75d, binding.getExplosionY(), 0d);
        for (int tick = 239; tick <= 280; tick++) binding.advanceAttachedTimelines();
        assertEquals("DefineSprite_442 frame21 stop", 20, binding.getExplosionFrameIndex());
        // The wrapper is visited once more after kill and then queued for
        // Presenter.allUnitsDone; the stopped effect stays physically present.
        fixture.animation.tickSourceFrame(true);
        binding.afterUnitFrame(false);
        assertEquals(1, fixture.stopped.size());
        assertTrue(binding.explosion().isAttached());
        fixture.stopped.removeAtAllUnitsDone();
        assertFalse(binding.explosion().isAttached());
        assertTrue("source dot survives deletion-list cleanup", binding.dot().isAttached());
    }

    @Test public void successfulShipRetainsBioloadImpactAtWaypointPlus104()
            throws Exception {
        Fixture fixture = fixture(2, false, "explosion_bioload_3", 800175);
        SourceCarrierPlaneDisplayDepths binding = fixture.binding;
        for (int tick = 1; tick <= 296; tick++) {
            binding.advanceAttachedTimelines();
            boolean impact = fixture.animation.tickSourceFrame(tick >= 234);
            binding.afterUnitFrame(impact);
            assertEquals("source unit1 payload only at tick296", tick == 296, impact);
        }
        assertTrue("flyOff keeps body", binding.body().isAttached());
        assertEquals("explosion_bioload_3", binding.getExplosionLinkage());
        assertEquals(800176, binding.explosion().getDepth());
        assertEquals(541d, binding.getExplosionX(), 0d);
        assertEquals(83d, binding.getExplosionY(), 0d);
        assertEquals(0, binding.getExplosionFrameIndex());
        for (int tick = 297; tick <= 425; tick++) {
            binding.advanceAttachedTimelines();
            fixture.animation.tickSourceFrame(true);
            binding.afterUnitFrame(false);
            if (tick == 400) assertEquals("bioload3 authored frame55 stop", 54,
                    binding.getExplosionFrameIndex());
        }
        assertEquals("one retained wrapper cleanup entry", 1, fixture.stopped.size());
        assertTrue(binding.explosion().isAttached());
        fixture.stopped.removeAtAllUnitsDone();
        assertFalse(binding.body().isAttached());
        assertFalse(binding.explosion().isAttached());
        assertTrue(binding.dot().isAttached());
    }

    private static Fixture fixture(int defender, boolean intercepted, String genericLinkage,
                                   int precedingHighestDepth) {
        BattlefieldDisplayState display = new BattlefieldDisplayState();
        display.attach(BattlefieldDisplayState.Kind.AUTHORED, 0, "unit1", null, 22509);
        display.attach(BattlefieldDisplayState.Kind.AUTHORED, 0, "shadow_unit1", null, 22510);
        display.attach(BattlefieldDisplayState.Kind.AUTHORED, 0, "tintunit1", null, 22511);
        display.attach(BattlefieldDisplayState.Kind.AUTHORED, 0, "targetDotunit1", null, 22512);
        // Source alias-repaired parent snapshot immediately before unit1 impact.
        display.attach(BattlefieldDisplayState.Kind.AUTHORED, 0, "precedingRootChild",
                null, precedingHighestDepth);
        FlashCarrierPlaneAnimation animation = new FlashCarrierPlaneAnimation(
                FlashBattleRoutes.createCarrierPlaneStrike(0, defender, 0, 0, intercepted),
                0, genericLinkage, null, new ReferenceRandom(0L), 3);
        SourceStoppedClipQueue stopped = new SourceStoppedClipQueue();
        return new Fixture(display, animation, stopped,
                new SourceCarrierPlaneDisplayDepths(display, animation, 0, stopped));
    }

    private static final class Fixture {
        final BattlefieldDisplayState display;
        final FlashCarrierPlaneAnimation animation;
        final SourceStoppedClipQueue stopped;
        final SourceCarrierPlaneDisplayDepths binding;
        Fixture(BattlefieldDisplayState display, FlashCarrierPlaneAnimation animation,
                SourceStoppedClipQueue stopped, SourceCarrierPlaneDisplayDepths binding) {
            this.display = display;
            this.animation = animation;
            this.stopped = stopped;
            this.binding = binding;
        }
    }
}
