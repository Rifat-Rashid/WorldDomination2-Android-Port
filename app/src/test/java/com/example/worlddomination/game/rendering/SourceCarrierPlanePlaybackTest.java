package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Source Unit/View poses for all three legal ship payload wrappers each frame. */
public final class SourceCarrierPlanePlaybackTest {
    @Test public void successfulThreePayloadShipMatchesAllSourceUnitFrames()
            throws Exception {
        assertShip("source-bio-ship-success-units.csv", 2, false, 234,
                502, new int[]{296, 339, 375});
    }

    @Test public void interceptedThreePayloadShipMatchesAllSourceUnitFrames()
            throws Exception {
        assertShip("source-bio-ship-intercept-units.csv", 1, true, 195,
                419, new int[]{238, 249, 269});
    }

    @Test public void oneAndTwoPayloadsUseSourceConstructionOffsets() {
        // Legal seed-0 UI captures in analysis/bio-source-audit:
        // count 1: source unit1 at (458.5,68.75);
        // count 2: source unit1 at (421.5,91.75), unit2 at (458.5,68.75).
        // Battle.py:430-463 enumerates ASArray keys in reverse while its
        // ship_plane_start_arr counter increases once per attached payload.
        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        SourceCarrierPlanePlayback one = new SourceCarrierPlanePlayback(
                FlashBattleRoutes.createCarrierPlaneStrike(0, 1, 0, 0, false),
                battleRandom, 1);
        SourceCarrierPlanePlayback firstOfTwo = new SourceCarrierPlanePlayback(
                FlashBattleRoutes.createCarrierPlaneStrike(0, 1, 0, 0, false),
                battleRandom, 2);
        SourceCarrierPlanePlayback secondOfTwo = new SourceCarrierPlanePlayback(
                FlashBattleRoutes.createCarrierPlaneStrike(0, 1, 1, 1, false),
                battleRandom, 2);
        assertEquals(458.5d, one.getX(), 0d);
        assertEquals(68.75d, one.getY(), 0d);
        assertEquals(421.5d, firstOfTwo.getX(), 0d);
        assertEquals(91.75d, firstOfTwo.getY(), 0d);
        assertEquals(458.5d, secondOfTwo.getX(), 0d);
        assertEquals(68.75d, secondOfTwo.getY(), 0d);
    }

    private void assertShip(String fixtureName, int defender, boolean intercepted,
                            int firstActivatedTick, int lastTick, int[] impactTicks)
            throws Exception {
        String[][][] source = sourceRows(fixtureName, lastTick);
        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        SourceCarrierPlanePlayback[] planes = new SourceCarrierPlanePlayback[3];
        for (int payload = 0; payload < planes.length; payload++) {
            planes[payload] = new SourceCarrierPlanePlayback(
                    FlashBattleRoutes.createCarrierPlaneStrike(0, defender, payload,
                            payload, intercepted), battleRandom);
        }
        int observed = 0;
        for (int tick = 0; tick <= lastTick; tick++) {
            for (int payload = 0; payload < planes.length; payload++) {
                SourceCarrierPlanePlayback plane = planes[payload];
                boolean impact = tick > 0 && plane.tickSourceFrame(
                        tick >= firstActivatedTick);
                assertEquals("payload " + payload + " impact tick " + tick,
                        tick == impactTicks[payload], impact);
                String[] row = source[tick][payload];
                if (row == null) {
                    assertTrue("source wrapper absent after removal at " + tick
                            + " payload " + payload, plane.isRemoved());
                    continue;
                }
                observed++;
                String label = fixtureName + " tick " + tick + " payload " + payload;
                assertFalse(label + " wrapper still present", plane.isRemoved());
                assertEquals(label + " action", row[4], plane.getAction());
                assertEquals(label + " logical X", Double.parseDouble(row[5]),
                        plane.getX(), 1e-8d);
                assertEquals(label + " logical Y", Double.parseDouble(row[6]),
                        plane.getY(), 1e-8d);
                assertEquals(label + " painted X", Double.parseDouble(row[7]),
                        plane.getRenderX(), 1e-8d);
                assertEquals(label + " painted Y", Double.parseDouble(row[8]),
                        plane.getRenderY(), 1e-8d);
                assertEquals(label + " MC frame", Integer.parseInt(row[9]),
                        plane.getSourceFrame());
                assertEquals(label + " MC alpha", Double.parseDouble(row[10]),
                        plane.getAlpha(), 1e-8d);
            }
        }
        assertTrue("full source fixture yielded actor observations", observed > 700);
    }

    private String[][][] sourceRows(String file, int lastTick) throws Exception {
        String[][][] rows = new String[lastTick + 1][3][];
        InputStream input = getClass().getClassLoader().getResourceAsStream(file);
        assertNotNull(file, input);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                StandardCharsets.UTF_8))) {
            assertEquals("tick,activeIndex,unitName,mcID,action,curX,curY,mcX,mcY,mcFrame,mcAlpha",
                    reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                assertEquals(file + " columns", 11, values.length);
                if (!values[2].startsWith("unit")) continue;
                int unit;
                try {
                    unit = Integer.parseInt(values[2].substring(4));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (unit < 1 || unit > 3 || !"ship_plane".equals(values[3])) continue;
                int tick = Integer.parseInt(values[0]);
                assertTrue("source tick within fixture", tick <= lastTick);
                assertEquals("one physical source unit per tick", null, rows[tick][unit - 1]);
                rows[tick][unit - 1] = values;
            }
        }
        for (int payload = 0; payload < 3; payload++)
            assertNotNull(file + " initial unit" + (payload + 1), rows[0][payload]);
        return rows;
    }
}
