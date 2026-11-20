package com.example.worlddomination.game.ui;

import java.util.Locale;

/** Money in the phone adapter's information panel, distinct from action-menu notation. */
public final class InformationPanelText {
    private InformationPanelText() { }

    public static String funds(int millions) {
        return String.format(Locale.US, "$ %,d", millions * 1_000_000L);
    }
}
