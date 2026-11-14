package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashChinookTankAnimationTest {
    @Test
    public void playsDelayedChinookDropTankRouteAndSourceGunCues() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 0, 2, "blue", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 3);

        assertEquals(3, animation.getActorCount());
        assertEquals("chinook", animation.getActor(0).linkage);
        assertEquals("tank_noshade", animation.getActor(1).linkage);
        assertEquals("tankTop_blue", animation.getActor(2).linkage);
        for (int actor = 0; actor < 3; actor++) {
            assertTrue("Source-attached body is visible at construction",
                    animation.getActor(actor).visible);
            assertEquals("Attached MovieClip starts at battle-parent origin", 0f,
                    animation.getActor(actor).renderX, 0f);
            assertEquals(0f, animation.getActor(actor).renderY, 0f);
        }
        assertEquals(420f, animation.getChinookShadow().renderX, 0f);
        assertEquals(310f, animation.getChinookShadow().renderY, 0f);
        for (int i = 0; i < 3; i++) {
            animation.tick();
            assertTrue(animation.getActor(0).visible);
            assertFalse("Waiting visits do not start the chopper cue",
                    animation.takeAudioCue());
        }
        animation.tick();
        assertTrue(animation.getActor(0).visible);
        assertEquals(OriginalAudio.Cue.TANK_CHOPPER, takeCue(animation));

        boolean tankDeployed = false;
        boolean tankDrove = false;
        int ticks = 0;
        while (!animation.isFinished() && ticks < 1800) {
            animation.tick();
            OriginalAudio.Cue cue;
            while ((cue = takeCueOrNull(animation)) != null) {
                if (cue == OriginalAudio.Cue.TANK_DRIVE) {
                    tankDrove = true;
                }
                if (cue == OriginalAudio.Cue.TANK_GUN) {
                    assertTrue("The source tank must drive before its turret fires", tankDrove);
                    assertTrue("Tank actors must be visible when firing",
                            animation.getActor(1).visible && animation.getActor(2).visible);
                }
            }
            if (animation.isTankDeployed() && animation.getActor(1).visible) {
                tankDeployed = true;
                assertEquals(route.targetPlayerId, animation.getActor(1).islandPlayerId);
                assertEquals(route.targetPlayerId, animation.getActor(2).islandPlayerId);
                assertEquals(animation.getActor(1).sourceX,
                        animation.getActor(2).sourceX, 0f);
                assertEquals(animation.getActor(1).sourceY,
                        animation.getActor(2).sourceY, 0f);
            }
            ticks++;
        }

        assertTrue("The source unit route should deploy its tank", tankDeployed);
        assertTrue("The source unit route should play the tank engine cue", tankDrove);
        assertTrue("Chinook and tank source sequences should finish", animation.isFinished());
        assertTrue("Stopped Chinook body survives until allUnitsDone", animation.getActor(0).visible);
        assertTrue("Stopped tank body survives until allUnitsDone", animation.getActor(1).visible);
        assertTrue("Stopped tank top survives until allUnitsDone", animation.getActor(2).visible);
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        animation.registerStoppedClips(queue);
        assertEquals(3, queue.size());
        assertEquals(3, queue.removeAtAllUnitsDone());
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.getActor(1).visible);
        assertFalse(animation.getActor(2).visible);
        assertFalse(animation.getChinookShadow().visible);
        // flyOff's 250 counter advances twice on ordinary source ticks.
        assertTrue("The source countdown takes at least 127 flyOff ticks", ticks >= 127);
    }

    @Test
    public void waitingAndActiveVisitsInvokeOneCarrierBaseTopCallbackEach() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 2, 1, "green", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 2);
        List<Integer> visits = new ArrayList<>();
        animation.setActorFrameListener((actor, sourceY, changed) -> visits.add(actor));
        animation.tick();
        assertEquals(Arrays.asList(0, 1, 2), visits);
        visits.clear();
        animation.tick();
        assertEquals(Arrays.asList(0, 1, 2), visits);
        visits.clear();
        animation.tick();
        assertEquals(Arrays.asList(0, 1, 2), visits);
        assertEquals(OriginalAudio.Cue.TANK_CHOPPER, takeCue(animation));
    }

    @Test
    public void inactiveChinookSeeksAuthoredSevenEightNineThenAlternates()
            throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 2, 1, "green", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 8);
        int[] expectedZeroBased = {6, 7, 8, 7, 8};
        for (int frame : expectedZeroBased) {
            animation.presentWaitingFrame();
            assertEquals("View's inactive Chinook fixedFrame seek", frame,
                    animation.getActor(0).sourceFrame);
            assertEquals(route.chinookStart.x, animation.getActor(0).renderX, 0f);
            assertEquals(route.chinookStart.y, animation.getActor(0).renderY, 0f);
            assertEquals(animation.getActor(1).sourceX,
                    animation.getActor(1).renderX, 0f);
            assertEquals(animation.getActor(2).sourceY,
                    animation.getActor(2).renderY, 0f);
        }
        assertFalse("Waiting seeks do not start activation audio",
                animation.takeAudioCue());
    }

    @Test
    public void rejectsMissingRouteAndNegativeDelay() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 0, 1, "blue", loadAnchors());
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new FlashChinookTankAnimation(null, 0));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new FlashChinookTankAnimation(route, -1));
    }

    @Test
    public void shortTankRouteKeepsChinookUntilItsSourceFlyOffCountdownEnds() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                4, 0, 0, 1, "green", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 0);
        int ticks = 0;
        while (!animation.isTankDeployed() && ticks < 200) {
            animation.tick();
            ticks++;
        }
        assertTrue("Chinook must deploy the tank", animation.getActor(1).visible);

        while (!animation.isTankStopped() && ticks < 700) {
            animation.tick();
            ticks++;
        }
        assertTrue("The tank should stop this short source route first",
                animation.isTankStopped());
        assertTrue("The stopped tank clip remains attached", animation.getActor(1).visible);
        assertTrue("Chinook remains through its 250-count flyOff countdown",
                animation.getActor(0).visible);
        assertFalse("The overall presentation waits for the Chinook", animation.isFinished());

        while (!animation.isFinished() && ticks < 800) {
            animation.tick();
            ticks++;
        }
        assertTrue(animation.isFinished());
        assertTrue("Removed Chinook clip survives until allUnitsDone", animation.getActor(0).visible);
    }

    @Test
    public void cityImpactIsTakenOnceOnPostShotStopBeforeWrapperRemoval() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 0, 2, "blue", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 3);
        for (int waiting = 0; waiting < 3; waiting++) {
            animation.tick();
            assertFalse(animation.takeCityImpact());
        }

        int activeVisits = 0;
        while (!animation.isTankStopped() && activeVisits < 900) {
            assertFalse("No impact precedes the source stop visit",
                    animation.takeCityImpact());
            animation.tick();
            activeVisits++;
        }
        assertTrue("The tank must reach its post-60-shot stop visit",
                animation.isTankStopped());
        Field shotCount = FlashChinookTankAnimation.class.getDeclaredField("shotCount");
        shotCount.setAccessible(true);
        assertEquals("Impact follows all 60 source shots", 60,
                shotCount.getInt(animation));
        assertFalse("Stop and wrapper removal have separate source visits",
                animation.isTankRemoved());
        assertTrue(animation.takeCityImpact());
        assertFalse("City impact is a one-shot event", animation.takeCityImpact());

        animation.tick();
        assertTrue(animation.isTankRemoved());
        assertFalse(animation.takeCityImpact());
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        animation.registerStoppedClips(queue);
        queue.removeAtAllUnitsDone();
        assertFalse("Cleanup cannot replay the impact", animation.takeCityImpact());
    }

    @Test
    public void turretFacesTheCityWhileTankDrivesToItsOffsetFirePoint() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 2, 0, 1, "blue", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 0);
        FlashChinookTankAnimation.Actor tank = animation.getActor(1);
        tank.sourceX = 490f;
        tank.sourceY = 90f;

        Method moveTank = FlashChinookTankAnimation.class.getDeclaredMethod(
                "moveTankAlongSourceRoute");
        moveTank.setAccessible(true);
        assertFalse((Boolean) moveTank.invoke(animation));

        FlashChinookTankAnimation.Actor turret = animation.getActor(2);
        assertEquals(492f, tank.sourceX, 0f);
        assertEquals(90f, tank.sourceY, 0f);
        assertEquals(492f, turret.sourceX, 0f);
        assertEquals(90f, turret.sourceY, 0f);
        // Source tankTop follows the base position, and its direction uses
        // the city-center waypoint (541, 79). Flash frame 19 is index 18.
        assertEquals(18, turret.sourceFrame);

        Field phaseField = FlashChinookTankAnimation.class.getDeclaredField("phase");
        phaseField.setAccessible(true);
        for (Object phase : phaseField.getType().getEnumConstants()) {
            if ("TANK_FIRE".equals(phase.toString())) {
                phaseField.set(animation, phase);
                break;
            }
        }
        for (int tick = 0; tick < 26; tick++) {
            animation.tick();
            assertEquals(18, turret.sourceFrame);
        }
        animation.tick();
        // Source nextFrame advances gotoAndStop(19) to Flash frame 20.
        assertEquals(19, turret.sourceFrame);
        assertEquals(OriginalAudio.Cue.TANK_GUN, takeCue(animation));
    }

    @Test
    public void arrivingAtFinalWaypointReaimsTurretBeforeItsFirstShot() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 2, 0, 1, "blue", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 0);
        FlashChinookTankAnimation.Actor tank = animation.getActor(1);
        tank.sourceX = 541f;
        tank.sourceY = 104f;
        Field waypoint = FlashChinookTankAnimation.class.getDeclaredField("tankWaypointIndex");
        waypoint.setAccessible(true);
        waypoint.setInt(animation, route.tankWaypoints.length - 1);
        animation.getActor(2).sourceFrame = 0;
        Method moveTank = FlashChinookTankAnimation.class.getDeclaredMethod(
                "moveTankAlongSourceRoute");
        moveTank.setAccessible(true);
        assertTrue((Boolean) moveTank.invoke(animation));
        assertEquals(541f, animation.getActor(2).sourceX, 0f);
        assertEquals(104f, animation.getActor(2).sourceY, 0f);
        // Executed source Unit: tankTopDirection=14, gotoAndStop(27).
        assertEquals(26, animation.getActor(2).sourceFrame);
        Field phaseField = FlashChinookTankAnimation.class.getDeclaredField("phase");
        phaseField.setAccessible(true);
        for (Object phase : phaseField.getType().getEnumConstants()) {
            if ("TANK_FIRE".equals(phase.toString())) phaseField.set(animation, phase);
        }
        for (int tick = 0; tick < 26; tick++) animation.tick();
        assertEquals(26, animation.getActor(2).sourceFrame);
        animation.tick();
        assertEquals(27, animation.getActor(2).sourceFrame);
        assertEquals(OriginalAudio.Cue.TANK_GUN, takeCue(animation));
    }

    @Test
    public void sourceFiringTracePreservesFlashHoldGunCadenceAndFinalStopTick() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 2, 0, 1, "blue", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 0);
        Field phaseField = FlashChinookTankAnimation.class.getDeclaredField("phase");
        phaseField.setAccessible(true);
        for (Object phase : phaseField.getType().getEnumConstants()) {
            if ("TANK_FIRE".equals(phase.toString())) phaseField.set(animation, phase);
        }
        for (int actor = 1; actor <= 2; actor++) {
            animation.getActor(actor).sourceX = 491f;
            animation.getActor(actor).sourceY = 90f;
            animation.getActor(actor).visible = true;
        }
        animation.getActor(2).sourceFrame = 18;
        List<Integer> gunTicks = new ArrayList<>();
        // Executed generated Unit.moveUnit, starting its firing counter at zero.
        for (int tick = 1; tick <= 85; tick++) {
            animation.tick();
            boolean flash = (tick >= 27 && tick <= 30) || (tick >= 53 && tick <= 56);
            assertEquals("Source flash frame at firing tick " + tick,
                    flash ? 19 : 18, animation.getActor(2).sourceFrame);
            assertTrue("Source base clip retained at firing tick " + tick,
                    animation.getActor(1).visible);
            assertTrue("Source turret clip retained at firing tick " + tick,
                    animation.getActor(2).visible);
            assertEquals("Base stop tick", tick >= 61, animation.isTankStopped());
            assertEquals("Turret stop tick", tick >= 61, animation.isTurretStopped());
            assertEquals("Base removal tick", tick >= 62, animation.isTankRemoved());
            assertEquals("Turret removal tick", tick >= 62, animation.isTurretRemoved());
            OriginalAudio.Cue cue;
            while ((cue = takeCueOrNull(animation)) != null) {
                assertEquals(OriginalAudio.Cue.TANK_GUN, cue);
                gunTicks.add(tick);
            }
        }
        assertEquals(Arrays.asList(27, 53), gunTicks);
    }

    @Test
    public void tankFirstRouteQueuesByRemovalTickAndChinookTakesFinalStep() throws Exception {
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 0, 2, "blue", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 0);
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        int ticks = 0;
        while (!animation.isChinookStopped() && ticks++ < 900) {
            animation.tick();
            animation.registerStoppedClips(queue);
        }
        assertTrue("Source flyOff reaches stop", animation.isChinookStopped());
        assertFalse("View removes Chinook on the following tick", animation.isChinookRemoved());
        // On this short 0->1 route both tank wrappers are removed earlier.
        // Queue order follows removal ticks; only same-tick wrappers use
        // View's physical body-before-top visitation order.
        assertTrue(animation.isTankRemoved());
        assertTrue(animation.isTurretRemoved());
        assertEquals(2, queue.size());
        float stoppedX = animation.getActor(0).sourceX;
        float stoppedY = animation.getActor(0).sourceY;
        Field xStep = FlashChinookTankAnimation.class.getDeclaredField("chinookXStep");
        Field yStep = FlashChinookTankAnimation.class.getDeclaredField("chinookYStep");
        xStep.setAccessible(true);
        yStep.setAccessible(true);
        float lastXStep = xStep.getFloat(animation);
        float lastYStep = yStep.getFloat(animation);
        animation.tick();
        animation.registerStoppedClips(queue);
        assertTrue(animation.isChinookRemoved());
        assertEquals(stoppedX + lastXStep, animation.getActor(0).sourceX, 0f);
        assertEquals(stoppedY + lastYStep, animation.getActor(0).sourceY, 0f);
        assertTrue("Removed Chinook and shadow clips remain visible",
                animation.getActor(0).visible && animation.getChinookShadow().visible);
        assertEquals(3, queue.size());
        while (!animation.isFinished() && ticks++ < 900) {
            animation.tick();
            animation.registerStoppedClips(queue);
        }
        assertTrue("All three source wrappers are removed", animation.isFinished());
        assertEquals(3, queue.size());
        Field clips = SourceStoppedClipQueue.class.getDeclaredField("clips");
        clips.setAccessible(true);
        List<?> queued = (List<?>) clips.get(queue);
        assertTrue(queued.get(0) == animation.getActor(1));
        assertTrue(queued.get(1) == animation.getActor(2));
        assertTrue(queued.get(2) == animation.getActor(0));
        animation.registerStoppedClips(queue);
        assertEquals("Wrappers enqueue only once", 3, queue.size());
        assertTrue(animation.getActor(1).visible && animation.getActor(2).visible);
        assertEquals(3, queue.removeAtAllUnitsDone());
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.getChinookShadow().visible);
        assertFalse(animation.getActor(1).visible);
        assertFalse(animation.getActor(2).visible);
    }

    @Test
    public void canonicalPlayerOneStrikeQueuesChinookBeforeTankBodyAndTop() throws Exception {
        // Valid source turn 28: weapon 14, player 1 -> 0, target city 3.
        // View removes Chinook first, then body and turret on their shared tick.
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                1, 0, 3, 1, "blue", loadAnchors());
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 0);
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        int chinookRemoval = -1;
        int tankRemoval = -1;
        for (int tick = 1; tick <= 900 && !animation.isFinished(); tick++) {
            animation.tick();
            animation.registerStoppedClips(queue);
            if (chinookRemoval < 0 && animation.isChinookRemoved()) chinookRemoval = tick;
            if (tankRemoval < 0 && animation.isTankRemoved()) tankRemoval = tick;
        }
        assertTrue(animation.isFinished());
        assertTrue("Source Chinook removes before the tank wrappers",
                chinookRemoval > 0 && chinookRemoval < tankRemoval);
        assertEquals(3, queue.size());
        Field clips = SourceStoppedClipQueue.class.getDeclaredField("clips");
        clips.setAccessible(true);
        List<?> queued = (List<?>) clips.get(queue);
        assertTrue(queued.get(0) == animation.getActor(0));
        assertTrue(queued.get(1) == animation.getActor(1));
        assertTrue(queued.get(2) == animation.getActor(2));
        assertTrue(animation.getActor(0).visible);
        assertTrue(animation.getActor(1).visible);
        assertTrue(animation.getActor(2).visible);
        assertEquals(3, queue.removeAtAllUnitsDone());
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.getChinookShadow().visible);
        assertFalse(animation.getActor(1).visible);
        assertFalse(animation.getActor(2).visible);
    }

    private static OriginalAudio.Cue takeCue(FlashChinookTankAnimation animation) {
        assertTrue(animation.takeAudioCue());
        return animation.getAudioCue();
    }

    private static OriginalAudio.Cue takeCueOrNull(FlashChinookTankAnimation animation) {
        return animation.takeAudioCue() ? animation.getAudioCue() : null;
    }

    private static Map<String, FlashBattleRoutes.Point> loadAnchors() throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        return FlashBattleRoutes.parseAnchors(json);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(moduleAsset)) {
            return moduleAsset;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
