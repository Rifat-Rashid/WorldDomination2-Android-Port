package com.example.worlddomination.game.audio;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Pins the legal source sound callback and Unit clock observations. */
public final class SourceAircraftAudioFixtureIntegrityTest {
    private static final String PROVENANCE_SHA =
            "4ea7ee94dff0eb0b180f1580aa2d9a3d49247b1bd5f19b090b7d5371a101ba4b";

    @Test public void sourceHashesAndPackagedAudioFixturesStayPinned() throws IOException {
        byte[] provenanceBytes = resource("source-aircraft-audio-provenance.json");
        assertEquals("provenance cannot silently relabel a source capture",
                PROVENANCE_SHA, sha256(provenanceBytes));
        JsonValue provenance = parse(provenanceBytes);
        JsonValue source = provenance.get("sourceSha256");
        assertEquals("fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1",
                source.getString("WorldDomination2.swf"));
        assertEquals("bc54c0e6c46f4e33bbb20e786c755f93aaba5d3d7224b0120eeeccc81eef8539",
                source.getString("wd2/generated/Unit.py"));
        assertEquals("dd103543e0f5abb5dfca5b93749e81b896175090a3b1c4c4d38c016a433bd36b",
                source.getString("wd2/menu_audio.py"));
        assertEquals(3, provenance.get("caseCounts").getInt("callbacks"));
        assertEquals(4, provenance.get("caseCounts").getInt("tails"));
        assertEquals("08639bbd036f6251b381cdac09bbbdf5a1f06fd3e1e5505205189ec5a5c2bba7",
                sha256(resource("source-aircraft-audio-callbacks.json")));
        assertEquals("76dc631189b5d85aa8755405938e9539a1c49e3c62ef8e28285a01bbb8d3995e",
                sha256(resource("source-aircraft-audio-tails.json")));
        JsonValue hashes = provenance.get("fixtureSha256");
        for (JsonValue entry = hashes.child; entry != null; entry = entry.next)
            assertEquals(entry.name, entry.asString(), sha256(resource(entry.name)));
    }

    @Test public void callbackAndTailCasesPreserveIndependentSourceBoundaries()
            throws IOException {
        JsonValue callbacks = parse(resource("source-aircraft-audio-callbacks.json"));
        JsonValue tails = parse(resource("source-aircraft-audio-tails.json"));
        assertEquals("source-aircraft-audio-callbacks-v1", callbacks.getString("schema"));
        assertEquals("source-aircraft-audio-tails-v1", tails.getString("schema"));
        assertEquals(3, callbacks.get("cases").size);
        assertEquals(4, tails.get("cases").size);
        JsonValue cues = callbacks.get("cueIdsAndExtractedBytes");
        assertEquals(66, cues.get("unit_Fightertakeoff.wav").getInt("swfSoundId"));
        assertEquals(20, cues.get("unit_fighterflight.wav").getInt("swfSoundId"));
        assertEquals(76, cues.get("unit_Carrierjet take off.wav").getInt("swfSoundId"));
        assertEquals(21, cues.get("unit_Carrierjet flight.wav").getInt("swfSoundId"));
        assertTrue(callbacks.getString("forcedCompletionNotice")
                .contains("physical cue durations were not measured"));
        assertTrue(tails.getString("syntheticBoundaryNotice")
                .contains("synthetic takeoff completion"));

        assertCallback(callbacks, "plane1", "f_12", 234,
                "unit_fighterflight.wav", 1);
        assertCallback(callbacks, "plane2", "f_13", 272,
                "unit_Carrierjet flight.wav", 999);
        assertCallback(callbacks, "ship", "f_14", 196,
                "unit_fighterflight.wav", 1);
        assertTail(tails, "plane1", 343, 344, 393, 470, 471, 533, 58);
        assertTail(tails, "plane2", 381, 382, 431, 508, 509, 571, 58);
        assertTail(tails, "ship", -1, -1, -1, 238, 239, 482, 5);
        assertTail(tails, "ship_success", 296, 297, 346, 423, 424, 565, 56);
    }

    private static void assertCallback(JsonValue callbacks, String family, String function,
                                       int firstTick, String flight, int loops) {
        JsonValue row = named(callbacks, family);
        assertEquals(function, row.getString("sourceCallback"));
        assertEquals(firstTick, row.getInt("firstVisitedTick"));
        assertEquals(firstTick, row.getInt("firstTakeoffRequestTick"));
        assertTrue(row.getBoolean("soundOwnerIsUnitMC"));
        JsonValue stages = row.get("stages");
        assertEquals(5, stages.size);
        for (JsonValue stage = stages.child; stage != null; stage = stage.next) {
            assertTrue(stage.getBoolean("callbackRetained"));
            assertTrue(stage.getBoolean("sameUnitSoundObject"));
        }
        for (int index = 1; index <= 3; index++) {
            JsonValue stage = stages.get(index);
            assertEquals(flight, stage.getString("linkage"));
            assertEquals(loops, stage.getInt("loops"));
            assertTrue(stage.getBoolean("playing"));
            JsonValue events = stage.get("events");
            assertEquals(2, events.size);
            assertEquals("complete", events.get(0).getString("event"));
            assertEquals("start", events.get(1).getString("event"));
            assertEquals(flight, events.get(1).getString("linkage"));
        }
        assertFalse(stages.get(3).getBoolean("optionPlaySound"));
        assertFalse(stages.get(4).getBoolean("playing"));
        assertEquals(1, stages.get(4).get("events").size);
        assertEquals("stop", stages.get(4).get("events").get(0).getString("event"));
    }

    private static void assertTail(JsonValue tails, String family, int flyOff,
                                   int fade, int zero, int stop, int removed,
                                   int last, int changedRows) {
        JsonValue row = named(tails, family);
        assertEquals(row.getInt("firstVisitedTick"),
                row.getInt("syntheticTakeoffCompletionTick"));
        assertEquals(stop, row.getInt("ownedSoundStopTick"));
        assertEquals(removed, row.getInt("wrapperRemovedTick"));
        assertEquals(stop + 1, removed);
        assertEquals(last, row.getInt("battleLastTick"));
        assertEquals(changedRows, row.get("sampledChangedRows").size);
        if (flyOff < 0) {
            assertTrue(row.get("firstFlyOffTick").isNull());
            assertTrue(row.get("firstFadeTick").isNull());
            assertTrue(row.get("zeroVolumeTick").isNull());
        } else {
            assertEquals(flyOff, row.getInt("firstFlyOffTick"));
            assertEquals(fade, row.getInt("firstFadeTick"));
            assertEquals(zero, row.getInt("zeroVolumeTick"));
            int changes = 0;
            for (JsonValue sample = row.get("sampledChangedRows").child;
                    sample != null; sample = sample.next) {
                if (sample.getInt("tick") >= fade && sample.getInt("tick") <= zero
                        && sample.getBoolean("soundPlaying")) {
                    assertEquals(100 - 2 * (sample.getInt("tick") - flyOff),
                            sample.getInt("soundVolume"));
                    changes++;
                }
            }
            assertEquals("every two-point fade step", 50, changes);
        }
    }

    private static JsonValue named(JsonValue root, String family) {
        for (JsonValue row = root.get("cases").child; row != null; row = row.next)
            if (family.equals(row.getString("family"))) return row;
        throw new AssertionError("Missing source audio case " + family);
    }

    private static JsonValue parse(byte[] bytes) {
        return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = SourceAircraftAudioFixtureIntegrityTest.class
                .getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, input);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest)
                hex.append(Character.forDigit((value >>> 4) & 15, 16))
                        .append(Character.forDigit(value & 15, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
