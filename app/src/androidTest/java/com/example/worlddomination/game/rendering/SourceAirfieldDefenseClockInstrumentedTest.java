package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Real World visits compared with legal source host states after every full 24 Hz tick. */
@RunWith(AndroidJUnit4.class)
public final class SourceAirfieldDefenseClockInstrumentedTest {
    private static final String PREFIX = "source-airfield-defense-";

    @Test public void plane001SuccessHasNoAirfieldDefenseForAll534HostTicks() throws Exception {
        verify("p1_success", 534, 0, false);
    }

    @Test public void plane001InterceptionMatchesAll378HostTicks() throws Exception {
        verify("p1_intercept", 378, 119, true);
    }

    @Test public void plane002SuccessHasNoAirfieldDefenseForAll572HostTicks() throws Exception {
        verify("p2_success", 572, 0, false);
    }

    @Test public void plane002InterceptionMatchesAll364HostTicks() throws Exception {
        verify("p2_intercept", 364, 105, true);
    }

    private static void verify(String scenario, int expectedHosts, int expectedCallbacks,
                               boolean intercepted) throws Exception {
        JsonValue provenance = provenance(scenario);
        List<Row> hosts = hostRows(scenario, expectedCallbacks);
        assertEquals(expectedHosts, hosts.size());
        assertEquals(expectedHosts, provenance.getInt("hostRows"));
        assertEquals(expectedCallbacks, provenance.getInt("callbackPairs"));
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = ready(scenario, provenance);
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("legal source turn", provenance.get("schedule").size, game.getTurn());
            String weapon = scenario.startsWith("p1") ? "plane1" : "plane2";
            GameState.CombatEvent result = null;
            for (GameState.CombatEvent event : game.getLastTurnCombatEvents())
                if (event.attackerPlayerId == 0 && weapon.equals(event.weaponName)) result = event;
            assertNotNull("legal " + scenario + " battle event", result);
            assertEquals("resolved source result", intercepted
                    ? GameState.CombatEventType.STRIKE_INTERCEPTED
                    : GameState.CombatEventType.STRIKE_IMPACT, result.type);
            @SuppressWarnings("unchecked")
            List<FlashStandalonePlaneAnimation> planes =
                    (List<FlashStandalonePlaneAnimation>) field(WorldDominationGame.class,
                            "standalonePlaneAnimations").get(app);
            FlashStandalonePlaneAnimation plane = null;
            String linkage = scenario.startsWith("p1") ? "plane001" : "plane002";
            for (FlashStandalonePlaneAnimation candidate : planes)
                if (candidate.getSourcePlayback() != null
                        && candidate.targetPlayerId() == 1
                        && linkage.equals(candidate.getRoute().linkage)) {
                    plane = candidate;
                    break;
                }
            assertNotNull("physical source " + scenario, plane);
            SourceAircraftPlayback core = plane.getSourcePlayback();
            @SuppressWarnings("unchecked")
            List<SourceAirDefenseController> groups =
                    (List<SourceAirDefenseController>) field(WorldDominationGame.class,
                            "sourceAirDefenseControllers").get(app);
            assertEquals("physical defender groups", intercepted ? 1 : 0, groups.size());
            SourceAirDefenseController group = intercepted ? groups.get(0) : null;
            SourceAirDefenseController.UnitVisit visit = null;
            FlashActionBuildingAnimation building = null;
            if (intercepted) {
                assertEquals(1, group.getDefenderPlayerId());
                assertEquals(2, group.getActionBuildingSlot());
                @SuppressWarnings("unchecked")
                List<SourceAirDefenseController.UnitVisit> visits =
                        (List<SourceAirDefenseController.UnitVisit>) field(
                                SourceAirDefenseController.class, "units").get(group);
                assertEquals("one standalone source Unit", 1, visits.size());
                visit = visits.get(0);
                assertSame(plane, visit.airfieldAnimation);
                building = group.getBuilding();
                assertNotNull(building);
                assertEquals("antiAir_fire", building.frameName);
                assertEquals("chr", building.religionCode);
            }
            ReferenceRandom privateRandom = (ReferenceRandom)
                    field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            assertNotNull("source private presentation stream", privateRandom);
            double canonicalNext = game.createPresentationRandomCopy().nextDouble();
            Method step = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
            step.setAccessible(true);
            for (int tick = 0; tick < hosts.size(); tick++) {
                if (tick > 0) step.invoke(app, 1f / 24f);
                Row source = hosts.get(tick);
                String context = scenario + " source host tick " + tick;
                assertEquals(context, tick, source.integer("tick"));
                assertEquals(context + " private RNG", source.real("rngNext"),
                        privateRandom.copy().nextDouble(), 0d);
                assertEquals(context + " canonical RNG stays frozen", canonicalNext,
                        game.createPresentationRandomCopy().nextDouble(), 0d);
                assertEquals(context + " wrapper active", source.bool("active"),
                        !core.isRemoved());
                assertEquals(context + " action", source.text("action"), core.getAction());
                assertEquals(context + " status", source.text("status"), core.getUnitStatus());
                number(context + " curX", source.text("curX"), core.getX());
                number(context + " curY", source.text("curY"), core.getY());
                number(context + " storedAngle", source.text("storedAngle"), core.getAngle());
                if (intercepted) {
                    assertTrue(context, source.bool("turretExists"));
                    assertEquals(context + " physical nested 5553", source.integer("turretFrame"),
                            building.getNestedFrameIndex(5553, 50) + 1);
                    assertEquals(context + " source alpha", source.real("turretAlpha"),
                            group.getPlayback().getTurretAlphaPercent(), 0d);
                    assertEquals(context + " source playing", source.bool("turretPlaying"),
                            group.getPlayback().isTurretPlaying());
                    assertEquals(context + " source stopped", source.bool("turretStopped"),
                            !group.getPlayback().isTurretPlaying());
                    assertEquals(context + " Unit countdown", source.integer("antiCountdown"),
                            visit.state.getCountdown());
                    assertEquals(context + " Unit offset", source.integer("antiOffset"),
                            visit.state.getOffset());
                    double[] anti = pair(source.text("antiPos"));
                    assertEquals(context + " Unit antiX", anti[0], visit.getAntiX(), 0d);
                    assertEquals(context + " Unit antiY", anti[1], visit.getAntiY(), 0d);
                    if ("self".equals(source.text("lock"))) {
                        assertSame(context + " defender lock", plane, group.getLockedTarget());
                        assertEquals(context, "unit1", source.text("lockOwnerName"));
                    } else {
                        assertEquals(context, "none", source.text("lock"));
                        assertNull(context + " defender lock", group.getLockedTarget());
                    }
                } else {
                    assertFalse(context, source.bool("turretExists"));
                    assertFalse(context, source.bool("antiActive"));
                    assertEquals(context, "none", source.text("lock"));
                    assertTrue(context + " no physical source defense", groups.isEmpty());
                }
            }
            assertFalse("source battle has handed off", cities.isBattleActive());
            assertTrue("private defense groups clear at handoff", groups.isEmpty());
        });
    }

    private static GameState ready(String scenario, JsonValue provenance) throws Exception {
        if (scenario.equals("p1_success"))
            return SourceBiologicalCarriersInstrumentedTest.gameReadyForPlane(true);
        if (scenario.equals("p1_intercept")) {
            Method ready = SourceInterceptedAircraftInstrumentedTest.class
                    .getDeclaredMethod("ready");
            ready.setAccessible(true);
            return (GameState) ready.invoke(null);
        }
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        JsonValue schedule = provenance.get("schedule");
        for (JsonValue turn : schedule) {
            while (game.getPendingHumanDiplomacyProposal() != null)
                assertTrue(game.respondToDiplomacyProposal(false));
            JsonValue options = turn.get("options");
            switch (turn.getString("action")) {
                case "research":
                    game.selectAction(GameState.Action.RESEARCH);
                    assertTrue(game.selectResearchTechnology(options.getString("tech")));
                    break;
                case "production":
                    game.selectAction(GameState.Action.PRODUCTION);
                    assertTrue(game.configureProduction(options.getString("category"),
                            options.getInt("percent")));
                    break;
                case "preAttack":
                    game.selectAction(GameState.Action.PREPARED_ATTACK);
                    if (options.has("target"))
                        assertTrue(game.configurePreparedLaunch(options.getInt("target"),
                                options.getInt("city"), options.getString("payload")));
                    else assertTrue(game.configurePreparedCarrier(options.getString("weapon")));
                    break;
                default: fail("Unrecognized legal source action " + turn.getString("action"));
            }
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            if (turn.getInt("turn") < schedule.size) game.confirmTurn();
        }
        assertEquals(schedule.size - 1, game.getTurn());
        return game;
    }

    private static JsonValue provenance(String scenario) throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open(PREFIX + scenario + ".provenance.json")) {
            return new JsonReader().parse(input);
        }
    }

    private static List<Row> hostRows(String scenario, int expectedCallbacks) throws Exception {
        List<Row> hosts = new ArrayList<>();
        int callbacks = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open(PREFIX + scenario + ".tsv"), StandardCharsets.UTF_8))) {
            String[] names = reader.readLine().split("\t", -1);
            assertEquals("39 source columns", 39, names.length);
            Map<String, Integer> columns = new HashMap<>();
            for (int index = 0; index < names.length; index++)
                assertNull("duplicate source column " + names[index],
                        columns.put(names[index], index));
            for (String line; (line = reader.readLine()) != null;) {
                Row row = new Row(columns, line);
                if ("C".equals(row.text("kind"))) {
                    if ("before".equals(row.text("phase"))) callbacks++;
                } else {
                    assertEquals("H", row.text("kind"));
                    assertEquals("afterHost", row.text("phase"));
                    hosts.add(row);
                }
            }
        }
        assertEquals("natural View.updateAnti callbacks", expectedCallbacks, callbacks);
        return hosts;
    }

    private static final class Row {
        final Map<String, Integer> columns;
        final String[] values;

        Row(Map<String, Integer> columns, String line) {
            this.columns = columns;
            values = line.split("\t", -1);
            assertEquals("source row width", columns.size(), values.length);
        }
        String text(String name) {
            Integer index = columns.get(name);
            assertNotNull("source column " + name, index);
            return values[index];
        }
        int integer(String name) { return Integer.parseInt(text(name)); }
        double real(String name) { return Double.parseDouble(text(name)); }
        boolean bool(String name) {
            String value = text(name);
            assertTrue(name + "=" + value, "True".equals(value) || "False".equals(value));
            return "True".equals(value);
        }
    }

    private static double[] pair(String json) {
        JsonValue values = new JsonReader().parse(json);
        return new double[]{values.get(0).asDouble(), values.get(1).asDouble()};
    }

    private static void number(String context, String expected, double actual) {
        if (expected.isEmpty()) assertTrue(context + " source undefined", Double.isNaN(actual));
        else assertEquals(context, Double.parseDouble(expected), actual, 1e-9d);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame game, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(game);
    }
}
