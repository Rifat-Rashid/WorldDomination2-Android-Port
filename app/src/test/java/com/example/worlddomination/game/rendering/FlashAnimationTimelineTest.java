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
        expectedFrameCounts.put("explosion001", 21);
        expectedFrameCounts.put("chinook", 37);
        expectedFrameCounts.put("chinook_shadow", 1);
        expectedFrameCounts.put("tankBase", 18);
        expectedFrameCounts.put("tank_noshade", 18);
        expectedFrameCounts.put("tankNoShade_shadow", 1);
        expectedFrameCounts.put("tankTop_blue", 37);
        expectedFrameCounts.put("tankTop_red", 37);
        expectedFrameCounts.put("tankTop_orange", 37);
        expectedFrameCounts.put("tankTop_yellow", 37);
        expectedFrameCounts.put("tankTop_green", 36);
        expectedFrameCounts.put("apache", 37);
        expectedFrameCounts.put("apache_tint", 37);
        expectedFrameCounts.put("apache_shadow", 1);
        expectedFrameCounts.put("marine", 432);
        expectedFrameCounts.put("soldier001_tint", 217);
        expectedFrameCounts.put("soldier001_shoot", 73);
        expectedFrameCounts.put("soldier001_shoot_tint", 73);
        expectedFrameCounts.put("suicide_bomber", 216);
        expectedFrameCounts.put("suicide_bomber_tint", 432);

        Map<Integer, String> sourceSymbolsById = new LinkedHashMap<>();
        JsonValue sourceSymbols = scene.get("symbols");
        for (JsonValue sourceSymbol = sourceSymbols.child; sourceSymbol != null;
             sourceSymbol = sourceSymbol.next) {
            sourceSymbolsById.put(sourceSymbol.getInt("id"),
                    sourceSymbol.getString("linkage", ""));
        }

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
                    float determinant = layer.a * layer.d - layer.b * layer.c;
                    assertTrue("Unexpected singular source frame in " + expected.getKey(),
                            Math.abs(determinant) > 0.0001f);
                    assertEquals("Source artwork uses rotation and reflection transforms only: "
                                    + expected.getKey(),
                            0f, layer.a * layer.c + layer.b * layer.d, 0.01f);
                    if (!Files.isRegularFile(shapeAsset(layer.shapeId))) {
                        String nestedLinkage = sourceSymbolsById.get(layer.shapeId);
                        assertNotNull("Missing original shape or nested symbol "
                                + layer.shapeId, nestedLinkage);
                        if (!nestedLinkage.isEmpty()) {
                            assertTrue("Nested movie clip is not loaded: " + nestedLinkage,
                                    expectedFrameCounts.containsKey(nestedLinkage));
                        }
                    }
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
    public void rocketAndTrailUseThePackagedFlashTimelinesAndNestedSourceArtwork()
            throws IOException {
        JsonValue scene = loadSourceScene();
        FlashAnimationTimeline rocket = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "rocket"), scene.getFloat("fps"));
        FlashAnimationTimeline trail = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "trail"), scene.getFloat("fps"));
        assertEquals(3, rocket.getFrameCount());
        assertEquals(1, trail.getFrameCount());
        assertTrue("Rocket body art is part of the original source symbol",
                containsShape(rocket, 792));
        assertTrue(Files.isRegularFile(shapeAsset(792)));

        JsonValue smokeSymbol = findSymbolById(scene, 4937);
        assertNotNull(smokeSymbol);
        assertTrue(containsShape(FlashAnimationTimeline.fromSymbol(
                smokeSymbol, scene.getFloat("fps")), 4936));
        assertTrue("The trail uses its original Flash smoke puff artwork",
                Files.isRegularFile(shapeAsset(4936)));
    }

    @Test
    public void carrierAircraftUsesItsPackaged24FrameFlashTimelineAndShapes()
            throws IOException {
        JsonValue scene = loadSourceScene();
        FlashAnimationTimeline carrierPlane = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "ship_plane"), scene.getFloat("fps"));
        JsonValue takeoffSound = scene.get("sound_metadata").get("76");

        assertEquals(24, carrierPlane.getFrameCount());
        assertEquals("ship_plane", carrierPlane.linkage);
        assertNotNull("Missing original carrier takeoff sound metadata", takeoffSound);
        assertEquals("Carrier takeoff sound timing is rounded to source ticks", 37,
                Math.round(takeoffSound.getFloat("sample_count")
                        / takeoffSound.getFloat("rate") * scene.getFloat("fps")));
        for (int frameIndex = 0; frameIndex < carrierPlane.getFrameCount(); frameIndex++) {
            FlashAnimationTimeline.Frame frame = carrierPlane.getFrame(frameIndex);
            assertNotNull("Missing carrier aircraft frame " + frameIndex, frame);
            assertTrue("Carrier aircraft frame must contain original source artwork",
                    frame.layers.length > 0);
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                assertTrue("Missing source aircraft shape " + layer.shapeId,
                        Files.isRegularFile(shapeAsset(layer.shapeId)));
            }
        }
    }

    @Test
    public void satelliteFlashLabelsSelectTheFiveOriginalReligionFrames() throws IOException {
        JsonValue scene = loadSourceScene();
        FlashAnimationTimeline satellite = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "satellite"), scene.getFloat("fps"));

        assertEquals(25, satellite.getFrameCount());
        assertEquals(1, satellite.getFrameNumberForLabel("mus"));
        assertEquals(5, satellite.getFrameNumberForLabel("jew"));
        assertEquals(10, satellite.getFrameNumberForLabel("com"));
        assertEquals(15, satellite.getFrameNumberForLabel("chr"));
        assertEquals(20, satellite.getFrameNumberForLabel("bhu"));
        assertNull(satellite.getFrameByLabel("unknown"));
        for (String religion : new String[]{"mus", "jew", "com", "chr", "bhu"}) {
            assertNotNull("Missing source satellite frame for " + religion,
                    satellite.getFrameByLabel(religion));
        }
        assertTrue("Satellite body is original source artwork",
                Files.isRegularFile(shapeAsset(2430)));
    }

    @Test
    public void satelliteReligionClipsLoadTheirAnimatedBodyAndLaserAnchorFrames()
            throws IOException {
        JsonValue scene = loadSourceScene();
        FlashAnimationTimeline satellite = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "satellite"), scene.getFloat("fps"));
        int[] nestedSymbolIds = {2494, 2557, 2620, 2683, 2746};
        int[] expectedFrameCounts = {32, 32, 33, 32, 33};
        String[] religions = {"mus", "jew", "com", "chr", "bhu"};

        for (int index = 0; index < religions.length; index++) {
            FlashAnimationTimeline.Frame religionFrame =
                    satellite.getFrameByLabel(religions[index]);
            assertNotNull(religionFrame);
            assertEquals(2430, religionFrame.layers[0].shapeId);
            assertEquals(nestedSymbolIds[index], religionFrame.layers[1].shapeId);

            FlashAnimationTimeline nested = FlashAnimationTimeline.fromSymbol(
                    findSymbolById(scene, nestedSymbolIds[index]), scene.getFloat("fps"));
            assertEquals(expectedFrameCounts[index], nested.getFrameCount());
            FlashAnimationTimeline.Layer firstMarker = null;
            FlashAnimationTimeline.Layer lastMarker = null;
            for (int frameIndex = 0; frameIndex < nested.getFrameCount(); frameIndex++) {
                FlashAnimationTimeline.Frame frame = nested.getFrame(frameIndex);
                assertNotNull("Missing nested satellite frame " + frameIndex, frame);
                assertTrue("Missing original satellite body frame " + frame.layers[0].shapeId,
                        Files.isRegularFile(shapeAsset(frame.layers[0].shapeId)));
                FlashAnimationTimeline.Layer marker = findLayer(frame, "laserStartMC");
                assertNotNull("Missing animated laser anchor in frame " + frameIndex, marker);
                assertEquals("The source laser marker is invisible art", 0f,
                        marker.alpha, 0f);
                if (frameIndex == 0) {
                    firstMarker = marker;
                }
                if (frameIndex == nested.getFrameCount() - 1) {
                    lastMarker = marker;
                }
            }
            assertNotNull(firstMarker);
            assertNotNull(lastMarker);
            assertTrue("Laser anchor follows the satellite animation",
                    firstMarker.tx != lastMarker.tx || firstMarker.ty != lastMarker.ty);
        }
    }

    @Test
    public void setupPortraitTimelineContainsEverySelectableOriginalLeaderPortrait()
            throws IOException {
        JsonValue scene = loadSourceScene();
        FlashAnimationTimeline faces = FlashAnimationTimeline.fromSymbol(
                findSymbol(scene, "faces"), scene.getFloat("fps"));

        assertEquals(20, faces.getFrameCount());
        for (int frameIndex = 0; frameIndex < 19; frameIndex++) {
            FlashAnimationTimeline.Frame frame = faces.getFrame(frameIndex);
            assertNotNull("Missing original leader portrait frame " + frameIndex, frame);
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                assertTrue("Missing original leader portrait shape " + layer.shapeId,
                        Files.isRegularFile(shapeAsset(layer.shapeId)));
            }
        }
    }

    @Test
    public void smokePuffFlashShapeIsPackagedAsVectorArtForNativeRasterization()
            throws IOException {
        String smokeSvg = loadShapeSvg(4936);
        assertFalse(smokeSvg.contains("data:image/webp;base64,"));
        assertTrue("Smoke puff keeps its source SVG path", smokeSvg.contains("<path "));
        assertTrue("Smoke puff keeps its source radial gradient",
                smokeSvg.contains("<radialGradient"));
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

    private static JsonValue findSymbolById(JsonValue scene, int id) {
        JsonValue symbols = scene.get("symbols");
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            if (symbol.getInt("id") == id) {
                return symbol;
            }
        }
        return null;
    }

    private static boolean containsShape(FlashAnimationTimeline timeline, int shapeId) {
        for (int frameIndex = 0; frameIndex < timeline.getFrameCount(); frameIndex++) {
            FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
            if (frame != null) {
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    if (layer.shapeId == shapeId) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static FlashAnimationTimeline.Layer findLayer(
            FlashAnimationTimeline.Frame frame, String name) {
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            if (name.equals(layer.name)) {
                return layer;
            }
        }
        return null;
    }

    private static Path shapeAsset(int shapeId) {
        return projectAsset("original/runtime/shapes/" + shapeId + ".svgz");
    }

    private static String loadShapeSvg(int shapeId) throws IOException {
        try (InputStream file = Files.newInputStream(shapeAsset(shapeId));
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            StringBuilder svg = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                svg.append(buffer, 0, count);
            }
            return svg.toString();
        }
    }

    private static Path projectAsset(String relative) {
        Path modulePath = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(modulePath)) {
            return modulePath;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
