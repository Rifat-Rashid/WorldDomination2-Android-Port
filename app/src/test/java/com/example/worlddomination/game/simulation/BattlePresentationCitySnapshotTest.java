package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class BattlePresentationCitySnapshotTest {
    @Test
    public void legalSoldierThreeTurnRetainsPreImpactCityImage() {
        GameState fresh = new GameState(1, 0L);
        assertThrows(IllegalStateException.class,
                () -> fresh.getBattlePresentationCity(1, 2));
        GameState game = prepareLegalSoldierThreeAttack();

        game.confirmTurn();
        GameState.BattleCitySnapshot snapshot = game.getBattlePresentationCity(1, 2);
        assertEquals(47, snapshot.populationMillions);
        assertEquals(4, snapshot.artworkFrame);
        assertEquals(35, game.getPlayerState(1).getCity(2).populationMillions);
        assertEquals(3, game.getPlayerState(1).getCity(2).getArtworkFrame());
        assertEquals(12, game.getLastTurnCombatEvents().get(0).casualtiesMillions);
    }

    @Test
    public void laterTurnReplacesSnapshotWithoutMutatingPriorValueOrRandom() {
        GameState observed = prepareLegalSoldierThreeAttack();
        GameState control = prepareLegalSoldierThreeAttack();
        observed.confirmTurn();
        control.confirmTurn();
        GameState.BattleCitySnapshot old = observed.getBattlePresentationCity(1, 2);
        assertEquals(control.createBattlePresentationRandomCopy().nextDouble(),
                observed.createBattlePresentationRandomCopy().nextDouble(), 0.0);

        resources(observed);
        resources(observed);
        resources(control);
        resources(control);
        observed.confirmTurn();
        control.confirmTurn();

        GameState.BattleCitySnapshot current = observed.getBattlePresentationCity(1, 2);
        assertNotSame(old, current);
        assertEquals(47, old.populationMillions);
        assertEquals(4, old.artworkFrame);
        assertEquals(control.getBattlePresentationCity(1, 2).populationMillions,
                current.populationMillions);
        assertEquals(control.getBattlePresentationCity(1, 2).artworkFrame,
                current.artworkFrame);
        assertEquals(control.createPresentationRandomCopy().nextDouble(),
                observed.createPresentationRandomCopy().nextDouble(), 0.0);
    }

    private static GameState prepareLegalSoldierThreeAttack() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);

        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier2"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();

        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier3"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 100));
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertTrue(game.getHuman().getWeapons().get("soldier3").active);
        assertTrue(game.getHuman().getWeapons().get("soldier3").inStock > 0);

        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 2, "soldier3"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        return game;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }
}
