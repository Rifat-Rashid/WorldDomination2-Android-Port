package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FlashSpriteTransformTest {
    @Test
    public void decomposesSourceTankTurretReflectionAndRotation() {
        FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                -0.9945221f, 0.1045227f, 0.1045227f, 0.9945221f);

        assertEquals(-1f, transform.scaleX, 0.001f);
        assertEquals(1f, transform.scaleY, 0.001f);
        assertEquals(6f, transform.rotationDegrees, 0.1f);
    }

    @Test
    public void keepsUnrotatedUniformAndNonUniformScale() {
        FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(2f, 0f, 0f, 3f);

        assertEquals(2f, transform.scaleX, 0f);
        assertEquals(3f, transform.scaleY, 0f);
        assertEquals(0f, transform.rotationDegrees, 0f);
    }

    @Test
    public void rejectsShearedAndSingularFlashMatrices() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashSpriteTransform.fromMatrix(1f, 0f, 0.25f, 1f));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashSpriteTransform.fromMatrix(1f, 2f, 2f, 4f));
    }
}
