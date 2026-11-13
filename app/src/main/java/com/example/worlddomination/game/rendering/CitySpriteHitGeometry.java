package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

/** The displayed city rectangles and the phone reference's two-stage island hit mapping. */
public final class CitySpriteHitGeometry {
    private static final float[][] ISLAND_CENTERS = {
            {425f, 345f}, {140f, 145f}, {665f, 145f},
            {150f, 500f}, {665f, 490f}
    };

    private CitySpriteHitGeometry() { }

    /** Uses the same source offsets, PNG dimensions, and uniform scale as the city draw call. */
    public static void setBounds(Rectangle result, Rectangle mapBounds, int playerId,
                                 int cityIndex, float offsetX, float offsetY,
                                 float imageWidth, float imageHeight, float artworkScale) {
        float scale = CitySpriteLayout.scale(mapBounds, artworkScale);
        result.set(BattlefieldCityLayout.x(mapBounds, playerId, cityIndex) + offsetX * scale,
                BattlefieldCityLayout.y(mapBounds, playerId, cityIndex)
                        - offsetY * scale - imageHeight * scale,
                imageWidth * scale, imageHeight * scale);
    }

    /** Returns an address in source display depth order, or -1. Arrays are reused by the caller. */
    public static int hitTest(float x, float y, Rectangle mapBounds,
                              Rectangle[] bounds, boolean[] visible, int[] renderOrder) {
        if (!containsPhonePoint(mapBounds, x, y)) {
            return -1;
        }
        int island = islandForTouch(x, y, mapBounds, bounds, visible);
        float selectedShift = islandShift(mapBounds, island);
        // Undo the selected island's crop, then dispatch through the unshifted
        // source city clips in reverse display depth, as ActionEntry.control_at does.
        for (int index = renderOrder.length - 1; index >= 0; index--) {
            int address = renderOrder[index];
            if (!visible[address]) {
                continue;
            }
            int player = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
            float sourceMappedX = x + islandShift(mapBounds, player) - selectedShift;
            if (containsFinalControl(bounds[address], sourceMappedX, y)) {
                return address;
            }
        }
        return -1;
    }

    static int islandForTouch(float x, float y, Rectangle mapBounds,
                              Rectangle[] bounds, boolean[] visible) {
        int island = -1;
        float closest = Float.POSITIVE_INFINITY;
        for (int address = 0; address < bounds.length; address++) {
            if (!visible[address] || !containsPhonePoint(bounds[address], x, y)) {
                continue;
            }
            int player = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
            int city = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
            float dx = x - BattlefieldCityLayout.x(mapBounds, player, city);
            float dy = y - BattlefieldCityLayout.y(mapBounds, player, city);
            float distance = dx * dx + dy * dy;
            // Python min((distance, island)) chooses the lower island on an exact tie.
            if (distance < closest || (distance == closest && player < island)) {
                closest = distance;
                island = player;
            }
        }
        if (island < 0) {
            for (int player = 0; player < ISLAND_CENTERS.length; player++) {
                float dx = x - projectedX(mapBounds, ISLAND_CENTERS[player][0], player);
                float dy = y - BattlefieldCityLayout.sourceY(mapBounds,
                        ISLAND_CENTERS[player][1]);
                float distance = dx * dx + dy * dy;
                if (distance < closest) {
                    closest = distance;
                    island = player;
                }
            }
        }
        return island;
    }

    private static boolean containsPhonePoint(Rectangle bounds, float x, float y) {
        // pygame FRect includes left/top and excludes right/bottom in top-down space.
        return bounds != null && x >= bounds.x && x < bounds.x + bounds.width
                && y > bounds.y && y <= bounds.y + bounds.height;
    }

    private static boolean containsFinalControl(Rectangle bounds, float x, float y) {
        // ActionEntry.control_at compares all four edges inclusively.
        return bounds != null && x >= bounds.x && x <= bounds.x + bounds.width
                && y >= bounds.y && y <= bounds.y + bounds.height;
    }

    private static float projectedX(Rectangle mapBounds, float sourceX, int island) {
        float scale = BattlefieldCityLayout.sourceScale(mapBounds);
        float margin = (mapBounds.width - 800f * scale) / 2f;
        return mapBounds.x + margin + sourceX * scale + islandShift(mapBounds, island);
    }

    private static float islandShift(Rectangle mapBounds, int island) {
        float margin = (mapBounds.width - 800f * BattlefieldCityLayout.sourceScale(mapBounds))
                / 2f;
        if (island == 1 || island == 3) return -margin;
        if (island == 2 || island == 4) return margin;
        return 0f;
    }
}
