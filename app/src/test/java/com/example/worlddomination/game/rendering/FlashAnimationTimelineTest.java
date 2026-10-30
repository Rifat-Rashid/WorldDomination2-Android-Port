package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FlashAnimationTimelineTest {
    @Test
    public void reconstructsFlashMoveAndRemovalTagsAcrossFrames() {
        JsonValue symbol = new JsonReader().parse("{\"id\":7,\"linkage\":\"fixture\","
                + "\"timeline\":{\"frames\":["
                + "[{\"depth\":1,\"values\":{\"character_id\":41,"
                + "\"matrix\":{\"a\":1,\"b\":0,\"c\":0,\"d\":1,"
                + "\"tx\":12,\"ty\":18}},\"move\":false,\"remove\":false}],"
                + "[{\"depth\":1,\"values\":{\"character_id\":42},"
                + "\"move\":true,\"remove\":false}],[],"
                + "[{\"depth\":1,\"values\":{},\"move\":false,\"remove\":true}]]}}");

        FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(symbol, 24f);

        assertEquals(4, timeline.getFrameCount());
        assertEquals(41, timeline.getFrame(0).shapeId);
        assertEquals(42, timeline.getFrame(1).shapeId);
        assertEquals(42, timeline.getFrame(2).shapeId);
        assertEquals(12f, timeline.getFrame(1).tx, 0f);
        assertEquals(18f, timeline.getFrame(2).ty, 0f);
        assertNull(timeline.getFrame(3));
        assertEquals(4f / 24f, timeline.getDurationSeconds(), 0.0001f);
    }

    @Test
    public void preservesMultipleDisplayListDepthsAndTheirIndependentTransforms() {
        JsonValue symbol = new JsonReader().parse("{\"id\":8,\"linkage\":\"layered\","
                + "\"timeline\":{\"frames\":["
                + "[{\"depth\":1,\"values\":{\"character_id\":41,"
                + "\"matrix\":{\"a\":1,\"b\":0,\"c\":0,\"d\":1,"
                + "\"tx\":12,\"ty\":18}},\"remove\":false},"
                + "{\"depth\":2,\"values\":{\"character_id\":42,"
                + "\"matrix\":{\"a\":2,\"b\":0,\"c\":0,\"d\":2,"
                + "\"tx\":-4,\"ty\":6}},\"remove\":false}],"
                + "[{\"depth\":2,\"remove\":true}],[]]}}");

        FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(symbol, 24f);

        assertEquals(2, timeline.getFrame(0).layers.length);
        assertEquals(41, timeline.getFrame(0).layers[0].shapeId);
        assertEquals(42, timeline.getFrame(0).layers[1].shapeId);
        assertEquals(12f, timeline.getFrame(0).layers[0].tx, 0f);
        assertEquals(18f, timeline.getFrame(0).layers[0].ty, 0f);
        assertEquals(-4f, timeline.getFrame(0).layers[1].tx, 0f);
        assertEquals(6f, timeline.getFrame(0).layers[1].ty, 0f);
        assertEquals(1, timeline.getFrame(1).layers.length);
        assertEquals(41, timeline.getFrame(1).layers[0].shapeId);
        assertEquals(1, timeline.getFrame(2).layers.length);
    }

    @Test
    public void packagedStrikeAndInterceptionSymbolsMatchSourceTimelines() throws IOException {
        JsonValue scene = loadSourceScene();
        assertEquals(24f, scene.getFloat("fps"), 0f);

        Map<String, Integer> expectedFrameCounts = new LinkedHashMap<>();
        expectedFrameCounts.put("explosion_warhead_1", 59);
        expectedFrameCounts.put("explosion_warhead_2", 52);
        expectedFrameCounts.put("explosion_warhead_3", 60);
        expectedFrameCounts.put("explosion_warhead_4", 93);
        expectedFrameCounts.put("explosion_bioload_1", 61);
        expectedFrameCounts.put("explosion_bioload_2", 61);
        expectedFrameCounts.put("explosion_bioload_3", 61);
        expectedFrameCounts.put("explosion_bioload_4", 87);
        expectedFrameCounts.put("explosion002", 51);
        expectedFrameCounts.put("explosion003", 22);
        expectedFrameCounts.put("chinook", 37);

        for (Map.Entry<String, Integer> expected : expectedFrameCounts.entrySet()) {
            JsonValue symbol = findSymbol(scene, expected.getKey());
            assertNotNull("Missing source timeline " + expected.getKey(), symbol);
            FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(symbol, 24f);
            assertEquals(expected.getKey(), expected.getValue().intValue(),
                    timeline.getFrameCount());
            assertEquals(expected.getValue() / 24f,
                    timeline.getDurationSeconds(), 0.0001f);

            int visibleFrames = 0;
            for (int i = 0; i < timeline.getFrameCount(); i++) {
                FlashAnimationTimeline.Frame frame = timeline.getFrame(i);
                if (frame == null) {
                    continue;
                }
                visibleFrames++;
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    assertTrue("Unexpected rotated source frame in " + expected.getKey(),
                            layer.b == 0f && layer.c == 0f);
                    assertTrue("Missing original shape " + layer.shapeId,
                            Files.isRegularFile(shapeAsset(layer.shapeId)));
                }
            }
            assertTrue("Source animation has no visible frames: " + expected.getKey(),
                    visibleFrames > 0);
        }
        FlashAnimationTimeline chinook = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "chinook"), 24f);
        int maximumLayerCount = 0;
        for (int i = 0; i < chinook.getFrameCount(); i++) {
            FlashAnimationTimeline.Frame frame = chinook.getFrame(i);
            if (frame != null) {
                maximumLayerCount = Math.max(maximumLayerCount, frame.layers.length);
            }
        }
        assertEquals("The original Chinook uses two simultaneous artwork layers", 2,
                maximumLayerCount);
    }

    @Test
    public void sequencesHonorSourceTickDelaysAndFinishAfterTheLastFrame() {
        JsonValue symbol = new JsonReader().parse("{\"id\":1,\"linkage\":\"two_frames\","
                + "\"timeline\":{\"frames\":["
                + "[{\"depth\":1,\"values\":{\"character_id\":9},"
                + "\"move\":false,\"remove\":false}],[]]}}");
        FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(symbol, 24f);
        BattleAnimationSequence sequence = new BattleAnimationSequence(timeline, 2, 3, 2, null);

        assertFalse(sequence.tick());
        assertNull(sequence.getCurrentFrame());
        assertFalse(sequence.tick());
        assertTrue(sequence.tick());
        assertEquals(9, sequence.getCurrentFrame().shapeId);
        assertFalse(sequence.tick());
        assertEquals(9, sequence.getCurrentFrame().shapeId);
        assertFalse(sequence.tick());
        assertTrue(sequence.isFinished());
        assertNull(sequence.getCurrentFrame());
    }

    private static JsonValue loadSourceScene() throws IOException {
        Path asset = projectAsset("original/runtime/scene.json.gz");
        try (InputStream file = Files.newInputStream(asset);
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                json.append(buffer, 0, count);
            }
            return new JsonReader().parse(json.toString());
        }
    }

    private static JsonValue findSymbol(JsonValue scene, String linkage) {
        JsonValue symbols = scene.get("symbols");
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            if (linkage.equals(symbol.getString("linkage", ""))) {
                return symbol;
            }
        }
        return null;
    }

    private static Path shapeAsset(int shapeId) {
        return projectAsset("original/runtime/shapes/" + shapeId + ".svgz");
    }

    private static Path projectAsset(String relative) {
        Path modulePath = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(modulePath)) {
            return modulePath;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
