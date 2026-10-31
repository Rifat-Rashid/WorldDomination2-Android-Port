package com.example.worlddomination.game.rendering;

/** Presentation state for the source 24 Hz city fade, frame switch, and fade-in. */
public final class CityShrinkSequence {
    public enum Phase {
        IDLE,
        FADE_OUT,
        SWITCH_FRAME,
        FADE_IN
    }

    private static final int ALPHA_STEP = 15;
    private static final int FULL_ALPHA = 100;

    private int frameNumber = -1;
    private int targetFrame = -1;
    private int alpha = FULL_ALPHA;
    private Phase phase = Phase.IDLE;

    /** Sets the initial artwork without animating the start of a new match. */
    public void reset(int initialFrame) {
        frameNumber = initialFrame;
        targetFrame = initialFrame;
        alpha = FULL_ALPHA;
        phase = Phase.IDLE;
    }

    /** Starts a source-style transition while preserving the current fade alpha. */
    public void target(int nextFrame) {
        if (nextFrame == targetFrame) {
            return;
        }
        targetFrame = nextFrame;
        phase = Phase.FADE_OUT;
    }

    /** Advances one original Flash frame at 24 Hz. */
    public void tick() {
        switch (phase) {
            case FADE_OUT:
                if (alpha > 0) {
                    alpha = Math.max(0, alpha - ALPHA_STEP);
                } else {
                    phase = Phase.SWITCH_FRAME;
                }
                break;
            case SWITCH_FRAME:
                frameNumber = targetFrame;
                phase = Phase.FADE_IN;
                break;
            case FADE_IN:
                if (alpha < FULL_ALPHA) {
                    alpha = Math.min(FULL_ALPHA, alpha + ALPHA_STEP);
                } else {
                    phase = Phase.IDLE;
                }
                break;
            case IDLE:
            default:
                break;
        }
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public int getTargetFrame() {
        return targetFrame;
    }

    public int getAlpha() {
        return alpha;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isAnimating() {
        return phase != Phase.IDLE;
    }
}
