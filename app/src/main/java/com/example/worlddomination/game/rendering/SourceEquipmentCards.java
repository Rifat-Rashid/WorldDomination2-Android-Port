package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** Eight separately authored inventory frames, retaining their individual shading. */
public final class SourceEquipmentCards {
    private static final int[] SOURCE_HEIGHTS = {73, 42, 117, 69, 79, 69, 69, 48};
    private static final int EDGE = 8;
    private final TextureRegion[][][] pieces = new TextureRegion[8][3][3];

    public SourceEquipmentCards(TextureRegion[] sources) {
        if (sources.length != SOURCE_HEIGHTS.length) {
            throw new IllegalArgumentException("Expected eight original equipment frames");
        }
        for (int group = 0; group < sources.length; group++) {
            TextureRegion source = sources[group];
            if (source.getRegionWidth() != 197
                    || source.getRegionHeight() != SOURCE_HEIGHTS[group]) {
                throw new IllegalArgumentException("Unexpected equipment frame dimensions: " + group);
            }
            // Shape 4536 includes ten fully transparent rows from its right-side
            // registration extension. Keep its visible lower rail on the card edge.
            int height = group == 4 ? 69 : SOURCE_HEIGHTS[group];
            int[] xs = {0, EDGE, 197 - EDGE};
            int[] ys = {0, EDGE, height - EDGE};
            int[] widths = {EDGE, 197 - EDGE * 2, EDGE};
            int[] heights = {EDGE, height - EDGE * 2, EDGE};
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    pieces[group][row][column] = new TextureRegion(source,
                            xs[column], ys[row], widths[column], heights[row]);
                }
            }
        }
    }

    public void draw(SpriteBatch batch, int group, Rectangle bounds) {
        if (group < 0 || group >= pieces.length) {
            throw new IllegalArgumentException("Invalid equipment group: " + group);
        }
        if (bounds.width <= 0f || bounds.height <= 0f) return;
        float edge = Math.min(EDGE, Math.min(bounds.width, bounds.height) / 2f);
        float color = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        for (int row = 0; row < 3; row++) {
            float y = row == 0 ? bounds.y + bounds.height - edge
                    : row == 1 ? bounds.y + edge : bounds.y;
            float height = row == 1 ? bounds.height - 2f * edge : edge;
            for (int column = 0; column < 3; column++) {
                float x = column == 0 ? bounds.x : column == 1 ? bounds.x + edge
                        : bounds.x + bounds.width - edge;
                float width = column == 1 ? bounds.width - 2f * edge : edge;
                if (width > 0f && height > 0f) {
                    batch.draw(pieces[group][row][column], x, y, width, height);
                }
            }
        }
        batch.setPackedColor(color);
    }
}
