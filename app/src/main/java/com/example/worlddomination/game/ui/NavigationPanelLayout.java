package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Calculates the scrollable area inside a navigation frame without a title header. */
public final class NavigationPanelLayout {
    private static final float EDGE_INSET = 8f;

    private NavigationPanelLayout() {
    }

    public static void setContentBounds(Rectangle panel, Rectangle content) {
        content.set(panel.x + EDGE_INSET, panel.y + EDGE_INSET,
                panel.width - EDGE_INSET * 2f, panel.height - EDGE_INSET * 2f);
    }
}
