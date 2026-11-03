package com.example.worlddomination.game.simulation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Compares resolved multi-turn Android state to recovered Python game sessions.
 * Source rows come from SetupMenu.start_game and Game.advance with the human
 * queuing Resources and Mass Media on each turn. Rows include each active
 * player's funds, population, religion, score, city populations, regular weapon
 * stock, and invented technologies; defense counters remain in their own source
 * array and are intentionally excluded from the weapon-stock row.
 */
@RunWith(Parameterized.class)
public final class AiSourceResolvedTurnParityTest {
    private static final String[][] SOURCE_TURN_HASHES = {
            {
                    "5c7a601c20844305cca8336165249aa0ff25342b5ba551862298f90245ddc4c2",
                    "f78b151e00f97939d3a73e9272185a2869ca97d9d99c4e42f81ab39c0cdde91c"
            },
            {
                    "3b14348e00af8c1f0c59ee3438ba743cbd101b4a8718637e032057dc67ad4d9b",
                    "3295361b7d2fb45d29692a926fab66c762a9ce651a34e1cadad248d1d4e2d36f"
            },
            {
                    "83a970dbf9e09e977f4f8b97c3e8220c554b4e71b5c76c102e349593b948dc47",
                    "d496666a9b05ad64af82d037e6b66ddc9dab66a50e7cdad4f9c61671fe05cbf0"
            },
            {
                    "a7dfcd5215bd3c2cfc37c352218a26faa027afaa36389ba804bdda4badc106e7",
                    "f88b3b607f7322d5aded2822945c38a6f164279540436eefa730c5a43b9e3a40"
            }
    };

    private final int scenario;
    private final long seed;
    private final int difficulty;
    private final int[] rivals;

    public AiSourceResolvedTurnParityTest(int scenario, long seed, int difficulty,
                                          int[] rivals) {
        this.scenario = scenario;
        this.seed = seed;
        this.difficulty = difficulty;
        this.rivals = rivals;
    }

    @Parameterized.Parameters(name = "resolved source session {0}, seed {1}, difficulty {2}")
    public static Collection<Object[]> scenarios() {
        return Arrays.asList(new Object[][]{
                {0, 0L, 0, new int[]{0}},
                {1, 7L, 1, new int[]{2, 9}},
                {2, 42L, 2, new int[]{4}},
                {3, 1337L, 1, new int[]{0, 9, 12, 18}}
        });
    }

    @Test
    public void twoResolvedTurnsMatchRecoveredPythonSession() throws Exception {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(difficulty);
        for (int rival : rivals) {
            assertTrue("setup accepts selected source rival " + rival,
                    setup.toggleOpponent(rival));
        }
        GameState game = new GameState(setup);

        for (int turn = 1; turn <= SOURCE_TURN_HASHES[scenario].length; turn++) {
            queueHumanResourcesAndMassMedia(game);
            game.confirmTurn();

            String trace = resolvedStateTrace(game, turn);
            assertEquals("source resolved-turn mismatch; state trace:\n" + trace,
                    SOURCE_TURN_HASHES[scenario][turn - 1], sha256(trace));
        }
    }

    private String resolvedStateTrace(GameState game, int turn) {
        List<String> rows = new ArrayList<>();
        for (int playerId = 0; playerId <= rivals.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            List<String> cities = new ArrayList<>();
            for (CityState city : player.getCities()) {
                cities.add(Integer.toString(city.populationMillions));
            }

            Map<String, Integer> stocks = new TreeMap<>();
            for (Map.Entry<String, GameState.WeaponState> weapon
                    : player.getWeapons().entrySet()) {
                if (!"anti".equals(weapon.getValue().definition.type)) {
                    stocks.put(weapon.getKey(), weapon.getValue().inStock);
                }
            }
            List<String> stockRows = new ArrayList<>();
            for (Map.Entry<String, Integer> stock : stocks.entrySet()) {
                stockRows.add(stock.getKey() + "=" + stock.getValue());
            }

            Set<String> technologies = player.getInventedTechnologies();
            List<String> sortedTechnologies = new ArrayList<>(technologies);
            Collections.sort(sortedTechnologies);

            rows.add(String.join("|", "P", Integer.toString(scenario),
                    Integer.toString(turn), Integer.toString(player.id),
                    player.alive ? "1" : "0", Integer.toString(player.moneyMillions),
                    Integer.toString(player.populationMillions),
                    Integer.toString(player.religion), Long.toString(player.score),
                    String.join(",", cities), String.join(",", stockRows),
                    String.join(",", sortedTechnologies)));
        }
        return String.join("\n", rows);
    }

    private static void queueHumanResourcesAndMassMedia(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
    }

    private static String sha256(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            result.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return result.toString();
    }
}
