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
}
