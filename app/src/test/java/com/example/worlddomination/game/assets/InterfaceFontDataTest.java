package com.example.worlddomination.game.assets;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public final class InterfaceFontDataTest {
    private static InterfaceFontData font() {
        Path file = Paths.get("app/src/main/assets/original/ui/fonts/resource-arial-48.fnt");
        if (!Files.isRegularFile(file)) {
            file = Paths.get("src/main/assets/original/ui/fonts/resource-arial-48.fnt");
        }
        return new InterfaceFontData(new FileHandle(file.toFile()));
    }

    @Test
    public void highResolutionGlyphsKeepExistingHudLineMetrics() {
        InterfaceFontData data = font();
        assertEquals(18f, data.lineHeight, 0.001f);
        assertEquals(1f, data.getLogicalScaleX(), 0.0001f);
        assertTrue("The glyph texture must retain high-resolution source pixels",
                data.getGlyph('H').height >= 30);
        for (char glyph : "Resources Production Technology Mass Media Direct Attack $0123456789,.%".toCharArray()) {
            assertNotNull("Missing HUD glyph " + glyph, data.getGlyph(glyph));
        }
    }

    @Test
    public void changingAndRestoringTextSizeDoesNotAccumulateScalingErrors() {
        InterfaceFontData data = font();
        for (int iteration = 0; iteration < 100; iteration++) {
            data.setScale(0.5f);
            assertEquals(9f, data.lineHeight, 0.001f);
            data.setScale(0.82f);
            assertEquals(14.76f, data.lineHeight, 0.001f);
            data.setScale(1f);
        }
        assertEquals(18f, data.lineHeight, 0.001f);
        data.scale(-0.25f);
        assertEquals(0.75f, data.getLogicalScaleX(), 0.001f);
        assertEquals(13.5f, data.lineHeight, 0.001f);
    }

    @Test
    public void realHudSizeSequenceRestoresIdenticalMetricsEveryFrame() {
        InterfaceFontData data = font();
        float[] initial = metrics(data);
        float[] heights = {9f, 9f, 9f, 13f, 7f, 7f, 6f};
        float[][] firstFrame = new float[heights.length][];
        for (int frame = 0; frame < 100; frame++) {
            for (int label = 0; label < heights.length; label++) {
                // World.drawCentered saves the logical scale, derives its target
                // from current line height, paints, and restores the old scale.
                float previous = data.getLogicalScaleX();
                data.setScale(heights[label] / Math.max(data.lineHeight, 1f));
                if (frame == 0) firstFrame[label] = metrics(data);
                else assertArrayEquals("scaled frame " + frame + " label " + label,
                        firstFrame[label], metrics(data), 0f);
                data.setScale(previous);
                assertArrayEquals("restored frame " + frame + " label " + label,
                        initial, metrics(data), 0f);
            }
        }
    }

    @Test
    public void anisotropicScalesRebuildEveryMetricAndRestoreExactly() {
        InterfaceFontData data = font();
        float[] initial = metrics(data);
        data.setScale(.5f, 1.25f);
        float[] expected = {initial[0] * 1.25f, initial[1] * .5f,
                initial[2] * 1.25f, initial[3] * 1.25f, initial[4] * 1.25f,
                initial[5] * 1.25f, initial[6] * 1.25f, initial[7] * .5f,
                initial[8] * .5f, initial[9] * 1.25f, initial[10] * 1.25f};
        assertArrayEquals("axis-specific source metrics", expected, metrics(data), 0f);
        assertEquals(.5f, data.getLogicalScaleX(), .000001f);
        assertEquals(1.25f, data.getLogicalScaleY(), .000001f);
        data.setScale(1f, 1f);
        assertArrayEquals("exact restore", initial, metrics(data), 0f);
        data.setScale(.5f, 1.25f);
        assertArrayEquals("same anisotropic target after restore", expected, metrics(data), 0f);
        data.scale(.25f);
        assertEquals(.75f, data.getLogicalScaleX(), .000001f);
        assertEquals(1.5f, data.getLogicalScaleY(), .000001f);
        try { data.setScale(0f, 1f); fail("zero x scale"); }
        catch (IllegalArgumentException expectedError) { assertEquals("scaleX cannot be 0.", expectedError.getMessage()); }
        try { data.setScale(1f, 0f); fail("zero y scale"); }
        catch (IllegalArgumentException expectedError) { assertEquals("scaleY cannot be 0.", expectedError.getMessage()); }
    }

    private static float[] metrics(InterfaceFontData data) {
        return new float[]{data.lineHeight, data.spaceXadvance, data.xHeight, data.capHeight,
                data.ascent, data.descent, data.down, data.padLeft, data.padRight,
                data.padTop, data.padBottom};
    }
}
