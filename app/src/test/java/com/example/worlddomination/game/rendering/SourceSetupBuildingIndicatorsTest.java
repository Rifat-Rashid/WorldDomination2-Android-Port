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

public final class SourceSetupBuildingIndicatorsTest {
    @Test public void mappingsMatchBothOriginalTimelineFrames() throws Exception {
        Path base = Paths.get("src/main/assets/original/runtime");
        JsonValue root;
        try (GZIPInputStream zip = new GZIPInputStream(Files.newInputStream(base.resolve("scene.json.gz")))) {
            root = new JsonReader().parse(new InputStreamReader(zip, StandardCharsets.UTF_8));
        }
        int[] symbols = {5846,5838,5842,5834,5850};
        JsonValue setup = null;
        for (JsonValue candidate : root.get("symbols"))
            if (candidate.getInt("id") == 5882) { setup = candidate; break; }
        assertNotNull(setup);
        float[] x = {508.7f, 559.25f, 609.8f, 660.35f, 711f};
        for (int i = 0; i < symbols.length; i++) {
            JsonValue placement = null;
            for (JsonValue op : setup.get("timeline").get("frames").get(0)) {
                JsonValue values = op.get("values");
                if (("buildingIndicator" + (i + 1)).equals(values.getString("name", ""))) {
                    placement = values; break;
                }
            }
            assertNotNull(placement);
            assertEquals(symbols[i], placement.getInt("character_id"));
            JsonValue matrix = placement.get("matrix");
            assertEquals(1f, matrix.getFloat("a"), 0f);
            assertEquals(1f, matrix.getFloat("d"), 0f);
            assertEquals(0f, matrix.getFloat("b"), 0f);
            assertEquals(0f, matrix.getFloat("c"), 0f);
            assertEquals(x[i], matrix.getFloat("tx"), .0001f);
            assertEquals(i == 4 ? 160.25f : 160.75f, matrix.getFloat("ty"), 0f);
            assertEquals(i == 4 ? -.5f : 0f, SourceSetupBuildingIndicators.parentOffsetY(i), 0f);
        }
        for (int i=0;i<symbols.length;i++) {
            JsonValue symbol = null;
            for (JsonValue candidate : root.get("symbols"))
                if (candidate.getInt("id") == symbols[i]) { symbol = candidate; break; }
            assertNotNull(symbol);
            JsonValue frames = symbol.get("timeline").get("frames");
            for (int state=0;state<2;state++) {
                int expected = SourceSetupBuildingIndicators.shapeId(i,state==1);
                assertEquals(expected,frames.get(state).get(0).get("values").getInt("character_id"));
                Path shape = base.resolve("shapes/" + expected + ".svgz");
                assertTrue(Files.isRegularFile(shape));
                try (GZIPInputStream svg = new GZIPInputStream(Files.newInputStream(shape))) {
                    String xml = new String(svg.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(xml.contains(state == 0 ? "width=\"35.0px\"" : "width=\"53.0px\""));
                    assertTrue(xml.contains(state == 0 ? "height=\"35.0px\"" : "height=\"55.0px\""));
                    String matrix = state == 1 ? "3.0, 4.0" : i == 4 ? "-6.0, -6.5" : "-6.0, -6.0";
                    assertTrue("source registration " + expected, xml.contains(matrix + ")"));
                }
                if (state == 0) {
                    JsonValue transform = frames.get(state).get(0).get("values").get("matrix");
                    assertEquals(0f, transform.getFloat("tx"), 0f);
                    assertEquals(0f, transform.getFloat("ty"), 0f);
                    assertEquals(1, symbol.get("timeline").get("action_frames").get(0).asInt());
                }
            }
        }
    }
    @Test public void fitPreservesSingleSourceRegistrationForBothStates() {
        assertEquals(2f,SourceSetupBuildingIndicators.scale(106f,111f),0f);
        assertEquals(1f,SourceSetupBuildingIndicators.scale(100f,55.5f),0f);
        assertEquals(1f,SourceSetupBuildingIndicators.scale(53f,100f),0f);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidBuilding() {
        SourceSetupBuildingIndicators.shapeId(5,false);
    }
}
