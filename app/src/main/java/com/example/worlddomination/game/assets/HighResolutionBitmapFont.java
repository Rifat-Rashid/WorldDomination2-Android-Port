package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureData;

/** Cached original-family Arial glyphs with mipmaps for phone-sized UI labels. */
public final class HighResolutionBitmapFont extends BitmapFont {
    public HighResolutionBitmapFont() {
        super(new InterfaceFontData(Gdx.files.internal("original/ui/fonts/resource-arial-48.fnt")),
                new TextureRegion(new Texture(new WhiteGlyphTextureData())), false);
        setOwnsTexture(true);
        getRegion().getTexture().setFilter(
                Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
    }

    @Override
    public float getScaleX() { return ((InterfaceFontData) getData()).getLogicalScaleX(); }

    @Override
    public float getScaleY() { return ((InterfaceFontData) getData()).getLogicalScaleY(); }

    /** Keep straight-alpha white ink white when minifying; coverage lives only in alpha. */
    private static final class WhiteGlyphTextureData extends FileTextureData {
        WhiteGlyphTextureData() {
            super(Gdx.files.internal("original/ui/fonts/resource-arial-48.png"),
                    null, Pixmap.Format.RGBA8888, true);
        }

        @Override public Pixmap consumePixmap() {
            Pixmap image = super.consumePixmap();
            // The PNG has black RGB in transparent texels. Averaging those into
            // mip levels otherwise multiplies coverage into RGB before blending.
            // FileTextureData remains managed and repeats this on context reload;
            // libGDX disposes the returned pixmap after uploading the mip chain.
            image.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.drawPixel(x, y, 0xffffff00 | (image.getPixel(x, y) & 0xff));
                }
            }
            return image;
        }
    }
}
