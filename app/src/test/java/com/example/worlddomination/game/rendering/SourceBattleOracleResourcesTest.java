package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.*;

/** Reference captures must survive checkout unchanged, independently of replay code. */
public final class SourceBattleOracleResourcesTest {
    @Test public void chinookCityImpactCaptureRetainsSourceHashAndBoundaryFrames()
            throws Exception {
        JsonValue source = json("source-chinook-depths-provenance.json");
        assertEquals(479, source.getInt("cityImpactRows"));
        assertEquals("c02766d36111df366892ed494baf817ba6e89f80a11355322844da1869b83537",
                source.getString("cityImpactSourceCityAsSha256"));
        assertEquals(source.getString("cityImpactCsvSha256"),
                sha256(bytes("source-chinook-city-impact.csv")));
        String[] rows = new String(bytes("source-chinook-city-impact.csv"),
                StandardCharsets.UTF_8).split("\\R");
        assertEquals("header plus source ticks 0 through 478", 480, rows.length);
        assertEquals("tick,cityFrame,rawAlpha,dustAttached,dustFrameZeroBased,"
                + "randString,targetFrame", rows[0]);
        assertEquals("379,4,85.0,1,0,65848,3", rows[380]);
        assertEquals("387,3,-4.999999999999993,0,-1,65848,3", rows[388]);
        assertEquals("478,3,100.0,0,-1,65848,3", rows[479]);
    }

    @Test public void chinookAliasPresentationRngCaptureRetainsRecordedSourceHash()
            throws Exception {
        JsonValue source = json("source-chinook-depths-provenance.json");
        assertEquals(479, source.getInt("lastSourceTick"));
        assertEquals(480, source.getInt("aliasRngRows"));
        assertTrue(source.getBoolean("presentationRngEqualAtAll480Ticks"));
        assertEquals(source.getString("aliasRngCsvSha256"),
                sha256(bytes("source-chinook-rng-alias.csv")));
        String rng = new String(bytes("source-chinook-rng-alias.csv"),
                StandardCharsets.UTF_8);
        String[] firstRows = rng.split("\\R", 3);
        assertEquals("source RNG schema", "mode,tick,stateSha256,nextRandom", firstRows[0]);
        assertEquals("post-construction source stream at tick zero",
                "alias,0,6363095b515288e75ba0d0814f7cefd536c6d98ea97b5e72c72705bd68ef07fb,"
                        + "0.5379287837318205", firstRows[1]);
    }

    @Test public void chinookLiteralAndAliasCapturesKeepDistinctSourceProvenance()
            throws Exception {
        JsonValue source = json("source-chinook-depths-provenance.json");
        assertEquals(json("source-apache-depths-provenance.json").getString("sourceSwfSha256"),
                source.getString("sourceSwfSha256"));
        assertEquals(479, source.getInt("lastSourceTick"));
        assertEquals(7200, source.getInt("actorRoleRowsPerMode"));
        assertTrue("literal source lacks tank_noShade body",
                source.getBoolean("literalTankBodyMissing"));
        assertEquals("tank_noShade", source.getString("literalMissingLinkage"));
        assertEquals(0, source.getInt("aliasAdditionalMissingLinkages"));
        assertTrue(source.getBoolean("simulationEventsAndRngEqualBetweenModes"));
        assertTrue(source.getBoolean("presentationRngEqualAtAll480Ticks"));
        assertEquals(source.getString("aliasCsvSha256"),
                sha256(bytes("source-chinook-depths-alias.csv")));
        assertEquals(source.getString("literalCsvSha256"),
                sha256(bytes("source-chinook-depths-literal.csv")));
        assertFalse("literal and alias captures remain distinguishable",
                source.getString("aliasCsvSha256").equals(source.getString("literalCsvSha256")));
        String literal = new String(bytes("source-chinook-depths-literal.csv"),
                StandardCharsets.UTF_8);
        String alias = new String(bytes("source-chinook-depths-alias.csv"),
                StandardCharsets.UTF_8);
        assertTrue("literal tick-zero tank body is absent",
                literal.contains("literal,0,1,unit2,tank_noShade,tail,body,False,False,"));
        assertTrue("alias tick-zero tank body occupies the source parent",
                alias.contains("alias,0,1,unit2,tank_noShade,tail,body,True,True,battleMC,22510"));
    }

    @Test public void infantryCaptureRetainsItsRecordedSourceHash() throws Exception {
        JsonValue source = json("source-infantry-depths-provenance.json");
        assertEquals(json("source-apache-depths-provenance.json").getString("sourceSwfSha256"),
                source.getString("sourceSwfSha256"));
        assertEquals(442, source.getInt("lastSourceTick"));
        assertEquals(5316, source.getInt("actorRoleRows"));
        assertEquals(122, source.getInt("authoredBattleChildren"));
        assertEquals(137, source.getInt("preUnitBattleChildren"));
        assertEquals(0, source.getInt("sourcePoseComparisonMismatches"));
        assertEquals(1772, source.getInt("sourcePoseComparisonRows"));
        assertEquals(source.getString("csvSha256"),
                sha256(bytes("source-infantry-depths-2.csv")));
    }

    @Test public void fourOpponentInfantryCaptureRetainsItsRecordedSourceHash() throws Exception {
        JsonValue source = json("source-infantry-depths-4-provenance.json");
        assertEquals(json("source-infantry-depths-provenance.json").getString("sourceSwfSha256"),
                source.getString("sourceSwfSha256"));
        assertEquals(648, source.getInt("lastSourceTick"));
        assertEquals(7788, source.getInt("actorRoleRows"));
        assertEquals(122, source.getInt("authoredBattleChildren"));
        assertEquals(147, source.getInt("preUnitBattleChildren"));
        assertEquals(source.getString("csvSha256"),
                sha256(bytes("source-infantry-depths-4.csv")));
    }

    @Test public void depthCapturesRetainTheirRecordedSourceHashes() throws Exception {
        JsonValue source = json("source-apache-depths-provenance.json");
        for (String opponents : new String[]{"2", "4"}) {
            assertEquals(source.get("csvSha256").getString(opponents),
                    sha256(bytes("source-apache-depths-" + opponents + ".csv")));
        }
    }

    @Test public void rngCapturesMatchDepthCapturesAndSourceRevision() throws Exception {
        JsonValue depths = json("source-apache-depths-provenance.json");
        JsonValue rng = json("source-apache-rng-provenance.json");
        assertEquals(depths.getString("sourceSwfSha256"),
                rng.get("referenceSha256").getString("WorldDomination2.swf"));
        assertEquals(2, rng.get("cases").size);
        for (JsonValue entry = rng.get("cases").child; entry != null; entry = entry.next) {
            String opponents = entry.getString("opponents");
            assertEquals(depths.get("csvSha256").getString(opponents),
                    entry.getString("actorOracleSha256"));
            assertEquals(depths.get("lastSourceTick").getInt(opponents),
                    entry.getInt("doneTick"));
            assertEquals(entry.getInt("doneTick") + 1, entry.getInt("rows"));
            assertEquals(entry.getInt("expectedActorRows"), entry.getInt("actorRowsCompared"));
            assertEquals(0, entry.get("actorMismatchesFirst30").size);
            assertEquals(entry.getString("rngCsvSha256"),
                    sha256(bytes("source-apache-rng-" + opponents + ".csv")));
        }
    }

    private static JsonValue json(String resource) throws Exception {
        return new JsonReader().parse(new String(bytes(resource), StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String resource) throws Exception {
        try (InputStream input = SourceBattleOracleResourcesTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull("packaged replay fixture " + resource, input);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        StringBuilder result = new StringBuilder(64);
        for (byte value : MessageDigest.getInstance("SHA-256").digest(data)) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }
}
