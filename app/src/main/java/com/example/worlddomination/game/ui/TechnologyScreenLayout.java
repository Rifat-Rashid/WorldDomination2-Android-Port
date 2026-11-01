package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Original category ordering and responsive bounds for the technology screen. */
public final class TechnologyScreenLayout {
    public static final int CARD_COUNT = 7;
    public static final float ART_WIDTH = 365f;
    public static final float ART_HEIGHT = 275f;
    private static final int[] SOURCE_GROUP_ORDER = {1, 6, 7, 2, 3, 4, 5};
    private static final float SIDE_INSET = 8f;
    private static final float COLUMN_GAP = 4f;
    private static final float GRID_TOP_INSET = 42f;
    private static final float GRID_BOTTOM_INSET = 89f;
    private static final float ROW_GAP = 3f;
    private static final float SELECTED_BOTTOM = 33f;
    private static final float SELECTED_HEIGHT = 47f;

    private TechnologyScreenLayout() { }

    public static int sourceGroupAtCard(int cardIndex) {
        checkCard(cardIndex);
        return SOURCE_GROUP_ORDER[cardIndex];
    }

    public static int cardIndexForGroup(int group) {
        for (int index = 0; index < SOURCE_GROUP_ORDER.length; index++) {
            if (SOURCE_GROUP_ORDER[index] == group) {
                return index;
            }
        }
        return -1;
    }

    public static Rectangle setCardBounds(int cardIndex, Rectangle modal, Rectangle output) {
        checkCard(cardIndex);
        float scaleX = modal.width / ART_WIDTH;
        float scaleY = modal.height / ART_HEIGHT;
        int column = cardIndex % 3;
        int row = cardIndex / 3;
        float gapX = COLUMN_GAP * scaleX;
        float left = modal.x + SIDE_INSET * scaleX;
        float width = (modal.width - SIDE_INSET * 2f * scaleX - gapX * 2f) / 3f;
        float gridTop = modal.y + modal.height - GRID_TOP_INSET * scaleY;
        float gridBottom = modal.y + GRID_BOTTOM_INSET * scaleY;
        float gapY = ROW_GAP * scaleY;
        float height = (gridTop - gridBottom - gapY * 2f) / 3f;
        float x = left + column * (width + gapX);
        float y = gridTop - (row + 1) * height - row * gapY;
        return output.set(x, y, width, height);
    }

    public static Rectangle setSelectedBounds(Rectangle modal, Rectangle output) {
        float scaleX = modal.width / ART_WIDTH;
        float scaleY = modal.height / ART_HEIGHT;
        return output.set(modal.x + SIDE_INSET * scaleX,
                modal.y + SELECTED_BOTTOM * scaleY,
                modal.width - SIDE_INSET * 2f * scaleX,
                SELECTED_HEIGHT * scaleY);
    }

    private static void checkCard(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= CARD_COUNT) {
            throw new IndexOutOfBoundsException("Technology card: " + cardIndex);
        }
    }
}
