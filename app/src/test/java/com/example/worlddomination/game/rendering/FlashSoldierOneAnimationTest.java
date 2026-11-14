package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashSoldierOneAnimationTest {
    @Test public void firstActiveBoatVisitEmitsOnlyItsPostMovementDepthCallback() throws Exception {
        FlashSoldierOneAnimation animation = createAnimation(0);
        int[] callbacks = {0};
        animation.setActorFrameListener((actor, y, changed) -> {
            assertEquals(0, actor);
            callbacks[0]++;
        });
        animation.tickSourceFrame();
        assertEquals("initial pose setup must not swap before movement", 1, callbacks[0]);
        assertEquals(408f, animation.getActor(0).sourceX, 0f);
        assertEquals(476f, animation.getActor(0).sourceY, 0f);
        animation.presentWaitingFrame();
        assertEquals("an already active boat has no waiting visit", 1, callbacks[0]);
        animation.tickSourceFrame();
        assertEquals(2, callbacks[0]);
    }

    @Test
    public void waitingBoatSeeksSourceStartOnFirstViewFrameBeforeItsRecord() throws Exception {
        FlashSoldierOneAnimation animation = createAnimation(2);
        FlashSoldierOneAnimation.Actor boat = animation.getActor(0);

        assertTrue(boat.visible);
        assertEquals("attached source MC starts at parent origin", 0f, boat.sourceX, 0f);
        assertEquals(0f, boat.sourceY, 0f);
        animation.tick();
        assertTrue(boat.visible);
        assertEquals("View paints curX even before record activation", 410f, boat.sourceX, 0f);
        assertEquals(474f, boat.sourceY, 0f);
        animation.tick();
        assertTrue(boat.visible);
        assertEquals(410f, boat.sourceX, 0f);
        assertEquals(474f, boat.sourceY, 0f);
        animation.tick();
        assertTrue(boat.visible);
        assertEquals("boat001", boat.linkage);
    }

    @Test
    public void unloadsThreeSoldiersAndEmitsOneSourceGunCuePerUnit() throws Exception {
        FlashSoldierOneAnimation animation = createAnimation(0);
        boolean[] becameVisible = new boolean[3];
        int gunCueCount = 0;
        int tick;
        for (tick = 0; tick < 6000 && !animation.isFinished(); tick++) {
            animation.tick();
            for (int soldier = 0; soldier < 3; soldier++) {
                becameVisible[soldier] |= animation.getActor(soldier + 1).visible;
            }
            if (animation.takeAudioCue()) {
                gunCueCount++;
            }
        }

        assertTrue("The boat and infantry should complete their source playback", tick < 6000);
        assertEquals(3, gunCueCount);
        for (boolean visible : becameVisible) {
            assertTrue("Each transported infantry actor should become visible", visible);
        }
        for (int soldier = 0; soldier < 3; soldier++) {
            assertTrue("Stopped source clips remain until allUnitsDone",
                    animation.getActor(soldier + 1).visible);
        }
        assertTrue(animation.getActor(0).visible);
        animation.clearStoppedActors();
        for (int actor = 0; actor < animation.getActorCount(); actor++) {
            assertFalse(animation.getActor(actor).visible);
        }
        assertFalse(animation.takeAudioCue());
    }

    @Test
    public void retainsStoppedInfantryAcrossShotStopAndRemovalBoundaries() throws Exception {
        FlashSoldierOneAnimation animation = createAnimation(0);
        FlashSoldierOneAnimation.Actor first = null;
        int firstActorIndex = -1;
        int guard = 0;
        while (first == null && guard++ < 6000) {
            assertFalse(animation.tickSourceFrame());
            // Flash processes unit1, unit2, unit3 in this physical order.
            for (int actor = 3; actor >= 1; actor--) {
                if ("soldier001_shoot".equals(animation.getActor(actor).linkage)) {
                    first = animation.getActor(actor);
                    firstActorIndex = actor;
                    break;
                }
            }
        }
        assertTrue("A first source shot must begin", first != null && guard < 6000);

        // The first swapped clip frame is shot 1. Source frames are zero-based
        // here; View.as:1516-1528 then advances a four-frame offset each tick.
        int[] cycleFrames = new int[4];
        for (int shot = 2; shot <= 75; shot++) {
            assertFalse(animation.tickSourceFrame());
            if (shot >= 6) {
                assertEquals("The source shoot cycle has four frames",
                        cycleFrames[shot % 4], first.sourceFrame);
            }
            cycleFrames[shot % 4] = first.sourceFrame;
        }
        int lastShotFrame = first.sourceFrame;
        assertTrue(first.visible);
        assertFalse(animation.tickSourceFrame()); // shot count 75 -> stop
        assertEquals(lastShotFrame, first.sourceFrame);
        assertTrue(first.visible);
        int laterActorIndex = firstActorIndex == 1 ? 2 : 1;
        FlashSoldierOneAnimation.Actor laterSoldier = animation.getActor(laterActorIndex);
        int laterSoldierFrame = laterSoldier.sourceFrame;
        assertTrue(animation.tickSourceFrame()); // stop -> deletion list, early return
        assertEquals(lastShotFrame, first.sourceFrame);
        assertTrue(first.visible);
        if (firstActorIndex > laterActorIndex) {
            assertEquals("Flash skips later units after infantry removal",
                    laterSoldierFrame, laterSoldier.sourceFrame);
        }
        assertTrue(laterSoldier.visible);
        int afterRemovalFrame = laterSoldier.sourceFrame;
        animation.clearStoppedActors();
        assertFalse(first.visible);
        assertTrue("Cleanup must leave active clips alone", laterSoldier.visible);
        assertEquals("Cleanup must not advance another animation",
                afterRemovalFrame, laterSoldier.sourceFrame);
    }

    @Test
    public void boatStillClipUsesSourceUnloadCadenceAndSurvivesStop() throws Exception {
        FlashSoldierOneAnimation animation = createAnimation(0);
        FlashSoldierOneAnimation.Actor boat = animation.getActor(0);
        int guard = 0;
        while (!"boat001_still".equals(boat.linkage) && guard++ < 6000) {
            animation.tickSourceFrame();
        }
        assertTrue(guard < 6000);
        assertEquals(0, boat.sourceFrame); // still clip frame 1
        for (int tick = 1; tick < 10; tick++) {
            animation.tickSourceFrame();
            assertEquals(0, boat.sourceFrame);
        }
        animation.tickSourceFrame();
        assertEquals(2, boat.sourceFrame); // first unload: still frame 3
        for (int stopGuard = 0; stopGuard < 100 && boat.sourceFrame != 4; stopGuard++) {
            animation.tickSourceFrame();
        }
        assertEquals(4, boat.sourceFrame); // final unload: still frame 5
        for (int tick = 0; tick < 11; tick++) {
            animation.tickSourceFrame();
        }
        assertEquals(1, boat.sourceFrame); // stopped still clip: direction frame 2
        assertTrue(boat.visible);
    }

    private static FlashSoldierOneAnimation createAnimation(int delayTicks) throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        FlashBattleRoutes.SoldierOneAttack route =
                FlashBattleRoutes.createSoldierOneAttack(0, 1, 1, anchors,
                        new ReferenceRandom(2026L));
        return new FlashSoldierOneAnimation(route, new ReferenceRandom(42L),
                OriginalAudio.Cue.SOLDIER_GUN, delayTicks);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(moduleAsset)) {
            return moduleAsset;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
