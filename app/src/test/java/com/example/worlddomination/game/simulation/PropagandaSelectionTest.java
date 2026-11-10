package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.*;

public final class PropagandaSelectionTest {
    @Test
    public void openingPropagandaRequiresAnExplicitEnemyCityTap() {
        GameState game = new GameState(1, 329L, false);
        assertTrue(game.selectBattlefieldCity(1, 0));
        game.selectAction(GameState.Action.PROPAGANDA);
        assertFalse(game.canAcceptPropaganda());
        assertFalse(game.acceptSelectedAction());
        assertEquals(0, game.getQueuedActionCount());

        assertTrue(game.selectBattlefieldCity(2, 3));
        assertTrue(game.canAcceptPropaganda());
        assertEquals(2, game.getSelectedTargetPlayer());
        assertEquals(3, game.getSelectedTargetCity());
    }

    @Test
    public void ownDeadAndEmptyCitiesCannotChangeTheChosenTarget() {
        GameState game = new GameState(1, 329L, false);
        game.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(game.selectBattlefieldCity(1, 2));
        game.getPlayerState(2).alive = false;
        game.getPlayerState(3).getCity(0).populationMillions = 0;
        assertFalse(game.selectBattlefieldCity(0, 0));
        assertFalse(game.selectBattlefieldCity(2, 0));
        assertFalse(game.selectBattlefieldCity(3, 0));
        assertFalse(game.configurePropaganda(-1, 0));
        assertFalse(game.configurePropaganda(5, 0));
        assertFalse(game.configurePropaganda(1, -1));
        assertFalse(game.configurePropaganda(1, 5));
        assertEquals(1, game.getSelectedTargetPlayer());
        assertEquals(2, game.getSelectedTargetCity());
        assertEquals(1, game.getSelectedMapCityOwner());
        assertEquals(2, game.getSelectedMapCityIndex());
    }

    @Test
    public void ceasefireTargetsAreRejectedEvenAtZeroRemainingTurns() throws Exception {
        GameState game = new GameState(1, 329L, false);
        game.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(game.configurePropaganda(1, 0));
        setCeasefire(game, 1, 0);
        assertFalse(game.canAcceptPropaganda());
        assertFalse(game.acceptSelectedAction());
        assertFalse(game.selectBattlefieldCity(1, 1));
        assertFalse(game.configurePropaganda(1, 1));
        game.cyclePropagandaTarget(1);
        assertTrue(game.canAcceptPropaganda());
        assertEquals(2, game.getSelectedTargetPlayer());
    }

    @Test
    public void cancelAndReopenRequireANewSelectionWithoutSpendingFunds() {
        GameState game = new GameState(1, 329L, false);
        int funds = game.getTurnMoneyMillions();
        game.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(game.selectBattlefieldCity(1, 0));
        game.cancelSelection();
        assertFalse(game.canAcceptPropaganda());
        game.selectAction(GameState.Action.PROPAGANDA);
        assertFalse(game.canAcceptPropaganda());
        assertEquals(0, game.getQueuedActionCount());
        assertEquals(funds, game.getTurnMoneyMillions());
    }

    @Test
    public void acceptingQueuesTheTappedCityAndResolvesOriginalPopulationTransfer() {
        GameState game = new GameState(1, 329L, false);
        int funds = game.getTurnMoneyMillions();
        int population = game.getPlayerState(2).getCity(3).populationMillions;
        int transfer = game.getPropagandaTransferEstimate(2, 3);
        game.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(game.selectBattlefieldCity(2, 3));
        assertTrue(game.acceptSelectedAction());
        assertNull(game.getSelectedAction());
        assertEquals(GameState.Action.PROPAGANDA, game.getQueuedAction(0));
        assertEquals(funds, game.getTurnMoneyMillions());
        assertEquals(population, game.getPlayerState(2).getCity(3).populationMillions);

        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertEquals(1, game.getLastTurnPropagandaEvents().size());
        GameState.PropagandaEvent event = game.getLastTurnPropagandaEvents().get(0);
        assertEquals(2, event.targetPlayerId);
        assertEquals(3, event.targetCityIndex);
        assertEquals(population - transfer,
                game.getPlayerState(2).getCity(3).populationMillions);
        game.selectAction(GameState.Action.PROPAGANDA);
        assertFalse(game.canAcceptPropaganda());
    }

    private static void setCeasefire(GameState game, int opponent, int turns) throws Exception {
        java.lang.reflect.Field field = GameState.PlayerState.class.getDeclaredField("ceasefireTurns");
        field.setAccessible(true);
        ((int[]) field.get(game.getHuman()))[opponent] = turns;
        field = GameState.PlayerState.class.getDeclaredField("ceasefireAtZero");
        field.setAccessible(true);
        ((boolean[]) field.get(game.getHuman()))[opponent] = turns == 0;
    }
}
