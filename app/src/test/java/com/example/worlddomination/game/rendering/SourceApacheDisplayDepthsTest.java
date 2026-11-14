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

public final class SourceApacheDisplayDepthsTest {
    @Test public void twoOpponentSourceAttachOrderAndStableDotDepths() throws Exception {
        BattlefieldDisplayState state = state(2);
        FlashApacheMarineAnimation animation = animation();
        SourceApacheDisplayDepths depths = new SourceApacheDisplayDepths(state, animation, 0);
        assertInitial(depths, 22509);
        assertEquals(22522, state.nextDepth());
        assertEquals("unit1.body", depths.binding(3).body().getValue().name);
        assertEquals("unit4.shadow", depths.binding(0).shadow().getValue().name);
        assertSame(depths.binding(1), depths.binding(1).body().getValue().owner);
    }

    @Test public void fourOpponentSourceAttachOrder() throws Exception {
        BattlefieldDisplayState state = state(4);
        SourceApacheDisplayDepths depths = new SourceApacheDisplayDepths(state, animation(), 0);
        assertInitial(depths, 30024);
        assertEquals(30037, state.nextDepth());
    }

    @Test public void exactDoubleYControlsDepthAndOccupiedSwap() throws Exception {
        BattlefieldDisplayState state = state(2);
        SourceApacheDisplayDepths depths = new SourceApacheDisplayDepths(state, animation(), 0);
        SourceApacheDisplayDepths.Binding marine = depths.binding(1); // source unit3
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> occupant = state.attach(
                BattlefieldDisplayState.Kind.AUTHORED, -1, "occupied-target", null, 10135);
        depths.onMarineFrame(1, 209.999999999, true, false);
        assertEquals(10135, marine.body().getDepth());
        assertEquals(10136, marine.tint().getDepth());
        assertEquals(22515, occupant.getDepth());
        assertSame(occupant, state.clips().atDepth(22515));
        depths.onMarineFrame(1, 210.0, true, false);
        // The legal two-opponent trace first moves unit3 to 10635/10636 at tick 291.
        assertEquals(10635, marine.body().getDepth());
        assertEquals(10636, marine.tint().getDepth());
    }

    @Test public void shootReattachesThenRestoresCachedDisplayDepth() throws Exception {
        BattlefieldDisplayState state = state(2);
        FlashApacheMarineAnimation animation = animation();
        SourceApacheDisplayDepths depths = new SourceApacheDisplayDepths(state, animation, 0);
        SourceApacheDisplayDepths.Binding marine = depths.binding(3); // source unit1
        depths.onMarineFrame(3, 240.0, true, false);
        assertEquals(12125, marine.body().getDepth());
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldBody = marine.body();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldTint = marine.tint();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot = marine.dot();
        depths.onMarineFrame(3, 240.0, false, true);
        assertFalse(oldBody.isAttached());
        assertFalse(oldTint.isAttached());
        assertNotSame(oldBody, marine.body());
        assertEquals(12125, marine.body().getDepth());
        assertEquals(12126, marine.tint().getDepth());
        assertSame(dot, marine.dot());
        assertEquals(22511, dot.getDepth());
        assertEquals(22522, state.nextDepth());
        // Stale handles never remove the replacement; source target dot remains.
        state.remove(oldBody);
        state.remove(oldTint);
        assertTrue(marine.body().isAttached());
        animation.getActor(3).detach();
        assertFalse(marine.body().isAttached());
        assertFalse(marine.tint().isAttached());
        assertTrue(dot.isAttached());
    }

    @Test public void apacheDetachRemovesShadowButRetainsTargetDot() throws Exception {
        BattlefieldDisplayState state = state(2);
        FlashApacheMarineAnimation animation = animation();
        SourceApacheDisplayDepths depths = new SourceApacheDisplayDepths(state, animation, 0);
        SourceApacheDisplayDepths.Binding apache = depths.binding(0);
        animation.getActor(0).detach();
        assertFalse(apache.body().isAttached());
        assertFalse(apache.shadow().isAttached());
        assertFalse(apache.tint().isAttached());
        assertTrue(apache.dot().isAttached());
    }

    private static void assertInitial(SourceApacheDisplayDepths depths, int start) {
        for (int sourceRow = 0; sourceRow < 3; sourceRow++) {
            SourceApacheDisplayDepths.Binding marine = depths.binding(3 - sourceRow);
            int first = start + sourceRow * 3;
            assertEquals(first, marine.body().getDepth());
            assertEquals(first + 1, marine.tint().getDepth());
            assertEquals(first + 2, marine.dot().getDepth());
            assertNull(marine.shadow());
            assertEquals((sourceRow + 1) * 5, marine.sourceParameter);
        }
        SourceApacheDisplayDepths.Binding apache = depths.binding(0);
        assertEquals(start + 9, apache.body().getDepth());
        assertEquals(start + 10, apache.shadow().getDepth());
        assertEquals(start + 11, apache.tint().getDepth());
        assertEquals(start + 12, apache.dot().getDepth());
    }

    private static BattlefieldDisplayState state(int opponents) throws Exception {
        GameSetup setup = new GameSetup(0L);
        for (int index = 0; index < opponents; index++) assertTrue(setup.toggleOpponent(index));
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(battleFrame(), new GameState(setup), true);
        return state;
    }

    private static FlashApacheMarineAnimation animation() throws Exception {
        String anchors = new String(Files.readAllBytes(projectAsset(
                "original/runtime/battle_anchors.json")), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(anchors);
        FlashBattleRoutes.ApacheMarineAttack route = FlashBattleRoutes.createSoldierTwoAttack(
                0, 1, 0, 1, points, new ReferenceRandom(2026L));
        return new FlashApacheMarineAnimation(route, new ReferenceRandom(42L), null, 0);
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
