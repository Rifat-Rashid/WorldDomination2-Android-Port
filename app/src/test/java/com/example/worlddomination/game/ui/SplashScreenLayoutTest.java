package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SplashScreenLayoutTest {
    @Test
    public void fitsAndCentersArtworkOnWideLandscapeViewport() {
        Rectangle bounds = SplashScreenLayout.fit(1200f, 500f, new Rectangle());

        assertEquals(666.6667f, bounds.width, 0.001f);
        assertEquals(500f, bounds.height, 0.001f);
        assertEquals((1200f - bounds.width) / 2f, bounds.x, 0.001f);
        assertEquals(0f, bounds.y, 0.001f);
        assertEquals(SplashScreenLayout.ART_WIDTH / SplashScreenLayout.ART_HEIGHT,
                bounds.width / bounds.height, 0.0001f);
    }

    @Test
    public void fitsArtworkInsidePortraitViewportWithoutDistortion() {
        Rectangle bounds = SplashScreenLayout.fit(600f, 900f, new Rectangle());

        assertEquals(600f, bounds.width, 0.001f);
        assertEquals(450f, bounds.height, 0.001f);
        assertEquals(0f, bounds.x, 0.001f);
        assertEquals(225f, bounds.y, 0.001f);
    }
}
