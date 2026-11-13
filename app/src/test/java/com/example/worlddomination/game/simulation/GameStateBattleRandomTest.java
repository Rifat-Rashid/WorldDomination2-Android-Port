package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The battle script starts from the source RNG boundary within turn resolution. */
public final class GameStateBattleRandomTest {
    // Recovered Battle.createBattleScriptVars completion, before route draws.
    // SetupMenu seed 0, Normal, color 5, building 4, rivals 0 and 1.
    private static final double[] RESOURCES_MASS_MEDIA = {
            0.38490114597266045, 0.5756510141648885, 0.290329502402758,
            0.18939132855435614, 0.1867295282555551, 0.6127731798686067,
            0.6566593889896288, 0.47653099200938076, 0.08982436119559367,
            0.7576039219664368
    };
    private static final double[] RESEARCH_PRODUCTION = {
            0.4449890262755162, 0.5962868615831063, 0.38490114597266045,
            0.5756510141648885, 0.290329502402758, 0.18939132855435614,
            0.1867295282555551, 0.6127731798686067, 0.6566593889896288,
            0.47653099200938076
    };
    private static final double[] PROPAGANDA_ATTACK = {
            0.5756510141648885, 0.290329502402758, 0.18939132855435614,
            0.1867295282555551, 0.6127731798686067, 0.6566593889896288,
            0.47653099200938076, 0.08982436119559367, 0.7576039219664368,
            0.8767703708227748
    };
    private static final double[] SPY_RESOURCES = {
            0.6566593889896288, 0.47653099200938076, 0.08982436119559367,
            0.7576039219664368, 0.8767703708227748, 0.9233810159462806,
            0.8424602231401824, 0.898173121357879, 0.9230824398201768,
            0.5405999249480544
    };

    @Test
    public void sourceResourcesMassMediaBattleBoundaryMatches() {
        assertSourcePrefix(ActionPair.RESOURCES_MASS_MEDIA, RESOURCES_MASS_MEDIA);
    }

    @Test
    public void sourceResearchProductionBattleBoundaryMatches() {
        assertSourcePrefix(ActionPair.RESEARCH_PRODUCTION, RESEARCH_PRODUCTION);
    }

    @Test
    public void sourcePropagandaAttackBattleBoundaryMatches() {
        assertSourcePrefix(ActionPair.PROPAGANDA_ATTACK, PROPAGANDA_ATTACK);
    }

    @Test
    public void sourceSpyResourcesBattleBoundaryMatches() {
        assertSourcePrefix(ActionPair.SPY_RESOURCES, SPY_RESOURCES);
    }

    @Test
    public void customSimulationRandomStillResolvesWithoutPresentationSnapshot() {
        GameState game = new GameState(1, new java.util.Random(0L), false);
        queue(game, ActionPair.RESOURCES_MASS_MEDIA);
        game.confirmTurn();
        assertEquals(1, game.getTurn());
        try {
            game.createBattlePresentationRandomCopy();
            fail("An arbitrary simulation stream cannot supply a reference snapshot");
        } catch (IllegalStateException expected) {
            // Explicit custom Random injection remains usable for simulation.
        }
    }

    @Test
    public void battleCopiesAreIndependentAndDoNotChangeFutureSimulation() {
        GameState observed = newSourceGame();
        GameState control = newSourceGame();
        try {
            observed.createBattlePresentationRandomCopy();
            fail("Battle RNG has no boundary before the first confirmed turn");
        } catch (IllegalStateException expected) {
            // The first boundary exists only after the turn's resource results.
        }

        queue(observed, ActionPair.RESOURCES_MASS_MEDIA);
        queue(control, ActionPair.RESOURCES_MASS_MEDIA);
        observed.confirmTurn();
        control.confirmTurn();

        ReferenceRandom firstCopy = observed.createBattlePresentationRandomCopy();
        ReferenceRandom secondCopy = observed.createBattlePresentationRandomCopy();
        assertEquals(RESOURCES_MASS_MEDIA[0], firstCopy.nextDouble(), 0.0);
        assertEquals(RESOURCES_MASS_MEDIA[0], secondCopy.nextDouble(), 0.0);
        for (int draw = 0; draw < 50; draw++) firstCopy.nextDouble();
        assertEquals(RESOURCES_MASS_MEDIA[0],
                observed.createBattlePresentationRandomCopy().nextDouble(), 0.0);
        assertEquals("The existing getter still copies the live simulation stream",
                control.createPresentationRandomCopy().nextDouble(),
                observed.createPresentationRandomCopy().nextDouble(), 0.0);

        queue(observed, ActionPair.RESOURCES_MASS_MEDIA);
        queue(control, ActionPair.RESOURCES_MASS_MEDIA);
        observed.confirmTurn();
        control.confirmTurn();
        double secondTurnBoundary = observed.createBattlePresentationRandomCopy().nextDouble();
        assertEquals(control.createBattlePresentationRandomCopy().nextDouble(),
                secondTurnBoundary, 0.0);
        assertNotEquals("A later turn replaces the stored battle boundary",
                RESOURCES_MASS_MEDIA[0], secondTurnBoundary, 0.0);
        assertEquals("Presentation draws cannot advance the next turn's simulation RNG",
                control.createPresentationRandomCopy().nextDouble(),
                observed.createPresentationRandomCopy().nextDouble(), 0.0);
        assertEquals(control.getHuman().moneyMillions, observed.getHuman().moneyMillions);
        assertEquals(control.getHuman().score, observed.getHuman().score);
    }

    private static void assertSourcePrefix(ActionPair pair, double[] expected) {
        GameState game = newSourceGame();
        queue(game, pair);
        game.confirmTurn();
        ReferenceRandom battle = game.createBattlePresentationRandomCopy();
        for (int draw = 0; draw < expected.length; draw++) {
            assertEquals(pair + " battle draw " + draw, expected[draw],
                    battle.nextDouble(), 0.0);
        }
    }

    private static GameState newSourceGame() {
        GameSetup setup = new GameSetup(0L);
        assertEquals(1, setup.getDifficulty());
        assertEquals(5, setup.getPlayerColor());
        assertEquals(4, setup.getStartingBuilding());
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }

    private static void queue(GameState game, ActionPair pair) {
        switch (pair) {
            case RESOURCES_MASS_MEDIA:
                game.selectAction(GameState.Action.RESOURCES);
                assertTrue(game.acceptSelectedAction());
                game.selectAction(GameState.Action.MASS_MEDIA);
                assertTrue(game.acceptSelectedAction());
                return;
            case RESEARCH_PRODUCTION:
                game.selectAction(GameState.Action.RESEARCH);
                assertTrue(game.selectResearchTechnology("soldier2"));
                assertTrue(game.acceptSelectedAction());
                game.selectAction(GameState.Action.PRODUCTION);
                assertTrue(game.configureProduction("soldier", 50));
                assertTrue(game.acceptSelectedAction());
                return;
            case PROPAGANDA_ATTACK:
                game.selectAction(GameState.Action.PROPAGANDA);
                assertTrue(game.configurePropaganda(1, 1));
                assertTrue(game.acceptSelectedAction());
                game.selectAction(GameState.Action.ATTACK);
                assertTrue(game.configureAttack(1, 0, "soldier1"));
                assertTrue(game.acceptSelectedAction());
                return;
            case SPY_RESOURCES:
                game.selectAction(GameState.Action.ATTACK);
                assertTrue(game.configureAttack(1, 0, "spy1"));
                assertTrue(game.acceptSelectedAction());
                game.selectAction(GameState.Action.RESOURCES);
                assertTrue(game.acceptSelectedAction());
                return;
            default:
                throw new AssertionError(pair);
        }
    }

    private enum ActionPair {
        RESOURCES_MASS_MEDIA, RESEARCH_PRODUCTION, PROPAGANDA_ATTACK, SPY_RESOURCES
    }
}
