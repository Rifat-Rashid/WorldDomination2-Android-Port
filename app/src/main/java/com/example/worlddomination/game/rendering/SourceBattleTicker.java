package com.example.worlddomination.game.rendering;

/**
 * The top-level cleanup decision in source View.f_37, called once per 24 Hz
 * battle ticker frame. The count includes frames while action labels play.
 */
public final class SourceBattleTicker {
    public enum FrameDecision {
        ADVANCE,
        SUPPRESS,
        CLEANUP
    }

    private static final int LAST_ACTIVE_FRAME = 800;

    private long frameCount;
    private boolean running;

    public void begin() {
        frameCount = 0;
        running = true;
    }

    public void reset() {
        frameCount = 0;
        running = false;
    }

    /**
     * Increments the ticker before testing for empty active arrays or count
     * greater than 800. The source {@code kill_all} flag is a cheat switch;
     * ordinary native playback passes false until that cheat is supported.
     */
    public FrameDecision advanceFrame(boolean hasActiveUnitsOrSubs,
                                      boolean killAllCheatActive) {
        if (!running) {
            throw new IllegalStateException("Battle ticker has not begun");
        }
        frameCount++;
        if (!hasActiveUnitsOrSubs || frameCount > LAST_ACTIVE_FRAME) {
            if (killAllCheatActive) {
                return FrameDecision.SUPPRESS;
            }
            running = false;
            return FrameDecision.CLEANUP;
        }
        return FrameDecision.ADVANCE;
    }

    public boolean isRunning() {
        return running;
    }

    public long getFrameCount() {
        return frameCount;
    }
}
