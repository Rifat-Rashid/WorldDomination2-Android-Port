package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/** Diagnostic source result records compared with the actual native prepared-strike path. */
public final class PreparedLaunchRegardTest {
    private static final String FIXTURE = "/source-prepared-launch-regard.json";
    private static final String FIXTURE_SHA256 =
            "8ae783f03f57559a768e5752c4ed8c810b903db529df2be2a48947136bcbc89b";

    @Test public void rocketImpactAndInterceptionSettleSourceHumanAndAiRegard()
            throws Exception {
        JsonValue fixture = sourceFixture();
        assertEquals("complete", fixture.getString("status"));
        assertEquals(30, fixture.get("rows").size);
        compare(fixture, 0, 14, false, .7);
        compare(fixture, 0, 0, true, .7);
        compare(fixture, 1, 15, false, .0);
        compare(fixture, 1, 0, true, .0);
    }

    private static void compare(JsonValue fixture, int attackerId, int casualties,
                                boolean intercepted, double casualtyDraw) throws Exception {
        int defenderId = attackerId == 0 ? 1 : 0;
        GameState game = new GameState(1, new FixedRandom(casualtyDraw), false);
        GameState.PlayerState attacker = game.getPlayerState(attackerId);
        GameState.PlayerState defender = game.getPlayerState(defenderId);
        // The source diagnostic has no special-person observer effect.
        Field specialPerson = GameState.PlayerState.class.getDeclaredField("specialPerson");
        specialPerson.setAccessible(true);
        specialPerson.setBoolean(defender, false);
        GameState.WeaponState rocket = attacker.getWeapons().get("rocket1");
        GameState.WeaponState warhead = attacker.getWeapons().get("warhead1");
        rocket.active = true;
        rocket.inStock = 1;
        warhead.active = true;
        warhead.inStock = 1;
        Field prepared = GameState.PlayerState.class.getDeclaredField("preparedCarrierName");
        prepared.setAccessible(true);
        prepared.set(attacker, "rocket1");

        Class<?> orderType = Class.forName(GameState.class.getName() + "$ActionOrder");
        Constructor<?> orderConstructor = orderType.getDeclaredConstructor(int.class,
                GameState.Action.class, Map.class, List.class, int.class, int.class,
                int.class, String.class, String.class, boolean.class, String.class, int.class);
        orderConstructor.setAccessible(true);
        Object launch = orderConstructor.newInstance(attackerId, GameState.Action.PREPARED_ATTACK,
                Collections.emptyMap(), Collections.emptyList(), 0, defenderId, 0,
                "rocket1", "warhead1", false, null, 0);
        List<Object> defenses = new ArrayList<>();
        if (intercepted) {
            defenses.add(orderConstructor.newInstance(defenderId, GameState.Action.DEFENSE,
                    Collections.emptyMap(), Collections.emptyList(), 0, -1, -1,
                    "antiRocket", null, false, null, 0));
        }
        Class<?> changesType = Class.forName(GameState.class.getName()
                + "$TurnPopulationChanges");
        Constructor<?> changesConstructor = changesType.getDeclaredConstructor();
        changesConstructor.setAccessible(true);
        Method resolve = GameState.class.getDeclaredMethod("resolvePreparedAttack", orderType,
                List.class, List.class, changesType);
        resolve.setAccessible(true);
        resolve.invoke(game, launch, defenses, new ArrayList<>(), changesConstructor.newInstance());

        assertEquals(1, game.getLastTurnCombatEvents().size());
        GameState.CombatEvent event = game.getLastTurnCombatEvents().get(0);
        assertEquals(intercepted ? GameState.CombatEventType.STRIKE_INTERCEPTED
                : GameState.CombatEventType.STRIKE_IMPACT, event.type);
        assertEquals(casualties, event.casualtiesMillions);
        Field hasRegard = orderType.getDeclaredField("hasBattleRegardResult");
        hasRegard.setAccessible(true);
        assertTrue(hasRegard.getBoolean(launch));
        Field regardLoss = orderType.getDeclaredField("battleRegardLoss");
        regardLoss.setAccessible(true);
        int actualLoss = regardLoss.getInt(launch);

        JsonValue expected = row(fixture, attackerId == 0 ? "human" : "AI",
                casualties, intercepted);
        assertEquals("source record casualties", casualties,
                expected.getInt("resultCasualties"));
        double beforeAttacker = attacker.getRegardTowardExact(defenderId);
        double beforeDefender = defender.getRegardTowardExact(attackerId);
        Method settle = GameState.class.getDeclaredMethod("applyTurnResultBattleHostilityEffects",
                GameState.PlayerState.class, GameState.PlayerState.class, int.class);
        settle.setAccessible(true);
        settle.invoke(game, attacker, defender, actualLoss);
        assertEquals("attacker regard delta", expected.get("regardDelta").getDouble(0),
                attacker.getRegardTowardExact(defenderId) - beforeAttacker, 0d);
        assertEquals("defender regard delta", expected.get("regardDelta").getDouble(1),
                defender.getRegardTowardExact(attackerId) - beforeDefender, 0d);
    }

    private static JsonValue row(JsonValue fixture, String actor, int casualties,
                                 boolean intercepted) {
        for (JsonValue candidate : fixture.get("rows")) {
            if (actor.equals(candidate.getString("actor"))
                    && "rocket1".equals(candidate.getString("carrier"))
                    && casualties == candidate.getInt("inputCasualties")
                    && intercepted == candidate.getBoolean("interceptedFlag")) {
                return candidate;
            }
        }
        throw new AssertionError("Source diagnostic row absent: " + actor + "/" + casualties);
    }

    private static JsonValue sourceFixture() throws Exception {
        byte[] bytes;
        try (InputStream stream = PreparedLaunchRegardTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull("guarded source fixture", stream);
            bytes = stream.readAllBytes();
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte value : hash) hex.append(String.format("%02x", value & 0xff));
        assertEquals("source fixture bytes", FIXTURE_SHA256, hex.toString());
        return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static final class FixedRandom extends Random {
        private final double casualtyDraw;
        FixedRandom(double casualtyDraw) { this.casualtyDraw = casualtyDraw; }
        @Override public double nextDouble() { return casualtyDraw; }
        @Override public int nextInt(int bound) { return 0; }
    }
}
