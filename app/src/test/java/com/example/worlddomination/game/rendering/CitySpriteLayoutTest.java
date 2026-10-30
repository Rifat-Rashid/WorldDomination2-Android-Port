package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CitySpriteLayoutTest {
    @Test
    public void appliesOriginalCityOffsetsAfterUniformMapScaling() {
        Rectangle map = new Rectangle(0f, 0f, 523f, 318f);
        float spriteScale = CitySpriteLayout.scale(map, 0.8f);

        assertEquals(0.424f, spriteScale, 0.0001f);
        assertEquals(BattlefieldCityLayout.x(map, 0, 0) - 16f * spriteScale,
                CitySpriteLayout.left(map, 0, 0, -16f, 0.8f), 0.0001f);
        float expectedBottom = BattlefieldCityLayout.y(map, 0, 0)
                - (-23.5f * spriteScale) - 35f * spriteScale;
        assertEquals(expectedBottom,
                CitySpriteLayout.bottom(map, 0, 0, -23.5f, 35f, 0.8f), 0.0001f);
    }

    @Test
    public void cityDrawOrderMatchesSourceDepthAndContainsEachAddressOnce() {
        int[] order = BattlefieldCityLayout.cityRenderOrder();
        Set<Integer> addresses = new HashSet<>();

        assertEquals(BattlefieldCityLayout.PLAYER_COUNT
                * BattlefieldCityLayout.CITIES_PER_PLAYER, order.length);
        for (int i = 0; i < order.length; i++) {
            assertTrue(addresses.add(order[i]));
            if (i > 0) {
                assertTrue(BattlefieldCityLayout.sourceDepth(order[i - 1])
                        <= BattlefieldCityLayout.sourceDepth(order[i]));
            }
        }
        assertEquals(order.length, addresses.size());
    }
}
