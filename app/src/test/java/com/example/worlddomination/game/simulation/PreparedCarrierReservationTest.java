package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Injected stock fixtures isolate reservation rules; these are not legal campaign traces. */
public final class PreparedCarrierReservationTest {
    private static final String[] CARRIERS = {"rocket1", "plane1", "sub", "ship", "space"};

    @Test
    public void preparationReservesOneCarrierAndTubeRemovalReleasesIt() {
        for (String carrier : CARRIERS) {
            GameState game = injectedStock(carrier, payloadFor(carrier), 1);
            queuePreparation(game, carrier);

            assertTrue(carrier, game.isQueuedCarrierPreparation(0));
            assertEquals(carrier, 2, game.getWeaponStock(0, carrier));
            assertEquals(carrier, 1, game.getAvailableWeaponStock(0, carrier));
            assertEquals(carrier, 1, game.getAvailableWeaponStock(0, payloadFor(carrier)));

            game.removeQueuedAction(0);
            assertNull(carrier, game.getQueuedAction(0));
            assertNull(carrier, game.getHuman().getPreparedCarrierName());
            assertEquals(carrier, 2, game.getAvailableWeaponStock(0, carrier));
            assertEquals(carrier, 1, game.getAvailableWeaponStock(0, payloadFor(carrier)));
        }
    }

    @Test
    public void readyLaunchReservesOnlyPayloadAndRemovalMakesItSelectableAgain() {
        for (String carrier : new String[]{"rocket1", "plane1", "sub", "space"}) {
            String payload = payloadFor(carrier);
            GameState game = readyCarrier(carrier, payload, 1);
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(carrier, game.configurePreparedLaunch(1, 0, payload));
            assertTrue(carrier, game.acceptSelectedAction());

            assertFalse(carrier, game.isQueuedCarrierPreparation(0));
            assertEquals(carrier, 1, game.getWeaponStock(0, carrier));
            assertEquals(carrier, 1, game.getAvailableWeaponStock(0, carrier));
            assertEquals(carrier, 1, game.getWeaponStock(0, payload));
            assertEquals(carrier, 0, game.getAvailableWeaponStock(0, payload));

            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertFalse(carrier, game.configurePreparedLaunch(1, 1, payload));
            game.cancelSelection();
            game.removeQueuedAction(0);
            assertEquals(carrier, game.getHuman().getPreparedCarrierName());
            assertEquals(carrier, 1, game.getAvailableWeaponStock(0, carrier));
            assertEquals(carrier, 1, game.getAvailableWeaponStock(0, payload));
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(carrier, game.configurePreparedLaunch(1, 1, payload));
            assertTrue(carrier, game.acceptSelectedAction());
            assertEquals(carrier, 0, game.getAvailableWeaponStock(0, payload));
            assertEquals(carrier, 1, game.getAvailableWeaponStock(0, carrier));
        }
    }

    @Test
    public void readyShipReservesEveryPayloadWithoutReservingSpareShip() {
        GameState game = readyCarrier("ship", "warhead1", 2);
        setStock(game, "warhead2", 1);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedShipStrike(1, 0, "warhead1"));
        assertTrue(game.configurePreparedShipStrike(1, 1, "warhead1"));
        assertFalse(game.configurePreparedShipStrike(1, 2, "warhead1"));
        assertTrue(game.configurePreparedShipStrike(1, 2, "warhead2"));
        assertTrue(game.acceptSelectedAction());

        assertFalse(game.isQueuedCarrierPreparation(0));
        assertEquals(3, game.getQueuedShipStrikeCount(0));
        assertEquals(1, game.getWeaponStock(0, "ship"));
        assertEquals(1, game.getAvailableWeaponStock(0, "ship"));
        assertEquals(2, game.getWeaponStock(0, "warhead1"));
        assertEquals(1, game.getWeaponStock(0, "warhead2"));
        assertEquals(0, game.getAvailableWeaponStock(0, "warhead1"));
        assertEquals(0, game.getAvailableWeaponStock(0, "warhead2"));
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertFalse(game.configurePreparedShipStrike(1, 3, "warhead1"));
        assertFalse(game.configurePreparedShipStrike(1, 3, "warhead2"));
        game.cancelSelection();

        game.removeQueuedAction(0);
        assertEquals("ship", game.getHuman().getPreparedCarrierName());
        assertEquals(1, game.getAvailableWeaponStock(0, "ship"));
        assertEquals(2, game.getAvailableWeaponStock(0, "warhead1"));
        assertEquals(1, game.getAvailableWeaponStock(0, "warhead2"));
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedShipStrike(1, 3, "warhead1"));
        assertTrue(game.configurePreparedShipStrike(1, 4, "warhead2"));
        assertTrue(game.acceptSelectedAction());
        assertEquals(1, game.getAvailableWeaponStock(0, "ship"));
        assertEquals(1, game.getAvailableWeaponStock(0, "warhead1"));
        assertEquals(0, game.getAvailableWeaponStock(0, "warhead2"));
    }

    private static GameState injectedStock(String carrier, String payload, int payloadCount) {
        GameState game = new GameState(1, 52L, false);
        setStock(game, carrier, 2);
        setStock(game, payload, payloadCount);
        return game;
    }

    private static GameState readyCarrier(String carrier, String payload, int payloadCount) {
        GameState game = injectedStock(carrier, payload, payloadCount);
        queuePreparation(game, carrier);
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertEquals(carrier, game.getHuman().getPreparedCarrierName());
        assertEquals(carrier, 1, game.getWeaponStock(0, carrier));
        assertEquals(carrier, payloadCount, game.getAvailableWeaponStock(0, payload));
        return game;
    }

    private static void queuePreparation(GameState game, String carrier) {
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(carrier, game.configurePreparedCarrier(carrier));
        assertTrue(carrier, game.acceptSelectedAction());
    }

    private static void setStock(GameState game, String name, int count) {
        GameState.WeaponState weapon = game.getHuman().getWeapons().get(name);
        weapon.active = true;
        weapon.inStock = count;
    }

    private static String payloadFor(String carrier) {
        return "space".equals(carrier) ? "specialLoad" : "warhead1";
    }
}
