package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Legal, observer-safe source Unit/View callbacks and post-autoplay host frames. */
public final class SourceAirfieldDefensePlaybackTest {
    private static final String PREFIX = "source-airfield-defense-";
    private static final String SCRIPT_SHA =
            "54991ddf5b2587f1b29b9b179eb09d02a4006ab983900e837927d23f6bd03ded";
    private static final String SWF_SHA =
            "fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1";

    private static final class Scenario {
        final String name;
        final int turns, hostRows, callbackPairs, firstCallback, lastCallback, deathTick;
        final boolean intercepted;
        final String fixtureSha, provenanceSha;

        Scenario(String name, int turns, int hostRows, int callbackPairs,
                 int firstCallback, int lastCallback, int deathTick, boolean intercepted,
                 String fixtureSha, String provenanceSha) {
            this.name = name;
            this.turns = turns;
            this.hostRows = hostRows;
            this.callbackPairs = callbackPairs;
            this.firstCallback = firstCallback;
            this.lastCallback = lastCallback;
            this.deathTick = deathTick;
            this.intercepted = intercepted;
            this.fixtureSha = fixtureSha;
            this.provenanceSha = provenanceSha;
        }
    }

    private static final Scenario P1_SUCCESS = new Scenario("p1_success", 3, 534, 0,
            -1, -1, -1, false,
            "965122c1debfaf03198fa89fc9c89524cf8c407791057b104d1a2a2c700ccf80",
            "1e5c03318c503b5436fda3c80d2b7abfe9359b2f7030f20e535b0f96c9e0fc1b");
    private static final Scenario P1_INTERCEPT = new Scenario("p1_intercept", 22, 378, 119,
            196, 314, 314, true,
            "0f00a6473f196158ea78d440e0b06a18dfebbe22aad8c70ca6b26ca8c6bdf320",
            "078f53efc9c6b82fa9cc9b806e22de59811bc0adbbf36f4a9a72953ba144b27a");
    private static final Scenario P2_SUCCESS = new Scenario("p2_success", 17, 572, 0,
            -1, -1, -1, false,
            "af0f6afdc3e248d8be3d224efdaeb748c2f9243620f795ae36059c5ba913cb7d",
            "087c823cf5c835995fce03fafad13eb8fe8c0627ae2c4eec1791b764f0e23d64");
    private static final Scenario P2_INTERCEPT = new Scenario("p2_intercept", 25, 364, 105,
            195, 299, 299, true,
            "bdaef53144dcd86e28f2ac3167e1755ae5086f799e86f02e984c6add0237b361",
            "06cddb37021a7c47761a8b2bca3ffca954f26fc31d5d7061704fcbd152d73a76");

    @Test public void plane001SuccessfulHostHasNoAntiAirCallback() throws Exception {
        replay(P1_SUCCESS);
    }

    @Test public void plane001InterceptedCallbacksAndHostMatchSource() throws Exception {
        replay(P1_INTERCEPT);
    }

    @Test public void plane002SuccessfulHostHasNoAntiAirCallback() throws Exception {
        replay(P2_SUCCESS);
    }

    @Test public void plane002InterceptedCallbacksAndHostMatchSource() throws Exception {
        replay(P2_INTERCEPT);
    }

    @Test public void legalFixturesPinBytesAndSourceProvenance() throws Exception {
        for (Scenario scenario : new Scenario[]{P1_SUCCESS, P1_INTERCEPT,
                P2_SUCCESS, P2_INTERCEPT}) {
            String file = PREFIX + scenario.name;
            assertEquals(file, scenario.fixtureSha, sha(resource(file + ".tsv")));
            assertEquals(file, scenario.provenanceSha,
                    sha(resource(file + ".provenance.json")));
            JsonValue manifest = new JsonReader().parse(new String(
                    resource(file + ".provenance.json"), StandardCharsets.UTF_8));
            assertEquals(file, scenario.name, manifest.getString("scenario"));
            assertTrue(file, manifest.getBoolean("legalMatchParity"));
            assertEquals(file, 0, manifest.getInt("seed"));
            assertEquals(file, "normal", manifest.getString("difficulty"));
            assertEquals(file, scenario.turns, manifest.get("schedule").size);
            assertEquals(file, scenario.hostRows, manifest.getInt("hostRows"));
            assertEquals(file, scenario.callbackPairs, manifest.getInt("callbackPairs"));
            assertEquals(file, scenario.fixtureSha, manifest.getString("fixtureSha256"));
            assertEquals(file, SCRIPT_SHA, manifest.getString("scriptSha256"));
            assertEquals(file, !scenario.intercepted,
                    manifest.get("resolvedEvent").getBoolean("_result"));
            assertEquals(file, scenario.intercepted ? scenario.firstCallback : null,
                    manifest.get("firstCallbackTick").isNull() ? null
                            : manifest.getInt("firstCallbackTick"));
            if (scenario.name.equals("p2_success"))
                assertTrue(manifest.getString("aliasRepair").contains("tank_noShade"));
            else assertTrue(file, manifest.get("aliasRepair").isNull());
            JsonValue source = manifest.get("sourceSha256");
            assertEquals(SWF_SHA, source.getString("WorldDomination2.swf"));
            assertEquals("bc54c0e6c46f4e33bbb20e786c755f93aaba5d3d7224b0120eeeccc81eef8539",
                    source.getString("wd2/generated/Unit.py"));
            assertEquals("2de8afd493f28e36c3a94afa96b80795d685b8b023b12dc9d9ed634692c3accf",
                    source.getString("wd2/generated/View.py"));
            assertEquals("c6e9c790d6a624b0425344260b4de904db5197ea756fdf3a2de912eb8cd73c10",
                    source.getString("wd2/generated/Battle.py"));
            assertEquals("eb8aad7305c870b157a3199b7d8213edb22f4fd35d169395cee3db95b12c08b9",
                    source.getString("wd2/generated/Presenter.py"));
            assertEquals("5633326451e768a84a14bde04e452043ba7fac2617a4c71b7b36dcd0495f0c4a",
                    source.getString("wd2/setup_menu.py"));
            assertEquals("a489c5f3b33a1fdac4448afd07f4ca63aa32d676440dc573805ab11fb4002765",
                    source.getString("wd2/movieclip_host.py"));
        }
    }

    private static void replay(Scenario scenario) throws Exception {
        SourceAntiDefensePlayback playback = SourceAntiDefensePlayback.forReligion("chr");
        Map<String, SourceAntiDefensePlayback.UnitState> units = new HashMap<>();
        Set<String> killed = new HashSet<>();
        Row pendingBefore = null;
        int hostCount = 0, callbackCount = 0, firstCallback = -1, lastCallback = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream(PREFIX + scenario.name + ".tsv"), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull(header);
            String[] names = header.split("\t", -1);
            assertEquals("39 named source columns", 39, names.length);
            Map<String, Integer> indices = new HashMap<>();
            for (int i = 0; i < names.length; i++)
                assertTrue("duplicate source column " + names[i], indices.put(names[i], i) == null);
            for (String line; (line = reader.readLine()) != null;) {
                Row row = new Row(indices, line);
                int tick = row.integer("tick");
                String context = scenario.name + " source tick " + tick + " " + row.text("kind")
                        + " " + row.text("phase") + " #" + row.text("callIndex");
                if ("C".equals(row.text("kind"))) {
                    assertTrue(context, scenario.intercepted);
                    if ("before".equals(row.text("phase"))) {
                        assertTrue(context, pendingBefore == null);
                        assertEquals(context, callbackCount, row.integer("callIndex"));
                        assertEquals(context, callbackCount * 2, row.integer("captureOrder"));
                        pendingBefore = row;
                        if (firstCallback < 0) firstCallback = tick;
                        SourceAntiDefensePlayback.UnitState unit = units.get(row.text("unitName"));
                        if (unit == null) {
                            unit = new SourceAntiDefensePlayback.UnitState();
                            units.put(row.text("unitName"), unit);
                        }
                        if ("dead".equals(row.text("status")) && killed.add(row.text("unitName")))
                            unit.markKilled();
                        assertSourceState(context, row, playback, unit);
                    } else {
                        assertEquals(context, "after", row.text("phase"));
                        assertNotNull(context, pendingBefore);
                        assertEquals(context, pendingBefore.integer("tick"), tick);
                        assertEquals(context, pendingBefore.text("unitName"), row.text("unitName"));
                        assertEquals(context, pendingBefore.text("callIndex"), row.text("callIndex"));
                        assertEquals(context, callbackCount * 2 + 1, row.integer("captureOrder"));
                        assertEquals(context, pendingBefore.text("rngNext"), row.text("rngNext"));
                        SourceAntiDefensePlayback.UnitState unit = units.get(row.text("unitName"));
                        double[] anti = pair(pendingBefore.text("antiPos"));
                        playback.invoke(unit, pendingBefore.bool("antiActive"),
                                pendingBefore.real("curX"), pendingBefore.real("curY"),
                                anti[0], anti[1], "dead".equals(pendingBefore.text("status")));
                        assertSourceState(context, row, playback, unit);
                        if (row.bool("antiActive"))
                            assertEquals(context, row.integer("storedAngle"),
                                    unit.getRawBearingDegrees());
                        pendingBefore = null;
                        callbackCount++;
                        lastCallback = tick;
                    }
                } else {
                    assertEquals(context, "H", row.text("kind"));
                    assertEquals(context, "afterHost", row.text("phase"));
                    assertTrue(context, pendingBefore == null);
                    assertEquals(context, hostCount, tick);
                    if (tick > 0) playback.advanceHostFrame();
                    if (scenario.intercepted) {
                        assertTrue(context, row.bool("turretExists"));
                        SourceAntiDefensePlayback.UnitState unit = units.get(row.text("unitName"));
                        if (unit == null) {
                            unit = new SourceAntiDefensePlayback.UnitState();
                            units.put(row.text("unitName"), unit);
                        }
                        assertSourceState(context, row, playback, unit);
                    } else {
                        assertFalse(context, row.bool("turretExists"));
                        assertEquals(context, "none", row.text("lock"));
                        assertFalse(context, row.bool("antiActive"));
                        assertEquals(context, "", row.text("turretFrame"));
                    }
                    hostCount++;
                }
            }
        }
        assertTrue(scenario.name, pendingBefore == null);
        assertEquals(scenario.name, scenario.hostRows, hostCount);
        assertEquals(scenario.name, scenario.callbackPairs, callbackCount);
        assertEquals(scenario.name, scenario.firstCallback, firstCallback);
        assertEquals(scenario.name, scenario.lastCallback, lastCallback);
        if (scenario.intercepted) assertTrue(scenario.name, killed.contains("unit1"));
        else assertTrue(scenario.name, killed.isEmpty());
    }

    private static void assertSourceState(String context, Row source,
                                          SourceAntiDefensePlayback model,
                                          SourceAntiDefensePlayback.UnitState unit) {
        assertEquals(context, source.integer("antiCountdown"), unit.getCountdown());
        assertEquals(context, source.integer("antiOffset"), unit.getOffset());
        assertEquals(context, source.integer("turretFrame"), model.getTurretFrame());
        assertEquals(context, source.real("turretAlpha"), model.getTurretAlphaPercent(), 0d);
        assertEquals(context, source.bool("turretPlaying"), model.isTurretPlaying());
        assertEquals(context, !source.bool("turretPlaying"), source.bool("turretStopped"));
    }

    private static final class Row {
        private final Map<String, Integer> columns;
        private final String[] values;

        Row(Map<String, Integer> columns, String line) {
            this.columns = columns;
            values = line.split("\t", -1);
            assertEquals("source row width", columns.size(), values.length);
        }

        String text(String name) {
            Integer index = columns.get(name);
            assertNotNull("missing source column " + name, index);
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
        JsonValue value = new JsonReader().parse(json);
        return new double[]{value.get(0).asDouble(), value.get(1).asDouble()};
    }

    private static InputStream stream(String name) {
        InputStream input = SourceAirfieldDefensePlaybackTest.class.getClassLoader()
                .getResourceAsStream(name);
        assertNotNull(name, input);
        return input;
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream input = stream(name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte value : hash) hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }
}
