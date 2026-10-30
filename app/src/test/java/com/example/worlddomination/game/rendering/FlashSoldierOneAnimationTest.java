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
    @Test
    public void respectsDelayBeforeShowingTheBoat() throws Exception {
        FlashSoldierOneAnimation animation = createAnimation(2);
        FlashSoldierOneAnimation.Actor boat = animation.getActor(0);

        assertFalse(boat.visible);
        animation.tick();
        assertFalse(boat.visible);
        animation.tick();
        assertFalse(boat.visible);
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
            assertFalse(animation.getActor(soldier + 1).visible);
        }
        assertFalse(animation.takeAudioCue());
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
