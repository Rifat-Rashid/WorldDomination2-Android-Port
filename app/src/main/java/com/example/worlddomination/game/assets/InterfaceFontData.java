package com.example.worlddomination.game.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

/** High-resolution glyphs retain the existing HUD's 18-point logical line metrics. */
public final class InterfaceFontData extends BitmapFont.BitmapFontData {
    private float resolutionScale = 1f;

    public InterfaceFontData(FileHandle file) {
        super(file, false);
        resolutionScale = 18f / lineHeight;
        super.setScale(resolutionScale, resolutionScale);
    }

    @Override
    public void setScale(float x, float y) {
        super.setScale(x * resolutionScale, y * resolutionScale);
    }

    @Override
    public void scale(float amount) {
        setScale(getLogicalScaleX() + amount, getLogicalScaleY() + amount);
    }

    public float getLogicalScaleX() { return scaleX / resolutionScale; }
    public float getLogicalScaleY() { return scaleY / resolutionScale; }
}
