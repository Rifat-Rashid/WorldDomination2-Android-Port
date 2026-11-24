package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.persistence.GameSaveCodec;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reload at every human boundary while the real opponent planner keeps running. */
public final class GameSaveContinuationTest {
    @Test public void repeatedReloadsKeepSeededAiTurnsEquivalentAcrossSetupChoices() throws Exception {
        int checkedTurns = 0;
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            for (int rivals = 1; rivals <= 4; rivals++) {
                for (long seed : new long[]{0L, 42L, 207L}) {
                    GameSetup setup = new GameSetup(seed);
                    setup.setDifficulty(difficulty);
                    for (int rival = 0; rival < rivals; rival++) assertTrue(setup.toggleOpponent(rival));
                    GameState original = new GameState(setup);
                    GameState resumed = GameSaveCodec.decode(GameSaveCodec.encode(original));
                    for (int turn = 0; turn < 16; turn++) {
                        for (GameState state : new GameState[]{original, resumed}) {
                            while (state.getPendingHumanDiplomacyProposal() != null)
                                assertTrue(state.respondToDiplomacyProposal(turn % 2 == 0
                                        && state.canAcceptPendingHumanDiplomacy()));
                            state.selectAction(GameState.Action.RESOURCES);
                            if (!state.acceptSelectedAction()) {
                                state.selectAction(GameState.Action.SKIP);
                                assertTrue(state.acceptSelectedAction());
                            }
                            state.selectAction(turn % 2 == 0 ? GameState.Action.MASS_MEDIA
                                    : GameState.Action.PRODUCTION);
                            if (turn % 2 != 0) state.configureProductionSlider("rockets", 50);
                            if (!state.acceptSelectedAction()) {
                                state.selectAction(GameState.Action.SKIP);
                                assertTrue(state.acceptSelectedAction());
                            }
                        }
                        resumed = GameSaveCodec.decode(GameSaveCodec.encode(resumed));
                        original.confirmTurn();
                        resumed.confirmTurn();
                        checkedTurns++;
                        assertEquals(original.getMatchStatus(), resumed.getMatchStatus());
                        assertEquals(original.getMessages(), resumed.getMessages());
                        assertEquals(original.createPresentationRandomCopy().nextDouble(),
                                resumed.createPresentationRandomCopy().nextDouble(), 0d);
                        if (original.getMatchStatus() != GameState.MatchStatus.ONGOING) break;
                        assertArrayEquals("Setup " + difficulty + "/" + rivals + "/" + seed + " turn " + turn,
                                GameSaveCodec.encode(original), GameSaveCodec.encode(resumed));
                        resumed = GameSaveCodec.decode(GameSaveCodec.encode(resumed));
                    }
                }
            }
        }
        assertTrue("Exercise repeated real AI turns", checkedTurns >= 200);
    }
}
