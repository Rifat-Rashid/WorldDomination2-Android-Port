package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RenderLoopPolicyTest {
    @Test
    public void idleSetupAndMatchScreensDoNotRequestContinuousFrames() {
        assertFalse(RenderLoopPolicy.shouldRenderContinuously(false, false, false));
        assertFalse(RenderLoopPolicy.shouldRenderContinuously(true, false, false));
    }

    @Test
    public void animatedMatchKeepsContinuousFramesEnabled() {
        assertTrue(RenderLoopPolicy.shouldRenderContinuously(true, false, true));
    }

    @Test
    public void timedExitPromptKeepsFramesEnabledOutsideAMatch() {
        assertTrue(RenderLoopPolicy.shouldRenderContinuously(false, true, false));
    }

    @Test
    public void presentationWithoutAnActiveMatchDoesNotKeepTheLoopRunning() {
        assertFalse(RenderLoopPolicy.shouldRenderContinuously(false, false, true));
    }
}
