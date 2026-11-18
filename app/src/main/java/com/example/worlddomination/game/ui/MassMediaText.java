package com.example.worlddomination.game.ui;

/** Display rules of the original Mass Media presenter. */
public final class MassMediaText {
    private MassMediaText() { }

    public static String population(int populationMillions) {
        // This presenter uses one tenth of population; Resources uses the
        // separate model formatter and must continue displaying the full value.
        return Math.round(populationMillions * 0.1d) + ".000.000";
    }
}
