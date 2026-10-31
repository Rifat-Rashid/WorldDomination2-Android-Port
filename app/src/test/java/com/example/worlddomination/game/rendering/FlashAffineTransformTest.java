package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class FlashAffineTransformTest {
    @Test
    public void nestedScaleAndTranslationAreComposedInFlashMatrixOrder() {
        FlashAffineTransform parent = new FlashAffineTransform().setIdentity();
        FlashAffineTransform shadow = new FlashAffineTransform().setProduct(parent,
                0.7f, 0f, 0f, 0.7f, -1f, 0.6f);
        FlashAffineTransform artwork = new FlashAffineTransform().setProduct(shadow,
                1f, 0f, 0f, 1f, 10f, 20f);

        assertEquals(0.7f, artwork.scaleX, 0.0001f);
        assertEquals(0.7f, artwork.scaleY, 0.0001f);
        assertEquals(6f, artwork.tx, 0.0001f);
        assertEquals(14.6f, artwork.ty, 0.0001f);
    }

    @Test
    public void composedRotationAndReflectionKeepTheirSpriteBatchOrientation() {
        FlashAffineTransform parent = new FlashAffineTransform().setProduct(
                new FlashAffineTransform(), 0f, -1f, 1f, 0f, 3f, 4f);
        FlashAffineTransform reflected = new FlashAffineTransform().setProduct(parent,
                -1f, 0f, 0f, 1f, 2f, 5f);

        assertTrue(reflected.scaleX < 0f);
        assertEquals(90f, reflected.rotationDegrees, 0.0001f);
        assertEquals(8f, reflected.tx, 0.0001f);
        assertEquals(2f, reflected.ty, 0.0001f);
    }
}
