package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ActionScreenLayoutTest {
    @Test
    public void mapFirstActionsKeepTheBattlefieldVisible() {
        assertFalse(ActionScreenLayout.opensCenterDialog(GameState.Action.MASS_MEDIA));
        assertFalse(ActionScreenLayout.opensCenterDialog(GameState.Action.ATTACK));
        assertTrue(ActionScreenLayout.hasInlineActionControls(GameState.Action.MASS_MEDIA));
        assertTrue(ActionScreenLayout.hasInlineActionControls(GameState.Action.ATTACK));
        assertTrue(ActionScreenLayout.selectsTargetsOnMap(GameState.Action.ATTACK));
        assertFalse(ActionScreenLayout.selectsTargetsOnMap(GameState.Action.MASS_MEDIA));
        assertTrue(ActionScreenLayout.opensCenterDialog(GameState.Action.RESEARCH));
    }

    @Test
    public void technologyCardsFollowSourceCategoryOrderAndFitWithoutOverlap() {
        int[] expectedGroups = {1, 6, 7, 2, 3, 4, 5};
        Rectangle modal = new Rectangle(130f, 40f, 335f, 253f);
        Rectangle titleBar = new Rectangle(modal.x + 3f, modal.y + modal.height - 29f,
                modal.width - 6f, 25f);
        Rectangle[] cards = new Rectangle[TechnologyScreenLayout.CARD_COUNT];
        for (int i = 0; i < cards.length; i++) {
            assertEquals(expectedGroups[i], TechnologyScreenLayout.sourceGroupAtCard(i));
            assertEquals(i, TechnologyScreenLayout.cardIndexForGroup(expectedGroups[i]));
            cards[i] = TechnologyScreenLayout.setCardBounds(i, modal, new Rectangle());
            assertTrue(modal.contains(cards[i]));
            assertFalse("Category cards stay below the title bar", cards[i].overlaps(titleBar));
            for (int previous = 0; previous < i; previous++) {
                assertFalse(cards[i].overlaps(cards[previous]));
            }
        }

        Rectangle selected = TechnologyScreenLayout.setSelectedBounds(modal, new Rectangle());
        assertTrue(modal.contains(selected));
        for (Rectangle card : cards) {
            assertFalse("Selected technology summary stays below the category grid",
                    selected.overlaps(card));
        }
    }

    @Test
    public void footerControlsFitAndRemainIndependent() {
        Rectangle panel = new Rectangle(10f, 20f, 360f, 64f);
        Rectangle[] buttons = new Rectangle[ActionFooterLayout.BUTTON_COUNT];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = ActionFooterLayout.setButtonBounds(panel, i, new Rectangle());
            assertTrue(panel.contains(buttons[i]));
            for (int previous = 0; previous < i; previous++) {
                assertFalse(buttons[i].overlaps(buttons[previous]));
            }
        }
    }

    @Test
    public void informationRowsClearTheTitleAndFitInsideThePanel() {
        Rectangle panel = new Rectangle(0f, 0f, 190f, 82f);
        float scale = InformationPanelLayout.fontScale(panel, 15f);
        float renderedLineHeight = scale * 15f;
        float rowHeight = InformationPanelLayout.rowHeight(panel);
        float titlePlateBottom = panel.y + panel.height - 5f;
        float firstRowTop = InformationPanelLayout.rowBaseline(panel, 0) + renderedLineHeight;
        float lastRowBottom = InformationPanelLayout.rowBaseline(panel, 4);

        assertTrue("Funds row remains below the info title plate", firstRowTop < titlePlateBottom);
        assertTrue("All five rows retain readable spacing",
                rowHeight >= renderedLineHeight);
        assertTrue("Spy row remains above the bottom panel frame", lastRowBottom > panel.y);
        assertTrue(InformationPanelLayout.barY(panel, 1)
                < InformationPanelLayout.rowBaseline(panel, 1));
    }

    @Test
    public void informationBarsSitInsideTheirRowsInsteadOfOnTextBaselines() {
        Rectangle panel = new Rectangle(0f, 20f, 190f, 82f);
        float baseline = InformationPanelLayout.rowBaseline(panel, 2);
        float barY = InformationPanelLayout.barY(panel, 2);

        assertTrue("Bars are vertically centered in the label row", barY < baseline);
        assertTrue("Bars remain below the preceding row", barY + 4.2f
                < InformationPanelLayout.rowBaseline(panel, 1));
        assertTrue("Bars remain within their own row", barY
                < baseline + InformationPanelLayout.rowHeight(panel));
    }
}
