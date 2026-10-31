package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;

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
import static org.junit.Assert.assertNotNull;

public final class CityBioloadCloudRendererTest {
    @Test
    public void flattensTheOriginalNestedCloudArtworkAndTransforms() throws IOException {
        JsonValue scene = loadScene();
        float frameRate = scene.getFloat("fps");
        JsonValue bioCloudSymbol = findSymbol(scene, "bioCloud");
        assertNotNull(bioCloudSymbol);
        FlashAnimationTimeline bioCloud = FlashAnimationTimeline.fromSymbol(
                bioCloudSymbol, frameRate);
        Map<Integer, FlashAnimationTimeline> nested = new LinkedHashMap<>();
        for (int symbolId : new int[]{2428, 2427}) {
            JsonValue symbol = findSymbol(scene, symbolId);
            assertNotNull(symbol);
            nested.put(symbolId, FlashAnimationTimeline.fromSymbol(symbol, frameRate));
        }

        CityBioloadCloudRenderer.ResolvedArtwork[] artwork =
                CityBioloadCloudRenderer.resolveArtwork(bioCloud, nested::get,
                        CityBioloadCloudRendererTest::hasShape);

        assertEquals(1, artwork.length);
        CityBioloadCloudRenderer.ResolvedArtwork cloud = artwork[0];
        assertEquals(OriginalFlashAnimationAssets.BIO_CLOUD_SHAPE_ID, cloud.shapeId);
        assertEquals(2.5600128f, cloud.a, 0.00001f);
        assertEquals(2.5600128f, cloud.d, 0.00001f);
        assertEquals(-12.450064f, cloud.tx, 0.0001f);
        assertEquals(-12.450064f, cloud.ty, 0.0001f);
        assertEquals(1f, cloud.alpha, 0.00001f);
    }

    private static JsonValue loadScene() throws IOException {
        Path modulePath = Paths.get("app", "src", "main", "assets",
                "original", "runtime", "scene.json.gz");
        Path asset = Files.isRegularFile(modulePath)
                ? modulePath
                : Paths.get("src", "main", "assets", "original", "runtime",
                        "scene.json.gz");
        try (InputStream file = Files.newInputStream(asset);
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip,
                     StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }

    private static boolean hasShape(int shapeId) {
        Path modulePath = Paths.get("app", "src", "main", "assets", "original",
                "runtime", "shapes", shapeId + ".svgz");
        Path asset = Files.isRegularFile(modulePath)
                ? modulePath
                : Paths.get("src", "main", "assets", "original", "runtime",
                        "shapes", shapeId + ".svgz");
        return Files.isRegularFile(asset);
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

    private static JsonValue findSymbol(JsonValue scene, int symbolId) {
        JsonValue symbols = scene.get("symbols");
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            if (symbolId == symbol.getInt("id")) {
                return symbol;
            }
        }
        return null;
    }
}
