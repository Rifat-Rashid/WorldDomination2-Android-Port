package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.GameSettingsMenu.Command;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GameSettingsMenuTest {
    @Test
    public void saveAndLoadAvailabilityControlTheirHitTargets() {
        GameSettingsMenu menu = createMenu();
        menu.open(true, false);
        assertEquals(Command.SAVE, hit(menu, menu.save));
        assertEquals(Command.NONE, hit(menu, menu.load));
        assertEquals(Command.SOUND, hit(menu, menu.sound));
        assertEquals(Command.CLOSE, hit(menu, menu.close));
        menu.requestLoad();
        assertFalse(menu.isConfirmingLoad());

        menu.open(false, true);
        assertEquals(Command.NONE, hit(menu, menu.save));
        assertEquals(Command.LOAD, hit(menu, menu.load));
    }

    @Test
    public void successfulSaveEnablesLoadInTheOpenMenu() {
        GameSettingsMenu menu = createMenu();
        menu.open(true, false);

        menu.saved(2);

        assertTrue(menu.isOpen());
        assertTrue(menu.canLoad());
        assertEquals(Command.LOAD, hit(menu, menu.load));
        assertEquals("Game saved. Turn 2.", menu.getStatus());
    }

    @Test
    public void loadConfirmationOwnsInputUntilBackAndCannotOpenFromClosedMenu() {
        GameSettingsMenu menu = createMenu();
        menu.open(true, true);
        menu.requestLoad();

        assertTrue(menu.isConfirmingLoad());
        assertEquals(Command.CONFIRM_LOAD, hit(menu, menu.confirm));
        assertEquals(Command.CANCEL_LOAD, hit(menu, menu.cancel));
        assertEquals(Command.NONE, hit(menu, menu.save));
        assertEquals(Command.NONE, hit(menu, menu.load));

        menu.back();
        assertTrue(menu.isOpen());
        assertFalse(menu.isConfirmingLoad());
        assertEquals(Command.LOAD, hit(menu, menu.load));

        menu.back();
        menu.requestLoad();
        assertFalse(menu.isOpen());
        assertFalse(menu.isConfirmingLoad());
        assertEquals(Command.NONE, hit(menu, menu.load));
    }

    @Test
    public void normalPanelKeepsItsAuthoredSizeAndPositions() {
        GameSettingsMenu menu = createMenu();

        assertEquals(new Rectangle(298f, 89f, 300f, 236f), menu.panel);
        assertEquals(new Rectangle(316f, 235f, 264f, 34f), menu.save);
        assertEquals(new Rectangle(451f, 119f, 129f, 32f), menu.confirm);
        assertEquals(1f, menu.getScale(), 0f);
    }

    @Test
    public void activeTargetsStayInsideThePanelAtNormalAndCompactSizes() {
        GameSettingsMenu menu = new GameSettingsMenu();
        menu.open(true, true);
        Rectangle[] areas = {
                new Rectangle(0f, 0f, 1194f, 552f),
                new Rectangle(18f, 12f, 450f, 280f),
                new Rectangle(34f, 11f, 250f, 294f),
                new Rectangle(12f, 20f, 550f, 160f),
                new Rectangle(1f, 8f, 96f, 78f)
        };
        for (Rectangle area : areas) {
            menu.layout(area);

            assertInside(area, menu.panel);
            assertEquals(300f / 236f, menu.panel.width / menu.panel.height, 0.0001f);
            assertDisjointInside(menu.panel, menu.save, menu.load, menu.sound, menu.close);
            assertDisjointInside(menu.panel, menu.cancel, menu.confirm);
            assertEquals(Command.LOAD, hit(menu, menu.load));
            menu.requestLoad();
            assertEquals(Command.CONFIRM_LOAD, hit(menu, menu.confirm));
            menu.back();
        }
    }

    @Test
    public void collapsedLayoutCannotActivateAControl() {
        GameSettingsMenu menu = createMenu();
        menu.open(true, true);
        menu.layout(new Rectangle(4f, 9f, 200f, 0f));

        assertEquals(0f, menu.getScale(), 0f);
        assertEquals(Command.NONE, hit(menu, menu.save));
    }

    private static GameSettingsMenu createMenu() {
        GameSettingsMenu menu = new GameSettingsMenu();
        menu.layout(new Rectangle(0f, 0f, 896f, 414f));
        return menu;
    }

    private static Command hit(GameSettingsMenu menu, Rectangle target) {
        return menu.hit(target.x + target.width / 2f, target.y + target.height / 2f);
    }

    private static void assertDisjointInside(Rectangle panel, Rectangle... controls) {
        for (int i = 0; i < controls.length; i++) {
            assertInside(panel, controls[i]);
            for (int j = i + 1; j < controls.length; j++) {
                assertFalse(controls[i].overlaps(controls[j]));
            }
        }
    }

    private static void assertInside(Rectangle outer, Rectangle inner) {
        assertTrue(inner.width > 0f && inner.height > 0f);
        assertTrue(outer.contains(inner.x, inner.y));
        assertTrue(outer.contains(inner.x + inner.width, inner.y + inner.height));
    }
}
