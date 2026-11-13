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
        assertEquals("tankBase", animation.getActor(1).linkage);
        assertEquals("tankTop_blue", animation.getActor(2).linkage);
        for (int i = 0; i < 3; i++) {
            animation.tick();
            assertFalse(animation.getActor(0).visible);
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
            if (animation.getActor(1).visible) {
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
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.getActor(1).visible);
        assertFalse(animation.getActor(2).visible);
        // flyOff's 250 counter advances twice on ordinary source ticks.
        assertTrue("The source countdown takes at least 127 flyOff ticks", ticks >= 127);
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
        while (!animation.getActor(1).visible && ticks < 200) {
            animation.tick();
            ticks++;
        }
        assertTrue("Chinook must deploy the tank", animation.getActor(1).visible);

        while (animation.getActor(1).visible && ticks < 700) {
            animation.tick();
            ticks++;
        }
        assertFalse("The tank should finish this short source route first",
                animation.getActor(1).visible);
        assertTrue("Chinook remains through its 250-count flyOff countdown",
                animation.getActor(0).visible);
        assertFalse("The overall presentation waits for the Chinook", animation.isFinished());

        while (!animation.isFinished() && ticks < 800) {
            animation.tick();
            ticks++;
        }
        assertTrue(animation.isFinished());
        assertFalse(animation.getActor(0).visible);
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
            assertEquals("Source base visibility at firing tick " + tick,
                    tick <= 60, animation.getActor(1).visible);
            assertEquals(tick <= 60, animation.getActor(2).visible);
            assertEquals("Source stop tick", tick >= 61, animation.isFinished());
            OriginalAudio.Cue cue;
            while ((cue = takeCueOrNull(animation)) != null) {
                assertEquals(OriginalAudio.Cue.TANK_GUN, cue);
                gunTicks.add(tick);
            }
        }
        assertEquals(Arrays.asList(27, 53), gunTicks);
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
