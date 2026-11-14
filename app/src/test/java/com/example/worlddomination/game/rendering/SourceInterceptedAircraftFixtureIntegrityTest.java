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

/** Locks the legal intercepted plane trace to its captured source bytes. */
public final class SourceInterceptedAircraftFixtureIntegrityTest {
    private static final String PREFIX = "source-intercepted-plane001-";
    private static final String PROVENANCE_SHA =
            "915a1bbb5c24fa804b42d32f7eeba130b34d5c867bb1ed45dd982ce24d163566";
    private static final String MUTED_PREFIX = PREFIX + "muted-";
    private static final String MUTED_PROVENANCE_SHA =
            "30b7964aaa97de0a123d5d546b6dacba6cdafd9b3c20a21e9ef11ebb201932e4";

    @Test public void sourceAndPackagedTraceHashesRemainPinned() throws IOException {
        byte[] bytes = resource(PREFIX + "provenance.json");
        assertEquals("provenance cannot silently relabel captures",
                PROVENANCE_SHA, sha256(bytes));
        JsonValue source = new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        JsonValue sourceHashes = source.get("sourceSha256");
        assertEquals("fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1",
                sourceHashes.getString("WorldDomination2.swf"));
        assertEquals("bc54c0e6c46f4e33bbb20e786c755f93aaba5d3d7224b0120eeeccc81eef8539",
                sourceHashes.getString("wd2/generated/Unit.py"));
        assertEquals("2de8afd493f28e36c3a94afa96b80795d685b8b023b12dc9d9ed634692c3accf",
                sourceHashes.getString("wd2/generated/View.py"));
        JsonValue packages = source.get("packagedFixtureSha256");
        assertEquals("seven captured data files plus provenance", 7, packages.size);
        for (JsonValue entry = packages.child; entry != null; entry = entry.next) {
            assertTrue(entry.name.startsWith(PREFIX));
            assertEquals(entry.name, entry.asString(), sha256(resource(entry.name)));
        }
        assertEquals("full source capture is packaged as state, while units is a projection",
                source.get("fixtureSha256").getString("units"),
                packages.getString(PREFIX + "state.csv"));
        assertNotEquals(packages.getString(PREFIX + "units.csv"),
                packages.getString(PREFIX + "state.csv"));
        JsonValue counts = source.get("packagedRows");
        assertEquals(378, counts.getInt("units"));
        assertEquals(378, counts.getInt("state"));
        assertEquals(2027, counts.getInt("roles"));
        assertEquals(19, counts.getInt("smoke"));
        assertEquals(378, counts.getInt("city"));
        assertEquals(19, counts.getInt("audio"));
        for (JsonValue count = counts.child; count != null; count = count.next) {
            assertEquals(count.name, count.asInt(), rows(PREFIX + count.name + ".csv"));
        }
    }

    @Test public void legalResultAndContinuousTicksStayDistinctFromAliasAdaptations()
            throws IOException {
        JsonValue source = provenance();
        assertEquals(0, source.getInt("seed"));
        assertEquals("normal", source.getString("difficulty"));
        assertEquals(2, source.get("leaders").size);
        assertEquals(0, source.get("leaders").getInt(0));
        assertEquals(1, source.get("leaders").getInt(1));
        assertEquals(22, source.get("schedule").size);
        JsonValue finalTurn = source.get("schedule").get(21);
        assertEquals(22, finalTurn.getInt("turn"));
        assertEquals("preAttack", finalTurn.getString("action"));
        assertEquals("plane1", finalTurn.get("options").getString("weapon"));
        assertEquals("bioload1", finalTurn.get("options").getString("payload"));
        assertEquals("resources", finalTurn.getString("second"));
        JsonValue event = source.get("event");
        assertEquals("battle", event.getString("_item"));
        assertEquals("preAttack", event.getString("_value"));
        assertEquals("airfield", event.getString("_frame"));
        assertEquals(5, event.getInt("_city"));
        assertFalse(event.getBoolean("_result"));
        assertFalse(event.getBoolean("_isShip"));
        assertEquals(0, event.getInt("_casualties"));
        assertTrue(source.getString("linkageAliasPolicy").startsWith("None:"));
        assertEquals(377, source.getInt("lastTick"));
        assertEquals(378, source.getInt("rows"));
        for (String suffix : new String[] {"units", "state", "city"})
            assertContinuousTicks(PREFIX + suffix + ".csv", 378);
    }

    @Test public void deathAndExplosionKeepTheirTwoBoundaryTicksAndCleanup()
            throws IOException {
        Map<Integer, String[]> state = rowsByTick(PREFIX + "state.csv", null);
        assertEquals("speedup_die", state.get(313)[1]);
        assertEquals("alive", state.get(313)[2]);
        assertEquals("dying", state.get(314)[1]);
        assertEquals("dead", state.get(314)[2]);
        assertEquals("True", state.get(314)[3]); // wrapper still in active array
        assertEquals("False", state.get(314)[34]); // body detached
        assertEquals("False", state.get(315)[3]); // next visit removes wrapper
        assertEquals(36.27275153669018d, Double.parseDouble(state.get(314)[5]), .001d);
        assertEquals(24.27275153669018d, Double.parseDouble(state.get(315)[5]), .001d);

        Map<Integer, String[]> explosions = rowsByTick(PREFIX + "roles.csv", "explosion");
        assertEquals("False", explosions.get(313)[2]);
        String[] impact = explosions.get(314);
        assertEquals("explosion003", impact[5]);
        assertEquals("22518", impact[6]);
        assertEquals("True", impact[7]);
        assertEquals("1", impact[8]);
        assertEquals(48.27275153669018d, Double.parseDouble(impact[9]), .001d);
        assertEquals(254.7482852266179d, Double.parseDouble(impact[10]), .001d);
        assertEquals("2", explosions.get(315)[8]);
        assertEquals("22", explosions.get(335)[8]);
        assertEquals("True", explosions.get(375)[7]);
        assertEquals("False", explosions.get(376)[7]);
        JsonValue source = provenance();
        assertTrue(source.getString("explosionCleanup").contains("tick 376"));
    }

    @Test public void mutedCapturePinsReadOnlySourceAndPackagedBytes() throws IOException {
        byte[] bytes = resource(MUTED_PREFIX + "provenance.json");
        assertEquals(MUTED_PROVENANCE_SHA, sha256(bytes));
        JsonValue muted = new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        JsonValue sound = muted.get("soundOption");
        assertFalse(sound.getBoolean("battleTick0"));
        assertTrue(sound.getBoolean("beforeStepTick199"));
        assertTrue("canonical resolution stays unmuted", sound.getBoolean("canonicalResolution"));
        assertFalse(muted.getBoolean("battleResult"));
        assertEquals(0, muted.getInt("battleCasualtiesMillions"));
        assertEquals(377, muted.getInt("lastTick"));
        assertEquals(77, muted.getInt("firstRngDivergenceFromNormalTick"));
        assertEquals("normal source capture must be the comparison baseline",
                provenance().get("sourceSha256").getString("WorldDomination2.swf"),
                muted.get("sourceSha256").getString("WorldDomination2.swf"));
        assertEquals("same legal source, different presentation sound option",
                provenance().get("packagedFixtureSha256").getString(PREFIX + "state.csv"),
                muted.getString("normalStateSha256"));
        JsonValue packaged = muted.get("packagedSha256");
        assertEquals(2, packaged.size);
        for (JsonValue entry = packaged.child; entry != null; entry = entry.next)
            assertEquals(entry.name, entry.asString(), sha256(resource(entry.name)));
        assertEquals(packaged.getString(MUTED_PREFIX + "state.csv"),
                muted.get("captureSha256").getString("units"));
        assertEquals(packaged.getString(MUTED_PREFIX + "audio.csv"),
                muted.get("captureSha256").getString("audio"));
        assertEquals(378, muted.get("rows").getInt("state"));
        assertEquals(11, muted.get("rows").getInt("audio"));
        assertContinuousTicks(MUTED_PREFIX + "state.csv", 378);
        assertEquals(11, rows(MUTED_PREFIX + "audio.csv"));
    }

    @Test public void mutedSoundChangesPrivateRandomButNotPlanePoseOrDeath()
            throws IOException {
        Map<Integer, String[]> normal = rowsByTick(PREFIX + "state.csv", null);
        Map<Integer, String[]> muted = rowsByTick(MUTED_PREFIX + "state.csv", null);
        assertEquals(378, muted.size());
        for (int tick = 0; tick < 378; tick++) {
            String[] before = normal.get(tick);
            String[] after = muted.get(tick);
            assertNotNull("normal tick " + tick, before);
            assertNotNull("muted tick " + tick, after);
            assertEquals("action t" + tick, before[1], after[1]);
            assertEquals("status t" + tick, before[2], after[2]);
            for (int column : new int[] {5, 6, 27, 28, 29, 30, 31, 32, 34})
                assertEquals("pose column " + column + " t" + tick,
                        before[column], after[column]);
            if (tick < 77) assertEquals("RNG before muted sound t" + tick,
                    before[before.length - 1], after[after.length - 1]);
        }
        assertNotEquals("source sound at tick 77 consumes a private random draw",
                normal.get(77)[normal.get(77).length - 1],
                muted.get(77)[muted.get(77).length - 1]);
        assertEquals("speedup_die", muted.get(313)[1]);
        assertEquals("dying", muted.get(314)[1]);
        assertEquals("dead", muted.get(314)[2]);
        assertEquals("False", muted.get(315)[3]);

        try (BufferedReader reader = reader(MUTED_PREFIX + "audio.csv")) {
            assertEquals("tick,event,linkage,loops,volume,pan", reader.readLine());
            int takeoffStarts = 0, takeoffStops = 0, deathStarts = 0;
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.contains("start,unit_Fightertakeoff.wav")) {
                    assertTrue(line.startsWith("199,"));
                    takeoffStarts++;
                } else if (line.contains("stop,unit_Fightertakeoff.wav")) {
                    assertTrue(line.startsWith("314,"));
                    takeoffStops++;
                } else if (line.contains("start,Explode small3.wav")) {
                    assertTrue(line.startsWith("314,"));
                    deathStarts++;
                }
            }
            assertEquals(1, takeoffStarts);
            assertEquals(1, takeoffStops);
            assertEquals(1, deathStarts);
        }
    }

    private static JsonValue provenance() throws IOException {
        return new JsonReader().parse(new String(resource(PREFIX + "provenance.json"),
                StandardCharsets.UTF_8));
    }

    private static int rows(String name) throws IOException {
        int count = -1;
        try (BufferedReader reader = reader(name)) {
            while (reader.readLine() != null) count++;
        }
        return count;
    }

    private static void assertContinuousTicks(String name, int expected) throws IOException {
        try (BufferedReader reader = reader(name)) {
            reader.readLine();
            int tick = 0;
            for (String line; (line = reader.readLine()) != null; tick++)
                assertEquals(name + " tick " + tick, tick,
                        Integer.parseInt(line.split(",", 2)[0]));
            assertEquals(name, expected, tick);
        }
    }

    private static Map<Integer, String[]> rowsByTick(String name, String role)
            throws IOException {
        Map<Integer, String[]> result = new HashMap<>();
        try (BufferedReader reader = reader(name)) {
            reader.readLine();
            for (String line; (line = reader.readLine()) != null; ) {
                String[] row = line.split(",", -1);
                if (role == null || role.equals(row[1]))
                    result.put(Integer.parseInt(row[0]), row);
            }
        }
        return result;
    }

    private static BufferedReader reader(String name) throws IOException {
        InputStream stream = SourceInterceptedAircraftFixtureIntegrityTest.class
                .getClassLoader().getResourceAsStream(name);
        if (stream == null) throw new IOException("Missing fixture " + name);
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream stream = SourceInterceptedAircraftFixtureIntegrityTest.class
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
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
