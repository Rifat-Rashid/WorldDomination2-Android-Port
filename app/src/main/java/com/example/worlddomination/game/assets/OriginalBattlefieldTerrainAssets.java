package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldTerrainState;

/** Registered terrain variants; only the five selected tiles are uploaded to the GPU. */
public final class OriginalBattlefieldTerrainAssets implements Disposable {
    private static final String ROOT = "original/ui/terrain/";
    private static final String[] FAITHS = {"chr", "bhu", "com", "jew", "mus"};

    private static final class Definition {
        String file;
        float sourceX;
        float sourceY;
        int pixelWidth;
        int pixelHeight;
    }

    public static final class Tile {
        public final String religion;
        public final TextureRegion region;
        public final float sourceX;
        public final float sourceY;
        public final float sourceWidth;
        public final float sourceHeight;
        private final Texture texture;

        private Tile(String religion, Definition definition, float density) {
            this.religion = religion;
            texture = loadTexture(definition.file);
            region = new TextureRegion(texture);
            sourceX = definition.sourceX;
            sourceY = definition.sourceY;
            sourceWidth = definition.pixelWidth / density;
            sourceHeight = definition.pixelHeight / density;
        }
    }

    private final Definition[][] definitions = new Definition[5][5];
    private final Tile[] selected = new Tile[BattlefieldTerrainState.ISLAND_COUNT];
    private final Texture oceanTexture;
    private final TextureRegion ocean;
    private final float rasterScale;

    public OriginalBattlefieldTerrainAssets() {
        JsonValue manifest = new JsonReader().parse(Gdx.files.internal(ROOT + "manifest.json"));
        if (manifest.getInt("format") != 1) {
            throw new IllegalStateException("Unsupported battlefield terrain format");
        }
        rasterScale = manifest.getFloat("rasterScale");
        if (rasterScale <= 0) throw new IllegalStateException("Invalid terrain raster scale");
        JsonValue islands = manifest.get("islands");
        for (JsonValue island = islands.child; island != null; island = island.next) {
            int islandId = island.getInt("island");
            if (islandId < 0 || islandId >= selected.length) {
                throw new IllegalStateException("Invalid terrain island");
            }
            JsonValue variants = island.get("variants");
            for (int faith = 0; faith < FAITHS.length; faith++) {
                JsonValue data = variants.get(FAITHS[faith]);
                Definition definition = new Definition();
                definition.file = data.getString("file");
                definition.sourceX = data.get("sourceOffset").getFloat(0);
                definition.sourceY = data.get("sourceOffset").getFloat(1);
                definition.pixelWidth = data.get("pixelSize").getInt(0);
                definition.pixelHeight = data.get("pixelSize").getInt(1);
                definitions[islandId][faith] = definition;
            }
        }
        for (Definition[] island : definitions) {
            for (Definition definition : island) {
                if (definition == null) throw new IllegalStateException("Missing terrain variant");
            }
        }
        oceanTexture = loadTexture(manifest.get("ocean").getString("file"));
        ocean = new TextureRegion(oceanTexture);
    }

    /** Call once when starting/loading a match, on the GL thread, never while drawing. */
    public void prepare(GameState game) {
        for (int island = 0; island < selected.length; island++) {
            String religion = BattlefieldTerrainState.religion(game, island);
            Tile previous = selected[island];
            if (religion == null) {
                if (previous != null) previous.texture.dispose();
                selected[island] = null;
            } else if (previous == null || !religion.equals(previous.religion)) {
                Tile replacement = new Tile(religion,
                        definitions[island][faithIndex(religion)], rasterScale);
                selected[island] = replacement;
                if (previous != null) previous.texture.dispose();
            }
        }
    }

    public TextureRegion getOcean() {
        return ocean;
    }

    public Tile getTile(int island) {
        if (island < 0 || island >= selected.length) {
            throw new IllegalArgumentException("Invalid battlefield island");
        }
        return selected[island];
    }

    public int getSelectedTileCount() {
        int count = 0;
        for (Tile tile : selected) if (tile != null) count++;
        return count;
    }

    private static int faithIndex(String religion) {
        for (int index = 0; index < FAITHS.length; index++) {
            if (FAITHS[index].equals(religion)) return index;
        }
        throw new IllegalArgumentException("Unknown terrain religion: " + religion);
    }

    private static Texture loadTexture(String file) {
        // File-backed textures are libGDX-managed and recover after GL context loss.
        Texture texture = new Texture(Gdx.files.internal(ROOT + file), true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        return texture;
    }

    @Override public void dispose() {
        for (int island = 0; island < selected.length; island++) {
            Tile tile = selected[island];
            if (tile != null) tile.texture.dispose();
            selected[island] = null;
        }
        oceanTexture.dispose();
    }
}
