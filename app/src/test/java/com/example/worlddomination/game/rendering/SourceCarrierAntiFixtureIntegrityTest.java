package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import static org.junit.Assert.*;

/** Pins two legal observer-safe carrier captures and the explicit host alias replay. */
public final class SourceCarrierAntiFixtureIntegrityTest {
    private static final String PREFIX = "source-carrier-anti-";

    @Test public void legalShipCapturesPinSourceAndPhysicalSmokeBoundaries() throws Exception {
        assertEquals("755cc2fdd9fa8c593789516f35bfc6a46e8ca8d8ba7e96fd5560ffed020ebb0f",
                sha(resource(PREFIX + "provenance.json")));
        JsonValue provenance = read(PREFIX + "provenance.json");
        assertEquals("fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1",
                provenance.getString("sourceSwfSha256"));
        assertEquals("938786d48f2de6560dcdeefad34b7d4d5ff19153a6b119401cb331802f219a98",
                provenance.get("source").getString("aliasProbe.py"));
        check(provenance, "intercept", 1, 482, 195, 239, "unit3",
                423.52740930490853d, 92.79671912485887d, 800126, 800127,
                "12f924785b592629e2b5024738e81bf819fa19b12da3bbaccb57bd13f1fdb4ba",
                "6a562c6178c779b3e4f72eabbed36b2dde0be68e88c228df0615a4278403748b",
                "9675e3fe4e1d58ae17ebd25ece9f2131394f8a5c4bc785ca95e1de9dfdc932bd");
        check(provenance, "success", 2, 565, 234, 278, null,
                457.39316740917997d, 77.36320399322697d, 800125, 800126,
                "97fead78f43161292618c8a9d4e29b185530aca5cb303c83895495129c0c075a",
                "7fe5139ca49151743fdbcbef69a39410a6795966a3a349af87a5afad493a628b",
                "3b33dc0ca172385f9175d82f922ca1dfdecad7af216477cb104ad495ba0f571f");
    }

    private static void check(JsonValue provenance, String kind, int target,
                              int lastTick, int activation, int birth, String tracked,
                              double x, double y, int literalDepth, int repairedDepth,
                              String fixtureHash, String ticksHash,
                              String aliasRawHash) throws Exception {
        String name = PREFIX + kind + ".json";
        assertEquals(fixtureHash, provenance.get("fixtures").getString(name));
        assertEquals(fixtureHash, sha(resource(name)));
        JsonValue original = provenance.get("source").get(kind);
        assertEquals(ticksHash,
                original.getString("source-bio-ship-" + kind + "-ticks.csv"));
        assertEquals(ticksHash, original.getString("aliasTicksSha256"));
        assertEquals(aliasRawHash, original.getString("aliasRawSha256"));
        JsonValue source = read(name);
        assertEquals(1, source.getInt("schema"));
        assertEquals(0, source.getInt("seed"));
        assertEquals(21, source.getInt("turn"));
        assertEquals(target, source.getInt("target"));
        assertEquals(lastTick, source.getInt("lastTick"));
        assertEquals(lastTick + 1, source.get("rng").size);
        assertEquals(activation, source.getInt("recordActivationTick"));
        assertEquals(birth, source.getInt("firstUnit3TrailTick"));
        if (tracked == null) assertTrue(source.get("trackedUnitAtActivation").isNull());
        else assertEquals(tracked, source.getString("trackedUnitAtActivation"));
        assertTrue(source.getString("compatibilityRepair").contains("tank_noShade"));
        assertEquals(x, source.get("firstUnit3Trail").getDouble("x"), 0d);
        assertEquals(y, source.get("firstUnit3Trail").getDouble("y"), 0d);
        assertEquals(literalDepth,
                source.get("firstUnit3TrailLiteral").getInt("depth"));
        assertEquals(repairedDepth, source.get("firstUnit3Trail").getInt("depth"));
    }

    private static JsonValue read(String name) throws Exception {
        return new JsonReader().parse(new String(resource(name), java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream input = SourceCarrierAntiFixtureIntegrityTest.class
                .getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, input);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
