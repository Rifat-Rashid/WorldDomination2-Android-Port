package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class OriginalEndgameAssetsTest {
    @Test public void allFourAuthoredFramesAndLeafAssetsArePackaged() throws IOException {
        JsonValue scene = loadScene();
        FlashAnimationTimeline root = OriginalFlashAnimationAssets.loadTimelineBySymbolId(
                scene, OriginalEndgameAssets.SYMBOL_ID);
        assertEquals(4, root.getFrameCount());
        assertEquals(5884, root.getFrameByLabel("win").layers[0].shapeId);
        assertEquals(5886, root.getFrameByLabel("lost").layers[0].shapeId);
        assertEquals(5888, root.getFrameByLabel("win2").layers[0].shapeId);
        assertEquals(5888, root.getFrameByLabel("lost2").layers[0].shapeId);
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, root);
        Set<Integer> leaves = OriginalEndgameAssets.collectLeafShapeIds(root, nested,
                id -> Files.isRegularFile(shape(id)));
        assertEquals(Set.of(103, 3253, 4117, 4484, 4580, 4582, 4584,
                5884, 5886, 5888, 5891, 5894, 5902), leaves);
        for (int id : leaves) assertTrue("Missing source endgame art " + id,
                Files.isRegularFile(shape(id)));
        assertTrue(Files.isRegularFile(asset("original/runtime/fonts/3310_Arial_11pt_st.ttf.gz")));
        assertTrue(Files.isRegularFile(asset("original/runtime/fonts/4439_Arial_30pt_st.ttf.gz")));
        FlashAnimationTimeline.Layer button = null;
        for (FlashAnimationTimeline.Layer layer : root.getFrameByLabel("win2").layers)
            if ("btn_new".equals(layer.name)) button = layer;
        assertNotNull(button);
        assertEquals(4120, button.shapeId);
        assertEquals(485.6f, button.tx, 0.01f);
        assertEquals(451.3f, button.ty, 0.01f);
    }

    @Test public void scoreArtKeepsBothSourceBitmapPatternsInTheirSvgPositions()
            throws IOException {
        FlashBitmapPatternComposer.Plan victory = FlashBitmapPatternComposer.parse(
                readGzip(shape(5891)));
        assertEquals(734, victory.width);
        assertEquals(332, victory.height);
        assertEquals(31f, victory.originX, .001f);
        assertEquals(109.85f, victory.originY, .001f);
        assertEquals(2, victory.parts.size());
        assertEquals(0f, victory.parts.get(0).x, .001f);
        assertEquals(0f, victory.parts.get(0).y, .001f);
        assertEquals(419.45f, victory.parts.get(1).x, .001f);
        assertEquals(243.05f, victory.parts.get(1).y, .001f);

        FlashBitmapPatternComposer.Plan defeat = FlashBitmapPatternComposer.parse(
                readGzip(shape(5894)));
        assertEquals(653, defeat.width);
        assertEquals(233, defeat.height);
        assertEquals(112.55f, defeat.originX, .001f);
        assertEquals(113.45f, defeat.originY, .001f);
        assertEquals(2, defeat.parts.size());
        assertEquals(338.9f, defeat.parts.get(0).x, .001f);
        assertEquals(0f, defeat.parts.get(0).y, .001f);
        assertEquals(0f, defeat.parts.get(1).x, .001f);
        assertEquals(11.55f, defeat.parts.get(1).y, .001f);
    }

    private static JsonValue loadScene() throws IOException {
        try (InputStream file = Files.newInputStream(asset("original/runtime/scene.json.gz"));
             InputStream unzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(unzip, StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }
    private static String readGzip(Path path) throws IOException {
        try (InputStream file = Files.newInputStream(path);
             InputStream unzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(unzip, StandardCharsets.UTF_8)) {
            char[] chars = new char[8192];
            StringBuilder text = new StringBuilder();
            int read;
            while ((read = reader.read(chars)) >= 0) text.append(chars, 0, read);
            return text.toString();
        }
    }
    private static Path shape(int id) { return asset("original/runtime/shapes/" + id + ".svgz"); }
    private static Path asset(String relative) {
        Path root = Paths.get("app", "src", "main", "assets", relative);
        return Files.isRegularFile(root) ? root : Paths.get("src", "main", "assets", relative);
    }
}
