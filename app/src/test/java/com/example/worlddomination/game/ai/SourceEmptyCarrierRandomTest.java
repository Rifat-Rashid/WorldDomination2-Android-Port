package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.Assert.*;

/** Source Model.getTarget and WeaponStock.randomAvailableCarrier draw boundaries. */
public final class SourceEmptyCarrierRandomTest {
    @Test public void retainedDuplicateEmptyCarrierConsumesZeroWidthDraw() throws Exception {
        Scenario s = new Scenario();
        ReferenceRandom random = new ReferenceRandom(4);
        assertTrue(s.plan(random).isEmpty());
        // Source: suppression, route, unused [1,3], empty [0,-1], then next.
        assertEquals(0.06651509567958991, random.nextDouble(), 0d);
    }
    @Test public void suppressedDuplicateWithoutWeaponsConsumesOnlySuppression() throws Exception {
        Scenario s = new Scenario();
        ReferenceRandom random = new ReferenceRandom(0);
        assertTrue(s.plan(random).isEmpty());
        assertEquals(0.7579544029403025, random.nextDouble(), 0d);
    }
    @Test public void retainedDuplicateWithCarrierConsumesNormalTypeDraw() throws Exception {
        Scenario s = new Scenario();
        s.actor.getWeapons().get("rocket1").active = true;
        s.actor.getWeapons().get("rocket1").inStock = 1;
        ReferenceRandom random = new ReferenceRandom(4);
        List<OpponentAI.Decision> actions = s.plan(random);
        assertEquals(1, actions.size());
        assertEquals(GameState.Action.PREPARED_ATTACK, actions.get(0).action);
        assertEquals("rocket1", actions.get(0).weaponName);
        assertEquals(0.06651509567958991, random.nextDouble(), 0d);
    }
    private static final class Scenario {
        final GameState game = new GameState(1, 0L);
        final GameState.PlayerState actor = game.getPlayerState(1);
        Scenario() {
            for (GameState.PlayerState p : game.getPlayers()) {
                p.alive = p.id == 0 || p.id == 1;
                for (GameState.WeaponState weapon : p.getWeapons().values()) weapon.inStock = 0;
            }
            game.getHuman().moneyMillions = 0;
        }
        @SuppressWarnings("unchecked") List<OpponentAI.Decision> plan(Random random) throws Exception {
            Method m = OpponentAI.class.getDeclaredMethod("planSourceAttack", GameState.class,
                    GameState.PlayerState.class, Random.class, boolean.class, Map.class);
            m.setAccessible(true);
            return (List<OpponentAI.Decision>) m.invoke(null, game, actor, random, true,
                    Collections.<String, Integer>emptyMap());
        }
    }
}
