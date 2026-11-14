package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Exact first Unit.updateTrail births from four legal source battle captures. */
public final class SourceAircraftTrailTest {
    @Test public void planeBiologicalFirstSmokeUsesSourceStreamAndPreviousPose()
            throws Exception {
        assertFirstBirth("plane1", SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION,
                238, 22513, 0.976229457649057d);
    }

    @Test public void planeWarheadFirstSmokeUsesSameSourceFormula() throws Exception {
        assertFirstBirth("plane1-warhead1",
                SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION, 238, 22513,
                0.4031691464450935d);
    }

    @Test public void interceptedCarrierFirstSmokeUsesPriorAngle() throws Exception {
        assertFirstBirth("ship-intercept", SourceAircraftTrail.Origin.SHIP_OFFSET,
                200, 800026, 0.16321128139375318d);
    }

    @Test public void successfulCarrierFirstSmokeUsesPriorAngle() throws Exception {
        assertFirstBirth("ship-success", SourceAircraftTrail.Origin.SHIP_OFFSET,
                239, 800026, 0.31943720121332564d);
    }

    @Test public void taxiSuppressesTrailCallbackButNotActiveFrameCounter() {
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION,
                new ReferenceRandom(0L));
        for (int frame = 0; frame < 5; frame++)
            trail.visit(true, "taxi_a", 0d, 0d, 0d, 0d, 0d);
        assertEquals(5, trail.getFramesActive());
        assertTrue(trail.isTrailActive());
        assertEquals(0, trail.getCreatedCount());
        trail.visit(false, "default", 0d, 0d, 0d, 0d, 0d);
        assertEquals(5, trail.getFramesActive());
        trail.visit(true, "default", 3d, 4d, 5d, 6d, 0d);
        assertEquals(3, trail.getCreatedCount());
        trail.removeTrail();
        assertEquals(0, trail.getAttachedCount());
        assertTrue(trail.isCreated(0));
        assertFalse(trail.isAttached(0));
    }

    private void assertFirstBirth(String scenario, SourceAircraftTrail.Origin origin,
                                  int sourceTick, int firstDepth,
                                  double firstSourceDraw) throws Exception {
        ReferenceRandom random = new ReferenceRandom(0L);
        restoreState(random, read("source-aircraft-trail-" + scenario
                + "-rng-pretrail.json"));
        assertEquals("source first Tools.randRange draw", firstSourceDraw,
                random.copy().nextDouble(), 0d);
        int startStateIndex = stateIndex(random);
        SourceAircraftTrail trail = new SourceAircraftTrail(origin, random);
        for (int frame = 1; frame <= 4; frame++)
            trail.tick(0d, 0d, 0d, 0d, 0d);
        assertEquals(0, trail.getCreatedCount());

        JsonValue samples = read("source-aircraft-trail-" + scenario
                + "-trail-samples.json");
        JsonValue first = null;
        JsonValue prior = null;
        for (JsonValue row = samples.child; row != null; row = row.next) {
            if (!"unit1".equals(row.getString("unitName"))) continue;
            int tick = row.getInt("tick");
            if (tick == sourceTick - 1) prior = row;
            if (tick == sourceTick && row.getInt("framesActive") == 5) first = row;
        }
        assertNotNull("source first smoke row", first);
        assertNotNull("source previous angle row", prior);
        assertEquals(1, first.getInt("trailCount"));
        assertEquals(50, first.getInt("trailSize"));
        assertEquals(3, first.get("children").size);

        // View computes the current angle after Unit.moveUnit/updateTrail.
        // The carrier branch therefore uses the previous View angle.
        trail.tick(first.getDouble("prevX"), first.getDouble("prevY"),
                first.getDouble("curX"), first.getDouble("curY"),
                prior.getDouble("angle"));
        assertEquals(5, trail.getFramesActive());
        assertEquals(3, trail.getCreatedCount());
        assertEquals(3, trail.getAttachedCount());
        int index = 0;
        for (JsonValue child = first.get("children").child;
             child != null; child = child.next, index++) {
            assertEquals("source trail linkage", 4938, child.getInt("characterId"));
            assertEquals("source first depths", firstDepth + index,
                    child.getInt("depth"));
            trail.setDepth(index, child.getInt("depth"));
            assertEquals(child.getInt("depth"), trail.getDepth(index));
            assertEquals("source trail x " + scenario + " slot " + index,
                    child.getDouble("x"), trail.getX(index), 1e-10d);
            assertEquals("source trail y " + scenario + " slot " + index,
                    child.getDouble("y"), trail.getY(index), 1e-10d);
            assertEquals("source trail alpha " + scenario + " slot " + index,
                    child.getDouble("alpha"), trail.getAlpha(index), 1e-10d);
            assertEquals("source trail width " + scenario + " slot " + index,
                    child.getDouble("width"), trail.getWidth(index), 1e-10d);
            assertEquals("source trail height " + scenario + " slot " + index,
                    child.getDouble("height"), trail.getHeight(index), 1e-10d);
            assertEquals(child.getDouble("sizePlus"), trail.getSizePlus(index), 0d);
            assertEquals(child.getDouble("alphaMin"), trail.getAlphaMin(index), 0d);
        }
        assertEquals(3, index);
        assertEquals("three slots consume five Math.random draws each",
                startStateIndex + 30, stateIndex(random));
    }

    private JsonValue read(String path) throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(path, input);
        try (InputStream stream = input;
             InputStreamReader reader = new InputStreamReader(stream,
                     StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }

    private static void restoreState(ReferenceRandom random, JsonValue snapshot)
            throws Exception {
        JsonValue mt = snapshot.get("mtState");
        assertEquals("Python MT state words and position", 625, mt.size);
        Field wordsField = ReferenceRandom.class.getDeclaredField("state");
        wordsField.setAccessible(true);
        int[] words = (int[]) wordsField.get(random);
        for (int index = 0; index < 624; index++)
            words[index] = (int) mt.get(index).asLong();
        Field indexField = ReferenceRandom.class.getDeclaredField("stateIndex");
        indexField.setAccessible(true);
        indexField.setInt(random, mt.get(624).asInt());
    }

    private static int stateIndex(ReferenceRandom random) throws Exception {
        Field index = ReferenceRandom.class.getDeclaredField("stateIndex");
        index.setAccessible(true);
        return index.getInt(random);
    }
}
