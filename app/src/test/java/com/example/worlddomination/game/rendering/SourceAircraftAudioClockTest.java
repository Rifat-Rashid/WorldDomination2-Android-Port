package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Legal Unit clock against source audio fade and owned-sound stop boundaries. */
public final class SourceAircraftAudioClockTest {
    @Test public void successfulPlane001FadesAndStopsBeforeWrapperRemoval()
            throws IOException {
        JsonValue row = tailCase("plane1");
        JsonValue event = row.get("humanBattleEvents").get(0);
        assertEquals(0, event.getInt("attacker"));
        assertEquals(1, event.getInt("defender"));
        assertEquals(7, event.getInt("cityAddress"));
        assertEquals(234, row.getInt("firstVisitedTick"));
        assertEquals(234, row.getInt("firstTakeoffRequestTick"));
        assertEquals(3, row.get("route").get("waypoints").size);
        SourceAircraftPlayback core = new SourceAircraftPlayback(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 2, 1,
                        "plane1", false), null);
        int stopCount = 0;
        JsonValue sample = row.get("sampledChangedRows").child;
        for (int tick = 0; tick <= row.getInt("battleLastTick"); tick++) {
            if (tick > 0 && tick < 234) core.presentWaitingFrame();
            if (tick >= 234) core.tickSourceFrame();
            if (tick == 233) assertFalse(core.isStarted());
            if (tick == 234) assertTrue(core.isStarted());
            boolean stop = core.takeSoundStop();
            if (stop) stopCount++;
            assertEquals("owned plane sound stop t" + tick,
                    tick == row.getInt("ownedSoundStopTick"), stop);
            assertEquals("plane wrapper removal t" + tick,
                    tick >= row.getInt("wrapperRemovedTick"), core.isRemoved());
            if (sample != null && tick == sample.getInt("tick")) {
                assertEquals("plane action t" + tick,
                        sample.getString("action"), core.getAction());
                assertSoundWhilePresent(sample, tick,
                        core.getSourceSoundVolumePercent(),
                        core.getSourceSoundPanPercent());
                sample = sample.next;
            }
        }
        assertEquals(1, stopCount);
        assertEquals(null, sample);
        assertEquals(470, row.getInt("ownedSoundStopTick"));
        assertEquals(471, row.getInt("wrapperRemovedTick"));
    }

    @Test public void shipPlaneFailedAndSuccessfulUnitOneUseTheirOwnClocks()
            throws IOException {
        assertShip(tailCase("ship"), true, 1, 195, 196, 238, 239);
        assertShip(tailCase("ship_success"), false, 2, 234, 235, 423, 424);
    }

    private static void assertShip(JsonValue row, boolean intercepted, int defender,
                                   int activationTick, int firstVisit,
                                   int stopTick, int removedTick) {
        JsonValue event = row.get("humanBattleEvents").get(0);
        assertEquals(0, event.getInt("attacker"));
        assertEquals(defender, event.getInt("defender"));
        JsonValue payloads = event.get("shipAttacks");
        assertNotNull(payloads);
        assertEquals("three legal UI-selected payloads", 3, payloads.size);
        assertEquals(3, event.get("shipResults").size);
        assertEquals(intercepted,
                !event.get("shipResults").get(0).getBoolean("_result"));
        assertEquals(0, row.get("route").getInt("sourceWildcardNumber"));
        assertEquals(3, row.get("route").getInt("sourceStartNum"));
        assertEquals(firstVisit, row.getInt("firstVisitedTick"));
        assertEquals(firstVisit, row.getInt("firstTakeoffRequestTick"));
        assertEquals(stopTick, row.getInt("ownedSoundStopTick"));
        assertEquals(removedTick, row.getInt("wrapperRemovedTick"));
        SourceCarrierPlanePlayback core = new SourceCarrierPlanePlayback(
                FlashBattleRoutes.createCarrierPlaneStrike(0, defender, 0, 0,
                        intercepted), new ReferenceRandom(0L), payloads.size);
        JsonValue sample = row.get("sampledChangedRows").child;
        int stopCount = 0;
        for (int tick = 0; tick <= row.getInt("battleLastTick"); tick++) {
            if (tick > 0) core.tickSourceFrame(tick >= activationTick);
            if (tick == activationTick - 1)
                assertEquals("waitForLiftOff", core.getAction());
            if (tick == firstVisit)
                assertTrue("first ship movement after wait visit",
                        core.getX() != 392.5d);
            boolean stop = core.takeSoundStop();
            if (stop) stopCount++;
            assertEquals("ship owned sound stop t" + tick, tick == stopTick, stop);
            assertEquals("ship wrapper removal t" + tick,
                    tick >= removedTick, core.isRemoved());
            if (sample != null && tick == sample.getInt("tick")) {
                assertEquals("ship action t" + tick,
                        sample.getString("action"), core.getAction());
                assertSoundWhilePresent(sample, tick,
                        core.getSourceSoundVolumePercent(),
                        core.getSourceSoundPanPercent());
                sample = sample.next;
            }
        }
        assertEquals("exactly one owned stop", 1, stopCount);
        assertEquals(null, sample);
    }

    private static void assertSoundWhilePresent(JsonValue sample, int tick,
                                                double volume, double pan) {
        // Unit.flyOff deletes Unit.sound at stop. The stopped Sound object still
        // appears in the oracle, but it is no longer a Unit-owned voice.
        if (!sample.getBoolean("soundPlaying")
                || !sample.getBoolean("unitSoundSameObject")) return;
        assertEquals("source volume t" + tick,
                sample.getDouble("soundVolume"), volume, 1e-9);
        assertEquals("source pan t" + tick,
                sample.getDouble("soundPan"), pan, 1e-9);
    }

    private static JsonValue tailCase(String family) throws IOException {
        JsonValue root = read("source-aircraft-audio-tails.json");
        assertEquals("source-aircraft-audio-tails-v1", root.getString("schema"));
        assertTrue(root.getString("syntheticBoundaryNotice")
                .contains("synthetic takeoff completion"));
        for (JsonValue row = root.get("cases").child; row != null; row = row.next)
            if (family.equals(row.getString("family"))) return row;
        throw new AssertionError("Missing legal source audio case " + family);
    }

    private static JsonValue read(String name) throws IOException {
        try (InputStream input = SourceAircraftAudioClockTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull(name, input);
            byte[] bytes = input.readAllBytes();
            return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
