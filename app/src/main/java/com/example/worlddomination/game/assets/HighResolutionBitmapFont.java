package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Cached original-family Arial glyphs with mipmaps for phone-sized UI labels. */
public final class HighResolutionBitmapFont extends BitmapFont {
    public HighResolutionBitmapFont() {
        super(new InterfaceFontData(Gdx.files.internal("original/ui/fonts/resource-arial-48.fnt")),
                new TextureRegion(new Texture(Gdx.files.internal(
                        "original/ui/fonts/resource-arial-48.png"), true)), false);
        setOwnsTexture(true);
        getRegion().getTexture().setFilter(
                Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
    }

    @Override
    public float getScaleX() { return ((InterfaceFontData) getData()).getLogicalScaleX(); }

    @Override
    public float getScaleY() { return ((InterfaceFontData) getData()).getLogicalScaleY(); }
}
