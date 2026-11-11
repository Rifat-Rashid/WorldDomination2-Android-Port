package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public final class BattlefieldFlagAssetsTest {
    @Test public void allFaithPoleFramesAndSynchronizedWaveChildrenArePreserved() throws Exception {
        JsonValue scene = scene();
        FlashAnimationTimeline pole = OriginalFlashAnimationAssets.loadTimelines(scene).get("flag_pole");
        assertNotNull(pole);
        String[] faiths = {"mus", "jew", "com", "chr", "bhu"};
        int[] poles = {4837, 4927, 4929, 4931, 4933};
        Map<Integer, FlashAnimationTimeline> nested = OriginalFlashAnimationAssets.loadNestedTimelines(scene, pole);
        assertEquals(25, nested.get(4888).getFrameCount());
        assertEquals(25, nested.get(4925).getFrameCount());
        for (int i = 0; i < faiths.length; i++) {
            FlashAnimationTimeline.Frame frame = pole.getFrameByLabel(faiths[i]);
            assertNotNull(frame);
            boolean foundPole = false, foundTint = false, foundHighlight = false;
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                foundPole |= layer.shapeId == poles[i];
                if ("tintMC".equals(layer.name)) {
                    foundTint = true;
                    assertEquals(4888, layer.shapeId);
                    assertEquals(.7528991699f, layer.a, .00001f);
                    assertEquals(-20.75f, layer.tx, .001f);
                    assertEquals(-48.05f, layer.ty, .001f);
                }
                if (layer.shapeId == 4925) {
                    foundHighlight = true;
                    assertEquals(.44140625f, layer.alpha, .000001f);
                }
            }
            assertTrue(foundPole && foundTint && foundHighlight);
        }
    }

    @Test public void flagWaveArtworkIsReachableAndPackaged() throws Exception {
        JsonValue scene = scene();
        Map<String, FlashAnimationTimeline> roots = OriginalFlashAnimationAssets.loadTimelines(scene);
        Map<Integer, FlashAnimationTimeline> nested = OriginalFlashAnimationAssets.loadNestedTimelines(scene, roots.get("flag_pole"));
        Set<Integer> shapes = OriginalFlashAnimationAssets.collectShapeIds(
                java.util.Collections.singletonMap("flag_pole", roots.get("flag_pole")), nested,
                id -> Files.isRegularFile(Paths.get("src/main/assets/original/runtime/shapes/" + id + ".svgz")));
        assertTrue(shapes.contains(4837));
        assertTrue(shapes.contains(4933));
        assertTrue("All wave/highlight leaves and five faith poles", shapes.size() >= 48);
    }

    @Test public void cleanTerrainRetainsHighResolutionAndOriginalEnhancedMap() throws Exception {
        byte[] png = Files.readAllBytes(Paths.get("src/main/assets/original/ui/battle-terrain.png"));
        ByteBuffer header = ByteBuffer.wrap(png);
        assertEquals(0x89504e470d0a1a0aL, header.getLong());
        assertEquals(1200, header.getInt(16)); assertEquals(730, header.getInt(20));
        assertTrue(Files.isRegularFile(Paths.get("src/main/assets/original/ui/battle-map-hd.png")));
    }

    private static JsonValue scene() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(
                Paths.get("src/main/assets/original/runtime/scene.json.gz"))), StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }
}
