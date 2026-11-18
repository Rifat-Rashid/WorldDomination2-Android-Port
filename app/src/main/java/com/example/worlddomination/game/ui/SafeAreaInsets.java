package com.example.worlddomination.game.ui;

/** Immutable edge exclusions; rectangle coordinates use a top-left origin. */
public final class SafeAreaInsets {
    public static final SafeAreaInsets NONE = new SafeAreaInsets(0, 0, 0, 0);

    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public SafeAreaInsets(float left, float top, float right, float bottom) {
        this.left = Math.max(0, left);
        this.top = Math.max(0, top);
        this.right = Math.max(0, right);
        this.bottom = Math.max(0, bottom);
    }

    /** Remove space already excluded by the child surface or viewport. */
    public SafeAreaInsets relativeTo(float parentWidth, float parentHeight,
                                    float x, float y, float width, float height) {
        if (width <= 0 || height <= 0) return NONE;
        return new SafeAreaInsets(
                Math.min(width, Math.max(0, left - x)),
                Math.min(height, Math.max(0, top - y)),
                Math.min(width, Math.max(0, right - (parentWidth - x - width))),
                Math.min(height, Math.max(0, bottom - (parentHeight - y - height))));
    }

    /** Convert viewport pixels to world units using the actual viewport scale. */
    public SafeAreaInsets toWorld(float worldWidth, float worldHeight,
                                 int pixelWidth, int pixelHeight) {
        if (pixelWidth <= 0 || pixelHeight <= 0) return NONE;
        return new SafeAreaInsets(left * worldWidth / pixelWidth,
                top * worldHeight / pixelHeight,
                right * worldWidth / pixelWidth,
                bottom * worldHeight / pixelHeight);
    }

    public boolean sameAs(SafeAreaInsets other) {
        return left == other.left && top == other.top
                && right == other.right && bottom == other.bottom;
    }
}
