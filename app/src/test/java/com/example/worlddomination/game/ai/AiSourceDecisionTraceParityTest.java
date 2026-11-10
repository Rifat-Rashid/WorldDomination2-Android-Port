package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Checks native AI scoring and target selection against a recovered-source game session. */
@RunWith(Parameterized.class)
public final class AiSourceDecisionTraceParityTest {
    private static final String[] SOURCE_TRACES = {
            "42a32adab295f7b223ebede57abee1eec36064bc69e5447aac7f780a253b023c",
            "475d3dc2e6d1167c293f46f99250f2f97469834f0c84f9a6ce87eb980f3ff0d5",
            "55ab683137aa3ca9a43c4c2050f7bbfe61cb11308ab73fc58dd6c6d64226bc5f",
            "177207e87824b71472cbe8445cf013b9b0555ffce18b88324d2b2aaba23f2d27"
    };
    private static String actualRows;

    private final int scenario;
    private final int seed;
    private final int difficulty;
    private final int[] rivalIds;

    public AiSourceDecisionTraceParityTest(int scenario, int seed, int difficulty,
                                           int[] rivalIds) {
        this.scenario = scenario;
        this.seed = seed;
        this.difficulty = difficulty;
        this.rivalIds = rivalIds;
    }

    @Parameterized.Parameters(name = "source AI trace {0}, seed {1}, difficulty {2}")
    public static Collection<Object[]> scenarios() {
        List<Object[]> scenarios = new ArrayList<>();
        scenarios.add(new Object[]{0, 0, 0, new int[]{0, 1, 2, 3}});
        scenarios.add(new Object[]{1, 7, 1, new int[]{2, 9}});
        scenarios.add(new Object[]{2, 42, 2, new int[]{4}});
        scenarios.add(new Object[]{3, 1337, 1, new int[]{0, 9, 12, 18}});
        return scenarios;
    }

    @Test
    public void gameStartScoresAndActionsMatchRecoveredSource() throws Exception {
        String digest = sourceSessionDigest(scenario, seed, difficulty, rivalIds);
        assertEquals(actualRows, SOURCE_TRACES[scenario], digest);
    }

    private static String sourceSessionDigest(int scenario, int seed, int difficulty,
                                              int[] rivalIds) throws Exception {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(difficulty);
        for (int rivalId : rivalIds) {
            assertTrue("Source setup accepts each selected rival", setup.toggleOpponent(rivalId));
        }
        GameState state = new GameState(setup);
        Random gameRandom = (Random) getField(GameState.class, state, "random");
        ReferenceRandom scoreRandom = ((ReferenceRandom) gameRandom).copy();
        GameState.PlayerState[] players = state.getPlayers();

        List<String> rows = new ArrayList<>();
        // The recovered Model.doTurn scores array entries in reverse key order.
        for (int playerId = players.length - 1; playerId >= 1; playerId--) {
            GameState.PlayerState player = players[playerId];
            if (!player.alive) {
                continue;
            }
            for (OpponentAI.ScoredAction score : OpponentAI.scoreActions(
                    state, player, scoreRandom)) {
                rows.add(String.join("|", "PROBE", "score", Integer.toString(scenario),
                        Integer.toString(player.id), scoreAction(score.action),
                        String.format(Locale.US, "%.9f", score.score)));
            }
        }

        @SuppressWarnings("unchecked")
        List<OpponentAI.Decision>[] decisions = new List[players.length];
        for (int playerId = 1; playerId < players.length; playerId++) {
            decisions[playerId] = new ArrayList<>();
        }
        for (OpponentAI.Decision decision : OpponentAI.planTurn(state, gameRandom)) {
            decisions[decision.playerId].add(decision);
        }
        for (int playerId = 1; playerId < players.length; playerId++) {
            if (!players[playerId].alive) {
                continue;
            }
            for (int slot = 0; slot < 2; slot++) {
                OpponentAI.Decision decision = slot < decisions[playerId].size()
                        ? decisions[playerId].get(slot) : null;
                rows.add(String.join("|", "PROBE", "action", Integer.toString(scenario),
                        Integer.toString(playerId), decisionAction(decision),
                        targetPlayer(decision), targetCity(state, decision),
                        attackWeapon(decision), carrier(decision), payload(decision),
                        diplomacyLeader(decision), diplomacyOption(decision),
                        spyBuilding(decision)));
            }
        }

        Collections.sort(rows);
        actualRows = String.join("\n", rows);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(actualRows.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hashed.length * 2);
        for (byte value : hashed) {
            hex.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static String scoreAction(GameState.Action action) {
        if (action == GameState.Action.ATTACK) {
            return "_attack";
        }
        if (action == GameState.Action.DEFENSE) {
            return "preCounter";
        }
        if (action == GameState.Action.PREPARED_ATTACK) {
            return "preAttack";
        }
        if (action == GameState.Action.PROPAGANDA) {
            return "propaganda";
        }
        if (action == GameState.Action.DIPLOMACY) {
            return "diplomacy";
        }
        if (action == GameState.Action.MASS_MEDIA) {
            return "religion";
        }
        if (action == GameState.Action.RESOURCES) {
            return "resources";
        }
        if (action == GameState.Action.PRODUCTION) {
            return "produce";
        }
        if (action == GameState.Action.RESEARCH) {
            return "research";
        }
        throw new AssertionError("Unmapped scored action: " + action);
    }

    private static String decisionAction(OpponentAI.Decision decision) {
        if (decision == null) {
            return "undefined";
        }
        if (decision.action == GameState.Action.PRODUCTION) {
            return "production";
        }
        return decision.action == GameState.Action.ATTACK ? "attack"
                : decision.action == GameState.Action.PREPARED_ATTACK ? "preAttack"
                : decision.action == GameState.Action.DEFENSE ? "preCounter"
                : decision.action == GameState.Action.PROPAGANDA ? "propaganda"
                : decision.action == GameState.Action.DIPLOMACY ? "diplomacy"
                : decision.action == GameState.Action.MASS_MEDIA ? "religion"
                : decision.action == GameState.Action.RESOURCES ? "resources"
                : decision.action == GameState.Action.RESEARCH ? "research"
                : "undefined";
    }

    private static String targetPlayer(OpponentAI.Decision decision) {
        return decision == null || decision.action == GameState.Action.DIPLOMACY
                || decision.targetPlayerId < 0 ? "undefined"
                : Integer.toString(decision.targetPlayerId);
    }

    private static String targetCity(GameState state, OpponentAI.Decision decision) {
        if (decision == null || decision.targetPlayerId < 0 || decision.targetCityIndex < 0) {
            return "undefined";
        }
        GameState.PlayerState target = state.getPlayerState(decision.targetPlayerId);
        return Integer.toString(target.getCity(decision.targetCityIndex).id);
    }

    private static String attackWeapon(OpponentAI.Decision decision) {
        return decision != null && decision.action != GameState.Action.PREPARED_ATTACK
                && decision.weaponName != null ? decision.weaponName : "undefined";
    }

    private static String carrier(OpponentAI.Decision decision) {
        return decision != null && decision.action == GameState.Action.PREPARED_ATTACK
                && decision.weaponName != null ? decision.weaponName : "undefined";
    }

    private static String payload(OpponentAI.Decision decision) {
        return decision != null && decision.action == GameState.Action.PREPARED_ATTACK
                && decision.payloadName != null ? decision.payloadName : "undefined";
    }

    private static String diplomacyLeader(OpponentAI.Decision decision) {
        return decision != null && decision.action == GameState.Action.DIPLOMACY
                ? Integer.toString(decision.targetPlayerId) : "undefined";
    }

    private static String diplomacyOption(OpponentAI.Decision decision) {
        return decision != null && decision.action == GameState.Action.DIPLOMACY
                ? Integer.toString(decision.diplomacyOption) : "undefined";
    }

    private static String spyBuilding(OpponentAI.Decision decision) {
        return decision == null || decision.spyBuilding == null ? "undefined"
                : decision.spyBuilding;
    }

    private static Object getField(Class<?> owner, Object target, String name)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
