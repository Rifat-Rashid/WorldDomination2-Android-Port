package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Five readable information rows use the full panel interior without a title plate. */
public final class InformationPanelLayout {
    public static final int ROW_COUNT = 5;
    private static final float TOP_PADDING = 4f;
    private static final float BOTTOM_PADDING = 8f;
    private static final float DESIRED_FONT_SCALE = 0.82f;
    public static final float BAR_HEIGHT = 6f;

    private InformationPanelLayout() { }

    public static float rowHeight(Rectangle panel) {
        return Math.max(0f, (panel.height - TOP_PADDING - BOTTOM_PADDING) / ROW_COUNT);
    }

    /** Baseline for Funds (row 0) through Spy (row 4), in libGDX world coordinates. */
    public static float rowBaseline(Rectangle panel, int row) {
        checkRow(row);
        return panel.y + panel.height - TOP_PADDING - (row + 1) * rowHeight(panel);
    }

    public static float barY(Rectangle panel, int statisticRow) {
        if (statisticRow < 1 || statisticRow >= ROW_COUNT) {
            throw new IndexOutOfBoundsException("Information statistic row: " + statisticRow);
        }
        float renderedTextHeight = rowHeight(panel);
        // BitmapFont baselines rise from the row's lower edge; put the bar a
        // half-row below the baseline so it aligns with the label's visible ink.
        return rowBaseline(panel, statisticRow)
                - Math.max(0f, (renderedTextHeight - BAR_HEIGHT) / 2f);
    }

    public static float fontScale(Rectangle panel, float fontLineHeight) {
        if (fontLineHeight <= 0f) {
            return 0f;
        }
        return Math.min(DESIRED_FONT_SCALE, rowHeight(panel) / fontLineHeight);
    }

    private static void checkRow(int row) {
        if (row < 0 || row >= ROW_COUNT) {
            throw new IndexOutOfBoundsException("Information panel row: " + row);
        }
    }
}
