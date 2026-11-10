package com.example.worlddomination.game.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;

/** Centers the rendered ink, including descenders, rather than guessing a font baseline. */
public final class InformationTextAlignment {
    private InformationTextAlignment() { }

    public static float drawY(BitmapFont.BitmapFontData data, CharSequence text, float center) {
        float bottom = Float.POSITIVE_INFINITY;
        float top = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < text.length(); index++) {
            BitmapFont.Glyph glyph = data.getGlyph(text.charAt(index));
            if (glyph == null || glyph.height == 0) {
                continue;
            }
            float glyphBottom = data.ascent + glyph.yoffset * data.scaleY;
            bottom = Math.min(bottom, glyphBottom);
            top = Math.max(top, glyphBottom + glyph.height * data.scaleY);
        }
        return bottom == Float.POSITIVE_INFINITY ? center : center - (top + bottom) / 2f;
    }
}
