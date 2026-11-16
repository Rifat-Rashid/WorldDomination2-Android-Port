package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Synthetic starting funds, captured through the reference Game.queue API. */
public final class SourceDefenseProductionCandidatesTest {
    @Test public void exactCostProducesDefenseAndAdvancesSourceRandom() {
        assertDefenseTurn(15, 100, 15, 41, 11122, 3, 0.5036365052098872);
    }

    @Test public void belowCostReturnsFundsAndAdvancesSourceRandom() {
        assertDefenseTurn(13, 100, 13, 54, 9102, 2, 0.18031790152968785);
    }

    @Test public void zeroBudgetFromPositivePercentageStillVisitsDefenseCandidates() {
        assertDefenseTurn(1, 1, 0, 42, 9102, 2, 0.18031790152968785);
    }

    @Test public void exactCostRocketKeepsItsSourceCandidateFilter() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        ReferenceRandom random = setup.copyGameplayRandom();
        GameState state = new GameState(setup, random);
        state.getHuman().moneyMillions = 5;
        state.getHuman().turnMoneyMillions = 5;
        state.selectAction(GameState.Action.PRODUCTION);
        assertTrue(state.configureProduction("rockets", 100));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals(46, state.getHuman().moneyMillions);
        assertEquals(9102, state.getHuman().score);
        assertEquals(1, state.getWeaponStock("rocket1"));
        assertEquals(0.18031790152968785, random.nextDouble(), 0d);
    }

    @Test public void capturedSourceInputsArePinned() throws Exception {
        assertFixtureSha("source-defense-production-queue.json", "812df7b4dbbb2ac5e0b17d66408adae2baa2a251aedab7cf286719f47175aea3");
        assertFixtureSha("source-defense-production-zero.json", "ba985ccc6ad29f6ad0d853979ced689a246465e18952e84c2ffe8310aea1cdcb");
        assertFixtureSha("source-defense-production-other.json", "03eb42746f4bf6593ab999611908c843c45f642a020c92c219a51412fc528d66");
        assertFixtureSha("source-defense-production-ai.json", "1f1390b701279665a3ab71c472429bf98485d0af98d63d79d757a5fbcb5dbb4a");
        assertFixtureSha("source-defense-production-provenance.json", "90416681d3b3c9ea5ef617a7a4e6b2af98f1764b77794c12764daf6e9c16f39d");
    }

    private static void assertFixtureSha(String name, String expected) throws Exception {
        try (java.io.InputStream input = SourceDefenseProductionCandidatesTest.class
                .getClassLoader().getResourceAsStream(name)) {
            org.junit.Assert.assertNotNull(name, input);
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.readAllBytes());
            StringBuilder actual = new StringBuilder();
            for (byte value : bytes) actual.append(String.format(java.util.Locale.ROOT,
                    "%02x", value & 255));
            assertEquals(name, expected, actual.toString());
        }
    }

    private static void assertDefenseTurn(int funds, int percent, int reserved, int expectedMoney,
                                          int expectedScore, int expectedRocket,
                                          double nextRandom) {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        ReferenceRandom random = setup.copyGameplayRandom();
        GameState state = new GameState(setup, random);
        state.getHuman().moneyMillions = funds;
        state.getHuman().turnMoneyMillions = funds;
        state.selectAction(GameState.Action.PRODUCTION);
        assertTrue(state.configureProduction("defense", percent));
        assertTrue(state.acceptSelectedAction());
        assertEquals(funds, state.getHuman().moneyMillions);
        assertEquals(funds - reserved, state.getTurnMoneyMillions());
        assertEquals(.8050278270130223, state.createPresentationRandomCopy().nextDouble(), 0d);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals(expectedMoney, state.getHuman().moneyMillions);
        assertEquals(expectedScore, state.getHuman().score);
        assertEquals(2, state.getWeaponStock("antiAir"));
        assertEquals(expectedRocket, state.getWeaponStock("antiRocket"));
        assertEquals(nextRandom, random.nextDouble(), 0d);
    }
}
