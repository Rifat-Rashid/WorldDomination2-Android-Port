package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NavigationPanelLayoutTest {
    @Test
    public void tallerViewportUsesSpaceBetweenLogoAndInfoWithoutChangingPhoneLayout() {
        Rectangle logo = new Rectangle(12f, 348f, 192f, 62f);
        Rectangle info = new Rectangle(12f, 4f, 192f, 82f);
        Rectangle panel = new Rectangle();
        NavigationPanelLayout.setPanelBounds(logo, info, panel);
        assertEquals(new Rectangle(12f, 90f, 192f, 244f), panel);

        logo.y += 258f;
        NavigationPanelLayout.setPanelBounds(logo, info, panel);
        assertEquals(new Rectangle(12f, 90f, 192f, 502f), panel);
    }

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
