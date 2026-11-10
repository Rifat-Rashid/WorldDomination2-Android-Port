package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.ui.ActionSlotAnimation;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import org.junit.Test;
import static org.junit.Assert.*;

/** Recovered Presenter_interface.btnPressed_tube and Player.getNextTurnAction. */
public final class SourceActionSlotParityTest {
    private final GameState game = new GameState(1, 17L, false);

    private void queue(GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }

    @Test public void cancellingFirstActionDoesNotMoveTheSecondTube() {
        queue(GameState.Action.RESOURCES); queue(GameState.Action.MASS_MEDIA);
        game.removeQueuedAction(0);
        assertNull(game.getQueuedAction(0));
        assertEquals(GameState.Action.MASS_MEDIA, game.getQueuedAction(1));
        assertEquals(1, game.getQueuedActionCount());
        assertEquals(0, game.getNextActionSlot());
        game.removeQueuedAction(0);
        assertEquals(GameState.Action.MASS_MEDIA, game.getQueuedAction(1));
        assertEquals(1, game.getQueuedActionCount());
    }

    @Test public void refillingFirstActionRetainsBothFixedSlotIdentities() {
        queue(GameState.Action.RESOURCES); queue(GameState.Action.MASS_MEDIA);
        game.removeQueuedAction(0); queue(GameState.Action.SKIP);
        assertEquals(GameState.Action.SKIP, game.getQueuedAction(0));
        assertEquals(GameState.Action.MASS_MEDIA, game.getQueuedAction(1));
        assertEquals(2, game.getQueuedActionCount());
        assertEquals(-1, game.getNextActionSlot());
        game.removeQueuedAction(1);
        assertEquals(GameState.Action.SKIP, game.getQueuedAction(0));
        assertNull(game.getQueuedAction(1));
        assertEquals(1, game.getNextActionSlot());
    }

    @Test public void cancellationRefundsOnlyItsOriginalReservedBudgetOnce() {
        int funds = game.getTurnMoneyMillions();
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 50));
        assertTrue(game.acceptSelectedAction());
        int reserved = funds - game.getTurnMoneyMillions();
        assertTrue(reserved > 0);
        queue(GameState.Action.MASS_MEDIA);
        game.removeQueuedAction(1);
        assertEquals(funds - reserved, game.getTurnMoneyMillions());
        queue(GameState.Action.SKIP);
        game.removeQueuedAction(0);
        assertEquals(funds, game.getTurnMoneyMillions());
        assertEquals(GameState.Action.SKIP, game.getQueuedAction(1));
        game.removeQueuedAction(0);
        assertEquals(funds, game.getTurnMoneyMillions());
    }

    @Test public void invalidSlotsCannotCancelOrdersOrRefundMoney() {
        queue(GameState.Action.RESOURCES); queue(GameState.Action.MASS_MEDIA);
        int funds = game.getTurnMoneyMillions();
        game.removeQueuedAction(-1); game.removeQueuedAction(2);
        assertEquals(2, game.getQueuedActionCount());
        assertEquals(funds, game.getTurnMoneyMillions());
        assertNull(game.getQueuedAction(-1)); assertNull(game.getQueuedAction(2));
    }

    @Test public void firstHoleReceivesThePreviewWithoutReplacingSecondAction() {
        queue(GameState.Action.RESOURCES); queue(GameState.Action.MASS_MEDIA);
        game.removeQueuedAction(0); game.selectAction(GameState.Action.SKIP);
        ActionSlotAnimation animation = new ActionSlotAnimation();
        animation.synchronize(game, false, false, 0);
        assertEquals(GameState.Action.SKIP, animation.getDisplayedAction(0));
        assertEquals(GameState.Action.MASS_MEDIA, animation.getDisplayedAction(1));
        animation.tick();
        assertEquals(ActionSlotAnimation.OPEN_FRAME, animation.getFrame(0));
        assertEquals(ActionSlotAnimation.SET_FRAME, animation.getFrame(1));
    }

    @Test public void skipDialogAcceptsOneEmptyActionWithoutSpendingMoney() {
        game.selectAction(GameState.Action.SKIP);
        SourceActionMenuState menu = new SourceActionMenuState(); menu.begin(game);
        assertEquals("skip_action", menu.getKey());
        assertTrue(menu.isAcceptVisible()); assertTrue(menu.isCancelVisible());
        assertFalse(menu.isMapSelection());
        int funds = game.getTurnMoneyMillions();
        assertTrue(menu.accept(game));
        assertEquals(funds, game.getTurnMoneyMillions());
        assertEquals(GameState.Action.SKIP, game.getQueuedAction(0));
        assertNull(game.getQueuedAction(1));
    }

    @Test public void cancellingSkipDoesNotConsumeAnActionOrChangeTheOtherSlot() {
        queue(GameState.Action.RESOURCES); queue(GameState.Action.MASS_MEDIA);
        game.removeQueuedAction(0); game.selectAction(GameState.Action.SKIP);
        SourceActionMenuState menu = new SourceActionMenuState(); menu.begin(game);
        assertTrue(menu.cancel(game));
        assertNull(game.getSelectedAction()); assertNull(game.getQueuedAction(0));
        assertEquals(GameState.Action.MASS_MEDIA, game.getQueuedAction(1));
        assertEquals(1, game.getQueuedActionCount());
    }

    @Test public void twoSkipsAdvanceTheTurnWithOnlySourceUpkeep() {
        GameSetup setup = new GameSetup(0L);
        setup.toggleOpponent(0);
        GameState sourceGame = new GameState(setup);
        for (int slot = 0; slot < 2; slot++) {
            sourceGame.selectAction(GameState.Action.SKIP);
            assertTrue(sourceGame.acceptSelectedAction());
        }
        sourceGame.confirmTurn();
        assertEquals(1, sourceGame.getTurn());
        assertEquals(65, sourceGame.getHuman().moneyMillions);
        assertEquals(65, sourceGame.getTurnMoneyMillions());
        assertEquals(450, sourceGame.getHuman().populationMillions);
        assertEquals(55, sourceGame.getHuman().religion);
        assertEquals(50, sourceGame.getHuman().spyReadiness);
        for (GameState.ActionBuildingEvent event : sourceGame.getLastTurnActionBuildings())
            assertNotEquals(0, event.playerId);
        for (GameState.CombatEvent event : sourceGame.getLastTurnCombatEventsInSourcePresentationOrder())
            assertNotEquals(0, event.attackerPlayerId);
        assertEquals(0, sourceGame.getQueuedActionCount()); assertEquals(0, sourceGame.getNextActionSlot());
    }

    @Test public void skipDoesNotBypassTheTwoAcceptedActionRequirement() {
        queue(GameState.Action.SKIP);
        try { game.confirmTurn(); fail("A single Skip does not fill both source tubes"); }
        catch (IllegalStateException expected) { assertEquals(0, game.getTurn()); }
        assertEquals(1, game.getQueuedActionCount());
        queue(GameState.Action.SKIP); game.selectAction(GameState.Action.RESOURCES);
        assertNull(game.getSelectedAction()); assertFalse(game.acceptSelectedAction());
        assertEquals(2, game.getQueuedActionCount());
    }

    @Test public void skipIsPortedWithoutAddingANavigationButton() {
        assertTrue(GameState.Action.SKIP.isPorted());
        assertTrue(SourceActionMenuState.supports(GameState.Action.SKIP));
        assertEquals(9, GameState.Action.navigationValues().length);
        for (GameState.Action action : GameState.Action.navigationValues())
            assertNotEquals(GameState.Action.SKIP, action);
    }
}
