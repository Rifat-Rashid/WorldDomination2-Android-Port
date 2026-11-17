package com.example.worlddomination.game.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

/** High-resolution glyphs retain the existing HUD's 18-point logical line metrics. */
public final class InterfaceFontData extends BitmapFont.BitmapFontData {
    private float resolutionScale = 1f;
    // BitmapFontData.setScale normally multiplies the current metrics by a
    // ratio. Rebuilding from these logical-scale-one metrics avoids drift when
    // the HUD repeatedly changes sizes and restores them every frame.
    private final float baseLineHeight, baseSpaceXadvance, baseXHeight, baseCapHeight;
    private final float baseAscent, baseDescent, baseDown;
    private final float basePadLeft, basePadRight, basePadTop, basePadBottom;

    public InterfaceFontData(FileHandle file) {
        super(file, false);
        resolutionScale = 18f / lineHeight;
        super.setScale(resolutionScale, resolutionScale);
        baseLineHeight = lineHeight;
        baseSpaceXadvance = spaceXadvance;
        baseXHeight = xHeight;
        baseCapHeight = capHeight;
        baseAscent = ascent;
        baseDescent = descent;
        baseDown = down;
        basePadLeft = padLeft;
        basePadRight = padRight;
        basePadTop = padTop;
        basePadBottom = padBottom;
    }

    @Override
    public void setScale(float x, float y) {
        float physicalX = x * resolutionScale;
        float physicalY = y * resolutionScale;
        if (physicalX == 0f) throw new IllegalArgumentException("scaleX cannot be 0.");
        if (physicalY == 0f) throw new IllegalArgumentException("scaleY cannot be 0.");
        lineHeight = baseLineHeight * y;
        spaceXadvance = baseSpaceXadvance * x;
        xHeight = baseXHeight * y;
        capHeight = baseCapHeight * y;
        ascent = baseAscent * y;
        descent = baseDescent * y;
        down = baseDown * y;
        padLeft = basePadLeft * x;
        padRight = basePadRight * x;
        padTop = basePadTop * y;
        padBottom = basePadBottom * y;
        scaleX = physicalX;
        scaleY = physicalY;
    }

    @Override
    public void scale(float amount) {
        setScale(getLogicalScaleX() + amount, getLogicalScaleY() + amount);
    }

    public float getLogicalScaleX() { return scaleX / resolutionScale; }
    public float getLogicalScaleY() { return scaleY / resolutionScale; }
}
