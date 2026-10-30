package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.rendering.FlashBattleRoutes;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class GameStateCombatEventTest {
    @Test
    public void resolvedSoldierAttackEmitsOneDirectAttackEvent() {
        GameState state = new GameState(1, 120L, false);
        GameState.WeaponState soldier = state.getHuman().getWeapons().get("soldier1");
        soldier.active = true;
        soldier.inStock = 1;

        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        List<GameState.CombatEvent> events = state.getLastTurnCombatEvents();
        assertEquals(1, events.size());
        assertEquals(GameState.CombatEventType.DIRECT_ATTACK, events.get(0).type);
        assertEquals(0, events.get(0).attackerPlayerId);
        assertEquals(1, events.get(0).defenderPlayerId);
        assertEquals(0, events.get(0).cityIndex);
        assertEquals("soldier1", events.get(0).weaponName);
        assertEquals(1, events.get(0).actionBuildingSlot);
        assertTrue(events.get(0).casualtiesMillions > 0);
        assertEquals(OriginalAudio.Cue.SOLDIER_GUN,
                OriginalAudio.cueForCombatEvent(events.get(0)));
    }

    @Test
    public void resolvedPreparedRocketAttackEmitsPayloadImpactEvent() {
        GameState state = new GameState(1, 121L, false);
        GameState.WeaponState rocket = state.getHuman().getWeapons().get("rocket1");
        GameState.WeaponState warhead = state.getHuman().getWeapons().get("warhead1");
        rocket.active = true;
        rocket.inStock = 1;
        warhead.active = true;
        warhead.inStock = 1;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("rocket1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedLaunch(1, 0, "warhead1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        List<GameState.CombatEvent> events = state.getLastTurnCombatEvents();
        assertEquals(1, events.size());
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, events.get(0).type);
        assertEquals(0, events.get(0).attackerPlayerId);
        assertEquals(1, events.get(0).defenderPlayerId);
        assertEquals(0, events.get(0).cityIndex);
        assertEquals("rocket1", events.get(0).weaponName);
        assertEquals("warhead1", events.get(0).payloadName);
        assertEquals(1, events.get(0).actionBuildingSlot);
        assertTrue(com.example.worlddomination.game.rendering.FlashBattleRoutes
                .supportsRocketStrike(events.get(0)));
        assertTrue(events.get(0).casualtiesMillions > 0);
    }

    @Test
    public void shipStrikeEmitsAnImpactForEachResolvedPayload() {
        GameState state = new GameState(1, 122L, false);
        GameState.WeaponState ship = state.getHuman().getWeapons().get("ship");
        GameState.WeaponState warhead = state.getHuman().getWeapons().get("warhead1");
        ship.active = true;
        ship.inStock = 1;
        warhead.active = true;
        warhead.inStock = 3;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("ship"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedShipStrike(1, 0, "warhead1"));
        assertTrue(state.configurePreparedShipStrike(1, 1, "warhead1"));
        assertTrue(state.configurePreparedShipStrike(1, 2, "warhead1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        List<GameState.CombatEvent> events = state.getLastTurnCombatEvents();
        assertEquals(3, events.size());
        for (int cityIndex = 0; cityIndex < events.size(); cityIndex++) {
            GameState.CombatEvent event = events.get(cityIndex);
            assertEquals(GameState.CombatEventType.STRIKE_IMPACT, event.type);
            assertEquals(cityIndex, event.cityIndex);
            assertEquals("ship", event.weaponName);
            assertEquals("warhead1", event.payloadName);
            assertTrue(FlashBattleRoutes.supportsCarrierPlaneStrike(event));
            FlashBattleRoutes.CarrierPlaneStrike route =
                    FlashBattleRoutes.createCarrierPlaneStrike(event.attackerPlayerId,
                            event.defenderPlayerId, event.cityIndex, cityIndex, false);
            assertEquals(cityIndex, route.planeIndex);
            assertTrue(event.casualtiesMillions > 0);
        }
    }

    @Test
    public void attackThatBecomesUnavailableDoesNotEmitCombatEvent() {
        GameState state = new GameState(1, 123L, false);
        GameState.WeaponState soldier = state.getHuman().getWeapons().get("soldier1");
        soldier.active = true;
        soldier.inStock = 1;

        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        soldier.active = false;
        state.confirmTurn();

        assertTrue(state.getLastTurnCombatEvents().isEmpty());
    }

    @Test
    public void directAttacksUseSourceUniqueBuildingOrderForApacheLaunchSlot() {
        GameState state = new GameState(1, 124L, false);
        activateWeapon(state, "soldier1", 1);
        activateWeapon(state, "soldier2", 1);

        queueAttack(state, 1, 0, "soldier1");
        queueAttack(state, 1, 1, "soldier2");
        state.confirmTurn();

        List<GameState.CombatEvent> events = state.getLastTurnCombatEvents();
        assertEquals(2, events.size());
        assertEquals("soldier1", events.get(0).weaponName);
        assertEquals(1, events.get(0).actionBuildingSlot);
        assertEquals("soldier2", events.get(1).weaponName);
        assertEquals(2, events.get(1).actionBuildingSlot);
    }

    @Test
    public void repeatedApacheAttacksReuseTheDeduplicatedHelipadSlot() {
        GameState state = new GameState(1, 125L, false);
        activateWeapon(state, "soldier2", 2);

        queueAttack(state, 1, 0, "soldier2");
        queueAttack(state, 1, 1, "soldier2");
        state.confirmTurn();

        List<GameState.CombatEvent> events = state.getLastTurnCombatEvents();
        assertEquals(2, events.size());
        assertEquals(1, events.get(0).actionBuildingSlot);
        assertEquals(1, events.get(1).actionBuildingSlot);
    }

    @Test
    public void marineDirectAttackUsesTheOriginalSealGunCue() {
        GameState state = new GameState(1, 126L, false);
        activateWeapon(state, "soldier2", 1);
        queueAttack(state, 1, 0, "soldier2");
        queueResources(state);
        state.confirmTurn();

        assertEquals(OriginalAudio.Cue.SEAL_GUN,
                OriginalAudio.cueForCombatEvent(state.getLastTurnCombatEvents().get(0)));
    }

    @Test
    public void tankDirectAttackUsesItsSourceSlotAndChinookSound() {
        GameState state = new GameState(1, 128L, false);
        activateWeapon(state, "soldier3", 1);
        queueAttack(state, 1, 0, "soldier3");
        queueResources(state);
        state.confirmTurn();

        GameState.CombatEvent event = state.getLastTurnCombatEvents().get(0);
        assertEquals("soldier3", event.weaponName);
        assertEquals(1, event.actionBuildingSlot);
        assertEquals(OriginalAudio.Cue.TANK_CHOPPER,
                OriginalAudio.cueForCombatEvent(event));
    }

    @Test
    public void suicideBomberAttackUsesTheHatchSlotAndOriginalScreamCue() {
        GameState state = new GameState(1, 127L, false);
        activateWeapon(state, "soldier4", 1);
        queueAttack(state, 1, 0, "soldier4");
        queueResources(state);
        state.confirmTurn();

        GameState.CombatEvent event = state.getLastTurnCombatEvents().get(0);
        assertEquals(1, event.actionBuildingSlot);
        assertEquals(OriginalAudio.Cue.SUICIDE_SCREAM,
                OriginalAudio.cueForCombatEvent(event));
    }

    private static void activateWeapon(GameState state, String name, int stock) {
        GameState.WeaponState weapon = state.getHuman().getWeapons().get(name);
        weapon.active = true;
        weapon.inStock = stock;
    }

    private static void queueAttack(GameState state, int targetPlayerId,
                                    int cityIndex, String weaponName) {
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(targetPlayerId, cityIndex, weaponName));
        assertTrue(state.acceptSelectedAction());
    }

    private static void queueResources(GameState state) {
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
    }
}
