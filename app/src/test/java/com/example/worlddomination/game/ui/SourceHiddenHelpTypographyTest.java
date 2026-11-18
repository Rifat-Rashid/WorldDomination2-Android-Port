package com.example.worlddomination.game.ui;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SourceHiddenHelpTypographyTest {
    @Test public void centeredOriginsFloorAtSourceFourTimesRasterIncludingClippedText() {
        assertEquals(120.5f, SourceHiddenHelpTypography.centeredOffset(318f, 307f / 4f), 0f);
        assertEquals(91.5f, SourceHiddenHelpTypography.centeredOffset(318f, 540f / 4f), 0f);
        assertEquals(4.5f, SourceHiddenHelpTypography.centeredOffset(141f, 527f / 4f), 0f);
        assertEquals(-.25f, SourceHiddenHelpTypography.centeredOffset(318f, 318.125f), 0f);
        assertEquals(-1f, SourceHiddenHelpTypography.centeredOffset(318f, 320f), 0f);
    }

    private static BitmapFont.BitmapFontData font(String name) {
        Path path = Path.of("src/main/assets/original/ui/help/hidden-art/" + name + ".fnt");
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        return new BitmapFont.BitmapFontData(new FileHandle(path.toFile()), false);
    }

    @Test public void authoredCommandAdvancesRetainSourceIntegerPixelsAndCaretPrefixes() {
        BitmapFont.BitmapFontData data = font("hidden-font-command-120");
        assertEquals(307f, SourceHiddenHelpTypography.advance(data, "trump"), 0f);
        assertEquals(540f, SourceHiddenHelpTypography.advance(data, "I am Bush"), 0f);
        float prefix = SourceHiddenHelpTypography.advance(data, "tru");
        assertEquals(data.getGlyph('t').xadvance + data.getGlyph('r').xadvance
                + data.getGlyph('u').xadvance, prefix, 0f);
        data.setScale(.25f);
        assertEquals(76.75f, SourceHiddenHelpTypography.advance(data, "trump"), 0f);
        assertEquals(135f, SourceHiddenHelpTypography.advance(data, "I am Bush"), 0f);
        assertEquals(0f, SourceHiddenHelpTypography.advance(data, ""), 0f);
    }

    @Test public void allFourSourceFightClubWarningLinesKeepExactAdvances() {
        BitmapFont.BitmapFontData data = font("hidden-font-warning-40");
        String[] lines = {"## It is only after you've lost", "everything, that you are free",
                "to do anything... ## ~Chuck", "Palahniuk, Fight Club,"};
        float[] advances = {527f, 530f, 514f, 415f};
        for (int index = 0; index < lines.length; index++)
            assertEquals(lines[index], advances[index],
                    SourceHiddenHelpTypography.advance(data, lines[index]), 0f);
        assertTrue(SourceHiddenHelpTypography.advance(data, "Palahniuk, Fight Club, Chapter") > 548f);
    }

    @Test public void visibleTopUsesGlyphOffsetsRunPositionAndVerticalScale() {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.ascent = 7f; data.scaleY = .25f;
        GlyphLayout layout = new GlyphLayout();
        GlyphLayout.GlyphRun run = new GlyphLayout.GlyphRun();
        run.y = -2f;
        BitmapFont.Glyph first = new BitmapFont.Glyph();
        first.width = 20; first.height = 40; first.yoffset = -44;
        BitmapFont.Glyph tall = new BitmapFont.Glyph();
        tall.width = 20; tall.height = 48; tall.yoffset = -44;
        BitmapFont.Glyph space = new BitmapFont.Glyph();
        space.width = 50; space.height = 0; space.yoffset = 1000;
        run.glyphs.add(first); run.glyphs.add(tall); run.glyphs.add(space);
        layout.runs.add(run);
        assertEquals(6f, SourceHiddenHelpTypography.topOffset(data, layout), 0f);
        run.glyphs.clear();
        assertEquals(0f, SourceHiddenHelpTypography.topOffset(data, layout), 0f);
    }

    @Test public void caretFollowsGlobalTwelveTickPhases() {
        assertTrue(SourceHiddenHelpTypography.caretVisible(0));
        assertTrue(SourceHiddenHelpTypography.caretVisible(11));
        assertFalse(SourceHiddenHelpTypography.caretVisible(12));
        assertFalse(SourceHiddenHelpTypography.caretVisible(23));
        assertTrue(SourceHiddenHelpTypography.caretVisible(24));
        assertFalse(SourceHiddenHelpTypography.caretVisible(12L * 1_000_001L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void callerMustSliceLinesBeforeMeasuring() {
        SourceHiddenHelpTypography.advance(font("hidden-font-warning-40"), "line1\nline2");
    }
}
