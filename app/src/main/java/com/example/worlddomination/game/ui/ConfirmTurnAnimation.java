package com.example.worlddomination.game.ui;

/** Source Btn_turn frames and the four 200 ms confirmation steps, advanced at 24 Hz. */
public final class ConfirmTurnAnimation {
    public static final int CLOSED_FRAME = 1;
    public static final int OPEN_FRAME = 11;
    public static final int PRESSED_FRAME = 12;
    public static final float SECOND_TUBE_DELAY_SECONDS = 0.2f;
    public static final float TURN_DELAY_SECONDS = 0.8f;
    private int frame = CLOSED_FRAME;
    private int target = CLOSED_FRAME;
    private boolean pending;
    private double elapsed;

    /** Returns true when an opening/closing sound should be played. */
    public boolean synchronize(int queuedActions, boolean playbackBlocking) {
        int next = !pending && !playbackBlocking && queuedActions == 2 ? OPEN_FRAME : CLOSED_FRAME;
        if (next == target) return false;
        target = next;
        return true;
    }

    public boolean press(int queuedActions, boolean playbackBlocking) {
        if (pending || playbackBlocking || queuedActions != 2 || frame != OPEN_FRAME) return false;
        pending = true;
        elapsed = 0;
        frame = PRESSED_FRAME;
        target = CLOSED_FRAME;
        return true;
    }

    /** Returns true exactly once when the source's 800 ms pre-turn sequence has completed. */
    public boolean tick(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f) return false;
        frame += Integer.compare(target, frame);
        if (!pending) return false;
        elapsed += seconds;
        if (elapsed + 0.000001 < TURN_DELAY_SECONDS || frame != CLOSED_FRAME) return false;
        pending = false;
        return true;
    }

    public int getFrame() { return frame; }
    public boolean isOpening() { return target == OPEN_FRAME; }
    public boolean isPending() { return pending; }
    public float getElapsedSeconds() { return (float) elapsed; }
    public boolean isAnimating() { return pending || frame != target; }

    public void reset() {
        frame = target = CLOSED_FRAME;
        pending = false;
        elapsed = 0;
    }
}
