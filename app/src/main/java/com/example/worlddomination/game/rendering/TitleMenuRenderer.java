package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.example.worlddomination.game.ui.TitleMenuLayout;

/** Stationary artwork and live controls over the animated title backdrop. */
public final class TitleMenuRenderer implements Disposable {
    private static final Color DISABLED_PLATE = Color.valueOf("8a9197");
    private static final Color PRESSED_PLATE = Color.valueOf("b8b2ac");
    private static final Color BUTTON_INK = Color.valueOf("fffdfa");
    private static final Color DISABLED_INK = Color.valueOf("d0d7dc");
    private static final Color METADATA_INK = Color.valueOf("b8c6d3");
    private static final Color MESSAGE_INK = Color.valueOf("ffcfaa");
    private static final Color LABEL_SHADOW = Color.valueOf("18191adb");

    private final Texture logo = texture("logo.png");
    private final Texture continuePlate = texture("continue-plate.png");
    private final Texture newGamePlate = texture("new-game-plate.png");
    private final GlyphLayout glyphs = new GlyphLayout();
    private final GlyphLayout shadow = new GlyphLayout();

    public TitleMenuRenderer() { }

    /** Draws inside the caller's begun batch, after the backdrop. */
    public void draw(SpriteBatch batch, BitmapFont font, TitleMenuLayout layout, boolean available,
                     TitleMenuLayout.Action pressed, String metadata, String message) {
        float scale = layout.scale;
        if (scale <= 0f) return;

        boolean continuePressed = available && pressed == TitleMenuLayout.Action.CONTINUE;
        boolean newGamePressed = pressed == TitleMenuLayout.Action.NEW_GAME;
        float continueOffset = continuePressed ? -1.5f * scale : 0f;
        float newGameOffset = newGamePressed ? -1.5f * scale : 0f;
        float previousTint = batch.getPackedColor();
        float previousScaleX = font.getScaleX();
        float previousScaleY = font.getScaleY();
        try {
            batch.setColor(Color.WHITE);
            drawContained(batch, logo, layout.logo, 0f);
            batch.setColor(available ? (continuePressed ? PRESSED_PLATE : Color.WHITE) : DISABLED_PLATE);
            drawContained(batch, continuePlate, layout.continueGame, continueOffset);
            batch.setColor(newGamePressed ? PRESSED_PLATE : Color.WHITE);
            drawContained(batch, newGamePlate, layout.newGame, newGameOffset);
            batch.setColor(Color.WHITE);

            font.getData().setScale(1f);
            float unitCapHeight = font.getCapHeight();
            float titleScale = 35f * scale / unitCapHeight;
            drawCaption(batch, font, "CONTINUE", layout.continueGame,
                    layout.continueGame.y + layout.continueGame.height * .5f + continueOffset,
                    titleScale, available ? BUTTON_INK : DISABLED_INK, scale);
            drawCaption(batch, font, "NEW GAME", layout.newGame,
                    layout.newGame.y + layout.newGame.height * .5f + newGameOffset,
                    titleScale, BUTTON_INK, scale);
            if (available) {
                float gapCenter = (layout.continueGame.y + layout.newGame.y + layout.newGame.height) * .5f;
                drawCaption(batch, font, metadata, layout.continueGame, gapCenter,
                        17f * scale / unitCapHeight, METADATA_INK, 0f);
            }
            drawCaption(batch, font, message, layout.newGame, layout.newGame.y - 22f * scale,
                    17f * scale / unitCapHeight, MESSAGE_INK, 0f);
        } finally {
            font.getData().setScale(previousScaleX, previousScaleY);
            batch.setPackedColor(previousTint);
        }
    }

    private void drawCaption(SpriteBatch batch, BitmapFont font, String text, Rectangle bounds,
                             float centerY, float fontScale, Color color, float weight) {
        if (text == null || text.isEmpty()) return;
        font.getData().setScale(fontScale);
        glyphs.setText(font, text, color, 0f, Align.left, false);
        float maxWidth = bounds.width * .92f;
        if (glyphs.width > maxWidth) {
            font.getData().setScale(fontScale * maxWidth / glyphs.width);
            glyphs.setText(font, text, color, 0f, Align.left, false);
        }
        float x = bounds.x + (bounds.width - glyphs.width) * .5f;
        float y = centerY + font.getCapHeight() * .5f;
        if (weight > 0f) {
            shadow.setText(font, text, LABEL_SHADOW, 0f, Align.left, false);
            font.draw(batch, shadow, x, y - weight);
            // Thicken the shared Arial glyphs without baking either title into the plate.
            font.draw(batch, glyphs, x - weight, y);
            font.draw(batch, glyphs, x + weight, y);
        }
        font.draw(batch, glyphs, x, y);
    }

    private static void drawContained(SpriteBatch batch, Texture texture, Rectangle bounds, float offsetY) {
        float scale = Math.min(bounds.width / texture.getWidth(), bounds.height / texture.getHeight());
        float width = texture.getWidth() * scale;
        float height = texture.getHeight() * scale;
        batch.draw(texture, bounds.x + (bounds.width - width) * .5f,
                bounds.y + (bounds.height - height) * .5f + offsetY, width, height);
    }

    private static Texture texture(String fileName) {
        Texture texture = new Texture(Gdx.files.internal("title-menu/" + fileName));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    @Override
    public void dispose() {
        logo.dispose();
        continuePlate.dispose();
        newGamePlate.dispose();
    }
}
