package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.List;

/** Cached original Flash-derived city frames and their registration offsets. */
public final class OriginalCityAssets implements Disposable {
    private static final String ASSET_ROOT = "original/cities/";
    private static final int[] CHARACTER_IDS = {1337, 1363, 1388, 1414, 1441};

    public static final class Frame {
        public final TextureRegion region;
        public final float offsetX;
        public final float offsetY;

        private Frame(TextureRegion region, float offsetX, float offsetY) {
            this.region = region;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    private final Frame[][] frames = new Frame[CHARACTER_IDS.length][16];
    private final List<Texture> textures = new ArrayList<>(60);
    private float artworkScale;

    public OriginalCityAssets() {
        try {
            loadManifest();
        } catch (RuntimeException error) {
            dispose();
            throw error;
        }
    }

    public float getArtworkScale() {
        return artworkScale;
    }

    public Frame getFrame(String religionCode, int frameNumber) {
        int theme = themeIndexForReligion(religionCode);
        if (frameNumber < 0 || frameNumber >= frames[theme].length) {
            return null;
        }
        return frames[theme][frameNumber];
    }

    /** Maps Model_gameMenu.get_religion_buildingSet_by_code to its SWF symbol ID. */
    public static int characterIdForReligion(String religionCode) {
        switch (religionCode == null ? "" : religionCode) {
            case "com": return 1337;
            case "chr": return 1363;
            case "bhu": return 1388;
            case "jew": return 1414;
            case "mus": return 1441;
            default: return 1363;
        }
    }

    private static int themeIndexForReligion(String religionCode) {
        int characterId = characterIdForReligion(religionCode);
        for (int i = 0; i < CHARACTER_IDS.length; i++) {
            if (CHARACTER_IDS[i] == characterId) {
                return i;
            }
        }
        return 1;
    }

    private void loadManifest() {
        JsonValue manifest = new JsonReader().parse(
                Gdx.files.internal(ASSET_ROOT + "manifest.json"));
        artworkScale = manifest.getFloat("scale");
        JsonValue symbols = manifest.get("symbols");
        if (symbols == null) {
            throw new IllegalStateException("Original city manifest has no symbols");
        }
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            int characterId = symbol.getInt("character_id");
            int theme = themeIndex(characterId);
            if (theme < 0) {
                continue;
            }
            JsonValue symbolFrames = symbol.get("frames");
            for (JsonValue entry = symbolFrames.child; entry != null; entry = entry.next) {
                int frameNumber = Integer.parseInt(entry.name);
                String path = ASSET_ROOT + entry.getString("file");
                Texture texture = new Texture(Gdx.files.internal(path), true);
                texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                textures.add(texture);
                JsonValue offset = entry.get("offset");
                frames[theme][frameNumber] = new Frame(new TextureRegion(texture),
                        offset.getFloat(0), offset.getFloat(1));
            }
        }
        for (int theme = 0; theme < frames.length; theme++) {
            for (int frame = 1; frame <= 6; frame++) {
                requireFrame(theme, frame);
            }
            for (int frame = 10; frame <= 15; frame++) {
                requireFrame(theme, frame);
            }
        }
        if (artworkScale <= 0f) {
            throw new IllegalStateException("Original city artwork scale must be positive");
        }
    }

    private void requireFrame(int theme, int frame) {
        if (frames[theme][frame] == null) {
            throw new IllegalStateException("Missing city frame " + frame
                    + " for SWF symbol " + CHARACTER_IDS[theme]);
        }
    }

    private int themeIndex(int characterId) {
        for (int i = 0; i < CHARACTER_IDS.length; i++) {
            if (CHARACTER_IDS[i] == characterId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void dispose() {
        for (Texture texture : textures) {
            texture.dispose();
        }
        textures.clear();
    }
}
