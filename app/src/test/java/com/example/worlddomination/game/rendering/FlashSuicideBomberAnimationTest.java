package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class FlashSuicideBomberAnimationTest {
    @Test
    public void fadesAtTheSourceLaunchBuildingThenScreamsBeforeAppearingOnTargetIsland()
            throws Exception {
        FlashBattleRoutes.SuicideBomberAttack route = createRoute();
        FlashSuicideBomberAnimation animation = new FlashSuicideBomberAnimation(route,
                OriginalAudio.Cue.SUICIDE_SCREAM, 2);
        FlashSuicideBomberAnimation.Actor bomber = animation.getActor(0);
        FlashSuicideBomberAnimation.Actor tint = animation.getActor(1);

        // Unit.as attaches visible clips at (0,0); View seeks heliStart on its
        // first waiting traversal, before active-unit movement can begin.
        assertTrue(bomber.visible);
        assertEquals(0f, bomber.renderX, 0f);
        assertEquals(0.75f, tint.alpha, 0f);
        animation.presentWaitingFrame();
        assertEquals(route.initialPosition.x, bomber.sourceX, 0f);
        assertEquals(route.initialPosition.y, bomber.sourceY, 0f);
        assertEquals(1f, bomber.alpha, 0f);
        assertEquals(0.75f, tint.alpha, 0f);
        assertEquals("suicide_fade_out", animation.getSourceAction());
        animation.tickSourceFrame();
        animation.tickSourceFrame();
        assertEquals(route.initialPosition.x, bomber.sourceX, 0f);

        boolean heardScream = false;
        boolean reappearedAtHome = false;
        for (int tick = 0; tick < 300 && !reappearedAtHome; tick++) {
            animation.tickSourceFrame();
            while (animation.takeAudioCue()) {
                assertEquals(OriginalAudio.Cue.SUICIDE_SCREAM, animation.getAudioCue());
                heardScream = true;
            }
            assertEquals(bomber.sourceX, tint.sourceX, 0f);
            assertEquals(bomber.sourceY, tint.sourceY, 0f);
            assertEquals(bomber.sourceFrame, tint.sourceFrame);
            if (bomber.alpha < 1f) assertEquals(bomber.alpha, tint.alpha, 0f);
            reappearedAtHome = heardScream && bomber.sourceX == 158f
                    && bomber.sourceY == 199f;
        }

        assertTrue("The launch fade must trigger the source scream", heardScream);
        assertTrue("The unit must seek the target island after disappearance",
                reappearedAtHome);
        assertEquals("suicide_appear", animation.getSourceAction());
        assertTrue(bomber.visible);
        assertSame(bomber, animation.getActor(0));
        assertSame(tint, animation.getActor(1));
    }

    @Test
    public void wrapperRemovalDoesNotEndOriginalExplosionTimeline()
            throws Exception {
        FlashSuicideBomberAnimation animation = new FlashSuicideBomberAnimation(
                createRoute(), OriginalAudio.Cue.SUICIDE_SCREAM, 0);
        boolean sawBomber = false;
        boolean sawTint = false;
        boolean sawExplosion = false;
        int cueCount = 0;
        int tick;
        animation.presentWaitingFrame();
        for (tick = 0; tick < 8000 && !animation.isRemoved(); tick++) {
            animation.advanceAttachedTimelines();
            animation.tickSourceFrame();
            sawBomber |= animation.getActor(0).visible;
            sawTint |= animation.getActor(1).visible;
            sawExplosion |= animation.getActor(2).visible;
            while (animation.takeAudioCue()) {
                if (cueCount == 0) {
                    assertEquals(OriginalAudio.Cue.SUICIDE_SCREAM,
                            animation.getAudioCue());
                } else {
                    assertEquals(OriginalAudio.Cue.SUICIDE_EXPLODE,
                            animation.getAudioCue());
                }
                cueCount++;
            }
        }

        assertTrue("The source wrapper should leave active units", tick < 8000);
        assertTrue(sawBomber);
        assertTrue(sawTint);
        assertTrue(sawExplosion);
        assertTrue("The explosion clip outlives its source wrapper",
                animation.getActor(2).visible);
        for (int frame = 0; frame < 30; frame++) animation.advanceAttachedTimelines();
        assertEquals(20, animation.getActor(2).sourceFrame);
        assertEquals(2, cueCount);
        assertFalse(animation.takeAudioCue());
    }

    @Test public void authoredTintAlphaUsesEachPlayerPaletteUntilFadeOverridesIt()
            throws Exception {
        float[] initial = {1f, 0.75f, 1f, 0.75f, 0.75f};
        for (int color = 1; color <= initial.length; color++) {
            FlashSuicideBomberAnimation animation = new FlashSuicideBomberAnimation(
                    createRoute(), OriginalAudio.Cue.SUICIDE_SCREAM, 0, color);
            assertEquals(1f, animation.getActor(0).alpha, 0f);
            assertEquals(initial[color - 1], animation.getActor(1).alpha, 0f);
            animation.presentWaitingFrame();
            assertEquals(initial[color - 1], animation.getActor(1).alpha, 0f);
            animation.tickSourceFrame();
            assertEquals(0.98f, animation.getActor(0).alpha, 0.000001f);
            assertEquals(0.98f, animation.getActor(1).alpha, 0.000001f);
        }
    }

    @Test public void legalSourceUnitTraceMatchesEveryDisplayedPoseThroughRemoval()
            throws Exception {
        Path fixture = Paths.get("app", "src", "test", "resources",
                "source-suicide-unit.csv");
        if (!Files.isRegularFile(fixture)) {
            fixture = Paths.get("src", "test", "resources", "source-suicide-unit.csv");
        }
        List<String> rows = Files.readAllLines(fixture, StandardCharsets.UTF_8);
        Path anchorsFile = projectAsset("original/runtime/battle_anchors.json");
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(
                new String(Files.readAllBytes(anchorsFile), StandardCharsets.UTF_8));
        FlashBattleRoutes.SuicideBomberAttack route =
                FlashBattleRoutes.createSuicideBomberAttack(0, 1, 2, 1, anchors);
        FlashSuicideBomberAnimation animation = new FlashSuicideBomberAnimation(
                route, OriginalAudio.Cue.SUICIDE_SCREAM, 0, 2);
        assertEquals("one header and four source roles per tick", 1 + 364 * 4,
                rows.size());
        for (int tick = 0; tick <= 301; tick++) {
            if (tick > 0) {
                animation.advanceAttachedTimelines();
                if (tick <= 234) animation.presentWaitingFrame();
                else animation.tickSourceFrame();
            }
            String[] body = rows.get(1 + tick * 4).split(",", -1);
            assertEquals("source role at " + tick, "body", body[1]);
            assertEquals("source action at " + tick, body[2], animation.getSourceAction());
            FlashSuicideBomberAnimation.Actor actor = animation.getActor(0);
            assertEquals("logical X at " + tick, Double.parseDouble(body[7]),
                    actor.sourceX, 0.02d);
            assertEquals("logical Y at " + tick, Double.parseDouble(body[8]),
                    actor.sourceY, 0.02d);
            if (tick < 300) {
                assertEquals("painted frame at " + tick, Integer.parseInt(body[16]) - 1,
                        actor.sourceFrame);
                assertEquals("painted X at " + tick, Double.parseDouble(body[18]),
                        actor.renderX, 0.02d);
                assertEquals("painted Y at " + tick, Double.parseDouble(body[19]),
                        actor.renderY, 0.02d);
                assertEquals("raw alpha at " + tick, Double.parseDouble(body[17]) / 100d,
                        actor.alpha, 0.0002d);
            }
            if (tick == 300) {
                String[] explosion = rows.get(4 + tick * 4).split(",", -1);
                assertEquals(206d, animation.getActor(2).renderX, 0.02d);
                assertEquals(162.2d, animation.getActor(2).renderY, 0.02d);
                assertEquals(Integer.parseInt(explosion[16]) - 1,
                        animation.getActor(2).sourceFrame);
                assertTrue(animation.takeCityImpact());
            }
            if (tick == 246 || tick == 300) {
                assertTrue("source one-shot audio at " + tick, animation.takeAudioCue());
                assertEquals(tick == 246 ? OriginalAudio.Cue.SUICIDE_SCREAM
                        : OriginalAudio.Cue.SUICIDE_EXPLODE, animation.getAudioCue());
                assertFalse(animation.takeAudioCue());
            } else {
                assertFalse("no extra suicide sound at " + tick, animation.takeAudioCue());
            }
            assertEquals("source wrapper membership at " + tick,
                    Integer.parseInt(body[5]) < 0, animation.isRemoved());
        }
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
