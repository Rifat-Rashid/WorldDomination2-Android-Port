package com.example.worlddomination.game.ui;

/** Tracks the launch menu independently from its graphics and game state. */
public final class SplashScreenState {
    private boolean visible = true;

    public boolean isVisible() {
        return visible;
    }

    /** @return true only when this call dismissed the splash screen. */
    public boolean dismiss() {
        if (!visible) {
            return false;
        }
        visible = false;
        return true;
    }
}
