package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

/** Synthetic slot boundaries from Model.getTarget ship logic; not a UI campaign. */
public final class SourceShipSlotOverwriteTest {
    @Test public void readyShipInSlotTwoOverwritesResourcesEvenWithoutSoldiers() throws Exception {
        Scenario s = new Scenario();
        List<OpponentAI.Decision> result = s.plan(GameState.Action.RESOURCES, GameState.Action.ATTACK);
        assertEquals(2, result.size());
        assertEquals(GameState.Action.ATTACK, result.get(0).action);
        assertNull(result.get(0).weaponName);
        assertEquals(0, result.get(0).targetPlayerId);
        assertEquals(4, result.get(0).targetCityIndex);
        assertEquals(GameState.Action.PREPARED_ATTACK, result.get(1).action);
        assertEquals("ship", result.get(1).weaponName);
        assertEquals(3, result.get(1).shipStrikes.size());
        assertEquals(Arrays.asList(5, 5, 5), s.random.bounds);
    }

    @Test public void readyShipInSlotTwoStagesStrongestAvailableSoldier() throws Exception {
        Scenario s = new Scenario();
        s.actor.getWeapons().get("soldier1").active = true;
        s.actor.getWeapons().get("soldier1").inStock = 1;
        s.actor.getWeapons().get("soldier2").active = true;
        s.actor.getWeapons().get("soldier2").inStock = 1;
        List<OpponentAI.Decision> result = s.plan(GameState.Action.RESOURCES, GameState.Action.ATTACK);
        assertEquals(2, result.size());
        assertEquals(GameState.Action.ATTACK, result.get(0).action);
        assertEquals("soldier2", result.get(0).weaponName);
        assertEquals(4, result.get(0).targetCityIndex);
        assertEquals(GameState.Action.PREPARED_ATTACK, result.get(1).action);
        assertEquals(Arrays.asList(5, 5, 5), s.random.bounds);
    }

    @Test public void readyShipInSlotOneKeepsItsRankedCompanion() throws Exception {
        Scenario s = new Scenario();
        List<OpponentAI.Decision> result = s.plan(GameState.Action.ATTACK, GameState.Action.MASS_MEDIA);
        assertEquals(2, result.size());
        assertEquals(GameState.Action.PREPARED_ATTACK, result.get(0).action);
        assertEquals("ship", result.get(0).weaponName);
        assertEquals(GameState.Action.MASS_MEDIA, result.get(1).action);
        assertEquals(Arrays.asList(5, 5, 5), s.random.bounds);
    }

    private static final class Scenario {
        final GameState game = new GameState(1, 0L);
        final GameState.PlayerState actor = game.getPlayerState(1);
        final ZeroRandom random = new ZeroRandom();
        Scenario() throws Exception {
            for (GameState.PlayerState player : game.getPlayers()) {
                player.alive = player.id == 0 || player.id == 1;
                for (GameState.WeaponState weapon : player.getWeapons().values()) weapon.inStock = 0;
            }
            game.getHuman().moneyMillions = 0;
            GameState.WeaponState payload = actor.getWeapons().get("warhead1");
            payload.active = true; payload.inStock = 3;
            actor.getWeapons().get("ship").active = true;
            Field field = GameState.PlayerState.class.getDeclaredField("preparedCarrierName");
            field.setAccessible(true); field.set(actor, "ship");
        }
        List<OpponentAI.Decision> plan(GameState.Action first, GameState.Action second) {
            return OpponentAI.planScoredActions(game, actor, random, Arrays.asList(
                    new OpponentAI.ScoredAction(first, 100),
                    new OpponentAI.ScoredAction(second, 90)));
        }
    }
    private static final class ZeroRandom extends Random {
        final List<Integer> bounds = new ArrayList<>();
        @Override public int nextInt(int bound) { bounds.add(bound); return 0; }
    }
}
