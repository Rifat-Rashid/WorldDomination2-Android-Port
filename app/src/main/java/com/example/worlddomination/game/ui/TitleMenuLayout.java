package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Scales the 1280 x 608 title composition in the same world coordinates as input. */
public final class TitleMenuLayout {
    public enum Action { NONE, CONTINUE, NEW_GAME }

    public final Rectangle logo = new Rectangle();
    public final Rectangle continueGame = new Rectangle();
    public final Rectangle newGame = new Rectangle();
    public float globeX, globeY, globeRadius, scale;

    public void fit(Rectangle safeArea) {
        scale = Math.min(safeArea.height / 608f, safeArea.width / 1160f);
        float controlsX = safeArea.x + safeArea.width * (908f / 1280f);
        float centerY = safeArea.y + safeArea.height * 0.5f;
        globeRadius = 305f * scale;
        globeX = Math.min(safeArea.x + safeArea.width * (300f / 1280f),
                controlsX - 608f * scale);
        globeY = centerY;
        place(logo, controlsX, centerY, 49, 526, 280);
        place(continueGame, controlsX, centerY, 337, 428, 89);
        place(newGame, controlsX, centerY, 465, 428, 89);
    }

    private void place(Rectangle bounds, float centerX, float centerY,
                       float referenceTop, float width, float height) {
        bounds.set(centerX - width * scale / 2f,
                centerY + (304f - referenceTop - height) * scale,
                width * scale, height * scale);
    }

    public Action hit(float x, float y, boolean canContinue) {
        if (canContinue && continueGame.contains(x, y)) return Action.CONTINUE;
        if (newGame.contains(x, y)) return Action.NEW_GAME;
        return Action.NONE;
    }
}
