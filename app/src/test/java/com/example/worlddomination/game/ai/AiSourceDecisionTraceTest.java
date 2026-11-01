package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.ReferenceRandom;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** Locks seeded setup-to-game AI planning traces to independent Python bytecode fixtures. */
@RunWith(Parameterized.class)
public final class AiSourceDecisionTraceTest {
    private final int scenario;
    private final long seed;
    private final int difficulty;
    private final int[] leaders;
    private final String expectedSha256;

    public AiSourceDecisionTraceTest(int scenario, long seed, int difficulty,
                                     int[] leaders, String expectedSha256) {
        this.scenario = scenario;
        this.seed = seed;
        this.difficulty = difficulty;
        this.leaders = leaders;
        this.expectedSha256 = expectedSha256;
    }

    @Parameterized.Parameters(name = "source setup trace {0}, difficulty {2}")
    public static Collection<Object[]> sourceSetups() {
        return Arrays.asList(new Object[][]{
                {0, 1L, 1, new int[]{0},
                        "9e3be5301537720fae253b4775b5f33a3ce5c47c9f01e002a7bad55b34f088b2"},
                {1, 9L, 0, new int[]{1, 4, 8},
                        "d46c2451c07f016ed75c94b9bcb253c15b59b68c91f0383bbab917e39c5ccbd6"},
                {2, 17L, 2, new int[]{0, 9, 18, 5},
                        "57b3004de04cfcef538e11b29820f1f094acff2b2d86781f2922147b501a8671"}
        });
    }

    @Test
    public void selectedLeaderGameMatchesSourceFirstTurnDecisionTrace() throws Exception {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(difficulty);
        for (int leader : leaders) {
            setup.toggleOpponent(leader);
        }
        GameState game = new GameState(setup);
        assertHardSetupMatchesSource(game);
        ReferenceRandom scoreRandom = game.createPresentationRandomCopy();
        List<String> rows = new ArrayList<>();

        GameState.PlayerState[] players = game.getPlayers();
        for (int playerId = players.length - 1; playerId >= 0; playerId--) {
            GameState.PlayerState player = players[playerId];
            if (player.human || !player.alive) {
                continue;
            }
            for (OpponentAI.ScoredAction score : OpponentAI.scoreActions(
                    game, player, scoreRandom)) {
                rows.add(String.join("|", "PROBE", "score",
                        Integer.toString(scenario), Integer.toString(player.id),
                        sourceScoreName(score.action),
                        String.format(Locale.US, "%.9f", score.score)));
            }
        }

        List<OpponentAI.Decision> decisions = OpponentAI.planTurn(
                game, game.createPresentationRandomCopy());
        for (OpponentAI.Decision decision : decisions) {
            rows.add(decisionRow(decision));
        }
        Collections.sort(rows);

        String actual = sha256(String.join("\n", rows));
        assertEquals("Python source trace mismatch:\n" + String.join("\n", rows),
                expectedSha256, actual);
    }

    private String decisionRow(OpponentAI.Decision decision) {
        String action = sourceDecisionName(decision.action);
        boolean usesTargetPlayer = decision.action == GameState.Action.ATTACK
                || decision.action == GameState.Action.PREPARED_ATTACK
                || decision.action == GameState.Action.PROPAGANDA
                || decision.action == GameState.Action.DIPLOMACY;
        boolean usesTargetCity = decision.action == GameState.Action.ATTACK
                || decision.action == GameState.Action.PREPARED_ATTACK
                || decision.action == GameState.Action.PROPAGANDA;
        String targetPlayer = !usesTargetPlayer || decision.targetPlayerId < 0
                ? "undefined" : Integer.toString(decision.targetPlayerId);
        String targetCity = !usesTargetCity || decision.targetCityIndex < 0
                ? "undefined" : Integer.toString(decision.targetPlayerId * 5
                + decision.targetCityIndex);
        String attackWeapon = decision.action == GameState.Action.ATTACK
                ? value(decision.weaponName) : "undefined";
        String carrier = decision.action == GameState.Action.PREPARED_ATTACK
                ? value(decision.weaponName) : "undefined";
        String payload = decision.action == GameState.Action.PREPARED_ATTACK
                ? value(decision.payloadName) : "undefined";
        String diplomacyLeader = decision.action == GameState.Action.DIPLOMACY
                ? targetPlayer : "undefined";
        String diplomacyOption = decision.action == GameState.Action.DIPLOMACY
                ? Integer.toString(decision.diplomacyOption) : "undefined";
        String spyBuilding = value(decision.spyBuilding);
        return String.join("|", "PROBE", "action", Integer.toString(scenario),
                Integer.toString(decision.playerId), action, targetPlayer, targetCity,
                attackWeapon, carrier, payload, diplomacyLeader, diplomacyOption,
                spyBuilding);
    }

    private void assertHardSetupMatchesSource(GameState game) {
        if (scenario != 2) {
            return;
        }
        assertArrayEquals(new int[]{50, 52, 70, 21, 60}, new int[]{
                game.getPlayerState(0).getRegardToward(0),
                game.getPlayerState(1).getRegardToward(0),
                game.getPlayerState(2).getRegardToward(0),
                game.getPlayerState(3).getRegardToward(0),
                game.getPlayerState(4).getRegardToward(0)
        });
        assertArrayEquals(new int[]{73, 47, 80, 95, 105}, cityPopulations(game, 0));
        assertArrayEquals(new int[]{59, 97, 54, 84, 156}, cityPopulations(game, 1));
        assertArrayEquals(new int[]{57, 80, 110, 78, 125}, cityPopulations(game, 2));
        assertArrayEquals(new int[]{66, 110, 67, 96, 111}, cityPopulations(game, 3));
        assertArrayEquals(new int[]{107, 41, 67, 43, 192}, cityPopulations(game, 4));
    }

    private static int[] cityPopulations(GameState game, int playerId) {
        int[] values = new int[5];
        for (int cityIndex = 0; cityIndex < values.length; cityIndex++) {
            values[cityIndex] = game.getPlayerState(playerId).getCity(cityIndex)
                    .populationMillions;
        }
        return values;
    }

    private static String value(String value) {
        return value == null ? "undefined" : value;
    }

    private static String sourceScoreName(GameState.Action action) {
        switch (action) {
            case ATTACK: return "_attack";
            case DEFENSE: return "preCounter";
            case PROPAGANDA: return "propaganda";
            case DIPLOMACY: return "diplomacy";
            case MASS_MEDIA: return "religion";
            case RESOURCES: return "resources";
            case PRODUCTION: return "produce";
            case RESEARCH: return "research";
            case PREPARED_ATTACK: return "preparedAttack";
            default: throw new IllegalArgumentException("Unmapped score action " + action);
        }
    }

    private static String sourceDecisionName(GameState.Action action) {
        switch (action) {
            case ATTACK: return "attack";
            case DEFENSE: return "defense";
            case PROPAGANDA: return "propaganda";
            case DIPLOMACY: return "diplomacy";
            case MASS_MEDIA: return "religion";
            case RESOURCES: return "resources";
            case PRODUCTION: return "production";
            case RESEARCH: return "research";
            case PREPARED_ATTACK: return "preAttack";
            default: throw new IllegalArgumentException("Unmapped decision action " + action);
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            result.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return result.toString();
    }

}
