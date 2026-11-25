package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Scales the 1280 x 608 title composition in the same world coordinates as input. */
public final class TitleMenuLayout {
    public enum Action {
        NONE, CONTINUE, NEW_GAME, BACK, SLOT_1(1), SLOT_2(2), SLOT_3(3);

        private static final Action[] SLOTS = {SLOT_1, SLOT_2, SLOT_3};
        public final int slotNumber;
        Action() { this(0); }
        Action(int slotNumber) { this.slotNumber = slotNumber; }
        public static Action slot(int index) { return SLOTS[index]; }
    }

    public final Rectangle logo = new Rectangle();
    public final Rectangle continueGame = new Rectangle();
    public final Rectangle newGame = new Rectangle();
    public final Rectangle[] slots = {new Rectangle(), new Rectangle(), new Rectangle()};
    public final Rectangle back = new Rectangle();
    public float globeX, globeY, globeRadius, scale;

    public void fit(Rectangle safeArea) {
        scale = Math.min(safeArea.height / 608f, safeArea.width / 1160f);
        float controlsX = safeArea.x + safeArea.width * (908f / 1280f);
        float centerY = safeArea.y + safeArea.height * 0.5f;
        globeRadius = 305f * scale;
        // The decorative hemisphere can extend under the left cutout. Controls stay in the safe area.
        globeX = Math.min(300f * scale, controlsX - 608f * scale);
        globeY = centerY;
        // Keep the original logo aspect and center the smaller mark above both actions.
        place(logo, controlsX, centerY, 78, 526f * 0.84f, 280f * 0.84f);
        place(continueGame, controlsX, centerY, 337, 428, 89);
        place(newGame, controlsX, centerY, 445, 428, 89);
        for (int i = 0; i < slots.length; i++) {
            place(slots[i], controlsX, centerY, 325 + 60 * i, 400, 54);
        }
        place(back, controlsX, centerY, 511, 150, 48);
    }

    private void place(Rectangle bounds, float centerX, float centerY,
                       float referenceTop, float width, float height) {
        bounds.set(centerX - width * scale / 2f,
                centerY + (304f - referenceTop - height) * scale,
                width * scale, height * scale);
    }

    public Action hit(float x, float y, boolean pickingSlots, boolean[] loadable) {
        if (pickingSlots) {
            if (back.contains(x, y)) return Action.BACK;
            for (int i = 0; i < slots.length; i++) {
                if (loadable[i] && slots[i].contains(x, y)) return Action.slot(i);
            }
            return Action.NONE;
        }
        if (continueGame.contains(x, y)) return Action.CONTINUE;
        if (newGame.contains(x, y)) return Action.NEW_GAME;
        return Action.NONE;
    }
}
