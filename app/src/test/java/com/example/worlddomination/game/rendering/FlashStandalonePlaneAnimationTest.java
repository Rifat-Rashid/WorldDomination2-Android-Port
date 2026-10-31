package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashStandalonePlaneAnimationTest {
    @Test
    public void startsOnSourceTickAndSelectsTheOriginalAircraftCues() {
        FlashBattleRoutes.StandalonePlaneStrike fighterRoute =
                FlashBattleRoutes.createStandalonePlaneStrike(0, 2, 0, 1,
                        "plane1", false);
        FlashStandalonePlaneAnimation fighter = new FlashStandalonePlaneAnimation(
                fighterRoute, 3, 2, "explosion_warhead_1",
                OriginalAudio.Cue.EXPLOSION_MEDIUM);
        assertFalse(fighter.tick());
        assertFalse(fighter.takeTakeoffCue());
        assertFalse(fighter.tick());
        assertFalse(fighter.getActor().visible);
        assertFalse(fighter.tick());
        assertTrue(fighter.takeTakeoffCue());
        assertFalse(fighter.takeTakeoffCue());
        assertEquals(OriginalAudio.Cue.FIGHTER_TAKEOFF, fighter.getTakeoffCue());
        assertEquals(OriginalAudio.Cue.FIGHTER_FLIGHT, fighter.getFlightCue());
        assertEquals("explosion_warhead_1", fighter.getImpactLinkage());
        assertEquals(OriginalAudio.Cue.EXPLOSION_MEDIUM,
                fighter.getImpactCue());

        FlashStandalonePlaneAnimation bomber = new FlashStandalonePlaneAnimation(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 2, 0, 1,
                        "plane2", false), 1, 0, "explosion_bioload_2",
                OriginalAudio.Cue.EXPLOSION_MEDIUM3);
        assertTrue(bomber.loopsFlightCue());
        assertEquals(OriginalAudio.Cue.CARRIERJET_TAKEOFF, bomber.getTakeoffCue());
        assertEquals(OriginalAudio.Cue.CARRIERJET_FLIGHT, bomber.getFlightCue());
    }

    @Test
    public void reproducesSourceLiftOffAlphaScaleAndShadowTiming() {
        FlashStandalonePlaneAnimation animation = new FlashStandalonePlaneAnimation(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 2, 0, 1,
                        "plane1", false), 2, 0, "explosion_warhead_1",
                OriginalAudio.Cue.EXPLOSION_MEDIUM);
        FlashStandalonePlaneAnimation.Actor actor = animation.getActor();

        assertEquals(0f, actor.alpha, 0f);
        assertEquals(0.5f, actor.scale, 0f);
        assertEquals(0f, actor.shadowYOffset, 0f);
        for (int tick = 1; tick <= 8; tick++) {
            animation.tick();
        }
        assertEquals(0.8f, actor.alpha, 0.0001f);
        assertEquals(0.5f, actor.scale, 0f);
        assertEquals(0f, actor.shadowYOffset, 0f);

        animation.tick();
        assertEquals(0.9f, actor.alpha, 0.0001f);
        assertEquals(0.52f, actor.scale, 0.0001f);
        assertEquals(2f, actor.shadowYOffset, 0f);
        for (int tick = 10; tick <= 24; tick++) {
            animation.tick();
        }
        assertEquals(1f, actor.alpha, 0f);
        assertEquals(0.8f, actor.scale, 0.0001f);
        assertEquals(32f, actor.shadowYOffset, 0f);
    }

    @Test
    public void fliesTheSourceOrbitAndCompletesOnePayloadImpactForEachAttacker() {
        for (int attacker = 0; attacker < BattlefieldCityLayout.PLAYER_COUNT; attacker++) {
            int target = (attacker + 1) % BattlefieldCityLayout.PLAYER_COUNT;
            FlashBattleRoutes.StandalonePlaneStrike route =
                    FlashBattleRoutes.createStandalonePlaneStrike(attacker, target,
                            attacker, 1, attacker % 2 == 0 ? "plane1" : "plane2", false);
            FlashStandalonePlaneAnimation animation = new FlashStandalonePlaneAnimation(
                    route, attacker + 1, 0, "explosion_warhead_3",
                    OriginalAudio.Cue.EXPLOSION_LARGE);
            boolean sawOrbit = false;
            boolean sawFlightCue = false;
            int impacts = 0;
            for (int tick = 0; tick < 1600 && !animation.isFinished(); tick++) {
                if (animation.getPhase() == FlashStandalonePlaneAnimation.Phase.ORBIT) {
                    sawOrbit = true;
                }
                animation.tick();
                sawFlightCue |= animation.takeFlightCue();
                if (animation.takeImpact()) {
                    impacts++;
                }
            }
            assertTrue("Player " + attacker + " never entered its source orbit", sawOrbit);
            assertTrue("Player " + attacker + " never started its flight sound", sawFlightCue);
            assertTrue("Player " + attacker + " aircraft did not resolve",
                    animation.isFinished());
            assertEquals(1, impacts);
            assertFalse(animation.getActor().visible);
            assertEquals(route.bombWaypoint.x, animation.getActor().sourceX, 0f);
            assertEquals(route.bombWaypoint.y, animation.getActor().sourceY, 0f);
            assertEquals(1f, animation.getActor().routeProgress, 0f);
            assertTrue(animation.getActor().sourceFrame >= 0
                    && animation.getActor().sourceFrame < 24);
        }
    }

    @Test
    public void interceptedAircraftResolvesAtTheCityAndImpactSignalIsOneShot() {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(3, 1, 4, 2,
                        "plane2", true);
        FlashStandalonePlaneAnimation animation = new FlashStandalonePlaneAnimation(
                route, 4, 0, "explosion003", OriginalAudio.Cue.EXPLOSION_SMALL3);
        int impacts = 0;
        for (int tick = 0; tick < 1600 && !animation.isFinished(); tick++) {
            animation.tick();
            if (animation.takeImpact()) {
                impacts++;
            }
        }
        assertTrue(animation.isFinished());
        assertEquals(1, impacts);
        assertEquals(route.target.x, animation.getActor().sourceX, 0f);
        assertEquals(route.target.y, animation.getActor().sourceY, 0f);
    }

    @Test
    public void rejectsInvalidAnimationInputs() {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 0, 1,
                        "plane1", false);
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new FlashStandalonePlaneAnimation(null, 1, 0, null, null));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new FlashStandalonePlaneAnimation(route, 0, 0, null, null));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new FlashStandalonePlaneAnimation(route, 1, -1, null, null));
    }
}
