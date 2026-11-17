package com.example.worlddomination.game.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SourceStatisticBarTest {
    @Test public void sourcePercentageBoundariesUseLogicalColumns() {
        assertEquals(0, SourceStatisticBar.filledColumns(100d, 0));
        assertEquals(1, SourceStatisticBar.filledColumns(100d, 1));
        assertEquals(3, SourceStatisticBar.filledColumns(100d, 3));
        assertEquals(5, SourceStatisticBar.filledColumns(100d, 5));
        assertEquals(25, SourceStatisticBar.filledColumns(100d, 25));
        assertEquals(50, SourceStatisticBar.filledColumns(100d, 50));
        assertEquals(100, SourceStatisticBar.filledColumns(100d, 100));
        assertEquals(0, SourceStatisticBar.filledColumns(97d, 0));
        assertEquals(97, SourceStatisticBar.filledColumns(97d, 100));
    }

    @Test public void valuesClampBeforeSourceColumnRounding() {
        assertEquals(0, SourceStatisticBar.filledColumns(83d, -40));
        assertEquals(83, SourceStatisticBar.filledColumns(83d, 140));
        assertEquals(0, SourceStatisticBar.filledColumns(0d, 100));
        assertEquals(0, SourceStatisticBar.filledColumns(-12d, 100));
    }

    @Test public void halfColumnsRoundToEvenLikePython() {
        assertEquals(0, SourceStatisticBar.filledColumns(50d, 1));
        assertEquals(2, SourceStatisticBar.filledColumns(50d, 3));
        assertEquals(2, SourceStatisticBar.filledColumns(50d, 5));
        assertEquals(4, SourceStatisticBar.filledColumns(50d, 7));
        assertEquals(24, SourceStatisticBar.filledColumns(49d, 50));
        assertEquals(74, SourceStatisticBar.filledColumns(147d, 50));
        assertEquals(79, SourceStatisticBar.filledColumns(78.89d, 100));
    }

    @Test public void sourceColorHasFixedTrackAndColumnGradient() {
        assertEquals(5, SourceStatisticBar.TRACK_RED);
        assertEquals(13, SourceStatisticBar.TRACK_GREEN);
        assertEquals(17, SourceStatisticBar.TRACK_BLUE);
        assertEquals(255, SourceStatisticBar.FILL_RED);
        assertEquals(0, SourceStatisticBar.FILL_BLUE);
        assertEquals(45, SourceStatisticBar.greenAt(100d, 0));
        assertEquals(92, SourceStatisticBar.greenAt(100d, 25));
        assertEquals(140, SourceStatisticBar.greenAt(100d, 50));
        assertEquals(233, SourceStatisticBar.greenAt(100d, 99));
        // The last painted column at 100% is column 99, not endpoint 100.
        assertEquals(235, SourceStatisticBar.greenAt(100d, 100));
        assertEquals(49, SourceStatisticBar.greenAt(50d, 1));
        assertEquals(53, SourceStatisticBar.greenAt(50d, 2));
        assertEquals(56, SourceStatisticBar.greenAt(50d, 3));
        assertEquals(231, SourceStatisticBar.greenAt(50d, 49));
        assertEquals(47, SourceStatisticBar.greenAt(100d, 1));
        assertEquals(49, SourceStatisticBar.greenAt(100d, 2));
        assertEquals(51, SourceStatisticBar.greenAt(100d, 3));
    }

    @Test public void greenUsesDivideFirstAndDoesNotDependOnFillCount() {
        assertEquals(45, SourceStatisticBar.greenAt(0d, 0));
        assertEquals(235, SourceStatisticBar.greenAt(0d, 1));
        assertEquals(45, SourceStatisticBar.greenAt(-4d, 0));
        assertEquals(108, SourceStatisticBar.greenAt(3d, 1));
        assertEquals(12, SourceStatisticBar.filledColumns(50d, 25));
        assertEquals(50, SourceStatisticBar.filledColumns(50d, 100));
        // Column 7 is painted at both fills and keeps its width-based green.
        assertEquals(72, SourceStatisticBar.greenAt(50d, 7));
        assertEquals(91, SourceStatisticBar.greenAt(50d, 12));
    }
}
