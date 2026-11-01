package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Seeded regression for source result ordering, score RNG, and production. */
public final class AiSourceTurnResolutionTraceTest {
    private static final int[][] EXPECTED_PLAYER_STATE = {
            // money, population, religion, score
            {86, 400, 74, 18146},
            {98, 450, 52, 16188},
            {101, 455, 50, 17252},
            {63, 450, 51, 44504},
            {78, 445, 53, 33834}
    };

    private static final int[][] EXPECTED_CITY_POPULATIONS = {
            {73, 47, 80, 95, 105},
            {59, 97, 54, 84, 156},
            {58, 81, 111, 79, 126},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 187}
    };

    private static final List<String> EXPECTED_RANDOM_DRAWS = Arrays.asList(
            "91:30", "3:2", "91:52", "3:0", "91:0", "3:2", "91:31", "3:1",
            "2:0", "3:1", "2:1", "141:43", "4:0", "21:20", "61:55", "4:0",
            "21:11", "61:41",
            "6:2", "6:4", "6:5", "6:3", "4:1", "3:0", "6:1",
            "11:1", "11:1", "11:2", "11:9", "11:5", "11:8", "11:10", "11:8",
            "11:0", "11:10", "11:1", "11:8", "11:9", "11:7", "11:4",
            "51:3", "51:4", "51:32", "51:12", "51:27", "51:13", "51:8", "6:1",
            "2:1", "51:24", "2:0", "51:24", "2:1", "51:26", "2:0", "51:49",
            "2:1", "2:0", "51:16", "2:0", "51:18", "2:0", "51:37", "2:1",
            "51:21");

    private static final int[][] EXPECTED_SECOND_TURN_PLAYER_STATE = {
            {117, 352, 93, 38992},
            {53, 450, 47, 191328},
            {67, 455, 43, 123816},
            {91, 450, 43, 70760},
            {63, 445, 45, 51948}
    };

    private static final int[][] EXPECTED_SECOND_TURN_CITY_POPULATIONS = {
            {73, 47, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {58, 81, 111, 79, 126},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 187}
    };

    // nextDouble() for the strike casualty is checked by the 48M combat event
    // below; this list records every bounded draw surrounding that raw draw.
    private static final List<String> EXPECTED_SECOND_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:81", "3:0", "91:52", "3:2", "91:50", "3:1", "91:33", "3:2",
            "3:2", "5:1", "141:50", "4:2", "21:10", "61:31", "61:11", "5:2",
            "5:3", "5:3", "5:2", "2:0", "6:3", "6:3", "6:2", "6:0", "6:1",
            "51:42", "11:5", "11:8", "11:2", "11:2", "11:5", "11:7",
            "11:0", "11:5", "11:9", "11:6", "11:10", "11:1", "11:9", "11:9",
            "11:0", "51:27", "51:38", "51:11", "51:33", "51:43", "51:40", "51:21",
            "51:2", "51:27", "51:9", "51:27", "51:13", "51:17", "1:0", "51:41",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0");

    @Test
    public void hardSeededFirstTurnMatchesSourceResolutionAndRandomTrace() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        // Initialization uses raw random() calls for city population splits.
        // Keep the recorder transparent, then compare only the turn trace below.
        random.clear();

        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();

        assertEquals(1, game.getTurn());
        for (int playerId = 0; playerId < EXPECTED_PLAYER_STATE.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            assertEquals("money for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][0], player.moneyMillions);
            assertEquals("population for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][1], player.populationMillions);
            assertEquals("religion for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][2], player.religion);
            assertEquals("score for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][3], player.score);
            for (int cityIndex = 0; cityIndex < EXPECTED_CITY_POPULATIONS[playerId].length;
                 cityIndex++) {
                assertEquals("city " + cityIndex + " for player " + playerId,
                        EXPECTED_CITY_POPULATIONS[playerId][cityIndex],
                        player.getCity(cityIndex).populationMillions);
            }
        }

        assertEquals(0, game.getPlayerState(1).getWeaponStockByName("plane1"));
        assertEquals(5, game.getPlayerState(3).getWeaponStockByName("soldier1"));
        assertEquals(3, game.getPlayerState(3).getWeaponStockByName("soldier2"));
        assertEquals(4, game.getPlayerState(4).getWeaponStockByName("warhead1"));
        assertEquals(2, game.getPlayerState(4).getWeaponStockByName("warhead2"));
        assertEquals(Arrays.asList(GameState.Action.RESOURCES,
                        GameState.Action.PREPARED_ATTACK),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PROPAGANDA),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("all AI, upkeep, timer, score, and production draws match source",
                EXPECTED_RANDOM_DRAWS, random.draws);

    }

    @Test
    public void hardSeededSecondTurnMatchesSourceActionSlotsAndState() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        queueHumanTurn(game);
        game.confirmTurn();
        assertEquals(1, game.getTurn());
        random.clear();
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(2, game.getTurn());
        GameState.CombatEvent strike = game.getLastTurnCombatEvents().get(0);
        assertEquals(1, strike.attackerPlayerId);
        assertEquals(0, strike.defenderPlayerId);
        assertEquals(3, strike.cityIndex);
        assertEquals("warhead2", strike.payloadName);
        assertEquals("turn-two strike " + strike.attackerPlayerId + "->"
                        + strike.defenderPlayerId + " city=" + strike.cityIndex
                        + " weapon=" + strike.weaponName + " payload=" + strike.payloadName,
                48, strike.casualtiesMillions);
        assertSeededState(game, EXPECTED_SECOND_TURN_PLAYER_STATE,
                EXPECTED_SECOND_TURN_CITY_POPULATIONS);
        assertEquals("second turn AI, city, and resolution draws match the source trace",
                EXPECTED_SECOND_TURN_RANDOM_DRAWS, random.draws);
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.RESEARCH),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.RESEARCH),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.DIPLOMACY),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals(2, game.getPlayerState(1).getWeaponStockByName("warhead1"));
        assertEquals(0, game.getPlayerState(1).getWeaponStockByName("warhead2"));
        assertEquals(5, game.getPlayerState(3).getWeaponStockByName("soldier1"));
        assertEquals(4, game.getPlayerState(3).getWeaponStockByName("soldier2"));
        assertTrue(game.getPlayerState(4).hasTechnology("warhead3"));
    }

    private static void assertSeededState(GameState game, int[][] players,
                                         int[][] cities) {
        for (int playerId = 0; playerId < players.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            assertEquals("money for player " + playerId,
                    players[playerId][0], player.moneyMillions);
            assertEquals("population for player " + playerId,
                    players[playerId][1], player.populationMillions);
            assertEquals("religion for player " + playerId,
                    players[playerId][2], player.religion);
            assertEquals("score for player " + playerId,
                    players[playerId][3], player.score);
            for (int cityIndex = 0; cityIndex < cities[playerId].length; cityIndex++) {
                assertEquals("city " + cityIndex + " for player " + playerId,
                        cities[playerId][cityIndex],
                        player.getCity(cityIndex).populationMillions);
            }
        }
    }

    private static void queueHumanTurn(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
    }

    private static final class RecordingRandom extends Random {
        private final Random delegate;
        private final List<String> draws = new ArrayList<>();

        RecordingRandom(Random delegate) {
            this.delegate = delegate;
        }

        void clear() {
            draws.clear();
        }

        @Override
        public int nextInt(int bound) {
            int value = delegate.nextInt(bound);
            draws.add(bound + ":" + value);
            return value;
        }

        @Override
        public double nextDouble() {
            return delegate.nextDouble();
        }
    }
}
