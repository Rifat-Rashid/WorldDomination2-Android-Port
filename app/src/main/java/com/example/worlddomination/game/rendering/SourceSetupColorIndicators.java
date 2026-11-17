package com.example.worlddomination.game.rendering;

/** Shape mapping and registration for source setup color indicators. */
public final class SourceSetupColorIndicators {
    private static final int[][] SHAPES = {
            {5864, 5866}, {5859, 5861}, {5869, 5871},
            {5874, 5876}, {5854, 5856}
    };
    private SourceSetupColorIndicators() { }
    public static int shapeId(int colorIndex, boolean selected) {
        if (colorIndex < 0 || colorIndex >= SHAPES.length)
            throw new IllegalArgumentException("Invalid setup color index");
        return SHAPES[colorIndex][selected ? 1 : 0];
    }
    /** All frame states share the selected frame's source registration box. */
    public static float scale(float width, float height) {
        return Math.min(width / 54f, height / 55f);
    }
}
