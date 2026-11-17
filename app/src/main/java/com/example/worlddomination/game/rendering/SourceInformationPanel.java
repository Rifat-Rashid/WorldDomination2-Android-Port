package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** Authored Funds panel, retaining the recessed statistics well and corner proportions. */
public final class SourceInformationPanel {
    private final TextureRegion[][] pieces = new TextureRegion[3][3];

    public SourceInformationPanel(TextureRegion source) {
        if (source.getRegionWidth() != 180 || source.getRegionHeight() != 102) {
            throw new IllegalArgumentException("Expected the original 180 by 102 information crop");
        }
        int[] x = {0, 20, 160};
        int[] y = {0, 20, 82};
        int[] width = {20, 140, 20};
        int[] height = {20, 62, 20};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                pieces[row][column] = new TextureRegion(source,
                        x[column], y[row], width[column], height[row]);
            }
        }
    }

    public void draw(SpriteBatch batch, Rectangle bounds) {
        if (bounds.width <= 0f || bounds.height <= 0f) return;
        float edge = Math.min(8f, Math.min(bounds.width, bounds.height) / 2f);
        float color = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        // Preserve source UV boundaries so an effective 1:1 draw retains every pixel.
        // The 20-to-8 corner scale keeps the well inside the existing text margins.
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
