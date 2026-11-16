package com.example.worlddomination.game.simulation;

import org.junit.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

/** Synthetic production visits exercising source Presenter.py:777-785. */
public final class SourceEasySpyProductionTest {
    @Test public void easyAiScoresButDoesNotBuySpy3OrSpy4() throws Exception {
        for (String high : new String[]{"spy3", "spy4"}) {
            Scenario s = new Scenario(0, false, high);
            assertEquals(0L, s.resolve());
            assertEquals(0, s.high.inStock);
            assertEquals(s.budget() / 5, s.low.inStock);
            assertEquals(100 - s.budget(), s.player.moneyMillions);
            assertEquals(0, s.player.score);
            List<Integer> expectedBounds = new ArrayList<>();
            for (int i = 0; i < 1 + s.budget() / 5; i++) {
                expectedBounds.add(2); expectedBounds.add(51);
            }
            assertEquals(expectedBounds, s.random.bounds);
        }
    }

    @Test public void mediumAiPurchasesBothLevelsNormally() throws Exception {
        for (String high : new String[]{"spy3", "spy4"}) {
            Scenario s = new Scenario(1, false, high);
            assertEquals(0L, s.resolve());
            assertEquals(1, s.high.inStock);
            assertEquals(1, s.low.inStock);
            assertEquals(100 - s.budget(), s.player.moneyMillions);
            assertEquals(2001L * (s.high.definition.level + 1), s.player.score);
            assertEquals(Arrays.asList(2, 51, 2, 51), s.random.bounds);
        }
    }

    @Test public void easyHumanPurchasesHighLevelSpyNormally() throws Exception {
        for (String high : new String[]{"spy3", "spy4"}) {
            Scenario s = new Scenario(0, true, high);
            assertEquals(0L, s.resolve());
            assertEquals(1, s.high.inStock);
            assertEquals(1, s.low.inStock);
            assertEquals(100 - s.budget(), s.player.moneyMillions);
            assertEquals(0, s.player.score);
            assertEquals(Arrays.asList(2, 51, 2, 51), s.random.bounds);
        }
    }

    private static final class Scenario {
        final PickHighThenLow random = new PickHighThenLow();
        final GameState game;
        final GameState.PlayerState player;
        final GameState.WeaponState low, high;
        Scenario(int difficulty, boolean human, String highName) {
            game = new GameState(difficulty, random);
            player = game.getPlayerState(human ? 0 : 1);
            for (GameState.WeaponState weapon : player.getWeapons().values()) {
                if ("spy".equals(weapon.definition.type)) { weapon.active = false; weapon.inStock = 0; }
            }
            low = player.getWeapons().get("spy1");
            high = player.getWeapons().get(highName);
            low.active = true; high.active = true;
            player.moneyMillions = 100;
            random.recording = true;
        }
        int budget() { return high.definition.costMillions + 5; }
        long resolve() throws Exception {
            Method method = GameState.class.getDeclaredMethod("resolveProductionInvestment",
                    GameState.PlayerState.class, String.class, double.class, boolean.class);
            method.setAccessible(true);
            return (Long) method.invoke(game, player, "spy", (double) budget(), false);
        }
    }

    private static final class PickHighThenLow extends Random {
        boolean recording;
        final List<Integer> bounds = new ArrayList<>();
        @Override public int nextInt(int bound) {
            if (!recording) return 0;
            bounds.add(bound);
            return bounds.size() == 1 ? 1 : 0;
        }
        @Override public double nextDouble() { return 0; }
    }
}
