package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;

import static org.junit.Assert.*;

public final class TitleMenuLayoutTest {
    @Test public void compactCompositionKeepsGenerousTapTargets() {
        TitleMenuLayout layout = new TitleMenuLayout();
        layout.fit(new Rectangle(0, 0, 1280, 608));
        assertEquals(300f, layout.globeX, 0.01f);
        assertEquals(304f, layout.globeY, 0.01f);
        assertEquals(305f, layout.globeRadius, 0.01f);
        assertEquals(441.84f, layout.logo.width, 0.01f);
        assertEquals(235.2f, layout.logo.height, 0.01f);
        assertEquals(new Rectangle(694, 182, 428, 89), layout.continueGame);
        assertEquals(new Rectangle(694, 74, 428, 89), layout.newGame);
        boolean[] empty = {false, false, false};
        assertEquals(TitleMenuLayout.Action.CONTINUE, layout.hit(695, 183, false, empty));
        assertEquals(TitleMenuLayout.Action.NEW_GAME, layout.hit(1121, 150, false, empty));
        assertEquals(TitleMenuLayout.Action.CONTINUE, layout.hit(908, 226, false, empty));
        assertEquals(TitleMenuLayout.Action.NEW_GAME, layout.hit(908, 98, false, empty));
        assertEquals(TitleMenuLayout.Action.NONE, layout.hit(908, 173, false, empty));
    }

    @Test public void pickerUsesOnlyOccupiedRowsAndBack() {
        TitleMenuLayout layout = new TitleMenuLayout();
        layout.fit(new Rectangle(10, 4, 960, 500));
        boolean[] loadable = {true, false, true};
        for (int i = 0; i < 3; i++) {
            Rectangle row = layout.slots[i];
            assertEquals(loadable[i] ? TitleMenuLayout.Action.slot(i) : TitleMenuLayout.Action.NONE,
                    layout.hit(row.x + 1, row.y + row.height / 2, true, loadable));
            if (i > 0) assertTrue(row.y + row.height < layout.slots[i - 1].y);
        }
        assertTrue(layout.back.y + layout.back.height < layout.slots[2].y);
        assertEquals(TitleMenuLayout.Action.BACK, layout.hit(layout.back.x + 1, layout.back.y + 1, true, loadable));
    }

    @Test public void controlsStayWithinOffsetSafeAreasAndRetainTheirProportions() {
        TitleMenuLayout layout = new TitleMenuLayout();
        for (Rectangle area : new Rectangle[]{new Rectangle(20, 8, 836, 470),
                new Rectangle(48, 12, 1170, 520), new Rectangle(29, 4, 680, 300)}) {
            layout.fit(area);
            assertTrue(area.contains(layout.logo));
            assertTrue(area.contains(layout.continueGame));
            assertTrue(area.contains(layout.newGame));
            assertEquals(526f / 280f, layout.logo.width / layout.logo.height, 0.001f);
            assertTrue(layout.newGame.y + layout.newGame.height < layout.continueGame.y);
            assertTrue(layout.continueGame.y + layout.continueGame.height < layout.logo.y);
            assertTrue(layout.globeX + layout.globeRadius < layout.logo.x);
        }
    }
}
