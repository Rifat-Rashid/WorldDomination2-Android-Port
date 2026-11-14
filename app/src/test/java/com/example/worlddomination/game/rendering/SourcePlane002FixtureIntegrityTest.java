package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Pins legal plane002 source captures and their lossless tick/role schema. */
public final class SourcePlane002FixtureIntegrityTest {
    private static final String PREFIX = "source-plane002-";
    private static final String PROVENANCE_SHA =
            "136296229a5fc03255ec49a6bf2d0b6f81881a5c318e4f932a3f5f3e52b87007";
    private static final String SUCCESS_SHA =
            "4fea37caf1730ad0e87bc56acf3c8caf3270c3b989280d418d4d03f4a334a5f5";
    private static final String INTERCEPT_SHA =
            "1d9f1755eb38850eed7fffdde736bb2b411b70a86269c4be95643e23c7ccca5d";

    @Test public void sourceAndCompactHashesPreserveLegalProvenance() throws Exception {
        assertEquals(PROVENANCE_SHA, sha(resource(PREFIX + "provenance.json")));
        JsonValue provenance = read(PREFIX + "provenance.json");
        assertEquals("source-plane002-provenance-v1", provenance.getString("schema"));
        assertTrue(provenance.getString("capturePolicy").contains("no outcome or stock overrides"));
        assertTrue(provenance.getString("normalization").contains("JSON null"));
        assertTrue(provenance.getString("observerPolicy").contains("never call mutating"));
        for (String name : new String[] {"success", "intercept"}) {
            JsonValue caseInfo = provenance.get("cases").get(name);
            assertNotNull(caseInfo);
            assertEquals(name.equals("success") ? SUCCESS_SHA : INTERCEPT_SHA,
                    caseInfo.getString("fixtureSha256"));
            assertEquals(caseInfo.getString("fixtureSha256"),
                    sha(resource(caseInfo.getString("fixture"))));
            assertEquals("fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1",
                    caseInfo.get("sourceSha256").getString("WorldDomination2.swf"));
            assertEquals("bc54c0e6c46f4e33bbb20e786c755f93aaba5d3d7224b0120eeeccc81eef8539",
                    caseInfo.get("sourceSha256").getString("wd2/generated/Unit.py"));
            assertEquals("2de8afd493f28e36c3a94afa96b80795d685b8b023b12dc9d9ed634692c3accf",
                    caseInfo.get("sourceSha256").getString("wd2/generated/View.py"));
            assertEquals(name.equals("success")
                            ? "4e636a9ec87ab794e59f359ac760793c6e49f9008776ed82fc8964b564606033"
                            : "f9d68a469e22ca8653c9775966ce73c04720626f0fc77a007cad765c8c8ed8d0",
                    caseInfo.getString("rawSha256"));
            assertEquals(name.equals("success")
                            ? "e6db601932ea8c0e838de04e095609e4ebb63c66fb933ec8dabf3fb9a0bb482a"
                            : "0c19ab6f4a0f1e2be99bd8d7b05cca96ec3c0f435c8efd69d5195986a0d68cd6",
                    caseInfo.getString("captureScriptSha256"));
            assertEquals(name.equals("success")
                            ? "04c8f2f5fa3037bc5dab1fcacbaf1418842605c373bf3776e0f5fc3300c37680"
                            : "8f168361a49fe5b0524191d703c5361a7bcc482309c9535ac79124d9b52cf2b7",
                    caseInfo.getString("cleanRawSha256"));
            assertEquals(name.equals("success")
                            ? "15465f18ad660efb5abe1933eb8a6164bbde51b9cdc58ea7860de972f9627d45"
                            : "5887ae1a96f3dede99af676b69b7c013d128ae2514fae3e7e0399f14a1f52722",
                    caseInfo.getString("cleanCaptureScriptSha256"));
            assertEquals("2123fdda84d768b0da9061eb604c84200d1c02d749da23e927a663351f50a5d3",
                    caseInfo.getString("observerEffectComparisonSha256"));
            assertEquals(name.equals("success")
                            ? "32f4d928a94959178141628f0119c89d9bd5f132a0809bafd8712211a4ca547f"
                            : "0757674bbff3ae1c0213f36f7dc65988cf46dcfe333a7a96b413f0ae37fe9ca0",
                    caseInfo.getString("observerContaminatedShadowRawSha256"));
            if (name.equals("success")) {
                assertEquals("b6d967bfec423132089bffc10274e12692ee4be29a41dbc06c3371194a7197dc",
                        caseInfo.getString("literalShadowRawSha256"));
                assertTrue(read(PREFIX + name + ".json").getString("compatibilityRepair")
                        .contains("Only battleMC attachMovie tank_noShade"));
            } else assertEquals("none",
                    read(PREFIX + name + ".json").getString("compatibilityRepair"));
        }
    }

    @Test public void successRetainsStoppedBodyAfterWrapperRemoval() throws Exception {
        JsonValue capture = read(PREFIX + "success.json");
        verifySchemaAndRows(capture, 572, 16, 730);
        assertEquals("success", capture.getString("case"));
        assertTrue(capture.get("events").get(1).getBoolean("_result"));
        assertEquals(272, capture.get("milestones").getInt("firstActiveMovement"));
        assertEquals(272, capture.get("milestones").getInt("firstAttachedTrail"));
        assertEquals(334, capture.get("milestones").getInt("firstCircularFlight"));
        assertEquals(381, capture.get("milestones").getInt("firstFlyOff"));
        assertTrue(capture.get("milestones").get("firstDeath").isNull());
        assertEquals(509, capture.get("milestones").getInt("firstWrapperInactive"));
        assertEquals(570, capture.get("milestones").getInt("firstBodyDetached"));
        assertEquals(571, capture.get("milestones").getInt("battleDone"));
        JsonValue rows = capture.get("rows");
        assertEquals("bomb", action(rows.get(380)));
        assertEquals("flyOff", action(rows.get(381)));
        JsonValue firstLeftTrail = rows.get(272).get(10).get(0);
        assertEquals("unit1trailLeft0", firstLeftTrail.get(0).asString());
        assertEquals(22519, firstLeftTrail.get(1).asInt());
        assertEquals(47d, firstLeftTrail.get(4).asDouble(), 0d);
        assertEquals(2, firstLeftTrail.get(7).asInt());
        assertTrue(rows.get(508).get(0).asBoolean());
        assertFalse(rows.get(509).get(0).asBoolean());
        assertTrue("stopped source body remains attached", rows.get(509).get(3).asBoolean());
        assertTrue(rows.get(569).get(3).asBoolean());
        assertFalse(rows.get(570).get(3).asBoolean());
        assertTrue(rows.get(571).get(12).asBoolean());
    }

    @Test public void interceptionDetachesBodyThenRetainsExplosionToCleanup()
            throws Exception {
        JsonValue capture = read(PREFIX + "intercept.json");
        verifySchemaAndRows(capture, 364, 24, 648);
        assertEquals("intercept", capture.getString("case"));
        assertFalse(capture.get("events").get(1).getBoolean("_result"));
        assertEquals(195, capture.get("milestones").getInt("firstActiveMovement"));
        assertEquals(195, capture.get("milestones").getInt("firstAttachedTrail"));
        assertEquals(257, capture.get("milestones").getInt("firstCircularFlight"));
        assertEquals(299, capture.get("milestones").getInt("firstDeath"));
        assertEquals(299, capture.get("milestones").getInt("firstBodyDetached"));
        assertEquals(300, capture.get("milestones").getInt("firstWrapperInactive"));
        assertEquals(299, capture.get("milestones").getInt("firstExplosionAttached"));
        assertEquals(362, capture.get("milestones").getInt("firstExplosionDetached"));
        assertEquals(363, capture.get("milestones").getInt("battleDone"));
        JsonValue rows = capture.get("rows");
        assertEquals("speedup_die", action(rows.get(298)));
        assertEquals("dying", action(rows.get(299)));
        JsonValue firstLeftTrail = rows.get(195).get(10).get(0);
        assertEquals("unit1trailLeft0", firstLeftTrail.get(0).asString());
        assertEquals(22517, firstLeftTrail.get(1).asInt());
        assertEquals(33d, firstLeftTrail.get(4).asDouble(), 0d);
        assertEquals(2, firstLeftTrail.get(7).asInt());
        JsonValue cleanSecondLeft = rows.get(196).get(10).get(1);
        assertEquals("unit1trailLeft1", cleanSecondLeft.get(0).asString());
        assertEquals(-2.3878935520612092d, cleanSecondLeft.get(2).asDouble(), 0d);
        assertEquals(15.435563588325621d, cleanSecondLeft.get(3).asDouble(), 0d);
        assertFalse(rows.get(299).get(3).asBoolean());
        assertTrue(rows.get(299).get(9).asBoolean());
        assertEquals(1, rows.get(299).get(7).get(2).asInt());
        assertEquals(22, rows.get(361).get(7).get(2).asInt());
        assertTrue(rows.get(361).get(9).asBoolean());
        assertFalse(rows.get(362).get(9).asBoolean());
        assertTrue(rows.get(363).get(12).asBoolean());
    }

    private static void verifySchemaAndRows(JsonValue capture, int expectedRows,
                                            int expectedSchedule, int undefinedWidths)
            throws Exception {
        assertEquals("source-plane002-compact-v1", capture.getString("schema"));
        assertTrue(capture.getString("nullNumberPolicy").contains("remain unknown"));
        assertEquals(0, capture.getInt("seed"));
        assertEquals("normal", capture.getString("difficulty"));
        assertEquals(2, capture.get("leaders").size);
        assertEquals(0, capture.getInt("firstTick"));
        assertEquals(expectedRows - 1, capture.getInt("lastTick"));
        assertEquals(expectedSchedule, capture.get("schedule").size);
        assertEquals(14, capture.get("rowFields").size);
        assertEquals("append-only shadow field", "shadowInnerFrame",
                capture.get("rowFields").get(13).asString());
        assertEquals(32, capture.get("unitFields").size);
        assertEquals("nonmutating stored direction frame", "frame",
                capture.get("unitFields").get(16).asString());
        assertEquals(7, capture.get("clipFields").size);
        assertEquals(9, capture.get("trailFields").size);
        assertEquals(88, capture.get("trailEvents").size);
        assertEquals(expectedRows, capture.get("rows").size);
        int trailWidths = 0;
        for (JsonValue row = capture.get("rows").child; row != null; row = row.next) {
            assertEquals(14, row.size);
            assertEquals(32, row.get(1).size);
            assertEquals(7, row.get(2).size);
            assertEquals(7, row.get(4).size);
            assertEquals(7, row.get(6).size);
            assertNotNull(row.get(11)); // private battle RNG peek for every tick
            assertEquals("nested shadow direction follows the source tint frame",
                    row.get(6).get(2).asInt(), row.get(13).asInt());
            for (JsonValue trail = row.get(10).child; trail != null; trail = trail.next) {
                assertEquals(9, trail.size);
                assertTrue("undefined source trail width remains null", trail.get(5).isNull());
                assertTrue("undefined source trail height remains null", trail.get(6).isNull());
                trailWidths++;
            }
        }
        assertEquals(undefinedWidths, trailWidths);
        JsonValue info = read(PREFIX + "provenance.json")
                .get("cases").get(capture.getString("case"));
        assertEquals(expectedRows, info.getInt("rows"));
        assertEquals(undefinedWidths,
                info.get("undefinedTrailDimensions").getInt("_width"));
        assertEquals(undefinedWidths,
                info.get("undefinedTrailDimensions").getInt("_height"));
    }

    private static String action(JsonValue row) { return row.get(1).get(0).asString(); }

    private static JsonValue read(String name) throws IOException {
        return new JsonReader().parse(new String(resource(name), StandardCharsets.UTF_8));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = SourcePlane002FixtureIntegrityTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull("packaged source fixture " + name, input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private static String sha(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
