package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Registration of the original score backing, inset window and color indicators. */
public final class ScorePanelLayout {
    public static final float SOURCE_WIDTH = 180f;
    public static final float SOURCE_HEIGHT = 49f;
    private static final float SOURCE_EDGE = 16f;

    private ScorePanelLayout() { }

    public static float edge(Rectangle panel) {
        return Math.max(0f, Math.min(8f, Math.min(panel.width, panel.height) / 2f));
    }

    public static float mapX(Rectangle panel, float sourceX) {
        return panel.x + map(sourceX, SOURCE_WIDTH, panel.width, edge(panel));
    }

    /** Distance down from the top, as in the Flash coordinate system. */
    public static float mapDown(Rectangle panel, float sourceY) {
        return map(sourceY, SOURCE_HEIGHT, panel.height, edge(panel));
    }

    private static float map(float source, float length, float target, float edge) {
        if (source <= SOURCE_EDGE) return source * edge / SOURCE_EDGE;
        if (source >= length - SOURCE_EDGE)
            return target - (length - source) * edge / SOURCE_EDGE;
        return edge + (source - SOURCE_EDGE) * Math.max(0f, target - 2f * edge)
                / (length - 2f * SOURCE_EDGE);
    }

    public static void windowBounds(Rectangle panel, Rectangle result) {
        float left = mapX(panel, 24.55f), right = mapX(panel, 159.55f);
        float top = mapDown(panel, 20.85f), bottom = mapDown(panel, 48.85f);
        result.set(left, panel.y + panel.height - bottom, right - left, bottom - top);
    }

    public static void dotBounds(Rectangle panel, int index, Rectangle result) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("Score indicator index");
        float size = 18f * edge(panel) / SOURCE_EDGE;
        float x = mapX(panel, index == 0 ? 32.55f : 152.55f);
        float y = panel.y + panel.height - mapDown(panel, index == 0 ? 12.85f : 11.85f);
        result.set(x - size / 2f, y - size / 2f, size, size);
    }

    public static void titleBounds(Rectangle panel, Rectangle result) {
        float centerDown = mapDown(panel, 14f);
        float width = Math.max(0f, mapX(panel, 108f) - mapX(panel, 71f));
        float height = Math.min(width * 10f / 37f,
                2f * Math.min(centerDown, panel.height - centerDown));
        width = Math.max(0f, height) * 37f / 10f;
        result.set(mapX(panel, 89.5f) - width / 2f,
                panel.y + panel.height - centerDown - height / 2f, width, height);
    }

    public static void valueBounds(Rectangle panel, Rectangle result) {
        windowBounds(panel, result);
        float inset = Math.min(4f, Math.min(result.width, result.height) / 4f);
        result.set(result.x + inset, result.y + inset,
                Math.max(0f, result.width - 2f * inset), Math.max(0f, result.height - 2f * inset));
    }
}
