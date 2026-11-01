package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

/** Fixed-tick fade-in for one of the source game's persistent action-building clips. */
public final class FlashActionBuildingAnimation {
    public static final int SOURCE_FRAME_RATE = 24;
    public static final int FADE_STEP_PERCENT = 5;
    public static final int FADE_TICKS = 100 / FADE_STEP_PERCENT;

    public final int playerId;
    public final GameState.Action action;
    public final int slot;
    public final String frameName;
    public final String religionCode;
    public final int sequenceIndex;

    private int elapsedTicks;

    public FlashActionBuildingAnimation(GameState.ActionBuildingEvent event) {
        if (event == null || event.playerId < 0 || event.playerId > 4
                || event.action == null || event.slot < 1 || event.slot > 2
                || event.frameName == null || event.frameName.isEmpty()
                || event.religionCode == null || event.religionCode.isEmpty()
                || event.sequenceIndex < 0) {
            throw new IllegalArgumentException("Invalid action-building event");
        }
        playerId = event.playerId;
        action = event.action;
        slot = event.slot;
        frameName = event.frameName;
        religionCode = event.religionCode;
        sequenceIndex = event.sequenceIndex;
    }

    /** Advances one source tick and returns whether the fade has reached full opacity. */
    public boolean tick() {
        if (elapsedTicks < FADE_TICKS) {
            elapsedTicks++;
        }
        return isFadeComplete();
    }

    /** True while the source 5-percent-per-frame fade is still running. */
    public boolean isAnimating() {
        return !isFadeComplete();
    }

    public boolean isFadeComplete() {
        return elapsedTicks >= FADE_TICKS;
    }

    public float getAlpha() {
        int fadeTicks = Math.min(FADE_TICKS, elapsedTicks);
        return Math.min(1f, fadeTicks * FADE_STEP_PERCENT / 100f);
    }
}
