package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

/** Synthetic direct Python presenter callbacks; these are not legal campaign traces. */
public final class SourcePropagandaRoundingTest {
    @Test public void privateCalculationMatchesEveryCapturedSourceBoundary() throws Exception {
        JsonValue cases = fixture().get("cases");
        assertEquals(24, cases.size);
        Method calculate = GameState.class.getDeclaredMethod("calculatePropagandaTransfer",
                GameState.PlayerState.class, GameState.PlayerState.class, int.class);
        calculate.setAccessible(true);
        for (JsonValue c : cases) {
            Context context = context(c);
            JsonValue before = c.get("before");
            int population = before.getInt("cityPopulation") - before.getInt("battleCasualties");
            int actual = (Integer) calculate.invoke(null, context.attacker, context.defender, population);
            assertEquals(c.toString(), c.get("targetPropagandaMin").getInt(0), actual);
        }
    }

    @Test public void aiHalfBoundariesMatchSourceEventGainAndRandomDraw() throws Exception {
        verifyCallback(fixture().get("cases").get(0));
        verifyCallback(fixture().get("cases").get(1));
    }

    @Test public void battleCasualtiesAndFiveRecipientCitiesUseSourceShare() throws Exception {
        JsonValue c = fixture().get("cases").get(10);
        assertEquals(32, c.get("before").getInt("battleCasualties"));
        verifyCallback(c);
    }

    @Test public void sourceCapFortyYieldsTwentyBeforeFiveWayShare() throws Exception {
        JsonValue c = fixture().get("cases").get(11);
        assertEquals(20, c.get("targetPropagandaMin").getInt(0));
        verifyCallback(c);
    }

    @Test public void sourceFixturesRemainByteIdentical() throws Exception {
        assertSha("source-propaganda-resolver.json", "014ff4b41f3550cbc4b22d3f836ba54f532a6f583367b8377a14f02d2f097d22");
        assertSha("source-propaganda-provenance.json", "3197c3b381850d50342971b3b4c62f648b3cd860e40b43f566bf5eafa087823f");
    }

    private static void verifyCallback(JsonValue c) throws Exception {
        Context context = context(c);
        JsonValue before = c.get("before");
        assertEquals(before.getDouble("rngNext"), context.random.copy().nextDouble(), 0d);
        Class<?> changesClass = Class.forName(GameState.class.getName() + "$TurnPopulationChanges");
        Constructor<?> changesConstructor = changesClass.getDeclaredConstructor();
        changesConstructor.setAccessible(true);
        Object changes = changesConstructor.newInstance();
        CityState target = context.defender.getCity(0);
        Method battle = changesClass.getDeclaredMethod("addBattleCasualties", CityState.class, int.class);
        battle.setAccessible(true);
        battle.invoke(changes, target, before.getInt("battleCasualties"));

        Class<?> orderClass = Class.forName(GameState.class.getName() + "$ActionOrder");
        Constructor<?> constructor = orderClass.getDeclaredConstructor(int.class, GameState.Action.class,
                Map.class, List.class, int.class, int.class, int.class, String.class, String.class,
                boolean.class, String.class, int.class);
        constructor.setAccessible(true);
        Object order = constructor.newInstance(context.attacker.id, GameState.Action.PROPAGANDA,
                Collections.emptyMap(), Collections.emptyList(), 0, context.defender.id, 0,
                null, null, false, null, 0);
        Method resolve = GameState.class.getDeclaredMethod("resolvePropaganda", orderClass, changesClass);
        resolve.setAccessible(true);
        resolve.invoke(context.game, order, changes);

        int loss = c.get("targetPropagandaMin").getInt(0);
        assertEquals(1, context.game.getLastTurnPropagandaEvents().size());
        assertEquals(loss, context.game.getLastTurnPropagandaEvents().get(0).transferredMillions);
        Method losses = changesClass.getDeclaredMethod("propagandaLossesFor", CityState.class);
        losses.setAccessible(true);
        assertEquals(Collections.singletonList(loss), losses.invoke(changes, target));
        Method gains = changesClass.getDeclaredMethod("propagandaGainFor", CityState.class);
        gains.setAccessible(true);
        for (int i = 0; i < 5; i++) {
            assertEquals(c.get("recipientPropagandaPlus").getInt(i),
                    ((Integer) gains.invoke(changes, context.attacker.getCity(i))).intValue());
        }
        assertEquals(c.getDouble("rngNext"), context.random.nextDouble(), 0d);
    }

    private static Context context(JsonValue c) {
        JsonValue before = c.get("before");
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        ReferenceRandom random = setup.copyGameplayRandom();
        GameState game = new GameState(setup, random);
        GameState.PlayerState attacker = game.getPlayerState(before.getInt("attacker"));
        GameState.PlayerState defender = game.getPlayerState(before.getInt("defender"));
        attacker.religion = before.getInt("attackerReligion");
        defender.religion = before.getInt("defenderReligion");
        attacker.populationMillions = before.getInt("attackerPopulation");
        defender.populationMillions = before.getInt("defenderPopulation");
        for (int i = 0; i < 5; i++) {
            attacker.getCity(i).setPopulationMillions(before.get("recipients").getInt(i));
        }
        defender.getCity(0).setPopulationMillions(before.getInt("cityPopulation"));
        return new Context(game, random, attacker, defender);
    }

    private static final class Context {
        final GameState game;
        final ReferenceRandom random;
        final GameState.PlayerState attacker, defender;
        Context(GameState game, ReferenceRandom random, GameState.PlayerState attacker,
                GameState.PlayerState defender) {
            this.game = game; this.random = random; this.attacker = attacker; this.defender = defender;
        }
    }

    private static JsonValue fixture() throws Exception {
        return new JsonReader().parse(new String(resource("source-propaganda-resolver.json"), StandardCharsets.UTF_8));
    }
    private static void assertSha(String name, String expected) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(resource(name));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        assertEquals(name, expected, hex.toString());
    }
    private static byte[] resource(String name) throws Exception {
        try (InputStream input = SourcePropagandaRoundingTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, input);
            return input.readAllBytes();
        }
    }
}
