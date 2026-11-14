package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Locks the recorded Python source evidence, including its noncanonical boundary probe. */
public final class SourceBiologicalFixtureIntegrityTest {
    private static final String[] NAMES = {
            "source-bio-plane1", "source-bio-plane1-warhead1",
            "source-bio-ship-intercept", "source-bio-ship-success",
            "source-bio-sub", "source-bio-sub-failed-unit"
    };
    // Pin provenance as well as its listed payload hashes; neither can be silently rewritten.
    private static final String[] PROVENANCE_SHA = {
            "85094189edbfd8c2e86f91313c9198278ee7c59200a731fe17aa513b57ae4ace",
            "4e01d4b447a5f55b7b754d70800a13bef1080e18319de0c2b5a6be4c33eb761b",
            "6936b87e1eeed0239d42b9711b2cc4b6ddbc80f3e41bdf44519e350f1a963157",
            "ea9acf5390e913f68fd8b1fb99c3c80decabf048886141aaf2b57011c10dc400",
            "30f7b9da2c1f4e6eb639364041dc7bcd1c7ebef2011d1367d1adda954dc56b8b",
            "2fc16924f372bf13b36733f336028535dc60a9b025f37540da1a28db9a7d61d0"
    };
    private static final String AIRCRAFT_PROVENANCE_SHA =
            "c89dc00a2e83e750636c8c10817bbd3d9b4bd4d9fbb770b2f43f6520d0ade35a";

    @Test
    public void everyRecordedFixtureHasItsOriginalBytesAndRowCount() throws IOException {
        for (int i = 0; i < NAMES.length; i++) {
            String name = NAMES[i];
            byte[] provenanceBytes = resource(name + "-provenance.json");
            assertEquals(name + " provenance", PROVENANCE_SHA[i], sha256(provenanceBytes));
            JsonValue provenance = new JsonReader().parse(
                    new String(provenanceBytes, StandardCharsets.UTF_8));
            JsonValue hashes = provenance.get("fixtureSha256");
            JsonValue counts = provenance.get("rowCounts");
            if (counts == null) counts = provenance.get("rows");
            assertNotNull(name, hashes);
            assertNotNull(name, counts);
            Map<String, Integer> observedRows = new HashMap<>();
            for (JsonValue file = hashes.child; file != null; file = file.next) {
                assertTrue(name + " foreign fixture " + file.name,
                        file.name.startsWith(name + "-"));
                byte[] bytes = resource(file.name);
                assertEquals(file.name, file.asString(), sha256(bytes));
                if (file.name.endsWith(".csv")) {
                    String key = file.name.substring(name.length() + 1,
                            file.name.length() - ".csv".length());
                    observedRows.put(key, csvRows(file.name));
                }
            }
            for (JsonValue count = counts.child; count != null; count = count.next) {
                String suffix = csvSuffix(count.name);
                int observed = observedRows.containsKey(suffix)
                        ? observedRows.get(suffix) : 0;
                assertEquals(name + " " + count.name, count.asInt(), observed);
                if (count.asInt() == 0 && observedRows.containsKey(suffix)) {
                    assertEquals(name + " empty CSV", 0, observedRows.get(suffix).intValue());
                }
            }
            assertEquals(name + " tick origin", 0, firstTick(name + "-ticks.csv"));
            assertEquals(name + " last tick", counts.getInt("ticks") - 1,
                    lastTick(name + "-ticks.csv"));
        }
    }

    @Test
    public void legalPayloadsAndNoncanonicalFailedUnitStayDistinct() throws IOException {
        JsonValue plane = provenance("source-bio-plane1");
        JsonValue warhead = provenance("source-bio-plane1-warhead1");
        JsonValue shipMiss = provenance("source-bio-ship-intercept");
        JsonValue shipHit = provenance("source-bio-ship-success");
        JsonValue sub = provenance("source-bio-sub");
        JsonValue failed = provenance("source-bio-sub-failed-unit");

        assertEquals(343, plane.getInt("firstCloudTick"));
        assertEquals(343, firstTick("source-bio-plane1-clouds.csv"));
        assertTrue(warhead.get("firstCloudTick").isNull());
        assertEquals(0, warhead.get("rowCounts").getInt("clouds"));

        assertTrue(shipMiss.get("firstCloudTick").isNull());
        assertEquals(0, shipMiss.get("rowCounts").getInt("clouds"));
        assertEquals(296, shipHit.getInt("firstCloudTick"));
        assertEquals(296, firstTick("source-bio-ship-success-clouds.csv"));
        assertEquals(3, shipBattleResults(shipMiss, false));
        assertEquals(3, shipBattleResults(shipHit, true));
        assertEquals(3, firstCityBioTicks("source-bio-ship-success-cities.csv").size());
        assertEquals(Integer.valueOf(296),
                firstCityBioTicks("source-bio-ship-success-cities.csv").get(10));
        assertEquals(Integer.valueOf(339),
                firstCityBioTicks("source-bio-ship-success-cities.csv").get(11));
        assertEquals(Integer.valueOf(375),
                firstCityBioTicks("source-bio-ship-success-cities.csv").get(12));

        assertEquals(577, sub.getInt("firstCloudTick"));
        assertEquals(577, firstTick("source-bio-sub-clouds.csv"));
        assertEquals(585, sub.getInt("firstDetachedTick"));
        JsonValue override = failed.get("boundaryOverride");
        assertEquals(234, override.getInt("tick"));
        assertFalse(override.getBoolean("legalMatchParity"));
        assertTrue(override.getBoolean("canonicalResultRemained"));
        assertFalse(override.get("fields").getBoolean("unit.result"));
        assertTrue(failed.get("firstCloudTick").isNull());
        assertEquals(0, failed.get("rows").getInt("clouds"));
        assertEquals(642, sub.get("rows").getInt("ticks"));
        assertEquals(582, failed.get("rows").getInt("ticks"));
    }

    @Test
    public void aircraftTrailSnapshotsKeepTheirRecordedBytesAndActivationBoundaries()
            throws IOException {
        byte[] manifestBytes = resource("source-aircraft-trail-provenance.json");
        assertEquals(AIRCRAFT_PROVENANCE_SHA, sha256(manifestBytes));
        JsonValue manifest = new JsonReader().parse(
                new String(manifestBytes, StandardCharsets.UTF_8));
        JsonValue hashes = manifest.get("sha256");
        assertEquals(8, hashes.size);
        for (JsonValue entry = hashes.child; entry != null; entry = entry.next) {
            assertTrue(entry.name.startsWith("source-aircraft-trail-"));
            assertEquals(entry.name, entry.asString(), sha256(resource(entry.name)));
        }
        String[] cases = {"plane1", "plane1-warhead1", "ship-intercept", "ship-success"};
        int[] firstTicks = {238, 238, 200, 239};
        int[] expectedRows = {7, 7, 16, 36};
        for (int index = 0; index < cases.length; index++) {
            String name = cases[index];
            JsonValue definition = manifest.get("cases").get(name);
            assertNotNull(name, definition);
            assertEquals(name, firstTicks[index], definition.getInt("firstTrailTick"));
            JsonValue rng = new JsonReader().parse(new String(resource(
                    "source-aircraft-trail-" + name + "-rng-pretrail.json"),
                    StandardCharsets.UTF_8));
            assertEquals(name, firstTicks[index] - 1, rng.getInt("afterTick"));
            assertTrue(name + " missing MT state", rng.get("mtState").size > 600);
            JsonValue samples = new JsonReader().parse(new String(resource(
                    "source-aircraft-trail-" + name + "-trail-samples.json"),
                    StandardCharsets.UTF_8));
            assertEquals(name, expectedRows[index], samples.size);
            int firstAttachedTrail = Integer.MAX_VALUE;
            for (JsonValue sample = samples.child; sample != null; sample = sample.next) {
                if (sample.getBoolean("trailActive")
                        && sample.get("children").size > 0) {
                    firstAttachedTrail = Math.min(firstAttachedTrail, sample.getInt("tick"));
                }
            }
            assertEquals(name, firstTicks[index], firstAttachedTrail);
        }
    }

    private static int shipBattleResults(JsonValue provenance, boolean expected) {
        for (JsonValue event = provenance.get("events").child;
             event != null; event = event.next) {
            if (!"battle".equals(event.getString("_item"))
                    || !event.getBoolean("_isShip", false)) continue;
            JsonValue results = event.get("_result_arr");
            assertEquals(3, results.size);
            for (JsonValue result = results.child; result != null; result = result.next) {
                assertEquals(expected, result.getBoolean("_result"));
            }
            return results.size;
        }
        fail("missing ship battle event");
        return 0;
    }

    private static Map<Integer, Integer> firstCityBioTicks(String file) throws IOException {
        Map<Integer, Integer> first = new HashMap<>();
        try (BufferedReader reader = reader(file)) {
            reader.readLine();
            for (String row; (row = reader.readLine()) != null; ) {
                String[] fields = row.split(",", 4);
                if ("True".equals(fields[2])) {
                    first.putIfAbsent(Integer.parseInt(fields[1]), Integer.parseInt(fields[0]));
                }
            }
        }
        return first;
    }

    private static String csvSuffix(String countKey) {
        switch (countKey) {
            case "cityChildren": return "city-children";
            case "rootRoles": return "root-roles";
            case "knownUnit": return "known-unit";
            default: return countKey;
        }
    }

    private static JsonValue provenance(String name) throws IOException {
        return new JsonReader().parse(new String(resource(name + "-provenance.json"),
                StandardCharsets.UTF_8));
    }

    private static int csvRows(String file) throws IOException {
        int rows = -1;
        try (BufferedReader reader = reader(file)) {
            while (reader.readLine() != null) rows++;
        }
        return rows;
    }

    private static int firstTick(String file) throws IOException {
        try (BufferedReader reader = reader(file)) {
            reader.readLine();
            return Integer.parseInt(reader.readLine().split(",", 2)[0]);
        }
    }

    private static int lastTick(String file) throws IOException {
        int tick = -1;
        try (BufferedReader reader = reader(file)) {
            reader.readLine();
            for (String row; (row = reader.readLine()) != null; ) {
                tick = Integer.parseInt(row.split(",", 2)[0]);
            }
        }
        return tick;
    }

    private static BufferedReader reader(String name) throws IOException {
        InputStream stream = SourceBiologicalFixtureIntegrityTest.class
                .getClassLoader().getResourceAsStream(name);
        if (stream == null) throw new IOException("Missing fixture " + name);
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream stream = SourceBiologicalFixtureIntegrityTest.class
                .getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("Missing fixture " + name);
            return stream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
