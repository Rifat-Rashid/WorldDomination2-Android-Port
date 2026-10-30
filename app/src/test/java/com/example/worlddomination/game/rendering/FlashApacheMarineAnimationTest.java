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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class FlashApacheMarineAnimationTest {
    @Test
    public void respectsDelayBeforeShowingApacheAtTheSourceActionBuilding() throws Exception {
        FlashApacheMarineAnimation animation = createAnimation(2);
        FlashApacheMarineAnimation.Actor apache = animation.getActor(0);

        assertFalse(apache.visible);
        animation.tick();
        assertFalse(apache.visible);
        animation.tick();
        assertFalse(apache.visible);
        animation.tick();
        assertTrue(apache.visible);
        assertEquals("apache", apache.linkage);
        assertEquals(396.25f, apache.sourceX, 0f);
        assertEquals(352.25f, apache.sourceY, 0f);
        assertSame(apache, animation.getActor(0));
    }

    @Test
    public void unloadsThreeMarinesAndEmitsOneOriginalCombatCuePerMarine() throws Exception {
        FlashApacheMarineAnimation animation = createAnimation(0);
        boolean[] becameVisible = new boolean[3];
        int combatCues = 0;
        int tick;
        for (tick = 0; tick < 6000 && !animation.isFinished(); tick++) {
            animation.tick();
            for (int marine = 0; marine < 3; marine++) {
                becameVisible[marine] |= animation.getActor(marine + 1).visible;
            }
            if (animation.takeAudioCue()) {
                combatCues++;
                assertEquals("soldier001_shoot",
                        animation.getActor(combatCues).linkage);
            }
        }

        assertTrue("The Apache, marines, and source shot loops should finish", tick < 6000);
        assertEquals(3, combatCues);
        for (int marine = 0; marine < 3; marine++) {
            assertTrue("Each Apache passenger should become visible", becameVisible[marine]);
            assertFalse(animation.getActor(marine + 1).visible);
        }
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.takeAudioCue());
    }

    private static FlashApacheMarineAnimation createAnimation(int delayTicks) throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        FlashBattleRoutes.ApacheMarineAttack route =
                FlashBattleRoutes.createSoldierTwoAttack(0, 1, 1, 1, anchors,
                        new ReferenceRandom(2026L));
        return new FlashApacheMarineAnimation(route, new ReferenceRandom(42L),
                OriginalAudio.Cue.SEAL_GUN, delayTicks);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(moduleAsset)) {
            return moduleAsset;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
