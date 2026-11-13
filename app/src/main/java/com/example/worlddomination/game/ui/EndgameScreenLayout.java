package com.example.worlddomination.game.ui;

/** Uniform source-stage fit and matching touch geometry for the authored endgame. */
public final class EndgameScreenLayout {
    public static final float STAGE_WIDTH = 800f;
    public static final float STAGE_HEIGHT = 600f;
    public static final float ROOT_OFFSET_X = 0.45f;
    // Raw symbol-5903 placement; ROOT_OFFSET_X is applied by x().
    private static final float NEW_GAME_X = 485.6f;
    private static final float NEW_GAME_Y = 451.3f;
    // Source control_bounds: (486.05,451.3)..(628.07687,484.53792) after root offset.
    private static final float NEW_GAME_WIDTH = 142.02687f;
    private static final float NEW_GAME_HEIGHT = 33.23792f;
    private float x, y, scale, width, height;

    /** Inputs use libGDX-style world coordinates with origin at bottom left. */
    public void fit(float contentX, float contentY, float contentWidth, float contentHeight) {
        scale = Math.min(contentWidth / STAGE_WIDTH, contentHeight / STAGE_HEIGHT);
        width = STAGE_WIDTH * scale;
        height = STAGE_HEIGHT * scale;
        x = contentX + (contentWidth - width) * 0.5f;
        y = contentY + (contentHeight - height) * 0.5f;
    }

    public float x(float sourceX) { return x + (sourceX + ROOT_OFFSET_X) * scale; }
    public float y(float sourceY) { return y + (STAGE_HEIGHT - sourceY) * scale; }
    public float scale() { return scale; }
    public float left() { return x; }
    public float bottom() { return y; }
    public float width() { return width; }
    public float height() { return height; }

    public boolean hitNewGame(float worldX, float worldY) {
        // The authored Website button starts 0.023 source pixels to the right.
        // Grow narrow targets leftward, never into that adjacent control.
        float horizontalPad = Math.max(0f, 44f - NEW_GAME_WIDTH * scale);
        float verticalPad = Math.max(0f, (44f - NEW_GAME_HEIGHT * scale) * 0.5f);
        float left = x(NEW_GAME_X) - horizontalPad;
        float right = x(NEW_GAME_X + NEW_GAME_WIDTH);
        float bottom = y(NEW_GAME_Y + NEW_GAME_HEIGHT) - verticalPad;
        float top = y(NEW_GAME_Y) + verticalPad;
        return worldX >= left && worldX <= right && worldY >= bottom && worldY <= top;
    }
}
