package com.example.worlddomination.game.simulation;

import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.Assert.*;

/** Synthetic source undefined-weapon attack followed by a ship casualty roll. */
public final class SourceNullWeaponAttackTest {
    @Test public void nullWeaponConsumesDrawButDefersRegardWithoutDamageOrScore()
            throws Exception {
        SequenceRandom random = new SequenceRandom();
        GameState game = new GameState(1, random);
        GameState.PlayerState attacker = game.getPlayerState(1);
        GameState.PlayerState defender = game.getPlayerState(2);
        Field special = GameState.PlayerState.class.getDeclaredField("specialPerson");
        special.setAccessible(true);
        special.setBoolean(defender, false);
        int population = defender.getCity(0).populationMillions;
        long attackerScore = attacker.score, defenderScore = defender.score;
        double attackerRegard = attacker.getRegardTowardExact(defender.id);
        double defenderRegard = defender.getRegardTowardExact(attacker.id);

        Class<?> orderClass = Class.forName(GameState.class.getName() + "$ActionOrder");
        Constructor<?> constructor = orderClass.getDeclaredConstructor(int.class, GameState.Action.class,
                Map.class, List.class, int.class, int.class, int.class, String.class, String.class,
                boolean.class, String.class, int.class);
        constructor.setAccessible(true);
        Object order = constructor.newInstance(attacker.id, GameState.Action.ATTACK,
                Collections.emptyMap(), Collections.emptyList(), 0, defender.id, 0,
                null, null, false, null, 0);
        Class<?> changesClass = Class.forName(GameState.class.getName() + "$TurnPopulationChanges");
        Constructor<?> changesConstructor = changesClass.getDeclaredConstructor();
        changesConstructor.setAccessible(true);
        Object changes = changesConstructor.newInstance();
        Method method = GameState.class.getDeclaredMethod("resolveAttack", orderClass, List.class,
                changesClass, long[].class, Map.class, List.class);
        method.setAccessible(true);
        random.recording = true;
        method.invoke(game, order, Collections.emptyList(), changes, new long[5],
                Collections.emptyMap(), new ArrayList<>());

        assertEquals(1, random.draws);
        assertEquals(attackerRegard, attacker.getRegardTowardExact(defender.id), 0d);
        assertEquals(defenderRegard, defender.getRegardTowardExact(attacker.id), 0d);
        assertEquals(population, defender.getCity(0).populationMillions);
        assertEquals(attackerScore, attacker.score);
        assertEquals(defenderScore, defender.score);
        assertTrue(game.getLastTurnCombatEvents().isEmpty());
        Method casualties = changesClass.getDeclaredMethod("battleCasualtiesFor", CityState.class);
        casualties.setAccessible(true);
        assertEquals(0, ((Integer) casualties.invoke(changes, defender.getCity(0))).intValue());
        // Source T21 draw33 is consumed by the null attack; draw34 then gives 55.
        assertEquals(55, GameState.sourceShipStrikeCasualties(90, random));
        assertEquals(2, random.draws);
    }

    @Test public void settlementAppliesMutualAndAliveObserverRegard() throws Exception {
        GameState baseline = playSynthetic(false, -1);
        GameState attacked = playSynthetic(true, -1);
        assertEquals(baseline.getPlayerState(1).getRegardTowardExact(2) - 40,
                attacked.getPlayerState(1).getRegardTowardExact(2), 0d);
        // The special victim also lowers every active player's regard toward the attacker.
        assertEquals(baseline.getPlayerState(2).getRegardTowardExact(1) - 47,
                attacked.getPlayerState(2).getRegardTowardExact(1), 0d);
        assertEquals(baseline.getPlayerState(4).getRegardTowardExact(1) - 7,
                attacked.getPlayerState(4).getRegardTowardExact(1), 0d);
    }

    @Test public void settlementExcludesObserverKilledInSameBattle() throws Exception {
        GameState baseline = playSynthetic(false, 4);
        GameState attacked = playSynthetic(true, 4);
        assertFalse(attacked.getPlayerState(4).alive);
        assertEquals(baseline.getPlayerState(4).getRegardTowardExact(1),
                attacked.getPlayerState(4).getRegardTowardExact(1), 0d);
        assertEquals(baseline.getPlayerState(3).getRegardTowardExact(1) - 7,
                attacked.getPlayerState(3).getRegardTowardExact(1), 0d);
    }

    @Test public void humanDefeatSkipsNullAttackRegardSettlement() throws Exception {
        GameState baseline = playSynthetic(false, 0);
        GameState attacked = playSynthetic(true, 0);
        assertFalse(attacked.getHuman().alive);
        assertEquals(baseline.getPlayerState(1).getRegardTowardExact(2),
                attacked.getPlayerState(1).getRegardTowardExact(2), 0d);
        assertEquals(baseline.getPlayerState(2).getRegardTowardExact(1),
                attacked.getPlayerState(2).getRegardTowardExact(1), 0d);
        assertEquals(baseline.getPlayerState(4).getRegardTowardExact(1),
                attacked.getPlayerState(4).getRegardTowardExact(1), 0d);
    }

    // Synthetic actions isolate result settlement; no claim of a legal AI schedule.
    private static GameState playSynthetic(boolean nullAttack, int killedPlayer) throws Exception {
        GameState game = new GameState(1, new ConstantRandom(), false);
        Field special = GameState.PlayerState.class.getDeclaredField("specialPerson");
        special.setAccessible(true);
        special.setBoolean(game.getPlayerState(2), true);
        if (killedPlayer >= 0) {
            GameState.PlayerState victim = game.getPlayerState(killedPlayer);
            for (CityState city : victim.getCities()) city.populationMillions = 0;
            victim.getCity(0).populationMillions = 1;
            victim.populationMillions = 1;
        }
        Class<?> orderClass = Class.forName(GameState.class.getName() + "$ActionOrder");
        Constructor<?> ctor = orderClass.getDeclaredConstructor(int.class, GameState.Action.class,
                Map.class, List.class, int.class, int.class, int.class, String.class, String.class,
                boolean.class, String.class, int.class);
        ctor.setAccessible(true);
        Field field = GameState.class.getDeclaredField("queuedActions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") List<Object> actions = (List<Object>) field.get(game);
        actions.add(ctor.newInstance(1, nullAttack ? GameState.Action.ATTACK : GameState.Action.SKIP,
                Collections.emptyMap(), Collections.emptyList(), 0, 2, 0, null, null, false, null, 0));
        actions.add(ctor.newInstance(3, killedPlayer < 0 ? GameState.Action.SKIP : GameState.Action.ATTACK,
                Collections.emptyMap(), Collections.emptyList(), 0, killedPlayer < 0 ? 0 : killedPlayer,
                0, killedPlayer < 0 ? null : "soldier1", null, false, null, 0));
        game.confirmTurn();
        return game;
    }

    private static final class ConstantRandom extends Random {
        @Override public double nextDouble() { return .5; }
        @Override public int nextInt(int bound) { return 0; }
    }

    private static final class SequenceRandom extends Random {
        boolean recording;
        int draws;
        @Override public double nextDouble() {
            if (!recording) return .5;
            return draws++ == 0 ? .8044456182179193 : .2371657760392406;
        }
    }
}
