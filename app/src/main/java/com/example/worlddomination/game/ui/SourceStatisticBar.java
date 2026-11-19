package com.example.worlddomination.game.ui;

/** Source phone statistic bar columns and colors, independent of the renderer. */
public final class SourceStatisticBar {
    public static final int TRACK_RED = 5;
    public static final int TRACK_GREEN = 13;
    public static final int TRACK_BLUE = 17;
    public static final int FILL_RED = 255;
    public static final int FILL_BLUE = 0;

    private SourceStatisticBar() { }

    /** Source rounds the filled logical-column count using Python's ties-to-even rule. */
    public static int filledColumns(double width, double value) {
        if (width <= 0d) return 0;
        double bounded = Math.max(0d, Math.min(100d, value));
        return (int) Math.rint(width * bounded / 100d);
    }

    /** Sub_Presenter_interface divides first and keeps the fractional mask scale. */
    public static double populationPercentage(long population, long activePopulation) {
        return activePopulation <= 0 ? 0d : (double) population / activePopulation * 100d;
    }

    /** Green varies by fixed column position, regardless of the current fill. */
    public static int greenAt(double width, int column) {
        return (int) Math.rint(45d + 190d * (column / Math.max(1d, width)));
    }
}
