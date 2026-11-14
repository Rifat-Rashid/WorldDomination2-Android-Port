package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Unit.as constructor depths, before any View movement or role rendering. */
public final class SourceBattleUnitAllocationTest {
    @Test public void soldierOneFourReverseUnitsEachAttachBodyTintDot() throws Exception {
        BattlefieldDisplayState state = state(2);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(state);
        assertEquals(0, allocation.reserveBeforeEvent(direct("soldier1", -1)));
        assertEquals("physical clips are installed after route construction", 22509,
                state.nextDepth());
        new SourceInfantryDisplayDepths(state, infantryAnimation(), 0);
        for (int unit = 1; unit <= 4; unit++) {
            int first = 22509 + (unit - 1) * 3;
            occupied(state, first, "unit" + unit);
            occupied(state, first + 1, "tintunit" + unit);
            occupied(state, first + 2, "targetDotunit" + unit);
        }
        assertEquals(22521, state.nextDepth());
        assertEquals(20, allocation.reserveBeforeEvent(unsupported()));
    }

    @Test public void apacheReservesFourParametersForPhysicalBinding() throws Exception {
        BattlefieldDisplayState state = state(2);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(state);
        assertEquals(0, allocation.reserveBeforeEvent(direct("soldier2", 1)));
        assertEquals("route construction alone does not attach source Units", 22509,
                state.nextDepth());
        SourceApacheDisplayDepths depths = new SourceApacheDisplayDepths(state, animation(), 0);
        assertEquals(22509, depths.binding(3).body().getDepth());
        assertEquals(22518, depths.binding(0).body().getDepth());
        assertEquals(22521, depths.binding(0).dot().getDepth());
        assertEquals(22522, state.nextDepth());
        assertEquals(20, allocation.reserveBeforeEvent(unsupported()));
    }

    @Test public void tankTopExplicitParamAndSuicideGapFollowSourceConstructor()
            throws Exception {
        BattlefieldDisplayState tankState = state(2);
        // This authored scene has no child at depth five. Add an occupied
        // target to verify Flash attach replacement without claiming it occurs
        // in the legal seed-zero battle fixture.
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> authored =
                tankState.attach(BattlefieldDisplayState.Kind.AUTHORED, -1,
                        "synthetic-occupied-depth", null, 5);
        SourceBattleUnitAllocation tanks = new SourceBattleUnitAllocation(tankState);
        assertEquals(0, tanks.reserveBeforeEvent(direct("soldier3", 1)));
        assertTrue("allocation alone must not replace a live clip", authored.isAttached());
        new SourceChinookDisplayDepths(tankState, chinookAnimation(), 0);
        assertFalse("tankTop replaces authored child at param_depth", authored.isAttached());
        occupied(tankState, 5, "unit1");
        occupied(tankState, 22509, "targetDotunit1");
        occupied(tankState, 22510, "unit2");
        occupied(tankState, 22511, "targetDotunit2");
        occupied(tankState, 22512, "unit3");
        occupied(tankState, 22513, "shadow_unit3");
        occupied(tankState, 22514, "targetDotunit3");
        assertEquals(22515, tankState.nextDepth());
        assertEquals(15, tanks.reserveBeforeEvent(unsupported()));

        BattlefieldDisplayState suicideState = state(2);
        SourceBattleUnitAllocation suicide = new SourceBattleUnitAllocation(suicideState);
        assertEquals(0, suicide.reserveBeforeEvent(direct("soldier4", -1)));
        assertEquals("route reservation leaves physical attachment to the binding",
                22509, suicideState.nextDepth());
        new SourceSuicideDisplayDepths(suicideState, suicideAnimation(), 0);
        assertNull("first suicide swap leaves original body depth empty",
                suicideState.clips().atDepth(22509));
        assertNull("second constructor swap leaves intermediate depth empty",
                suicideState.clips().atDepth(22510));
        occupied(suicideState, 22511, "tintunit1");
        occupied(suicideState, 22512, "targetDotunit1");
        occupied(suicideState, 22513, "unit1");
        assertEquals(22514, suicideState.nextDepth());
        assertEquals(5, suicide.reserveBeforeEvent(unsupported()));

        // The following Apache cohort starts after the second body swap, while
        // both abandoned lower depths remain empty in the source parent.
        assertEquals(5, suicide.reserveBeforeEvent(direct("soldier2", 1)));
        SourceApacheDisplayDepths following =
                new SourceApacheDisplayDepths(suicideState, animation(), 5);
        assertEquals(22514, following.binding(3).body().getDepth());
        assertEquals(22523, following.binding(0).body().getDepth());
        assertEquals(22526, following.binding(0).dot().getDepth());
        assertNull(suicideState.clips().atDepth(22509));
        assertNull(suicideState.clips().atDepth(22510));
        assertEquals(22527, suicideState.nextDepth());
    }

    @Test public void rocketSubmarineSatelliteAndBothPlanesReserveDirectRoles()
            throws Exception {
        assertSingle("rocket1", GameState.CombatEventType.STRIKE_IMPACT, null, 1,
                22509, "unit1", "targetDotunit1");
        assertSingle("rocket4", GameState.CombatEventType.STRIKE_INTERCEPTED, null, 2,
                22509, "unit1", "targetDotunit1");
        assertSingle("sub", GameState.CombatEventType.STRIKE_IMPACT, "warhead1", -1,
                22509, "unit1", "targetDotunit1");
        assertSingle("space", GameState.CombatEventType.STRIKE_IMPACT, "specialLoad", -1,
                22509, "unit1", "targetDotunit1");
        for (String plane : new String[]{"plane1", "plane2"}) {
            assertSingle(plane, GameState.CombatEventType.STRIKE_IMPACT, "warhead1", 1,
                    22509, "unit1", "shadow_unit1", "tintunit1", "targetDotunit1");
            assertSingle(plane, GameState.CombatEventType.STRIKE_INTERCEPTED,
                    "warhead1", 2, 22509, "unit1", "shadow_unit1",
                    "tintunit1", "targetDotunit1");
        }
    }

    @Test public void shipFormationUsesWildcardAbsoluteDepthAndAdvancesHighWater()
            throws Exception {
        BattlefieldDisplayState state = state(2);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(state);
        // Battle.as passes wildcard _number from each reverse payload index.
        assertEquals(0, allocation.reserveBeforeEvent(strike("ship", null, -1, 2)));
        occupied(state, 800007, "unit1");
        occupied(state, 800008, "shadow_unit1");
        occupied(state, 800009, "tintunit1");
        occupied(state, 800010, "targetDotunit1");
        assertNull(state.clips().atDepth(800005));
        assertNull(state.clips().atDepth(800006));
        assertEquals(800011, state.nextDepth());
        assertEquals(5, allocation.reserveBeforeEvent(strike("ship", null, -1, 1)));
        occupied(state, 800011, "unit2");
        occupied(state, 800014, "targetDotunit2");
        assertEquals(10, allocation.reserveBeforeEvent(strike("ship", null, -1, 0)));
        occupied(state, 800015, "unit3");
        occupied(state, 800018, "targetDotunit3");
        assertEquals(800019, state.nextDepth());
        assertEquals(15, allocation.reserveBeforeEvent(unsupported()));
    }

    @Test public void mixedFamiliesPreserveApacheBeforeAndAfterDepthClock()
            throws Exception {
        BattlefieldDisplayState first = state(2);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(first);
        assertEquals(0, allocation.reserveBeforeEvent(strike("plane1", "warhead1", 1, -1)));
        occupied(first, 22509, "unit1");
        assertEquals(5, allocation.reserveBeforeEvent(direct("soldier2", 1)));
        SourceApacheDisplayDepths apache = new SourceApacheDisplayDepths(first, animation(), 5);
        assertEquals(22513, apache.binding(3).body().getDepth());
        assertEquals(22522, apache.binding(0).body().getDepth());
        assertEquals(25, allocation.reserveBeforeEvent(strike("space", "specialLoad", -1, -1)));
        occupied(first, 22526, "unit6");
        occupied(first, 22527, "targetDotunit6");

        BattlefieldDisplayState second = state(2);
        SourceBattleUnitAllocation after = new SourceBattleUnitAllocation(second);
        assertEquals(0, after.reserveBeforeEvent(direct("soldier2", 1)));
        SourceApacheDisplayDepths apacheFirst =
                new SourceApacheDisplayDepths(second, animation(), 0);
        assertEquals(22521, apacheFirst.binding(0).dot().getDepth());
        assertEquals(20, after.reserveBeforeEvent(strike("ship", null, -1, 1)));
        occupied(second, 800026, "unit5");
        occupied(second, 800029, "targetDotunit5");
        assertEquals(25, after.reserveBeforeEvent(direct("soldier1", -1)));
        new SourceInfantryDisplayDepths(second, infantryAnimation(), 25);
        occupied(second, 800030, "unit6");
        occupied(second, 800041, "targetDotunit9");
        assertEquals(800042, second.nextDepth());
    }

    @Test public void unsupportedResultDoesNotConsumeSourceUnitParameter() throws Exception {
        BattlefieldDisplayState state = state(2);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(state);
        assertEquals(0, allocation.reserveBeforeEvent(unsupported()));
        assertEquals(22509, state.nextDepth());
        assertEquals(0, allocation.reserveBeforeEvent(strike("space", "specialLoad", -1, -1)));
        occupied(state, 22509, "unit1");
        assertEquals(5, allocation.reserveBeforeEvent(unsupported()));
    }

    @Test public void fourOpponentSceneStartsUnitAtSourceHighWaterDepth() throws Exception {
        BattlefieldDisplayState state = state(4);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(state);
        assertEquals(30024, state.nextDepth());
        assertEquals(0, allocation.reserveBeforeEvent(strike("rocket2", "warhead1", 1, -1)));
        occupied(state, 30024, "unit1");
        occupied(state, 30025, "targetDotunit1");
        assertEquals(30026, state.nextDepth());
    }

    private static void assertSingle(String weapon,
                                     GameState.CombatEventType type, String payload,
                                     int slot, int firstDepth, String... names) throws Exception {
        BattlefieldDisplayState state = state(2);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(state);
        assertEquals(0, allocation.reserveBeforeEvent(event(type, weapon, payload, slot, -1)));
        for (int index = 0; index < names.length; index++) {
            if (names[index] != null) occupied(state, firstDepth + index, names[index]);
        }
        assertEquals(firstDepth + names.length, state.nextDepth());
        assertEquals(5, allocation.reserveBeforeEvent(unsupported()));
    }

    private static void occupied(BattlefieldDisplayState state, int depth, String name) {
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                state.clips().atDepth(depth);
        assertNotNull("missing depth " + depth, entry);
        assertTrue(entry.isAttached());
        assertEquals(name, entry.getValue().name);
    }

    private static GameState.CombatEvent direct(String weapon, int slot) throws Exception {
        return event(GameState.CombatEventType.DIRECT_ATTACK, weapon, null, slot, -1);
    }

    private static GameState.CombatEvent strike(String weapon, String payload,
                                                int slot, int formation) throws Exception {
        return event(GameState.CombatEventType.STRIKE_IMPACT, weapon, payload,
                slot, formation);
    }

    private static GameState.CombatEvent unsupported() throws Exception {
        return event(GameState.CombatEventType.DIRECT_ATTACK, "spy", null, -1, -1);
    }

    private static GameState.CombatEvent event(GameState.CombatEventType type,
                                               String weapon, String payload,
                                               int slot, int formation) throws Exception {
        Constructor<GameState.CombatEvent> constructor = GameState.CombatEvent.class
                .getDeclaredConstructor(GameState.CombatEventType.class,
                        int.class, int.class, int.class, String.class, String.class,
                        int.class, String.class, int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(type, 0, 1, 0, weapon, payload, 0,
                null, slot, -1, formation, 1);
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

    private static FlashSoldierOneAnimation infantryAnimation() throws Exception {
        String anchors = new String(Files.readAllBytes(projectAsset(
                "original/runtime/battle_anchors.json")), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(anchors);
        FlashBattleRoutes.SoldierOneAttack route = FlashBattleRoutes.createSoldierOneAttack(
                0, 1, 0, points, new ReferenceRandom(2026L));
        return new FlashSoldierOneAnimation(route, new ReferenceRandom(42L), null, 0);
    }

    private static FlashChinookTankAnimation chinookAnimation() throws Exception {
        String anchors = new String(Files.readAllBytes(projectAsset(
                "original/runtime/battle_anchors.json")), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(anchors);
        FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                0, 1, 0, 1, "green", points, new ReferenceRandom(2026L));
        return new FlashChinookTankAnimation(route, 0);
    }

    private static FlashSuicideBomberAnimation suicideAnimation() throws Exception {
        String anchors = new String(Files.readAllBytes(projectAsset(
                "original/runtime/battle_anchors.json")), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(anchors);
        return new FlashSuicideBomberAnimation(FlashBattleRoutes.createSuicideBomberAttack(
                0, 1, 0, 1, points),
                com.example.worlddomination.game.audio.OriginalAudio.Cue.SUICIDE_SCREAM, 0);
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
