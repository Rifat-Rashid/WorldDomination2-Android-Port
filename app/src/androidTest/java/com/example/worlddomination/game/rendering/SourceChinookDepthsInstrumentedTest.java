package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real World soldier3 depth replay against the explicitly alias-resolved source capture. */
@RunWith(AndroidJUnit4.class)
public final class SourceChinookDepthsInstrumentedTest {
    private static final int LAST_TICK = 479;
    private static final int ACTORS = 3; // Source order: turret, tank, Chinook.
    private static final int ROLES = 5; // body, direct shadow, tint, dot, nested shadow.
    private static final int IMPACTED_CITY_ADDRESS = 7; // Player 1, city 2.
    private static final String HEADER = "mode,tick,actor,unitName,mcID,action,role,exists,"
            + "attached,parentName,sourceDepth,lastDepth,linkage,visible,frame,curX,curY,"
            + "clipX,clipY,activeIndex,deleteIndex,unitDepthParam";

    @Test public void legalChinookBattleMatchesAliasResolvedSourceDepthsEveryFrame()
            throws Exception {
        Expected[][][] source = readSource();
        double[] sourceRandom = readRandom();
        CityImpact[] sourceCity = readCityImpact();
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = gameReadyForAttack();
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cityRenderer = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cityRenderer.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("legal source attack turn", 3, game.getTurn());
            double nextSimulationDraw = game.createPresentationRandomCopy().nextDouble();
            BattlefieldDisplayState display = (BattlefieldDisplayState)
                    field(WorldDominationGame.class, "battlefieldDisplay").get(app);
            SourceChinookDisplayDepths.Binding[] bindings = bindings(display);
            TurnPlaybackSequence playback = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            ReferenceRandom presentationRandom = (ReferenceRandom)
                    field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            assertNotNull("private battle presentation stream", presentationRandom);
            assertEquals("source post-construction presentation RNG", sourceRandom[0],
                    presentationRandom.copy().nextDouble(), 0d);
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> authoredLand =
                    named(display, "land01");
            assertNotNull("authored land01 slot", authoredLand);
            assertTrue("land01 initially attached", authoredLand.isAttached());
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> originalTankBody =
                    bindings[1].body();
            assertTick(0, source[0], bindings);
            assertCityImpact(0, sourceCity[0], cityRenderer);
            for (int tick = 1; tick < LAST_TICK; tick++) {
                advance(app);
                assertEquals("source private presentation RNG at tick " + tick,
                        sourceRandom[tick], presentationRandom.copy().nextDouble(), 0d);
                assertTick(tick, source[tick], bindings);
                assertCityImpact(tick, sourceCity[tick], cityRenderer);
                if (tick == 288) {
                    assertSame("tank_noShade still attached before drop completion",
                            originalTankBody, bindings[1].body());
                    assertTrue("land01 remains before tankBase replacement",
                            authoredLand.isAttached());
                }
                if (tick == 289) {
                    assertNotSame("source tankBase replacement creates a new clip",
                            originalTankBody, bindings[1].body());
                    assertFalse("old tank body removed", originalTankBody.isAttached());
                    assertFalse("tankBase low-depth attach replaces land01",
                            authoredLand.isAttached());
                }
                if (tick == 380) {
                    assertTrue("stopped tank body survives active-array removal",
                            bindings[1].body().isAttached());
                    assertTrue("stopped turret body survives active-array removal",
                            bindings[2].body().isAttached());
                }
                if (tick == 417) {
                    assertTrue("stopped carrier body survives active-array removal",
                            bindings[0].body().isAttached());
                    assertTrue("stopped carrier shadow survives active-array removal",
                            bindings[0].shadow().isAttached());
                }
            }
            assertEquals("source cleanup handoff", TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                    playback.getPhase());
            assertRetainedDotsAtHandoff(source[478], bindings);
            advance(app); // Source endBattle discards old parent at tick 479.
            assertEquals("source final tick", TurnPlaybackSequence.Phase.IDLE,
                    playback.getPhase());
            assertFalse(playback.isBlockingInput());
            assertEquals("presentation left simulation RNG untouched", nextSimulationDraw,
                    game.createPresentationRandomCopy().nextDouble(), 0d);
            assertEquals("source final private presentation RNG", sourceRandom[LAST_TICK],
                    presentationRandom.copy().nextDouble(), 0d);
            assertResetParent(display, app, bindings);
        });
    }

    private static SourceChinookDisplayDepths.Binding[] bindings(BattlefieldDisplayState state) {
        SourceChinookDisplayDepths.Binding[] result =
                new SourceChinookDisplayDepths.Binding[ACTORS];
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        for (int index = 0; index < clips.size(); index++) {
            BattlefieldDisplayState.Item item = clips.entryAt(index).getValue();
            if (!(item.owner instanceof SourceChinookDisplayDepths.Binding)) continue;
            SourceChinookDisplayDepths.Binding binding =
                    (SourceChinookDisplayDepths.Binding) item.owner;
            assertTrue("native Chinook actor index", binding.actorIndex >= 0
                    && binding.actorIndex < ACTORS);
            if (result[binding.actorIndex] != null) {
                assertSame("one binding per actor", result[binding.actorIndex], binding);
            }
            result[binding.actorIndex] = binding;
        }
        for (int actor = 0; actor < ACTORS; actor++) {
            assertNotNull("source actor bound at native index " + actor, result[actor]);
        }
        return result;
    }

    private static void assertTick(int tick, Expected[][] source,
                                   SourceChinookDisplayDepths.Binding[] bindings) {
        for (int sourceActor = 0; sourceActor < ACTORS; sourceActor++) {
            int nativeActor = 2 - sourceActor;
            SourceChinookDisplayDepths.Binding binding = bindings[nativeActor];
            FlashChinookTankAnimation.Actor actor = binding.animation.getActor(nativeActor);
            boolean removed = nativeActor == 0 ? binding.animation.isChinookRemoved()
                    : nativeActor == 1 ? binding.animation.isTankRemoved()
                    : binding.animation.isTurretRemoved();
            for (int role = 0; role < ROLES; role++) {
                Expected expected = source[sourceActor][role];
                String label = "tick " + tick + " source actor " + sourceActor
                        + " " + expected.role + ": ";
                assertEquals(label + "source actor", sourceActor, expected.actor);
                assertEquals(label + "source constructor parameter", expected.unitDepthParam,
                        binding.sourceParameter + (nativeActor == 0 ? 800000 : 0));
                if (role == 0) {
                    assertEquals(label + "physical active-array membership",
                            expected.active, !removed);
                }
                if (role == 4) {
                    // The tank's shadow is nested inside its body, not a root peer.
                    if (expected.exists) assertEquals(label + "nested parent",
                            "unit2", expected.parentName);
                    assertNull(label + "no separate tank shadow root entry",
                            nativeActor == 1 ? binding.shadow() : null);
                    continue;
                }
                SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = entry(binding, role);
                if (!expected.exists) {
                    assertNull(label + "absent direct source clip", entry);
                    continue;
                }
                assertNotNull(label + "source clip exists", entry);
                assertSame(label + "binding owns clip", binding, entry.getValue().owner);
                assertEquals(label + "clip kind", kind(role), entry.getValue().kind);
                assertEquals(label + "attached", expected.attached, entry.isAttached());
                assertEquals(label + "stored depth", expected.lastDepth, entry.getDepth());
                if (expected.attached) {
                    assertEquals(label + "occupied depth", expected.sourceDepth,
                            entry.getDepth());
                    assertEquals(label + "direct battle parent", "battleMC",
                            expected.parentName);
                }
                if (expected.attached && (role == 0 || role == 1)) {
                    FlashChinookTankAnimation.Actor pose = role == 0
                            ? actor : binding.animation.getChinookShadow();
                    assertEquals(label + "clip visibility", expected.visible, pose.visible);
                    assertEquals(label + "clip linkage", expected.linkage, pose.linkage);
                    if (expected.visible) {
                        if (role == 0) {
                            assertEquals(label + "painted body frame", expected.frame - 1,
                                    actor.sourceFrame);
                        }
                        assertNotNull(label + "painted clip X exists", expected.clipX);
                        assertNotNull(label + "painted clip Y exists", expected.clipY);
                        assertEquals(label + "painted clip X", expected.clipX,
                                pose.renderX, 0.001f);
                        assertEquals(label + "painted clip Y", expected.clipY,
                                pose.renderY, 0.001f);
                    }
                }
            }
        }
    }

    private static void assertRetainedDotsAtHandoff(Expected[][] source,
            SourceChinookDisplayDepths.Binding[] bindings) {
        for (int sourceActor = 0; sourceActor < ACTORS; sourceActor++) {
            SourceChinookDisplayDepths.Binding binding = bindings[2 - sourceActor];
            assertFalse("source body removed at handoff", source[sourceActor][0].attached);
            assertTrue("source dot retained at handoff", source[sourceActor][3].attached);
            assertFalse("native body removed at handoff", binding.body().isAttached());
            assertTrue("native dot retained at handoff", binding.dot().isAttached());
            if (sourceActor == 2) {
                assertFalse("source carrier shadow removed", source[sourceActor][1].attached);
                assertFalse("native carrier shadow removed", binding.shadow().isAttached());
            }
        }
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry(
            SourceChinookDisplayDepths.Binding binding, int role) {
        switch (role) {
            case 0: return binding.body();
            case 1: return binding.shadow();
            case 2: return null; // No direct tint for this family.
            case 3: return binding.dot();
            default: throw new AssertionError("Unknown direct source role " + role);
        }
    }

    private static BattlefieldDisplayState.Kind kind(int role) {
        switch (role) {
            case 0: return BattlefieldDisplayState.Kind.UNIT_BODY;
            case 1: return BattlefieldDisplayState.Kind.UNIT_SHADOW;
            case 3: return BattlefieldDisplayState.Kind.UNIT_DOT;
            default: throw new AssertionError("Unknown direct source role " + role);
        }
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> named(
            BattlefieldDisplayState state, String name) {
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        for (int index = 0; index < clips.size(); index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = clips.entryAt(index);
            if (name.equals(entry.getValue().name)) return entry;
        }
        return null;
    }

    private static void assertResetParent(BattlefieldDisplayState state, WorldDominationGame app,
            SourceChinookDisplayDepths.Binding[] oldBindings) throws Exception {
        for (SourceChinookDisplayDepths.Binding binding : oldBindings) {
            for (int role : new int[]{0, 1, 3}) {
                SourceDisplayList.Entry<BattlefieldDisplayState.Item> old = entry(binding, role);
                if (old != null) assertFalse("old playback clip detached", old.isAttached());
            }
        }
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        int actionBuildings = 0;
        for (int index = 0; index < clips.size(); index++) {
            BattlefieldDisplayState.Item item = clips.entryAt(index).getValue();
            assertFalse("no unit peers in canonical parent", item.kind.name().startsWith("UNIT_"));
            if (item.kind == BattlefieldDisplayState.Kind.ACTION_BUILDING) actionBuildings++;
        }
        assertEquals("five nations retain authored action slots", 10, actionBuildings);
        FlashAnimationTimeline.Frame authored = (FlashAnimationTimeline.Frame)
                field(WorldDominationGame.class, "authoredBattleFrame").get(app);
        assertNotNull("authored parent", authored);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> land = named(state, "land01");
        assertNotNull("authored land01 restored in fresh parent", land);
        assertTrue("authored land01 attached in fresh parent", land.isAttached());
        for (FlashAnimationTimeline.Layer layer : authored.layers) {
            if ("land01".equals(layer.name)) {
                assertEquals("authored land01 restored depth", layer.depth, land.getDepth());
            }
        }
    }

    private static Expected[][][] readSource() throws Exception {
        Expected[][][] result = new Expected[LAST_TICK + 1][ACTORS][ROLES];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-chinook-depths-alias.csv"), StandardCharsets.UTF_8))) {
            assertEquals("alias-resolved source schema", HEADER, reader.readLine());
            String line;
            int rows = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source Chinook columns", 22, cells.length);
                assertEquals("source repair mode", "alias", cells[0]);
                int tick = Integer.parseInt(cells[1]);
                int actor = Integer.parseInt(cells[2]);
                int role = role(cells[6]);
                assertTrue("source tick range", tick >= 0 && tick <= LAST_TICK);
                assertTrue("source actor range", actor >= 0 && actor < ACTORS);
                assertNull("duplicate source actor role", result[tick][actor][role]);
                result[tick][actor][role] = new Expected(cells);
                rows++;
            }
            assertEquals("complete source actor-role fixture",
                    (LAST_TICK + 1) * ACTORS * ROLES, rows);
        }
        for (int tick = 0; tick <= LAST_TICK; tick++) {
            for (int actor = 0; actor < ACTORS; actor++) {
                for (int role = 0; role < ROLES; role++) {
                    assertNotNull("missing source tick " + tick + " actor " + actor
                            + " role " + role, result[tick][actor][role]);
                }
            }
        }
        return result;
    }

    private static double[] readRandom() throws Exception {
        double[] result = new double[LAST_TICK + 1];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-chinook-rng-alias.csv"), StandardCharsets.UTF_8))) {
            assertEquals("mode,tick,stateSha256,nextRandom", reader.readLine());
            String line;
            int tick = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source RNG columns", 4, cells.length);
                assertEquals("source repaired mode", "alias", cells[0]);
                assertEquals("consecutive source RNG ticks", tick,
                        Integer.parseInt(cells[1]));
                assertTrue("source RNG tick range", tick <= LAST_TICK);
                assertEquals("source RNG state SHA-256 width", 64, cells[2].length());
                result[tick] = Double.parseDouble(cells[3]);
                assertTrue("source RNG unit interval", result[tick] >= 0d
                        && result[tick] < 1d);
                tick++;
            }
            assertEquals("complete source RNG frames", LAST_TICK + 1, tick);
        }
        return result;
    }

    private static CityImpact[] readCityImpact() throws Exception {
        CityImpact[] result = new CityImpact[LAST_TICK]; // Source parent survives through 478.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-chinook-city-impact.csv"), StandardCharsets.UTF_8))) {
            assertEquals("tick,cityFrame,rawAlpha,dustAttached,dustFrameZeroBased,"
                    + "randString,targetFrame", reader.readLine());
            String line;
            int tick = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("city impact columns", 7, cells.length);
                assertTrue("source city impact tick range", tick < LAST_TICK);
                assertEquals("consecutive city impact tick", tick,
                        Integer.parseInt(cells[0]));
                result[tick] = new CityImpact(cells);
                tick++;
            }
            assertEquals("complete source city impact frames", LAST_TICK, tick);
        }
        return result;
    }

    private static void assertCityImpact(int tick, CityImpact expected,
                                         BattlefieldCityRenderer renderer) {
        assertNotNull("source city impact tick " + tick, expected);
        assertEquals("city 7 image frame at tick " + tick, expected.frame,
                renderer.getSourceCityFrame(IMPACTED_CITY_ADDRESS));
        assertEquals("city 7 raw alpha at tick " + tick, expected.rawAlpha,
                renderer.getSourceCityAlpha(IMPACTED_CITY_ADDRESS), 0.001f);
        assertEquals("city 7 dust frame at tick " + tick, expected.dustFrame,
                renderer.getCityDustFrame(IMPACTED_CITY_ADDRESS));
        assertEquals("city 7 dust name at tick " + tick, expected.randString,
                renderer.getCityDustName(IMPACTED_CITY_ADDRESS));
        assertEquals("city 7 dust occupancy at tick " + tick, expected.dustAttached,
                renderer.getCityDustFrame(IMPACTED_CITY_ADDRESS) >= 0);
    }

    private static final class CityImpact {
        final int frame;
        final float rawAlpha;
        final boolean dustAttached;
        final int dustFrame;
        final int randString;

        CityImpact(String[] cells) {
            frame = Integer.parseInt(cells[1]);
            rawAlpha = Float.parseFloat(cells[2]);
            dustAttached = "1".equals(cells[3]);
            dustFrame = Integer.parseInt(cells[4]);
            randString = Integer.parseInt(cells[5]);
            int targetFrame = Integer.parseInt(cells[6]);
            assertEquals("city impact target absent before shot", targetFrame == -1,
                    randString == -1);
            assertEquals("dust presence matches source frame", dustAttached,
                    dustFrame >= 0);
        }
    }

    private static int role(String name) {
        switch (name) {
            case "body": return 0;
            case "shadow": return 1;
            case "tint": return 2;
            case "dot": return 3;
            case "nestedShadow": return 4;
            default: throw new AssertionError("Unknown source role " + name);
        }
    }

    private static final class Expected {
        final int actor;
        final String role;
        final boolean exists;
        final boolean attached;
        final String parentName;
        final int sourceDepth;
        final int lastDepth;
        final String linkage;
        final boolean visible;
        final int frame;
        final Float clipX;
        final Float clipY;
        final boolean active;
        final int unitDepthParam;

        Expected(String[] cells) {
            actor = Integer.parseInt(cells[2]);
            role = cells[6];
            exists = Boolean.parseBoolean(cells[7]);
            attached = Boolean.parseBoolean(cells[8]);
            parentName = cells[9];
            sourceDepth = cells[10].isEmpty() ? -1 : Integer.parseInt(cells[10]);
            lastDepth = cells[11].isEmpty() ? -1 : Integer.parseInt(cells[11]);
            linkage = cells[12];
            visible = Boolean.parseBoolean(cells[13]);
            frame = cells[14].isEmpty() ? 0 : Integer.parseInt(cells[14]);
            clipX = cells[17].isEmpty() ? null : Float.parseFloat(cells[17]);
            clipY = cells[18].isEmpty() ? null : Float.parseFloat(cells[18]);
            active = !cells[19].isEmpty();
            unitDepthParam = Integer.parseInt(cells[21]);
        }
    }

    /** Legal queued third turn, shared with the depth rendering control. */
    static GameState gameReadyForAttack() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier2"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier3"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 100));
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertTrue("legal research and production yielded soldier3 stock",
                game.getHuman().getWeapons().get("soldier3").active
                        && game.getHuman().getWeapons().get("soldier3").inStock > 0);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 2, "soldier3"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
