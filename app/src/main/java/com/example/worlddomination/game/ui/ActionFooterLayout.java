package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Source-style Details, Cancel and Accept controls in the message footer. */
public final class ActionFooterLayout {
    public static final int BUTTON_COUNT = 3;
    private static final float HORIZONTAL_INSET = 5f;
    private static final float GAP = 4f;
    private static final float BOTTOM_INSET = 5f;
    private static final float BUTTON_HEIGHT = 20f;

    private ActionFooterLayout() { }

    public static Rectangle setButtonBounds(Rectangle footer, int buttonIndex,
                                            Rectangle output) {
        if (buttonIndex < 0 || buttonIndex >= BUTTON_COUNT) {
            throw new IndexOutOfBoundsException("Action footer button: " + buttonIndex);
        }
        float width = (footer.width - HORIZONTAL_INSET * 2f - GAP * 2f) / BUTTON_COUNT;
        float height = Math.min(BUTTON_HEIGHT,
                Math.max(0f, footer.height - BOTTOM_INSET * 2f));
        float x = footer.x + HORIZONTAL_INSET + buttonIndex * (width + GAP);
        return output.set(x, footer.y + BOTTOM_INSET, width, height);
    }
}
