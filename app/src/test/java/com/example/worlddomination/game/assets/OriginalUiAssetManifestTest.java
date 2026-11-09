package com.example.worlddomination.game.assets;

import org.junit.Test;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class OriginalUiAssetManifestTest {
    @Test
    public void resourceDialogUsesHighResolutionArtAndSeparateScalableText() throws IOException {
        Path assets = findUiAssets();
        int[] art = pngSize(assets.resolve("resource-menu-hd.png"));
        assertEquals(1460, art[0]);
        assertEquals(1100, art[1]);
        assertEquals(365f / 275f, (float) art[0] / art[1], 0.001f);
        String font = Files.readString(assets.resolve("fonts/resource-arial-48.fnt"));
        assertTrue(font.contains("size=48"));
        assertTrue(font.contains("file=\"resource-arial-48.png\""));
        for (char glyph : "Budget: Population Resource OIL Estimated income CANCEL ACCEPT0123456789.,".toCharArray()) {
            assertTrue("Dialog font is missing " + glyph,
                    font.contains("char id=" + (int) glyph + " "));
        }
        assertTrue(Files.isRegularFile(assets.resolve("fonts/resource-arial-48.png")));
    }

    @Test
    public void actionTubeAtlasPackagesEveryOriginalOpeningAndSetFrame() throws IOException {
        Path assets = findUiAssets();
        java.util.List<String> atlas = Files.readAllLines(assets.resolve("action-tubes.atlas"));
        for (int frame = 1; frame <= 11; frame++) {
            assertTrue("Missing original action tube frame " + frame, atlas.contains("tube" + frame));
        }
        int[] size = pngSize(assets.resolve("action-tubes.png"));
        assertTrue(size[0] <= 2048 && size[1] <= 2048);
    }

    private static final String[] REQUIRED_ATLAS_REGIONS = {
            "metalTile", "button", "resourceModal", "inventoryCounter",
            "inventorySwitch", "inventoryRadio", "actionFirst", "actionSecond",
            "actionConfirm", "portrait1", "portrait2", "portrait3", "portrait4",
            "navResources", "navProduction", "navTechnology", "navMedia", "navAttack"
    };

    @Test
    public void atlasIncludesEveryRegionUsedByTheLandscapeUi() throws IOException {
        Path uiAssets = findUiAssets();
        java.util.List<String> atlasLines = Files.readAllLines(
                uiAssets.resolve("ui.atlas"), StandardCharsets.UTF_8);

        for (String region : REQUIRED_ATLAS_REGIONS) {
            assertTrue("Atlas is missing region " + region,
                    atlasLines.contains(region));
        }
    }

    @Test
    public void enhancedBattleMapHasValidPngDimensionsAndPreservesSourceAspectRatio()
            throws IOException {
        Path uiAssets = findUiAssets();
        int[] source = pngSize(uiAssets.resolve("battle-map.png"));
        int[] enhanced = pngSize(uiAssets.resolve("battle-map-hd.png"));

        assertEquals(523, source[0]);
        assertEquals(318, source[1]);
        assertTrue(enhanced[0] >= source[0] * 2);
        assertTrue(enhanced[1] >= source[1] * 2);
        assertEquals((float) source[0] / source[1],
                (float) enhanced[0] / enhanced[1], 0.003f);
    }

    @Test
    public void productionMenuArtworkUsesTheOriginalAspectRatioAtTwoTimesResolution()
            throws IOException {
        Path uiAssets = findUiAssets();
        int[] production = pngSize(uiAssets.resolve("production-modal.png"));

        assertEquals(730, production[0]);
        assertEquals(550, production[1]);
        assertEquals(365f / 275f, (float) production[0] / production[1], 0.001f);
        assertTrue(Files.isRegularFile(uiAssets.resolve("production-modal.png")));
    }

    @Test
    public void standaloneTexturesAndAtlasPageRequiredByLoaderArePackaged() throws IOException {
        Path uiAssets = findUiAssets();
        for (String texture : new String[]{
                "ui.png", "metal-panel.png", "logo-mark.png", "battle-map-hd.png",
                "production-modal.png"}) {
            assertTrue("Missing UI texture " + texture,
                    Files.isRegularFile(uiAssets.resolve(texture)));
        }
        assertEquals("ui.png", Files.readAllLines(
                uiAssets.resolve("ui.atlas"), StandardCharsets.UTF_8).get(0));
    }

    private static int[] pngSize(Path file) throws IOException {
        byte[] header = new byte[24];
        try (InputStream input = Files.newInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                if (read < 0) {
                    throw new IOException("PNG header is truncated: " + file);
                }
                offset += read;
            }
        }
        byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        for (int i = 0; i < pngSignature.length; i++) {
            if (header[i] != pngSignature[i]) {
                throw new IOException("Invalid PNG signature: " + file);
            }
        }
        ByteBuffer dimensions = ByteBuffer.wrap(header, 16, 8).order(ByteOrder.BIG_ENDIAN);
        return new int[]{dimensions.getInt(), dimensions.getInt()};
    }

    private static Path findUiAssets() {
        Path current = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++) {
            Path inModule = current.resolve("src/main/assets/original/ui");
            if (Files.isDirectory(inModule)) {
                return inModule;
            }
            Path fromRoot = current.resolve("app/src/main/assets/original/ui");
            if (Files.isDirectory(fromRoot)) {
                return fromRoot;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate the Android original/ui asset directory");
    }
}
