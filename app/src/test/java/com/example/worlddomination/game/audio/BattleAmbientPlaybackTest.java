package com.example.worlddomination.game.audio;

import org.junit.Test;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class BattleAmbientPlaybackTest {
    @Test public void sourceMappingsAndFiniteGain() {
        Fixture f = new Fixture();
        String[] frames = {"resources", "production", "diplomacy", "defense", "religion", "research", "game"};
        OriginalAudio.Cue[] cues = {OriginalAudio.Cue.RESOURCES_AMBIENT, OriginalAudio.Cue.PRODUCTION_AMBIENT,
                OriginalAudio.Cue.DIPLOMACY_AMBIENT, OriginalAudio.Cue.DEFENSE_AMBIENT,
                OriginalAudio.Cue.MASS_MEDIA_AMBIENT, OriginalAudio.Cue.TECHNOLOGY_AMBIENT,
                OriginalAudio.Cue.GAME_AMBIENT};
        for (int i = 0; i < frames.length; i++) {
            assertTrue(f.playback.startForBuilding(frames[i]));
            assertEquals(cues[i], f.requested.get(i));
            assertEquals("play:1.0:1.0:0.0:9999:1", f.fake(cues[i]).events.get(0));
            assertEquals("volume:1:0.21", f.fake(cues[i]).events.get(1));
        }
        assertFalse(f.playback.startForBuilding("factory"));
        assertFalse(f.playback.startForBuilding(null));
        assertEquals(7, f.requested.size());
    }

    @Test public void repeatedFramesOccupySeparateSlotsAndEleventhReplacesFirst() {
        Fixture f = new Fixture();
        for (int i = 0; i < 11; i++) assertTrue(f.playback.startForBuilding("resources"));
        Fake sound = f.fake(OriginalAudio.Cue.RESOURCES_AMBIENT);
        assertEquals(11, sound.next);
        assertTrue(sound.events.contains("stop:1"));
        assertFalse(sound.events.contains("stop:2"));
        f.playback.stopAll();
        assertEquals(11, sound.events.stream().filter(e -> e.startsWith("stop:")).count());
        assertTrue(f.playback.startForBuilding("resources"));
        f.playback.stopAll();
        assertTrue(sound.events.contains("stop:12"));
    }

    @Test public void optionAndCleanupDoNotResetIndexOrStopOtherOwners() throws Exception {
        Fixture f = new Fixture();
        Fake shared = f.fake(OriginalAudio.Cue.RESOURCES_AMBIENT);
        long canonicalId = shared.sound.playFinite(1f, 1f, 0f, 2);
        for (int i = 0; i < 3; i++) assertTrue(f.playback.startForBuilding("resources"));
        f.playback.setSoundEnabled(false);
        assertFalse(f.playback.startForBuilding("defense"));
        assertFalse(shared.events.contains("stop:" + canonicalId));
        f.playback.stopAll();
        java.lang.reflect.Field index = BattleAmbientPlayback.class.getDeclaredField("nextSlot");
        index.setAccessible(true);
        assertEquals(3, index.getInt(f.playback));
        f.playback.setSoundEnabled(true);
        for (int i = 0; i < 8; i++) assertTrue(f.playback.startForBuilding("resources"));
        // Index remained 3 after cleanup: the eighth start wraps to slot 1.
        assertFalse(shared.events.contains("stop:" + canonicalId));
        f.playback.beginBattle();
        assertEquals(0, index.getInt(f.playback));
        int stops = (int) shared.events.stream().filter(e -> e.startsWith("stop:")).count();
        assertEquals(11, stops);
        f.playback.dispose();
        assertFalse(shared.events.contains("dispose"));
    }

    @Test public void muteAndPausePreserveStreamsAndIndependentOption() {
        Fixture f = new Fixture();
        assertTrue(f.playback.startForBuilding("resources"));
        Fake sound = f.fake(OriginalAudio.Cue.RESOURCES_AMBIENT);
        f.playback.setOutputMuted(true);
        f.playback.pause();
        assertTrue(f.playback.startForBuilding("resources"));
        assertTrue(sound.events.contains("play:0.0:1.0:0.0:9999:2"));
        assertTrue(sound.events.contains("pause:1"));
        assertTrue(sound.events.contains("pause:2"));
        f.playback.setSoundEnabled(false);
        f.playback.setOutputMuted(false);
        f.playback.resume();
        assertTrue(sound.events.contains("volume:1:0.21"));
        assertTrue(sound.events.contains("volume:2:0.21"));
        assertTrue(sound.events.contains("resume:1"));
        assertTrue(sound.events.contains("resume:2"));
        assertFalse(sound.events.contains("stop:1"));
    }

    @Test public void capturedSourceLegalAndBoundaryTracesMatch() throws Exception {
        JsonValue trace;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/source-ambient/ambient-trace.json"),
                StandardCharsets.UTF_8)) {
            trace = new JsonReader().parse(reader);
        }
        JsonValue legal = trace.get("legal").get("events");
        Fixture legalPlayback = new Fixture();
        for (int i = 0; i < 3; i++) assertTrue(legalPlayback.playback.startForBuilding("resources"));
        legalPlayback.playback.stopAll();
        List<String> expectedLegal = new ArrayList<>();
        Map<String, Long> legalOwners = new HashMap<>();
        appendResourceEvents(legal, expectedLegal, legalOwners, 0, false);
        assertEquals(expectedLegal, legalPlayback.fake(OriginalAudio.Cue.RESOURCES_AMBIENT).events);

        Fixture boundaryPlayback = new Fixture();
        for (int i = 0; i < 14; i++) assertTrue(boundaryPlayback.playback.startForBuilding("resources"));
        assertTrue(boundaryPlayback.playback.startForBuilding("production"));
        boundaryPlayback.playback.stopAll();
        List<String> expectedBoundary = new ArrayList<>();
        Map<String, Long> owners = new HashMap<>();
        long next = appendResourceEvents(legal, expectedBoundary, owners, 0, true);
        appendResourceEvents(trace.get("boundary"), expectedBoundary, owners, next, false);
        assertEquals(expectedBoundary, boundaryPlayback.fake(OriginalAudio.Cue.RESOURCES_AMBIENT).events);
    }

    /** Normalize captured owner slots into IDs; values and ordering remain from Python. */
    private static long appendResourceEvents(JsonValue events, List<String> result,
            Map<String, Long> owners, long next, boolean initialOnly) {
        for (JsonValue event = events.child; event != null; event = event.next) {
            if (!"Resources ambient loop.wav".equals(event.getString("cue", ""))) continue;
            if (initialOnly && event.getInt("tick") != -1) continue;
            String operation = event.getString("op");
            String owner = event.getString("owner");
            if ("start".equals(operation)) {
                long id = ++next;
                owners.put(owner, id);
                result.add("play:" + event.getInt("volume") / 100f + ":1.0:0.0:"
                        + event.getInt("loops") + ":" + id);
            } else if ("volume".equals(operation)) {
                result.add("volume:" + owners.get(owner) + ":" + event.getInt("volume") / 100f);
            } else if ("stop".equals(operation)) {
                result.add("stop:" + owners.get(owner));
            }
        }
        return next;
    }

    @Test public void missingNativeSampleStillRotatesAndReclaimsItsLogicalSlot() throws Exception {
        Fixture f = new Fixture();
        for (int i = 0; i < 10; i++) assertTrue(f.playback.startForBuilding("resources"));
        f.unavailable = true;
        assertFalse(f.playback.startForBuilding("resources"));
        Fake sound = f.fake(OriginalAudio.Cue.RESOURCES_AMBIENT);
        assertTrue(sound.events.contains("stop:1"));
        assertFalse(sound.events.contains("stop:2"));
        java.lang.reflect.Field index = BattleAmbientPlayback.class.getDeclaredField("nextSlot");
        index.setAccessible(true);
        assertEquals(1, index.getInt(f.playback));
        f.unavailable = false;
        assertTrue(f.playback.startForBuilding("resources"));
        assertTrue(sound.events.contains("stop:2"));
        f.playback.stopAll();
        assertEquals(11, sound.events.stream().filter(e -> e.startsWith("stop:")).count());
    }

    private static final class Fixture {
        final Map<OriginalAudio.Cue, Fake> sounds = new EnumMap<>(OriginalAudio.Cue.class);
        final List<OriginalAudio.Cue> requested = new ArrayList<>();
        boolean unavailable;
        final BattleAmbientPlayback playback = new BattleAmbientPlayback(cue -> {
            requested.add(cue);
            return unavailable ? null : fake(cue).sound;
        });
        Fake fake(OriginalAudio.Cue cue) { return sounds.computeIfAbsent(cue, ignored -> new Fake()); }
    }

    private static final class Fake {
        long next;
        final List<String> events = new ArrayList<>();
        final FiniteRepeatSound sound = (FiniteRepeatSound) Proxy.newProxyInstance(
                FiniteRepeatSound.class.getClassLoader(), new Class<?>[]{FiniteRepeatSound.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "playFinite":
                            long id = ++next;
                            events.add("play:" + args[0] + ":" + args[1] + ":" + args[2] + ":" + args[3] + ":" + id);
                            return id;
                        case "setVolume": events.add("volume:" + args[0] + ":" + args[1]); return null;
                        case "stop": case "pause": case "resume":
                            events.add(method.getName() + ":" + args[0]); return null;
                        case "dispose": events.add("dispose"); return null;
                        default: return null;
                    }
                });
    }
}
