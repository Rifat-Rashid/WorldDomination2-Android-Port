package com.example.worlddomination.game.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SplashScreenStateTest {
    @Test
    public void startsVisibleAndFirstTapDismissesIt() {
        SplashScreenState state = new SplashScreenState();

        assertTrue(state.isVisible());
        assertTrue(state.dismiss());
        assertFalse(state.isVisible());
    }

    @Test
    public void laterTapsDoNotRepeatDismissal() {
        SplashScreenState state = new SplashScreenState();
        state.dismiss();

        assertFalse(state.dismiss());
        assertFalse(state.isVisible());
    }
}
