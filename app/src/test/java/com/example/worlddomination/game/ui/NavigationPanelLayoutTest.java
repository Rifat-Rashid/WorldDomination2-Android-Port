package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NavigationPanelLayoutTest {
    @Test
    public void headerFreeViewportKeepsEvenFrameInsetsAndUsesTheRecoveredSpace() {
        Rectangle panel = new Rectangle(12f, 24f, 240f, 244f);
        Rectangle content = new Rectangle();

        NavigationPanelLayout.setContentBounds(panel, content);

        assertEquals(20f, content.x, 0.001f);
        assertEquals(32f, content.y, 0.001f);
        assertEquals(224f, content.width, 0.001f);
        assertEquals(228f, content.height, 0.001f);
        assertEquals(8f, panel.y + panel.height - content.y - content.height,
                0.001f);
    }
}
