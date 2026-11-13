package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.BattlefieldCityOrderMarkers;

/** Source one-frame crosshair with depth-ordered, width-centered action icons. */
public final class BattlefieldCityOrderMarkerRenderer {
    private static final int CROSSHAIR_SHAPE_ID = 2218;
    private static final int SOLDIER_SHAPE_ID = 2233;
    private static final int ROCKET_SHAPE_ID = 2221;
    private static final int PLANE_SHAPE_ID = 2227;
    private static final int PROPAGANDA_SHAPE_ID = 2224;
    private static final int SPECIAL_SHAPE_ID = 2230;
    private static final float SOURCE_MARKER_SCALE = 2f;
    private static final float SOURCE_MARKER_Y_OFFSET = -25f;
    private static final float SOURCE_ICON_Y_OFFSET = 25f;
    private static final int[] CITY_RENDER_ORDER = BattlefieldCityLayout.cityRenderOrder();

    private final OriginalFlashAnimationAssets assets;

    public BattlefieldCityOrderMarkerRenderer(OriginalFlashAnimationAssets assets) {
        this.assets = assets;
    }

    /** Draw within an already begun batch, after battlefield cities. */
    public void draw(SpriteBatch batch, Rectangle mapBounds, GameState game,
                     String selectedEquipment) {
        if (game == null || assets == null || assets.getShape(CROSSHAIR_SHAPE_ID) == null) return;
        for (int address : CITY_RENDER_ORDER) {
            drawCity(batch, mapBounds, game, address);
        }
    }

    /** Draws one source city marker at image_depth + 1500 for depth interleaving. */
    public void drawCity(SpriteBatch batch, Rectangle mapBounds, GameState game, int address) {
        if (game == null || assets == null || assets.getShape(CROSSHAIR_SHAPE_ID) == null) return;
        int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
        int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
        int count = BattlefieldCityOrderMarkers.iconCount(game, playerId, cityIndex);
        if (count == 0) return;
        float scale = BattlefieldCityLayout.sourceScale(mapBounds) * SOURCE_MARKER_SCALE;
        float centerX = BattlefieldCityLayout.x(mapBounds, playerId, cityIndex);
        float centerY = BattlefieldCityLayout.y(mapBounds, playerId, cityIndex)
                - SOURCE_MARKER_Y_OFFSET * scale / SOURCE_MARKER_SCALE;
        drawShape(batch, CROSSHAIR_SHAPE_ID, centerX, centerY, scale);
        float totalWidth = 0f;
        for (int iconIndex = 0; iconIndex < count; iconIndex++) {
            int shapeId = iconShapeId(BattlefieldCityOrderMarkers.iconAt(
                    game, playerId, cityIndex, iconIndex));
            TextureRegion region = assets.getShape(shapeId);
            if (region != null) totalWidth += region.getRegionWidth();
        }
        float childX = -totalWidth / 2f;
        for (int iconIndex = 0; iconIndex < count; iconIndex++) {
            int shapeId = iconShapeId(BattlefieldCityOrderMarkers.iconAt(
                    game, playerId, cityIndex, iconIndex));
            TextureRegion region = assets.getShape(shapeId);
            if (region == null) continue;
            drawShape(batch, shapeId, centerX + childX * scale,
                    centerY - SOURCE_ICON_Y_OFFSET * scale, scale);
            childX += region.getRegionWidth();
        }
    }

    private void drawShape(SpriteBatch batch, int shapeId, float sourceOriginX,
                           float sourceOriginY, float scale) {
        TextureRegion region = assets.getShape(shapeId);
        if (region == null) return;
        float x = sourceOriginX + assets.getShapeOriginX(shapeId) * scale;
        float y = sourceOriginY - (assets.getShapeOriginY(shapeId)
                + region.getRegionHeight()) * scale;
        batch.draw(region, x, y, region.getRegionWidth() * scale,
                region.getRegionHeight() * scale);
    }

    private static int iconShapeId(BattlefieldCityOrderMarkers.Icon icon) {
        if (icon == BattlefieldCityOrderMarkers.Icon.SOLDIER) return SOLDIER_SHAPE_ID;
        if (icon == BattlefieldCityOrderMarkers.Icon.ROCKET) return ROCKET_SHAPE_ID;
        if (icon == BattlefieldCityOrderMarkers.Icon.PLANE) return PLANE_SHAPE_ID;
        if (icon == BattlefieldCityOrderMarkers.Icon.PROPAGANDA) return PROPAGANDA_SHAPE_ID;
        return SPECIAL_SHAPE_ID;
    }
}
