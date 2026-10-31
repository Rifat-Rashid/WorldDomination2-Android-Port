package com.example.worlddomination.game.ui;

/** Independent vertical scroll and fling state for one side panel. */
public final class ScrollPaneState {
    private float offset;
    private float velocity;
    private float limit;

    public float getOffset() {
        return offset;
    }

    public float getLimit() {
        return limit;
    }

    /** Returns whether another fixed tick can still move the panel after a fling. */
    public boolean hasMotion() {
        return velocity != 0f;
    }

    public void setRange(float contentHeight, float viewportHeight) {
        limit = Math.max(0f, contentHeight - viewportHeight);
        offset = clamp(offset, 0f, limit);
        if (offset == 0f || offset == limit) {
            velocity = 0f;
        }
    }

    public void dragBy(float fingerDeltaY) {
        offset = clamp(offset - fingerDeltaY, 0f, limit);
        velocity = 0f;
    }

    public void endGesture(float fingerVelocityY) {
        velocity = -fingerVelocityY;
    }

    public void moveBy(float amount) {
        offset = clamp(offset + amount, 0f, limit);
        velocity = 0f;
    }

    public void tick(float seconds) {
        if (seconds <= 0f || velocity == 0f) {
            return;
        }
        offset = clamp(offset + velocity * seconds, 0f, limit);
        velocity *= (float) Math.exp(-10.0 * seconds);
        if (offset == 0f || offset == limit || Math.abs(velocity) < 4f) {
            velocity = 0f;
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
