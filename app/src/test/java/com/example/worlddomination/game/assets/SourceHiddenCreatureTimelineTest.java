package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent source display-list frames and bitmap leaves for hidden creatures. */
public final class SourceHiddenCreatureTimelineTest {
    private static final String FIXTURE_SHA256 =
            "6322fb41ebddf08c78b35fee2d0390e4d159a7f025adcdebad523ba2cc440623";
    private static final Path ASSETS = Paths.get("src/main/assets/original/runtime");

    @Test public void everySourceCreatureFrameAndLeafIsPackaged() throws Exception {
        JsonValue oracle = fixture();
        byte[] sceneBytes = Files.readAllBytes(ASSETS.resolve("scene.json.gz"));
        assertEquals(oracle.getString("sourceSceneSha256"), sha256(sceneBytes));
        JsonValue scene = new JsonReader().parse(new String(
                gunzip(ASSETS.resolve("scene.json.gz")), StandardCharsets.UTF_8));
        Map<String, FlashAnimationTimeline> loaded = OriginalFlashAnimationAssets.loadTimelines(scene);
        MessageDigest shapeDigest = MessageDigest.getInstance("SHA-256");
        Set<Integer> expectedLeaves = new HashSet<>();
        for (JsonValue row = oracle.get("creatures").child; row != null; row = row.next) {
            String name = row.getString("name");
            FlashAnimationTimeline timeline = loaded.get(name);
            assertNotNull(name, timeline);
            assertEquals(name, row.getInt("symbolId"), timeline.symbolId);
            assertEquals(name, row.get("frames").size, timeline.getFrameCount());
            assertEquals("source first-frame stop action", 1,
                    row.get("stopActionFramesOneBased").getInt(0));
            assertNull("source still first frame", timeline.getFrame(0));
            for (int frameIndex = 0; frameIndex < timeline.getFrameCount(); frameIndex++) {
                JsonValue ids = row.get("frames").get(frameIndex);
                FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
                assertEquals(name + " frame " + frameIndex, ids.size,
                        frame == null ? 0 : frame.layers.length);
                for (int layerIndex = 0; layerIndex < ids.size; layerIndex++) {
                    assertEquals(name + " frame " + frameIndex + " layer " + layerIndex,
                            ids.getInt(layerIndex), frame.layers[layerIndex].shapeId);
                }
            }
            assertTrue("creature has direct shape leaves only: " + name,
                    OriginalFlashAnimationAssets.loadNestedTimelines(scene, timeline).isEmpty());
            for (JsonValue leaf = row.get("leaves").child; leaf != null; leaf = leaf.next) {
                int id = leaf.getInt("id");
                expectedLeaves.add(id);
                assertEquals(1, leaf.getInt("bitmapFillCount"));
                String svg = new String(gunzip(ASSETS.resolve("shapes/" + id + ".svgz")),
                        StandardCharsets.UTF_8);
                assertEquals("single image fill " + id, 1, occurrences(svg, "<image "));
                assertEquals("single pattern " + id, 1, occurrences(svg, "<pattern "));
                assertEquals("single path " + id, 1, occurrences(svg, "<path "));
            }
        }
        assertEquals(52, expectedLeaves.size());
        for (JsonValue id = oracle.get("shapeOrder").child; id != null; id = id.next) {
            shapeDigest.update(Files.readAllBytes(
                    ASSETS.resolve("shapes/" + id.asInt() + ".svgz")));
        }
        assertEquals("independent source SVGZ bytes", oracle.getString("sourceShapeBytesSha256"),
                hex(shapeDigest.digest()));
        Set<Integer> collected = OriginalFlashAnimationAssets.collectShapeIds(
                Map.of("orca", loaded.get(OriginalFlashAnimationAssets.HIDDEN_ORCA_TIMELINE),
                       "nessy", loaded.get(OriginalFlashAnimationAssets.HIDDEN_NESSY_TIMELINE)),
                Map.of(), id -> Files.isRegularFile(ASSETS.resolve("shapes/" + id + ".svgz")));
        assertTrue("all source creature leaves reach the atlas collector",
                collected.containsAll(expectedLeaves));
        int withheld = 5764;
        Set<Integer> negative = OriginalFlashAnimationAssets.collectShapeIds(
                Map.of("orca", loaded.get(OriginalFlashAnimationAssets.HIDDEN_ORCA_TIMELINE),
                       "nessy", loaded.get(OriginalFlashAnimationAssets.HIDDEN_NESSY_TIMELINE)),
                Map.of(), id -> id != withheld && Files.isRegularFile(
                        ASSETS.resolve("shapes/" + id + ".svgz")));
        assertFalse("missing-leaf negative control", negative.contains(withheld));
        assertFalse("negative control fails full leaf coverage", negative.containsAll(expectedLeaves));
    }

    private static JsonValue fixture() throws Exception {
        try (InputStream in = SourceHiddenCreatureTimelineTest.class.getResourceAsStream(
                "/source-hidden-creatures.json")) {
            assertNotNull(in);
            byte[] bytes = in.readAllBytes();
            assertEquals(FIXTURE_SHA256, sha256(bytes));
            return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] gunzip(Path path) throws Exception {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(path))) {
            return in.readAllBytes();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int pos = text.indexOf(token); pos >= 0; pos = text.indexOf(token, pos + token.length())) {
            count++;
        }
        return count;
    }
}
