package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SetupScreenLayoutTest {
    @Test
    public void removalArtworkKeepsSourceAspectRatioInsideEveryOccupiedCard() {
        for (float[] size : new float[][]{{800, 600}, {1194, 552}, {2424, 1080}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], 0, 0, 0, 0);
            for (int i = 0; i < layout.opponentSlots.length; i++) {
                Rectangle card = layout.opponentSlots[i], icon = layout.opponentRemoveIcons[i];
                assertTrue(card.contains(icon));
                assertTrue(icon.x > card.x + card.width * .6f);
                assertTrue(icon.y > card.y + card.height * .6f);
                assertEquals(17.3f / 17.1f, icon.width / icon.height, .0001f);
            }
        }
    }

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
    public void rivalSlotsLeaveRoomForDirectionMarkerAndPortraitNameBar() {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(1854f, 849f, 34f, 12f, 22f, 10f);

        assertTrue("Rival cards sit to the right of the panel center",
                layout.opponentSlots[0].x > layout.enemiesPanel.x
                        + layout.enemiesPanel.width * 0.25f);
        assertTrue("Direction marker has its own clear hit-safe region",
                layout.opponentArrow.x + layout.opponentArrow.width
                        < layout.opponentSlots[0].x);
        assertTrue("Opponent cards sit above the portrait carousel",
                layout.opponentSlots[0].y
                        > layout.leaderCarouselPanel.y + layout.leaderCarouselPanel.height);
        assertTrue("Carousel leaves the original enemy panel footer visible",
                layout.leaderCarouselPanel.y > layout.enemiesPanel.y
                        + layout.enemiesPanel.height * 0.08f);
        assertTrue("The name bar remains within the carousel frame",
                layout.leaderDescription.x >= layout.leaderCarouselPanel.x
                        && layout.leaderDescription.x + layout.leaderDescription.width
                        <= layout.leaderCarouselPanel.x + layout.leaderCarouselPanel.width);
        assertTrue("Carousel arrows are vertically inset from portrait cards",
                layout.leaderPrevious.height < layout.leaderCards[0].height
                        && layout.leaderNext.height < layout.leaderCards[0].height);
        assertTrue("The name bar stays below the portrait cards",
                layout.leaderDescription.y + layout.leaderDescription.height
                        < layout.leaderCards[0].y);
    }

    @Test
    public void carouselPreservesAuthoredFooterWithoutCoveringCardsOrDescription() {
        for (float[] size : new float[][]{{800, 600}, {896, 414}, {1194, 552}, {2424, 1080}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], 0, 0, 0, 0);
            Rectangle carousel = layout.leaderCarouselPanel;
            assertTrue(layout.enemiesPanel.contains(carousel));
            // Shape 5813 places its carousel about 28px above the 330px enemy
            // plate bottom. Keep that visible footer in the responsive layout.
            assertEquals(28f / 330f,
                    (carousel.y - layout.enemiesPanel.y) / layout.enemiesPanel.height, .001f);
            assertTrue(carousel.contains(layout.leaderDescription));
            assertTrue("Description must remain readable at the smallest phone size",
                    layout.leaderDescription.height >= 17f);
            assertTrue(layout.leaderDescription.y + layout.leaderDescription.height
                    < layout.leaderCards[0].y);
            for (Rectangle card : layout.leaderCards) assertTrue(carousel.contains(card));
            assertTrue(carousel.contains(layout.leaderPrevious));
            assertTrue(carousel.contains(layout.leaderNext));
        }
    }

    @Test
    public void carouselRetainsWideFootprintAndCardTargets() {
        for (float[] size : new float[][]{{800, 600}, {1194, 552}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], 0, 0, 0, 0);
            Rectangle crop = layout.leaderCarouselPanel;
            assertEquals(layout.enemiesPanel.width * .86f, crop.width, .001f);
            float oldWidth = (layout.enemiesPanel.width * .86f
                    - 2f * layout.enemiesPanel.width * .018f
                    - 2f * layout.enemiesPanel.width * .028f
                    - 7f * layout.enemiesPanel.width * .012f) / 6f;
            for (Rectangle card : layout.leaderCards) {
                assertEquals(oldWidth, card.width, .001f);
                assertEquals(layout.enemiesPanel.height * .326f, card.height, .001f);
                assertTrue(crop.contains(card));
            }
            assertFalse(layout.leaderPrevious.overlaps(layout.leaderCards[0]));
            assertFalse(layout.leaderNext.overlaps(layout.leaderCards[5]));
            assertTrue(crop.contains(layout.leaderDescription));
        }
    }

    @Test
    public void landscapeCutoutDoesNotShiftTheMainSetupPanelsAcrossTheScreen() {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(930f, 414f, 54f, 0f, 0f, 0f);

        assertTrue("Logo stays clear of the left cutout", layout.logo.x >= 54f);
        assertTrue("Player panel keeps the reference left margin",
                layout.playerStylePanel.x < 930f * 0.15f);
        assertTrue("Difficulty panel remains beside the logo",
                layout.difficultyPanel.x < 930f * 0.20f);
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
        assertInside(layout.opponentArrow, width, height,
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
