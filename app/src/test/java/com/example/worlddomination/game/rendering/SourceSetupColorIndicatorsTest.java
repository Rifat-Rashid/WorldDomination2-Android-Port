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

public final class SourceSetupColorIndicatorsTest {
    @Test public void mappingsMatchBothOriginalTimelineFrames() throws Exception {
        Path base = Paths.get("src/main/assets/original/runtime");
        JsonValue root;
        try (GZIPInputStream zip = new GZIPInputStream(Files.newInputStream(base.resolve("scene.json.gz")))) {
            root = new JsonReader().parse(new InputStreamReader(zip, StandardCharsets.UTF_8));
        }
        int[] symbols = {5867,5862,5872,5877,5857};
        for (int i=0;i<symbols.length;i++) {
            JsonValue symbol = null;
            for (JsonValue candidate : root.get("symbols"))
                if (candidate.getInt("id") == symbols[i]) { symbol = candidate; break; }
            assertNotNull(symbol);
            JsonValue frames = symbol.get("timeline").get("frames");
            for (int state=0;state<2;state++) {
                int expected = SourceSetupColorIndicators.shapeId(i,state==1);
                assertEquals(expected,frames.get(state).get(0).get("values").getInt("character_id"));
                assertTrue(Files.isRegularFile(base.resolve("shapes/"+expected+".svgz")));
            }
        }
    }
    @Test public void fitPreservesSingleSourceRegistrationForBothStates() {
        assertEquals(2f,SourceSetupColorIndicators.scale(108f,110f),0f);
        assertEquals(1f,SourceSetupColorIndicators.scale(100f,55f),0f);
        assertEquals(1f,SourceSetupColorIndicators.scale(54f,100f),0f);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidColor() {
        SourceSetupColorIndicators.shapeId(5,false);
    }
}
