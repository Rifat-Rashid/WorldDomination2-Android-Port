package com.example.worlddomination.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/** Source dynamic-field advances and visible-ink anchors; caller owns cached fonts/layouts. */
public final class SourceHiddenHelpTypography {
    private SourceHiddenHelpTypography() { }

    /** Source-unit origin after the phone adapter floors its four-times raster.
     * Preserve negative offsets: long command text clips without reflow. */
    public static float centeredOffset(float fieldWidth, float advance) {
        return (float) Math.floor((fieldWidth - advance) * 2f) / 4f;
    }

    /** Includes trailing spaces and the full advance of the last glyph, unlike ink width. */
    public static float advance(BitmapFont.BitmapFontData data, CharSequence text) {
        float width = 0f;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n' || character == '\r')
                throw new IllegalArgumentException("Hidden Help typography takes one line");
            BitmapFont.Glyph glyph = data.getGlyph(character);
            if (glyph == null) glyph = data.missingGlyph;
            if (glyph != null) width += glyph.xadvance * data.scaleX;
        }
        return width;
    }

    /** No wrapping: source callers wrap/slice warning lines before asking for glyph layout. */
    public static void layout(GlyphLayout layout, BitmapFont font, String text, Color color) {
        float width = advance(font.getData(), text);
        layout.setText(font, text, color, 0f, com.badlogic.gdx.utils.Align.left, false);
        for (int index = 0; index < layout.runs.size; index++) {
            GlyphLayout.GlyphRun run = layout.runs.get(index);
            // Gdx compensates the first glyph's left bearing; source keeps it.
            if (run.xAdvances.size != 0) run.xAdvances.set(0, 0f);
            float runWidth = 0f;
            for (int glyphIndex = 0; glyphIndex < run.glyphs.size; glyphIndex++)
                runWidth += run.glyphs.get(glyphIndex).xadvance * font.getData().scaleX;
            run.width = runWidth;
        }
        layout.width = width;
    }

    /** Draw at desired visible top minus this offset, with the same scale used for layout. */
    public static float topOffset(BitmapFont.BitmapFontData data, GlyphLayout layout) {
        float top = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < layout.runs.size; index++) {
            GlyphLayout.GlyphRun run = layout.runs.get(index);
            for (int glyphIndex = 0; glyphIndex < run.glyphs.size; glyphIndex++) {
                BitmapFont.Glyph glyph = run.glyphs.get(glyphIndex);
                if (glyph.width > 0 && glyph.height > 0)
                    top = Math.max(top, data.ascent
                            + (glyph.yoffset + glyph.height) * data.scaleY + run.y);
            }
        }
        return top == Float.NEGATIVE_INFINITY ? 0f : top;
    }

    /** Source clock phase: half a second on, half a second off, at 24 Hz. */
    public static boolean caretVisible(long ticks) { return (ticks / 12L) % 2L == 0L; }
}
