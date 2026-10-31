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
}
