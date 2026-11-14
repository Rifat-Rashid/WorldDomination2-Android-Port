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
    @Test public void battlePhaseCapturesRetainSourceHashesAndHandoffBoundaries()
            throws Exception {
        JsonValue provenance = json("source-phase-provenance.json");
        String swf = "fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1";
        assertEquals(swf, provenance.get("sourceSha256").getString("WorldDomination2.swf"));
        assertEquals(swf, provenance.get("suicideHandoff")
                .get("sourceSha256").getString("WorldDomination2.swf"));
        assertEquals(json("source-apache-depths-provenance.json").getString("sourceSwfSha256"),
                swf);
        assertEquals("b72ab1b0cb2aec47ace662223d8ce4eaa08c715a0f9e79d088fc911dbfa358e3",
                provenance.getString("baselineGeneratorSha256"));
        assertEquals("31647286f84f07b21b180a6fe2472d13fa5eb6cc4365d06b4d19a15e3e6597bc",
                provenance.getString("sourceGeneratorSha256"));
        assertEquals("8b35c2fc69b0aa078dcc25baf94f1ed35d0952baecdd657716376260e04552db",
                provenance.getString("packagingGeneratorSha256"));
        assertEquals("45a7a3f5e490a8053cae898c9cc20d75ebc2d41b223b9b6d871b3483a37e99e2",
                provenance.get("suicideHandoff").getString("sourceGeneratorSha256"));

        byte[] twoTurns = bytes("source-phase-two-turns.csv");
        byte[] suicide = bytes("source-phase-suicide-handoff.csv");
        assertEquals("f32cf4d0a281463b79eee7997bd98f4a6940f7141eef21e92df703018d5951b3",
                sha256(twoTurns));
        assertEquals("d560fff75f4a6de7b0ba3110c281494f873bc93ee4649124bb39163bb3938020",
                sha256(suicide));
        assertEquals(440, provenance.getInt("rows"));
        assertEquals(365, provenance.get("suicideHandoff").getInt("rows"));
        assertEquals(provenance.getString("sha256"), sha256(twoTurns));
        assertEquals(provenance.get("suicideHandoff").getString("sha256"), sha256(suicide));
        String[] simpleRows = new String(twoTurns, StandardCharsets.UTF_8).split("\\R");
        String[] impactRows = new String(suicide, StandardCharsets.UTF_8).split("\\R");
        assertEquals("two battles, 220 ticks each, plus header", 441, simpleRows.length);
        assertEquals("battle ticks 0..363, first idle 364, plus header", 366,
                impactRows.length);
        assertEquals("turn,tick,canonicalClock,canonicalFlag0Tint,battleFlag0Tint,"
                + "canonicalCity0Frame,canonicalCity0Alpha,battleCity0Frame,"
                + "battleCity0Alpha,battleAb01Frame,battleAb02Frame,battleAb02PumpFrame,"
                + "canonicalRngNext,battleRngNext", simpleRows[0]);
        assertEquals("phase,tick,canonicalClock,canonicalCity7Frame,canonicalCity7Alpha,"
                + "battleCity7Frame,battleCity7Alpha,playbackDone,menuHasPlayback",
                impactRows[0]);
        assertEquals("1,0,0,1,1,6,100.0,6,100.0,7,8,1,"
                + "0.18031790152968785,0.5962868615831063", simpleRows[1]);
        assertEquals("1,94,94,1,20,6,100.0,6,100.0,7,8,2,"
                + "0.18031790152968785,0.38490114597266045", simpleRows[95]);
        assertEquals("1,98,98,1,24,6,100.0,6,100.0,7,8,6,"
                + "0.18031790152968785,0.1867295282555551", simpleRows[99]);
        assertEquals("1,219,219,1,20,6,100.0,6,100.0,7,8,3,"
                + "0.18031790152968785,0.1867295282555551", simpleRows[220]);
        assertEquals("2,0,220,1,1,6,100.0,6,100.0,7,8,1,"
                + "0.8124149323637591,0.04569649356841665", simpleRows[221]);
        assertEquals("2,219,439,1,20,6,100.0,6,100.0,7,8,3,"
                + "0.8124149323637591,0.2878779148564", simpleRows[440]);
        assertEquals("after_confirm,0,0,2,100.0,4,100.0,0,1", impactRows[1]);
        assertEquals("playback,299,299,2,100.0,4,100.0,0,1", impactRows[300]);
        assertEquals("playback,300,300,2,100.0,4,85.0,0,1", impactRows[301]);
        assertEquals("playback,362,362,2,100.0,2,100.0,0,1", impactRows[363]);
        assertEquals("end_playback,363,363,2,100.0,2,100.0,1,0", impactRows[364]);
        assertEquals("first_idle,364,364,2,100.0,2,100.0,1,0", impactRows[365]);
    }

    @Test public void suicideBattleCaptureRetainsLegalSourceHashesAndImpactBoundary()
            throws Exception {
        assertEquals("e69dce0e6645f466167b05cdabb3ac288c19efa0dacd55717b4e215fb11c5469",
                sha256(bytes("source-suicide-unit.csv")));
        assertEquals("baf13f14a3d0bfc812a2554db54490b4086445310fae80c9ab7b2f2a6a211193",
                sha256(bytes("source-suicide-city.csv")));
        assertEquals("a4eb22a38fd9c7bc2e84667d97f591e1544c9262c48f512ecd909e52d66e2e87",
                sha256(bytes("source-suicide-rng.csv")));
        assertEquals("acfa199a07f51007017f8559a821a87fde26c2540a5e3b5806ea1fb009a556fb",
                sha256(bytes("source-suicide-operations.csv")));
        String[] city = new String(bytes("source-suicide-city.csv"),
                StandardCharsets.UTF_8).split("\\R");
        String[] rng = new String(bytes("source-suicide-rng.csv"),
                StandardCharsets.UTF_8).split("\\R");
        String[] unit = new String(bytes("source-suicide-unit.csv"),
                StandardCharsets.UTF_8).split("\\R");
        assertEquals("364 city ticks plus header", 365, city.length);
        assertEquals("364 RNG ticks plus header", 365, rng.length);
        assertEquals("four roles per tick plus header", 1457, unit.length);
        assertTrue("city callback starts on tick 300",
                city[301].startsWith("300,47,47,18,2,,4,85.0,2,1,98982,1,1,"));
        assertTrue("source explosion attaches at pre-move body pose",
                unit[300 * 4 + 4].startsWith("300,explosion,dying,dead,5,0,-1,"));
        assertTrue("retained explosion detached at handoff",
                unit[362 * 4 + 4].contains(",1,0,,-1,22513,explosion001,1,21,100,"
                        + "206.0,162.20000000000002"));
    }

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
