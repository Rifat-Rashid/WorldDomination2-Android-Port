package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** Original shape-4450 navigation chrome, with the removed titles cleared offline. */
public final class SourceNavigationPanel {
    private final TextureRegion[][] pieces = new TextureRegion[3][3];

    public SourceNavigationPanel(TextureRegion source) {
        if (source.getRegionWidth() != 180 || source.getRegionHeight() != 301) {
            throw new IllegalArgumentException("Expected the original 180 by 301 navigation crop");
        }
        int[] x = {0, 16, 164};
        int[] y = {0, 16, 285};
        int[] width = {16, 148, 16};
        int[] height = {16, 269, 16};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                pieces[row][column] = new TextureRegion(source,
                        x[column], y[row], width[column], height[row]);
            }
        }
    }

    public void draw(SpriteBatch batch, Rectangle bounds) {
        // The frame must fit inside even a transient tiny viewport during resize.
        if (bounds.width <= 0f || bounds.height <= 0f) return;
        float edge = Math.min(8f, Math.min(bounds.width, bounds.height) / 2f);
        float color = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        // Keep the original region boundaries. Insetting stretch-region UVs
        // would resample even an exact-size source draw.
        for (int row = 0; row < 3; row++) {
            float y = row == 0 ? bounds.y + bounds.height - edge
                    : row == 1 ? bounds.y + edge : bounds.y;
            float height = row == 1 ? bounds.height - 2f * edge : edge;
            for (int column = 0; column < 3; column++) {
                float x = column == 0 ? bounds.x : column == 1 ? bounds.x + edge
                        : bounds.x + bounds.width - edge;
                float width = column == 1 ? bounds.width - 2f * edge : edge;
                if (width > 0f && height > 0f) {
                    batch.draw(pieces[row][column], x, y, width, height);
                }
            }
        }
        batch.setPackedColor(color);
    }
}
