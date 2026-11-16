package com.example.worlddomination.game.ai;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

/** Synthetic source WeaponStock getter and spy decision boundary; not a campaign trace. */
public final class SourceSpyStockLevelTest {
    @Test public void allCapturedStockCasesMatchEverySourceLevel() throws Exception {
        JsonValue cases = fixture().get("cases");
        assertEquals(5, cases.size);
        for (JsonValue captured : cases) {
            Scenario s = new Scenario(captured);
            JsonValue totals = captured.get("sourceTotals");
            for (int level = 1; level <= 4; level++) {
                assertEquals(captured.getString("name") + " level " + level,
                        totals.getInt(level - 1), stock(s.target, level));
            }
        }
    }

    @Test public void eightAndNineRegularWeaponsDoNotQualifyButTenDo() throws Exception {
        JsonValue cases = fixture().get("cases");
        for (int i = 0; i < 3; i++) {
            Scenario s = new Scenario(cases.get(i));
            CountingRandom random = new CountingRandom();
            Object target = spyTarget(s, random);
            assertEquals("cash-alert threshold still draws once", 1, random.draws);
            if (i < 2) assertNull("anti counters cannot cross the ten-weapon threshold", target);
            else {
                assertNotNull(target);
                assertEquals(2, fieldInt(target, "playerId"));
                assertEquals(1, fieldInt(target, "spyLevel"));
            }
        }
    }

    @Test public void highHumanMoneyStillTriggersIndependentCashAlert() throws Exception {
        Scenario s = new Scenario(fixture().get("cases").get(0));
        s.game.getHuman().moneyMillions = 10000;
        CountingRandom random = new CountingRandom();
        Object selected = spyTarget(s, random);
        assertNotNull(selected);
        assertEquals(0, fieldInt(selected, "playerId"));
        assertEquals(1, fieldInt(selected, "spyLevel"));
        assertEquals(1, random.draws);
    }

    @Test public void inactiveRegularWeaponsAndSpecialLoadRemainCounted() throws Exception {
        JsonValue cases = fixture().get("cases");
        Scenario inactive = new Scenario(cases.get(3));
        assertFalse(inactive.target.getWeapons().get("warhead1").active);
        assertEquals(8, stock(inactive.target, 1));
        Scenario levels = new Scenario(cases.get(4));
        assertFalse(levels.target.getWeapons().get("specialLoad").active);
        int[] expected = {2, 3, 4, 12};
        for (int i = 1; i <= 4; i++) assertEquals(expected[i - 1], stock(levels.target, i));
    }

    @Test public void capturedFixtureAndProvenanceAreBytePinned() throws Exception {
        assertSha("source-spy-stock-level.json", "b536b5d2506384b7695b79c01fefd587f4868805571e60e504e306f2ff3e0457");
        assertSha("source-spy-stock-level-provenance.json", "98ee4c3e755d0e32f5b8ff51a78d3410bcc5650e66e7f58e30a8e93d5fa11c77");
    }

    @Test public void emptySoldierChoicesStillDrawCityAndIndexBeforeNoSpyTarget() throws Exception {
        Scenario s = new Scenario(fixture().get("cases").get(0));
        CountingRandom random = new CountingRandom();
        assertNull(directAttack(s, random));
        assertEquals(Arrays.asList(5, 4, 91), random.bounds);
    }

    @Test public void emptySoldierChoicesStillDrawBeforeSelectingEligibleSpy() throws Exception {
        Scenario s = new Scenario(fixture().get("cases").get(2));
        CountingRandom random = new CountingRandom();
        OpponentAI.Decision decision = directAttack(s, random);
        assertNotNull(decision);
        assertEquals("spy1", decision.weaponName);
        assertEquals(2, decision.targetPlayerId);
        assertEquals(4, decision.targetCityIndex);
        assertEquals("spyCenter", decision.spyBuilding);
        assertEquals(Arrays.asList(5, 4, 91, 5, 5), random.bounds);
    }

    @Test public void rejectedLeadingAttackRetriesExactlyTwiceBeforeOtherActions() throws Exception {
        Scenario s = new Scenario(fixture().get("cases").get(0));
        CountingRandom random = new CountingRandom();
        List<OpponentAI.ScoredAction> scores = Arrays.asList(
                new OpponentAI.ScoredAction(GameState.Action.ATTACK, 100),
                new OpponentAI.ScoredAction(GameState.Action.MASS_MEDIA, 90),
                new OpponentAI.ScoredAction(GameState.Action.RESOURCES, 80));
        List<OpponentAI.Decision> decisions = OpponentAI.planScoredActions(
                s.game, s.actor, random, scores);
        assertEquals(2, decisions.size());
        assertEquals(GameState.Action.MASS_MEDIA, decisions.get(0).action);
        assertEquals(GameState.Action.RESOURCES, decisions.get(1).action);
        assertEquals(Arrays.asList(5, 4, 91, 5, 4, 91), random.bounds);
    }

    @Test public void rejectedAttackPreservesCompanionResetOnRetry() throws Exception {
        Scenario s = new Scenario(fixture().get("cases").get(0));
        s.actor.getWeapons().get("soldier1").active = true;
        s.actor.getWeapons().get("soldier1").inStock = 1;
        SequenceRandom random = new SequenceRandom(1, 0, 1, 0, 0, 0, 0);
        List<OpponentAI.Decision> result = OpponentAI.planScoredActions(s.game, s.actor,
                random, Arrays.asList(new OpponentAI.ScoredAction(GameState.Action.ATTACK, 100),
                        new OpponentAI.ScoredAction(GameState.Action.MASS_MEDIA, 40)));
        assertEquals(2, result.size());
        assertEquals(GameState.Action.ATTACK, result.get(0).action);
        assertEquals("soldier1", result.get(0).weaponName);
        assertEquals(GameState.Action.MASS_MEDIA, result.get(1).action);
        assertEquals(Arrays.asList(3, 5, 4, 91, 5, 4, 91), random.bounds);
    }

    @Test public void rejectedAttackRestoresRankedCompanionEvenWhenDuplicateWasKept() throws Exception {
        Scenario s = new Scenario(fixture().get("cases").get(0));
        s.actor.getWeapons().get("soldier1").active = true;
        s.actor.getWeapons().get("soldier1").inStock = 1;
        SequenceRandom random = new SequenceRandom(0, 0, 1, 0, 0, 0, 0);
        List<OpponentAI.Decision> result = OpponentAI.planScoredActions(s.game, s.actor,
                random, Arrays.asList(new OpponentAI.ScoredAction(GameState.Action.ATTACK, 100),
                        new OpponentAI.ScoredAction(GameState.Action.MASS_MEDIA, 40)));
        assertEquals(2, result.size());
        assertEquals(GameState.Action.ATTACK, result.get(0).action);
        assertEquals("soldier1", result.get(0).weaponName);
        assertEquals(GameState.Action.MASS_MEDIA, result.get(1).action);
        assertEquals(Arrays.asList(3, 5, 4, 91, 5, 4, 91), random.bounds);
    }

    /** Synthetic boundary derived from Model.py:814-844, not a campaign replay. */
    private static OpponentAI.Decision directAttack(Scenario scenario, Random random) throws Exception {
        Method method = OpponentAI.class.getDeclaredMethod("planDirectAttackForTarget",
                GameState.class, GameState.PlayerState.class, GameState.PlayerState.class,
                Random.class, Map.class);
        method.setAccessible(true);
        return (OpponentAI.Decision) method.invoke(null, scenario.game, scenario.actor,
                scenario.target, random, Collections.<String, Integer>emptyMap());
    }

    private static int stock(GameState.PlayerState player, int level) throws Exception {
        Method method = OpponentAI.class.getDeclaredMethod("stockAtLevel", GameState.PlayerState.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, player, level);
    }

    private static Object spyTarget(Scenario scenario, Random random) throws Exception {
        Method method = OpponentAI.class.getDeclaredMethod("findSpyTarget", GameState.class,
                GameState.PlayerState.class, Random.class);
        method.setAccessible(true);
        return method.invoke(null, scenario.game, scenario.actor, random);
    }

    private static int fieldInt(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }

    private static final class CountingRandom extends Random {
        int draws;
        final List<Integer> bounds = new ArrayList<>();
        @Override public int nextInt(int bound) { draws++; bounds.add(bound); return 0; }
    }

    private static final class SequenceRandom extends Random {
        final List<Integer> bounds = new ArrayList<>();
        final int[] values;
        int index;
        SequenceRandom(int... values) { this.values = values; }
        @Override public int nextInt(int bound) {
            bounds.add(bound);
            assertTrue("unexpected random draw " + bounds, index < values.length);
            int value = values[index++];
            assertTrue("random value outside bound", value >= 0 && value < bound);
            return value;
        }
    }

    private static final class Scenario {
        final GameState game = new GameState(1, 0L);
        final GameState.PlayerState actor = game.getPlayerState(1);
        final GameState.PlayerState target = game.getPlayerState(2);
        Scenario(JsonValue captured) {
            for (GameState.PlayerState player : game.getPlayers()) {
                player.alive = player.id <= 2;
                for (GameState.WeaponState weapon : player.getWeapons().values()) weapon.inStock = 0;
            }
            game.getHuman().moneyMillions = 0;
            actor.spyReadiness = 25;
            actor.getWeapons().get("spy1").active = true;
            actor.getWeapons().get("spy1").inStock = 1;
            for (JsonValue stock : captured.get("stocks")) {
                GameState.WeaponState weapon = target.getWeapons().get(stock.name);
                assertNotNull(stock.name, weapon);
                weapon.inStock = stock.asInt();
            }
            for (JsonValue name : captured.get("explicitlyInactive")) {
                target.getWeapons().get(name.asString()).active = false;
            }
        }
    }

    private static JsonValue fixture() throws Exception {
        return new JsonReader().parse(new String(resource("source-spy-stock-level.json"), StandardCharsets.UTF_8));
    }

    private static void assertSha(String name, String expected) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(resource(name));
        StringBuilder value = new StringBuilder();
        for (byte b : digest) value.append(String.format(Locale.ROOT, "%02x", b & 255));
        assertEquals(name, expected, value.toString());
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = SourceSpyStockLevelTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, in);
            return in.readAllBytes();
        }
    }
}
