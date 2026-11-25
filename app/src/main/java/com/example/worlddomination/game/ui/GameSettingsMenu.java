package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.persistence.GameSaveStore;

/** Presentation state for the local save library. Slot numbers are one-based. */
public final class GameSettingsMenu {
    public enum Command { NONE, SAVE, LOAD, SOUND, CLOSE, CONFIRM_LOAD, CANCEL_LOAD }

    public final Rectangle panel = new Rectangle();
    public final Rectangle save = new Rectangle();
    public final Rectangle load = new Rectangle();
    public final Rectangle sound = new Rectangle();
    public final Rectangle close = new Rectangle();
    public final Rectangle confirm = new Rectangle();
    public final Rectangle cancel = new Rectangle();
    public final Rectangle[] slots = new Rectangle[GameSaveStore.SLOT_COUNT];
    private final boolean[] occupied = new boolean[slots.length];
    private float scale;
    private boolean open;
    private boolean confirmingLoad;
    private boolean saveAvailable;
    private int selectedSlot = 1;
    private String status = "";

    public GameSettingsMenu() {
        for (int i = 0; i < slots.length; i++) slots[i] = new Rectangle();
    }

    public void open(boolean canSave, boolean hasSave) {
        open(canSave, new boolean[]{hasSave, false, false});
    }

    public void open(boolean canSave, boolean[] availability) {
        System.arraycopy(availability, 0, occupied, 0, occupied.length);
        open = true;
        confirmingLoad = false;
        saveAvailable = canSave;
        selectedSlot = 1;
        status = "";
    }

    public void layout(Rectangle area) {
        scale = Math.max(0f, Math.min((area.width - 16f) / 800f, (area.height - 16f) / 360f));
        float width = 800f * scale;
        float height = 360f * scale;
        panel.set(area.x + (area.width - width) / 2f, area.y + (area.height - height) / 2f,
                width, height);
        for (int i = 0; i < slots.length; i++) place(slots[i], 16, 118 - 48 * i, 296, 42);
        place(save, 350, 45, 145, 40);
        place(load, 507, 45, 145, 40);
        place(sound, 350, 6, 145, 30);
        place(close, 738, 310, 52, 36);
        confirm.set(save);
        cancel.set(load);
    }

    private void place(Rectangle r, float x, float y, float w, float h) {
        r.set(panel.x + x * scale, panel.y + y * scale, w * scale, h * scale);
    }

    public Command hit(float x, float y) {
        if (!open || scale == 0f) return Command.NONE;
        if (confirmingLoad) {
            if (confirm.contains(x, y) && canLoad()) return Command.CONFIRM_LOAD;
            return cancel.contains(x, y) ? Command.CANCEL_LOAD : Command.NONE;
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].contains(x, y)) {
                selectedSlot = i + 1;
                status = "";
                return Command.NONE;
            }
        }
        if (save.contains(x, y) && saveAvailable) return Command.SAVE;
        if (load.contains(x, y) && canLoad()) return Command.LOAD;
        if (sound.contains(x, y)) return Command.SOUND;
        return close.contains(x, y) ? Command.CLOSE : Command.NONE;
    }

    public void requestLoad() { if (open && canLoad()) confirmingLoad = true; }
    public void back() { if (confirmingLoad) confirmingLoad = false; else close(); }
    public void close() { open = false; confirmingLoad = false; }
    public void saved(int turn) {
        occupied[selectedSlot - 1] = true;
        status = "Game saved. Turn " + turn + ".";
    }
    public void loaded(int turn) {
        saveAvailable = true;
        message("Loaded turn " + turn + ".");
    }
    public void message(String text) { status = text; confirmingLoad = false; }
    public int getSelectedSlot() { return selectedSlot; }
    public boolean isOccupied(int slot) { return occupied[slot - 1]; }
    public boolean isOpen() { return open; }
    public boolean isConfirmingLoad() { return confirmingLoad; }
    public boolean canSave() { return saveAvailable; }
    public boolean canLoad() { return isOccupied(selectedSlot); }
    public float getScale() { return scale; }
    public String getStatus() { return status; }
}
