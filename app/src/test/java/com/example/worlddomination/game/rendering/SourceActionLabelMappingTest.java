package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class SourceActionLabelMappingTest {
    @Test
    public void allSourceFrameMappingsAndUnmappedFrames() throws Exception {
        JsonValue source = fixture();
        assertEquals("source table size", 18, source.get("mappedFrames").size);
        for (JsonValue row : source.get("mappedFrames")) {
            assertEquals(row.getString("frame"), GameState.Action.valueOf(
                    row.getString("action")), SourceActionLabelMapping.actionForFrame(
                    row.getString("frame")));
        }
        for (JsonValue row : source.get("excludedFrames")) {
            assertNull("unmapped source frame " + row.asString(),
                    SourceActionLabelMapping.actionForFrame(row.asString()));
        }
        assertNull(SourceActionLabelMapping.actionForFrame(null));
    }

    @Test
    public void firedDefenseBuildingDoesNotExtendInterceptedShipLabels() throws Exception {
        JsonValue source = fixture();
        assertSourceGate(source.get("interceptedShipFrames"),
                source.getInt("interceptedDistinctLabels"),
                source.getInt("interceptedGateOpenTick"));
        assertSourceGate(source.get("successfulShipFrames"),
                source.getInt("successfulDistinctLabels"),
                source.getInt("successfulGateOpenTick"));
        assertEquals(GameState.Action.DEFENSE,
                SourceActionLabelMapping.actionForFrame("antiAir_still"));
        assertNull(SourceActionLabelMapping.actionForFrame("antiAir_fire"));
    }

    private static void assertSourceGate(JsonValue frames, int expectedDistinctLabels,
                                         int expectedGateTick) {
        List<GameState.Action> labels = new ArrayList<>();
        EnumSet<GameState.Action> distinct = EnumSet.noneOf(GameState.Action.class);
        for (JsonValue frame : frames) {
            GameState.Action action = SourceActionLabelMapping.actionForFrame(
                    frame.asString());
            if (action != null) {
                labels.add(action);
                distinct.add(action);
            }
        }
        assertEquals("source distinct action labels", expectedDistinctLabels, distinct.size());
        TurnPlaybackSequence playback = new TurnPlaybackSequence();
        playback.begin(labels);
        for (int tick = 1; tick < expectedGateTick; tick++) {
            playback.advance(1f / 24f);
            assertEquals("still showing labels on tick " + tick,
                    TurnPlaybackSequence.Phase.ACTION_LABELS, playback.getPhase());
        }
        playback.advance(1f / 24f);
        assertEquals("source gate tick " + expectedGateTick,
                TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
    }

    private static JsonValue fixture() throws Exception {
        try (InputStream stream = SourceActionLabelMappingTest.class.getClassLoader()
                .getResourceAsStream("source-bio-action-labels.json")) {
            assertNotNull("source action label provenance fixture", stream);
            return new JsonReader().parse(new InputStreamReader(stream,
                    StandardCharsets.UTF_8));
        }
    }
}
