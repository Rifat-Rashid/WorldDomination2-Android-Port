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

/** Legal soldier4 World playback checked against every source tick, not a synthetic pose. */
@RunWith(AndroidJUnit4.class)
public final class SourceSuicideDepthsInstrumentedTest {
    private static final int LAST_TICK = 363;
    private static final int CITY_ADDRESS = 7; // Defender player 1, city index 2.
    private static final String UNIT_HEADER = "tick,role,action,status,storedParam,"
            + "activeIndex,deleteIndex,curX,curY,exists,attached,parent,sourceDepth,"
            + "lastDepth,linkage,visible,frame,alpha,clipX,clipY";
    private static final String CITY_HEADER = "tick,prevPopulation,population,curCasualties,"
            + "previousFrame,deathFrame,imageFrame,rawAlpha,targetFrame,hasOnEnterFrame,"
            + "randString,dustCount,dustFrame,dustDepth";

    @Test public void legalSuicideBattleMatchesAllSourceDepthPoseCityAndRandomTicks()
            throws Exception {
        ExpectedUnit[][] source = readUnits();
        ExpectedCity[] sourceCity = readCities();
        double[] sourceRandom = readRandom();
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = gameReadyForAttack();
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cityRenderer = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cityRenderer.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("four legal source turns", 4, game.getTurn());
            double simulationNext = game.createPresentationRandomCopy().nextDouble();
            BattlefieldDisplayState display = (BattlefieldDisplayState)
                    field(WorldDominationGame.class, "battlefieldDisplay").get(app);
            SourceSuicideDisplayDepths.Binding binding = binding(display);
            assertEquals("source constructor parameter", 5, binding.sourceParameter);
            TurnPlaybackSequence playback = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            ReferenceRandom presentationRandom = (ReferenceRandom)
                    field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            assertNotNull("private source battle stream", presentationRandom);
            assertTick(0, source[0], binding);
            assertCity(0, sourceCity[0], cityRenderer);
            assertEquals("source private RNG at tick 0", sourceRandom[0],
                    presentationRandom.copy().nextDouble(), 0d);
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> firstBody = binding.body();
            int[] laterWrapperVisits = {0};
            ((SourceBattleUnitPlayback) field(WorldDominationGame.class,
                    "sourceUnitPlayback").get(app)).add(() -> {
                laterWrapperVisits[0]++;
                return false;
            });
            for (int tick = 1; tick < LAST_TICK; tick++) {
                int previousLaterVisits = laterWrapperVisits[0];
                advance(app);
                assertTick(tick, source[tick], binding);
                assertCity(tick, sourceCity[tick], cityRenderer);
                assertEquals("source private RNG at tick " + tick, sourceRandom[tick],
                        presentationRandom.copy().nextDouble(), 0d);
                if (tick == 300) {
                    assertFalse("suicide body removed at impact", firstBody.isAttached());
                    assertTrue("explosion attached at source impact", binding.explosion().isAttached());
                }
                if (tick == 301) {
                    assertEquals("suicide removal continues later source wrappers",
                            previousLaterVisits + 1, laterWrapperVisits[0]);
                }
                if (tick == 320 || tick == 361) {
                    assertTrue("stopped explosion retained at " + tick,
                            binding.explosion().isAttached());
                    assertEquals("source stopped explosion frame", 20,
                            binding.animation.getActor(2).sourceFrame);
                }
                if (tick == 362) {
                    assertFalse("allUnitsDone detached stopped explosion",
                            binding.explosion().isAttached());
                    assertTrue("source dot retained until parent handoff",
                            binding.dot().isAttached());
                }
            }
            assertEquals("source cleanup handoff", TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                    playback.getPhase());
            advance(app);
            assertEquals("source endBattle tick", TurnPlaybackSequence.Phase.IDLE,
                    playback.getPhase());
            assertFalse(playback.isBlockingInput());
            assertEquals("source private RNG after endBattle", sourceRandom[LAST_TICK],
                    presentationRandom.copy().nextDouble(), 0d);
            assertEquals("simulation random stream was untouched", simulationNext,
                    game.createPresentationRandomCopy().nextDouble(), 0d);
            assertResetParent(display, binding);
        });
    }

    private static void assertTick(int tick, ExpectedUnit[] source,
                                   SourceSuicideDisplayDepths.Binding binding) {
        assertEquals("source Unit.depth_ at tick " + tick, source[0].storedParam,
                binding.sourceParameter);
        assertEquals("source wrapper active membership at tick " + tick,
                source[0].activeIndex >= 0, !binding.animation.isRemoved());
        assertEquals("source Unit action at tick " + tick, source[0].action,
                binding.animation.getSourceAction());
        FlashSuicideBomberAnimation.Actor body = binding.animation.getActor(0);
        assertEquals("logical body X at tick " + tick, source[0].curX,
                body.sourceX, 0.02f);
        assertEquals("logical body Y at tick " + tick, source[0].curY,
                body.sourceY, 0.02f);
        for (int role = 0; role < 4; role++) {
            ExpectedUnit expected = source[role];
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = entry(binding, role);
            String label = "tick " + tick + " " + expected.role + ": ";
            if (!expected.exists) {
                assertNull(label + "clip has not been created", entry);
                continue;
            }
            assertNotNull(label + "source clip exists", entry);
            assertSame(label + "typed source binding owns entry", binding,
                    entry.getValue().owner);
            assertEquals(label + "kind", kind(role), entry.getValue().kind);
            assertEquals(label + "attachment", expected.attached, entry.isAttached());
            assertEquals(label + "stored depth after detach", expected.lastDepth,
                    entry.getDepth());
            if (expected.attached) {
                assertEquals(label + "occupied source depth", expected.sourceDepth,
                        entry.getDepth());
                assertEquals(label + "battle parent", "battleMC", expected.parent);
            }
            if (role == 2) continue; // The target dot is a direct display entry, not an actor.
            FlashSuicideBomberAnimation.Actor pose = binding.animation.getActor(
                    role == 3 ? 2 : role);
            assertEquals(label + "linkage", expected.linkage, pose.linkage);
            if (expected.attached) {
                assertEquals(label + "visibility", expected.visible, pose.visible);
                if (expected.visible) {
                    assertEquals(label + "painted frame", expected.frame - 1,
                            pose.sourceFrame);
                    assertEquals(label + "raw clip alpha", expected.alpha / 100f,
                            pose.alpha, 0.0002f);
                    assertEquals(label + "painted clip X", expected.clipX,
                            pose.renderX, 0.02f);
                    assertEquals(label + "painted clip Y", expected.clipY,
                            pose.renderY, 0.02f);
                }
            }
        }
    }

    private static void assertCity(int tick, ExpectedCity expected,
                                   BattlefieldCityRenderer renderer) throws Exception {
        assertEquals("city 7 frame at tick " + tick, expected.frame,
                renderer.getSourceCityFrame(CITY_ADDRESS));
        assertEquals("city 7 raw alpha at tick " + tick, expected.alpha,
                renderer.getSourceCityAlpha(CITY_ADDRESS), 0.001f);
        assertEquals("city 7 dust random name at tick " + tick, expected.randString,
                renderer.getCityDustName(CITY_ADDRESS));
        assertEquals("city 7 dust frame at tick " + tick,
                expected.dustFrame < 0 ? -1 : expected.dustFrame - 1,
                renderer.getCityDustFrame(CITY_ADDRESS));
        CityShrinkSequence[] sequences = (CityShrinkSequence[])
                field(BattlefieldCityRenderer.class, "citySequences").get(renderer);
        if (expected.targetFrame >= 0) {
            assertEquals("city 7 shrink target at tick " + tick, expected.targetFrame,
                    sequences[CITY_ADDRESS].getTargetFrame());
        }
    }

    private static SourceSuicideDisplayDepths.Binding binding(BattlefieldDisplayState state) {
        SourceSuicideDisplayDepths.Binding result = null;
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        for (int index = 0; index < clips.size(); index++) {
            BattlefieldDisplayState.Item item = clips.entryAt(index).getValue();
            if (!(item.owner instanceof SourceSuicideDisplayDepths.Binding)) continue;
            SourceSuicideDisplayDepths.Binding found =
                    (SourceSuicideDisplayDepths.Binding) item.owner;
            if (result != null) assertSame("one source suicide binding", result, found);
            result = found;
        }
        assertNotNull("legal human suicide binding", result);
        assertEquals("single source actor", 0, result.actorIndex);
        return result;
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry(
            SourceSuicideDisplayDepths.Binding binding, int role) {
        switch (role) {
            case 0: return binding.body();
            case 1: return binding.tint();
            case 2: return binding.dot();
            case 3: return binding.explosion();
            default: throw new AssertionError("Unexpected source suicide role " + role);
        }
    }

    private static BattlefieldDisplayState.Kind kind(int role) {
        switch (role) {
            case 0: return BattlefieldDisplayState.Kind.UNIT_BODY;
            case 1: return BattlefieldDisplayState.Kind.UNIT_TINT;
            case 2: return BattlefieldDisplayState.Kind.UNIT_DOT;
            case 3: return BattlefieldDisplayState.Kind.UNIT_BODY;
            default: throw new AssertionError("Unexpected source suicide role " + role);
        }
    }

    private static void assertResetParent(BattlefieldDisplayState display,
                                          SourceSuicideDisplayDepths.Binding old) {
        for (int role = 0; role < 4; role++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = entry(old, role);
            if (entry != null) assertFalse("old source clip detached", entry.isAttached());
        }
        SourceDisplayList<BattlefieldDisplayState.Item> clips = display.clips();
        for (int index = 0; index < clips.size(); index++) {
            assertFalse("fresh parent has no old unit roles",
                    clips.entryAt(index).getValue().kind.name().startsWith("UNIT_"));
        }
    }

    private static ExpectedUnit[][] readUnits() throws Exception {
        ExpectedUnit[][] result = new ExpectedUnit[LAST_TICK + 1][4];
        try (BufferedReader reader = asset("source-suicide-unit.csv")) {
            assertEquals(UNIT_HEADER, reader.readLine());
            String line;
            int rows = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source unit columns", 20, cells.length);
                int tick = Integer.parseInt(cells[0]);
                int role = role(cells[1]);
                assertTrue("source unit tick", tick >= 0 && tick <= LAST_TICK);
                assertNull("unique source role per tick", result[tick][role]);
                result[tick][role] = new ExpectedUnit(cells);
                rows++;
            }
            assertEquals("four roles on all source ticks", (LAST_TICK + 1) * 4, rows);
        }
        for (int tick = 0; tick <= LAST_TICK; tick++)
            for (int role = 0; role < 4; role++)
                assertNotNull("source unit tick " + tick + " role " + role,
                        result[tick][role]);
        return result;
    }

    private static ExpectedCity[] readCities() throws Exception {
        ExpectedCity[] result = new ExpectedCity[LAST_TICK + 1];
        try (BufferedReader reader = asset("source-suicide-city.csv")) {
            assertEquals(CITY_HEADER, reader.readLine());
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source city columns", 14, cells.length);
                assertEquals("source city consecutive tick", count,
                        Integer.parseInt(cells[0]));
                result[count++] = new ExpectedCity(cells);
            }
            assertEquals("all source city ticks", LAST_TICK + 1, count);
        }
        return result;
    }

    private static double[] readRandom() throws Exception {
        double[] result = new double[LAST_TICK + 1];
        try (BufferedReader reader = asset("source-suicide-rng.csv")) {
            assertEquals("tick,stateSha256,nextRandom", reader.readLine());
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source RNG columns", 3, cells.length);
                assertEquals("source RNG consecutive tick", count,
                        Integer.parseInt(cells[0]));
                assertEquals("source RNG state hash width", 64, cells[1].length());
                result[count++] = Double.parseDouble(cells[2]);
            }
            assertEquals("all private source RNG ticks", LAST_TICK + 1, count);
        }
        return result;
    }

    private static BufferedReader asset(String name) throws Exception {
        return new BufferedReader(new InputStreamReader(InstrumentationRegistry
                .getInstrumentation().getContext().getAssets().open(name),
                StandardCharsets.UTF_8));
    }

    private static int role(String value) {
        switch (value) {
            case "body": return 0;
            case "tint": return 1;
            case "dot": return 2;
            case "explosion": return 3;
            default: throw new AssertionError("Unknown source role " + value);
        }
    }

    private static final class ExpectedUnit {
        final String role;
        final String action;
        final int storedParam;
        final int activeIndex;
        final float curX;
        final float curY;
        final boolean exists;
        final boolean attached;
        final String parent;
        final int sourceDepth;
        final int lastDepth;
        final String linkage;
        final boolean visible;
        final int frame;
        final float alpha;
        final float clipX;
        final float clipY;

        ExpectedUnit(String[] cells) {
            role = cells[1];
            action = cells[2];
            storedParam = Integer.parseInt(cells[4]);
            activeIndex = Integer.parseInt(cells[5]);
            curX = Float.parseFloat(cells[7]);
            curY = Float.parseFloat(cells[8]);
            exists = "1".equals(cells[9]);
            attached = "1".equals(cells[10]);
            parent = cells[11];
            sourceDepth = Integer.parseInt(cells[12]);
            lastDepth = Integer.parseInt(cells[13]);
            linkage = cells[14];
            visible = "1".equals(cells[15]);
            frame = Integer.parseInt(cells[16]);
            alpha = cells[17].isEmpty() ? 0f : Float.parseFloat(cells[17]);
            clipX = cells[18].isEmpty() ? 0f : Float.parseFloat(cells[18]);
            clipY = cells[19].isEmpty() ? 0f : Float.parseFloat(cells[19]);
        }
    }

    private static final class ExpectedCity {
        final int frame;
        final float alpha;
        final int targetFrame;
        final int randString;
        final int dustFrame;

        ExpectedCity(String[] cells) {
            frame = Integer.parseInt(cells[6]);
            alpha = Float.parseFloat(cells[7]);
            targetFrame = cells[8].isEmpty() ? -1 : Integer.parseInt(cells[8]);
            randString = cells[10].isEmpty() ? -1 : Integer.parseInt(cells[10]);
            dustFrame = Integer.parseInt(cells[12]);
            assertEquals("source dust count consistent with frame", dustFrame >= 0,
                    Integer.parseInt(cells[11]) > 0);
        }
    }

    /** Source reachable four-turn setup, without direct stock or unlock mutation. */
    static GameState gameReadyForAttack() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        research(game, "soldier2"); resources(game); game.confirmTurn();
        research(game, "soldier3"); resources(game); game.confirmTurn();
        research(game, "soldier4");
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 100));
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertTrue("legal production yielded active soldier4 stock",
                game.getHuman().getWeapons().get("soldier4").active
                        && game.getHuman().getWeapons().get("soldier4").inStock > 0);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 2, "soldier4"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    private static void research(GameState game, String technology) {
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology(technology));
        assertTrue(game.acceptSelectedAction());
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
