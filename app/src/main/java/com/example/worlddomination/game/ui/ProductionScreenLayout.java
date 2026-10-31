package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Source-sized layout and slider mapping for the original Flash production menu. */
public final class ProductionScreenLayout {
    public static final float ART_WIDTH = 365f;
    public static final float ART_HEIGHT = 275f;
    public static final float MAP_BUTTON_WIDTH = 50f;
    public static final float MAP_BUTTON_HEIGHT = 22f;

    private static final String[] CATEGORY_KEYS = {
            "rockets", "planes", "special", "warhead", "bioload", "soldier", "spy", "defense"
    };
    private static final float[] TRACK_CENTER_X = {
            13.5f, 13.5f, 131f, 247.5f, 247.5f, 13.5f, 13.5f, 13.5f
    };
    private static final float[] TRACK_CENTER_Y_FROM_TOP = {
            61f, 104f, 61f, 61f, 104f, 147f, 190f, 234f
    };
    private static final float TRACK_LENGTH = 96.5f;
    private static final float HIT_PADDING_X = 7f;
    private static final float HIT_HALF_HEIGHT = 9f;
    private static final float HANDLE_WIDTH = 10f;
    private static final float HANDLE_HEIGHT = 9f;
    private static final float[] SPECIAL_STAT_TOPS = {75f, 92.5f, 110f, 127f};
    private static final float SPECIAL_STAT_LEFT = 129f;
    private static final float SPECIAL_STAT_WIDTH = 97f;
    private static final float SPECIAL_STAT_HEIGHT = 6.5f;

    private ProductionScreenLayout() { }

    public static int sliderCount() {
        return CATEGORY_KEYS.length;
    }

    public static String categoryKey(int sliderIndex) {
        checkSliderIndex(sliderIndex);
        return CATEGORY_KEYS[sliderIndex];
    }

    /** Places the source MAP control at the upper-right edge of the battlefield frame. */
    public static Rectangle setMapButtonBounds(Rectangle mapFrame, Rectangle output) {
        return output.set(mapFrame.x + mapFrame.width - MAP_BUTTON_WIDTH - 3f,
                mapFrame.y + mapFrame.height - MAP_BUTTON_HEIGHT - 2f,
                MAP_BUTTON_WIDTH, MAP_BUTTON_HEIGHT);
    }

    /** Sets a touch target around one source slider track, in game world coordinates. */
    public static Rectangle setSliderHitBounds(int sliderIndex, Rectangle modal,
                                               Rectangle output) {
        checkSliderIndex(sliderIndex);
        float scaleX = modal.width / ART_WIDTH;
        float scaleY = modal.height / ART_HEIGHT;
        float left = modal.x + (TRACK_CENTER_X[sliderIndex] - HIT_PADDING_X) * scaleX;
        float centerY = modal.y + modal.height
                - TRACK_CENTER_Y_FROM_TOP[sliderIndex] * scaleY;
        float width = (TRACK_LENGTH + HIT_PADDING_X * 2f) * scaleX;
        float height = HIT_HALF_HEIGHT * 2f * scaleY;
        return output.set(left, centerY - height / 2f, width, height);
    }

    /** Sets the original slider handle bounds at a percent position. */
    public static Rectangle setHandleBounds(int sliderIndex, int percent, Rectangle modal,
                                            Rectangle output) {
        checkSliderIndex(sliderIndex);
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Production slider percent must be 0..100");
        }
        float scaleX = modal.width / ART_WIDTH;
        float scaleY = modal.height / ART_HEIGHT;
        float centerX = modal.x + (TRACK_CENTER_X[sliderIndex]
                + TRACK_LENGTH * percent / 100f) * scaleX;
        float centerY = modal.y + modal.height
                - TRACK_CENTER_Y_FROM_TOP[sliderIndex] * scaleY;
        float width = HANDLE_WIDTH * scaleX;
        float height = HANDLE_HEIGHT * scaleY;
        return output.set(centerX - width / 2f, centerY - height / 2f, width, height);
    }

    /** Converts a touch on one slider to the source's asymmetric 10-percent steps. */
    public static int percentAt(int sliderIndex, Rectangle modal, float worldX,
                                int remainingPercent) {
        checkSliderIndex(sliderIndex);
        float scaleX = modal.width / ART_WIDTH;
        float start = modal.x + TRACK_CENTER_X[sliderIndex] * scaleX;
        float length = TRACK_LENGTH * scaleX;
        float position = Math.max(0f, Math.min(1f, (worldX - start) / length));
        int percent = sourceStepPercent(position * 100f);
        return Math.min(percent, Math.max(0, Math.min(100, remainingPercent)));
    }

    /** Source slider rounds down through the midpoint and rounds to nearest above it. */
    public static int sourceStepPercent(float rawPercent) {
        float clamped = Math.max(0f, Math.min(100f, rawPercent));
        int step = clamped > 50f
                ? Math.round(clamped / 10f)
                : (int) Math.floor(clamped / 10f);
        return Math.max(0, Math.min(100, step * 10));
    }

    /** Sets the inner fill rectangle for one original special-carrier stat bar. */
    public static Rectangle setSpecialStatFillBounds(int statIndex, float fraction,
                                                     Rectangle modal, Rectangle output) {
        if (statIndex < 0 || statIndex >= SPECIAL_STAT_TOPS.length) {
            throw new IndexOutOfBoundsException("Special-carrier stat index: " + statIndex);
        }
        float scaleX = modal.width / ART_WIDTH;
        float scaleY = modal.height / ART_HEIGHT;
        float progress = Math.max(0f, Math.min(1f, fraction));
        float width = Math.max(0f, SPECIAL_STAT_WIDTH - 4f) * progress * scaleX;
        float height = (SPECIAL_STAT_HEIGHT - 3f) * scaleY;
        float x = modal.x + (SPECIAL_STAT_LEFT + 2f) * scaleX;
        float y = modal.y + modal.height
                - (SPECIAL_STAT_TOPS[statIndex] + SPECIAL_STAT_HEIGHT - 2f) * scaleY;
        return output.set(x, y, width, height);
    }

    private static void checkSliderIndex(int sliderIndex) {
        if (sliderIndex < 0 || sliderIndex >= CATEGORY_KEYS.length) {
            throw new IndexOutOfBoundsException("Production slider index: " + sliderIndex);
        }
    }
}
