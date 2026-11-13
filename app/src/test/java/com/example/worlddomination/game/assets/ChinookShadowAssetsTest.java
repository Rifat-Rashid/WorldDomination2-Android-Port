package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import com.example.worlddomination.game.rendering.FlashColorTransform;

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
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Source shadow subtrees needed by the Chinook tank deployment renderer. */
public final class ChinookShadowAssetsTest {
    @Test
    public void bothShadowSubtreesAndEveryRotorFrameReachTheBattleAtlas() throws IOException {
        JsonValue scene = loadScene();
        Map<String, FlashAnimationTimeline> roots = OriginalFlashAnimationAssets.loadTimelines(scene);
        Map<Integer, FlashAnimationTimeline> children = new LinkedHashMap<>();
        for (String linkage : new String[]{"tank_noshade", "chinook_shadow"}) {
            assertNotNull(linkage, roots.get(linkage));
            children.putAll(OriginalFlashAnimationAssets.loadNestedTimelines(
                    scene, roots.get(linkage)));
        }
        FlashAnimationTimeline tankShadow = children.get(677);
        FlashAnimationTimeline rotor = children.get(2373);
        assertNotNull("The tank shadow clip must be loaded", tankShadow);
        assertNotNull("The animated Chinook shadow clip must be loaded", rotor);
        assertEquals(1, tankShadow.getFrameCount());
        assertEquals(37, rotor.getFrameCount());

        Set<Integer> atlasShapes = OriginalFlashAnimationAssets.collectShapeIds(
                roots, children, shape -> Files.isRegularFile(shapeAsset(shape)));
        assertLeafArt(tankShadow, atlasShapes);
        assertLeafArt(rotor, atlasShapes);
    }

    @Test
    public void authoredShadowLayersKeepTheirDepthMatricesAndColorTransforms()
            throws IOException {
        Map<String, FlashAnimationTimeline> roots =
                OriginalFlashAnimationAssets.loadTimelines(loadScene());
        FlashAnimationTimeline.Layer tankShadow = roots.get("tank_noshade")
                .getFrame(0).layers[0];
        assertEquals(1, tankShadow.depth);
        assertEquals(677, tankShadow.shapeId);
        assertEquals("shadowMC", tankShadow.name);
        assertEquals(-27.25f, tankShadow.tx, 0f);
        assertEquals(-3.15f, tankShadow.ty, .0001f);
        assertEquals(.69921875f, tankShadow.alpha, 0f);
        assertEquals(3, roots.get("tank_noshade").getFrame(0).layers[1].depth);

        FlashAnimationTimeline.Layer chinookShadow = roots.get("chinook_shadow")
                .getFrame(0).layers[0];
        assertEquals(1, chinookShadow.depth);
        assertEquals(2373, chinookShadow.shapeId);
        assertEquals("mc", chinookShadow.name);
        assertEquals(-.15f, chinookShadow.tx, .0001f);
        assertEquals(.25f, chinookShadow.ty, 0f);
        assertEquals(.3984375f, chinookShadow.alpha, 0f);
        FlashColorTransform color = chinookShadow.colorTransform;
        FlashColorTransform.Composition composed = new FlashColorTransform.Composition()
                .setProduct(new FlashColorTransform.Composition(), color);
        for (float multiplier : new float[]{color.redMultiplier, color.greenMultiplier,
                color.blueMultiplier}) {
            assertEquals(-1f, multiplier, 0f);
        }
        for (float addition : new float[]{color.redAdd, color.greenAdd, color.blueAdd}) {
            assertEquals(-1f, addition, 0f); // Source -255 is normalized by 255.
        }
        assertEquals(.3984375f, composed.alphaMultiplier, 0f);
        assertEquals(0f, Math.max(0f, composed.redMultiplier * 1f + composed.redAdd), 0f);
        assertEquals(0f, Math.max(0f, composed.greenMultiplier * .5f
                + composed.greenAdd), 0f);
        assertEquals(0f, Math.max(0f, composed.blueMultiplier * 0f
                + composed.blueAdd), 0f);
    }

    @Test
    public void tankDirectionFramesRetainTheOriginalShadowPlacement() throws IOException {
        JsonValue symbol = findSymbol(loadScene(), 714);
        assertNotNull(symbol);
        JsonValue frames = symbol.get("timeline").get("frames");
        assertEquals(18, frames.size);
        for (int frame = 1; frame < frames.size; frame++) {
            for (JsonValue operation = frames.get(frame).child;
                 operation != null; operation = operation.next) {
                assertFalse("Frame " + (frame + 1) + " must leave depth-one shadow intact",
                        operation.getInt("depth") == 1);
            }
        }
    }

    private static void assertLeafArt(FlashAnimationTimeline timeline, Set<Integer> atlasShapes) {
        for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
            FlashAnimationTimeline.Frame snapshot = timeline.getFrame(frame);
            assertNotNull("Missing shadow frame " + frame, snapshot);
            assertTrue("Empty shadow frame " + frame, snapshot.layers.length > 0);
            for (FlashAnimationTimeline.Layer layer : snapshot.layers) {
                assertTrue("Missing packaged shape " + layer.shapeId,
                        Files.isRegularFile(shapeAsset(layer.shapeId)));
                assertTrue("Shape absent from atlas collection " + layer.shapeId,
                        atlasShapes.contains(layer.shapeId));
            }
        }
    }

    private static JsonValue loadScene() throws IOException {
        try (InputStream file = Files.newInputStream(projectAsset(
                "original/runtime/scene.json.gz"));
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }

    private static JsonValue findSymbol(JsonValue scene, int id) {
        for (JsonValue symbol = scene.get("symbols").child;
             symbol != null; symbol = symbol.next) {
            if (symbol.getInt("id") == id) {
                return symbol;
            }
        }
        return null;
    }

    private static Path shapeAsset(int id) {
        return projectAsset("original/runtime/shapes/" + id + ".svgz");
    }

    private static Path projectAsset(String relative) {
        Path module = Paths.get("app", "src", "main", "assets", relative);
        return Files.isRegularFile(module) ? module
                : Paths.get("src", "main", "assets", relative);
    }
}
