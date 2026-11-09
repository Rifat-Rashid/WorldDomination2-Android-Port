package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class FlashSpriteQuadTest {
    @Test
    public void exportedDiplomacyMaskKeepsItsNegativeRegistrationCoordinates() {
        float[] vertices = new float[20];
        // Shape 5158's export translation is (142.25,49), not a sprite origin of (0,0).
        FlashSpriteQuad.positions(vertices, new FlashAffineTransform(),
                new Rectangle(0, 0, 800, 600), 400, 300, 0,
                -142.25f, -49, 116.95f, 82.75f);
        assertEquals(257.75f, vertices[5], 0.001f);
        assertEquals(349f, vertices[6], 0.001f);
        assertEquals(374.7f, vertices[10], 0.001f);
        assertEquals(266.25f, vertices[1], 0.001f);
    }

    @Test
    public void shearAndReflectionPreserveAllFourSourceCorners() {
        float[] vertices = new float[20];
        FlashAffineTransform transform = new FlashAffineTransform().setProduct(
                new FlashAffineTransform(), -2, 0.5f, 0.75f, 3, 12, -8);
        FlashSpriteQuad.positions(vertices, transform, new Rectangle(0, 0, 800, 600),
                400, 300, 0, -4, -6, 20, 10);
        assertEquals(423f, vertices[0], 0.001f); // BL: source (-4,4)
        assertEquals(298f, vertices[1], 0.001f);
        assertEquals(415.5f, vertices[5], 0.001f); // TL: source (-4,-6)
        assertEquals(328f, vertices[6], 0.001f);
        assertEquals(375.5f, vertices[10], 0.001f);
        assertEquals(318f, vertices[11], 0.001f);
        assertEquals(383f, vertices[15], 0.001f);
        assertEquals(288f, vertices[16], 0.001f);
    }

    @Test
    public void widescreenIslandShiftDoesNotStretchOrMoveTheLocalArtwork() {
        float[] vertices = new float[20];
        Rectangle bounds = new Rectangle(10, 20, 1000, 600);
        FlashSpriteQuad.positions(vertices, new FlashAffineTransform(), bounds,
                100, 100, 1, 0, 0, 80, 50);
        assertEquals(110f, vertices[5], 0f);
        assertEquals(520f, vertices[6], 0f);
        assertEquals(80f, vertices[10] - vertices[5], 0f);
        assertEquals(50f, vertices[6] - vertices[1], 0f);
    }
}
