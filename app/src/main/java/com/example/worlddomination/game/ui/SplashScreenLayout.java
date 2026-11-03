package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Uniformly fits the original 4:3 Flash title art into the landscape viewport. */
public final class SplashScreenLayout {
    public static final float ART_WIDTH = 1024f;
    public static final float ART_HEIGHT = 768f;

    private SplashScreenLayout() {
    }

    public static Rectangle fit(float width, float height, Rectangle out) {
        float scale = Math.min(Math.max(0f, width) / ART_WIDTH,
                Math.max(0f, height) / ART_HEIGHT);
        float fittedWidth = ART_WIDTH * scale;
        float fittedHeight = ART_HEIGHT * scale;
        out.set((width - fittedWidth) * 0.5f, (height - fittedHeight) * 0.5f,
                fittedWidth, fittedHeight);
        return out;
    }
}
