package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.Assert.*;

/** Synthetic getTarget boundaries from Model.py's duplicate attack route choice. */
public final class SourceDuplicateAttackRouteTest {
    @Test public void preservedDuplicateCanSelectEmptyDirectRouteWithCarrierAvailable() throws Exception {
        Scenario s = new Scenario(0, 1, 0, 0);
        s.actor.getWeapons().get("rocket1").active = true;
        s.actor.getWeapons().get("rocket1").inStock = 1;
        assertTrue(s.plan().isEmpty());
        assertEquals(Arrays.asList(3, 2, 5, 4), s.random.bounds);
    }

    @Test public void preservedDuplicateChoosesRouteEvenWhenBothAreEmpty() throws Exception {
        Scenario direct = new Scenario(0, 1, 0, 0);
        assertTrue(direct.plan().isEmpty());
        assertEquals(Arrays.asList(3, 2, 5, 4), direct.random.bounds);
        Scenario carrier = new Scenario(0, 0, 0);
        assertTrue(carrier.plan().isEmpty());
        assertEquals(Arrays.asList(3, 2, 3), carrier.random.bounds);
        assertEquals(1, carrier.random.zeroWidthDraws);
    }

    @Test public void suppressedDuplicateKeepsOrdinaryEmptyAvailabilityShortcut() throws Exception {
        Scenario s = new Scenario(1);
        assertTrue(s.plan().isEmpty());
        assertEquals(Collections.singletonList(3), s.random.bounds);
        assertEquals(0, s.random.zeroWidthDraws);
    }

    private static final class Scenario {
        final GameState game = new GameState(1, 0L);
        final GameState.PlayerState actor = game.getPlayerState(1);
        final SequenceRandom random;
        Scenario(int... values) {
            random = new SequenceRandom(values);
            for (GameState.PlayerState player : game.getPlayers()) {
                player.alive = player.id == 0 || player.id == 1;
                for (GameState.WeaponState weapon : player.getWeapons().values()) weapon.inStock = 0;
            }
            game.getHuman().moneyMillions = 0;
        }
        @SuppressWarnings("unchecked") List<OpponentAI.Decision> plan() throws Exception {
            Method method = OpponentAI.class.getDeclaredMethod("planSourceAttack", GameState.class,
                    GameState.PlayerState.class, Random.class, boolean.class, Map.class);
            method.setAccessible(true);
            return (List<OpponentAI.Decision>) method.invoke(null, game, actor, random, true,
                    Collections.<String, Integer>emptyMap());
        }
    }
    private static final class SequenceRandom extends Random {
        final int[] values;
        int index;
        int zeroWidthDraws;
        @Override public double nextDouble() { zeroWidthDraws++; return 0.25d; }
        final List<Integer> bounds = new ArrayList<>();
        SequenceRandom(int... values) { this.values = values; }
        @Override public int nextInt(int bound) {
            bounds.add(bound);
            if (index == values.length) throw new AssertionError("Unexpected RNG draw: " + bound);
            int value = values[index++];
            if (value < 0 || value >= bound) throw new AssertionError("Invalid scripted draw");
            return value;
        }
    }
}
