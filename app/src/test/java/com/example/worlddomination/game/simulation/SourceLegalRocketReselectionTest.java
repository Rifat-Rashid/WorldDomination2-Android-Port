package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

/** Legal seed-7 source campaign through occupied prepared-rocket tube reselection. */
public final class SourceLegalRocketReselectionTest {
    private static final String FIXTURE = "/source-legal-rocket-reselection.json";
    private static final String FIXTURE_SHA256 =
            "c66c76e66aded719a9b70e6be6b60d2465ab50ebd1ff7db48831c5ee2a877de9";

    @Test public void legalRocketLaunchCanBeRemovedAndReacceptedWithoutChangingOutcome()
            throws Exception {
        JsonValue source = loadFixture();
        GameSetup setup = new GameSetup(7L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(2));
        assertTrue(setup.toggleOpponent(9));
        GameState game = new GameState(setup);
        JsonValue hashes = source.get("prefixSourceHashes");
        for (int turn = 1; turn <= 8; turn++) {
            queuePrefixTurn(game, turn);
            game.confirmTurn();
            assertEquals("source prefix turn " + turn, hashes.getString(turn - 1),
                    sha256(trace(game, turn).getBytes(StandardCharsets.UTF_8)));
        }
        assertStage(game, stage(source, "after_turn8"));

        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(2, 0, "warhead1"));
        assertStage(game, stage(source, "turn9_selected"));
        assertTrue(game.acceptSelectedAction());
        assertStage(game, stage(source, "turn9_accepted"));

        // WorldDominationGame.activateActionSlot invokes both calls for an occupied tube.
        // This JVM test covers controller semantics; it does not exercise Android hit testing.
        game.removeQueuedAction(0);
        game.cancelSelection();
        assertStage(game, stage(source, "turn9_removed"));

        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(2, 0, "warhead1"));
        assertStage(game, stage(source, "turn9_reselected"));
        assertTrue(game.acceptSelectedAction());
        assertStage(game, stage(source, "turn9_reaccepted"));
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        assertStage(game, stage(source, "turn9_two_slots"));
        game.confirmTurn();
        assertEquals(source.getString("baselineTurn9SourceHash"),
                source.getString("modifiedTurn9SourceHash"));
        assertEquals("source resolved turn 9", source.getString("modifiedTurn9SourceHash"),
                sha256(trace(game, 9).getBytes(StandardCharsets.UTF_8)));
        assertStage(game, stage(source, "turn9_confirmed"));
    }

    private static void queuePrefixTurn(GameState game, int turn) {
        switch (turn) {
            case 1:
                resources(game);
                game.selectAction(GameState.Action.RESEARCH);
                assertTrue(game.selectResearchTechnology("soldier2"));
                break;
            case 2:
                game.selectAction(GameState.Action.PRODUCTION);
                assertTrue(game.configureProduction("soldier", 50));
                break;
            case 3:
                game.selectAction(GameState.Action.ATTACK);
                assertTrue(game.configureAttack(1, 0, "soldier1"));
                break;
            case 4:
                game.selectAction(GameState.Action.MASS_MEDIA);
                assertTrue(game.acceptSelectedAction());
                game.selectAction(GameState.Action.PROPAGANDA);
                assertTrue(game.configurePropaganda(2, 0));
                break;
            case 5:
                game.selectAction(GameState.Action.DEFENSE);
                assertTrue(game.configureDefense("antiAir"));
                break;
            case 6:
                game.selectAction(GameState.Action.DIPLOMACY);
                while (game.getSelectedTargetPlayer() != 1) game.cycleDiplomacyTarget(1);
                assertEquals(0, game.getSelectedDiplomacyOption());
                break;
            case 7:
                game.selectAction(GameState.Action.ATTACK);
                assertTrue(game.configureAttack(2, 0, "spy1"));
                break;
            case 8:
                game.selectAction(GameState.Action.PREPARED_ATTACK);
                assertTrue(game.configurePreparedCarrier("rocket1"));
                break;
            default: throw new AssertionError("Unexpected prefix turn " + turn);
        }
        assertTrue("accept turn " + turn, game.acceptSelectedAction());
        if (turn != 1 && turn != 4) resources(game);
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void assertStage(GameState game, JsonValue expected) throws Exception {
        String where = expected.getString("label") + " ";
        assertEquals(where + "turn", expected.getInt("turn"), game.getTurn());
        assertEquals(where + "money", expected.getInt("money"), game.getHuman().moneyMillions);
        assertEquals(where + "budget", expected.getInt("turnMoney"), game.getTurnMoneyMillions());
        assertEquals(where + "canonical next RNG", expected.getDouble("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        String prepared = value(expected, "preparedWeapon");
        assertEquals(where + "ready carrier", prepared, game.getPreparedCarrierName(0));
        assertEquals(where + "prepared status", "ready".equals(expected.getString("preparedStatus")),
                game.getPreparedCarrierName(0) != null);
        String selected = value(expected, "selectedAction");
        assertEquals(where + "selected action", "preAttack".equals(selected)
                ? GameState.Action.PREPARED_ATTACK : null, game.getSelectedAction());
        if (selected != null) {
            // Source keeps the selected payload in userSelectedWeapon while native
            // keeps it in selectedPreparedPayload. The source's distinct
            // userSelectedPayload field is undefined in this non-ship route.
            assertEquals(where + "selected payload", value(expected, "selectedWeapon"),
                    game.getSelectedPreparedPayload());
            assertEquals(where + "selected carrier", prepared,
                    game.getSelectedPreparedCarrier());
        }
        JsonValue slots = expected.get("queue");
        int queued = 0;
        for (int slot = 0; slot < 2; slot++) {
            JsonValue order = slots.get(slot);
            String action = value(order, "action");
            if (action != null) queued++;
            assertEquals(where + "slot " + slot, "preAttack".equals(action)
                    ? GameState.Action.PREPARED_ATTACK
                    : "resources".equals(action) ? GameState.Action.RESOURCES : null,
                    game.getQueuedAction(slot));
            if ("preAttack".equals(action)) {
                assertEquals(where + "carrier", value(order, "carrier"),
                        game.getQueuedWeaponName(slot));
                assertEquals(where + "payload", value(order, "payload"),
                        queuedPayload(game, slot));
                assertEquals(where + "target player", order.getInt("targetPlayer"),
                        game.getQueuedTargetPlayer(slot));
                assertEquals(where + "target city", order.getInt("targetCity") % 5,
                        game.getQueuedTargetCity(slot));
            }
        }
        assertEquals(where + "queued count", queued, game.getQueuedActionCount());
        assertStock(game, expected.get("rocket1"), "rocket1", selected == null, where);
        assertStock(game, expected.get("warhead1"), "warhead1", selected == null, where);
        JsonValue players = expected.get("players");
        if (players != null) assertCapturedPlayers(game, players, where);
    }

    private static void assertStock(GameState game, JsonValue expected, String name,
                                    boolean compareReservation, String where) {
        assertEquals(where + name + " inStock", expected.getInt("inStock"),
                game.getWeaponStock(0, name));
        if (compareReservation) assertEquals(where + name + " inUse", expected.getInt("inUse"),
                game.getWeaponStock(0, name) - game.getAvailableWeaponStock(0, name));
    }

    private static void assertCapturedPlayers(GameState game, JsonValue players, String where) {
        assertEquals(where + "player count", 3, players.size);
        for (JsonValue source : players) {
            int id = source.getInt("id");
            GameState.PlayerState player = game.getPlayerState(id);
            String prefix = where + "player " + id + " ";
            assertEquals(prefix + "alive", source.getBoolean("alive"), player.alive);
            assertEquals(prefix + "money", source.getInt("money"), player.moneyMillions);
            assertEquals(prefix + "budget", source.getInt("turnMoney"), player.turnMoneyMillions);
            assertEquals(prefix + "population", source.getInt("population"), player.populationMillions);
            assertEquals(prefix + "religion", source.getInt("religion"), player.religion);
            assertEquals(prefix + "score", source.getLong("score"), player.score);
            for (int city = 0; city < 5; city++) assertEquals(prefix + "city " + city,
                    source.get("cities").getInt(city), player.getCity(city).populationMillions);
            for (JsonValue item : source.get("stocks")) assertEquals(prefix + item.name,
                    item.asInt(), player.getWeaponStockByName(item.name));
            for (JsonValue item : source.get("antiStocks")) assertEquals(prefix + item.name,
                    item.asInt(), player.getWeaponStockByName(item.name));
            for (JsonValue item : source.get("inUse")) assertEquals(prefix + item.name + " inUse",
                    item.asInt(), player.getWeaponStockByName(item.name)
                            - game.getAvailableWeaponStock(id, item.name));
            for (JsonValue item : source.get("antiInUse")) assertEquals(prefix + item.name + " inUse",
                    item.asInt(), player.getWeaponStockByName(item.name)
                            - game.getAvailableWeaponStock(id, item.name));
            List<String> technologies = new ArrayList<>();
            for (JsonValue item : source.get("inventedTech")) technologies.add(item.asString());
            List<String> nativeTechnologies = new ArrayList<>(player.getInventedTechnologies());
            Collections.sort(nativeTechnologies);
            assertEquals(prefix + "technologies", technologies, nativeTechnologies);
            for (int target = 0; target < 5; target++) {
                assertEquals(prefix + "regard " + target,
                        source.get("regards").getDouble(target),
                        player.getRegardTowardExact(target), 0d);
                JsonValue cease = source.get("ceasefires").get(target);
                assertEquals(prefix + "ceasefire " + target,
                        cease.getBoolean("status"), player.hasCeasefireWith(target));
                assertEquals(prefix + "ceasefire turns " + target,
                        cease.getInt("turnsLeft"), player.getCeasefireTurnsWith(target));
            }
        }
    }

    private static String queuedPayload(GameState game, int slot) throws Exception {
        Method at = GameState.class.getDeclaredMethod("queuedOrderAt", int.class);
        at.setAccessible(true);
        Object order = at.invoke(game, slot);
        Field payload = order.getClass().getDeclaredField("payloadName");
        payload.setAccessible(true);
        return (String) payload.get(order);
    }

    private static JsonValue stage(JsonValue fixture, String name) {
        for (JsonValue candidate : fixture.get("stages"))
            if (name.equals(candidate.getString("label"))) return candidate;
        throw new AssertionError("Missing source stage " + name);
    }

    private static String value(JsonValue object, String key) {
        JsonValue item = object.get(key);
        return item == null || item.isNull() ? null : item.asString();
    }

    private static JsonValue loadFixture() throws Exception {
        byte[] bytes;
        try (InputStream stream = SourceLegalRocketReselectionTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull("guarded source fixture", stream);
            bytes = stream.readAllBytes();
        }
        assertEquals("source fixture bytes", FIXTURE_SHA256, sha256(bytes));
        return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static String trace(GameState game, int turn) {
        List<String> rows = new ArrayList<>();
        for (int id = 0; id <= 2; id++) {
            GameState.PlayerState player = game.getPlayerState(id);
            String cities = player.getCities().stream()
                    .map(city -> Integer.toString(city.populationMillions))
                    .collect(Collectors.joining(","));
            Map<String, Integer> stocks = new TreeMap<>();
            player.getWeapons().forEach((name, weapon) -> {
                if (!"anti".equals(weapon.definition.type)) stocks.put(name, weapon.inStock);
            });
            String stock = stocks.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
            String technologies = player.getInventedTechnologies().stream().sorted()
                    .collect(Collectors.joining(","));
            rows.add(String.join("|", "P", Integer.toString(turn), Integer.toString(id),
                    player.alive ? "1" : "0", Integer.toString(player.moneyMillions),
                    Integer.toString(player.populationMillions), Integer.toString(player.religion),
                    Long.toString(player.score), cities, stock, technologies));
        }
        return String.join("\n", rows);
    }
}
