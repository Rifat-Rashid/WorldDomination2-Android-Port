package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FlashColorTransformTest {
    @Test
    public void mapsAllSetupColorsToTheOriginalFlashMultipliersAndOffsets() {
        assertTransform(1, .15f, 0f, .74f, 0f, 1f, 1f, 1f);
        assertTransform(2, .5f, 1f, 0f, 0f, 0f, 0f, .75f);
        assertTransform(3, 1f, 91f / 255f, .62f, 40f / 255f, 0f, 0f, 1f);
        assertTransform(4, .5f, 1f, .5f, 1f, 0f, 0f, .75f);
        assertTransform(5, 0f, 0f, .5f, 1f, 0f, 0f, .75f);
    }

    private static void assertTransform(int playerColor, float redMultiplier,
                                        float redAdd, float greenMultiplier,
                                        float greenAdd, float blueMultiplier,
                                        float blueAdd, float alphaMultiplier) {
        FlashColorTransform transform = FlashColorTransform.forPlayerColor(playerColor);
        assertEquals(redMultiplier, transform.redMultiplier, 0.0001f);
        assertEquals(redAdd, transform.redAdd, 0.0001f);
        assertEquals(greenMultiplier, transform.greenMultiplier, 0.0001f);
        assertEquals(greenAdd, transform.greenAdd, 0.0001f);
        assertEquals(blueMultiplier, transform.blueMultiplier, 0.0001f);
        assertEquals(blueAdd, transform.blueAdd, 0.0001f);
        assertEquals(alphaMultiplier, transform.alphaMultiplier, 0.0001f);
        assertEquals(0f, transform.alphaAdd, 0.0001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsColorsOutsideTheFivePlayerPalette() {
        FlashColorTransform.forPlayerColor(0);
    }
}
