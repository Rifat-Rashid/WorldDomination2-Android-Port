package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;

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

/** Source Unit/View depth operations with the explicit tank_noshade alias. */
public final class SourceChinookDisplayDepthsTest {
    @Test public void physicalConstructionMatchesAliasRepairedSourceParent()
            throws Exception {
        BattlefieldDisplayState state = state();
        FlashChinookTankAnimation animation = animation();
        SourceChinookDisplayDepths depths = new SourceChinookDisplayDepths(
                state, animation, 0);
        SourceChinookDisplayDepths.Binding top = depths.binding(2);
        SourceChinookDisplayDepths.Binding base = depths.binding(1);
        SourceChinookDisplayDepths.Binding carrier = depths.binding(0);
        assertEquals(5, top.sourceParameter);
        assertEquals(10, base.sourceParameter);
        assertEquals(15, carrier.sourceParameter);
        assertEquals(5, top.body().getDepth());
        assertEquals(22509, top.dot().getDepth());
        assertEquals(22510, base.body().getDepth());
        assertEquals(22511, base.dot().getDepth());
        assertEquals(22512, carrier.body().getDepth());
        assertEquals(22513, carrier.shadow().getDepth());
        assertEquals(22514, carrier.dot().getDepth());
        assertNull(top.shadow());
        assertNull(base.shadow());
        assertEquals("unit1", top.body().getValue().name);
        assertEquals("unit2", base.body().getValue().name);
        assertEquals("shadow_unit3", carrier.shadow().getValue().name);
        assertEquals("targetDotunit3", carrier.dot().getValue().name);
        assertSame(carrier, carrier.body().getValue().owner);
        assertEquals(22515, state.nextDepth());
    }

    @Test public void firstVisitExchangesTurretWithTankDotWithoutMovingOtherPeers()
            throws Exception {
        BattlefieldDisplayState state = state();
        SourceChinookDisplayDepths depths = new SourceChinookDisplayDepths(
                state, animation(), 0);
        SourceChinookDisplayDepths.Binding top = depths.binding(2);
        SourceChinookDisplayDepths.Binding base = depths.binding(1);
        SourceChinookDisplayDepths.Binding carrier = depths.binding(0);
        depths.onActorFrame(0, 277.25, false);
        depths.onActorFrame(1, 307.25, false);
        depths.onActorFrame(2, 307.25, false);
        assertEquals(22511, top.body().getDepth());
        assertEquals("occupied swap puts target dot at prior top depth",
                5, base.dot().getDepth());
        assertSame(base.dot(), state.clips().atDepth(5));
        assertEquals(22510, base.body().getDepth());
        assertEquals(22512, carrier.body().getDepth());
        assertEquals(22513, carrier.shadow().getDepth());
        assertEquals(22514, carrier.dot().getDepth());
    }

    @Test public void dropReattachesBaseAtLowDepthReplacingAuthoredLandThenSwaps()
            throws Exception {
        BattlefieldDisplayState state = state();
        FlashChinookTankAnimation animation = animation();
        SourceChinookDisplayDepths depths = new SourceChinookDisplayDepths(
                state, animation, 0);
        SourceChinookDisplayDepths.Binding base = depths.binding(1);
        SourceChinookDisplayDepths.Binding top = depths.binding(2);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> previous = base.body();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> land = state.clips().atDepth(10);
        assertNotNull(land);
        assertEquals("land01", land.getValue().name);
        animation.getActor(1).linkage = "tankBase";
        depths.onActorFrame(1, 237.25, true);
        depths.onActorFrame(2, 237.25, false);
        assertFalse(previous.isAttached());
        assertFalse("Unit.attachMovie at depth 10 replaces land01", land.isAttached());
        assertEquals(11630, base.body().getDepth());
        assertEquals(11631, top.body().getDepth());
        assertEquals(22511, base.dot().getDepth());
        assertEquals(22515, state.nextDepth());
        state.remove(previous);
        assertTrue("stale old body cannot remove tankBase", base.body().isAttached());
    }

    @Test public void queueDetachRemovesBodyAndDirectShadowButRetainsDots()
            throws Exception {
        BattlefieldDisplayState state = state();
        FlashChinookTankAnimation animation = animation();
        SourceChinookDisplayDepths depths = new SourceChinookDisplayDepths(
                state, animation, 0);
        for (int index = 0; index < 3; index++) {
            animation.getActor(index).detach();
            assertFalse(depths.binding(index).body().isAttached());
            assertTrue(depths.binding(index).dot().isAttached());
        }
        assertFalse(depths.binding(0).shadow().isAttached());
        assertFalse(animation.getChinookShadow().visible);
        assertTrue(depths.binding(1).dot().isAttached());
    }

    @Test public void invalidParameterDoesNotAttachAnySourceClips() throws Exception {
        BattlefieldDisplayState state = state();
        int before = state.clips().size();
        try {
            new SourceChinookDisplayDepths(state, animation(), 3);
            fail("Expected invalid source parameter");
        } catch (IllegalArgumentException expected) {
            assertEquals(before, state.clips().size());
        }
    }

    private static FlashChinookTankAnimation animation() throws Exception {
        String json = new String(Files.readAllBytes(asset("original/runtime/battle_anchors.json")),
                StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 2, 1, "green", anchors);
        return new FlashChinookTankAnimation(route, 0);
    }

    private static BattlefieldDisplayState state() throws Exception {
        GameSetup setup = new GameSetup(0L);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(battleFrame(), new GameState(setup), true);
        return state;
    }

    private static FlashAnimationTimeline.Frame battleFrame() throws Exception {
        try (InputStream file = Files.newInputStream(asset("original/runtime/scene.json.gz"));
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

    private static Path asset(String relative) {
        Path module = Paths.get("app", "src", "main", "assets", relative);
        return Files.isRegularFile(module) ? module : Paths.get("src", "main", "assets", relative);
    }
}
