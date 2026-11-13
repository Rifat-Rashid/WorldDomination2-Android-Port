package com.example.worlddomination.game.ui;

/** Source phone panel sampling in logical points per second. */
public final class PanelGestureVelocity {
    private PanelGestureVelocity() { }

    public static float sample(float fingerDeltaY, long previousNanos, long currentNanos) {
        // Subtract integer timestamps before conversion so uptime cannot erase a frame interval.
        double seconds = Math.max(8_000_000L, currentNanos - previousNanos) / 1_000_000_000d;
        double velocity = fingerDeltaY / seconds;
        return (float) Math.max(-1800d, Math.min(1800d, velocity));
    }
}
