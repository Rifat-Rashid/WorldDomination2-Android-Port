package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

/** Fixed-tick fade and nested playback for a persistent source action-building clip. */
public final class FlashActionBuildingAnimation {
    public static final int SOURCE_FRAME_RATE = 24;
    public static final int FADE_STEP_PERCENT = 5;
    public static final int FADE_TICKS = 100 / FADE_STEP_PERCENT;
    public static final int RESOURCE_PUMP_SYMBOL_ID = 5334;
    public static final int TECHNOLOGY_LIGHT_SYMBOL_ID = 3707;
    public static final int FACTORY_SMOKE_SYMBOL_ID = 3996;
    public static final int ACTION_SELECTION_SYMBOL_ID = 5277;
    public static final int ACTION_SELECTION_FRAME_COUNT = 30;

    public final int playerId;
    public final GameState.Action action;
    public final int slot;
    public final String frameName;
    public final String religionCode;
    public final int sequenceIndex;

    private int elapsedTicks;
    private long playbackTicks;
    private int factorySmokeFrame;
    private boolean factorySmokePlaying;
    private int selectionFrame;
    private boolean selectionPlaying;
    private int flameCharacterId;
    private int flameFrameOneBased;

    public FlashActionBuildingAnimation(GameState.ActionBuildingEvent event) {
        this(event, null);
    }

    /** A gotoAndStop to the same building frame preserves its nested movie clips. */
    public FlashActionBuildingAnimation(GameState.ActionBuildingEvent event,
                                        FlashActionBuildingAnimation previous) {
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
        if (previous != null && previous.playerId == playerId && previous.slot == slot
                && previous.frameName.equals(frameName)
                && previous.religionCode.equals(religionCode)) {
            playbackTicks = previous.playbackTicks;
            factorySmokeFrame = previous.factorySmokeFrame;
            factorySmokePlaying = previous.factorySmokePlaying;
            selectionFrame = previous.selectionFrame;
            selectionPlaying = previous.selectionPlaying;
        }
    }

    /** Advances one source tick and returns whether the fade has reached full opacity. */
    public boolean tick() {
        if (elapsedTicks < FADE_TICKS) {
            elapsedTicks++;
        }
        playbackTicks++;
        if (selectionPlaying) {
            selectionFrame = (selectionFrame + 1) % ACTION_SELECTION_FRAME_COUNT;
            selectionPlaying = selectionFrame != 0;
        }
        if (factorySmokePlaying) {
            factorySmokeFrame = (factorySmokeFrame + 1) % 50;
            // Original factory_smoke stops at frames 1 and 41.
            factorySmokePlaying = factorySmokeFrame != 0 && factorySmokeFrame != 40;
        }
        return isFadeComplete();
    }

    /** Source play_AB_selectMC starts factory smoke when Production is announced. */
    public void revealAction(GameState.Action revealedAction) {
        if (revealedAction == action) {
            selectionFrame = 0;
            selectionPlaying = true;
        }
        if (revealedAction == action && "factory".equals(frameName)) {
            factorySmokePlaying = true;
        }
    }

    /** Zero-based frames in the source pump, research light, and factory smoke clips. */
    public int getNestedFrameIndex(int symbolId, int frameCount) {
        if (frameCount <= 0) {
            throw new IllegalArgumentException("A nested clip must have frames");
        }
        if (symbolId == RESOURCE_PUMP_SYMBOL_ID || symbolId == TECHNOLOGY_LIGHT_SYMBOL_ID) {
            return (int) (playbackTicks % frameCount);
        }
        if (symbolId == ACTION_SELECTION_SYMBOL_ID) {
            return Math.min(selectionFrame, frameCount - 1);
        }
        if (symbolId == FACTORY_SMOKE_SYMBOL_ID) {
            return Math.min(factorySmokeFrame, frameCount - 1);
        }
        if (symbolId == flameCharacterId && flameFrameOneBased > 0) {
            return Math.min(flameFrameOneBased - 1, frameCount - 1);
        }
        return 0;
    }

    /** Updated by the shared source action-building timeline, never by rendering. */
    void setFlameFrame(int characterId, int oneBasedFrame) {
        flameCharacterId = characterId;
        flameFrameOneBased = oneBasedFrame;
    }

    void clearFlameFrame() {
        flameCharacterId = 0;
        flameFrameOneBased = 0;
    }

    /** Autoplaying pump and research lights continue after their parent fade finishes. */
    public boolean isAnimating() {
        return !isFadeComplete() || "resources".equals(frameName) || "tech".equals(frameName)
                || factorySmokePlaying || selectionPlaying || flameCharacterId != 0;
    }

    public boolean isFadeComplete() {
        return elapsedTicks >= FADE_TICKS;
    }

    public float getAlpha() {
        int fadeTicks = Math.min(FADE_TICKS, elapsedTicks);
        return Math.min(1f, fadeTicks * FADE_STEP_PERCENT / 100f);
    }
}
