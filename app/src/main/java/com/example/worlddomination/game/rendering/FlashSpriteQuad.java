package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

/** Exact affine projection of an exported SVG rectangle, including its registration offset. */
final class FlashSpriteQuad {
    private FlashSpriteQuad() { }

    /** Writes SpriteBatch's BL, TL, TR, BR positions; color/UV slots remain untouched. */
    static void positions(float[] vertices, FlashAffineTransform transform,
                          Rectangle mapBounds, float sourceX, float sourceY, int playerId,
                          float originX, float originY, float width, float height) {
        corner(vertices, 0, transform, mapBounds, sourceX, sourceY, playerId,
                originX, originY + height);
        corner(vertices, 5, transform, mapBounds, sourceX, sourceY, playerId,
                originX, originY);
        corner(vertices, 10, transform, mapBounds, sourceX, sourceY, playerId,
                originX + width, originY);
        corner(vertices, 15, transform, mapBounds, sourceX, sourceY, playerId,
                originX + width, originY + height);
    }

    private static void corner(float[] vertices, int offset, FlashAffineTransform transform,
                               Rectangle bounds, float x, float y, int playerId,
                               float localX, float localY) {
        vertices[offset] = BattlefieldCityLayout.sourceX(bounds,
                x + transform.tx + transform.a * localX + transform.c * localY, playerId);
        vertices[offset + 1] = BattlefieldCityLayout.sourceY(bounds,
                y + transform.ty + transform.b * localX + transform.d * localY);
    }
}
