package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** Source tube frames plus a short label fill for the mobile default-open slots. */
public final class ActionSlotAnimation {
    public static final int OPEN_FRAME = 10;
    public static final int SET_FRAME = 11;
    public static final int LABEL_FILL_TICKS = 9;
    private final int[] frames = {OPEN_FRAME, OPEN_FRAME};
    private final int[] targets = {OPEN_FRAME, OPEN_FRAME};
    private final int[] labelTicks = new int[2];
    private final GameState.Action[] displayed = new GameState.Action[2];

    /** Preview the selected action in the next free slot; accepting still owns the queue. */
    public void synchronize(GameState state, GameState.Action playbackAction) {
        for (int slot = 0; slot < 2; slot++) {
            GameState.Action queued = state.getQueuedAction(slot);
            GameState.Action next = queued;
            int target = queued == null ? OPEN_FRAME : SET_FRAME;
            if (slot == 0 && playbackAction != null) {
                next = playbackAction;
                target = OPEN_FRAME;
            } else if (queued == null && slot == state.getQueuedActionCount()) {
                next = state.getSelectedAction();
            }
            if (next != displayed[slot]) {
                displayed[slot] = next;
                labelTicks[slot] = 0;
            }
            targets[slot] = target;
        }
    }

    public void tick() {
        for (int slot = 0; slot < 2; slot++) {
            frames[slot] += Integer.compare(targets[slot], frames[slot]);
            if (displayed[slot] != null && labelTicks[slot] < LABEL_FILL_TICKS) {
                labelTicks[slot]++;
            }
        }
    }

    public void reset() {
        for (int slot = 0; slot < 2; slot++) {
            frames[slot] = targets[slot] = OPEN_FRAME;
            displayed[slot] = null;
            labelTicks[slot] = 0;
        }
    }

    public int getFrame(int slot) { return frames[slot]; }
    public GameState.Action getDisplayedAction(int slot) { return displayed[slot]; }
    public float getLabelAlpha(int slot) { return labelTicks[slot] / (float) LABEL_FILL_TICKS; }

    public boolean isAnimating() {
        for (int slot = 0; slot < 2; slot++) {
            if (frames[slot] != targets[slot]
                    || (displayed[slot] != null && labelTicks[slot] < LABEL_FILL_TICKS)) {
                return true;
            }
        }
        return false;
    }
}
