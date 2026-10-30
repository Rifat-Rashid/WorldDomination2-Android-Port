package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Projects the source's five-by-five city anchors into the fitted battlefield map. */
public final class BattlefieldCityLayout {
    public static final int PLAYER_COUNT = 5;
    public static final int CITIES_PER_PLAYER = 5;

    private static final float SOURCE_WIDTH = 800f;
    private static final float SOURCE_HEIGHT = 600f;
    private static final float CITY_HIT_RADIUS_SOURCE = 24f;
    private static final float MIN_CITY_HIT_RADIUS = 7f;

    // battleMC citypos{player}_{city} anchors recovered from the reference scene.
    private static final float[][] CITY_X = {
            {335.65f, 297.70f, 391.70f, 564.50f, 475.25f},
            {29.00f, 141.65f, 212.80f, 251.25f, 35.00f},
            {541.00f, 605.00f, 691.80f, 767.80f, 772.80f},
            {325.95f, 247.95f, 152.00f, 34.00f, 32.00f},
            {502.75f, 604.60f, 664.45f, 772.80f, 769.80f}
    };
    private static final float[][] CITY_Y = {
            {416.70f, 301.75f, 248.50f, 318.00f, 442.00f},
            {262.00f, 256.05f, 166.75f, 76.85f, 83.85f},
            {79.00f, 170.00f, 215.55f, 271.75f, 83.10f},
            {590.05f, 490.25f, 433.70f, 418.70f, 585.15f},
            {577.75f, 502.50f, 429.70f, 397.90f, 573.90f}
    };
    private static final int[] CITY_RENDER_ORDER = createCityRenderOrder();

    private BattlefieldCityLayout() { }

    /** Returns the source city address as playerId * 5 + cityIndex, or -1 outside a city. */
    public static int hitTest(float x, float y, Rectangle mapBounds) {
        if (!mapBounds.contains(x, y)) {
            return -1;
        }
        float scale = sourceScale(mapBounds);
        float radius = Math.max(MIN_CITY_HIT_RADIUS, CITY_HIT_RADIUS_SOURCE * scale);
        float nearestDistance = radius * radius;
        int nearestCity = -1;
        for (int playerId = 0; playerId < PLAYER_COUNT; playerId++) {
            for (int cityIndex = 0; cityIndex < CITIES_PER_PLAYER; cityIndex++) {
                float dx = x(mapBounds, playerId, cityIndex) - x;
                float dy = y(mapBounds, playerId, cityIndex) - y;
                float distance = dx * dx + dy * dy;
                if (distance <= nearestDistance) {
                    if (distance < nearestDistance || nearestCity < 0) {
                        nearestCity = playerId * CITIES_PER_PLAYER + cityIndex;
                    }
                    nearestDistance = distance;
                }
            }
        }
        return nearestCity;
    }

    /** Returns the source city anchor's x-coordinate in libGDX world space. */
    public static float x(Rectangle mapBounds, int playerId, int cityIndex) {
        validateCity(playerId, cityIndex);
        float scale = sourceScale(mapBounds);
        float marginX = (mapBounds.width - SOURCE_WIDTH * scale) / 2f;
        return mapBounds.x + marginX + CITY_X[playerId][cityIndex] * scale
                + islandShift(playerId, marginX);
    }

    /** Returns the source city anchor's y-coordinate in libGDX world space. */
    public static float y(Rectangle mapBounds, int playerId, int cityIndex) {
        validateCity(playerId, cityIndex);
        float scale = sourceScale(mapBounds);
        float marginY = (mapBounds.height - SOURCE_HEIGHT * scale) / 2f;
        float topDownY = marginY + CITY_Y[playerId][cityIndex] * scale;
        return mapBounds.y + mapBounds.height - topDownY;
    }

    /** Returns the city-selection outline radius in libGDX world space. */
    public static float selectionRadius(Rectangle mapBounds) {
        return Math.max(MIN_CITY_HIT_RADIUS, CITY_HIT_RADIUS_SOURCE
                * sourceScale(mapBounds));
    }

    /** Source image transform used by both map sprites and city sprite offsets. */
    public static float sourceScale(Rectangle mapBounds) {
        if (mapBounds.width <= 0f || mapBounds.height <= 0f) {
            throw new IllegalArgumentException("Map bounds must have positive dimensions");
        }
        return Math.min(mapBounds.width / SOURCE_WIDTH, mapBounds.height / SOURCE_HEIGHT);
    }

    /** Source City image depths, sorted back-to-front once and reused by the renderer. */
    public static int[] cityRenderOrder() {
        return CITY_RENDER_ORDER.clone();
    }

    private static int[] createCityRenderOrder() {
        int[] order = new int[PLAYER_COUNT * CITIES_PER_PLAYER];
        for (int cityAddress = 0; cityAddress < order.length; cityAddress++) {
            order[cityAddress] = cityAddress;
        }
        for (int i = 1; i < order.length; i++) {
            int cityAddress = order[i];
            int depth = sourceDepth(cityAddress);
            int destination = i;
            while (destination > 0 && sourceDepth(order[destination - 1]) > depth) {
                order[destination] = order[destination - 1];
                destination--;
            }
            order[destination] = cityAddress;
        }
        return order;
    }

    public static int sourceDepth(int cityAddress) {
        if (cityAddress < 0 || cityAddress >= PLAYER_COUNT * CITIES_PER_PLAYER) {
            throw new IllegalArgumentException("Invalid battlefield city address");
        }
        int playerId = cityAddress / CITIES_PER_PLAYER;
        int cityIndex = cityAddress % CITIES_PER_PLAYER;
        int sourceCityId = cityAddress + 1;
        return (int) Math.floor((CITY_Y[playerId][cityIndex] + 15f) / 10f) * 500
                + 4 + sourceCityId;
    }

    private static float islandShift(int playerId, float marginX) {
        if (playerId == 1 || playerId == 3) {
            return -marginX;
        }
        if (playerId == 2 || playerId == 4) {
            return marginX;
        }
        return 0f;
    }

    private static void validateCity(int playerId, int cityIndex) {
        if (playerId < 0 || playerId >= PLAYER_COUNT
                || cityIndex < 0 || cityIndex >= CITIES_PER_PLAYER) {
            throw new IllegalArgumentException("Invalid battlefield city address");
        }
    }
}
