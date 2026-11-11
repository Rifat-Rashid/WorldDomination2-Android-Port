package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.example.worlddomination.game.assets.OriginalBattlefieldTerrainAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.BattlefieldTerrainState;

/** Cached original terrain with the same uniform camera used by cities and flags. */
public final class BattlefieldTerrainRenderer implements Disposable {
    private final OriginalBattlefieldTerrainAssets assets;

    public BattlefieldTerrainRenderer() {
        assets = new OriginalBattlefieldTerrainAssets();
    }

    /** Uploads selected tiles on match start/load; unchanged selections retain their textures. */
    public void prepare(GameState game) {
        assets.prepare(game);
    }

    /** Draw inside the battlefield scissor, below flags, cities, selection and battle effects. */
    public void draw(SpriteBatch batch, Rectangle mapBounds) {
        float previousColor = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(assets.getOcean(), mapBounds.x, mapBounds.y, mapBounds.width, mapBounds.height);
        float scale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (int island = 0; island < BattlefieldTerrainState.ISLAND_COUNT; island++) {
            OriginalBattlefieldTerrainAssets.Tile tile = assets.getTile(island);
            if (tile == null) continue;
            float x = BattlefieldCityLayout.sourceX(mapBounds, tile.sourceX, island);
            float top = BattlefieldCityLayout.sourceY(mapBounds, tile.sourceY);
            batch.draw(tile.region, x, top - tile.sourceHeight * scale,
                    tile.sourceWidth * scale, tile.sourceHeight * scale);
        }
        batch.setPackedColor(previousColor);
    }

    public int getSelectedTileCount() {
        return assets.getSelectedTileCount();
    }

    @Override public void dispose() {
        assets.dispose();
    }
}
