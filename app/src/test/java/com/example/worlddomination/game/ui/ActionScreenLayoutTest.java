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
        assertTrue(ActionScreenLayout.opensCenterDialog(GameState.Action.MASS_MEDIA));
        assertFalse(ActionScreenLayout.opensCenterDialog(GameState.Action.ATTACK));
        assertFalse(ActionScreenLayout.hasInlineActionControls(GameState.Action.MASS_MEDIA));
        assertTrue(ActionScreenLayout.hasInlineActionControls(GameState.Action.ATTACK));
        assertTrue(ActionScreenLayout.selectsTargetsOnMap(GameState.Action.ATTACK));
        assertFalse(ActionScreenLayout.selectsTargetsOnMap(GameState.Action.MASS_MEDIA));
        assertTrue(ActionScreenLayout.opensCenterDialog(GameState.Action.RESEARCH));
    }

    @Test
    public void technologyCardsFollowSourceCategoryOrderAndFitWithoutOverlap() {
        int[] expectedGroups = {1, 6, 4, 5, 7, 2, 3};
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
    public void technologyTouchTargetsMatchTheOriginalFlashPanelPositions() {
        Rectangle modal = new Rectangle(50f, 30f, 365f, 275f);
        Rectangle first = TechnologyScreenLayout.setCardBounds(0, modal, new Rectangle());
        Rectangle specialCarrier = TechnologyScreenLayout.setCardBounds(4, modal,
                new Rectangle());
        Rectangle warheads = TechnologyScreenLayout.setCardBounds(5, modal, new Rectangle());
        Rectangle bioloads = TechnologyScreenLayout.setCardBounds(6, modal, new Rectangle());

        assertEquals(modal.x + 14f, first.x, 0.001f);
        assertEquals(modal.y + 275f - 39f - 50f, first.y, 0.001f);
        assertEquals(modal.x + 145f, specialCarrier.x, 0.001f);
        assertEquals(modal.y + 275f - 38f - 89f, specialCarrier.y, 0.001f);
        assertEquals(modal.x + 245f, warheads.x, 0.001f);
        assertEquals(modal.x + 245f, bioloads.x, 0.001f);
        assertTrue(ActionScreenLayout.opensCenterDialog(GameState.Action.RESEARCH));
    }

    @Test
    public void researchTextFieldsMatchTheirFlashTimelinePositions() {
        assertEquals(57.2f, TechnologyScreenLayout.cardCostX(0), 0.001f);
        assertEquals(46.2f, TechnologyScreenLayout.cardCostBaselineTop(0), 0.001f);
        assertEquals(151.6f, TechnologyScreenLayout.cardCostX(4), 0.001f);
        assertEquals(114.7f, TechnologyScreenLayout.cardCostBaselineTop(4), 0.001f);
        assertEquals(151.5f, TechnologyScreenLayout.selectedNamesBaselineTop(), 0.001f);
        assertEquals(152f, TechnologyScreenLayout.selectedCostsBaselineTop(), 0.001f);
        assertEquals(234.1f, TechnologyScreenLayout.estimatedTotalBaselineTop(), 0.001f);
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
