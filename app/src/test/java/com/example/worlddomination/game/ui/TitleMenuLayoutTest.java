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
        assertEquals(new Rectangle(694, 62, 428, 89), layout.newGame);
        assertEquals(TitleMenuLayout.Action.CONTINUE, layout.hit(695, 183, true));
        assertEquals(TitleMenuLayout.Action.NEW_GAME, layout.hit(1121, 150, false));
        assertEquals(TitleMenuLayout.Action.CONTINUE, layout.hit(908, 226, true));
        assertEquals(TitleMenuLayout.Action.NONE, layout.hit(908, 226, false));
        assertEquals(TitleMenuLayout.Action.NEW_GAME, layout.hit(908, 98, false));
        assertEquals(TitleMenuLayout.Action.NONE, layout.hit(908, 156, true));
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
