package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SetupScreenLayoutTest {
    @Test
    public void setupSectionsAndControlsFitCommonLandscapeSizesAndSafeInsets() {
        assertLayout(896f, 414f, 0f, 0f, 0f, 0f);
        assertLayout(1854f, 849f, 34f, 12f, 22f, 10f);
    }

    @Test
    public void setupSectionsKeepTheirOriginalVerticalHierarchy() {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(896f, 414f, 0f, 0f, 0f, 0f);

        assertTrue(layout.startPanel.y + layout.startPanel.height
                < layout.enemiesPanel.y);
        assertTrue(layout.enemiesPanel.y + layout.enemiesPanel.height
                < layout.playerStylePanel.y);
        assertTrue(layout.playerStylePanel.y + layout.playerStylePanel.height
                < layout.difficultyPanel.y);
        assertTrue(layout.difficultyPanel.x > layout.logo.x + layout.logo.width);
    }

    @Test
    public void leaderCarouselWrapsAtTheLastSourcePortrait() {
        assertEquals(17, SetupScreenLayout.leaderIdAt(17, 0, 19));
        assertEquals(18, SetupScreenLayout.leaderIdAt(17, 1, 19));
        assertEquals(0, SetupScreenLayout.leaderIdAt(17, 2, 19));
        assertEquals(3, SetupScreenLayout.leaderIdAt(17, 5, 19));
    }

    @Test
    public void leaderCarouselRejectsInvalidVisibleIndicesAndEmptyRosters() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> SetupScreenLayout.leaderIdAt(0, -1, 19));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> SetupScreenLayout.leaderIdAt(0, SetupScreenLayout.VISIBLE_LEADERS, 19));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> SetupScreenLayout.leaderIdAt(0, 0, 0));
    }

    private static void assertLayout(float width, float height,
                                     float safeLeft, float safeTop,
                                     float safeRight, float safeBottom) {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(width, height, safeLeft, safeTop, safeRight, safeBottom);
        Rectangle[] sections = {
                layout.logo, layout.difficultyPanel, layout.playerStylePanel,
                layout.enemiesPanel, layout.leaderCarouselPanel, layout.startPanel
        };
        for (Rectangle section : sections) {
            assertInside(section, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.difficultyButtons) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.colorSwatches) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.buildingButtons) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.opponentSlots) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.leaderCards) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        assertInside(layout.leaderPrevious, width, height,
                safeLeft, safeTop, safeRight, safeBottom);
        assertInside(layout.leaderNext, width, height,
                safeLeft, safeTop, safeRight, safeBottom);
        assertInside(layout.leaderDescription, width, height,
                safeLeft, safeTop, safeRight, safeBottom);
        assertInside(layout.startButton, width, height,
                safeLeft, safeTop, safeRight, safeBottom);

        for (int i = 0; i < layout.leaderCards.length - 1; i++) {
            assertTrue("Leader cards must not overlap", !layout.leaderCards[i]
                    .overlaps(layout.leaderCards[i + 1]));
        }
    }

    private static void assertInside(Rectangle rectangle, float width, float height,
                                     float safeLeft, float safeTop,
                                     float safeRight, float safeBottom) {
        assertTrue("Control width must be positive", rectangle.width > 0f);
        assertTrue("Control height must be positive", rectangle.height > 0f);
        assertTrue("Control crosses left safe area", rectangle.x >= safeLeft);
        assertTrue("Control crosses bottom safe area", rectangle.y >= safeBottom);
        assertTrue("Control crosses right safe area",
                rectangle.x + rectangle.width <= width - safeRight);
        assertTrue("Control crosses top safe area",
                rectangle.y + rectangle.height <= height - safeTop);
    }
}
