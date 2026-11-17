package com.example.worlddomination.game.rendering;

/** Source 5882 building indicator frame shapes and common registration box. */
public final class SourceSetupBuildingIndicators {
    private static final int[][] SHAPES = {
            {5844, 5845}, {5836, 5837}, {5840, 5841},
            {5831, 5833}, {5848, 5849}
    };
    private SourceSetupBuildingIndicators() { }
    public static int shapeId(int index, boolean selected) {
        if (index < 0 || index >= SHAPES.length)
            throw new IllegalArgumentException("Invalid building index");
        return SHAPES[index][selected ? 1 : 0];
    }
    /** Fifth parent sits half a source pixel higher than the other four. */
    public static float parentOffsetY(int index) {
        if (index < 0 || index >= SHAPES.length)
            throw new IllegalArgumentException("Invalid building index");
        return index == 4 ? -0.5f : 0f;
    }
    public static float scale(float width, float height) {
        return Math.min(width / 53f, height / 55.5f);
    }
}
