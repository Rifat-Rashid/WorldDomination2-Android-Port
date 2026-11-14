package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Four legal source carrier outcomes, including unsuccessful biological payloads. */
@RunWith(AndroidJUnit4.class)
public final class SourceBiologicalCarriersInstrumentedTest {
    @Test public void planeOneBiologicalPayloadMatchesAllSourceTicks() throws Exception {
        verify("source-bio-plane1", 533, 1, new int[]{2}, true, false);
    }

    @Test public void planeOneWarheadDoesNotCreateBiologicalClouds() throws Exception {
        verify("source-bio-plane1-warhead1", 533, 1, new int[]{2}, false, false);
    }

    @Test public void threeShipBiologicalStrikesReachPlayerTwoCities() throws Exception {
        verify("source-bio-ship-success", 565, 2, new int[]{0, 1, 2}, true, true);
    }

    @Test public void interceptedShipBiologicalStrikesNeverAttachClouds() throws Exception {
        verify("source-bio-ship-intercept", 482, 1, new int[]{0, 1, 2}, false, true);
    }

    private static void verify(String prefix, int lastTick, int target,
                               int[] cityIndices, boolean success, boolean ship)
            throws Exception {
        Row[] ticks = onePerTick(prefix + "-ticks.csv", lastTick);
        Row[][] units = unitRows(prefix + "-units.csv", lastTick, ship ? 3 : 1,
                ship ? "ship_plane" : "plane001");
        int[] lastUnitTicks = lastUnitTicks(units);
        List<Row>[] cities = ship ? rowsByTick(prefix + "-cities.csv", lastTick) : null;
        List<Row>[] clouds = success ? rowsByTick(prefix + "-clouds.csv", lastTick) : null;
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalAudio audio = (OriginalAudio)
                    field(WorldDominationGame.class, "audio").get(app);
            boolean restoreMuted = !audio.isSoundEnabled();
            if (restoreMuted) audio.toggleSound();
            try {
            GameState game = ship ? gameReadyForShip(target) : gameReadyForPlane(success);
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cityRenderer = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cityRenderer.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("source launch turn", ship ? 21 : 3, game.getTurn());
            assertEquals("source biological result", success,
                    hasSuccessfulBiologicalEvent(game));
            ReferenceRandom privateRandom = (ReferenceRandom)
                    field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            assertNotNull("isolated source RNG", privateRandom);
            SourceAircraftPlayback standalone = null;
            SourceCarrierPlanePlayback[] carriers = null;
            if (ship) {
                @SuppressWarnings("unchecked")
                List<FlashCarrierPlaneAnimation> live = (List<FlashCarrierPlaneAnimation>)
                        field(WorldDominationGame.class, "carrierPlaneAnimations").get(app);
                assertEquals(prefix + " source ship aircraft", 3, live.size());
                carriers = new SourceCarrierPlanePlayback[3];
                for (FlashCarrierPlaneAnimation animation : live) {
                    SourceCarrierPlanePlayback core = animation.getSourcePlayback();
                    assertNotNull(prefix + " actual source core", core);
                    int physical = core.getRoute().planeIndex;
                    assertTrue(prefix + " source aircraft ordinal", physical >= 0 && physical < 3);
                    assertNull(prefix + " unique aircraft ordinal", carriers[physical]);
                    assertEquals(prefix + " source payload city for unit" + (physical + 1),
                            cityIndices[physical], core.getRoute().targetCityIndex);
                    carriers[physical] = core;
                }
                for (SourceCarrierPlanePlayback core : carriers) assertNotNull(core);
            } else {
                @SuppressWarnings("unchecked")
                List<FlashStandalonePlaneAnimation> live = (List<FlashStandalonePlaneAnimation>)
                        field(WorldDominationGame.class, "standalonePlaneAnimations").get(app);
                assertEquals(prefix + " source airfield aircraft", 1, live.size());
                standalone = live.get(0).getSourcePlayback();
                assertNotNull(prefix + " actual source core", standalone);
                assertEquals(prefix + " source payload city", cityIndices[0],
                        live.get(0).getRoute().targetCityIndex);
            }
            for (int tick = 0; tick <= lastTick; tick++) {
                if (tick != 0) advance(app);
                if (ship && !success && tick == 195) {
                    TurnPlaybackSequence sequence = (TurnPlaybackSequence)
                            field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
                    SourceBattleRecordClock clock = (SourceBattleRecordClock)
                            field(WorldDominationGame.class, "battleRecordClock").get(app);
                    StringBuilder gate = new StringBuilder("source first ship visit; native phase=")
                            .append(sequence.getPhase()).append(" clock=")
                            .append(clock.getElapsedTicks()).append(" actions=");
                    for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings())
                        gate.append(event.playerId).append(':').append(event.action).append(' ');
                    assertEquals(gate.toString(), 3, clock.getActivatedRecordCount());
                }
                Row source = ticks[tick];
                assertNotNull(prefix + " tick " + tick, source);
                for (int index = 0; index < units.length; index++)
                    assertUnit(prefix, tick, index, units[index][tick],
                            lastUnitTicks[index], standalone, carriers);
                assertEquals(prefix + " canonical RNG t" + tick,
                        source.number("canonicalRngNext"),
                        game.createPresentationRandomCopy().nextDouble(), 0d);
                assertEquals(prefix + " isolated RNG t" + tick,
                        source.number("isolatedRngNext"),
                        privateRandom.copy().nextDouble(), 0d);
                if (ship) {
                    assertArray(source, "canonicalBio", cityIndices, target, game,
                            tick, true);
                    assertArray(source, "canonicalBattleFlag", cityIndices, target,
                            game, tick, false);
                } else {
                    CityState canonical = game.getPlayerState(target).getCity(cityIndices[0]);
                    assertEquals(prefix + " canonical bio t" + tick,
                            source.bool("canonicalCityBioloaded"), canonical.bioloaded);
                    if (source.has("canonicalBattleFlag"))
                        assertEquals(prefix + " canonical battle flag t" + tick,
                                source.bool("canonicalBattleFlag"), canonical.bioloadInBattle);
                }
                if (tick == lastTick) {
                    assertFalse(prefix + " isolated scene ended", cityRenderer.isBattleActive());
                    continue; // Source adapter retains its detached model for its last row.
                }
                assertTrue(prefix + " isolated scene t" + tick,
                        cityRenderer.isBattleActive());
                if (ship) {
                    assertShipCities(prefix, tick, cities[tick], clouds == null ? null : clouds[tick],
                            cityIndices, target, cityRenderer);
                    assertJsonFlags(prefix, tick, source, cityIndices, "cityBio",
                            target, cityRenderer, true);
                    assertJsonCounts(prefix, tick, source, cityIndices, target, cityRenderer);
                } else {
                    int address = target * 5 + cityIndices[0];
                    assertEquals(prefix + " isolated city bio t" + tick,
                            source.bool("cityBioloaded"),
                            cityRenderer.isSourceCityBioloaded(address));
                    assertEquals(prefix + " city frame t" + tick,
                            source.integer("cityFrame"), cityRenderer.getSourceCityFrame(address));
                    assertEquals(prefix + " city alpha t" + tick,
                            source.number("cityAlpha"),
                            cityRenderer.getSourceCityAlpha(address), .001d);
                    assertEquals(prefix + " cloud count t" + tick,
                            source.integer("clouds"), cityRenderer.getSourceCloudCount(address));
                    if (source.integer("clouds") > 0)
                        assertCloudRows(prefix, tick, clouds[tick], address, cityRenderer);
                    else assertFalse(prefix + " no attached cloud t" + tick,
                            cityRenderer.isSourceCloudAttached(address));
                }
                if (!success)
                    for (int city : cityIndices)
                        assertEquals(prefix + " no biological cloud t" + tick,
                                0, cityRenderer.getSourceCloudCount(target * 5 + city));
            }
            } finally {
                if (restoreMuted) audio.toggleSound();
            }
        });
    }

    private static void assertUnit(String prefix, int tick, int ordinal, Row source,
                                   int lastObserved, SourceAircraftPlayback standalone,
                                   SourceCarrierPlanePlayback[] carriers) {
        String label = prefix + " unit" + (ordinal + 1) + " t" + tick;
        if (source == null && tick <= lastObserved)
            fail(label + " missing source row before source removal");
        if (carriers == null) {
            assertNotNull(label + " standalone core", standalone);
            if (source == null) {
                assertTrue(label + " removed", standalone.isRemoved());
                return;
            }
            assertFalse(label + " still active", standalone.isRemoved());
            assertEquals(label + " action", source.text("action"), standalone.getAction());
            assertSourceNumber(label + " logical X", source, "curX", standalone.getX());
            assertSourceNumber(label + " logical Y", source, "curY", standalone.getY());
            assertSourceNumber(label + " painted X", source, "mcX",
                    standalone.getRenderX());
            assertSourceNumber(label + " painted Y", source, "mcY",
                    standalone.getRenderY());
            assertEquals(label + " frame", source.integer("mcFrame"),
                    standalone.getSourceFrame());
            assertEquals(label + " raw alpha", source.number("mcAlpha"),
                    standalone.getAlpha(), .001d);
        } else {
            SourceCarrierPlanePlayback core = carriers[ordinal];
            assertNotNull(label + " carrier core", core);
            if (source == null) {
                assertTrue(label + " removed", core.isRemoved());
                return;
            }
            assertFalse(label + " still active", core.isRemoved());
            assertEquals(label + " action", source.text("action"), core.getAction());
            assertSourceNumber(label + " logical X", source, "curX", core.getX());
            assertSourceNumber(label + " logical Y", source, "curY", core.getY());
            assertSourceNumber(label + " painted X", source, "mcX", core.getRenderX());
            assertSourceNumber(label + " painted Y", source, "mcY", core.getRenderY());
            assertEquals(label + " frame", source.integer("mcFrame"), core.getSourceFrame());
            assertEquals(label + " raw alpha", source.number("mcAlpha"),
                    core.getAlpha(), .001d);
        }
    }

    private static void assertSourceNumber(String label, Row source, String key,
                                           double actual) {
        String text = source.text(key);
        if ("nan".equalsIgnoreCase(text)) assertTrue(label + " NaN", Double.isNaN(actual));
        else assertEquals(label, Double.parseDouble(text), actual, .02d);
    }

    private static Row[][] unitRows(String file, int lastTick, int count,
                                    String linkage) throws Exception {
        Row[][] rows = new Row[count][lastTick + 1];
        for (Row row : read(file)) {
            String name = row.text("unitName");
            if (name == null || !name.startsWith("unit")
                    || !linkage.equals(row.text("mcID"))) continue;
            int ordinal = Integer.parseInt(name.substring(4)) - 1;
            assertTrue(file + " unit ordinal", ordinal >= 0 && ordinal < count);
            assertEquals(file + " source linkage", linkage, row.text("mcID"));
            int tick = row.integer("tick");
            assertTrue(file + " tick", tick >= 0 && tick <= lastTick);
            assertNull(file + " unique unit" + name + " tick" + tick, rows[ordinal][tick]);
            rows[ordinal][tick] = row;
        }
        for (int ordinal = 0; ordinal < count; ordinal++)
            assertNotNull(file + " initial source unit" + ordinal, rows[ordinal][0]);
        return rows;
    }

    private static int[] lastUnitTicks(Row[][] rows) {
        int[] last = new int[rows.length];
        for (int ordinal = 0; ordinal < rows.length; ordinal++) {
            for (int tick = 0; tick < rows[ordinal].length; tick++)
                if (rows[ordinal][tick] != null) last[ordinal] = tick;
        }
        return last;
    }

    private static boolean hasSuccessfulBiologicalEvent(GameState game) {
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents())
            if (event.attackerPlayerId == 0 && event.bioload
                    && event.type == GameState.CombatEventType.STRIKE_IMPACT) return true;
        return false;
    }

    /** Queue the source's first two legal plane turns, then the third-turn strike. */
    static GameState gameReadyForPlane(boolean biological) {
        GameState game = newGame();
        rejectDiplomacy(game); produce(game, "planes"); resources(game); game.confirmTurn();
        rejectDiplomacy(game); prepare(game, "plane1"); resources(game); game.confirmTurn();
        rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 2,
                biological ? "bioload1" : "warhead1"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    /** Source turn 21: 12 level-2..4 researches, sub/ship, production, prepare, strike. */
    static GameState gameReadyForShip(int target) {
        GameState game = newGame();
        for (String group : new String[]{"rocket", "warhead", "bioload", "soldier"})
            for (int level = 2; level <= 4; level++) {
                rejectDiplomacy(game);
                research(game, group + level); resources(game); game.confirmTurn();
            }
        for (String technology : new String[]{"sub", "ship"}) {
            rejectDiplomacy(game);
            research(game, technology); resources(game); game.confirmTurn();
        }
        rejectDiplomacy(game); produce(game, "special"); resources(game); game.confirmTurn();
        for (int turn = 0; turn < 4; turn++) {
            rejectDiplomacy(game);
            produce(game, "bioload"); resources(game); game.confirmTurn();
        }
        rejectDiplomacy(game); prepare(game, "ship"); resources(game); game.confirmTurn();
        assertEquals(20, game.getTurn());
        rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        for (int city = 0; city < 3; city++)
            assertTrue("source ship city " + city,
                    game.configurePreparedShipStrike(target, city, "bioload3"));
        assertEquals(3, game.getSelectedShipStrikeCount());
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    private static GameState newGame() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }

    private static void rejectDiplomacy(GameState game) {
        while (game.getPendingHumanDiplomacyProposal() != null)
            assertTrue(game.respondToDiplomacyProposal(false));
    }

    private static void research(GameState game, String technology) {
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology(technology));
        assertTrue(game.acceptSelectedAction());
    }

    private static void produce(GameState game, String category) {
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction(category, 100));
        assertTrue(game.acceptSelectedAction());
    }

    private static void prepare(GameState game, String carrier) {
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier(carrier));
        assertTrue(game.acceptSelectedAction());
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void assertArray(Row source, String key, int[] cities, int target,
                                    GameState game, int tick, boolean bio) {
        JsonValue values = new JsonReader().parse(source.text(key));
        assertEquals(key + " length t" + tick, cities.length, values.size);
        for (int index = 0; index < cities.length; index++) {
            CityState canonical = game.getPlayerState(target).getCity(cities[index]);
            assertEquals(key + " city " + cities[index] + " t" + tick,
                    values.get(index).asBoolean(),
                    bio ? canonical.bioloaded : canonical.bioloadInBattle);
        }
    }

    private static void assertJsonFlags(String prefix, int tick, Row source,
                                        int[] cities, String key, int target,
                                        BattlefieldCityRenderer renderer, boolean bio) {
        JsonValue values = new JsonReader().parse(source.text(key));
        assertEquals(prefix + " " + key + " t" + tick, cities.length, values.size);
        for (int index = 0; index < cities.length; index++) {
            int address = target * 5 + cities[index];
            assertEquals(prefix + " " + key + " city " + address + " t" + tick,
                    values.get(index).asBoolean(),
                    bio ? renderer.isSourceCityBioloaded(address)
                            : renderer.isSourceCloudAttached(address));
        }
    }

    private static void assertJsonCounts(String prefix, int tick, Row source,
                                         int[] cities, int target,
                                         BattlefieldCityRenderer renderer) {
        JsonValue values = new JsonReader().parse(source.text("cloudCounts"));
        assertEquals(prefix + " counts t" + tick, cities.length, values.size);
        for (int index = 0; index < cities.length; index++) {
            int address = target * 5 + cities[index];
            assertEquals(prefix + " clouds city " + address + " t" + tick,
                    values.get(index).asInt(), renderer.getSourceCloudCount(address));
        }
    }

    private static void assertShipCities(String prefix, int tick, List<Row> rows,
                                         List<Row> cloudRows, int[] cityIndices,
                                         int target, BattlefieldCityRenderer renderer) {
        assertEquals(prefix + " three cities t" + tick, 3, rows.size());
        for (Row row : rows) {
            int address = row.integer("cityId");
            assertEquals(prefix + " owner t" + tick, target, address / 5);
            assertTrue(prefix + " selected city t" + tick,
                    contains(cityIndices, address % 5));
            assertEquals(prefix + " bio city " + address + " t" + tick,
                    row.bool("bioloaded"), renderer.isSourceCityBioloaded(address));
            assertEquals(prefix + " city frame " + address + " t" + tick,
                    row.integer("imageFrame"), renderer.getSourceCityFrame(address));
            assertEquals(prefix + " city alpha " + address + " t" + tick,
                    row.number("imageAlpha"), renderer.getSourceCityAlpha(address), .001d);
            assertEquals(prefix + " retained cloud children " + address + " t" + tick,
                    row.integer("cloudCount"), renderer.getSourceCloudCount(address));
            assertEquals(prefix + " cloud attachment " + address + " t" + tick,
                    row.bool("containerRootAttached"),
                    renderer.isSourceCloudAttached(address));
            if (row.integer("cloudCount") > 0) {
                assertEquals(prefix + " cloud depth " + address + " t" + tick,
                        row.integer("containerDepth"), renderer.getSourceCloudDepth(address));
            }
        }
        if (cloudRows != null) {
            Map<Integer, List<Row>> byCity = new HashMap<>();
            for (Row row : cloudRows)
                byCity.computeIfAbsent(row.integer("cityId"), key -> new ArrayList<>()).add(row);
            for (int city : cityIndices) {
                int address = target * 5 + city;
                List<Row> expected = byCity.get(address);
                if (expected != null) assertCloudRows(prefix, tick, expected, address, renderer);
            }
        }
    }

    private static boolean contains(int[] values, int value) {
        for (int current : values) if (current == value) return true;
        return false;
    }

    private static void assertCloudRows(String prefix, int tick, List<Row> rows,
                                        int address, BattlefieldCityRenderer renderer) {
        assertEquals(prefix + " child rows " + address + " t" + tick, 17, rows.size());
        BioloadCloudAnimation field = renderer.getSourceCloudCohort(address, 0);
        for (Row row : rows) {
            int index = row.integer("index");
            String label = prefix + " city " + address + " cloud " + index + " t" + tick;
            assertEquals(label + " child depth", index, row.integer("depth"));
            assertEquals(label + " parent attachment", row.bool("rootAttached"),
                    renderer.isSourceCloudAttached(address));
            assertEquals(label + " x", row.number("x"), field.getSourceX(index), 1e-9);
            assertEquals(label + " y", row.number("y"), field.getSourceY(index), 1e-9);
            assertEquals(label + " alpha", row.number("alpha"),
                    field.getSourceAlpha(index), 1e-9);
            assertEquals(label + " scale", row.number("xscale"),
                    field.getSourceScalePercent(index), 1e-8);
        }
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
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Row[] onePerTick(String file, int lastTick) throws Exception {
        Row[] rows = new Row[lastTick + 1];
        for (Row row : read(file)) rows[row.integer("tick")] = row;
        for (int tick = 0; tick <= lastTick; tick++)
            assertNotNull(file + " tick " + tick, rows[tick]);
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Row>[] rowsByTick(String file, int lastTick) throws Exception {
        List<Row>[] rows = new List[lastTick + 1];
        for (int tick = 0; tick <= lastTick; tick++) rows[tick] = new ArrayList<>();
        for (Row row : read(file)) rows[row.integer("tick")].add(row);
        return rows;
    }

    private static List<Row> read(String file) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(file),
                StandardCharsets.UTF_8))) {
            List<String> headers = parseCsv(reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> cells = parseCsv(line);
                assertEquals(file + " columns", headers.size(), cells.size());
                Map<String, String> values = new HashMap<>();
                for (int index = 0; index < headers.size(); index++)
                    values.put(headers.get(index), cells.get(index));
                rows.add(new Row(values));
            }
        }
        return rows;
    }

    /** RFC 4180 fields, including JSON arrays quoted inside the CSV. */
    private static List<String> parseCsv(String line) {
        assertNotNull(line);
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"'); index++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                values.add(current.toString()); current.setLength(0);
            } else current.append(ch);
        }
        assertFalse("unclosed CSV quote", quoted);
        values.add(current.toString());
        return values;
    }

    private static final class Row {
        private final Map<String, String> values;
        Row(Map<String, String> values) { this.values = values; }
        String text(String key) { return values.get(key); }
        boolean has(String key) { return text(key) != null && !text(key).isEmpty(); }
        boolean bool(String key) { return "True".equals(text(key)); }
        int integer(String key) { return (int) Double.parseDouble(text(key)); }
        double number(String key) { return Double.parseDouble(text(key)); }
    }
}
