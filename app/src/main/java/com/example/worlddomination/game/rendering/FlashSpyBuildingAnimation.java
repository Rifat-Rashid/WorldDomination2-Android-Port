package com.example.worlddomination.game.rendering;

/** Source-timed fade-in for a spy mission's original Flash action-building clip. */
public final class FlashSpyBuildingAnimation {
    public static final int SOURCE_FRAME_RATE = 24;
    public static final int FADE_STEP_PERCENT = 5;
    public static final int FADE_TICKS = 100 / FADE_STEP_PERCENT;
    public static final int DISPLAY_INTERVAL_MILLIS = 1600;
    public static final int DISPLAY_TICKS = Math.round(
            DISPLAY_INTERVAL_MILLIS * SOURCE_FRAME_RATE / 1000f);

    public final int attackerPlayerId;
    public final int defenderPlayerId;
    public final int actionBuildingSlot;
    public final String actionBuildingFrame;
    public final String religionCode;

    private final int startDelayTicks;
    private int elapsedTicks;
    private boolean finished;

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
        startDelayTicks = sequenceIndex * DISPLAY_TICKS;
    }

    /** Advances one 24 Hz source frame and returns true when the clip expires. */
    public boolean tick() {
        return tick(false);
    }

    /** Keeps the source clip visible while the same turn's unit playback is active. */
    public boolean tick(boolean holdForBattlePlayback) {
        if (!finished) {
            elapsedTicks++;
            finished = !holdForBattlePlayback
                    && elapsedTicks >= startDelayTicks + DISPLAY_TICKS;
        }
        return finished;
    }

    public boolean isStarted() {
        return elapsedTicks >= startDelayTicks;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Current Flash alpha in the libGDX 0..1 range. */
    public float getAlpha() {
        if (!isStarted() || isFinished()) {
            return 0f;
        }
        int fadeTicks = Math.min(FADE_TICKS, elapsedTicks - startDelayTicks);
        return Math.min(1f, fadeTicks * FADE_STEP_PERCENT / 100f);
    }
}
