package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProductionScreenLayoutTest {
    @Test
    public void productionRowsFollowTheOriginalFlashOrderAndColumns() {
        String[] expected = {
                "rockets", "planes", "special", "warhead", "bioload", "soldier", "spy", "defense"
        };
        assertEquals(expected.length, ProductionScreenLayout.sliderCount());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], ProductionScreenLayout.categoryKey(index));
        }

        Rectangle modal = new Rectangle(100f, 40f,
                ProductionScreenLayout.ART_WIDTH, ProductionScreenLayout.ART_HEIGHT);
        Rectangle[] tracks = new Rectangle[expected.length];
        for (int index = 0; index < tracks.length; index++) {
            tracks[index] = ProductionScreenLayout.setSliderHitBounds(
                    index, modal, new Rectangle());
            assertTrue("Slider hit area must stay inside the production artwork",
                    modal.contains(tracks[index]));
        }
        assertFalse("Warhead and special-carrier tracks are separate columns",
                tracks[2].overlaps(tracks[3]));
        assertFalse("Bioload and aircraft tracks are separate columns",
                tracks[1].overlaps(tracks[4]));
    }

    @Test
    public void productionArtworkFitsDesktopAndMobileFramesWithoutDistortion() {
        Rectangle[] mapFrames = {
                new Rectangle(0f, 0f, 380f, 360f),
                new Rectangle(120f, 30f, 620f, 340f),
                new Rectangle(40f, 20f, 1040f, 410f)
        };

        for (Rectangle mapFrame : mapFrames) {
            Rectangle modal = ProductionScreenLayout.setModalBounds(
                    mapFrame, new Rectangle());

            assertTrue("The production artwork must fit inside the battlefield",
                    mapFrame.contains(modal));
            assertEquals("The source Flash artwork aspect ratio must be preserved",
                    ProductionScreenLayout.ART_WIDTH / ProductionScreenLayout.ART_HEIGHT,
                    modal.width / modal.height, 0.001f);
            assertEquals("The production modal remains centered",
                    mapFrame.x + mapFrame.width / 2f,
                    modal.x + modal.width / 2f, 0.001f);
            assertEquals("The production modal remains centered vertically",
                    mapFrame.y + mapFrame.height / 2f,
                    modal.y + modal.height / 2f, 0.001f);
        }
    }

    @Test
    public void specialCarrierTrackKeepsItsOriginalBoundsWhileTechnologyIsLocked() {
        Rectangle modal = new Rectangle(0f, 0f,
                ProductionScreenLayout.ART_WIDTH, ProductionScreenLayout.ART_HEIGHT);
        Rectangle track = ProductionScreenLayout.setSliderHitBounds(
                2, modal, new Rectangle());
        Rectangle zeroHandle = ProductionScreenLayout.setHandleBounds(
                2, 0, modal, new Rectangle());
        Rectangle fullHandle = ProductionScreenLayout.setHandleBounds(
                2, 100, modal, new Rectangle());

        assertTrue(track.contains(zeroHandle.x + zeroHandle.width / 2f,
                zeroHandle.y + zeroHandle.height / 2f));
        assertTrue(track.contains(fullHandle.x + fullHandle.width / 2f,
                fullHandle.y + fullHandle.height / 2f));
        assertEquals(96.5f, fullHandle.x + fullHandle.width / 2f
                - (zeroHandle.x + zeroHandle.width / 2f), 0.001f);
    }

    @Test
    public void sourceSliderUsesTenPercentStepsAndSourceMidpointRounding() {
        assertEquals(0, ProductionScreenLayout.sourceStepPercent(-5f));
        assertEquals(20, ProductionScreenLayout.sourceStepPercent(29f));
        assertEquals(50, ProductionScreenLayout.sourceStepPercent(50f));
        assertEquals(50, ProductionScreenLayout.sourceStepPercent(54f));
        assertEquals(60, ProductionScreenLayout.sourceStepPercent(55f));
        assertEquals(80, ProductionScreenLayout.sourceStepPercent(75f));
        assertEquals(100, ProductionScreenLayout.sourceStepPercent(100f));
    }

    @Test
    public void screenTouchClampsToTheRemainingBudgetAndKeepsEndpointsExact() {
        Rectangle modal = new Rectangle(12f, 18f, 408f, 305f);
        Rectangle endHandle = ProductionScreenLayout.setHandleBounds(
                0, 100, modal, new Rectangle());
        float endX = endHandle.x + endHandle.width / 2f;

        assertEquals(100, ProductionScreenLayout.percentAt(0, modal, endX, 100));
        assertEquals(50, ProductionScreenLayout.percentAt(0, modal,
                modal.x + (13.5f + 96.5f * 0.54f)
                        * (modal.width / ProductionScreenLayout.ART_WIDTH), 55));
        assertEquals(55, ProductionScreenLayout.percentAt(0, modal,
                modal.x + (13.5f + 96.5f * 0.92f)
                        * (modal.width / ProductionScreenLayout.ART_WIDTH), 55));
    }

    @Test
    public void draggingFromTheHandlePreservesTheSourceGrabOffset() {
        Rectangle modal = new Rectangle(20f, 30f,
                ProductionScreenLayout.ART_WIDTH, ProductionScreenLayout.ART_HEIGHT);
        Rectangle handle = ProductionScreenLayout.setHandleBounds(
                0, 30, modal, new Rectangle());
        float handleCenterX = handle.x + handle.width / 2f;
        float pointerDownX = handleCenterX + 4f;

        int unchanged = ProductionScreenLayout.percentAt(
                0, modal, pointerDownX, pointerDownX - handleCenterX, 100);
        int afterDrag = ProductionScreenLayout.percentAt(
                0, modal, pointerDownX + 6f, pointerDownX - handleCenterX, 100);

        assertEquals("A stationary handle press must keep its current setting", 30, unchanged);
        assertEquals("A short drag must keep the finger at its original point on the handle",
                30, afterDrag);
        assertEquals("Treating the drag as a track tap would incorrectly snap to 40%", 40,
                ProductionScreenLayout.percentAt(0, modal, pointerDownX + 6f, 100));
    }

    @Test
    public void specialCarrierProgressBarsRespectTheirBounds() {
        Rectangle modal = new Rectangle(0f, 0f,
                ProductionScreenLayout.ART_WIDTH, ProductionScreenLayout.ART_HEIGHT);
        Rectangle empty = ProductionScreenLayout.setSpecialStatFillBounds(
                0, 0f, modal, new Rectangle());
        Rectangle full = ProductionScreenLayout.setSpecialStatFillBounds(
                0, 1.5f, modal, new Rectangle());

        assertEquals(0f, empty.width, 0.001f);
        assertEquals(full.x, empty.x, 0.001f);
        assertTrue(full.width < 97f);
        assertTrue(full.height > 0f);
    }
}
