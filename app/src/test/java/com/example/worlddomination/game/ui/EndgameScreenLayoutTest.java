package com.example.worlddomination.game.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EndgameScreenLayoutTest {
    @Test public void uniformlyFitsSourceStageInsideSafeLandscapeContent() {
        EndgameScreenLayout layout = new EndgameScreenLayout();
        layout.fit(48f, 25f, 1098f, 523f);
        assertEquals(523f / 600f, layout.scale(), 0.0001f);
        assertEquals(248.333f, layout.left(), 0.01f);
        assertEquals(697.333f, layout.width(), 0.01f);
        assertEquals(25f, layout.bottom(), 0.01f);
        assertEquals(layout.left() + .45f * layout.scale(), layout.x(0), 0.001f);
    }

    @Test public void newGameHitUsesTheDrawTransformAndStaysOutOfWebsite() {
        EndgameScreenLayout layout = new EndgameScreenLayout();
        layout.fit(48f, 25f, 1098f, 523f);
        assertTrue(layout.hitNewGame(layout.x(553.55f), layout.y(465.3f)));
        assertTrue(layout.hitNewGame(layout.x(627.6f), layout.y(465.3f)));
        assertFalse("Website begins at raw x=627.65", layout.hitNewGame(
                layout.x(627.65f), layout.y(465.3f)));
        assertFalse(layout.hitNewGame(layout.x(700f), layout.y(465.3f)));
        assertFalse(layout.hitNewGame(layout.x(553.55f), layout.y(300f)));
        layout.fit(0f, 0f, 1280f, 720f);
        assertTrue(layout.hitNewGame(layout.x(553.55f), layout.y(465.3f)));
    }
}
