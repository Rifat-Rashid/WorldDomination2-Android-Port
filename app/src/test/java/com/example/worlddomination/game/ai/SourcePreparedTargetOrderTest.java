package com.example.worlddomination.game.ai;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.CityState;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic ready-carrier planner scenarios; source Model.py:795-807. */
public final class SourcePreparedTargetOrderTest {
    @Test public void readyStrikeChoosesLowestEligibleIndexIncludingExactThreshold() throws Exception {
        Scenario s = new Scenario();
        int k = s.kills;
        s.populations(0, k + 1, k, 1, k);
        OpponentAI.Decision decision = s.plan(new CountingRandom());
        assertNotNull(decision);
        assertEquals(GameState.Action.PREPARED_ATTACK, decision.action);
        assertEquals(s.target.id, decision.targetPlayerId);
        assertEquals("descending keys overwrite until lowest eligible slot", 2, decision.targetCityIndex);
        assertEquals("warhead1", decision.payloadName);
    }

    @Test public void noEligibleCityUsesRandomPopulatedFallbackAndOneExtraDraw() throws Exception {
        Scenario eligible = new Scenario();
        eligible.populations(0, eligible.kills, eligible.kills + 1, eligible.kills + 1, eligible.kills + 1);
        CountingRandom a = new CountingRandom();
        OpponentAI.Decision selected = eligible.plan(a);
        assertNotNull(selected);
        assertEquals(1, selected.targetCityIndex);
        Scenario fallback = new Scenario();
        fallback.populations(0, fallback.kills + 1, fallback.kills + 1, fallback.kills + 1, fallback.kills + 1);
        CountingRandom b = new CountingRandom();
        OpponentAI.Decision random = fallback.plan(b);
        assertNotNull(random);
        assertEquals("zero draw selects first source descending populated slot", 4, random.targetCityIndex);
        assertEquals("fallback consumes one city draw", a.draws + 1, b.draws);
    }

    @Test public void deadCitiesDoNotWinSelectionOrRandomFallback() throws Exception {
        Scenario s = new Scenario();
        s.populations(0, 0, s.kills + 1, 0, s.kills);
        assertEquals(4, s.plan(new CountingRandom()).targetCityIndex);
        s.populations(0, 0, s.kills + 1, 0, 0);
        assertEquals(2, s.plan(new CountingRandom()).targetCityIndex);
    }

    @Test public void syntheticSourcePairedTieKeepsFirstDescendingKey() throws Exception {
        byte[] data;
        try (InputStream in = getClass().getResourceAsStream("/source-ai-paired-tie.json")) {
            assertNotNull(in);
            data = in.readAllBytes();
        }
        StringBuilder digest = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(data))
            digest.append(String.format(Locale.ROOT, "%02x", value & 255));
        assertEquals("2d9db70c5e997f923316c69c048a82bbf8ec3e6e6d57221a9a497c29cc153ea2",
                digest.toString());
        JsonValue fixture = new JsonReader().parse(new String(data, StandardCharsets.UTF_8));
        JsonValue after = fixture.get("after");
        int[] kills = after.get("chosenMaxKills").asIntArray();
        assertArrayEquals(new int[]{90, 4}, kills);
        Scenario s = new Scenario();
        s.populations(fixture.get("before").get("targetCityPopulations").get(0).asIntArray());
        assertEquals("source getTarget synthetic captured tie",
                after.get("slots").get(0).getInt("city"), paired(s, kills[0] + kills[1]).index);
    }

    @Test public void syntheticPairedSelectionIsStrictAndIgnoresDeadCities() throws Exception {
        Scenario s = new Scenario();
        s.populations(0,94,93,94,93);
        assertEquals("exact combined threshold excluded; highest tie wins",4,paired(s,94).index);
        s.populations(0,94,0,95,0);
        assertNull("no positive population below threshold",paired(s,94));
    }

    private static CityState paired(Scenario scenario, int kills) throws Exception {
        java.lang.reflect.Method method = OpponentAI.class.getDeclaredMethod(
                "largestCityBelowCombinedKills", GameState.PlayerState.class, int.class);
        method.setAccessible(true);
        return (CityState) method.invoke(null, scenario.target, kills);
    }

    private static final class Scenario {
        final GameState game = new GameState(1, 531L);
        final GameState.PlayerState actor = game.getPlayerState(1);
        final GameState.PlayerState target = game.getPlayerState(0);
        final int kills;
        Scenario() throws Exception {
            for (GameState.PlayerState player : game.getPlayers()) {
                player.alive = player.id == actor.id || player.id == target.id;
            }
            target.moneyMillions = 0;
            for (GameState.WeaponState weapon : actor.getWeapons().values()) weapon.inStock = 0;
            GameState.WeaponState carrier = actor.getWeapons().get("rocket1");
            carrier.active = true;
            GameState.WeaponState payload = actor.getWeapons().get("warhead1");
            payload.active = true; payload.inStock = 1;
            kills = payload.definition.maxKillsMillions;
            assertTrue(kills > 0);
            Field f = GameState.PlayerState.class.getDeclaredField("preparedCarrierName");
            f.setAccessible(true); f.set(actor, "rocket1");
        }
        void populations(int... values) {
            assertEquals(5, values.length);
            for (int i=0; i<values.length; i++) target.getCity(i).populationMillions = values[i];
        }
        OpponentAI.Decision plan(Random random) {
            return OpponentAI.planAction(game, actor, GameState.Action.ATTACK, random, 1,
                    Collections.<String, Integer>emptyMap());
        }
    }
    private static final class CountingRandom extends Random {
        int draws;
        @Override public int nextInt(int bound) { draws++; return 0; }
    }
}
