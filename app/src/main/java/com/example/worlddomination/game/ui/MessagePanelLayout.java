package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Message clipping stays inside the recessed reference well, clear of its chrome. */
public final class MessagePanelLayout {
    private MessagePanelLayout() { }

    public static void updateViewport(Rectangle panel, Rectangle viewport) {
        viewport.set(panel.x + 11f, panel.y + 9f,
                Math.max(1f, panel.width - 22f), Math.max(1f, panel.height - 18f));
    }

    /** Use the same complete-row capacity for both drawing and one-based scrolling. */
    public static int visibleLineCount(float height, float lineHeight) {
        return Math.max(1, (int) Math.floor(height / Math.max(1f, lineHeight)));
    }
}
