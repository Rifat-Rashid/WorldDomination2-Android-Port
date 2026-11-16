package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Source Unit/View observations from UI-confirmed rocket attacks with the documented preStart compatibility transfer. */
public final class SourceOrdinaryRocketPlaybackTest {
    private static final double EPS = 0.0000001d;

    @Test
    public void successfulSiloRocketMatchesEveryVisitedUnitAndSmokeFrame() throws Exception {
        replay("success", false, 158, 278, 22511, 341, 1030);
    }

    @Test
    public void interceptedSiloRocketMatchesDoubleKillAndSmokeFrames() throws Exception {
        replay("failed", true, 196, 298, 22523, 646, 1035);
    }

    @Test
    public void compactSourceFixturesRetainCaptureHashesAndBoundaries() throws Exception {
        assertSha("source-silo-rocket-success-ticks.tsv",
                "0f0300a020c10267ce8056e25c8f5d57c8a0a1fe62f6b2783ed34d5768f87096");
        assertSha("source-silo-rocket-failed-ticks.tsv",
                "d9b3cf2ffacf279ee9a5f11af15adde3043592177aabf8198cf51005d2cd2da0");
        assertSha("source-silo-rocket-success-trails.tsv",
                "3bec64bc159808b904123c2c02b8ae31717eb9552973c609358e31fe1144c4b1");
        assertSha("source-silo-rocket-failed-trails.tsv",
                "b481d695a94b135d0df9138cc2f40131afe4b77d73aea7d8f767aeeb9956bfd0");
        assertSha("source-silo-rocket-failed-defense.tsv",
                "72c3d31be23eee597d412618e039ffbcabc42838e2861b72486501979a3b526f");
        assertSha("source-silo-rocket-success-rng-pre-move.json",
                "150a26569a978a432dda6b027a730d2a19e39820bca82e87eeb9dd188719381d");
        assertSha("source-silo-rocket-failed-rng-pre-move.json",
                "11a8806da5b588d787a2766debfbe7a0a641cc1f705d57edcedd7b3217116240");
        assertSha("source-silo-rocket-provenance.json",
                "ca95681471f4972318e63ee8e9a743bf06743363f7c3d97ab985e2ad1e573f20");
        List<Map<String, String>> success = rows("source-silo-rocket-success-ticks.tsv");
        List<Map<String, String>> failed = rows("source-silo-rocket-failed-ticks.tsv");
        assertEquals(341, success.size());
        assertEquals(646, failed.size());
        assertEquals("rocket_explode_city", success.get(276).get("action"));
        assertEquals("False", success.get(277).get("bodyAttached"));
        assertEquals("-1", success.get(278).get("activeIndex"));
        assertEquals("rocket_explode_self", failed.get(296).get("action"));
        assertEquals("False", failed.get(297).get("bodyAttached"));
        assertEquals("-1", failed.get(298).get("activeIndex"));
        assertEquals(1030, rows("source-silo-rocket-success-trails.tsv").size());
        assertEquals(1035, rows("source-silo-rocket-failed-trails.tsv").size());
        String provenance = resource("source-silo-rocket-provenance.json");
        assertTrue(provenance.contains("in-memory prep preStart compatibility replay"));
        assertTrue(provenance.contains("7213f06fac4e887050ad89e0d3c9a67f47789096d559af8e08333991f7616629"));
        assertTrue(provenance.contains("24ec582aedf0ca220c18be2930c159fbaff679a2aa0dfd433ae2fd38050d03a0"));
        assertTrue(provenance.contains("08ec88b32755f58cc3c08e9e87c9f08fcc0e8005f388a1556cf4d24d28154504"));
    }

    private void replay(String caseName, boolean intercepted, int firstMove, int finalVisit,
                        int firstSmokeDepth, int fixtureTicks, int trailRows) throws Exception {
        List<Map<String, String>> ticks = rows("source-silo-rocket-" + caseName + "-ticks.tsv");
        List<Map<String, String>> smoke = rows("source-silo-rocket-" + caseName + "-trails.tsv");
        assertEquals(fixtureTicks, ticks.size());
        assertEquals(trailRows, smoke.size());
        Map<Integer, List<Map<String, String>>> smokeAt = new HashMap<>();
        for (Map<String, String> row : smoke) {
            int tick = integer(row, "tick");
            smokeAt.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(row);
        }
        ReferenceRandom random = new ReferenceRandom(0L);
        restoreSourceRandom(random, "source-silo-rocket-" + caseName + "-rng-pre-move.json");
        FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createRocketStrike(
                0, 1, 2, 1, intercepted);
        SourceRocketPlayback core = SourceRocketPlayback.forOrdinary(route, random);
        core.setTrailDepthBase(firstSmokeDepth);
        assertEquals(396.25d, core.getX(), EPS);
        assertEquals(356.25d, core.getY(), EPS);
        assertEquals(0d, core.getRenderX(), 0d);
        assertEquals(0d, core.getRenderY(), 0d);
        assertEquals(1, core.getSourceFrame());
        int impactCount = 0;
        for (int tick = firstMove; tick <= finalVisit; tick++) {
            Map<String, String> source = ticks.get(tick);
            assertEquals(tick, integer(source, "tick"));
            // The defender's position is assigned by View after its first moving
            // visit (failed global tick 196), and is used on the next visit.
            if (intercepted && tick == 197) core.setAntiPosition(130.75d, 91.75d);
            if (core.tickOrdinary()) {
                impactCount++;
                if (!intercepted) {
                    // This pure Unit replay excludes the World city callback.
                    // Replay its documented source boundary: one dust-name draw,
                    // then View.createBioCloud's 17 initial child spawns. The real
                    // World test must prove the callback and its own city flags.
                    random.nextInt(90000);
                    BioloadCloudAnimation.fromSource(random);
                }
            }
            assertEquals(caseName + " action at " + tick, source.get("action"), core.getAction());
            assertEquals(caseName + " status at " + tick, source.get("status"), core.getUnitStatus());
            same(source, "logicalX", core.getX(), tick);
            same(source, "logicalY", core.getY(), tick);
            same(source, "renderX", core.getRenderX(), tick);
            same(source, "renderY", core.getRenderY(), tick);
            assertEquals(caseName + " body at " + tick,
                    Boolean.parseBoolean(source.get("bodyAttached")), core.isBodyAttached());
            assertEquals(caseName + " body frame at " + tick,
                    integer(source, "bodyFrame"), core.getSourceFrame());
            // Frame3 replaces the named maskMC; it has no stored X/Y to read.
            if (core.getSourceFrame() == 1) {
                same(source, "maskX", -133.2d + core.getMaskOffsetX(), tick);
            } else {
                assertEquals("", source.get("maskX"));
                assertEquals("", source.get("maskY"));
            }
            same(source, "xStep", core.getXStep(), tick);
            same(source, "yStep", core.getYStep(), tick);
            same(source, "ellipseAngle", core.getEllipseAngle(), tick);
            assertEquals(caseName + " smoke count at " + tick,
                    integer(source, "attachedTrailCount"), core.getAttachedTrailCount());
            List<Map<String, String>> expected = smokeAt.get(tick);
            boolean[] attached = new boolean[core.getTrailSlotCount()];
            if (expected != null) for (Map<String, String> trail : expected) {
                int slot = integer(trail, "slot");
                attached[slot] = true;
                assertTrue(caseName + " slot " + slot + " at " + tick, core.isTrailAttached(slot));
                assertEquals(caseName + " depth " + slot + " at " + tick,
                        integer(trail, "depth"), core.getTrailDepth(slot));
                same(trail, "x", core.getTrailX(slot), tick);
                same(trail, "y", core.getTrailY(slot), tick);
                same(trail, "alpha", core.getTrailAlpha(slot), tick);
                same(trail, "scaleX", core.getTrailScalePercent(slot), tick);
                same(trail, "scaleY", core.getTrailScalePercent(slot), tick);
                same(trail, "sizePlus", core.getTrailSizePlus(slot), tick);
                same(trail, "alphaMin", core.getTrailAlphaMin(slot), tick);
            }
            for (int slot = 0; slot < attached.length; slot++)
                assertEquals(caseName + " unattached slot " + slot + " at " + tick,
                        attached[slot], core.isTrailAttached(slot));
            same(source, "sourcePrivateRngNext", random.copy().nextDouble(), tick);
        }
        assertTrue(core.isRemoved());
        assertFalse(core.isBodyAttached());
        assertEquals(intercepted ? 2 : 1, impactCount);
        assertEquals(intercepted ? 2 : 0, core.getSelfExplosionCount());
    }

    private static void same(Map<String, String> row, String field, double actual, int tick) {
        assertEquals(field + " at source tick " + tick,
                Double.parseDouble(row.get(field)), actual, EPS);
    }

    private static int integer(Map<String, String> row, String field) {
        return Integer.parseInt(row.get(field));
    }

    private List<Map<String, String>> rows(String path) throws Exception {
        String[] lines = resource(path).split("\n");
        String[] header = lines[0].split("\t", -1);
        List<Map<String, String>> records = new ArrayList<>();
        for (int line = 1; line < lines.length; line++) {
            if (lines[line].isEmpty()) continue;
            String[] cells = lines[line].split("\t", -1);
            assertEquals(path + " row " + line, header.length, cells.length);
            Map<String, String> row = new HashMap<>();
            for (int column = 0; column < header.length; column++) row.put(header[column], cells[column]);
            records.add(row);
        }
        return records;
    }

    private String resource(String path) throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(path, stream);
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        }
        return text.toString();
    }

    private void assertSha(String path, String expected) throws Exception {
        byte[] data;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(path, stream);
            data = stream.readAllBytes();
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        assertEquals(path, expected, hex.toString());
    }

    private void restoreSourceRandom(ReferenceRandom random, String path) throws Exception {
        String json = resource(path);
        int start = json.indexOf("\"stateWords\":[") + "\"stateWords\":[".length();
        int end = json.indexOf(']', start);
        assertTrue(start > 0 && end > start);
        String[] words = json.substring(start, end).split(",");
        assertEquals(624, words.length);
        Field stateField = ReferenceRandom.class.getDeclaredField("state");
        stateField.setAccessible(true);
        int[] state = (int[]) stateField.get(random);
        for (int index = 0; index < words.length; index++)
            state[index] = (int) Long.parseLong(words[index].trim());
        start = json.indexOf("\"stateIndex\":", end) + "\"stateIndex\":".length();
        end = json.indexOf(',', start);
        Field indexField = ReferenceRandom.class.getDeclaredField("stateIndex");
        indexField.setAccessible(true);
        indexField.setInt(random, Integer.parseInt(json.substring(start, end).trim()));
    }
}
