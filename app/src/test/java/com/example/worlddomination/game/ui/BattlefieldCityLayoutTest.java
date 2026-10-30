package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class BattlefieldCityLayoutTest {
    @Test
    public void projectsReferenceAnchorsWithIslandOceanShifts() {
        Rectangle map = new Rectangle(0f, 0f, 523f, 318f);

        assertEquals(227.3945f,
                BattlefieldCityLayout.x(map, 0, 0), 0.001f);
        assertEquals(97.149f,
                BattlefieldCityLayout.y(map, 0, 0), 0.001f);
        assertEquals(15.37f,
                BattlefieldCityLayout.x(map, 1, 0), 0.001f);
        assertEquals(179.14f,
                BattlefieldCityLayout.y(map, 1, 0), 0.001f);
        assertEquals(385.73f,
                BattlefieldCityLayout.x(map, 2, 0), 0.001f);
        assertEquals(276.13f,
                BattlefieldCityLayout.y(map, 2, 0), 0.001f);
    }

    @Test
    public void everySourceCityAnchorIsTouchableWithinMapBounds() {
        Rectangle map = new Rectangle(12f, 8f, 523f, 318f);
        for (int playerId = 0; playerId < BattlefieldCityLayout.PLAYER_COUNT; playerId++) {
            for (int cityIndex = 0;
                 cityIndex < BattlefieldCityLayout.CITIES_PER_PLAYER; cityIndex++) {
                int expected = playerId * BattlefieldCityLayout.CITIES_PER_PLAYER + cityIndex;
                assertEquals(expected, BattlefieldCityLayout.hitTest(
                        BattlefieldCityLayout.x(map, playerId, cityIndex),
                        BattlefieldCityLayout.y(map, playerId, cityIndex), map));
            }
        }
    }

    @Test
    public void cityHitTestingClipsToMapAndUsesTouchFriendlySourceBounds() {
        Rectangle map = new Rectangle(0f, 0f, 1000f, 600f);
        float cityX = BattlefieldCityLayout.x(map, 1, 0);
        float cityY = BattlefieldCityLayout.y(map, 1, 0);
        float radius = BattlefieldCityLayout.selectionRadius(map);

        assertTrue(radius >= 24f);
        assertEquals(5, BattlefieldCityLayout.hitTest(cityX, cityY, map));
        assertEquals(-1, BattlefieldCityLayout.hitTest(cityX + radius + 1f, cityY, map));
        assertEquals(-1, BattlefieldCityLayout.hitTest(-1f, cityY, map));
    }
}
