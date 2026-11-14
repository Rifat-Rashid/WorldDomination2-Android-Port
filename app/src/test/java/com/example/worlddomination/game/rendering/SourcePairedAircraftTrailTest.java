package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Unit.updateTrail's two-clip branch; first rows are from a legal plane002 battle. */
public final class SourcePairedAircraftTrailTest {
    private static final String FIRST_PAIRS_SHA =
            "282633e25f056ec74a7f955ba3f497c775364d48238417672e115d4993233730";

    @Test public void firstTwoPairsMatchLegalSourceHostAndSharedRandom() throws Exception {
        JsonValue fixture = read("source-plane002-trail-first-pairs.json");
        assertEquals("2213756b9b9911a63467dccd49a760a0783af121754c9fc14049156aac8c6fca",
                fixture.getString("sourceRawSha256"));
        assertEquals("1e21c7bd7d5417d29b52574bb74cd092c6e44d8bac4a47596c5fa810e3425421",
                fixture.getString("captureScriptSha256"));
        assertEquals("4e636a9ec87ab794e59f359ac760793c6e49f9008776ed82fc8964b564606033",
                fixture.getString("observerContaminatedControlSha256"));
        assertEquals("fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1",
                fixture.get("sourceSha256").getString("WorldDomination2.swf"));
        assertTrue(fixture.getString("observerPolicy").contains("never call mutating"));
        assertTrue(fixture.getString("nullPolicy").contains("not inferred"));
        ReferenceRandom random = new ReferenceRandom(0L);
        restoreState(random, fixture.get("preFirstTrailRngState"));
        JsonValue rows = fixture.get("rows");
        assertEquals(4, rows.size);
        assertEquals(271, rows.get(0).getInt("tick"));
        assertEquals(272, rows.get(1).getInt("tick"));
        assertTrue("unvisited source direction is undefined",
                rows.get(0).get("unit").get("frame").isNull());
        assertEquals(10, rows.get(1).get("unit").getInt("frame"));
        assertEquals(22518, rows.get(1).get("trails").get(0).getInt("depth"));
        assertEquals(22519, rows.get(1).get("trails").get(1).getInt("depth"));
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PAIRED_PLANE, random);
        assertTrue(trail.isPaired());
        assertEquals(44, trail.getSlotCount());
        assertEquals("trailLeft0", trail.getClipNameSuffix(0));
        assertEquals("trailRight0", trail.getClipNameSuffix(1));
        assertEquals("trailLeft1", trail.getClipNameSuffix(2));
        assertEquals("trailRight1", trail.getClipNameSuffix(3));

        // View has not published the new Unit angle at the first source visit.
        assertEquals(rows.get(0).getDouble("rngNext"), random.copy().nextDouble(), 0d);
        trail.visit(true, "liftOff", 0d, 0d, 2d, 1d, Double.NaN);
        assertSourcePair(rows.get(1), trail, 0);
        assertEquals(2, trail.getCreatedCount());
        assertEquals(2, trail.getAttachedCount());
        assertEquals(rows.get(1).getDouble("rngNext"), random.copy().nextDouble(), 0d);

        // The second visit uses the angle View published after the first visit.
        trail.visit(true, "liftOff", 2d, 1d, 6d, 3d,
                rows.get(1).get("unit").getDouble("angle"));
        assertSourcePair(rows.get(2), trail, 2);
        assertEquals(4, trail.getCreatedCount());
        assertEquals(2, trail.getAttachedCount());
        assertFalse("right clip expiration detaches its left peer", trail.isAttached(0));
        assertFalse(trail.isAttached(1));
        assertEquals(rows.get(2).getDouble("rngNext"), random.copy().nextDouble(), 0d);
    }

    @Test public void leftExpirationStillUpdatesRightBeforeBothDetach() {
        // Isolated source-branch boundary: seed 22 yields deductions 97/33,
        // growth 1/1, fades 4/1. Left reaches -1 on its birth visit.
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PAIRED_PLANE, new ReferenceRandom(22L));
        trail.tick(0d, 0d, 20d, 30d, 0d);
        assertEquals(2, trail.getCreatedCount());
        assertEquals(0, trail.getAttachedCount());
        assertFalse(trail.isAttached(0));
        assertFalse(trail.isAttached(1));
        assertEquals(-1d, trail.getAlpha(0), 1e-10d);
        assertEquals(66d, trail.getAlpha(1), 1e-10d);
        assertEquals(9d, trail.getWidth(0), 0d);
        assertEquals(9d, trail.getWidth(1), 0d);
        assertEquals("trailLeft0", trail.getClipNameSuffix(0));
        assertEquals("trailRight0", trail.getClipNameSuffix(1));
    }

    @Test public void onePairPerVisitStopsAtTwentyTwoAndRemovalKeepsSlotsOccupied() {
        ReferenceRandom random = new ReferenceRandom(0L);
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PAIRED_PLANE, random);
        for (int frame = 1; frame <= 22; frame++) {
            trail.tick(1d, 2d, 3d, 4d, 30d);
            assertEquals(frame * 2, trail.getCreatedCount());
        }
        double next = random.copy().nextDouble();
        trail.tick(1d, 2d, 3d, 4d, 30d);
        assertEquals(44, trail.getCreatedCount());
        assertEquals(next, random.copy().nextDouble(), 0d);
        trail.removeTrail();
        assertEquals(0, trail.getAttachedCount());
        trail.removeTrail();
        assertEquals(0, trail.getAttachedCount());
        for (int slot = 0; slot < 44; slot++) {
            assertTrue(trail.isCreated(slot));
            assertFalse(trail.isAttached(slot));
        }
    }

    @Test public void legacySingleModeKeepsFiftySlotsAndNames() {
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION,
                new ReferenceRandom(0L));
        assertFalse(trail.isPaired());
        assertEquals(50, trail.getSlotCount());
        assertEquals("trail0", trail.getClipNameSuffix(0));
        for (int frame = 0; frame < 4; frame++) trail.tick(0d, 0d, 0d, 0d, 0d);
        assertEquals(0, trail.getCreatedCount());
        trail.tick(0d, 0d, 0d, 0d, 0d);
        assertEquals(3, trail.getCreatedCount());
    }

    private static void assertSourcePair(JsonValue row, SourceAircraftTrail trail,
                                         int firstSlot) {
        assertEquals(2, row.get("trails").size);
        int slot = firstSlot;
        for (JsonValue source = row.get("trails").child;
             source != null; source = source.next, slot++) {
            assertTrue("source paired slot " + slot, trail.isAttached(slot));
            assertEquals(source.getString("name").substring("unit1".length()),
                    trail.getClipNameSuffix(slot));
            trail.setDepth(slot, source.getInt("depth"));
            assertEquals(source.getInt("depth"), trail.getDepth(slot));
            assertEquals(source.getDouble("_x"), trail.getX(slot), 1e-10d);
            assertEquals(source.getDouble("_y"), trail.getY(slot), 1e-10d);
            assertEquals(source.getDouble("_alpha"), trail.getAlpha(slot), 1e-10d);
            assertEquals(source.getDouble("_sizePlus"), trail.getSizePlus(slot), 0d);
            assertEquals(source.getDouble("_alphaMin"), trail.getAlphaMin(slot), 0d);
        }
        assertEquals(firstSlot + 2, slot);
    }

    private static JsonValue read(String name) throws Exception {
        InputStream stream = SourcePairedAircraftTrailTest.class.getClassLoader()
                .getResourceAsStream(name);
        assertNotNull(name, stream);
        try (InputStream input = stream) {
            byte[] bytes = input.readAllBytes();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) hash.append(String.format("%02x", value & 0xff));
            assertEquals("source fixture bytes cannot silently change", FIRST_PAIRS_SHA,
                    hash.toString());
            return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static void restoreState(ReferenceRandom random, JsonValue snapshot)
            throws Exception {
        JsonValue words = snapshot.get(1);
        assertEquals(625, words.size);
        Field stateField = ReferenceRandom.class.getDeclaredField("state");
        stateField.setAccessible(true);
        int[] state = (int[]) stateField.get(random);
        for (int index = 0; index < 624; index++)
            state[index] = (int) words.get(index).asLong();
        Field indexField = ReferenceRandom.class.getDeclaredField("stateIndex");
        indexField.setAccessible(true);
        indexField.setInt(random, words.get(624).asInt());
    }
}
