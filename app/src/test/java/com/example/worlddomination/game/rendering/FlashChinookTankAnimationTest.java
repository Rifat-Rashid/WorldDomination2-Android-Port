package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

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
            }
            ticks++;
        }

        assertTrue("The source unit route should deploy its tank", tankDeployed);
        assertTrue("The source unit route should play the tank engine cue", tankDrove);
        assertTrue("Chinook and tank source sequences should finish", animation.isFinished());
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.getActor(1).visible);
        assertFalse(animation.getActor(2).visible);
        assertTrue("The source countdown takes multiple seconds", ticks > 250);
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
        assertTrue("Chinook remains through its 250-tick flyoff countdown",
                animation.getActor(0).visible);
        assertFalse("The overall presentation waits for the Chinook", animation.isFinished());

        while (!animation.isFinished() && ticks < 800) {
            animation.tick();
            ticks++;
        }
        assertTrue(animation.isFinished());
        assertFalse(animation.getActor(0).visible);
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
