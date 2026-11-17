package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.ScorePanelLayout;

/** Source backing, separate score window, original caption, and player-color dots. */
public final class SourceScorePanel {
    private final Slices backing;
    private final Slices window;
    private final TextureRegion[] dots;
    private final TextureRegion title;
    private final Rectangle scratch = new Rectangle();

    public SourceScorePanel(TextureRegion backing, TextureRegion window,
            TextureRegion[] dots, TextureRegion title) {
        requireSize(backing, 180, 49);
        requireSize(window, 135, 28);
        requireSize(title, 148, 40);
        if (dots.length != 5) throw new IllegalArgumentException("Five source color indicators required");
        for (TextureRegion dot : dots) requireSize(dot, 18, 18);
        this.backing = new Slices(backing, 16);
        this.window = new Slices(window, 6);
        this.dots = dots.clone();
        this.title = title;
    }

    private static void requireSize(TextureRegion region, int width, int height) {
        if (region.getRegionWidth() != width || region.getRegionHeight() != height)
            throw new IllegalArgumentException("Unexpected score artwork dimensions");
    }

    public void draw(SpriteBatch batch, Rectangle bounds, int colorFrame) {
        if (colorFrame < 1 || colorFrame > 5) throw new IllegalArgumentException("Score color frame");
        if (bounds.width <= 0f || bounds.height <= 0f) return;
        float color = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        backing.draw(batch, bounds, ScorePanelLayout.edge(bounds));
        ScorePanelLayout.windowBounds(bounds, scratch);
        window.draw(batch, scratch, Math.min(3f, Math.min(scratch.width, scratch.height) / 2f));
        for (int index = 0; index < 2; index++) {
            ScorePanelLayout.dotBounds(bounds, index, scratch);
            batch.draw(dots[colorFrame - 1], scratch.x, scratch.y, scratch.width, scratch.height);
        }
        ScorePanelLayout.titleBounds(bounds, scratch);
        batch.draw(title, scratch.x, scratch.y, scratch.width, scratch.height);
        batch.setPackedColor(color);
    }

    private static final class Slices {
        private final TextureRegion[][] pieces = new TextureRegion[3][3];

        Slices(TextureRegion source, int edge) {
            int width = source.getRegionWidth(), height = source.getRegionHeight();
            int[] xs = {0, edge, width - edge}, ys = {0, edge, height - edge};
            int[] widths = {edge, width - 2 * edge, edge};
            int[] heights = {edge, height - 2 * edge, edge};
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
                pieces[row][col] = new TextureRegion(source, xs[col], ys[row], widths[col], heights[row]);
        }

        void draw(SpriteBatch batch, Rectangle bounds, float edge) {
            for (int row = 0; row < 3; row++) {
                float y = row == 0 ? bounds.y + bounds.height - edge
                        : row == 1 ? bounds.y + edge : bounds.y;
                float height = row == 1 ? bounds.height - 2f * edge : edge;
                for (int col = 0; col < 3; col++) {
                    float x = col == 0 ? bounds.x : col == 1 ? bounds.x + edge
                            : bounds.x + bounds.width - edge;
                    float width = col == 1 ? bounds.width - 2f * edge : edge;
                    if (width > 0f && height > 0f) batch.draw(pieces[row][col], x, y, width, height);
                }
            }
        }
    }
}
