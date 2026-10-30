package com.example.worlddomination.game.rendering;

/** Chooses continuous rendering only while time-based presentation needs new frames. */
public final class RenderLoopPolicy {
    private RenderLoopPolicy() {
    }

    public static boolean shouldRenderContinuously(boolean matchActive,
                                                   boolean timedPromptActive,
                                                   boolean animatedPresentationActive) {
        return timedPromptActive || (matchActive && animatedPresentationActive);
    }
}
