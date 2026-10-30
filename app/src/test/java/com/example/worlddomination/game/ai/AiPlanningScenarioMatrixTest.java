package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Exercises deterministic and structurally valid AI plans across a scenario matrix. */
@RunWith(Parameterized.class)
public final class AiPlanningScenarioMatrixTest {
    private final int scenario;
    private final int difficulty;
    private final int phase;

    public AiPlanningScenarioMatrixTest(int scenario, int difficulty, int phase) {
        this.scenario = scenario;
        this.difficulty = difficulty;
        this.phase = phase;
    }

    @Parameterized.Parameters(name = "scenario {0}, difficulty {1}, phase {2}")
    public static Collection<Object[]> scenarios() {
        List<Object[]> scenarios = new ArrayList<>();
        for (int scenario = 0; scenario < 36; scenario++) {
            scenarios.add(new Object[]{scenario, scenario % 3, scenario / 3});
        }
        return scenarios;
    }

    @Test
    public void planningIsDeterministicAndProducesValidActions() throws Exception {
        String firstRun = planAndValidate();
        String secondRun = planAndValidate();
        assertEquals("The same seed and scenario must produce the same AI trace",
                firstRun, secondRun);
    }

    private String planAndValidate() throws Exception {
        LcgRandom random = new LcgRandom(scenario + 1L);
        GameState game = new GameState(difficulty, random);
        configure(game);
        StringBuilder trace = new StringBuilder();

        for (GameState.PlayerState player : game.getPlayers()) {
            if (player.id == 0 || !player.alive) {
                continue;
            }
            LcgRandom scoreRandom = random.copy();
            List<OpponentAI.ScoredAction> scores = OpponentAI.scoreActions(
                    game, player, scoreRandom);
            assertEquals("Each opponent should score the full action set", 8, scores.size());
            Set<GameState.Action> uniqueActions = new HashSet<>();
            for (OpponentAI.ScoredAction score : scores) {
                assertTrue("Scores must be finite", Double.isFinite(score.score));
                assertTrue("A scoring action must appear once", uniqueActions.add(score.action));
                trace.append("score|").append(player.id).append('|')
                        .append(score.action.name()).append('|')
                        .append(String.format(Locale.US, "%.9f", score.score)).append('\n');
            }
            assertEquals(8, uniqueActions.size());

            int availableBudget = player.turnMoneyMillions;
            List<OpponentAI.Decision> decisions = OpponentAI.plan(game, player, random);
            assertTrue("An opponent can reserve at most two actions", decisions.size() <= 2);
            assertTrue("Planning must not overdraw the action budget",
                    player.turnMoneyMillions >= 0);
            assertTrue("Planning cannot increase the available action budget",
                    player.turnMoneyMillions <= availableBudget);
            for (OpponentAI.Decision decision : decisions) {
                validateDecision(game, player, decision);
                trace.append("action|").append(player.id).append('|')
                        .append(decision.action.name()).append('|')
                        .append(decision.targetPlayerId).append('|')
                        .append(decision.targetCityIndex).append('|')
                        .append(decision.weaponName).append('|')
                        .append(decision.payloadName).append('|')
                        .append(decision.diplomacyOption).append('|')
                        .append(decision.spyBuilding).append('|')
                        .append(decision.reservedMillions).append('\n');
            }
        }
        trace.append("rng|").append(random.count).append('|').append(random.state);
        return trace.toString();
    }

    private void validateDecision(GameState game, GameState.PlayerState actor,
                                  OpponentAI.Decision decision) {
        assertEquals("The planner must not issue orders for another nation",
                actor.id, decision.playerId);
        assertTrue("Reserved spending cannot be negative", decision.reservedMillions >= 0);
        assertNotNull("Every decision has an action", decision.action);

        if (decision.targetPlayerId >= 0) {
            GameState.PlayerState target = game.getPlayerState(decision.targetPlayerId);
            assertTrue("AI decisions target living opponents", target.alive);
            assertFalse("AI decisions cannot target themselves", target.id == actor.id);
            if (decision.targetCityIndex >= 0) {
                CityState city = target.getCity(decision.targetCityIndex);
                assertNotNull("Target city index must be valid", city);
                assertTrue("Selected target cities must have population",
                        city.populationMillions > 0);
            }
        }

        if (decision.action == GameState.Action.ATTACK) {
            assertTrue("Direct attacks need a living target city",
                    decision.targetPlayerId >= 0 && decision.targetCityIndex >= 0);
            assertNotNull("Direct attacks need a weapon", decision.weaponName);
        } else if (decision.action == GameState.Action.PROPAGANDA) {
            assertTrue("Propaganda needs a living target city",
                    decision.targetPlayerId >= 0 && decision.targetCityIndex >= 0);
        } else if (decision.action == GameState.Action.DIPLOMACY) {
            assertTrue("Diplomacy needs a valid option", decision.diplomacyOption >= 0
                    && decision.diplomacyOption < 6);
        } else if (decision.action == GameState.Action.PREPARED_ATTACK) {
            assertNotNull("Prepared attacks need a carrier", decision.weaponName);
            if (!decision.prepareCarrier) {
                assertNotNull("Launched prepared attacks need a payload", decision.payloadName);
                assertTrue("Launched prepared attacks need a target city",
                        decision.targetPlayerId >= 0 && decision.targetCityIndex >= 0);
            }
        }
    }

    private void configure(GameState game) throws Exception {
        setField(GameState.class, game, "turn", phase * 5);
        for (GameState.PlayerState player : game.getPlayers()) {
            if (player.id == 0) {
                continue;
            }
            if (phase > 0) {
                player.moneyMillions = phase * 30 + player.id * 7;
                player.turnMoneyMillions = player.moneyMillions;
                player.religion = (phase * 17 + player.id * 11) % 101;
                double[] regard = (double[]) getField(
                        GameState.PlayerState.class, player, "regardToward");
                for (int targetId = 0; targetId < regard.length; targetId++) {
                    regard[targetId] = (phase * 13 + player.id * 7 + targetId * 11) % 101;
                }
            }
            if (phase >= 4) {
                @SuppressWarnings("unchecked")
                Set<String> invented = (Set<String>) getField(
                        GameState.PlayerState.class, player, "inventedTechnologies");
                for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
                    if (technology.level < 4 && technology.group != 7) {
                        invented.add(technology.name);
                    }
                }
                for (GameState.WeaponState weapon : player.getWeapons().values()) {
                    if (weapon.definition.level == 1) {
                        weapon.inStock = 3;
                    } else if (weapon.definition.level == 2) {
                        weapon.inStock = 2;
                    }
                    weapon.active = invented.contains(weapon.definition.technology);
                }
            }
            if (phase == 7 || phase == 8) {
                setField(GameState.PlayerState.class, player, "specialPerson", true);
            } else if (phase == 9 || phase == 10) {
                setField(GameState.PlayerState.class, player, "suicidePerson", true);
            }
        }
    }

    private static Object getField(Class<?> owner, Object target, String name)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Test-only version of the reference fixture's controlled 32-bit LCG. */
    private static final class LcgRandom extends Random {
        private static final long serialVersionUID = 1L;
        private long state;
        private int count;

        LcgRandom(long seed) {
            state = seed;
        }

        private LcgRandom(long state, int count) {
            this.state = state;
            this.count = count;
        }

        LcgRandom copy() {
            return new LcgRandom(state, count);
        }

        @Override
        public double nextDouble() {
            state = (state * 1664525L + 1013904223L) & 0xffffffffL;
            count++;
            return state / 4294967296.0;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return (int) (nextDouble() * bound);
        }

        @Override
        public boolean nextBoolean() {
            return nextInt(2) != 0;
        }
    }
}
