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

/** Legal soldier1 battle against all direct-root unit clips in the source replay. */
@RunWith(AndroidJUnit4.class)
public final class SourceInfantryDepthsInstrumentedTest {
    private static final int ACTORS = 4; // Source visitation: boat, unit3, unit2, unit1.
    private static final int ROLES = 3;
    private static final String HEADER = "tick,actor,unitName,mcID,action,role,exists,"
            + "attached,visible,sourceDepth,lastDepth,linkage,frame,activeIndex,"
            + "deleteIndex,unitDepthParam,curX,curY,clipX,clipY";

    @Test public void twoOpponentSoldierOneBattleMatchesSourceDepthsAndPaintedPoses()
            throws Exception {
        assertBattle(2, 1, 0, 442);
    }

    @Test public void fourOpponentSoldierOneBattleMatchesSourceDepthsAndPaintedPoses()
            throws Exception {
        assertBattle(4, 4, 4, 648);
    }

    private static void assertBattle(int opponents, int target, int city, int lastTick)
            throws Exception {
        Expected[][][] source = readSource(opponents, lastTick);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = legalBattle(opponents, target, city);
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("source first turn", 1, game.getTurn());
            double nextSimulationDraw = game.createPresentationRandomCopy().nextDouble();
            BattlefieldDisplayState display = (BattlefieldDisplayState)
                    field(WorldDominationGame.class, "battlefieldDisplay").get(app);
            SourceInfantryDisplayDepths.Binding[] bindings = bindings(display);
            TurnPlaybackSequence playback = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            assertTick(0, source[0], bindings);
            for (int tick = 1; tick < lastTick; tick++) {
                advance(app);
                assertTick(tick, source[tick], bindings);
            }
            assertEquals("source cleanup handoff", TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                    playback.getPhase());
            assertRetainedDotsAtHandoff(source[lastTick - 1], bindings);
            advance(app); // Source endBattle discards the old parent at the final tick.
            assertEquals("source final tick", TurnPlaybackSequence.Phase.IDLE,
                    playback.getPhase());
            assertFalse(playback.isBlockingInput());
            assertEquals("display playback did not advance simulation RNG", nextSimulationDraw,
                    game.createPresentationRandomCopy().nextDouble(), 0d);
            assertResetParent(display, app, bindings);
        });
    }

    private static SourceInfantryDisplayDepths.Binding[] bindings(BattlefieldDisplayState state) {
        SourceInfantryDisplayDepths.Binding[] result =
                new SourceInfantryDisplayDepths.Binding[ACTORS];
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        for (int index = 0; index < clips.size(); index++) {
            BattlefieldDisplayState.Item item = clips.entryAt(index).getValue();
            if (!(item.owner instanceof SourceInfantryDisplayDepths.Binding)) continue;
            SourceInfantryDisplayDepths.Binding binding =
                    (SourceInfantryDisplayDepths.Binding) item.owner;
            int actor = binding.actorIndex;
            assertTrue("native infantry actor index", actor >= 0 && actor < ACTORS);
            if (result[actor] != null) assertSame("one binding per actor", result[actor], binding);
            result[actor] = binding;
        }
        for (int actor = 0; actor < ACTORS; actor++) {
            assertNotNull("source actor bound at native index " + actor, result[actor]);
        }
        return result;
    }

    private static void assertTick(int tick, Expected[][] expected,
                                   SourceInfantryDisplayDepths.Binding[] bindings) {
        for (int actorIndex = 0; actorIndex < ACTORS; actorIndex++) {
            SourceInfantryDisplayDepths.Binding binding = bindings[actorIndex];
            FlashSoldierOneAnimation.Actor actor = binding.animation.getActor(actorIndex);
            for (int role = 0; role < ROLES; role++) {
                Expected source = expected[actorIndex][role];
                String label = "tick " + tick + " actor " + actorIndex + " " + source.role + ": ";
                assertEquals(label + "source actor", actorIndex, source.actor);
                assertEquals(label + "constructor depth parameter", source.unitDepthParam,
                        binding.sourceParameter);
                SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = entry(binding, role);
                if (!source.exists) {
                    assertNull(label + "absent source clip", entry);
                    continue;
                }
                assertNotNull(label + "source clip exists", entry);
                assertSame(label + "binding owns clip", binding, entry.getValue().owner);
                assertEquals(label + "clip kind", kind(role), entry.getValue().kind);
                assertEquals(label + "attached", source.attached, entry.isAttached());
                assertEquals(label + "stored depth", source.lastDepth, entry.getDepth());
                if (source.attached) {
                    assertEquals(label + "occupied depth", source.sourceDepth, entry.getDepth());
                }
                if (role == 0 && source.attached) {
                    assertEquals(label + "body visibility", source.visible, actor.visible);
                    assertEquals(label + "body linkage", source.linkage, actor.linkage);
                    if (source.visible) {
                        assertEquals(label + "painted body frame", source.frame - 1,
                                actor.sourceFrame);
                        assertTrue(label + "painted clip X exists", source.clipX != null);
                        assertTrue(label + "painted clip Y exists", source.clipY != null);
                        assertEquals(label + "painted clip X", source.clipX,
                                actor.sourceX, 0.001f);
                        assertEquals(label + "painted clip Y", source.clipY,
                                actor.sourceY, 0.001f);
                    }
                }
            }
        }
    }

    private static void assertRetainedDotsAtHandoff(Expected[][] source,
            SourceInfantryDisplayDepths.Binding[] bindings) {
        for (int actor = 0; actor < ACTORS; actor++) {
            assertFalse("source body removed at handoff " + actor, source[actor][0].attached);
            assertFalse("source tint removed at handoff " + actor, source[actor][1].attached);
            assertTrue("source dot retained at handoff " + actor, source[actor][2].attached);
            assertFalse("native body removed at handoff " + actor,
                    bindings[actor].body().isAttached());
            assertFalse("native tint removed at handoff " + actor,
                    bindings[actor].tint().isAttached());
            assertTrue("native dot retained at handoff " + actor,
                    bindings[actor].dot().isAttached());
        }
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry(
            SourceInfantryDisplayDepths.Binding binding, int role) {
        switch (role) {
            case 0: return binding.body();
            case 1: return binding.tint();
            case 2: return binding.dot();
            default: throw new AssertionError("Unknown source role " + role);
        }
    }

    private static BattlefieldDisplayState.Kind kind(int role) {
        switch (role) {
            case 0: return BattlefieldDisplayState.Kind.UNIT_BODY;
            case 1: return BattlefieldDisplayState.Kind.UNIT_TINT;
            case 2: return BattlefieldDisplayState.Kind.UNIT_DOT;
            default: throw new AssertionError("Unknown source role " + role);
        }
    }

    private static void assertResetParent(BattlefieldDisplayState state, WorldDominationGame app,
            SourceInfantryDisplayDepths.Binding[] oldBindings) throws Exception {
        for (SourceInfantryDisplayDepths.Binding binding : oldBindings) {
            for (int role = 0; role < ROLES; role++) {
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-infantry-depths-" + opponents + ".csv"),
                StandardCharsets.UTF_8))) {
            assertEquals("source infantry schema", HEADER, reader.readLine());
            String line;
            int rows = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source infantry columns", 20, cells.length);
                int tick = Integer.parseInt(cells[0]);
                int actor = Integer.parseInt(cells[1]);
                int role = role(cells[5]);
                assertTrue("source tick range", tick >= 0 && tick <= lastTick);
                assertTrue("source actor range", actor >= 0 && actor < ACTORS);
                assertNull("duplicate source actor role", result[tick][actor][role]);
                result[tick][actor][role] = new Expected(cells);
                rows++;
            }
            assertEquals("complete source actor-role fixture", (lastTick + 1) * ACTORS * ROLES,
                    rows);
        }
        for (int tick = 0; tick <= lastTick; tick++) {
            for (int actor = 0; actor < ACTORS; actor++) {
                for (int role = 0; role < ROLES; role++) {
                    assertNotNull("missing source tick " + tick + " actor " + actor
                            + " role " + role, result[tick][actor][role]);
                }
            }
        }
        return result;
    }

    private static int role(String name) {
        switch (name) {
            case "body": return 0;
            case "tint": return 1;
            case "dot": return 2;
            default: throw new AssertionError("Unknown source role " + name);
        }
    }

    private static final class Expected {
        final int actor;
        final String role;
        final boolean exists;
        final boolean attached;
        final boolean visible;
        final int sourceDepth;
        final int lastDepth;
        final String linkage;
        final int frame;
        final int unitDepthParam;
        final Float clipX;
        final Float clipY;

        Expected(String[] cells) {
            actor = Integer.parseInt(cells[1]);
            role = cells[5];
            exists = Boolean.parseBoolean(cells[6]);
            attached = Boolean.parseBoolean(cells[7]);
            visible = Boolean.parseBoolean(cells[8]);
            sourceDepth = cells[9].isEmpty() ? -1 : Integer.parseInt(cells[9]);
            lastDepth = cells[10].isEmpty() ? -1 : Integer.parseInt(cells[10]);
            linkage = cells[11];
            frame = cells[12].isEmpty() ? 0 : Integer.parseInt(cells[12]);
            unitDepthParam = Integer.parseInt(cells[15]);
            clipX = cells[18].isEmpty() ? null : Float.parseFloat(cells[18]);
            clipY = cells[19].isEmpty() ? null : Float.parseFloat(cells[19]);
        }
    }

    private static GameState legalBattle(int opponents, int target, int city) {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        for (int opponent = 0; opponent < opponents; opponent++) {
            assertTrue("source selected opponent " + opponent,
                    setup.toggleOpponent(opponent));
        }
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(target, city, "soldier1"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        assertEquals(2, game.getQueuedActionCount());
        return game;
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
