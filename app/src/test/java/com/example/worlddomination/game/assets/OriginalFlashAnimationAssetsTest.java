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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class OriginalFlashAnimationAssetsTest {
    @Test
    public void loadsEveryNestedSatelliteClipAndItsLaserAnchorSymbol() throws IOException {
        JsonValue scene = loadScene();
        FlashAnimationTimeline satellite = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "satellite"), scene.getFloat("fps"));

        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, satellite);
        Set<Integer> atlasShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                Collections.singletonMap("satellite", satellite), nested,
                shapeId -> Files.isRegularFile(shapeAsset(shapeId)));

        int[] satelliteClipIds = {2494, 2557, 2620, 2683, 2746};
        int[] sourceFrameCounts = {32, 32, 33, 32, 33};
        for (int index = 0; index < satelliteClipIds.length; index++) {
            FlashAnimationTimeline clip = nested.get(satelliteClipIds[index]);
            assertNotNull("Nested satellite clip was not loaded", clip);
            assertEquals(sourceFrameCounts[index], clip.getFrameCount());
            for (int frameIndex = 0; frameIndex < clip.getFrameCount(); frameIndex++) {
                FlashAnimationTimeline.Frame frame = clip.getFrame(frameIndex);
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    if (Files.isRegularFile(shapeAsset(layer.shapeId))) {
                        assertTrue("Nested satellite artwork missing from battle atlas: "
                                        + layer.shapeId,
                                atlasShapeIds.contains(layer.shapeId));
                    }
                }
            }
        }
        assertNotNull("The named laserStartMC nested symbol was not loaded",
                nested.get(2433));
    }

    @Test
    public void loadsOriginalSubmarineHullAndTintTimelinesIntoBattleAtlas() throws IOException {
        JsonValue scene = loadScene();
        FlashAnimationTimeline submarine = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "submarine"), scene.getFloat("fps"));
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, submarine);
        Set<Integer> atlasShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                Collections.singletonMap("submarine", submarine), nested,
                shapeId -> Files.isRegularFile(shapeAsset(shapeId)));

        assertEquals(1, submarine.getFrameCount());
        assertNotNull("The submarine hull movie clip was not loaded", nested.get(4833));
        assertNotNull("The submarine tint movie clip was not loaded", nested.get(4834));
        assertEquals(66, nested.get(4833).getFrameCount());
        assertEquals(65, nested.get(4834).getFrameCount());
        for (int symbolId : new int[]{4833, 4834}) {
            FlashAnimationTimeline clip = nested.get(symbolId);
            for (int frameIndex = 0; frameIndex < clip.getFrameCount(); frameIndex++) {
                FlashAnimationTimeline.Frame frame = clip.getFrame(frameIndex);
                if (frame == null) {
                    continue;
                }
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    assertTrue("Missing original submarine shape asset: " + layer.shapeId,
                            Files.isRegularFile(shapeAsset(layer.shapeId)));
                    assertTrue("Submarine shape missing from battle atlas: "
                                    + layer.shapeId,
                            atlasShapeIds.contains(layer.shapeId));
                }
            }
        }
    }

    @Test
    public void loadsOriginalStandaloneAircraftFramesTintAndShadows() throws IOException {
        JsonValue scene = loadScene();
        Map<String, FlashAnimationTimeline> timelines =
                OriginalFlashAnimationAssets.loadTimelines(scene);
        Map<Integer, FlashAnimationTimeline> nested = new LinkedHashMap<>();
        for (String linkage : new String[]{"plane001_shadow", "plane002_shadow"}) {
            nested.putAll(OriginalFlashAnimationAssets.loadNestedTimelines(
                    scene, timelines.get(linkage)));
        }
        Set<Integer> atlasShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                timelines, nested,
                shapeId -> Files.isRegularFile(shapeAsset(shapeId)));

        for (String linkage : new String[]{"plane001", "plane001_tint", "plane002",
                "plane002_tint"}) {
            FlashAnimationTimeline plane = timelines.get(linkage);
            assertNotNull("Missing original airplane Flash linkage " + linkage, plane);
            assertEquals(linkage, 24, plane.getFrameCount());
            for (int frameIndex = 0; frameIndex < plane.getFrameCount(); frameIndex++) {
                FlashAnimationTimeline.Frame frame = plane.getFrame(frameIndex);
                assertNotNull("Missing airplane frame " + linkage + ":" + frameIndex, frame);
                assertTrue("Empty airplane frame " + linkage + ":" + frameIndex,
                        frame.layers.length > 0);
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    assertTrue("Missing original plane art " + layer.shapeId,
                            Files.isRegularFile(shapeAsset(layer.shapeId)));
                    assertTrue("Plane art not included in the battle atlas " + layer.shapeId,
                            atlasShapeIds.contains(layer.shapeId));
                }
            }
        }
        assertNotNull("The fighter's shadow must resolve to the original plane clip",
                nested.get(399));
        assertNotNull("The bomber's shadow must resolve to the original plane clip",
                nested.get(847));
        assertTrue(atlasShapeIds.contains(349));
        assertTrue(atlasShapeIds.contains(800));

        JsonValue fighterTakeoff = scene.get("sound_metadata").get("66");
        JsonValue bomberTakeoff = scene.get("sound_metadata").get("76");
        assertEquals(60, Math.round(fighterTakeoff.getFloat("sample_count")
                / fighterTakeoff.getFloat("rate") * scene.getFloat("fps")));
        assertEquals(37, Math.round(bomberTakeoff.getFloat("sample_count")
                / bomberTakeoff.getFloat("rate") * scene.getFloat("fps")));
    }

    @Test
    public void standaloneAircraftTrailResolvesToOriginalPackagedSmokeShape()
            throws IOException {
        JsonValue scene = loadScene();
        FlashAnimationTimeline trail = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "trail"), scene.getFloat("fps"));
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, trail);
        Set<Integer> atlasShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                Collections.singletonMap("trail", trail), nested,
                shapeId -> Files.isRegularFile(shapeAsset(shapeId)));

        assertEquals(1, trail.getFrameCount());
        assertNotNull(nested.get(4937));
        assertTrue("Flash trail linkage should resolve to its original smoke shape",
                atlasShapeIds.contains(OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID));
        assertTrue("Original smoke SVGZ should remain packaged",
                Files.isRegularFile(shapeAsset(
                        OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID)));
    }

    @Test
    public void loadsOriginalBioloadCloudArtIntoTheBattleAtlas() throws IOException {
        JsonValue scene = loadScene();
        Map<String, FlashAnimationTimeline> timelines =
                OriginalFlashAnimationAssets.loadTimelines(scene);
        FlashAnimationTimeline bioCloud = timelines.get("bioCloud");
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, bioCloud);

        assertNotNull("The source city-contamination clip was not loaded", bioCloud);
        assertEquals(1, bioCloud.getFrameCount());
        FlashAnimationTimeline.Frame bioCloudFrame = bioCloud.getFrame(0);
        assertEquals(1, bioCloudFrame.layers.length);
        assertEquals(2428, bioCloudFrame.layers[0].shapeId);
        FlashAnimationTimeline cloud = nested.get(2428);
        assertNotNull("The nested cloud movie clip was not loaded", cloud);
        assertEquals(2427, cloud.getFrame(0).layers[0].shapeId);
        FlashAnimationTimeline cloudArtwork = nested.get(2427);
        assertNotNull("The nested cloud artwork clip was not loaded", cloudArtwork);
        assertEquals(1, cloudArtwork.getFrame(0).layers.length);
        assertEquals(OriginalFlashAnimationAssets.BIO_CLOUD_SHAPE_ID,
                cloudArtwork.getFrame(0).layers[0].shapeId);
        assertTrue("Original bioCloud SVG artwork is missing",
                Files.isRegularFile(shapeAsset(OriginalFlashAnimationAssets.BIO_CLOUD_SHAPE_ID)));

        Set<Integer> battleShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                timelines, nested,
                shapeId -> Files.isRegularFile(shapeAsset(shapeId)));
        assertTrue("Original bioCloud artwork is not included in the battle atlas",
                battleShapeIds.contains(OriginalFlashAnimationAssets.BIO_CLOUD_SHAPE_ID));
    }

    private static JsonValue loadScene() throws IOException {
        Path asset = projectAsset("original/runtime/scene.json.gz");
        try (InputStream file = Files.newInputStream(asset);
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
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

    private static Path projectAsset(String relative) {
        Path modulePath = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(modulePath)) {
            return modulePath;
        }
        return Paths.get("src", "main", "assets", relative);
    }

    private static Path shapeAsset(int shapeId) {
        return projectAsset("original/runtime/shapes/" + shapeId + ".svgz");
    }
}
