package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.persistence.GameSaveStore;
import com.example.worlddomination.game.ui.GameSettingsMenu;

/** Two-column commander's profile, drawn entirely with the game's managed HUD resources. */
public final class SettingsSaveScreenRenderer {
    private static final Color NIGHT = Color.valueOf("101c24ff");
    private static final Color SIDEBAR = Color.valueOf("20323eff");
    private static final Color CARD = Color.valueOf("293e4cff");
    private static final Color SELECTED = Color.valueOf("375466ff");
    private static final Color STEEL = Color.valueOf("718d9bff");
    private static final Color LIGHT = Color.valueOf("c6d8dfff");
    private static final Color ORANGE = Color.valueOf("ff982dff");
    private static final String[] TITLES = {"Slot 1", "Slot 2", "Slot 3"};
    private static final String[] DIFFICULTIES = {"Easy", "Medium", "Hard"};

    private final GameSettingsMenu menu;
    private final String[] summary = new String[GameSaveStore.SLOT_COUNT];
    private final String[] resources = new String[GameSaveStore.SLOT_COUNT];
    private final String[] scores = new String[GameSaveStore.SLOT_COUNT];
    private int used;
    private String occupancy;

    public SettingsSaveScreenRenderer(GameSettingsMenu menu) {
        this.menu = menu;
    }

    /** Format only when a slot is inspected or written, outside the draw loop. */
    public void update(int slot, boolean exists, GameSaveStore.SlotDescription info) {
        int i = slot - 1;
        summary[i] = info == null ? (exists ? "Unreadable save" : "Empty") :
                "Turn " + info.turn + "   /   " + DIFFICULTIES[info.difficulty];
        resources[i] = info == null ? null : "$" + String.format(java.util.Locale.US, "%,d",
                (long) info.fundsMillions * 1_000_000L);
        scores[i] = info == null ? null : String.format(java.util.Locale.US, "%,d", info.score);
    }

    private float x(float value) { return menu.panel.x + value * menu.getScale(); }
    private float y(float value) { return menu.panel.y + value * menu.getScale(); }
    private float size(float value) { return value * menu.getScale(); }

    public void draw(Matrix4 projection, SpriteBatch batch, ShapeRenderer shapes, BitmapFont font,
                     OriginalUiAssets art, float worldWidth, float worldHeight, boolean soundEnabled) {
        if (menu.getScale() <= 0f) return;
        final int selected = menu.getSelectedSlot() - 1;
        final boolean confirming = menu.isConfirmingLoad();
        final boolean readable = resources[selected] != null;
        shapes.setProjectionMatrix(projection);
        batch.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(NIGHT);
        shapes.rect(0, 0, worldWidth, worldHeight);
        rect(shapes, 0, 0, 800, 360, STEEL);
        rect(shapes, 1, 1, 327, 358, SIDEBAR);
        rect(shapes, 329, 1, 470, 358, NIGHT);
        rect(shapes, 350, 305, 425, 1, CARD);
        rect(shapes, 350, 305, 70, 2, ORANGE);
        rect(shapes, 350, 151, 425, 119, CARD);
        for (int i = 0; i < menu.slots.length; i++) {
            fill(shapes, menu.slots[i], i == selected ? SELECTED : CARD);
            if (i == selected) rect(shapes, 16, 118 - 48 * i, 3, 42, ORANGE);
        }
        fill(shapes, menu.save, (confirming || menu.canSave()) ? ORANGE : CARD);
        fill(shapes, menu.load, (confirming || menu.canLoad()) ? SELECTED : CARD);
        shapes.end();

        batch.begin();
        batch.setColor(0.55f, 0.74f, 0.84f, 1f);
        batch.draw(art.metalPanel, x(1), y(279), size(327), size(80), 0f, 0f, 1.35f, 0.48f);
        batch.setColor(Color.WHITE);
        contain(batch, art.logo, 160, 283, 153, 71);
        if (readable) contain(batch, art.battleMap, 362, 201, 84, 58);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SIDEBAR);
        shapes.circle(x(58), y(278), size(34), 48);
        shapes.setColor(STEEL);
        shapes.circle(x(58), y(278), size(30), 48);
        shapes.setColor(NIGHT);
        shapes.circle(x(58), y(278), size(28), 48);
        shapes.end();

        batch.begin();
        batch.setColor(Color.WHITE);
        contain(batch, art.phoneGear, 38, 258, 40, 40);
        float oldScale = font.getScaleX();
        label(batch, font, "Saved games", 17, 232, 25, Color.WHITE);
        int count = 0;
        for (int i = 0; i < menu.slots.length; i++) {
            if (menu.isOccupied(i + 1)) count++;
            int top = 148 - 48 * i;
            label(batch, font, TITLES[i], 28, top, 17, Color.WHITE);
            label(batch, font, summary[i], 102, top - 1, 15, LIGHT);
        }
        if (count != used || occupancy == null) {
            used = count;
            occupancy = count + " of 3 slots used";
        }
        label(batch, font, occupancy, 18, 201, 15, LIGHT);
        label(batch, font, "Overview", 350, 337, 18, Color.WHITE);
        if (!confirming) buttonLabel(batch, font, menu.close, "Close", LIGHT, 15);
        label(batch, font, "Selected save", 350, 291, 14, LIGHT);
        int headingX = readable ? 459 : 366;
        label(batch, font, TITLES[selected], headingX, 253, 22, Color.WHITE);
        label(batch, font, summary[selected], headingX, 226, 16, LIGHT);
        if (readable) {
            label(batch, font, "Funds", 366, 193, 13, LIGHT);
            label(batch, font, resources[selected], 366, 177, 18, Color.WHITE);
            label(batch, font, "Score", 590, 193, 13, LIGHT);
            label(batch, font, scores[selected], 590, 177, 18, Color.WHITE);
        } else {
            String hint = menu.canLoad() ? "This save could not be read." :
                    (menu.canSave() ? "Save your current game here." : "Start a game to save here.");
            label(batch, font, hint, 366, 184, 15, LIGHT);
        }
        String message = menu.getStatus();
        if (confirming) message = menu.canSave() ? "Load this save and replace your current progress?" : "Load this saved game?";
        else if (message.isEmpty() && menu.canLoad() && menu.canSave()) message = "Saving here replaces this slot.";
        label(batch, font, message, 350, 125, 16, LIGHT);
        buttonLabel(batch, font, menu.save, confirming ? "Confirm load" : "Save game",
                confirming || menu.canSave() ? NIGHT : STEEL, 18);
        buttonLabel(batch, font, menu.load, confirming ? "Cancel" : "Load game",
                confirming || menu.canLoad() ? Color.WHITE : STEEL, 18);
        if (!confirming) buttonLabel(batch, font, menu.sound, soundEnabled ? "Sound on" : "Sound off", LIGHT, 15);
        font.getData().setScale(oldScale);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void label(SpriteBatch batch, BitmapFont font, String text, int dx, int dy, int height, Color color) {
        if (text == null || text.isEmpty()) return;
        font.getData().setScale(size(height) / 18f);
        font.setColor(color);
        font.draw(batch, text, x(dx), y(dy));
    }

    private void buttonLabel(SpriteBatch batch, BitmapFont font, Rectangle r, String text, Color color, int height) {
        font.getData().setScale(size(height) / 18f);
        font.setColor(color);
        font.draw(batch, text, r.x, r.y + (r.height + font.getCapHeight()) / 2f, r.width, Align.center, false);
    }

    private void contain(SpriteBatch batch, TextureRegion region, int dx, int dy, int w, int h) {
        float scale = Math.min(size(w) / region.getRegionWidth(), size(h) / region.getRegionHeight());
        float width = region.getRegionWidth() * scale, height = region.getRegionHeight() * scale;
        batch.draw(region, x(dx) + (size(w) - width) / 2f, y(dy) + (size(h) - height) / 2f, width, height);
    }

    private void rect(ShapeRenderer shapes, int dx, int dy, int width, int height, Color color) {
        shapes.setColor(color);
        shapes.rect(x(dx), y(dy), size(width), size(height));
    }

    private static void fill(ShapeRenderer shapes, Rectangle r, Color color) {
        shapes.setColor(color);
        shapes.rect(r.x, r.y, r.width, r.height);
    }
}
