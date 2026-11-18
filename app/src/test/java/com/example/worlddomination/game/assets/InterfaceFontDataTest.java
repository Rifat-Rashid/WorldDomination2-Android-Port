package com.example.worlddomination.game.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import com.example.worlddomination.game.ui.TechnologyScreenLayout;
import com.example.worlddomination.game.ui.TechnologySelectionTextLayout;
import org.junit.Test;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.zip.GZIPInputStream;
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
    public void selectedTechnologyAdvancesMatchIndependentSourceArialMetrics() {
        // Source extracted Arial, TextFormat size 10, rendered at 4x by the
        // Python renderer. These advances come from that font, not this atlas.
        String[] texts = {"ROCKET (25 MEG)", "10.000.000", "estimated total: 10.000.000"};
        float[] sourceAdvances = {87.75f, 49.5f, 120.75f};
        for (float modalScale : new float[]{0.5f, 1f, 2f}) {
            InterfaceFontData data = font();
            data.setScale(TechnologyScreenLayout.SELECTED_TEXT_LINE_HEIGHT * modalScale
                    / data.lineHeight);
            for (int index = 0; index < texts.length; index++) {
                float advance = 0f;
                for (char character : texts[index].toCharArray()) {
                    advance += data.getGlyph(character).xadvance * data.scaleX;
                }
                assertEquals("Source face size: " + texts[index] + " at " + modalScale,
                        sourceAdvances[index] * modalScale, advance,
                        sourceAdvances[index] * modalScale * .02f);
            }
        }
    }

    @Test
    public void allSourceTechnologyNamesWrapLikeTheRecoveredTextField() throws Exception {
        JsonValue fixture = sourceTechnologyFixture();
        JsonValue provenance = fixture.get("provenance");
        assertEquals(10, provenance.getInt("source_text_format_size"));
        assertEquals(93, provenance.getInt("name_field_wrap_width"));
        assertEquals(6, provenance.getInt("name_visible_lines"));
        assertEquals(12, provenance.getInt("name_line_spacing"));

        InterfaceFontData data = font();
        data.setScale(TechnologyScreenLayout.SELECTED_TEXT_LINE_HEIGHT / data.lineHeight);
        TechnologySelectionTextLayout layout = new TechnologySelectionTextLayout(
                text -> glyphAdvance(data, text));
        JsonValue technologies = fixture.get("technologies");
        assertEquals("Every source TechTree entry must be in the oracle", 28, technologies.size);
        assertEquals(TechnologyCatalog.all().size(), technologies.size);
        for (JsonValue expected = technologies.child; expected != null; expected = expected.next) {
            String key = expected.getString("key");
            TechnologyCatalog.Definition definition = TechnologyCatalog.byName(key);
            assertNotNull("Source technology " + key, definition);
            assertEquals("Source pretty name " + key, expected.getString("pretty"),
                    definition.prettyName);
            float sourceAdvance = expected.getFloat("source_advance");
            assertEquals("Extracted Arial advance " + key, sourceAdvance,
                    glyphAdvance(data, definition.prettyName), sourceAdvance * .02f);
            JsonValue sourceLines = expected.get("lines");
            List<String> actualLines = layout.nameLines(key);
            assertEquals("Source wrapped line count " + key, sourceLines.size,
                    actualLines.size());
            for (int index = 0; index < sourceLines.size; index++) {
                assertEquals("Source line " + index + " for " + key,
                        sourceLines.getString(index), actualLines.get(index));
            }
        }

        // The old line-height denominator made this 97.25-point source name
        // appear under the 93-point boundary, hiding its required wrap.
        InterfaceFontData oldScale = font();
        oldScale.setScale(10f / oldScale.lineHeight);
        assertTrue(glyphAdvance(oldScale, "WARHEAD (25 MEG)") < 93f);
        assertEquals(2, layout.nameLines("warhead2").size());
        try {
            layout.nameLines("warhead2").add("changed");
            fail("Cached source lines must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals("WARHEAD (25", layout.nameLines("warhead2").get(0));
        }
    }

    @Test
    public void selectedTechnologyOracleUsesThePackagedExtractedSourceFont() throws Exception {
        JsonValue provenance = sourceTechnologyFixture().get("provenance");
        Path fontFile = assetPath("original/runtime/fonts/1_Arial_Arial.ttf.gz");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fontBytes = new GZIPInputStream(Files.newInputStream(fontFile))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = fontBytes.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
        assertEquals("Captured source Arial must match the packaged font",
                provenance.getString("decoded_font_sha256"), hex.toString());
    }

    private static float glyphAdvance(InterfaceFontData data, String text) {
        float advance = 0f;
        for (int index = 0; index < text.length(); index++) {
            advance += data.getGlyph(text.charAt(index)).xadvance * data.scaleX;
        }
        return advance;
    }

    private static JsonValue sourceTechnologyFixture() throws Exception {
        try (InputStream input = InterfaceFontDataTest.class.getResourceAsStream(
                "/source-technology-selected-text.json")) {
            assertNotNull("Source Technology text fixture", input);
            return new JsonReader().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Path assetPath(String relative) {
        Path file = Paths.get("app/src/main/assets", relative);
        if (!Files.isRegularFile(file)) file = Paths.get("src/main/assets", relative);
        assertTrue("Packaged source asset " + relative, Files.isRegularFile(file));
        return file;
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
