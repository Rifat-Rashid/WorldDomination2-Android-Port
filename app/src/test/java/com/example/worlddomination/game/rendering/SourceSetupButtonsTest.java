package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

public final class SourceSetupButtonsTest {
    private JsonValue scene() throws Exception {
        Path path = Paths.get("src/main/assets/original/runtime/scene.json.gz");
        try (GZIPInputStream zip = new GZIPInputStream(Files.newInputStream(path))) {
            return new JsonReader().parse(new InputStreamReader(zip, StandardCharsets.UTF_8));
        }
    }

    private JsonValue symbol(JsonValue scene, int id) {
        for (JsonValue symbol : scene.get("symbols")) {
            if (symbol.getInt("id") == id) return symbol;
        }
        throw new AssertionError("Missing source symbol " + id);
    }

    @Test public void sourceFramesRetainBothStoppedEmptyChildren() throws Exception {
        JsonValue scene = scene();
        FlashAnimationTimeline button = FlashAnimationTimeline.fromSymbol(symbol(scene, 4586), 24f);
        for (int frame = 0; frame < 2; frame++) {
            FlashAnimationTimeline.Layer[] layers = button.getFrame(frame).layers;
            assertEquals(3, layers.length);
            assertEquals(frame == 0 ? 4580 : 4582, layers[0].shapeId);
            assertEquals(4486, layers[1].shapeId);
            assertEquals(4486, layers[2].shapeId);
            assertEquals(2, layers[1].depth);
            assertEquals(4, layers[2].depth);
        }
        JsonValue child = symbol(scene, 4486).get("timeline");
        assertEquals(45, child.get("frames").size);
        assertEquals(0, child.get("frames").get(0).size);
        // Reference DefineSprite_4486/frame_1/DoAction is Stop. Setup setters
        // change only 4586; the retained children never enter their playing frames.
        assertEquals(1, child.get("action_frames").get(0).asInt());
        for (int id : new int[] {4580, 4582}) {
            assertTrue(Files.isRegularFile(Paths.get("src/main/assets/original/runtime/shapes/" + id + ".svgz")));
        }
    }
}
