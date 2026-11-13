package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Source: SetupMenu(17), hard difficulty, leaders 0/9/18/5, 21 resource/media turns. */
public final class ConcurrentAiStrikeDefenseParityTest {
    @Test
    public void matchingDefenseInterceptsOneOfTwoConcurrentAiStrikes() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        ReferenceRandom random = setup.copyGameplayRandom();
        GameState game = new GameState(setup, random);
        for (int turn = 0; turn < 21; turn++) {
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue(game.acceptSelectedAction());
            game.confirmTurn();
        }
        assertEquals("rocket2", game.getPreparedCarrierName(3));
        assertEquals("plane1", game.getPreparedCarrierName(4));
        assertEquals(2, game.getWeaponStock(0, "antiRocket"));

        game.selectAction(GameState.Action.DEFENSE);
        assertTrue(game.configureDefense("antiRocket"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();

        List<GameState.CombatEvent> strikes = game.getLastTurnCombatEvents();
        assertEquals(2, strikes.size());
        assertEquals(GameState.CombatEventType.STRIKE_INTERCEPTED, strikes.get(0).type);
        assertEquals(3, strikes.get(0).attackerPlayerId);
        assertEquals(2, strikes.get(0).cityIndex);
        assertEquals("bioload2", strikes.get(0).payloadName);
        assertEquals(0, strikes.get(0).casualtiesMillions);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, strikes.get(1).type);
        assertEquals(4, strikes.get(1).attackerPlayerId);
        assertEquals(2, strikes.get(1).cityIndex);
        assertEquals("warhead2", strikes.get(1).payloadName);
        assertEquals(37, strikes.get(1).casualtiesMillions);
        assertEquals(105, game.getHuman().populationMillions);
        assertEquals(0, game.getHuman().getCity(2).populationMillions);
        assertEquals(350834, game.getHuman().score);
        assertEquals(515732, game.getPlayerState(3).score);
        assertEquals(665810, game.getPlayerState(4).score);
        assertEquals(1, game.getWeaponStock(0, "antiRocket"));
        assertEquals(null, game.getPreparedCarrierName(3));
        assertEquals(null, game.getPreparedCarrierName(4));
        assertEquals(0.8785621649396403, random.nextDouble(), 1e-15);
    }
}
