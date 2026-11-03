package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.utils.Disposable;

/** Cached raster export of the original Flash title artwork. */
public final class OriginalSplashAssets implements Disposable {
    private final Texture texture;

    public OriginalSplashAssets() {
        texture = new Texture(Gdx.files.internal("original/ui/title-splash.png"), true);
        texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
    }

    public Texture getTexture() {
        return texture;
    }

    @Override
    public void dispose() {
        texture.dispose();
    }
}
