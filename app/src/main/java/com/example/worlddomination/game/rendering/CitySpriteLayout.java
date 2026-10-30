package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

/** Pure placement math matching CityLayer's scaled source registration offsets. */
public final class CitySpriteLayout {
    private CitySpriteLayout() { }

    public static float scale(Rectangle mapBounds, float artworkScale) {
        if (artworkScale <= 0f) {
            throw new IllegalArgumentException("Artwork scale must be positive");
        }
        return BattlefieldCityLayout.sourceScale(mapBounds) * artworkScale;
    }

    public static float left(Rectangle mapBounds, int playerId, int cityIndex,
                             float sourceOffsetX, float artworkScale) {
        float scale = scale(mapBounds, artworkScale);
        return BattlefieldCityLayout.x(mapBounds, playerId, cityIndex)
                + sourceOffsetX * scale;
    }

    /** Converts source top-down offsets and image height to libGDX bottom-up y. */
    public static float bottom(Rectangle mapBounds, int playerId, int cityIndex,
                               float sourceOffsetY, float imageHeight,
                               float artworkScale) {
        float scale = scale(mapBounds, artworkScale);
        return BattlefieldCityLayout.y(mapBounds, playerId, cityIndex)
                - sourceOffsetY * scale - imageHeight * scale;
    }
}
