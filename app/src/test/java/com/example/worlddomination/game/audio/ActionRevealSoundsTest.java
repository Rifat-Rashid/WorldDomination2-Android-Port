package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.rendering.TurnPlaybackSequence;
import org.junit.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public final class ActionRevealSoundsTest {
    private static final String[] EMPTY = new String[10];

    @Test public void legalLabelStartsMatchIndependentSourceTrace() throws Exception {
        JsonValue captured = trace();
        JsonValue aliases = captured.get("boundary").get("physicalFrameAliases");
        JsonValue legal = captured.get("legal");
        assertEquals(10, legal.size);
        for (JsonValue caseData = legal.child; caseData != null; caseData = caseData.next) {
            String[] slots = slotsFromCapture(caseData.get("positions").get("indexOrder"));
            List<GameState.Action> selected = new ArrayList<>();
            for (JsonValue building = caseData.get("sourceAllTurnBuildings").child;
                 building != null; building = building.next) {
                GameState.Action action = actionFromCapturedAliases(
                        aliases, building.getString("frame"));
                if (action != null) selected.add(action);
            }
            TurnPlaybackSequence sequence = new TurnPlaybackSequence();
            sequence.begin(selected);
            List<String> actual = new ArrayList<>();
            JsonValue religionPredictions = caseData.get("religionPredictions");
            assertTrue(religionPredictions.size <= 1);
            int mediaVariant = religionPredictions.size == 0 ? 0
                    : religionPredictions.get(0).getInt("actualVariant");
            for (int tick = 0; tick <= caseData.getInt("lastTick"); tick++) {
                GameState.Action action = sequence.advance(1f / 24f);
                if (action != null) {
                    final int sourceTick = tick;
                    ActionRevealSounds.play(action, slots, mediaVariant,
                            cue -> actual.add(event(sourceTick, cue)));
                }
            }
            List<String> expected = new ArrayList<>();
            JsonValue starts = caseData.get("sourceBuildingStarts");
            for (JsonValue start = starts.child; start != null; start = start.next) {
                assertEquals("start", start.getString("op"));
                assertEquals(100, start.getInt("volume"));
                assertEquals(0, start.getInt("pan"));
                assertEquals(1, start.getInt("loops"));
                expected.add(start.getInt("tick") + "|" + start.getInt("symbol")
                        + "|" + start.getString("cue"));
            }
            assertTrue("Capture must exercise starts: " + caseData.getString("name"),
                    expected.size() >= 2);
            assertEquals(caseData.getString("name"), expected, actual);
        }
    }

    private static String[] slotsFromCapture(JsonValue positions) {
        String[] slots = new String[10];
        for (JsonValue position = positions.child; position != null; position = position.next) {
            String clipName = position.getString("clipName", null);
            if (clipName == null) continue;
            assertTrue(clipName.matches("ab[0-4]_[12]"));
            int player = clipName.charAt(2) - '0';
            int slot = clipName.charAt(4) - '0';
            assertEquals(player, position.getInt("player"));
            assertNull(slots[player * 2 + slot - 1]);
            slots[player * 2 + slot - 1] = position.getString("building");
        }
        return slots;
    }

    private static GameState.Action actionFromCapturedAliases(JsonValue aliases, String frame) {
        for (JsonValue entry = aliases.child; entry != null; entry = entry.next) {
            if (frame.equals(entry.getString("frame"))) {
                switch (entry.getString("action")) {
                    case "resources": return GameState.Action.RESOURCES;
                    case "production": return GameState.Action.PRODUCTION;
                    case "research": return GameState.Action.RESEARCH;
                    case "religion": return GameState.Action.MASS_MEDIA;
                    case "preAttack": return GameState.Action.PREPARED_ATTACK;
                    case "attack": return GameState.Action.ATTACK;
                    case "defense": return GameState.Action.DEFENSE;
                    case "propaganda": return GameState.Action.PROPAGANDA;
                    case "diplomacy": return GameState.Action.DIPLOMACY;
                    default: throw new AssertionError(entry.getString("action"));
                }
            }
        }
        return null;
    }

    private static String event(int tick, OriginalAudio.Cue cue) {
        String source = OriginalAudio.sourceAssetPath(cue);
        int separator = source.lastIndexOf('/') + 1;
        int underscore = source.indexOf('_', separator);
        String symbol = source.substring(separator, underscore);
        String name = source.substring(underscore + 1, source.length() - ".mp3.gz".length());
        return tick + "|" + Integer.parseInt(symbol) + "|" + name;
    }

    @Test public void physicalSlotsFollowCapturedReverseOrderAndDedupRules() throws Exception {
        JsonValue boundary = trace().get("boundary");
        JsonValue duplicated = boundary.get("duplicatePreAttack");
        String[] slots = new String[10];
        JsonValue names = duplicated.get("names");
        for (int i = 0; i < names.size; i++) slots[i ^ 1] = names.getString(i);
        List<String> actual = new ArrayList<>();
        ActionRevealSounds.play(GameState.Action.PREPARED_ATTACK, slots, 0,
                cue -> actual.add(event(3, cue)));
        assertEquals(capturedEvents(duplicated.get("starts")), actual);
        assertEquals(3, actual.size());
        actual.clear();
        Arrays.fill(slots, null);
        slots[0] = "hatch";
        slots[9] = "hatch";
        ActionRevealSounds.play(GameState.Action.ATTACK, slots, 0,
                cue -> actual.add(event(4, cue)));
        assertEquals(capturedEvents(boundary.get("attackHatches")), actual);
        assertEquals(2, actual.size());
        actual.clear();
        ActionRevealSounds.play(GameState.Action.PRODUCTION, slots, 0,
                cue -> actual.add(event(0, cue)));
        assertEquals(Arrays.asList(event(0, OriginalAudio.Cue.PRODUCTION_REVEAL)), actual);
        actual.clear();
        ActionRevealSounds.play(GameState.Action.PREPARED_ATTACK, EMPTY, 0,
                cue -> actual.add(event(0, cue)));
        assertEquals(boundary.get("empty").size, actual.size());
    }

    private static List<String> capturedEvents(JsonValue starts) {
        List<String> events = new ArrayList<>();
        for (JsonValue start = starts.child; start != null; start = start.next)
            events.add(start.getInt("tick") + "|" + start.getInt("symbol") + "|"
                    + start.getString("cue"));
        return events;
    }

    @Test public void missingSpaceAndReligionVariantAreLiteralAndRngFree() throws Exception {
        JsonValue boundary = trace().get("boundary");
        assertEquals("building_space_launch_2.wav",
                boundary.get("missingSpace").get("missing").get(0).getString("linkage"));
        List<OriginalAudio.Cue> cues = new ArrayList<>();
        String[] slots = new String[10];
        slots[9] = "space";
        ActionRevealSounds.play(GameState.Action.PREPARED_ATTACK, slots, 0, cues::add);
        assertTrue(cues.isEmpty());
        ActionRevealSounds.play(GameState.Action.MASS_MEDIA, slots, 4, cues::add);
        assertEquals(Arrays.asList(OriginalAudio.Cue.MASS_MEDIA_REVEAL_4), cues);
        cues.clear();
        ActionRevealSounds.play(GameState.Action.MASS_MEDIA, slots, 0, cues::add);
        assertTrue(cues.isEmpty());
        assertTrue(boundary.get("disabled").getBoolean("rngUnchanged"));
    }

    @Test public void battleVoicesUseFullGainAndStopOnlyTheirOwnIds() throws Exception {
        OriginalAudio audio = new OriginalAudio();
        List<String> calls = new ArrayList<>();
        long[] next = {10};
        Sound sample = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),
                new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                    if ("play".equals(method.getName())) {
                        calls.add("play:" + args[0] + ":" + args[1] + ":" + args[2]);
                        return next[0]++;
                    }
                    if ("stop".equals(method.getName())) calls.add("stop:" + args[0]);
                    if ("setPan".equals(method.getName())) calls.add("pan:" + args[0] + ":" + args[2]);
                    if ("pause".equals(method.getName())) calls.add("pause:" + args[0]);
                    if ("resume".equals(method.getName())) calls.add("resume:" + args[0]);
                    return null;
                });
        Field soundsField = OriginalAudio.class.getDeclaredField("sounds");
        soundsField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<OriginalAudio.Cue, Sound> sounds =
                (Map<OriginalAudio.Cue, Sound>) soundsField.get(audio);
        sounds.put(OriginalAudio.Cue.RESOURCES_REVEAL, sample);
        audio.beginBattleActionSounds();
        audio.playActionReveal(GameState.Action.RESOURCES, EMPTY, 0);
        assertTrue(calls.contains("play:1.0:1.0:0.0"));
        long unrelated = audio.playInstance(OriginalAudio.Cue.RESOURCES_REVEAL, 0f);
        assertEquals(11L, unrelated);
        assertTrue(calls.contains("play:0.8:1.0:0.0"));
        audio.toggleOutputMuted();
        assertTrue(calls.contains("pan:10:0.0"));
        audio.toggleOutputMuted();
        assertTrue(calls.contains("pan:10:1.0"));
        audio.pause();
        audio.resume();
        assertTrue(calls.contains("pause:10"));
        assertTrue(calls.contains("resume:10"));
        assertFalse(audio.toggleSound());
        audio.playActionReveal(GameState.Action.RESOURCES, EMPTY, 0);
        assertEquals(2, calls.stream().filter(s -> s.startsWith("play:")).count());
        audio.finishBattleActionSounds();
        assertTrue(calls.contains("stop:10"));
        assertFalse(calls.contains("stop:11"));
        assertFalse(calls.contains("stop:null"));
        assertTrue(audio.toggleSound());
        audio.toggleOutputMuted();
        audio.beginBattleActionSounds();
        audio.playActionReveal(GameState.Action.RESOURCES, EMPTY, 0);
        assertTrue(calls.contains("play:0.0:1.0:0.0"));
        audio.beginBattleActionSounds();
        assertTrue(calls.contains("stop:12"));
        assertFalse(calls.contains("stop:11"));
        audio.playActionReveal(GameState.Action.RESOURCES, EMPTY, 0);
        audio.dispose();
        assertTrue(calls.contains("stop:13"));
    }

    private static JsonValue trace() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                ActionRevealSoundsTest.class.getResourceAsStream(
                        "/source-action-building-audio/action-building-trace.json"),
                StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }
}
