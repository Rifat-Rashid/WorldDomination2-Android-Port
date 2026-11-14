package com.example.worlddomination.game.rendering;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/** Real, legal source plane001 visits; the payload does not alter its flight. */
public final class SourceAircraftPlaybackTest {
    @Test public void biologicalPlaneMatchesEverySourceFlightFrame() throws IOException {
        compare("source-bio-plane1-units.csv");
    }

    @Test public void warheadPlaneUsesTheSameSourceFlight() throws IOException {
        compare("source-bio-plane1-warhead1-units.csv");
    }

    private static void compare(String file) throws IOException {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 2, 1,
                        "plane1", false);
        assertEquals(327d, route.liftOff.x, .001d);
        assertEquals(246d, route.liftOff.y, .001d);
        assertEquals(173d, route.orbitEntry.x, .001d);
        assertEquals(298d, route.orbitEntry.y, .001d);
        SourceAircraftPlayback plane = new SourceAircraftPlayback(route, null);
        Path resource = Paths.get("app", "src", "test", "resources", file);
        if (!Files.isRegularFile(resource)) resource = Paths.get("src", "test", "resources", file);
        int nextTick = 0;
        int visitedRows = 0;
        try (BufferedReader reader = Files.newBufferedReader(resource,
                StandardCharsets.UTF_8)) {
            assertEquals("tick,activeIndex,unitName,mcID,action,curX,curY,mcX,mcY,mcFrame,mcAlpha",
                    reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split(",", -1);
                assertEquals(11, row.length);
                if (!"unit1".equals(row[2])) continue;
                int tick = Integer.parseInt(row[0]);
                assertEquals("one source unit1 row per tick", nextTick++, tick);
                if (tick > 0 && tick < 234) plane.presentWaitingFrame();
                if (tick >= 234) plane.tickSourceFrame();
                assertEquals("source action t" + tick, row[4], plane.getAction());
                assertNumber("logical X t" + tick, row[5], plane.getX());
                assertNumber("logical Y t" + tick, row[6], plane.getY());
                assertNumber("display X t" + tick, row[7], plane.getRenderX());
                assertNumber("display Y t" + tick, row[8], plane.getRenderY());
                assertEquals("source pose frame t" + tick,
                        Integer.parseInt(row[9]), plane.getFrame());
                assertEquals("raw source alpha t" + tick,
                        Double.parseDouble(row[10]), plane.getAlpha(), .001d);
                if (tick == 241) {
                    assertEquals("source plane first growth", 52d,
                            plane.getScalePercent(), 1e-10);
                    assertEquals("shadow still at runway", 0d,
                            plane.getShadowYOffset(), 0d);
                }
                if (tick == 255) assertEquals("source matrix scale before final step",
                        79.99999999999997d, plane.getScalePercent(), 1e-10);
                if (tick == 256) assertEquals("source matrix permits final growth",
                        81.99999999999996d, plane.getScalePercent(), 1e-10);
                if (tick == 342) assertEquals("source flight shadow ceiling",
                        90d, plane.getShadowYOffset(), 0d);
                assertEquals("impact pulse t" + tick,
                        tick == 343, plane.takeImpact());
                assertFalse("wrapper still active t" + tick, plane.isRemoved());
                visitedRows++;
            }
        }
        assertEquals("source wrapper through stop frame", 471, visitedRows);
        assertEquals("stop at t470", "stop", plane.getAction());
        plane.tickSourceFrame(); // source View removes the wrapper at t471.
        assertTrue(plane.isRemoved());
    }

    private static void assertNumber(String label, String expected, double actual) {
        if ("nan".equalsIgnoreCase(expected)) assertTrue(label, Double.isNaN(actual));
        else assertEquals(label, Double.parseDouble(expected), actual, .02d);
    }
}
