package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** Resizable border from Flash shape 4447; the battlefield keeps its existing inset. */
public final class OriginalBattlefieldFrame {
    private final TextureRegion[] pieces = new TextureRegion[8];

    public OriginalBattlefieldFrame(TextureRegion source) {
        if (source.getRegionWidth() != 413 || source.getRegionHeight() != 304) {
            throw new IllegalArgumentException("Expected the original 413 by 304 frame");
        }
        pieces[0] = new TextureRegion(source, 0, 0, 28, 24);
        pieces[1] = new TextureRegion(source, 28, 0, 357, 24);
        pieces[2] = new TextureRegion(source, 385, 0, 28, 24);
        pieces[3] = new TextureRegion(source, 0, 24, 28, 256);
        pieces[4] = new TextureRegion(source, 385, 24, 28, 256);
        pieces[5] = new TextureRegion(source, 0, 280, 28, 24);
        // The middle of the original bottom rail has a baked MAIN SCREEN label.
        // Reuse its text-free left segment so no removed HUD label returns.
        pieces[6] = new TextureRegion(source, 35, 280, 85, 24);
        pieces[7] = new TextureRegion(source, 385, 280, 28, 24);
    }

    public void draw(SpriteBatch batch, Rectangle bounds) {
        if (bounds.width <= 0f || bounds.height <= 0f) return;
        float scale = Math.min(6f / 28f, Math.min(bounds.width / 56f, bounds.height / 48f));
        float w = 28f * scale, h = 24f * scale;
        float middleWidth = bounds.width - 2f * w;
        float middleHeight = bounds.height - 2f * h;
        float x = bounds.x, y = bounds.y;
        float right = x + bounds.width - w, top = y + bounds.height - h;
        float color = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(pieces[0], x, top, w, h);
        batch.draw(pieces[1], x + w, top, middleWidth, h);
        batch.draw(pieces[2], right, top, w, h);
        batch.draw(pieces[3], x, y + h, w, middleHeight);
        batch.draw(pieces[4], right, y + h, w, middleHeight);
        batch.draw(pieces[5], x, y, w, h);
        batch.draw(pieces[6], x + w, y, middleWidth, h);
        batch.draw(pieces[7], right, y, w, h);
        batch.setPackedColor(color);
    }
}
