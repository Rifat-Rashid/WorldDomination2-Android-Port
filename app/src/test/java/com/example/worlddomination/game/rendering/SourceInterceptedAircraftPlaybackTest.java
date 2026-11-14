package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Legal seed-zero turn 22: intercepted plane1 versus nation 1's city 0. */
public final class SourceInterceptedAircraftPlaybackTest {
    @Test public void interceptedPlaneMatchesEverySourceUnitFrame() throws Exception {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 0, 1,
                        "plane1", true);
        assertTrue(route.intercepted);
        assertEquals(route.target.x, route.bombWaypoint.x, 0d);
        assertEquals(route.target.y, route.bombWaypoint.y, 0d);
        ReferenceRandom smokeRandom = new ReferenceRandom(0);
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION,
                smokeRandom);
        SourceAircraftPlayback plane = new SourceAircraftPlayback(route, trail);
        Path source = Paths.get("app", "src", "test", "resources",
                "source-intercepted-plane001-units.csv");
        if (!Files.isRegularFile(source)) source = Paths.get("src", "test", "resources",
                "source-intercepted-plane001-units.csv");
        Path stateSource = source.resolveSibling("source-intercepted-plane001-state.csv");
        Path rngSource = source.resolveSibling("source-intercepted-plane001-rng.json");
        JsonValue sourceRng = new JsonReader().parse(Files.readString(rngSource,
                StandardCharsets.UTF_8));
        int rows = 0;
        int createdAtDeath = -1;
        try (BufferedReader reader = Files.newBufferedReader(source,
                StandardCharsets.UTF_8);
             BufferedReader stateReader = Files.newBufferedReader(stateSource,
                     StandardCharsets.UTF_8)) {
            String[] names = reader.readLine().split(",", -1);
            Map<String, Integer> columns = new HashMap<>();
            for (int index = 0; index < names.length; index++) columns.put(names[index], index);
            String[] stateNames = stateReader.readLine().split(",", -1);
            Map<String, Integer> stateColumns = new HashMap<>();
            for (int index = 0; index < stateNames.length; index++)
                stateColumns.put(stateNames[index], index);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split(",", -1);
                String[] state = stateReader.readLine().split(",", -1);
                int tick = Integer.parseInt(row[columns.get("tick")]);
                assertEquals("continuous source ticks", rows++, tick);
                assertEquals("enriched source tick", tick,
                        Integer.parseInt(state[stateColumns.get("tick")]));
                if (tick > 0 && tick < 196) plane.presentWaitingFrame();
                if (tick == 200) restorePythonState(smokeRandom,
                        sourceRng.get("isolatedAfterTick199"));
                if (tick >= 196 && tick <= 315) plane.tickSourceFrame();
                assertEquals("action t" + tick, row[columns.get("action")], plane.getAction());
                assertNumber("logical X t" + tick, row[columns.get("curX")], plane.getX());
                assertNumber("logical Y t" + tick, row[columns.get("curY")], plane.getY());
                assertNumber("MC X t" + tick, row[columns.get("mcX")], plane.getRenderX());
                assertNumber("MC Y t" + tick, row[columns.get("mcY")], plane.getRenderY());
                assertEquals("body frame t" + tick,
                        Integer.parseInt(row[columns.get("mcFrame")]), plane.getFrame());
                assertEquals("body attachment t" + tick,
                        Boolean.parseBoolean(row[columns.get("bodyAttached")]),
                        plane.isBodyAttached());
                assertEquals("source Unit status t" + tick,
                        row[columns.get("status")], plane.getUnitStatus());
                assertEquals("active wrapper t" + tick,
                        Boolean.parseBoolean(row[columns.get("active")]),
                        !plane.isRemoved());
                assertEquals("source moving flag t" + tick,
                        Boolean.parseBoolean(row[columns.get("moving")]),
                        plane.isActiveAndMoving());
                assertNumber("previous X t" + tick,
                        state[stateColumns.get("previousX")], plane.getPreviousX());
                assertNumber("previous Y t" + tick,
                        state[stateColumns.get("previousY")], plane.getPreviousY());
                assertNumber("X step t" + tick,
                        state[stateColumns.get("xStep")], plane.getXStep());
                assertNumber("Y step t" + tick,
                        state[stateColumns.get("yStep")], plane.getYStep());
                assertEquals("active frame count t" + tick,
                        Integer.parseInt(state[stateColumns.get("framesActive")]),
                        plane.getActiveFrames());
                assertNumber("body alpha t" + tick,
                        state[stateColumns.get("mcAlpha")], plane.getAlpha());
                assertNumber("body scale t" + tick,
                        state[stateColumns.get("mcScaleX")], plane.getScalePercent());
                assertNumber("shadow offset t" + tick,
                        state[stateColumns.get("shadowYOffset")], plane.getShadowYOffset());
                assertEquals("death pulse t" + tick, tick == 314, plane.takeDeath());
                assertFalse("intercept has no city bomb pulse t" + tick,
                        plane.takeImpact());
                if (tick == 200) assertEquals("source first smoke birth", 3,
                        trail.getCreatedCount());
                if (tick == 200 || tick == 201) {
                    assertEquals("source first smoke cohorts t" + tick,
                            Integer.parseInt(state[stateColumns.get("trailSlots")]),
                            trail.getAttachedCount());
                    assertEquals("shared source RNG after smoke t" + tick,
                            Double.parseDouble(state[stateColumns.get("rngNext")]),
                            smokeRandom.copy().nextDouble(), 0d);
                }
                if (tick == 314) {
                    assertEquals("effect uses pre-move logical X",
                            48.27275153669018d, plane.getDeathEffectX(), .001d);
                    assertEquals("effect uses pre-move logical Y",
                            254.7482852266179d, plane.getDeathEffectY(), .001d);
                    assertEquals("sound pans from pre-move MC X",
                            48.27275153669018d, plane.getDeathSoundX(), .001d);
                    assertFalse("death pulse is one shot", plane.takeDeath());
                    assertEquals("kill detaches smoke immediately", 0,
                            trail.getAttachedCount());
                    createdAtDeath = trail.getCreatedCount();
                }
                if (tick >= 315) {
                    assertEquals("no smoke is born after body removal",
                            createdAtDeath, trail.getCreatedCount());
                    assertEquals(0, trail.getAttachedCount());
                }
            }
        }
        assertEquals("source rows 0..377", 378, rows);
        assertTrue(plane.isRemoved());
        assertEquals("dying", plane.getAction());
    }

    private static void restorePythonState(ReferenceRandom random, JsonValue source)
            throws ReflectiveOperationException {
        JsonValue words = source.get("state");
        // Python getstate() stores 624 MT words followed by the state index.
        assertEquals(625, words.size);
        assertEquals(words.getInt(624), source.getInt("index"));
        Field state = ReferenceRandom.class.getDeclaredField("state");
        Field index = ReferenceRandom.class.getDeclaredField("stateIndex");
        state.setAccessible(true);
        index.setAccessible(true);
        int[] target = (int[]) state.get(random);
        for (int position = 0; position < target.length; position++)
            target[position] = (int) words.getLong(position);
        index.setInt(random, source.getInt("index"));
    }

    private static void assertNumber(String label, String expected, double actual) {
        if ("nan".equalsIgnoreCase(expected)) assertTrue(label, Double.isNaN(actual));
        else assertEquals(label, Double.parseDouble(expected), actual, .02d);
    }
}
