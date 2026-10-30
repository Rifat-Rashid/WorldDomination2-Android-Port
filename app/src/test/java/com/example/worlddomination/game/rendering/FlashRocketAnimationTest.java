package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashRocketAnimationTest {
    @Test
    public void waitsForItsSourceDelayThenStartsFromTheFlashSiloArc() {
        FlashBattleRoutes.RocketStrike route =
                FlashBattleRoutes.createRocketStrike(1, 2, 3, 2, false);
        FlashRocketAnimation animation = new FlashRocketAnimation(route, 2,
                "explosion_warhead_1", OriginalAudio.Cue.EXPLOSION_SMALL3);

        assertFalse(animation.tick());
        assertFalse(animation.takeLaunchSound());
        assertFalse(animation.tick());
        assertFalse(animation.takeLaunchSound());
        assertFalse(animation.tick());

        assertTrue(animation.takeLaunchSound());
        assertEquals(OriginalAudio.Cue.WARROCKET, animation.getLaunchCue());
        assertEquals(route.launch.x, animation.getRocket().sourceX, 0f);
        assertEquals(route.ellipseCenterY, animation.getRocket().sourceY, 0.001f);
        assertEquals(route.attackerPlayerId, animation.getRocket().islandPlayerId);
        assertEquals("explosion_warhead_1", animation.getImpactLinkage());
        assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3, animation.getImpactCue());
    }

    @Test
    public void followsTheSourceHalfEllipseThenDropsTenPixelsPerTick() {
        FlashBattleRoutes.RocketStrike route =
                FlashBattleRoutes.createRocketStrike(0, 1, 0, 1, false);
        FlashRocketAnimation animation = new FlashRocketAnimation(route, 0);
        assertFalse(animation.tick());

        while (!animation.isDropping()) {
            assertFalse(animation.tick());
        }
        assertEquals(route.target.x, animation.getRocket().sourceX, 0.001f);
        assertEquals(route.ellipseCenterY, animation.getRocket().sourceY, 0.001f);

        float beforeDrop = animation.getRocket().sourceY;
        assertFalse(animation.tick());
        assertEquals(beforeDrop + 10f, animation.getRocket().sourceY, 0.001f);

        boolean impact = false;
        for (int tick = 0; tick < 100 && !impact; tick++) {
            impact = animation.tick();
        }
        assertTrue("Rocket must report its city impact exactly once", impact);
        assertTrue(animation.isFlightFinished());
        assertEquals(route.target.y - 20f, animation.getRocket().sourceY, 0.001f);
        assertFalse(animation.getRocket().visible);
        assertFalse(animation.tick());
    }

    @Test
    public void interceptedRocketDropsAboveTheTargetAndOriginalSmokeFadesOut() {
        FlashBattleRoutes.RocketStrike route =
                FlashBattleRoutes.createRocketStrike(3, 2, 4, 2, true);
        FlashRocketAnimation animation = new FlashRocketAnimation(route, 0,
                "explosion002", OriginalAudio.Cue.EXPLOSION_SMALL3);
        boolean impact = false;
        for (int tick = 0; tick < 200 && !impact; tick++) {
            impact = animation.tick();
        }

        assertTrue(impact);
        assertTrue(route.intercepted);
        assertTrue(animation.getRocket().sourceY >= route.target.y - 120f);
        assertTrue("Intercepted rocket should explode above its target city",
                animation.getRocket().sourceY < route.target.y);
        assertEquals("explosion002", animation.getImpactLinkage());
        assertTrue(animation.getTrailCount() > 0);
        for (int tick = 0; tick < 50; tick++) {
            animation.tick();
        }
        assertEquals(0, animation.getTrailCount());
        assertTrue(animation.isFinished());
    }
}
