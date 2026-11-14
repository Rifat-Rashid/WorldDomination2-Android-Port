package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class FlashBattleUnitRendererTest {
    @Test
    public void satelliteLaserColorsMatchTheSourceColorAndBrightnessSteps() {
        assertEquals(0x222FF2,
                FlashBattleUnitRenderer.sourceSatelliteLaserColor(1, 0));
        assertEquals(0x444FF4,
                FlashBattleUnitRenderer.sourceSatelliteLaserColor(1, 2));
        assertEquals(0x777FF7,
                FlashBattleUnitRenderer.sourceSatelliteLaserColor(1, 3));
        assertEquals(0xF77777,
                FlashBattleUnitRenderer.sourceSatelliteLaserColor(2, 4));
        assertEquals(0xFFF777,
                FlashBattleUnitRenderer.sourceSatelliteLaserColor(3, 4));
        assertEquals(0x7FF777,
                FlashBattleUnitRenderer.sourceSatelliteLaserColor(5, 3));
        assertEquals(0xFFFFFF,
                FlashBattleUnitRenderer.sourceSatelliteLaserColor(1, 6));
    }

    @Test
    public void satelliteLaserPaletteRejectsInvalidSourceIndices() {
        assertThrows(IllegalArgumentException.class,
                () -> FlashBattleUnitRenderer.sourceSatelliteLaserColor(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> FlashBattleUnitRenderer.sourceSatelliteLaserColor(5, 8));
    }

    @Test public void suicidePaletteAlphaIsNotMultipliedTwiceAtConstruction() {
        FlashColorTransform.Composition color = new FlashColorTransform.Composition();
        FlashBattleUnitRenderer.setSuicideColor(color, "suicide_bomber_tint", "green", .75f);
        assertEquals(.75f, color.alphaMultiplier, 0f);
        assertEquals(0f, color.redMultiplier, 0f);
        assertEquals(.5f, color.greenMultiplier, 0f);
        assertEquals(1f, color.greenAdd, 0f);
        assertEquals(0f, color.blueMultiplier, 0f);
    }

    @Test public void sourceFadeReplacesTintAlphaAndRetainsPlayerRgbTransform() {
        FlashColorTransform.Composition color = new FlashColorTransform.Composition();
        FlashBattleUnitRenderer.setSuicideColor(color, "suicide_bomber_tint", "red", .98f);
        assertEquals(.98f, color.alphaMultiplier, 0f);
        assertEquals(.5f, color.redMultiplier, 0f);
        assertEquals(1f, color.redAdd, 0f);
        assertEquals(0f, color.greenMultiplier, 0f);
    }

    @Test public void bodyAndExplosionDoNotInheritTheTintPeerColor() {
        FlashColorTransform.Composition color = new FlashColorTransform.Composition();
        FlashBattleUnitRenderer.setSuicideColor(color, "suicide_bomber_tint", "red", .98f);
        for (String linkage : new String[]{"suicide_bomber", "explosion001"}) {
            FlashBattleUnitRenderer.setSuicideColor(color, linkage, "red", 1f);
            org.junit.Assert.assertTrue(color.isIdentity());
        }
    }
}
