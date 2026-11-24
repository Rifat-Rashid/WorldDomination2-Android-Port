package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Native-only settings overlay; never changes queued gameplay actions itself. */
public final class GameSettingsMenu {
    public enum Command { NONE, SAVE, LOAD, SOUND, CLOSE, CONFIRM_LOAD, CANCEL_LOAD }

    private static final float PANEL_WIDTH = 300f;
    private static final float PANEL_HEIGHT = 236f;
    private static final float OUTER_PADDING = 8f;
    private static final float ROW_HEIGHT = 34f;
    private static final float ROW_SPACING = 6f;
    private static final float ROW_INSET = 18f;

    public final Rectangle panel = new Rectangle();
    public final Rectangle save = new Rectangle();
    public final Rectangle load = new Rectangle();
    public final Rectangle sound = new Rectangle();
    public final Rectangle close = new Rectangle();
    public final Rectangle confirm = new Rectangle();
    public final Rectangle cancel = new Rectangle();
    private final Rectangle[] rows = {save, load, sound, close};
    private float scale;
    private boolean open;
    private boolean confirmingLoad;
    private boolean saveAvailable;
    private boolean loadAvailable;
    private String status = "";

    public void open(boolean canSave, boolean hasSave) {
        open = true;
        confirmingLoad = false;
        saveAvailable = canSave;
        loadAvailable = hasSave;
        status = hasSave ? "One local save. Saving replaces it." : "No saved game on this device.";
    }

    public void layout(Rectangle area) {
        float availableWidth = Math.max(0f, area.width - 2f * OUTER_PADDING);
        float availableHeight = Math.max(0f, area.height - 2f * OUTER_PADDING);
        scale = Math.min(1f, Math.min(availableWidth / PANEL_WIDTH, availableHeight / PANEL_HEIGHT));
        float width = PANEL_WIDTH * scale;
        float height = PANEL_HEIGHT * scale;
        panel.set(area.x + (area.width - width) / 2f,
                area.y + (area.height - height) / 2f, width, height);

        float rowTop = PANEL_HEIGHT - 56f;
        float rowWidth = PANEL_WIDTH - 2f * ROW_INSET;
        for (int i = 0; i < rows.length; i++) {
            place(rows[i], ROW_INSET, rowTop - ROW_HEIGHT - i * (ROW_HEIGHT + ROW_SPACING),
                    rowWidth, ROW_HEIGHT);
        }
        float half = (rowWidth - ROW_SPACING) / 2f;
        place(cancel, ROW_INSET, 30f, half, 32f);
        place(confirm, ROW_INSET + half + ROW_SPACING, 30f, half, 32f);
    }

    private void place(Rectangle rectangle, float x, float y, float width, float height) {
        rectangle.set(panel.x + x * scale, panel.y + y * scale, width * scale, height * scale);
    }

    public Command hit(float x, float y) {
        if (!open || scale == 0f) return Command.NONE;
        if (confirmingLoad) {
            if (confirm.contains(x, y) && loadAvailable) return Command.CONFIRM_LOAD;
            return cancel.contains(x, y) ? Command.CANCEL_LOAD : Command.NONE;
        }
        if (save.contains(x, y) && saveAvailable) return Command.SAVE;
        if (load.contains(x, y) && loadAvailable) return Command.LOAD;
        if (sound.contains(x, y)) return Command.SOUND;
        return close.contains(x, y) ? Command.CLOSE : Command.NONE;
    }

    public void requestLoad() { if (open && loadAvailable) confirmingLoad = true; }
    public void back() { if (confirmingLoad) confirmingLoad = false; else close(); }
    public void close() { open = false; confirmingLoad = false; }
    public void saved(int turn) { loadAvailable = true; status = "Game saved. Turn " + turn + "."; }
    public void message(String text) { status = text; confirmingLoad = false; }
    public boolean isOpen() { return open; }
    public boolean isConfirmingLoad() { return confirmingLoad; }
    public boolean canSave() { return saveAvailable; }
    public boolean canLoad() { return loadAvailable; }
    public float getScale() { return scale; }
    public String getStatus() { return status; }
}
