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
