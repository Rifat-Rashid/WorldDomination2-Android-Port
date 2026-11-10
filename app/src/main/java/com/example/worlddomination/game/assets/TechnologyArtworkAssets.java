package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/** Build-time, four-times rasterized source research labels, panels and selection states. */
public final class TechnologyArtworkAssets implements Disposable {
    public static final float RASTER_SCALE = 4f;

    public static final class Artwork {
        public final TextureRegion region;
        public final float originX;
        public final float originY;

        private Artwork(TextureRegion region, float originX, float originY) {
            this.region = region;
            this.originX = originX;
            this.originY = originY;
        }
    }

    private final TextureAtlas atlas;
    private final IntMap<Artwork> artwork = new IntMap<>();

    public TechnologyArtworkAssets() {
        atlas = new TextureAtlas(Gdx.files.internal("original/ui/technology-hd/technology.atlas"));
        JsonValue manifest = new JsonReader().parse(
                Gdx.files.internal("original/ui/technology-hd/origins.json"));
        if (manifest.getInt("scale") != (int) RASTER_SCALE) {
            throw new IllegalStateException("Unsupported technology artwork resolution");
        }
        for (JsonValue entry : manifest.get("artwork")) {
            int id = entry.getInt("id");
            TextureRegion region = atlas.findRegion("art" + id);
            if (region == null) {
                throw new IllegalStateException("Missing source technology artwork " + id);
            }
            artwork.put(id, new Artwork(region, entry.getFloat("x"), entry.getFloat("y")));
        }
    }

    public Artwork get(int symbolId) { return artwork.get(symbolId); }

    @Override
    public void dispose() { atlas.dispose(); }
}
