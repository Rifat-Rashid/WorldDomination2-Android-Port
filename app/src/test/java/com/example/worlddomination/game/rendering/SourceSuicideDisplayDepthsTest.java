package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;

/** Unit.as constructor/kill and View source-parent occupancy. */
public final class SourceSuicideDisplayDepthsTest {
    @Test public void constructorLeavesTwoGapsAndWaitingVisitMovesBodyBehindCity()
            throws Exception {
        BattlefieldDisplayState state = parent();
        FlashSuicideBomberAnimation animation = animation();
        SourceSuicideDisplayDepths.Binding binding =
                new SourceSuicideDisplayDepths(state, animation, 0).binding();
        assertEquals(5, binding.sourceParameter);
        assertEquals(22513, binding.body().getDepth());
        assertEquals(22511, binding.tint().getDepth());
        assertEquals(22512, binding.dot().getDepth());
        assertNull(state.clips().atDepth(22509));
        assertNull(state.clips().atDepth(22510));
        assertEquals(22514, state.nextDepth());

        animation.presentWaitingFrame();
        assertEquals(16125, binding.body().getDepth());
        assertEquals(16126, binding.tint().getDepth());
        assertEquals(22512, binding.dot().getDepth());
        assertEquals(22513, state.nextDepth());
        assertEquals(49, animation.getActor(0).sourceFrame);
        assertEquals(1f, animation.getActor(0).alpha, 0f);
        assertEquals(0.75f, animation.getActor(1).alpha, 0f);
    }

    @Test public void occupiedFloorSwapExchangesHandlesWithoutDestroyingPeer()
            throws Exception {
        BattlefieldDisplayState state = parent();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> peer = state.attach(
                BattlefieldDisplayState.Kind.AUTHORED, -1, "occupied-floor", null, 16125);
        FlashSuicideBomberAnimation animation = animation();
        SourceSuicideDisplayDepths.Binding binding =
                new SourceSuicideDisplayDepths(state, animation, 0).binding();
        animation.presentWaitingFrame();
        assertEquals(16125, binding.body().getDepth());
        assertEquals(22514, peer.getDepth());
        assertTrue(peer.isAttached());
        assertSame(peer, state.clips().atDepth(22514));
    }

    @Test public void explosionReusesNextDepthAndSurvivesWrapperUntilCleanup()
            throws Exception {
        BattlefieldDisplayState state = parent();
        FlashSuicideBomberAnimation animation = animation();
        SourceSuicideDisplayDepths.Binding binding =
                new SourceSuicideDisplayDepths(state, animation, 0).binding();
        SourceStoppedClipQueue stopped = new SourceStoppedClipQueue();
        animation.presentWaitingFrame();
        int frames = 0;
        while (!animation.isRemoved() && frames++ < 2000) {
            animation.advanceAttachedTimelines();
            animation.tickSourceFrame();
            animation.registerStoppedClips(stopped);
        }
        assertTrue("The source wrapper must reach its death visit", frames < 2000);
        assertTrue(animation.isRemoved());
        assertTrue(animation.getActor(2).visible);
        assertFalse(binding.body().isAttached());
        assertFalse(binding.tint().isAttached());
        assertEquals(22513, binding.explosion().getDepth());
        assertTrue(binding.explosion().isAttached());
        assertEquals(1, stopped.size());
        assertTrue(animation.takeCityImpact());
        assertFalse(animation.takeCityImpact());
        for (int i = 0; i < 30; i++) animation.advanceAttachedTimelines();
        assertEquals(20, animation.getActor(2).sourceFrame);
        assertEquals(1, stopped.removeAtAllUnitsDone());
        assertFalse(binding.explosion().isAttached());
        assertTrue("Source dot remains until parent reset", binding.dot().isAttached());
        assertEquals(0, stopped.removeAtAllUnitsDone());
    }

    private static BattlefieldDisplayState parent() {
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.attach(BattlefieldDisplayState.Kind.AUTHORED, -1, "highest-authored", null, 22508);
        return state;
    }

    @Test public void floorDepthUsesExactSourceYBeforeFloatPaintingConversion()
            throws Exception {
        SourceSuicideDisplayDepths depths = new SourceSuicideDisplayDepths(
                parent(), animation(), 0);
        depths.onMovingFrame(Math.nextDown(200d));
        assertEquals(9625, depths.binding().body().getDepth());
        assertEquals(9626, depths.binding().tint().getDepth());
        depths.onMovingFrame(200d);
        assertEquals(10125, depths.binding().body().getDepth());
        assertEquals(10126, depths.binding().tint().getDepth());
    }

    private static FlashSuicideBomberAnimation animation() throws Exception {
        Path asset = Paths.get("app", "src", "main", "assets", "original", "runtime",
                "battle_anchors.json");
        if (!Files.isRegularFile(asset)) {
            asset = Paths.get("src", "main", "assets", "original", "runtime",
                    "battle_anchors.json");
        }
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        FlashBattleRoutes.SuicideBomberAttack route =
                FlashBattleRoutes.createSuicideBomberAttack(0, 1, 2, 1, anchors);
        return new FlashSuicideBomberAnimation(route, OriginalAudio.Cue.SUICIDE_SCREAM, 0);
    }
}
