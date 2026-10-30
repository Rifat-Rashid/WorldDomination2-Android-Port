package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.assets.OriginalCityAssets.Frame;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

/** Draws source city artwork in the recovered back-to-front depth order. */
public final class BattlefieldCityRenderer {
    private static final int[] CITY_RENDER_ORDER = BattlefieldCityLayout.cityRenderOrder();

    private final OriginalCityAssets cityAssets;

    public BattlefieldCityRenderer(OriginalCityAssets cityAssets) {
        this.cityAssets = cityAssets;
    }

    public void draw(SpriteBatch batch, Rectangle mapBounds, GameState game) {
        if (game == null) {
            return;
        }
        float scale = CitySpriteLayout.scale(mapBounds, cityAssets.getArtworkScale());
        for (int address : CITY_RENDER_ORDER) {
            int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
            int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
            GameState.PlayerState owner = game.getPlayerState(playerId);
            CityState city = owner.getCity(cityIndex);
            if (city == null) {
                continue;
            }
            int frameNumber = city.getArtworkFrame();
            if (frameNumber < 1) {
                continue;
            }
            Frame frame = cityAssets.getFrame(owner.getReligionCode(), frameNumber);
            if (frame == null) {
                continue;
            }
            float width = frame.region.getRegionWidth() * scale;
            float height = frame.region.getRegionHeight() * scale;
            float x = BattlefieldCityLayout.x(mapBounds, playerId, cityIndex)
                    + frame.offsetX * scale;
            float y = BattlefieldCityLayout.y(mapBounds, playerId, cityIndex)
                    - frame.offsetY * scale - height;
            batch.draw(frame.region, x, y, width, height);
        }
    }
}
