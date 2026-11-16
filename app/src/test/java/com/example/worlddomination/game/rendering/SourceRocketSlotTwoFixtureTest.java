package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

/** Independent View capture; the full shared battle stream is checked on Android. */
public final class SourceRocketSlotTwoFixtureTest {
    @Test public void sourceBytesAndCompatibilityBoundaryArePinned() throws Exception {
        assertSha("ticks.csv", "cd0d51feb865418e28d0fae4d41412bca9832b47f61c60631173100b19fef112");
        assertSha("smoke.csv", "c403c73735c375197c55ae06a7570ad36d2135f8c605b8d43cf696a90fa51a9f");
        assertSha("provenance.json", "a79276621d40b97be5fb1b551b918b5f7c88c6f2e7e44a5042110ca50f46aa6f");
        List<Map<String,String>> ticks = rows("ticks.csv");
        assertEquals(366, ticks.size());
        assertEquals(990, rows("smoke.csv").size());
        assertEquals("False", ticks.get(196).get("moving"));
        assertEquals("True", ticks.get(197).get("moving"));
        assertEquals("rocket_follow_ellipse", ticks.get(238).get("action"));
        assertEquals("3", ticks.get(239).get("mcFrame"));
        assertEquals("True", ticks.get(302).get("effectAttached"));
        assertEquals("22511", ticks.get(302).get("effectDepth"));
        assertEquals("-1", ticks.get(303).get("activeIndex"));
        assertEquals("False", ticks.get(365).get("effectAttached"));
        String provenance = new String(bytes("provenance.json"), StandardCharsets.UTF_8);
        assertTrue(provenance.contains("\"otherStoredFields\": 6230"));
        assertTrue(provenance.contains("\"otherStoredFieldIdentitiesUnchanged\": true"));
        assertTrue(provenance.contains("\"canonicalRngStateUnchanged\": true"));
        assertTrue(provenance.contains("compatibility: only prep View preStart and preLocation"));
    }

    @Test public void routeUsesTheActualSecondSiloAndPreparedStart() {
        FlashBattleRoutes.RocketStrike route = route(2);
        assertEquals(2, route.actionBuildingSlot);
        assertEquals(432.25f, route.silo.x, 0f);
        assertEquals(296.75f, route.silo.y, 0f);
        assertEquals(442.25f, route.launch.x, 0f);
        assertEquals(300.75f, route.launch.y, 0f);
        assertEquals(212.8f, route.target.x, 0f);
        assertEquals(166.75f, route.target.y, 0f);
        assertEquals(126.75f, route.ellipseCenterY, 0f);
        FlashBattleRoutes.RocketStrike wrong = route(1);
        assertEquals(396.25f, wrong.launch.x, 0f);
        assertEquals(356.25f, wrong.launch.y, 0f);
        assertNotEquals(wrong.launch.x, route.launch.x, 0f);
        assertNotEquals(wrong.launch.y, route.launch.y, 0f);
    }

    @Test public void geometryMatchesAll366CapturedActivationVisits() throws Exception {
        SourceRocketPlayback core = SourceRocketPlayback.forOrdinary(route(2), new ReferenceRandom(0));
        List<Map<String,String>> ticks = rows("ticks.csv");
        for (int tick = 0; tick < ticks.size(); tick++) {
            Map<String,String> row = ticks.get(tick);
            if (tick > 0) {
                if (Boolean.parseBoolean(row.get("moving"))) core.tickOrdinary();
                else core.visitWaitingOrdinary();
            }
            String context = "source host " + tick;
            assertEquals(context, row.get("action"), core.getAction());
            assertEquals(context, row.get("status"), core.getUnitStatus());
            number(row, "x", core.getX(), context); number(row, "y", core.getY(), context);
            number(row, "previousX", core.getPreviousX(), context);
            number(row, "previousY", core.getPreviousY(), context);
            number(row, "xStep", core.getXStep(), context); number(row, "yStep", core.getYStep(), context);
            number(row, "ellipseAngle", core.getEllipseAngle(), context);
            number(row, "mcX", core.getRenderX(), context); number(row, "mcY", core.getRenderY(), context);
            // MovieClip._rotation reads a matrix-normalized angle; the Java Unit
            // stores the assigned angle (for example -271 rather than 89).
            number(row, "rotation", normalizedRotation(core.getRotation()), context);
            assertEquals(context, Integer.parseInt(row.get("angle")), core.getRawAngle());
            assertEquals(context, Integer.parseInt(row.get("mcFrame")), core.getSourceFrame());
            assertEquals(context, Boolean.parseBoolean(row.get("attached")), core.isBodyAttached());
            assertEquals(context, Integer.parseInt(row.get("activeIndex")) >= 0, !core.isRemoved());
            if (Boolean.parseBoolean(row.get("effectAttached"))) {
                number(row, "effectX", core.getImpactEffectX(), context);
                number(row, "effectY", core.getImpactEffectY(), context);
            }
        }
        // This isolates geometry at captured activation boundaries. Other source
        // actors consume the shared stream, so World tests own smoke/RNG timing.
    }

    private static double normalizedRotation(double value) {
        value %= 360d;
        if (value < -180d) value += 360d;
        if (value > 180d) value -= 360d;
        return value;
    }

    private static FlashBattleRoutes.RocketStrike route(int slot) {
        return FlashBattleRoutes.createRocketStrike(0, 1, 2, slot, false);
    }
    private static void number(Map<String,String> row, String key, double actual, String context) {
        assertEquals(context + " " + key, Double.parseDouble(row.get(key)), actual, 1e-7);
    }
    private static byte[] bytes(String suffix) throws Exception {
        try (InputStream in = SourceRocketSlotTwoFixtureTest.class.getClassLoader()
                .getResourceAsStream("source-silo-slot2-" + suffix)) {
            assertNotNull(suffix, in); return in.readAllBytes();
        }
    }
    private static void assertSha(String suffix, String expected) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes(suffix));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        assertEquals(suffix, expected, result.toString());
    }
    private static List<Map<String,String>> rows(String suffix) throws Exception {
        String[] lines = new String(bytes(suffix), StandardCharsets.UTF_8).split("\r?\n");
        String[] header = lines[0].split(",", -1); List<Map<String,String>> result = new ArrayList<>();
        for (int line = 1; line < lines.length; line++) {
            String[] fields = lines[line].split(",", -1); assertEquals(header.length, fields.length);
            Map<String,String> row = new HashMap<>();
            for (int i = 0; i < header.length; i++) row.put(header[i], fields[i]);
            result.add(row);
        }
        return result;
    }
}
