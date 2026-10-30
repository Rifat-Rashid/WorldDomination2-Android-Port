package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import org.junit.Test;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class OriginalCityAssetsTest {
    @Test
    public void manifestRetainsAllSourceThemesLivingAndRuinFrames() throws Exception {
        Path root = cityAssetRoot();
        JsonValue manifest = new JsonReader().parse(
                new String(Files.readAllBytes(root.resolve("manifest.json")), "UTF-8"));
        JsonValue symbols = manifest.get("symbols");
        Set<Integer> characterIds = new HashSet<>();
        int[] expectedFrameNumbers = {1, 2, 3, 4, 5, 6, 10, 11, 12, 13, 14, 15};
        int imageCount = 0;

        assertEquals(0.8f, manifest.getFloat("scale"), 0f);
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            characterIds.add(symbol.getInt("character_id"));
            JsonValue frames = symbol.get("frames");
            assertEquals(expectedFrameNumbers.length, frames.size);
            for (int frameNumber : expectedFrameNumbers) {
                JsonValue frame = frames.get(Integer.toString(frameNumber));
                assertTrue("Missing source frame " + frameNumber, frame != null);
                Path imagePath = root.resolve(frame.getString("file"));
                assertTrue("Missing packaged source frame " + imagePath,
                        Files.isRegularFile(imagePath));
                int[] dimensions = pngDimensions(imagePath);
                JsonValue crop = frame.get("crop");
                assertEquals(crop.getInt(2) - crop.getInt(0), dimensions[0]);
                assertEquals(crop.getInt(3) - crop.getInt(1), dimensions[1]);
                imageCount++;
            }
        }

        assertEquals(5, symbols.size);
        assertEquals(60, imageCount);
        assertEquals(new HashSet<>(java.util.Arrays.asList(1337, 1363, 1388, 1414, 1441)),
                characterIds);
    }

    private static int[] pngDimensions(Path imagePath) throws Exception {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(imagePath))) {
            byte[] signature = new byte[8];
            input.readFully(signature);
            assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, signature);
            assertEquals(13, input.readInt());
            byte[] type = new byte[4];
            input.readFully(type);
            assertEquals("IHDR", new String(type, "US-ASCII"));
            return new int[]{input.readInt(), input.readInt()};
        }
    }

    private static Path cityAssetRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path moduleAssets = current.resolve("src/main/assets/original/cities");
            if (Files.isRegularFile(moduleAssets.resolve("manifest.json"))) {
                return moduleAssets;
            }
            Path projectAssets = current.resolve("app/src/main/assets/original/cities");
            if (Files.isRegularFile(projectAssets.resolve("manifest.json"))) {
                return projectAssets;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the Android city asset manifest");
    }

    @Test
    public void religionCodesSelectTheSourceBuildingSetSymbols() {
        assertEquals(1337, OriginalCityAssets.characterIdForReligion("com"));
        assertEquals(1363, OriginalCityAssets.characterIdForReligion("chr"));
        assertEquals(1388, OriginalCityAssets.characterIdForReligion("bhu"));
        assertEquals(1414, OriginalCityAssets.characterIdForReligion("jew"));
        assertEquals(1441, OriginalCityAssets.characterIdForReligion("mus"));
        assertEquals(1363, OriginalCityAssets.characterIdForReligion("unknown"));
    }
}
