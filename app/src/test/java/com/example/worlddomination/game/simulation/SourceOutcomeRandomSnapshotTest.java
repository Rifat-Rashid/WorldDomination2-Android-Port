package com.example.worlddomination.game.simulation;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public final class SourceOutcomeRandomSnapshotTest {
    private static final double SOURCE_SEED_ZERO_START = 0.3101475693193326;

    @Test public void setupSeedZeroMatchesCapturedMatchStartStream() {
        GameState game = match(0L);
        assertEquals(SOURCE_SEED_ZERO_START,
                game.createOutcomePresentationRandomCopy().nextDouble(), 0d);
        assertEquals(SOURCE_SEED_ZERO_START,
                game.createPresentationRandomCopy().nextDouble(), 0d);
    }

    @Test public void separateOutcomeCopiesDoNotAdvanceEachOtherOrGameplay() {
        GameState game = match(0L);
        ReferenceRandom first = game.createOutcomePresentationRandomCopy();
        ReferenceRandom second = game.createOutcomePresentationRandomCopy();
        assertEquals(SOURCE_SEED_ZERO_START, first.nextDouble(), 0d);
        first.nextDouble();
        assertEquals(SOURCE_SEED_ZERO_START, second.nextDouble(), 0d);
        assertEquals(SOURCE_SEED_ZERO_START, game.createOutcomePresentationRandomCopy().nextDouble(), 0d);
        assertEquals(SOURCE_SEED_ZERO_START, game.createPresentationRandomCopy().nextDouble(), 0d);
    }

    @Test public void legalTurnsAdvanceCanonicalStreamButKeepOutcomeStart() {
        GameState game = match(0L);
        for (int turn = 0; turn < 3; turn++) {
            while (game.getPendingHumanDiplomacyProposal() != null)
                assertTrue(game.respondToDiplomacyProposal(false));
            queueTurn(game);
            assertEquals(SOURCE_SEED_ZERO_START,
                    game.createOutcomePresentationRandomCopy().nextDouble(), 0d);
        }
        assertNotEquals(SOURCE_SEED_ZERO_START, game.createPresentationRandomCopy().nextDouble(), 0d);
    }

    @Test public void freshMatchesOwnTheirOwnStartingStreams() {
        GameState first = match(0L);
        queueTurn(first);
        GameState restarted = match(0L);
        GameState otherSeed = match(1L);
        double otherInitial = otherSeed.createPresentationRandomCopy().nextDouble();
        assertNotEquals(SOURCE_SEED_ZERO_START, otherInitial, 0d);
        assertEquals(otherInitial, otherSeed.createOutcomePresentationRandomCopy().nextDouble(), 0d);
        assertEquals(SOURCE_SEED_ZERO_START, restarted.createOutcomePresentationRandomCopy().nextDouble(), 0d);
        assertEquals(SOURCE_SEED_ZERO_START, first.createOutcomePresentationRandomCopy().nextDouble(), 0d);
    }

    @Test public void genericRandomStillPlaysButCannotProvideOutcomeSnapshot() {
        GameState game = new GameState(0, new Random(17L), false);
        queueTurn(game);
        assertEquals(1, game.getTurn());
        try {
            game.createOutcomePresentationRandomCopy();
            fail("generic random cannot provide a reference-compatible snapshot");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("reference-compatible"));
        }
    }

    private static GameState match(long seed) {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(0);
        assertTrue(setup.toggleOpponent(0));
        return new GameState(setup);
    }

    private static void queueTurn(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
    }
}
