package com.example.worlddomination.game.rendering;

/** Source-timed fade-in for a spy mission's original Flash action-building clip. */
public final class FlashSpyBuildingAnimation {
    public static final int SOURCE_FRAME_RATE = 24;
    public static final int FADE_STEP_PERCENT = 5;
    public static final int FADE_TICKS = 100 / FADE_STEP_PERCENT;
    public static final int SEQUENCE_INTERVAL_MILLIS = 1600;
    public static final int SEQUENCE_INTERVAL_TICKS = Math.round(
            SEQUENCE_INTERVAL_MILLIS * SOURCE_FRAME_RATE / 1000f);

    public final int attackerPlayerId;
    public final int defenderPlayerId;
    public final int actionBuildingSlot;
    public final String actionBuildingFrame;
    public final String religionCode;

    private final int startDelayTicks;
    private int elapsedTicks;

    public FlashSpyBuildingAnimation(int attackerPlayerId, int defenderPlayerId,
                                     int actionBuildingSlot, String actionBuildingFrame,
                                     String religionCode, int sequenceIndex) {
        if (attackerPlayerId < 0 || attackerPlayerId > 4
                || defenderPlayerId < 0 || defenderPlayerId > 4
                || actionBuildingSlot < 1 || actionBuildingSlot > 2
                || actionBuildingFrame == null || actionBuildingFrame.isEmpty()
                || religionCode == null || religionCode.isEmpty() || sequenceIndex < 0) {
            throw new IllegalArgumentException("Invalid source spy building presentation data");
        }
        this.attackerPlayerId = attackerPlayerId;
        this.defenderPlayerId = defenderPlayerId;
        this.actionBuildingSlot = actionBuildingSlot;
        this.actionBuildingFrame = actionBuildingFrame;
        this.religionCode = religionCode;
        startDelayTicks = sequenceIndex * SEQUENCE_INTERVAL_TICKS;
    }

    /** Advances one 24 Hz source frame; the clip stays visible after fading in. */
    public boolean tick() {
        if (elapsedTicks < startDelayTicks + FADE_TICKS) {
            elapsedTicks++;
        }
        return isFadeComplete();
    }

    public boolean isStarted() {
        return elapsedTicks >= startDelayTicks;
    }

    /** True while this clip still needs fixed-tick updates for its delay or fade. */
    public boolean isAnimating() {
        return !isStarted() || !isFadeComplete();
    }

    /** Fade completion does not retire the source clip; it remains until its slot is reused. */
    public boolean isFadeComplete() {
        return isStarted() && elapsedTicks - startDelayTicks >= FADE_TICKS;
    }

    /** Current Flash alpha in the libGDX 0..1 range. */
    public float getAlpha() {
        if (!isStarted()) {
            return 0f;
        }
        int fadeTicks = Math.min(FADE_TICKS, elapsedTicks - startDelayTicks);
        return Math.min(1f, fadeTicks * FADE_STEP_PERCENT / 100f);
    }
}
