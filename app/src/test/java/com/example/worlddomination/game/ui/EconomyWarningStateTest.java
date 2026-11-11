package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Recovered source warning frames, messages and retained selections, executed seed 0. */
public final class EconomyWarningStateTest {
    private GameState game() {
        GameSetup setup = new GameSetup(0L); setup.toggleOpponent(0); return new GameState(setup);
    }
    @Test public void lockedProductionReplacesTheMenuWithoutLosingExistingAllocations() {
        GameState game = game(); game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 30));
        List<String> messages = new ArrayList<>(game.getMessages());
        assertFalse(game.configureProduction("special", 40));
        SourceActionMenuState state = new SourceActionMenuState();
        state.showEconomyWarning(game, EconomyWarningText.PRODUCTION_LOCKED);
        assertEquals("production_error", state.getKey());
        assertEquals("You have not yet invented this Technology", state.getError());
        assertFalse(state.isAcceptVisible()); assertFalse(state.isCancelVisible());
        state.dismissError(game);
        assertFalse(state.isEconomyWarning(game)); assertNull(state.getKey());
        assertEquals(GameState.Action.PRODUCTION, game.getSelectedAction());
        assertEquals(30, game.getProductionPercent("soldier"));
        assertEquals(0, game.getProductionPercent("special"));
        assertEquals(65, game.getTurnMoneyMillions()); assertEquals(messages, game.getMessages());
    }
    @Test public void unaffordableResearchUsesTheOriginalExclamationAndDoesNotSpendMoney() {
        GameState game = game(); game.selectAction(GameState.Action.RESEARCH);
        game.getHuman().turnMoneyMillions = 9;
        assertFalse(game.selectResearchTechnology("rocket2"));
        SourceActionMenuState state = new SourceActionMenuState();
        state.showEconomyWarning(game, EconomyWarningText.research(game, "rocket2"));
        assertEquals("research_error", state.getKey()); assertEquals("Not enough money!", state.getError());
        assertEquals(9, game.getTurnMoneyMillions()); assertEquals(0, game.getSelectedResearchCostMillions());
        assertFalse(state.accept(game)); assertFalse(state.isCancelVisible());
    }
    @Test public void dismissingTheSourceFundsWarningRetainsThePriorTenMillionSelection() {
        GameState game = game(); game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("rocket2")); game.getHuman().turnMoneyMillions = 10;
        List<String> messages = new ArrayList<>(game.getMessages());
        assertFalse(game.selectResearchTechnology("plane2"));
        SourceActionMenuState state = new SourceActionMenuState();
        state.showEconomyWarning(game, EconomyWarningText.research(game, "plane2")); state.dismissError(game);
        assertEquals(GameState.Action.RESEARCH, game.getSelectedAction());
        assertEquals(10, game.getSelectedResearchCostMillions());
        assertTrue(game.isResearchTechnologySelected("rocket2"));
        assertFalse(game.isResearchTechnologySelected("plane2"));
        assertEquals(10, game.getTurnMoneyMillions()); assertEquals(messages, game.getMessages());
    }
    @Test public void exactAffordabilityDoesNotProduceAWarning() {
        GameState game = game(); game.selectAction(GameState.Action.RESEARCH);
        game.getHuman().turnMoneyMillions = 10;
        assertNull(EconomyWarningText.research(game, "rocket2"));
        assertTrue(game.selectResearchTechnology("rocket2"));
        assertNull(EconomyWarningText.research(game, "rocket2"));
        assertTrue(game.selectResearchTechnology("rocket2"));
        assertEquals(0, game.getSelectedResearchCostMillions());
    }
    @Test public void specialPrerequisiteWarningOverridesTheInsufficientFundsWarning() {
        GameState game = game(); game.selectAction(GameState.Action.RESEARCH);
        game.getHuman().turnMoneyMillions = 0;
        assertEquals("You need to invent   4 complete technologies before you can start inventing special weapons.",
                EconomyWarningText.research(game, "sub"));
    }
    @Test public void completedAndUnknownCardsDoNotInventWarnings() {
        GameState game = game(); game.selectAction(GameState.Action.RESEARCH);
        assertNull(EconomyWarningText.research(game, "rocket1"));
        assertNull(EconomyWarningText.research(game, "not-a-technology"));
        game.cancelSelection(); assertNull(EconomyWarningText.research(game, "rocket2"));
    }
    @Test public void clearingAWarningDoesNotRefundOrRemoveAnotherAcceptedAction() {
        GameState game = game(); game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("rocket2")); assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.PRODUCTION);
        SourceActionMenuState state = new SourceActionMenuState();
        state.showEconomyWarning(game, EconomyWarningText.PRODUCTION_LOCKED); state.clearEconomyWarning();
        assertEquals(GameState.Action.RESEARCH, game.getQueuedAction(0));
        assertEquals(55, game.getTurnMoneyMillions());
        assertFalse(state.isEconomyWarning(game));
        game.cancelSelection(); state.begin(game); assertFalse(state.isEconomyWarning(game));
    }
    @Test public void staleWarningsCannotReplaceADifferentActionScreen() {
        GameState game = game(); game.selectAction(GameState.Action.RESEARCH);
        SourceActionMenuState state = new SourceActionMenuState();
        state.showEconomyWarning(game, EconomyWarningText.RESEARCH_FUNDS);
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertFalse(state.isEconomyWarning(game)); state.begin(game);
        assertEquals("religion-" + game.getHuman().getReligionCode(), state.getKey()); assertNull(state.getError());
    }
}
