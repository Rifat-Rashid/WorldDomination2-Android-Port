package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Source Unit/View direct-clip operations at the infantry lifecycle boundaries. */
public final class SourceInfantryDisplayDepthsTest {
    @Test public void constructionUsesPhysicalSourceOrderAndKeepsDotsAsPeers()
            throws Exception {
        BattlefieldDisplayState state = state(2);
        FlashSoldierOneAnimation animation = animation();
        SourceInfantryDisplayDepths depths = new SourceInfantryDisplayDepths(state, animation, 0);
        assertEquals("source constructor MC remains at origin", 0f,
                animation.getActor(0).sourceX, 0f);
        assertEquals(0f, animation.getActor(0).sourceY, 0f);
        for (int sourceRow = 0; sourceRow < 4; sourceRow++) {
            int nativeActor = 3 - sourceRow;
            SourceInfantryDisplayDepths.Binding binding = depths.binding(nativeActor);
            int firstDepth = 22509 + sourceRow * 3;
            assertEquals((sourceRow + 1) * 5, binding.sourceParameter);
            assertEquals(firstDepth, binding.body().getDepth());
            assertEquals(firstDepth + 1, binding.tint().getDepth());
            assertEquals(firstDepth + 2, binding.dot().getDepth());
            assertEquals("unit" + (sourceRow + 1), binding.body().getValue().name);
            assertEquals("tintunit" + (sourceRow + 1), binding.tint().getValue().name);
            assertEquals("targetDotunit" + (sourceRow + 1), binding.dot().getValue().name);
            assertSame(binding, binding.body().getValue().owner);
        }
        assertEquals(22521, state.nextDepth());
    }

    @Test public void fourOpponentBaseAndWaitingBoatUseExactSourceYDepth()
            throws Exception {
        BattlefieldDisplayState state = state(4);
        FlashSoldierOneAnimation animation = animation();
        SourceInfantryDisplayDepths depths = new SourceInfantryDisplayDepths(state, animation, 0);
        assertEquals(30024, depths.binding(3).body().getDepth());
        assertEquals(30036, state.nextDepth());

        animation.presentWaitingFrame();
        SourceInfantryDisplayDepths.Binding boat = depths.binding(0);
        assertEquals("source tick 1 boat MC moves to route start", 410f,
                animation.getActor(0).sourceX, 0f);
        assertEquals(474f, animation.getActor(0).sourceY, 0f);
        assertEquals(23640, boat.body().getDepth());
        assertEquals(23641, boat.tint().getDepth());
        assertEquals("target dot remains at constructor depth", 30035,
                boat.dot().getDepth());
        animation.presentWaitingFrame();
        assertEquals(23640, boat.body().getDepth());
    }

    @Test public void unloadReplacesBodyAtStoredParameterThenRestoresYDepth()
            throws Exception {
        BattlefieldDisplayState state = state(2);
        SourceInfantryDisplayDepths depths = new SourceInfantryDisplayDepths(state,
                animation(), 0);
        SourceInfantryDisplayDepths.Binding boat = depths.binding(0);
        depths.onActorFrame(0, 242d, false);
        assertEquals(12140, boat.body().getDepth());
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> previousBody = boat.body();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> previousTint = boat.tint();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot = boat.dot();
        depths.onActorFrame(0, 242d, true); // Unit.unload: boat001 -> boat001_still
        assertFalse(previousBody.isAttached());
        assertFalse(previousTint.isAttached());
        assertNotSame(previousBody, boat.body());
        assertEquals(12140, boat.body().getDepth());
        assertEquals(12141, boat.tint().getDepth());
        assertSame(dot, boat.dot());
        assertEquals(22520, dot.getDepth());
        assertEquals(22521, state.nextDepth());
        state.remove(previousBody);
        assertTrue("stale old body cannot remove still clip", boat.body().isAttached());
    }

    @Test public void shootingReattachesAtNextHighestThenRestoresCachedWalkDepth()
            throws Exception {
        BattlefieldDisplayState state = state(2);
        FlashSoldierOneAnimation animation = animation();
        SourceInfantryDisplayDepths depths = new SourceInfantryDisplayDepths(state,
                animation, 0);
        SourceInfantryDisplayDepths.Binding soldier = depths.binding(3);
        depths.onActorFrame(3, 229d, false);
        assertEquals(11125, soldier.body().getDepth());
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldBody = soldier.body();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldTint = soldier.tint();
        depths.onActorFrame(3, 229d, true); // Source tick 288 first shoot clip.
        assertFalse(oldBody.isAttached());
        assertFalse(oldTint.isAttached());
        assertEquals(11125, soldier.body().getDepth());
        assertEquals(11126, soldier.tint().getDepth());
        assertEquals(22511, soldier.dot().getDepth());
        assertEquals(22521, state.nextDepth());
        animation.getActor(3).linkage = "soldier001_shoot";
        depths.onActorFrame(3, 240d, false);
        assertEquals("shooting uses cached depth, not changed Y", 11125,
                soldier.body().getDepth());
    }

    @Test public void occupiedSwapMovesPreviousOccupantAndCleanupLeavesDots()
            throws Exception {
        BattlefieldDisplayState state = state(2);
        FlashSoldierOneAnimation animation = animation();
        SourceInfantryDisplayDepths depths = new SourceInfantryDisplayDepths(state,
                animation, 0);
        SourceInfantryDisplayDepths.Binding soldier = depths.binding(1);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> occupant = state.attach(
                BattlefieldDisplayState.Kind.EFFECT, -1, "occupied", null, 10135);
        depths.onActorFrame(1, 206d, false);
        assertEquals(10135, soldier.body().getDepth());
        assertEquals(10136, soldier.tint().getDepth());
        assertEquals("occupied target swaps to old soldier body depth", 22515,
                occupant.getDepth());
        assertSame(occupant, state.clips().atDepth(22515));

        SourceInfantryDisplayDepths.Binding boat = depths.binding(0);
        animation.getActor(1).detach();
        animation.getActor(0).detach();
        assertFalse(soldier.body().isAttached());
        assertFalse(soldier.tint().isAttached());
        assertFalse(boat.body().isAttached());
        assertFalse(boat.tint().isAttached());
        assertTrue(soldier.dot().isAttached());
        assertTrue(boat.dot().isAttached());
        assertSame(occupant, state.clips().atDepth(22515));
    }

    private static BattlefieldDisplayState state(int opponents) throws Exception {
        GameSetup setup = new GameSetup(0L);
        for (int leader = 0; leader < opponents; leader++) assertTrue(setup.toggleOpponent(leader));
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(battleFrame(), new GameState(setup), true);
        return state;
    }

    private static FlashSoldierOneAnimation animation() throws Exception {
        String anchors = new String(Files.readAllBytes(projectAsset(
                "original/runtime/battle_anchors.json")), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(anchors);
        FlashBattleRoutes.SoldierOneAttack route = FlashBattleRoutes.createSoldierOneAttack(
                0, 1, 0, points, new ReferenceRandom(2026L));
        return new FlashSoldierOneAnimation(route, new ReferenceRandom(42L), null, 0);
    }

    private static FlashAnimationTimeline.Frame battleFrame() throws Exception {
        try (InputStream file = Files.newInputStream(projectAsset(
                     "original/runtime/scene.json.gz"));
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            JsonValue scene = new JsonReader().parse(reader);
            for (JsonValue symbol = scene.get("symbols").child; symbol != null;
                 symbol = symbol.next) {
                if (symbol.getInt("id") == 5766)
                    return FlashAnimationTimeline.fromSymbol(symbol, 24f).getFrame(0);
            }
        }
        throw new AssertionError("Missing source battle root");
    }

    private static Path projectAsset(String relative) {
        Path module = Paths.get("app", "src", "main", "assets", relative);
        return Files.isRegularFile(module) ? module : Paths.get("src", "main", "assets", relative);
    }
}
