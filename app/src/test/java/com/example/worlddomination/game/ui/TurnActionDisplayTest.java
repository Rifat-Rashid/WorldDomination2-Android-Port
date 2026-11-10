package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TurnActionDisplayTest {
    @Test public void eachSourceAnnouncementIncludesAiNationsAndDoesNotAlterTheQueue() {
        GameState game = resolvedTurn();
        TurnActionDisplay display = new TurnActionDisplay();
        boolean sawOpponent = false;
        for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings()) {
            display.reveal(event.action, game.getLastTurnActionBuildings());
            assertEquals(event.action, display.getAction());
            assertTrue(display.includesPlayer(event.playerId));
            if (event.playerId != 0) sawOpponent = true;
        }
        assertTrue("An enemy's chosen actions must appear in playback", sawOpponent);
        assertEquals(0, game.getQueuedActionCount());
        assertEquals(1, game.getTurn());
    }

    @Test public void changingActionClearsHighlightsForNationsNotPerformingTheNextAction() {
        GameState game = resolvedTurn();
        TurnActionDisplay display = new TurnActionDisplay();
        display.reveal(GameState.Action.RESOURCES, game.getLastTurnActionBuildings());
        assertTrue(display.includesPlayer(0));
        display.reveal(GameState.Action.ATTACK, game.getLastTurnActionBuildings());
        assertFalse(display.includesPlayer(0));
        display.reveal(GameState.Action.RESOURCES, game.getLastTurnActionBuildings());
        assertTrue(display.includesPlayer(0));
    }

    @Test public void sourceOverlayFadesByTwentyPointsToEightyAndBackToZero() {
        GameState game = resolvedTurn();
        TurnActionDisplay display = new TurnActionDisplay();
        display.reveal(GameState.Action.RESOURCES, game.getLastTurnActionBuildings());
        for (int step = 1; step <= 4; step++) {
            display.tick();
            assertEquals(step * 0.2f, display.getAlpha(), 0.0001f);
        }
        assertFalse(display.isAnimating());
        display.hide();
        for (int step = 3; step >= 0; step--) {
            display.tick();
            assertEquals(step * 0.2f, display.getAlpha(), 0.0001f);
        }
        assertFalse(display.isAnimating());
    }

    private static GameState resolvedTurn() {
        GameState game = new GameState(1, 17L);
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        return game;
    }
}
