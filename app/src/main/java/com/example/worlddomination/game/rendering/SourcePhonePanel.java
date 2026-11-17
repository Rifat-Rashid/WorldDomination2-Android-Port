package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** Phone adapter panel, using extracted source tile and source-drawn chrome. */
public final class SourcePhonePanel {
    private static final int CAP = 10;
    private final TextureRegion tile;
    private final TextureRegion partialTile;
    private final TextureRegion[][] parts = new TextureRegion[2][8];

    /** The caller owns the managed textures and must retain them through each draw. */
    public SourcePhonePanel(TextureRegion tile, TextureRegion shortChrome, TextureRegion tallChrome) {
        if (tile.getRegionWidth() != 160 || tile.getRegionHeight() != 14
                || shortChrome.getRegionWidth() != 160 || shortChrome.getRegionHeight() != 42
                || tallChrome.getRegionWidth() != 160 || tallChrome.getRegionHeight() != 43) {
            throw new IllegalArgumentException("Unexpected phone panel source dimensions");
        }
        this.tile = tile;
        partialTile = new TextureRegion(tile);
        makeParts(0, shortChrome);
        makeParts(1, tallChrome);
    }

    private void makeParts(int index, TextureRegion source) {
        int bottom = source.getRegionHeight() - CAP;
        parts[index][0] = new TextureRegion(source, 0, 0, CAP, CAP);
        parts[index][1] = new TextureRegion(source, 150, 0, CAP, CAP);
        parts[index][2] = new TextureRegion(source, 0, bottom, CAP, CAP);
        parts[index][3] = new TextureRegion(source, 150, bottom, CAP, CAP);
        parts[index][4] = new TextureRegion(source, 10, 0, 1, CAP);
        parts[index][5] = new TextureRegion(source, 10, bottom, 1, CAP);
        parts[index][6] = new TextureRegion(source, 0, 10, CAP, 1);
        parts[index][7] = new TextureRegion(source, 150, 10, CAP, 1);
    }

    /** Draws at rounded logical size. Bounds must be at least 20 by 20 logical pixels. */
    public void draw(SpriteBatch batch, Rectangle bounds) {
        int width = Math.round(bounds.width), height = Math.round(bounds.height);
        if (width < 20 || height < 20) return;
        float x = bounds.x, y = bounds.y;
        float savedColor = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        int innerWidth = width - 7, innerHeight = height - 7;
        for (int top = 0; top < innerHeight; top += 14) {
            int h = Math.min(14, innerHeight - top);
            for (int left = 0; left < innerWidth; left += 160) {
                int w = Math.min(160, innerWidth - left);
                partialTile.setRegion(tile, 0, 0, w, h);
                batch.draw(partialTile, x + 3 + left, y + height - 3 - top - h, w, h);
            }
        }
        TextureRegion[] p = parts[height > 42 ? 1 : 0];
        batch.draw(p[0], x, y + height - CAP, CAP, CAP);
        batch.draw(p[1], x + width - CAP, y + height - CAP, CAP, CAP);
        batch.draw(p[2], x, y, CAP, CAP);
        batch.draw(p[3], x + width - CAP, y, CAP, CAP);
        batch.draw(p[4], x + CAP, y + height - CAP, width - 2 * CAP, CAP);
        batch.draw(p[5], x + CAP, y, width - 2 * CAP, CAP);
        batch.draw(p[6], x, y + CAP, CAP, height - 2 * CAP);
        batch.draw(p[7], x + width - CAP, y + CAP, CAP, height - 2 * CAP);
        batch.setPackedColor(savedColor);
    }
}
