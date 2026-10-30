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
    public void fadesAtTheSourceLaunchBuildingThenScreamsBeforeReappearingAtHome()
            throws Exception {
        FlashBattleRoutes.SuicideBomberAttack route = createRoute();
        FlashSuicideBomberAnimation animation = new FlashSuicideBomberAnimation(route,
                OriginalAudio.Cue.SUICIDE_SCREAM, 2);
        FlashSuicideBomberAnimation.Actor bomber = animation.getActor(0);
        FlashSuicideBomberAnimation.Actor tint = animation.getActor(1);

        animation.tick();
        animation.tick();
        assertFalse(bomber.visible);
        animation.tick();
        assertTrue(bomber.visible);
        assertEquals(route.initialPosition.x, bomber.sourceX, 0f);
        assertEquals(route.initialPosition.y, bomber.sourceY, 0f);
        assertEquals(1f, bomber.alpha, 0f);

        boolean heardScream = false;
        boolean reappearedAtHome = false;
        for (int tick = 0; tick < 300 && !reappearedAtHome; tick++) {
            animation.tick();
            while (animation.takeAudioCue()) {
                assertEquals(OriginalAudio.Cue.SUICIDE_SCREAM, animation.getAudioCue());
                heardScream = true;
            }
            assertEquals(bomber.sourceX, tint.sourceX, 0f);
            assertEquals(bomber.sourceY, tint.sourceY, 0f);
            assertEquals(bomber.sourceFrame, tint.sourceFrame);
            assertEquals(bomber.alpha, tint.alpha, 0f);
            reappearedAtHome = heardScream && bomber.sourceX == route.spawn.x
                    && bomber.sourceY == route.spawn.y;
        }

        assertTrue("The launch fade must trigger the source scream", heardScream);
        assertTrue("The hidden unit must teleport to its home island", reappearedAtHome);
        assertEquals(16, bomber.sourceFrame);
        assertTrue(bomber.visible);
        assertSame(bomber, animation.getActor(0));
        assertSame(tint, animation.getActor(1));
    }

    @Test
    public void followsSourceRouteWithTintAndEndsOnOriginalExplosionTimeline()
            throws Exception {
        FlashSuicideBomberAnimation animation = new FlashSuicideBomberAnimation(
                createRoute(), OriginalAudio.Cue.SUICIDE_SCREAM, 0);
        boolean sawBomber = false;
        boolean sawTint = false;
        boolean sawExplosion = false;
        int cueCount = 0;
        int tick;
        for (tick = 0; tick < 8000 && !animation.isFinished(); tick++) {
            animation.tick();
            sawBomber |= animation.getActor(0).visible;
            sawTint |= animation.getActor(1).visible;
            sawExplosion |= animation.getActor(2).visible;
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
        assertTrue(sawTint);
        assertTrue(sawExplosion);
        assertFalse(animation.getActor(0).visible);
        assertFalse(animation.getActor(1).visible);
        assertFalse(animation.getActor(2).visible);
        assertEquals(2, cueCount);
        assertFalse(animation.takeAudioCue());
    }

    private static FlashBattleRoutes.SuicideBomberAttack createRoute() throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        return FlashBattleRoutes.createSuicideBomberAttack(0, 1, 1, 2, anchors);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(moduleAsset)) {
            return moduleAsset;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
