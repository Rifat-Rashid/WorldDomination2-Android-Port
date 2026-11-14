package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Legal source turn 17, including source private RNG and biological callback timing. */
@RunWith(AndroidJUnit4.class)
public final class SourceBiologicalPlaybackInstrumentedTest {
    private static final int CITY = 7;
    private static final int LAST_TICK = 641;

    @Test public void legalSubmarineBattleMatchesSourceThroughCanonicalHandoff()
            throws Exception {
        Row[] ticks = onePerTick("source-bio-sub-ticks.csv", LAST_TICK);
        Row[] units = namedUnit("source-bio-sub-units.csv", LAST_TICK, "unit1");
        List<Row>[] clouds = rowsByTick("source-bio-sub-clouds.csv", LAST_TICK);
        List<Row>[] smoke = rowsByTick("source-bio-sub-smoke.csv", LAST_TICK);
        List<Row>[] roles = rowsByTick("source-bio-sub-root-roles.csv", LAST_TICK);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> withSourceSound(app, ignored -> {
            GameState game = gameReadyForSubmarine();
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("legal source turn", 17, game.getTurn());
            assertEquals("resolved source casualties", 43,
                    game.getLastTurnCombatEvents().get(0).casualtiesMillions);
            assertTrue(game.getLastTurnCombatEvents().get(0).bioload);

            @SuppressWarnings("unchecked")
            List<SourceRocketDisplayDepths> bindings = (List<SourceRocketDisplayDepths>)
                    field(WorldDominationGame.class, "sourceRocketBindings").get(app);
            assertEquals("one source submarine projectile", 1, bindings.size());
            SourceRocketDisplayDepths.Binding binding = bindings.get(0).binding();
            SourceRocketPlayback core = binding.animation.getSourcePlayback();
            assertNotNull(core);
            assertEquals("source direct clip parameter", 5, binding.sourceParameter);
            ReferenceRandom privateRandom = (ReferenceRandom)
                    field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            assertNotNull(privateRandom);
            TurnPlaybackSequence playback = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            assertTick(0, ticks[0], units[0], clouds[0], smoke[0], roles[0],
                    game, cities, binding, core, privateRandom, playback);
            for (int tick = 1; tick <= LAST_TICK; tick++) {
                advance(app);
                assertTick(tick, ticks[tick], units[tick], clouds[tick], smoke[tick],
                        roles[tick], game, cities, binding, core, privateRandom, playback);
            }
        }));
    }

    static void withSourceSound(WorldDominationGame app,
                                SourceBattleCleanupInstrumentedTest.GameCheck check) throws Exception {
        OriginalAudio audio = (OriginalAudio) field(WorldDominationGame.class, "audio").get(app);
        boolean restoreMuted = !audio.isSoundEnabled();
        if (restoreMuted) audio.toggleSound();
        try {
            check.run(app);
        } finally {
            if (restoreMuted) audio.toggleSound();
        }
    }

    private static void assertTick(int tick, Row source, Row unit,
                                   List<Row> cloudRows, List<Row> smokeRows,
                                   List<Row> roleRows, GameState game,
                                   BattlefieldCityRenderer cities,
                                   SourceRocketDisplayDepths.Binding binding,
                                   SourceRocketPlayback core,
                                   ReferenceRandom privateRandom,
                                   TurnPlaybackSequence playback) {
        assertNotNull("source tick " + tick, source);
        CityState canonical = game.getPlayerState(1).getCity(2);
        assertEquals("canonical city cloud flag at " + tick,
                source.bool("canonicalBioloaded"), canonical.bioloaded);
        assertEquals("canonical retained battle flag at " + tick,
                source.bool("canonicalBattleFlag"), canonical.bioloadInBattle);
        assertEquals("canonical city never owns cloud children at " + tick,
                source.integer("canonicalCloudCount"), canonicalCloudCount(cities));
        assertEquals("canonical city frame stays resolved at " + tick,
                source.integer("canonicalModelImageFrame"),
                canonicalCity(cities).getFrameNumber());
        assertEquals("canonical city has no shrink callback at " + tick,
                100, canonicalCity(cities).getSourceAlpha());
        assertEquals("canonical presentation RNG at " + tick,
                source.number("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        if (source.has("canonicalTargetPlayerBioloaded")) {
            assertEquals("temporary player hazard at " + tick,
                    source.bool("canonicalTargetPlayerBioloaded"),
                    game.getPlayerState(1).isBioloaded());
        }

        if (tick < 640) {
            assertTrue("isolated battle active at " + tick, cities.isBattleActive());
            assertEquals("isolated city artwork at " + tick,
                    source.integer("isolatedImageFrame"), cities.getSourceCityFrame(CITY));
            assertEquals("isolated city raw alpha at " + tick,
                    source.number("isolatedImageAlpha"),
                    cities.getSourceCityAlpha(CITY), 0.001d);
            assertEquals("isolated city cloud model flag at " + tick,
                    source.bool("isolatedBioloaded"),
                    cities.isSourceCityBioloaded(CITY));
            assertEquals("retained cloud children at " + tick,
                    source.integer("isolatedCloudCount"),
                    cities.getSourceCloudCount(CITY));
        } else {
            assertFalse("battle city bundle discarded at " + tick,
                    cities.isBattleActive());
            assertEquals("canonical image restored at " + tick,
                    source.integer("canonicalModelImageFrame"),
                    cities.getSourceCityFrame(CITY));
            assertEquals("canonical city has no source cloud at " + tick,
                    0, cities.getSourceCloudCount(CITY));
            assertFalse(cities.isSourceCloudAttached(CITY));
        }
        if (source.has("isolatedRngNext")) {
            assertEquals("held private battle RNG at " + tick,
                    source.number("isolatedRngNext"),
                    privateRandom.copy().nextDouble(), 0d);
        }

        if (unit != null) {
            String label = "unit1 at " + tick;
            assertFalse("active source wrapper " + label, core.isRemoved());
            assertEquals("action " + label, unit.text("action"), core.getAction());
            assertEquals("logical X " + label, unit.number("curX"), core.getX(), 0.02d);
            assertEquals("logical Y " + label, unit.number("curY"), core.getY(), 0.02d);
            assertEquals("render X " + label, unit.number("mcX"), core.getRenderX(), 0.02d);
            assertEquals("render Y " + label, unit.number("mcY"), core.getRenderY(), 0.02d);
            assertEquals("frame " + label, unit.integer("mcFrame"), core.getSourceFrame());
            assertEquals("body membership " + label,
                    unit.bool("mcRootAttached"), core.isBodyAttached());
            assertEquals("horizontal step " + label,
                    unit.number("xStep"), core.getXStep(), 0.02d);
            assertEquals("vertical step " + label,
                    unit.number("yStep"), core.getYStep(), 0.02d);
            assertEquals("ellipse angle " + label,
                    unit.number("ellipseAngle"), core.getEllipseAngle(), 0.02d);
            assertEquals("trail children " + label,
                    unit.integer("attachedTrailChildren"), core.getAttachedTrailCount());
        } else if (tick >= 578) {
            assertTrue("unit wrapper removed after dying callback at " + tick,
                    core.isRemoved());
        }
        for (Row row : smokeRows) assertSmoke(tick, row, binding, core);
        for (Row row : roleRows) assertRole(tick, row, binding);
        if (tick >= 577 && tick < 640) assertClouds(tick, cloudRows, cities);
        if (tick == 577) {
            assertTrue("source cloud born on impact", cities.isSourceCloudAttached(CITY));
            assertEquals(30, cities.getSourceCloudDepth(CITY));
        }
        if (tick == 585) {
            assertFalse("frame seek detaches cloud container", cities.isSourceCloudAttached(CITY));
            assertEquals("source retains orphan child references", 17,
                    cities.getSourceCloudCount(CITY));
        }
        if (tick == 640 || tick == 641) {
            assertEquals("source battle handoff", TurnPlaybackSequence.Phase.IDLE,
                    playback.getPhase());
            assertFalse("stopped explosion absent after parent handoff",
                    binding.explosion().isAttached());
        }
    }

    private static void assertClouds(int tick, List<Row> rows,
                                     BattlefieldCityRenderer cities) {
        assertEquals("17 source children at " + tick, 17, rows.size());
        BioloadCloudAnimation field = cities.getSourceCloudCohort(CITY, 0);
        for (Row row : rows) {
            int index = row.integer("index");
            String label = "tick " + tick + " cloud " + index;
            assertEquals(label + " child depth", index, row.integer("depth"));
            assertEquals(label + " parent attachment", row.bool("rootAttached"),
                    cities.isSourceCloudAttached(CITY));
            assertEquals(label + " x", row.number("x"), field.getSourceX(index), 1e-9);
            assertEquals(label + " y", row.number("y"), field.getSourceY(index), 1e-9);
            assertEquals(label + " alpha", row.number("alpha"),
                    field.getSourceAlpha(index), 1e-9);
            assertEquals(label + " x scale", row.number("xscale"),
                    field.getSourceScalePercent(index), 1e-8);
            assertEquals(label + " y scale", row.number("yscale"),
                    field.getSourceScalePercent(index), 1e-8);
            assertEquals(label + " alpha decay", row.integer("alphaDecay"),
                    field.getSourceAlphaDecay(index));
            assertEquals(label + " size decay", row.integer("sizeDecay"),
                    field.getSourceSizeDecay(index));
            assertEquals(label + " horizontal offset", row.integer("horizontalOffset"),
                    field.getSourceHorizontalTicks(index));
            assertEquals(label + " horizontal direction",
                    row.integer("horizontalMultiplier"),
                    field.getSourceHorizontalDirection(index));
        }
    }

    private static void assertSmoke(int tick, Row row,
                                    SourceRocketDisplayDepths.Binding binding,
                                    SourceRocketPlayback core) {
        String name = row.text("name");
        int slot = Integer.parseInt(name.substring(name.lastIndexOf("trail") + 5));
        String label = "tick " + tick + " smoke " + slot;
        assertTrue(label, core.isTrailCreated(slot));
        assertEquals(label + " x", row.number("x"), core.getTrailX(slot), 0.02d);
        assertEquals(label + " y", row.number("y"), core.getTrailY(slot), 0.02d);
        assertEquals(label + " alpha", row.number("alpha"),
                core.getTrailAlpha(slot), 0.02d);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = binding.smoke(slot);
        assertNotNull(label + " entry", entry);
        assertEquals(label + " depth", row.integer("depth"), entry.getDepth());
        assertEquals(label + " attachment", row.bool("rootAttached"),
                entry.isAttached());
    }

    private static void assertRole(int tick, Row row,
                                   SourceRocketDisplayDepths.Binding binding) {
        String role = row.text("role");
        if ("tint".equals(role)) return; // Nested in the source body, not a root entry.
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                "body".equals(role) ? binding.body()
                : "dot".equals(role) ? binding.dot()
                : "explosion".equals(role) ? binding.explosion() : null;
        assertNotNull("tick " + tick + " " + role + " source role", entry);
        if (tick == 640) return; // Source's isolated parent survives in held probe state.
        assertEquals("tick " + tick + " " + role + " depth",
                row.integer("depth"), entry.getDepth());
        assertEquals("tick " + tick + " " + role + " attachment",
                row.bool("rootAttached"), entry.isAttached());
        if ("explosion".equals(role) && row.bool("rootAttached")) {
            assertEquals("source effect autoplay at " + tick,
                    row.integer("frame") - 1, binding.getExplosionFrameIndex());
        }
    }

    /** Source-reachable setup through 16 legal turns; the final launch is queued. */
    static GameState gameReadyForSubmarine() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        for (String group : new String[]{"rocket", "warhead", "bioload", "soldier"}) {
            for (int level = 2; level <= 4; level++) {
                rejectDiplomacy(game);
                research(game, group + level); resources(game); game.confirmTurn();
            }
        }
        rejectDiplomacy(game);
        research(game, "sub"); resources(game); game.confirmTurn();
        rejectDiplomacy(game);
        produce(game, "special"); resources(game); game.confirmTurn();
        rejectDiplomacy(game);
        produce(game, "bioload"); resources(game); game.confirmTurn();
        rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("sub"));
        assertTrue(game.acceptSelectedAction());
        resources(game); game.confirmTurn();
        rejectDiplomacy(game);
        assertEquals(16, game.getTurn());
        assertEquals("sub", game.getHuman().getPreparedCarrierName());
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 2, "bioload3"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, game.getQueuedActionCount());
        return game;
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
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static CityShrinkSequence canonicalCity(BattlefieldCityRenderer cities) {
        try {
            Object state = field(BattlefieldCityRenderer.class, "canonicalState").get(cities);
            CityShrinkSequence[] sequences = (CityShrinkSequence[])
                    field(state.getClass(), "citySequences").get(state);
            return sequences[CITY];
        } catch (Exception error) {
            throw new AssertionError("canonical city bundle", error);
        }
    }

    private static int canonicalCloudCount(BattlefieldCityRenderer cities) {
        try {
            Object clouds = field(BattlefieldCityRenderer.class,
                    "bioloadCloudRenderer").get(cities);
            Object state = field(clouds.getClass(), "canonicalState").get(clouds);
            Object[] fields = (Object[]) field(state.getClass(), "cityClouds").get(state);
            return fields[CITY] == null ? 0 : BioloadCloudAnimation.CLOUD_COUNT;
        } catch (Exception error) {
            throw new AssertionError("canonical cloud bundle", error);
        }
    }

    private static Row[] onePerTick(String file, int lastTick) throws Exception {
        Row[] rows = new Row[lastTick + 1];
        for (Row row : read(file)) rows[row.integer("tick")] = row;
        for (int tick = 0; tick <= lastTick; tick++) assertNotNull(file + " tick " + tick, rows[tick]);
        return rows;
    }

    private static Row[] namedUnit(String file, int lastTick, String unitName)
            throws Exception {
        Row[] rows = new Row[lastTick + 1];
        for (Row row : read(file)) {
            if (unitName.equals(row.text("unitName"))) rows[row.integer("tick")] = row;
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Row>[] rowsByTick(String file, int lastTick) throws Exception {
        List<Row>[] result = new List[lastTick + 1];
        for (int tick = 0; tick <= lastTick; tick++) result[tick] = new ArrayList<>();
        for (Row row : read(file)) result[row.integer("tick")].add(row);
        return result;
    }

    private static List<Row> read(String file) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(file),
                StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            assertNotNull(file + " header", headerLine);
            List<String> headers = parseCsv(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = parseCsv(line);
                assertEquals(file + " CSV columns", headers.size(), values.size());
                Map<String, String> cells = new HashMap<>();
                for (int index = 0; index < headers.size(); index++)
                    cells.put(headers.get(index), values.get(index));
                rows.add(new Row(cells));
            }
        }
        return rows;
    }

    /** RFC 4180 field quoting, including escaped quotes in fixture JSON columns. */
    private static List<String> parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"'); index++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                fields.add(field.toString()); field.setLength(0);
            } else field.append(ch);
        }
        fields.add(field.toString());
        assertFalse("unterminated quoted CSV field", quoted);
        return fields;
    }

    private static final class Row {
        private final Map<String, String> values;
        Row(Map<String, String> values) { this.values = values; }
        String text(String key) { return values.get(key); }
        boolean has(String key) { return values.containsKey(key) && !values.get(key).isEmpty(); }
        boolean bool(String key) { return "True".equals(text(key)); }
        int integer(String key) { return (int) Double.parseDouble(text(key)); }
        double number(String key) { return Double.parseDouble(text(key)); }
    }
}
