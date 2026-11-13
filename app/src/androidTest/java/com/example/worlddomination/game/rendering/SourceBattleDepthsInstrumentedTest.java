package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real World depth occupancy against read-only Flash battles, including hidden peers. */
@RunWith(AndroidJUnit4.class)
public final class SourceBattleDepthsInstrumentedTest {
    private static final int ACTORS = 4;
    private static final int ROLES = 4;
    private static final String HEADER = "opponents,tick,actor,unitName,mcID,action,role,"
            + "exists,attached,visible,sourceDepth,lastDepth,linkage,frame,activeIndex,"
            + "deleteIndex,unitDepthParam";

    @Test public void twoOpponentApacheDepthsMatchEverySourceFrame() throws Exception {
        assertBattle(2, 525);
    }

    @Test public void fourOpponentApacheDepthsMatchEverySourceFrame() throws Exception {
        assertBattle(4, 526);
    }

    private static void assertBattle(int opponents, int lastTick) throws Exception {
        Expected[][][] expected = readSource(opponents, lastTick);
        double[] sourceRandom = readRandom(opponents, lastTick);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = prepareThirdTurn(opponents);
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals(3, game.getTurn());
            double nextSimulationDraw = game.createPresentationRandomCopy().nextDouble();
            BattlefieldDisplayState display = (BattlefieldDisplayState)
                    field(WorldDominationGame.class, "battlefieldDisplay").get(app);
            SourceApacheDisplayDepths.Binding[] bindings = bindings(display);
            TurnPlaybackSequence playback = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            com.example.worlddomination.game.simulation.ReferenceRandom presentationRandom =
                    (com.example.worlddomination.game.simulation.ReferenceRandom)
                    field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            assertEquals("source post-construction RNG", sourceRandom[0],
                    presentationRandom.copy().nextDouble(), 0d);
            assertTick(0, expected[0], bindings);
            for (int tick = 1; tick < lastTick; tick++) {
                advance(app);
                assertEquals("source private presentation RNG at tick " + tick,
                        sourceRandom[tick], presentationRandom.copy().nextDouble(), 0d);
                assertTick(tick, expected[tick], bindings);
            }
            assertEquals("source cleanup handoff", TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                    playback.getPhase());
            advance(app); // Source endBattle discards its parent and target dots here.
            assertEquals("source final tick", TurnPlaybackSequence.Phase.IDLE,
                    playback.getPhase());
            assertFalse(playback.isBlockingInput());
            assertEquals("battle playback did not advance simulation RNG",
                    nextSimulationDraw, game.createPresentationRandomCopy().nextDouble(), 0d);
            assertEquals("source final presentation RNG", sourceRandom[lastTick],
                    presentationRandom.copy().nextDouble(), 0d);
            assertResetParent(display, app, bindings);
        });
    }

    private static SourceApacheDisplayDepths.Binding[] bindings(BattlefieldDisplayState state) {
        SourceApacheDisplayDepths.Binding[] result =
                new SourceApacheDisplayDepths.Binding[ACTORS];
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        for (int index = 0; index < clips.size(); index++) {
            BattlefieldDisplayState.Item item = clips.entryAt(index).getValue();
            if (!(item.owner instanceof SourceApacheDisplayDepths.Binding)) continue;
            SourceApacheDisplayDepths.Binding binding =
                    (SourceApacheDisplayDepths.Binding) item.owner;
            int actor = binding.actorIndex;
            assertTrue("native actor index", actor >= 0 && actor < ACTORS);
            if (result[actor] != null) assertSame("one binding per native actor",
                    result[actor], binding);
            result[actor] = binding;
        }
        for (int actor = 0; actor < ACTORS; actor++) {
            assertNotNull("source actor bound at native index " + actor, result[actor]);
        }
        return result;
    }

    private static void assertTick(int tick, Expected[][] source,
                                   SourceApacheDisplayDepths.Binding[] bindings) {
        for (int sourceActor = 0; sourceActor < ACTORS; sourceActor++) {
            int nativeActor = 3 - sourceActor;
            SourceApacheDisplayDepths.Binding binding = bindings[nativeActor];
            for (int role = 0; role < ROLES; role++) {
                Expected expected = source[sourceActor][role];
                String label = "tick " + tick + " unit" + (sourceActor + 1)
                        + " " + expected.role + " pose=("
                        + binding.animation.getActor(nativeActor).sourceX + ","
                        + binding.animation.getActor(nativeActor).sourceY + ") ";
                assertEquals(label + "source unit", sourceActor + 1, expected.actor);
                assertEquals(label + "depth parameter", expected.unitDepthParam,
                        binding.sourceParameter + (nativeActor == 0 ? 800000 : 0));
                SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                        entry(binding, role);
                if (!expected.exists) {
                    assertNull(label + "absent source clip", entry);
                    continue;
                }
                assertNotNull(label + "source clip exists", entry);
                assertSame(label + "owner", binding, entry.getValue().owner);
                assertEquals(label + "kind", kind(role), entry.getValue().kind);
                assertEquals(label + "attached", expected.attached, entry.isAttached());
                if (role == 0 && expected.attached) {
                    FlashApacheMarineAnimation.Actor actor = binding.animation.getActor(nativeActor);
                    assertEquals(label + "body visibility", expected.visible, actor.visible);
                    assertEquals(label + "body linkage", expected.linkage, actor.linkage);
                    assertEquals(label + "body frame", expected.frame - 1, actor.sourceFrame);
                }
                assertEquals(label + "stored depth", expected.lastDepth, entry.getDepth());
                if (expected.attached) {
                    assertEquals(label + "occupied depth", expected.sourceDepth,
                            entry.getDepth());
                }
            }
        }
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry(
            SourceApacheDisplayDepths.Binding binding, int role) {
        switch (role) {
            case 0: return binding.body();
            case 1: return binding.shadow();
            case 2: return binding.tint();
            case 3: return binding.dot();
            default: throw new AssertionError("Unknown role " + role);
        }
    }

    private static BattlefieldDisplayState.Kind kind(int role) {
        switch (role) {
            case 0: return BattlefieldDisplayState.Kind.UNIT_BODY;
            case 1: return BattlefieldDisplayState.Kind.UNIT_SHADOW;
            case 2: return BattlefieldDisplayState.Kind.UNIT_TINT;
            case 3: return BattlefieldDisplayState.Kind.UNIT_DOT;
            default: throw new AssertionError("Unknown role " + role);
        }
    }

    private static void assertResetParent(BattlefieldDisplayState state,
                                          WorldDominationGame app,
                                          SourceApacheDisplayDepths.Binding[] oldBindings)
            throws Exception {
        for (SourceApacheDisplayDepths.Binding binding : oldBindings) {
            for (int role = 0; role < ROLES; role++) {
                SourceDisplayList.Entry<BattlefieldDisplayState.Item> old = entry(binding, role);
                if (old != null) assertFalse("old playback clip detached", old.isAttached());
            }
        }
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        int actionBuildings = 0;
        for (int index = 0; index < clips.size(); index++) {
            BattlefieldDisplayState.Item item = clips.entryAt(index).getValue();
            assertFalse("no Unit peers in canonical parent", item.kind.name().startsWith("UNIT_"));
            if (item.kind == BattlefieldDisplayState.Kind.ACTION_BUILDING) actionBuildings++;
        }
        assertEquals("five nations keep two authored action-building slots", 10,
                actionBuildings);
        FlashAnimationTimeline.Frame authored = (FlashAnimationTimeline.Frame)
                field(WorldDominationGame.class, "authoredBattleFrame").get(app);
        assertNotNull("authored battle parent", authored);
        for (FlashAnimationTimeline.Layer layer : authored.layers) {
            String name = layer.name;
            if (name == null || name.length() != 5 || !name.startsWith("ab")
                    || name.charAt(3) != '_' || name.charAt(4) < '1'
                    || name.charAt(4) > '2') continue;
            int player = name.charAt(2) - '0';
            assertTrue("authored player", player >= 0 && player < 5);
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> slot =
                    state.actionBuilding(player, name.charAt(4) - '0');
            assertNotNull("authored action building " + name, slot);
            assertTrue("authored slot attached " + name, slot.isAttached());
            assertEquals("authored slot depth " + name, layer.depth, slot.getDepth());
        }
    }

    private static Expected[][][] readSource(int opponents, int lastTick) throws Exception {
        Expected[][][] result = new Expected[lastTick + 1][ACTORS][ROLES];
        String asset = "source-apache-depths-" + opponents + ".csv";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open(asset), StandardCharsets.UTF_8))) {
            assertEquals("source depth schema", HEADER, reader.readLine());
            String line;
            int rows = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source depth columns", 17, cells.length);
                assertEquals("source opponent case", opponents, Integer.parseInt(cells[0]));
                int tick = Integer.parseInt(cells[1]);
                int actor = Integer.parseInt(cells[2]) - 1;
                int role = role(cells[6]);
                assertTrue("source tick range", tick >= 0 && tick <= lastTick);
                assertTrue("source actor range", actor >= 0 && actor < ACTORS);
                assertNull("duplicate source actor role", result[tick][actor][role]);
                result[tick][actor][role] = new Expected(cells);
                rows++;
            }
            assertEquals("complete source depth rows", (lastTick + 1) * ACTORS * ROLES,
                    rows);
        }
        for (int tick = 0; tick <= lastTick; tick++) {
            for (int actor = 0; actor < ACTORS; actor++) {
                for (int role = 0; role < ROLES; role++) {
                    assertNotNull("missing tick " + tick + " actor " + actor
                            + " role " + role, result[tick][actor][role]);
                }
            }
        }
        return result;
    }

    private static double[] readRandom(int opponents, int lastTick) throws Exception {
        double[] result = new double[lastTick + 1];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-apache-rng-" + opponents + ".csv"), StandardCharsets.UTF_8))) {
            assertEquals("opponents,tick,nextRandom", reader.readLine());
            String line;
            int tick = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals(3, cells.length);
                assertEquals(opponents, Integer.parseInt(cells[0]));
                assertEquals("consecutive source RNG ticks", tick, Integer.parseInt(cells[1]));
                assertTrue("source RNG tick range", tick <= lastTick);
                result[tick] = Double.parseDouble(cells[2]);
                assertTrue(result[tick] >= 0d && result[tick] < 1d);
                tick++;
            }
            assertEquals(lastTick + 1, tick);
        }
        return result;
    }

    private static int role(String role) {
        switch (role) {
            case "body": return 0;
            case "shadow": return 1;
            case "tint": return 2;
            case "dot": return 3;
            default: throw new AssertionError("Unexpected source role " + role);
        }
    }

    private static final class Expected {
        final int actor;
        final String role;
        final boolean exists;
        final boolean attached;
        final boolean visible;
        final String linkage;
        final int frame;
        final int sourceDepth;
        final int lastDepth;
        final int unitDepthParam;

        Expected(String[] cells) {
            actor = Integer.parseInt(cells[2]);
            role = cells[6];
            exists = Boolean.parseBoolean(cells[7]);
            attached = Boolean.parseBoolean(cells[8]);
            visible = Boolean.parseBoolean(cells[9]);
            linkage = cells[12];
            frame = cells[13].isEmpty() ? 0 : Integer.parseInt(cells[13]);
            sourceDepth = cells[10].isEmpty() ? -1 : Integer.parseInt(cells[10]);
            lastDepth = cells[11].isEmpty() ? -1 : Integer.parseInt(cells[11]);
            unitDepthParam = Integer.parseInt(cells[16]);
        }
    }

    private static GameState prepareThirdTurn(int opponents) {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        for (int opponent = 0; opponent < opponents; opponent++) {
            assertTrue("select source leader " + opponent, setup.toggleOpponent(opponent));
        }
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier2"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 100));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier2"));
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
