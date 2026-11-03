package com.example.worlddomination.game.simulation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import com.example.worlddomination.game.ai.OpponentAI;

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
                    "f78b151e00f97939d3a73e9272185a2869ca97d9d99c4e42f81ab39c0cdde91c",
                    "e871b101da07e4c0bf024f9a7179dc32b91216fc67e96c8740c880362d8240a8",
                    "e90c0cda94eef37e2427ea7cca8bdda08379f7c1543679355a44dd84a6f609b3",
                    "4f36ee2d232a4cb3b0fb787c5346504347ea37bba054db40b75c78625348271a",
                    "e368dd0737832b6a6c9b8e9c993eb0bd78901d996ff16cedc90c743a1193b2fd",
                    "f0d0a31f2ae9eaf7cbd11d630d9de55550f56b71a0ab3c40f427c8da7c9defbc",
                    "ad221e151d1a5f4920f310290d08229c4625744266008589e08a28b882520a10",
                    "0067efc606dbb4b3372ee2422920982c4076a756bf1fbdc988bc43dd0b1584e9",
                    "204044106f1bc12078de39be64b2117b5835d328615f3efc79acf8d6afcace2a",
                    "0b4d6516c93e43b0a92f7735bc7852cf5140f28580d36f11e065b47351c6622c",
                    "842b5987675a3bc4b1884a35ec6d3d9828288f85c78165854af16547f33d2f9d",
                    "dbfe6cc09bd941e18206d10b4f5db9c29a7f5adc10e6660bfe790c9f7080f03d",
                    "c66112be1264360c7b921f3e6fa69793119f74e8078a7abe8472f2d23e1cee8e",
                    "50c93c3f44887db0ea28e01a11deeb7c51b775657eef04d13acd3cebdfa914e9",
                    "cb1f7bd8664d9f35621c10a57ec9e60ffae37a6cf662664f40f34fce04e38bae",
                    "978809f2ca7004e5188ec21db771926868c0435c65dd66133c2d4836be97e240",
                    "4e40a1acaa99568db489b6fb74874e29e7275f840d737e12d3e229e93a6bef8f",
                    "423488f1eb237b439f2fe57b505ce78212191ae356f485d79ff5379f8f2c6633",
                    "27defcf9d1c13cb568de5a2284296639d186e837486c424afc64baa6f8bc6899"
            },
            {
                    "3b14348e00af8c1f0c59ee3438ba743cbd101b4a8718637e032057dc67ad4d9b",
                    "3295361b7d2fb45d29692a926fab66c762a9ce651a34e1cadad248d1d4e2d36f",
                    "3eeda30a78b2be567666911453828930133dc205e8352dc3929da38459de4a6f",
                    "e59bac49dc9efaa586e3026ec49617427ef8de08bf89c8eedb073be2f44d408d",
                    "c82c676b23084533d89f72718a2a4a1a8e38c14272e6dce37f24ca177e104b8f",
                    "983a77d9d0a67942239aa658b418626f3f3b134e77fc8af5bfbc9d1a5fdcc71d",
                    "da5a79b65a980466cbd77db8a75c98fe0e0d6f79c06fb16ceca4a41d04094e46",
                    "0ec9927d06daa38f1fd9e9c4d12bc67b02259060f14aecb0e83156f966f00797",
                    "84a059b51c3613c0bc84703364b6c866ba80d1897e965ac8cbb9f6d22269d819",
                    "82873bcdadd08d68e2324bb17902207b14e157c9404a89d30a07b3c952fc77e7",
                    "a838ff909aa18718423d5acef36dc410d15002643360847846b961c62879a917",
                    "e2633efc82727eebec3fd9c758e560d7d7f7e7ff8c635f5ff593bfda035ea23a",
                    "188eb792a72bf2e6fb1cde9818320f1f0b482ee555c36c6f4d593ae2bea1f3ea",
                    "dcd033d5e6a6a7278e7bf4386b239eeb3a980ac277cb283fe8e30bae30316976",
                    "f2ea1952d42f161cd4e5cd5496a0ac3e4a167f33932e7f36f0f1605f74726c85",
                    "a630556cd41248c5bfb97049e084d9192bdf010731edf32cbeb178944b4fe30b",
                    "19f770c0bab03e9d6c2cba6d2d5d792700444eb0bbd539e6b40b4072f084e679",
                    "13544da0e309fd3986a17d47e8550e0ae2111455fe7ac02e8ebdf65355099df8",
                    "1f91ae980e7eb28cc671a55cf088031ab62e987c6cd66897666f59fe89076af9",
                    "61934f809b71c41e13ccfb4a8876291e27c61ab2ddf3b7404ba5d87e2adfa468"
            },
            {
                    "83a970dbf9e09e977f4f8b97c3e8220c554b4e71b5c76c102e349593b948dc47",
                    "d496666a9b05ad64af82d037e6b66ddc9dab66a50e7cdad4f9c61671fe05cbf0",
                    "fd8a252944df5ddfe65c9d91e3a65c104c027003fb7d0e3643a65cbb17140473",
                    "787bbad6a0cbd9f7147dcb53236ee567c2c16b71fd51aa3b3e98575c7aec597a",
                    "f9e0277c2550b7168a074cbaa7178fe46499de74fd1614f4969bc78298a9c7f5",
                    "2e912425f2926f7d7717b8e347e71787202e13c6d468e58b83ec1d537a05cb1d",
                    "11610b3cba551b0e3797ac224411659ca7d9c7d57e31597d700fe003ccb348cb",
                    "85b4653a3e444429c5b3e59d2b75215479a1c9b1bf7e3698644d0879e8be4733",
                    "6586d1c3e6f3a607abd92967a994d6ec583b589aa80f13af533410dbb6f56993",
                    "cfe1da9d2e134fad89199c7b15c3277a34c366f2ea7b93164c1842afeaaaf7c7",
                    "5fbc289567e7a7b3e56802db051a82b6d7edf309261f66dde7e08ec8a777be33",
                    "5d06bd2f53b72f6ecee11b1a887366f688cee7617a1a443412cf1965d269ee69",
                    "030e1a27bf21c358a1cd24a6023861744c461ef574d3c392ab18423759ab97e1",
                    "91641e2ce080de4a20776eba96f8eba34d09d10f0a411e600f91fc72a1d357ed",
                    "cb83aaca888a5356fef01c81869d5b254c0ea1c4fe505ffcccf96b871b1b8ff3",
                    "5ec55c98ddb804d677f4a43d2cddc8c4fc9d2592f88495194f470ec1d3aab098",
                    "4550b11d718ba47e9928cd485eae6dc5fd003d7816bf6117417c8a8c284861be",
                    "bf9e64a0e7a72d299c10f0d8a78730e4066b1d664669469d23ca60246fa746eb",
                    "f5c8e87729d13d6969f0674e8eb40b39f635ddfc568b88b96dae7644dc6c6f74",
                    "7f7210978f1a3ea410112cccbda53b9419e3029fa14b1a7dc4a8f616feb15a52"
            },
            {
                    "a7dfcd5215bd3c2cfc37c352218a26faa027afaa36389ba804bdda4badc106e7",
                    "f88b3b607f7322d5aded2822945c38a6f164279540436eefa730c5a43b9e3a40",
                    "9a0d1363c4b447d74f012712e72d197c306ee7cd6ba767990e189f3d984ae663",
                    "a767aebe133d097332d29a0afecfa07997141e8b9a00f11c87765b6b45e607d8",
                    "cfec13ca1c89cd036b9975916adfcfb61ad6c5132ef5296acc04c1d8c9ea1f66",
                    "8908bd456e47355e8dc64b65e07d247e04a82e1ba984962d80ed319987c243ba",
                    "5e2403be58ed4fddf98df9f6fe645d045d3f8fcbfa8cc0a4cc81ca0183d9ccc5",
                    "293f38bd15df2016fe82d753871803283996712061d72a24df800c946dfbde67",
                    "77dca5dd612761dd168ec19fd29fc9012c1c8ddba5103366e5b521180d6d6b56",
                    "fecabf806621eb5efbd7ff154dd25b38ca6b0fe9fe8f47f466ca637bf6990c6a",
                    "cd6dbb49cbdf608b0805decc4916172f02e24aad676d90f25413eb5f811a5662",
                    "5e28be26a466cb1390f7184d20eb5aeea0369e456eb9d62324262435fc67bd9b",
                    "e08eb1d3e06382b61a14ee67cbf6c9cf6e185fc0ec48bd873365d98aaa511e25",
                    "b07f014f89063aae752c982e0bf35acccda3f00758744c81a3ce989e8c3974c3",
                    "2d3fe3d447a499c4853ed375e1b1ba214609f16cef8783d9044f0bb023003a34",
                    "edb1bc98451f5b7950aa6037a3c551c330a74b1975fb09a1f2cae81ee27442b3",
                    "15c5620a74db272a6d63acbbb8afc7a040c8eea56d3faaa8df3947fb2f7e02b6",
                    "0fb0d6b13cbfd99fc89401d358dce3ec10426c491a09277131b8c739a1fa5e8b",
                    "26c38523a64f760f72cd296cb1ae49e02414548ad818a688ea6e158fd5e912dc",
                    "283036c7f333acadbfaad1da4ddf4a295e9d2d47d0cfb362944333b76634b6e7"
            },
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
    public void twentyResolvedTurnsMatchRecoveredPythonSession() throws Exception {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(difficulty);
        for (int rival : rivals) {
            assertTrue("setup accepts selected source rival " + rival,
                    setup.toggleOpponent(rival));
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        List<String> actionHistory = new ArrayList<>();
        List<String> randomHistory = new ArrayList<>();
        List<String> hiddenStateHistory = new ArrayList<>();
        List<String> actionScoreHistory = new ArrayList<>();
        for (int turn = 1; turn <= SOURCE_TURN_HASHES[scenario].length; turn++) {
            queueHumanResourcesAndMassMedia(game);
            if (turn == 9 || turn == 10 || turn == 11
                    || turn == SOURCE_TURN_HASHES[scenario].length) {
                actionScoreHistory.add("turn " + turn + ": "
                        + actionScoresBeforePlanning(game, random));
            }
            random.clear();
            game.confirmTurn();

            actionHistory.add("turn " + turn + ": " + lastTurnActions(game));
            String drawSummary = turn <= 2 ? String.join(",", random.draws)
                    : random.draws.size() + ":" + sha256(String.join(",", random.draws));
            randomHistory.add("turn " + turn + "=" + drawSummary);
            hiddenStateHistory.add("turn " + turn + ": " + hiddenStateTrace(game));
            String trace = resolvedStateTrace(game, turn);
            assertEquals("source resolved-turn mismatch; state trace:\n" + trace
                            + "\nlast-turn actions by turn: " + String.join("; ", actionHistory)
                            + "\nrandom stream by turn: " + String.join("; ", randomHistory)
                            + "\nhidden state by turn: " + String.join("; ", hiddenStateHistory)
                            + "\naction scores before planning: "
                            + String.join("; ", actionScoreHistory),
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
            double value = delegate.nextDouble();
            draws.add("raw:" + Double.toHexString(value));
            return value;
        }
    }

    private String lastTurnActions(GameState game) {
        List<String> actions = new ArrayList<>();
        for (int playerId = 0; playerId <= rivals.length; playerId++) {
            actions.add(playerId + "=" + game.getPlayerState(playerId).getLastTurnActions());
        }
        return String.join(", ", actions);
    }

    private String hiddenStateTrace(GameState game) {
        List<String> rows = new ArrayList<>();
        for (int playerId = 0; playerId <= rivals.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            List<String> regard = new ArrayList<>();
            List<String> ceasefire = new ArrayList<>();
            List<String> diplomacy = new ArrayList<>();
            for (int otherId = 0; otherId <= rivals.length; otherId++) {
                regard.add(String.format(Locale.US, "%.5f",
                        player.getRegardTowardExact(otherId)));
                ceasefire.add(Integer.toString(player.getCeasefireTurnsWith(otherId)));
                diplomacy.add(Integer.toString(
                        player.getDiplomacyOfferedLastTurn(otherId)));
            }
            List<String> desires = new ArrayList<>();
            List<String> active = new ArrayList<>();
            for (Map.Entry<String, GameState.WeaponState> weapon
                    : player.getWeapons().entrySet()) {
                int desire = player.getDesiredStock(weapon.getKey());
                if (desire > 0) {
                    desires.add(weapon.getKey() + "=" + desire);
                }
                if (weapon.getValue().active) {
                    active.add(weapon.getKey());
                }
            }
            List<String> bioloaded = new ArrayList<>();
            for (GameState.Action action : GameState.Action.values()) {
                if (player.isActionBioloaded(action)) {
                    bioloaded.add(action.name());
                }
            }
            rows.add(String.join("|", "P" + playerId,
                    player.isSpecialPerson() ? "special" : "ordinary",
                    player.isSuicidePerson() ? "suicide" : "ordinary",
                    "enemy=" + player.getPrimaryEnemyId() + "/"
                            + player.getSecondaryEnemyId(),
                    "spy=" + player.spyReadiness,
                    "regard=" + regard,
                    "cease=" + ceasefire,
                    "diplomacy=" + diplomacy,
                    "money=" + player.moneyMillions + "/" + player.turnMoneyMillions,
                    "bio=" + bioloaded,
                    "desired=" + desires,
                    "active=" + active,
                    "tech=" + player.getInventedTechnologies()));
        }
        return String.join(";", rows);
    }

    private String actionScoresBeforePlanning(GameState game, RecordingRandom random)
            throws Exception {
        Method scoreActions = OpponentAI.class.getDeclaredMethod("scoreActions",
                GameState.class, GameState.PlayerState.class, Random.class);
        scoreActions.setAccessible(true);
        Field actionField = Class.forName(
                "com.example.worlddomination.game.ai.OpponentAI$ScoredAction")
                .getDeclaredField("action");
        Field scoreField = Class.forName(
                "com.example.worlddomination.game.ai.OpponentAI$ScoredAction")
                .getDeclaredField("score");
        actionField.setAccessible(true);
        scoreField.setAccessible(true);

        ReferenceRandom randomCopy = ((ReferenceRandom) random.delegate).copy();
        List<String> players = new ArrayList<>();
        for (int playerId = rivals.length; playerId >= 1; playerId--) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            List<?> scores = (List<?>) scoreActions.invoke(null, game, player, randomCopy);
            List<String> entries = new ArrayList<>();
            for (Object score : scores) {
                entries.add(actionField.get(score) + "=" + scoreField.get(score));
            }
            players.add("P" + playerId + "[" + String.join(",", entries) + "]");
        }
        return String.join(" ", players);
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
