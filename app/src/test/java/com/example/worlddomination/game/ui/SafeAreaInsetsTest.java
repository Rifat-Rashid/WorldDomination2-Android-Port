package com.example.worlddomination.game.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SafeAreaInsetsTest {
    @Test public void pixelsUseViewportScaleInsteadOfAndroidDensity() {
        SafeAreaInsets world = new SafeAreaInsets(120, 24, 40, 16)
                .toWorld(1000, 500, 2000, 1000);
        assertInsets(world, 60, 12, 20, 8);
        // A density of 3 would incorrectly yield 40 world units at the left.
        assertNotEquals(120f / 3f, world.left, 0.001f);
    }

    @Test public void aSurfaceAlreadyInsideTheSafeWindowNeedsNoExtraInset() {
        SafeAreaInsets surface = new SafeAreaInsets(100, 40, 60, 20)
                .relativeTo(2000, 1000, 100, 40, 1840, 940);
        assertInsets(surface, 0, 0, 0, 0);
    }

    @Test public void partiallyInsetSurfaceExcludesOnlyRemainingCutoutPixels() {
        SafeAreaInsets surface = new SafeAreaInsets(100, 40, 60, 20)
                .relativeTo(2000, 1000, 25, 10, 1965, 985);
        assertInsets(surface, 75, 30, 50, 15);
    }

    @Test public void letterboxCanCompletelyAbsorbTheCutout() {
        SafeAreaInsets world = new SafeAreaInsets(80, 10, 70, 5)
                .relativeTo(2200, 1100, 100, 50, 2000, 1000)
                .toWorld(1000, 500, 2000, 1000);
        assertInsets(world, 0, 0, 0, 0);
    }

    @Test public void partialLetterboxOverlapUsesEachEdgeAndAxisScale() {
        SafeAreaInsets world = new SafeAreaInsets(140, 80, 120, 70)
                .relativeTo(2200, 1100, 100, 50, 2000, 1000)
                .toWorld(1000, 400, 2000, 1000);
        assertInsets(world, 20, 12, 10, 8);
    }

    @Test public void unchangedPixelInsetsAreReprojectedAfterResize() {
        SafeAreaInsets pixels = new SafeAreaInsets(120, 0, 0, 0);
        assertEquals(60, pixels.toWorld(1000, 500, 2000, 1000).left, 0.001f);
        assertEquals(40, pixels.toWorld(1000, 500, 3000, 1500).left, 0.001f);
        assertEquals(120, pixels.left, 0f);
    }

    @Test public void emptyOrFullyExcludedSurfacesStayBounded() {
        SafeAreaInsets pixels = new SafeAreaInsets(100, 50, -2, -3);
        assertInsets(pixels.relativeTo(1000, 500, 0, 0, 25, 10), 25, 10, 0, 0);
        assertSame(SafeAreaInsets.NONE, pixels.relativeTo(1000, 500, 0, 0, 0, 10));
        assertSame(SafeAreaInsets.NONE, pixels.toWorld(1000, 500, 2000, 0));
        assertTrue(SafeAreaInsets.NONE.sameAs(new SafeAreaInsets(0, 0, 0, 0)));
    }

    private static void assertInsets(SafeAreaInsets insets, float left, float top,
                                     float right, float bottom) {
        assertEquals(left, insets.left, 0.001f);
        assertEquals(top, insets.top, 0.001f);
        assertEquals(right, insets.right, 0.001f);
        assertEquals(bottom, insets.bottom, 0.001f);
    }
}
