package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Original category ordering and responsive bounds for the technology screen. */
public final class TechnologyScreenLayout {
    public static final int CARD_COUNT = 7;
    public static final float ART_WIDTH = 365f;
    public static final float ART_HEIGHT = 275f;
    // The seven original Flash tech clips appear in this order in centerScreen.
    private static final int[] SOURCE_GROUP_ORDER = {1, 6, 4, 5, 7, 2, 3};
    private static final float[] SOURCE_X = {14f, 15f, 14f, 14f, 145f, 245f, 245f};
    private static final float[] SOURCE_Y = {39f, 92f, 141f, 194f, 38f, 36f, 80f};
    private static final float[] SOURCE_WIDTH = {111f, 111f, 111f, 111f, 96f, 110f, 110f};
    private static final float[] SOURCE_HEIGHT = {50f, 46f, 50f, 50f, 89f, 42f, 47f};
    private static final float SELECTED_LEFT = 126f;
    private static final float SELECTED_BOTTOM = 130f;
    private static final float SELECTED_WIDTH = 230f;
    private static final float SELECTED_HEIGHT = 112f;
    private static final float STANDARD_COST_FIELD_X = 43.2f;
    private static final float STANDARD_COST_FIELD_Y = 2.2f;
    private static final float CARRIER_COST_FIELD_X = 6.6f;
    private static final float CARRIER_COST_FIELD_Y = 71.7f;
    private static final float FLASH_TEXT_BASELINE_OFFSET = 5f;

    public static final float SELECTED_NAMES_X = 136.4f;
    public static final float SELECTED_COSTS_X = 232.8f;
    public static final float SELECTED_NAMES_FIELD_Y = 141.5f;
    public static final float SELECTED_COSTS_FIELD_Y = 142f;
    public static final float ESTIMATED_TOTAL_X = 83.15f;
    public static final float ESTIMATED_TOTAL_FIELD_Y = 224.1f;

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

    /** Source position for the dynamic cost field inside a technology clip. */
    public static float cardCostX(int cardIndex) {
        checkCard(cardIndex);
        return SOURCE_X[cardIndex] + (SOURCE_GROUP_ORDER[cardIndex] == 7
                ? CARRIER_COST_FIELD_X : STANDARD_COST_FIELD_X);
    }

    /** Source baseline for the first line in a technology clip's cost field. */
    public static float cardCostBaselineTop(int cardIndex) {
        checkCard(cardIndex);
        float localY = SOURCE_GROUP_ORDER[cardIndex] == 7
                ? CARRIER_COST_FIELD_Y : STANDARD_COST_FIELD_Y;
        return SOURCE_Y[cardIndex] + localY + FLASH_TEXT_BASELINE_OFFSET;
    }

    public static float selectedNamesBaselineTop() {
        return SELECTED_NAMES_FIELD_Y + 10f;
    }

    public static float selectedCostsBaselineTop() {
        return SELECTED_COSTS_FIELD_Y + 10f;
    }

    public static float estimatedTotalBaselineTop() {
        return ESTIMATED_TOTAL_FIELD_Y + 10f;
    }

    public static Rectangle setCardBounds(int cardIndex, Rectangle modal, Rectangle output) {
        checkCard(cardIndex);
        float scaleX = modal.width / ART_WIDTH;
        float scaleY = modal.height / ART_HEIGHT;
        return output.set(modal.x + SOURCE_X[cardIndex] * scaleX,
                modal.y + modal.height - (SOURCE_Y[cardIndex] + SOURCE_HEIGHT[cardIndex])
                        * scaleY,
                SOURCE_WIDTH[cardIndex] * scaleX,
                SOURCE_HEIGHT[cardIndex] * scaleY);
    }

    public static Rectangle setSelectedBounds(Rectangle modal, Rectangle output) {
        float scaleX = modal.width / ART_WIDTH;
        float scaleY = modal.height / ART_HEIGHT;
        return output.set(modal.x + SELECTED_LEFT * scaleX,
                modal.y + (ART_HEIGHT - SELECTED_BOTTOM - SELECTED_HEIGHT) * scaleY,
                SELECTED_WIDTH * scaleX,
                SELECTED_HEIGHT * scaleY);
    }

    private static void checkCard(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= CARD_COUNT) {
            throw new IndexOutOfBoundsException("Technology card: " + cardIndex);
        }
    }
}
