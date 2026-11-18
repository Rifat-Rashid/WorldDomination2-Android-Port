package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.ui.BattlefieldCityOrderMarkers;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Occupied tube cancellation after a prepared ship is launched in the next turn. */
public final class SourcePreparedShipTubeCancelTest {
    @Test
    public void removingSecondTubeReleasesItsPayloadAndAllowsDifferentEnemy() throws Exception {
        JsonValue source = sourceFixture();
        JsonValue setup = source.get("setup");
        JsonValue accepted = row(source, "first_ship_payload_accepted_slot2");
        JsonValue removed = row(source, "occupied_tube2_removed");
        JsonValue replacement = row(source, "replacement_payload_accepted_slot2");

        // Quiet opponents isolate the human's slot and stock behavior. Seed 3
        // mirrors the source probe, while AI and full setup RNG are out of scope.
        GameState state = new GameState(1, setup.getInt("seed"), false);
        GameState.PlayerState human = state.getHuman();
        GameState.WeaponState ship = human.getWeapons().get("ship");
        ship.active = true;
        ship.inStock = setup.get("diagnostic_stock").getInt("ship");
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("ship"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        int javaTurnStartFunds = state.getTurnMoneyMillions();
        int sourceTurnStartFunds = row(source, "next_turn_ready").getInt("funds");

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        GameState.WeaponState first = human.getWeapons().get("warhead1");
        first.active = true;
        first.inStock = setup.get("diagnostic_stock").getInt("warhead1");
        JsonValue firstSourceStrike = accepted.get("slots").get(1).get("ship_strikes").get(0);
        assertEquals(setup.getInt("first_city_id"), firstSourceStrike.getInt("city_id"));
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedShipStrike(
                firstSourceStrike.getInt("city_id") / 5,
                firstSourceStrike.getInt("city_id") % 5,
                sourcePayloadName(setup, firstSourceStrike.getInt("weapon_id"))));
        assertTrue(state.acceptSelectedAction());
        assertCheckpoint(state, setup, accepted, javaTurnStartFunds, sourceTurnStartFunds);

        state.removeQueuedAction(1);
        state.cancelSelection(); // Occupied-tube UI follows removal with selection reset.
        assertCheckpoint(state, setup, removed, javaTurnStartFunds, sourceTurnStartFunds);

        GameState.WeaponState second = human.getWeapons().get("warhead2");
        second.active = true;
        second.inStock = setup.get("diagnostic_stock").getInt("warhead2");
        JsonValue secondSourceStrike = replacement.get("slots").get(1).get("ship_strikes").get(0);
        assertEquals(setup.getInt("replacement_city_id"), secondSourceStrike.getInt("city_id"));
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedShipStrike(
                secondSourceStrike.getInt("city_id") / 5,
                secondSourceStrike.getInt("city_id") % 5,
                sourcePayloadName(setup, secondSourceStrike.getInt("weapon_id"))));
        assertTrue(state.acceptSelectedAction());
        assertCheckpoint(state, setup, replacement, javaTurnStartFunds, sourceTurnStartFunds);
    }

    private static void assertCheckpoint(GameState state, JsonValue setup, JsonValue source,
                                         int javaTurnStartFunds, int sourceTurnStartFunds)
            throws ReflectiveOperationException {
        assertEquals(source.getString("prepared_weapon"),
                state.getHuman().getPreparedCarrierName());
        assertEquals(source.getInt("funds") - sourceTurnStartFunds,
                state.getTurnMoneyMillions() - javaTurnStartFunds);
        assertEquals(source.get("model_ship_strikes").size,
                state.getSelectedShipStrikeCount());
        assertNull(state.getSelectedAction());

        JsonValue sourceSlots = source.get("slots");
        for (int slot = 0; slot < 2; slot++) {
            JsonValue sourceSlot = sourceSlots.get(slot);
            JsonValue action = sourceSlot.get("action");
            GameState.Action expected = action.isNull() ? null
                    : "resources".equals(action.asString()) ? GameState.Action.RESOURCES
                    : GameState.Action.PREPARED_ATTACK;
            assertEquals(expected, state.getQueuedAction(slot));
            JsonValue strikes = sourceSlot.get("ship_strikes");
            assertEquals(strikes.size, state.getQueuedShipStrikeCount(slot));
            List<GameState.ShipStrike> queued = queuedShipStrikes(state, slot);
            for (int i = 0; i < strikes.size; i++) {
                JsonValue strike = strikes.get(i);
                int sourceCityId = strike.getInt("city_id");
                assertEquals(sourceCityId / 5, state.getQueuedShipStrikePlayer(slot, i));
                assertEquals(sourceCityId % 5, state.getQueuedShipStrikeCity(slot, i));
                assertEquals(sourcePayloadName(setup, strike.getInt("weapon_id")),
                        queued.get(i).payloadName);
            }
        }

        for (String name : new String[] {"ship", "warhead1", "warhead2"}) {
            JsonValue sourceStock = source.get("stock").get(name);
            int inStock = state.getWeaponStock(0, name);
            assertEquals(name + " stock", sourceStock.getInt("in_stock"), inStock);
            assertEquals(name + " reserved", sourceStock.getInt("in_use"),
                    inStock - state.getAvailableWeaponStock(0, name));
        }
        assertEquals(source.get("city_ship_count").getInt("enemy1_city0"),
                BattlefieldCityOrderMarkers.iconCount(state, 1, 0));
        assertEquals(source.get("city_ship_count").getInt("enemy2_city0"),
                BattlefieldCityOrderMarkers.iconCount(state, 2, 0));
    }

    private static String sourcePayloadName(JsonValue setup, int sourceWeaponId) {
        JsonValue ids = setup.get("source_weapon_ids");
        if (sourceWeaponId == ids.getInt("warhead1")) return "warhead1";
        if (sourceWeaponId == ids.getInt("warhead2")) return "warhead2";
        throw new AssertionError("Unexpected source payload ID " + sourceWeaponId);
    }

    @SuppressWarnings("unchecked")
    private static List<GameState.ShipStrike> queuedShipStrikes(GameState state, int slot)
            throws ReflectiveOperationException {
        Method orderAt = GameState.class.getDeclaredMethod("queuedOrderAt", int.class);
        orderAt.setAccessible(true);
        Object order = orderAt.invoke(state, slot);
        if (order == null) return java.util.Collections.emptyList();
        Field strikes = order.getClass().getDeclaredField("shipStrikes");
        strikes.setAccessible(true);
        return (List<GameState.ShipStrike>) strikes.get(order);
    }

    private static JsonValue row(JsonValue fixture, String step) {
        for (JsonValue candidate : fixture.get("rows")) {
            if (step.equals(candidate.getString("step"))) return candidate;
        }
        throw new AssertionError("Missing source step " + step);
    }

    private static JsonValue sourceFixture() throws IOException {
        try (InputStream input = SourcePreparedShipTubeCancelTest.class.getResourceAsStream(
                "/source-prepared-ship-tube-cancel.json")) {
            assertNotNull("Missing guarded source trace", input);
            return new JsonReader().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
