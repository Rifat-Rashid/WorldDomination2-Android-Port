package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class BattlefieldTerrainAssetsTest {
    private static final Path ROOT = Paths.get("src/main/assets/original/ui/terrain");
    private static final String[] FAITHS = {"chr", "bhu", "com", "jew", "mus"};

    @Test public void twentyFiveFaithVariantsMatchThePackagedSourceScene() throws Exception {
        JsonValue manifest = manifest();
        Path sceneFile = Paths.get("src/main/assets/original/runtime/scene.json.gz");
        assertEquals(sha256(sceneFile), manifest.getString("sourceSceneSha256"));
        Map<Integer, JsonValue> symbols = new HashMap<>();
        try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(
                Files.newInputStream(sceneFile)), StandardCharsets.UTF_8)) {
            JsonValue scene = new JsonReader().parse(reader);
            for (JsonValue symbol = scene.get("symbols").child; symbol != null; symbol = symbol.next) {
                symbols.put(symbol.getInt("id"), symbol);
            }
        }
        int count = 0;
        int[] ids = {5253, 5237, 5221, 5205, 5189};
        for (JsonValue island = manifest.get("islands").child; island != null; island = island.next) {
            int id = island.getInt("island");
            assertEquals(ids[id], island.getInt("symbol"));
            FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(symbols.get(ids[id]), 24f);
            for (String faith : FAITHS) {
                JsonValue tile = island.get("variants").get(faith);
                assertNotNull(tile);
                assertEquals(timeline.getFrameNumberForLabel(faith), tile.getInt("frame"));
                assertEquals(timeline.getFrameByLabel(faith).shapeId, tile.getInt("shape"));
                count++;
            }
        }
        assertEquals(25, count);
    }

    @Test public void croppedPngsKeepTheirSourceRegistrationAndIntegrity() throws Exception {
        JsonValue manifest = manifest();
        float scale = manifest.getFloat("rasterScale");
        assertEquals(2f, scale, 0f);
        Set<String> files = new HashSet<>();
        for (JsonValue island = manifest.get("islands").child; island != null; island = island.next) {
            for (String faith : FAITHS) {
                JsonValue tile = island.get("variants").get(faith);
                String file = tile.getString("file");
                assertTrue(files.add(file));
                Path path = ROOT.resolve(file);
                assertEquals(tile.getString("sha256"), sha256(path));
                byte[] png = Files.readAllBytes(path);
                ByteBuffer header = ByteBuffer.wrap(png);
                assertEquals(0x89504e470d0a1a0aL, header.getLong());
                assertEquals(tile.get("pixelSize").getInt(0), header.getInt(16));
                assertEquals(tile.get("pixelSize").getInt(1), header.getInt(20));
                float left = tile.get("sourceOffset").getFloat(0);
                float top = tile.get("sourceOffset").getFloat(1);
                assertTrue(left >= 0 && top >= 0);
                assertTrue(left + header.getInt(16) / scale <= 800f);
                assertTrue(top + header.getInt(20) / scale <= 600f);
            }
        }
        assertEquals(25, files.size());
    }

    @Test public void oceanUsesTheAuthoredMobileSwatchAndPreservesExistingEnhancedArtwork() throws Exception {
        JsonValue ocean = manifest().get("ocean");
        int[] crop = {358, 159, 48, 50};
        for (int index = 0; index < crop.length; index++) {
            assertEquals(crop[index], ocean.get("stageCrop").getInt(index));
        }
        Path path = ROOT.resolve(ocean.getString("file"));
        assertEquals(ocean.getString("sha256"), sha256(path));
        ByteBuffer png = ByteBuffer.wrap(Files.readAllBytes(path));
        assertEquals(144, png.getInt(16));
        assertEquals(150, png.getInt(20));
        assertTrue(Files.isRegularFile(ROOT.resolveSibling("battle-map-hd.png")));
    }

    private static JsonValue manifest() throws Exception {
        JsonValue manifest = new JsonReader().parse(Files.readString(ROOT.resolve("manifest.json")));
        assertEquals(1, manifest.getInt("format"));
        assertEquals(800, manifest.get("sourceStage").getInt(0));
        assertEquals(600, manifest.get("sourceStage").getInt(1));
        assertEquals(5, manifest.get("islands").size);
        return manifest;
    }

    private static String sha256(Path file) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format(java.util.Locale.US, "%02x", value & 255));
        return result.toString();
    }
}
