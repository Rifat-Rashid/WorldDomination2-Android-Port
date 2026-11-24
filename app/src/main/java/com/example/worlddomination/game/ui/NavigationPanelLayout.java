package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Calculates the scrollable area inside a navigation frame without a title header. */
public final class NavigationPanelLayout {
    private static final float EDGE_INSET = 8f;
    private static final float LOGO_GAP = 14f;
    private static final float INFO_GAP = 4f;

    private NavigationPanelLayout() {
    }

    public static void setPanelBounds(Rectangle logo, Rectangle info, Rectangle panel) {
        float bottom = info.y + info.height + INFO_GAP;
        panel.set(logo.x, bottom, logo.width, logo.y - LOGO_GAP - bottom);
    }

    public static void setContentBounds(Rectangle panel, Rectangle content) {
        content.set(panel.x + EDGE_INSET, panel.y + EDGE_INSET,
                panel.width - EDGE_INSET * 2f, panel.height - EDGE_INSET * 2f);
    }
}
