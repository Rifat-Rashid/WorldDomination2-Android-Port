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
    // Scale the plate and its label together within the full-size touch target.
    private static final float BUTTON_ART_SCALE = 0.88f;
    private static final int PLATE_END_PIXELS = 26;
    private static final String[] SLOT_LABELS = {"Slot 1", "Slot 2", "Slot 3"};
    private static final Color UNAVAILABLE_PLATE = Color.valueOf("8a9197");
    private static final Color PRESSED_PLATE = Color.valueOf("b8b2ac");
    private static final Color BUTTON_INK = Color.valueOf("fffdfa");
    private static final Color UNAVAILABLE_INK = Color.valueOf("a9b4bd");
    private static final Color METADATA_INK = Color.valueOf("b8c6d3");
    private static final Color MESSAGE_INK = Color.valueOf("ffcfaa");
    private static final Color LABEL_SHADOW = Color.valueOf("18191adb");

    private final Texture logo = texture("logo.png");
    private final Texture orangePlate = texture("orange-plate.png");
    private final Texture steelPlate = texture("steel-plate.png");
    private final BitmapFont buttonFont = loadButtonFont();
    private final float buttonCapHeight = buttonFont.getCapHeight();
    private final GlyphLayout glyphs = new GlyphLayout();
    private final GlyphLayout shadow = new GlyphLayout();

    public TitleMenuRenderer() { }

    /** Draws inside the caller's begun batch, after the backdrop. */
    public void draw(SpriteBatch batch, BitmapFont font, TitleMenuLayout layout, boolean available,
                     TitleMenuLayout.Action pressed, String metadata, String message,
                     boolean picking, String[] summaries, boolean[] loadable) {
        float scale = layout.scale;
        if (scale <= 0f) return;

        float previousTint = batch.getPackedColor();
        float previousScaleX = font.getScaleX();
        float previousScaleY = font.getScaleY();
        try {
            batch.setColor(Color.WHITE);
            drawContained(batch, logo, layout.logo, 1f, 0f);
            font.getData().setScale(1f);
            float bodyCapHeight = font.getCapHeight();
            float captionScale = 17f * scale / bodyCapHeight;
            if (picking) {
                drawSlots(batch, font, layout, pressed, summaries, loadable, bodyCapHeight);
                drawCaption(batch, font, message, layout.continueGame, layout.back.y - 22f * scale,
                        captionScale, MESSAGE_INK, 0f);
            } else {
                drawMainActions(batch, layout, available, pressed);
                if (available) {
                    float gapCenter = (layout.continueGame.y + layout.newGame.y + layout.newGame.height) * .5f;
                    drawCaption(batch, font, metadata, layout.continueGame, gapCenter,
                            captionScale, METADATA_INK, 0f);
                }
                drawCaption(batch, font, message, layout.newGame, layout.newGame.y - 22f * scale,
                        captionScale, MESSAGE_INK, 0f);
            }
        } finally {
            font.getData().setScale(previousScaleX, previousScaleY);
            batch.setPackedColor(previousTint);
        }
    }

    private void drawMainActions(SpriteBatch batch, TitleMenuLayout layout, boolean available,
                                 TitleMenuLayout.Action pressed) {
        float scale = layout.scale;
        float titleScale = 35f * BUTTON_ART_SCALE * scale / buttonCapHeight;
        drawMainButton(batch, "CONTINUE", available ? orangePlate : steelPlate, layout.continueGame,
                pressed == TitleMenuLayout.Action.CONTINUE, titleScale, scale);
        drawMainButton(batch, "NEW GAME", available ? steelPlate : orangePlate, layout.newGame,
                pressed == TitleMenuLayout.Action.NEW_GAME, titleScale, scale);
    }

    private void drawMainButton(SpriteBatch batch, String label, Texture plate, Rectangle bounds,
                                boolean pressed, float fontScale, float scale) {
        float offset = pressed ? -1.5f * scale : 0f;
        batch.setColor(pressed ? PRESSED_PLATE : Color.WHITE);
        drawContained(batch, plate, bounds, BUTTON_ART_SCALE, offset);
        batch.setColor(Color.WHITE);
        drawCaption(batch, buttonFont, label, bounds, bounds.y + bounds.height * .5f + offset,
                fontScale, BUTTON_INK, scale);
    }

    private void drawSlots(SpriteBatch batch, BitmapFont font, TitleMenuLayout layout,
                           TitleMenuLayout.Action pressed, String[] summaries, boolean[] loadable,
                           float bodyCapHeight) {
        float scale = layout.scale;
        float labelScale = 19f * scale / bodyCapHeight;
        float summaryScale = 16f * scale / bodyCapHeight;
        for (int slot = 0; slot < SLOT_LABELS.length; slot++) {
            Rectangle row = layout.slots[slot];
            boolean rowPressed = loadable[slot] && pressed == TitleMenuLayout.Action.slot(slot);
            float offset = rowPressed ? -1.5f * scale : 0f;
            Color rowTint = loadable[slot] ? Color.WHITE : UNAVAILABLE_PLATE;
            batch.setColor(rowPressed ? PRESSED_PLATE : rowTint);
            drawSteelPlate(batch, row, offset);
            batch.setColor(Color.WHITE);

            float inset = 23f * scale;
            float x = row.x + inset;
            float width = row.width - 2f * inset;
            float centerY = row.y + row.height * .5f + offset;
            drawLabel(batch, font, SLOT_LABELS[slot], x, width * .30f, Align.left, centerY,
                    labelScale, loadable[slot] ? BUTTON_INK : UNAVAILABLE_INK, scale);
            drawLabel(batch, font, summaries[slot], x + width * .32f, width * .68f, Align.right, centerY,
                    summaryScale, loadable[slot] ? METADATA_INK : UNAVAILABLE_INK, scale);
        }
        Rectangle back = layout.back;
        boolean backPressed = pressed == TitleMenuLayout.Action.BACK;
        float offset = backPressed ? -1.5f * scale : 0f;
        batch.setColor(backPressed ? PRESSED_PLATE : Color.WHITE);
        drawSteelPlate(batch, back, offset);
        batch.setColor(Color.WHITE);
        drawCaption(batch, buttonFont, "BACK", back, back.y + back.height * .5f + offset,
                20f * scale / buttonCapHeight, BUTTON_INK, scale);
    }

    private void drawSteelPlate(SpriteBatch batch, Rectangle bounds, float offsetY) {
        int width = steelPlate.getWidth();
        int height = steelPlate.getHeight();
        float endWidth = PLATE_END_PIXELS * bounds.height / height;
        float y = bounds.y + offsetY;
        // Stretch only the flat center of the plate so the original corners keep their proportions.
        batch.draw(steelPlate, bounds.x, y, endWidth, bounds.height,
                0, 0, PLATE_END_PIXELS, height, false, false);
        batch.draw(steelPlate, bounds.x + endWidth, y, bounds.width - 2f * endWidth, bounds.height,
                PLATE_END_PIXELS, 0, width - 2 * PLATE_END_PIXELS, height, false, false);
        batch.draw(steelPlate, bounds.x + bounds.width - endWidth, y, endWidth, bounds.height,
                width - PLATE_END_PIXELS, 0, PLATE_END_PIXELS, height, false, false);
    }

    private void drawCaption(SpriteBatch batch, BitmapFont font, String text, Rectangle bounds,
                             float centerY, float fontScale, Color color, float shadowOffset) {
        drawLabel(batch, font, text, bounds.x + bounds.width * .04f, bounds.width * .92f,
                Align.center, centerY, fontScale, color, shadowOffset);
    }

    private void drawLabel(SpriteBatch batch, BitmapFont font, String text, float x, float width,
                           int alignment, float centerY, float fontScale, Color color, float shadowOffset) {
        if (text == null || text.isEmpty()) return;
        font.getData().setScale(fontScale);
        glyphs.setText(font, text, color, 0f, Align.left, false);
        if (glyphs.width > width) {
            font.getData().setScale(fontScale * width / glyphs.width);
            glyphs.setText(font, text, color, 0f, Align.left, false);
        }
        float spareWidth = width - glyphs.width;
        if (alignment == Align.right) x += spareWidth;
        if (alignment == Align.center) x += spareWidth * .5f;
        float y = centerY + font.getCapHeight() * .5f;
        if (shadowOffset > 0f) {
            shadow.setText(font, text, LABEL_SHADOW, 0f, Align.left, false);
            font.draw(batch, shadow, x, y - shadowOffset);
        }
        font.draw(batch, glyphs, x, y);
    }

    private static void drawContained(SpriteBatch batch, Texture texture, Rectangle bounds,
                                      float artScale, float offsetY) {
        float scale = artScale * Math.min(bounds.width / texture.getWidth(), bounds.height / texture.getHeight());
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

    private static BitmapFont loadButtonFont() {
        BitmapFont font = new BitmapFont(Gdx.files.internal("title-menu/button-caps.fnt"));
        font.setUseIntegerPositions(false);
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return font;
    }

    @Override
    public void dispose() {
        logo.dispose();
        orangePlate.dispose();
        steelPlate.dispose();
        buttonFont.dispose();
    }
}
