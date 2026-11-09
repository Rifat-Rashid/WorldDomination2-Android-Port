package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Captured source session: Normal, seed 7, rivals 2 and 9, with AI enabled. */
public final class HumanSourceActionSessionTest {
    private static final String[] SOURCE_STATES = {
            "3e0a47c37d809b211332e65b03c007e4ba1c9f4b66ff980c173c22635d490859",
            "27a72e3b6f603d60c49654fcdd32ecb5bc8b2bdb45a3639e3bf95e52707a3f4c",
            "5ae3262d1ac2bd97d8beed69e33dfcaf62eef7b84b1a1625f198ef94d9cb9927",
            "630fcb42b52c1494a2e5822ff47b414b49f1005ba07332d98c2792625ca444e6",
            "bc3af7175b1e8b084534de5dbb2bae43662c75e42c72468db08d9b782b690018",
            "9a0c293873ccf55ae49688afaa7207c9d892dc92893184bb3d9701f72697403a",
            "5f9e043fdef829e1a15060f4e5733ca702c516fb999e259aa44bbe557bf9e76e",
            "982759422415d9801fe3df6a325c602ff33d4e75a34b006f44d4111eeaf1dafe",
            "5f1d086ff8bf70f1a19520780225668493eb0c7cfe702eac2ad810bb88a7fcde",
            "3e485387b397fb9fab38a664c5afb6f3e62840b0db0298df70a188431cc13437"
    };

    @Test
    public void mixedHumanActionsMatchTenResolvedSourceTurns() throws Exception {
        GameSetup setup = new GameSetup(7L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(2));
        assertTrue(setup.toggleOpponent(9));
        GameState game = new GameState(setup);
        for (int turn = 1; turn <= SOURCE_STATES.length; turn++) {
            queueTurn(game, turn);
            game.confirmTurn();
            String trace = trace(game, turn);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(trace.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte value : digest) {
                hash.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            assertEquals("source human-action session turn " + turn + "\n" + trace,
                    SOURCE_STATES[turn - 1], hash.toString());
        }
    }

    private static void queueTurn(GameState game, int turn) {
        switch (turn) {
            case 1:
                resources(game);
                game.selectAction(GameState.Action.RESEARCH);
                assertTrue(game.selectResearchTechnology("soldier2"));
                break;
            case 2:
                game.selectAction(GameState.Action.PRODUCTION);
                assertTrue(game.configureProduction("soldier", 50));
                break;
            case 3:
                game.selectAction(GameState.Action.ATTACK);
                assertTrue(game.configureAttack(1, 0, "soldier1"));
                break;
            case 4:
                game.selectAction(GameState.Action.MASS_MEDIA);
                assertTrue(game.acceptSelectedAction());
                game.selectAction(GameState.Action.PROPAGANDA);
                assertTrue(game.configurePropaganda(2, 0));
                break;
            case 5:
                game.selectAction(GameState.Action.DEFENSE);
                assertTrue(game.configureDefense("antiAir"));
                break;
            case 6:
                game.selectAction(GameState.Action.DIPLOMACY);
                while (game.getSelectedTargetPlayer() != 1) {
                    game.cycleDiplomacyTarget(1);
                }
                assertEquals(0, game.getSelectedDiplomacyOption());
                break;
            case 7:
                game.selectAction(GameState.Action.ATTACK);
                assertTrue(game.configureAttack(2, 0, "spy1"));
                break;
            case 8:
                game.selectAction(GameState.Action.PREPARED_ATTACK);
                assertTrue(game.configurePreparedCarrier("rocket1"));
                break;
            case 9:
                game.selectAction(GameState.Action.PREPARED_ATTACK);
                assertTrue(game.configurePreparedLaunch(2, 0, "warhead1"));
                break;
            case 10:
                game.selectAction(GameState.Action.RESEARCH);
                assertTrue(game.selectResearchTechnology("warhead2"));
                break;
            default:
                throw new AssertionError("Unexpected turn " + turn);
        }
        assertTrue(game.acceptSelectedAction());
        if (turn != 1 && turn != 4) {
            resources(game);
        }
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static String trace(GameState game, int turn) {
        List<String> rows = new ArrayList<>();
        for (int id = 0; id <= 2; id++) {
            GameState.PlayerState player = game.getPlayerState(id);
            String cities = player.getCities().stream()
                    .map(city -> Integer.toString(city.populationMillions))
                    .collect(Collectors.joining(","));
            Map<String, Integer> stocks = new TreeMap<>();
            player.getWeapons().forEach((name, weapon) -> {
                if (!"anti".equals(weapon.definition.type)) {
                    stocks.put(name, weapon.inStock);
                }
            });
            String stock = stocks.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
            String technologies = player.getInventedTechnologies().stream().sorted()
                    .collect(Collectors.joining(","));
            rows.add(String.join("|", "P", Integer.toString(turn), Integer.toString(id),
                    player.alive ? "1" : "0", Integer.toString(player.moneyMillions),
                    Integer.toString(player.populationMillions), Integer.toString(player.religion),
                    Long.toString(player.score), cities, stock, technologies));
        }
        return String.join("\n", rows);
    }
}
