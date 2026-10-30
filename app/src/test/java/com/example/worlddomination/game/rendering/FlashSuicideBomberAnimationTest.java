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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class FlashSuicideBomberAnimationTest {
    @Test
    public void hidesBomberDuringSourceScreamAndFadesInAtItsHomeIsland() throws Exception {
        FlashSuicideBomberAnimation animation = createAnimation(2);
        FlashSuicideBomberAnimation.Actor bomber = animation.getActor(0);

        animation.tick();
        animation.tick();
        assertFalse(bomber.visible);
        animation.tick();
        assertTrue(animation.takeAudioCue());
        assertEquals(OriginalAudio.Cue.SUICIDE_SCREAM, animation.getAudioCue());
        assertFalse(bomber.visible);

        for (int tick = 0; tick < 200 && !bomber.visible; tick++) {
            animation.tick();
        }
        assertTrue(bomber.visible);
        assertEquals(420f, bomber.sourceX, 0f);
        assertEquals(420f, bomber.sourceY, 0f);
        assertEquals(0f, bomber.alpha, 0f);
        assertSame(bomber, animation.getActor(0));
    }

    @Test
    public void followsTargetRouteAndPlaysSourceExplosionAfterTheBomberDisappears()
            throws Exception {
        FlashSuicideBomberAnimation animation = createAnimation(0);
        boolean sawBomber = false;
        boolean sawExplosion = false;
        int cueCount = 0;
        int tick;
        for (tick = 0; tick < 8000 && !animation.isFinished(); tick++) {
            animation.tick();
            sawBomber |= animation.getActor(0).visible;
            sawExplosion |= animation.getActor(1).visible;
            while (animation.takeAudioCue()) {
                if (cueCount == 0) {
                    assertEquals(OriginalAudio.Cue.SUICIDE_SCREAM,
                            animation.getAudioCue());
                } else {
                    assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3,
                            animation.getAudioCue());
                }
                cueCount++;
            }
        }

        assertTrue("The source bomber and explosion timelines should finish", tick < 8000);
        assertTrue(sawBomber);
        assertTrue(sawExplosion);
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.getActor(1).visible);
        assertEquals(2, cueCount);
        assertFalse(animation.takeAudioCue());
    }

    private static FlashSuicideBomberAnimation createAnimation(int delayTicks)
            throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        FlashBattleRoutes.SuicideBomberAttack route =
                FlashBattleRoutes.createSuicideBomberAttack(0, 1, 1, anchors);
        return new FlashSuicideBomberAnimation(route,
                OriginalAudio.Cue.SUICIDE_SCREAM, delayTicks);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(moduleAsset)) {
            return moduleAsset;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
