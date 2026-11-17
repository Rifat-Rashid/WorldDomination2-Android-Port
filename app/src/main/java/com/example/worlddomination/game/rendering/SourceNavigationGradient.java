package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

/** Phone adapter navigation fill; iphone_skin.py MobileSkin.button. */
public final class SourceNavigationGradient {
    private static final Color NORMAL_TOP = color(48, 66, 75);
    private static final Color NORMAL_BOTTOM = color(11, 23, 29);
    private static final Color SELECTED_TOP = color(42, 90, 115);
    private static final Color SELECTED_BOTTOM = color(12, 47, 68);

    private SourceNavigationGradient() { }

    private static Color color(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }

    /** Caller owns a begun filled ShapeRenderer; existing button edge stays visible. */
    public static void draw(ShapeRenderer shapes, Rectangle row, boolean selected) {
        Color top = selected ? SELECTED_TOP : NORMAL_TOP;
        Color bottom = selected ? SELECTED_BOTTOM : NORMAL_BOTTOM;
        shapes.rect(row.x + 2.5f, row.y + 3f, row.width - 5f, row.height - 6f,
                bottom, bottom, top, top);
    }
}
