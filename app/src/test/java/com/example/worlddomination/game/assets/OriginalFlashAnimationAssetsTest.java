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
    public void centerDialogUsesSourceResearchAndMassMediaFramesAndArtwork() throws IOException {
        JsonValue scene = loadScene();
        FlashAnimationTimeline centerScreen = OriginalFlashAnimationAssets.loadTimelineBySymbolId(
                scene, OriginalFlashAnimationAssets.CENTER_UI_SYMBOL_ID);

        assertEquals(10, centerScreen.getFrameNumberForLabel("research"));
        assertEquals(14, centerScreen.getFrameNumberForLabel("religion"));
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, centerScreen,
                        new int[]{9, 13});
        Set<Integer> techClipIds = Set.of(3396, 3419, 3463, 3516, 3561, 3606, 3654);
        for (int techClipId : techClipIds) {
            assertTrue("Missing original technology panel " + techClipId,
                    nested.containsKey(techClipId));
        }
        assertTrue("Missing original mass-media speaker clip", nested.containsKey(3727));

        for (int frameIndex : new int[]{9, 13}) {
            FlashAnimationTimeline.Frame frame = centerScreen.getFrame(frameIndex);
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                assertTrue("Center-screen source layer is missing: " + layer.shapeId,
                        nested.containsKey(layer.shapeId)
                                || Files.isRegularFile(shapeAsset(layer.shapeId))
                                || Files.isRegularFile(textAsset(layer.shapeId)));
            }
        }
        for (FlashAnimationTimeline clip : nested.values()) {
            for (int frameIndex = 0; frameIndex < clip.getFrameCount(); frameIndex++) {
                FlashAnimationTimeline.Frame frame = clip.getFrame(frameIndex);
                if (frame == null) {
                    continue;
                }
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    assertTrue("Center-screen artwork is missing: " + layer.shapeId,
                            nested.containsKey(layer.shapeId)
                                    || Files.isRegularFile(shapeAsset(layer.shapeId))
                                    || Files.isRegularFile(textAsset(layer.shapeId)));
                }
            }
        }
    }

    @Test
    public void earlyAtlasIncludesResearchAndMassMediaDialogArt() throws IOException {
        JsonValue scene = loadScene();
        FlashAnimationTimeline centerScreen = OriginalFlashAnimationAssets.loadTimelineBySymbolId(
                scene, OriginalFlashAnimationAssets.CENTER_UI_SYMBOL_ID);
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, centerScreen,
                        new int[]{9, 13});

        Set<Integer> artwork = OriginalFlashAnimationAssets.collectCenterUiArtworkIds(
                centerScreen, nested, new int[]{9, 13},
                shapeId -> Files.isRegularFile(shapeAsset(shapeId))
                        || Files.isRegularFile(textAsset(shapeId)));

        for (int shapeId : new int[]{3258, 3278, 3281, 3306, 3331, 3728, 3737}) {
            assertTrue("Missing center-screen art from the early atlas: " + shapeId,
                    artwork.contains(shapeId));
            assertTrue("Missing center-screen source vector " + shapeId,
                    Files.isRegularFile(shapeAsset(shapeId))
                            || Files.isRegularFile(textAsset(shapeId)));
        }
    }

    @Test
    public void firstCenterDialogAtlasPrioritizesVisiblePanelsAndControls()
            throws IOException {
        JsonValue scene = loadScene();
        FlashAnimationTimeline centerScreen = OriginalFlashAnimationAssets.loadTimelineBySymbolId(
                scene, OriginalFlashAnimationAssets.CENTER_UI_SYMBOL_ID);
        int[] sourceFrames = {9, 13};
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, centerScreen,
                        sourceFrames);
        java.util.function.IntPredicate hasArtwork = shapeId ->
                Files.isRegularFile(shapeAsset(shapeId))
                        || Files.isRegularFile(textAsset(shapeId));

        Set<Integer> initial = OriginalFlashAnimationAssets.collectInitialCenterUiArtworkIds(
                centerScreen, nested, sourceFrames, hasArtwork);
        Set<Integer> complete = OriginalFlashAnimationAssets.collectCenterUiArtworkIds(
                centerScreen, nested, sourceFrames, hasArtwork);

        assertTrue("The prioritized dialog atlas should contain original artwork",
                initial.size() > 0);
        assertTrue("The battle atlas should retain deferred technology variants",
                initial.size() < complete.size());
        for (int shapeId : new int[]{3258, 3278, 3281, 3306, 3331, 3397, 3401, 3728, 3737}) {
            assertTrue("Missing first-frame dialog artwork " + shapeId,
                    initial.contains(shapeId));
        }
    }

    @Test
    public void lateAtlasDoesNotRequireDialogArtAlreadyPackedInTheEarlyAtlas() {
        Set<Integer> allFlashShapes = Set.of(3278, 3281, 4000, 5000);
        Set<Integer> setupPortraits = Set.of(5000);
        Set<Integer> earlyDialogArt = Set.of(3278, 3281);

        Set<Integer> remaining = OriginalFlashAnimationAssets.excludeAlreadyPackedShapes(
                allFlashShapes, setupPortraits, earlyDialogArt);

        assertEquals(Set.of(4000), remaining);
    }

    @Test
    public void textAssetRegistrationOffsetPreservesItsFlashOrigin() throws IOException {
        String svg;
        try (InputStream file = Files.newInputStream(textAsset(3281));
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            StringBuilder source = new StringBuilder();
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                source.append(buffer, 0, count);
            }
            svg = source.toString();
        }

        float[] origin = new float[2];
        OriginalFlashAnimationAssets.parseRegistrationOrigin(svg, origin);

        assertEquals(52.25f, origin[0], 0.001f);
        assertEquals(-0.35f, origin[1], 0.001f);
    }

    @Test
    public void loadsOriginalActionBuildingFramesAndReligionVariantsIntoTheBattleAtlas()
            throws IOException {
        JsonValue scene = loadScene();
        Map<String, FlashAnimationTimeline> timelines =
                OriginalFlashAnimationAssets.loadTimelines(scene);
        FlashAnimationTimeline buildings = timelines.get(
                OriginalFlashAnimationAssets.ACTION_BUILDINGS_TIMELINE);

        assertNotNull("Missing source action-building root timeline", buildings);
        assertEquals(OriginalFlashAnimationAssets.ACTION_BUILDINGS_SYMBOL_ID,
                buildings.symbolId);
        assertEquals(21, buildings.getFrameCount());

        String[] sourceFrames = {
                "diplomacy", "factory", "helipad", "base", "propaganda", "tech",
                "resources", "religion", "antiAir_fire", "antiRocket_fire", "silo",
                "airfield", "produce", "hatch", "sub", "ship", "space",
                "antiAir_still", "antiRocket_still", "spyCenter"
        };
        int[] sourceFrameNumbers = {
                2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21
        };
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, buildings);
        Set<Integer> battleShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                timelines, nested, shapeId -> Files.isRegularFile(shapeAsset(shapeId)));

        for (int index = 0; index < sourceFrames.length; index++) {
            String label = sourceFrames[index];
            FlashAnimationTimeline.Frame frame = buildings.getFrameByLabel(label);
            assertEquals("Unexpected source frame number for " + label,
                    sourceFrameNumbers[index], buildings.getFrameNumberForLabel(label));
            assertNotNull("Missing original Flash frame " + label, frame);
            // Battle.createToShowABArray selects "factory" for production; the
            // separate "produce" library frame is not shown during turn playback.
            if ("produce".equals(label)) {
                continue;
            }
            assertTrue("Empty source action-building frame " + label,
                    frame.layers.length > 0);
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                assertTrue("Missing source symbol or artwork for " + label + ": "
                                + layer.shapeId,
                        nested.containsKey(layer.shapeId)
                                || Files.isRegularFile(shapeAsset(layer.shapeId)));
            }
        }

        FlashAnimationTimeline.Frame religionFrame = buildings.getFrameByLabel("religion");
        FlashAnimationTimeline religion = null;
        for (FlashAnimationTimeline.Layer layer : religionFrame.layers) {
            FlashAnimationTimeline candidate = nested.get(layer.shapeId);
            if (candidate != null && candidate.getFrameByLabel("mus") != null) {
                religion = candidate;
                break;
            }
        }
        assertNotNull("Missing nested religion action-building clip", religion);
        for (String religionCode : new String[]{"mus", "jew", "com", "chr", "bhu", "disco"}) {
            assertNotNull("Missing original religion variant " + religionCode,
                    religion.getFrameByLabel(religionCode));
        }
        assertTrue("The action-building source art was not added to the cached battle atlas",
                !battleShapeIds.isEmpty());
    }

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
    public void productionSliderUsesTheOriginalFlashHandleArtwork() throws IOException {
        JsonValue scene = loadScene();
        Map<String, FlashAnimationTimeline> timelines =
                OriginalFlashAnimationAssets.loadTimelines(scene);
        FlashAnimationTimeline slider = timelines.get("interface_center_slider_1");
        assertNotNull("Missing original production slider timeline", slider);

        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, slider);
        Set<Integer> shapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                timelines, nested, shapeId -> Files.isRegularFile(shapeAsset(shapeId)));

        assertTrue("Original white slider triangle is not included in the Flash atlas",
                shapeIds.contains(OriginalFlashAnimationAssets.PRODUCTION_SLIDER_HANDLE_SHAPE_ID));
        assertTrue("Original slider triangle SVGZ was not packaged",
                Files.isRegularFile(shapeAsset(
                        OriginalFlashAnimationAssets.PRODUCTION_SLIDER_HANDLE_SHAPE_ID)));
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
    public void loadsOriginalReligionSpecificAntiAirFramesIntoTheBattleAtlas()
            throws IOException {
        JsonValue scene = loadScene();
        Map<String, FlashAnimationTimeline> timelines =
                OriginalFlashAnimationAssets.loadTimelines(scene);
        FlashAnimationTimeline buildings = timelines.get("buildings_antiAir");
        assertNotNull("Missing original anti-air action-building timeline", buildings);
        assertEquals(5, buildings.getFrameCount());

        int[] religionSymbols = {5107, 5072, 4349, 5035, 4998};
        for (int frameIndex = 0; frameIndex < religionSymbols.length; frameIndex++) {
            FlashAnimationTimeline.Frame religion = buildings.getFrame(frameIndex);
            assertNotNull("Missing anti-air religion frame " + frameIndex, religion);
            assertEquals(religionSymbols[frameIndex], religion.layers[0].shapeId);
        }

        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, buildings);
        Set<Integer> battleShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                timelines, nested, shapeId -> Files.isRegularFile(shapeAsset(shapeId)));
        for (int symbolId : religionSymbols) {
            FlashAnimationTimeline religion = nested.get(symbolId);
            assertNotNull("Missing nested anti-air religion clip " + symbolId, religion);
            assertTrue("Religion clip does not contain the source 18-way turret animation",
                    religion.getFrameCount() >= 36);
            for (int direction = 0; direction < 18; direction++) {
                FlashAnimationTimeline.Frame frame = religion.getFrame(direction * 2);
                assertNotNull("Missing anti-air direction " + direction, frame);
                assertTrue("Empty anti-air direction " + direction, frame.layers.length > 0);
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    assertTrue("Missing source anti-air art " + layer.shapeId,
                            Files.isRegularFile(shapeAsset(layer.shapeId)));
                    assertTrue("Anti-air art missing from the cached battle atlas "
                                    + layer.shapeId,
                            battleShapeIds.contains(layer.shapeId));
                }
            }
        }
        assertTrue("Communist anti-air art must use the original symbol 4349 chain",
                nested.containsKey(4349));
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

    @Test
    public void loadsOriginalSpyActionBuildingFramesReligionVariantsAndArt()
            throws IOException {
        JsonValue scene = loadScene();
        Map<String, FlashAnimationTimeline> timelines =
                OriginalFlashAnimationAssets.loadTimelines(scene);
        FlashAnimationTimeline buildings = timelines.get(
                OriginalFlashAnimationAssets.ACTION_BUILDINGS_TIMELINE);
        assertNotNull("The source action-building root timeline was not loaded", buildings);
        assertEquals(OriginalFlashAnimationAssets.ACTION_BUILDINGS_SYMBOL_ID,
                buildings.symbolId);
        assertEquals(24f, buildings.frameRate, 0f);
        assertEquals(21, buildings.getFrameCount());
        assertEquals(21, buildings.getFrameNumberForLabel("spyCenter"));

        String[] sourceFrames = {"factory", "tech", "resources", "religion", "spyCenter"};
        Map<Integer, FlashAnimationTimeline> nested =
                OriginalFlashAnimationAssets.loadNestedTimelines(scene, buildings);
        Set<Integer> atlasShapeIds = OriginalFlashAnimationAssets.collectShapeIds(
                Collections.singletonMap(
                        OriginalFlashAnimationAssets.ACTION_BUILDINGS_TIMELINE, buildings),
                nested, shapeId -> Files.isRegularFile(shapeAsset(shapeId)));

        for (String label : sourceFrames) {
            FlashAnimationTimeline.Frame frame = buildings.getFrameByLabel(label);
            assertNotNull("Missing original spy action-building frame " + label, frame);
            assertTrue("Empty original spy action-building frame " + label,
                    frame.layers.length > 0);

            FlashAnimationTimeline religionBuilding = null;
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                if ("citySelectMC".equals(layer.name)) {
                    continue;
                }
                FlashAnimationTimeline child = nested.get(layer.shapeId);
                if (child != null) {
                    religionBuilding = child;
                    break;
                }
            }
            assertNotNull("Missing nested original building symbol for " + label,
                    religionBuilding);
            if ("resources".equals(label)) {
                // The source calls gotoAndStop(religionCode) on this unlabeled clip;
                // Flash leaves its first frame selected when the label is absent.
                assertNotNull(religionBuilding.getFrame(0));
                assertTrue(religionBuilding.getFrameByLabel("chr") == null);
            } else {
                boolean hasReligionVariant = false;
                for (String code : new String[]{"bhu", "chr", "com", "jew", "mus"}) {
                    hasReligionVariant |= religionBuilding.getFrameByLabel(code) != null;
                }
                assertTrue("Missing source religion frame for spy building " + label,
                        hasReligionVariant);
            }
        }

        int sourceArtworkCount = 0;
        for (FlashAnimationTimeline clip : nested.values()) {
            for (int frameIndex = 0; frameIndex < clip.getFrameCount(); frameIndex++) {
                FlashAnimationTimeline.Frame frame = clip.getFrame(frameIndex);
                if (frame == null) {
                    continue;
                }
                for (FlashAnimationTimeline.Layer layer : frame.layers) {
                    if (nested.containsKey(layer.shapeId)) {
                        continue;
                    }
                    assertTrue("Missing original action-building artwork " + layer.shapeId,
                            Files.isRegularFile(shapeAsset(layer.shapeId)));
                    assertTrue("Action-building art missing from the cached battle atlas "
                                    + layer.shapeId,
                            atlasShapeIds.contains(layer.shapeId));
                    sourceArtworkCount++;
                }
            }
        }
        assertTrue("Expected source Flash art in the action-building subtree",
                sourceArtworkCount > 0);
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

    private static Path textAsset(int textId) {
        return projectAsset("original/runtime/texts/" + textId + ".svgz");
    }
}
