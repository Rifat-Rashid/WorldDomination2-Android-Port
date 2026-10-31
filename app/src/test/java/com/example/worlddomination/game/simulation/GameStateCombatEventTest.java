package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.rendering.FlashBattleRoutes;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class GameStateCombatEventTest {
    @Test
    public void antiDefenseActionsUseTheirDistinctSourceBuildingFrames() {
        assertEquals("antiAir_fire", GameState.sourceDefenseBuildingFrame("antiAir"));
        assertEquals("antiRocket_fire",
                GameState.sourceDefenseBuildingFrame("antiRocket"));
    }

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
        assertEquals("base", state.getLastTurnActionBuildings().get(0).frameName);
        assertEquals(1, state.getLastTurnActionBuildings().get(0).slot);
        assertEquals(OriginalAudio.Cue.SOLDIER_GUN,
                OriginalAudio.cueForCombatEvent(events.get(0)));
    }

    @Test
    public void resolvedSpyMissionEmitsTheChosenOriginalBuildingAndSourceSlot() {
        GameState state = new GameState(1, 122L, false);
        GameState.WeaponState spy = state.getHuman().getWeapons().get("spy1");
        spy.active = true;
        spy.inStock = 1;

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "spy1"));
        state.cycleSpyBuilding(1);
        assertEquals("tech", state.getSelectedSpyBuilding());
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(1, state.getLastTurnSpyMissions().size());
        GameState.SpyMissionEvent event = state.getLastTurnSpyMissions().get(0);
        assertEquals(0, event.attackerPlayerId);
        assertEquals(1, event.defenderPlayerId);
        assertEquals(2, event.actionBuildingSlot);
        assertEquals("tech", event.actionBuildingFrame);
        assertEquals(state.getHuman().getReligionCode(), event.religionCode);

        queueResources(state);
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertTrue(state.getLastTurnSpyMissions().isEmpty());
    }

    @Test
    public void spyActionBuildingUsesSourceSpyCenterFrameWhenItsBuildingIsDuplicated() {
        GameState state = new GameState(1, 123L, false);
        GameState.WeaponState spy = state.getHuman().getWeapons().get("spy1");
        spy.active = true;
        spy.inStock = 1;

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "spy1"));
        state.cycleSpyBuilding(1);
        state.cycleSpyBuilding(1);
        state.cycleSpyBuilding(1);
        assertEquals("resources", state.getSelectedSpyBuilding());
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        GameState.SpyMissionEvent event = state.getLastTurnSpyMissions().get(0);
        assertEquals(1, event.actionBuildingSlot);
        assertEquals("spyCenter", event.actionBuildingFrame);
    }

    @Test
    public void spyAfterPreparedCarrierUsesTheSecondSourceActionBuildingSlot() {
        GameState state = new GameState(1, 124L, false);
        GameState.WeaponState rocket = state.getHuman().getWeapons().get("rocket1");
        GameState.WeaponState spy = state.getHuman().getWeapons().get("spy1");
        rocket.active = true;
        rocket.inStock = 1;
        spy.active = true;
        spy.inStock = 1;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("rocket1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "spy1"));
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(2, state.getLastTurnSpyMissions().get(0).actionBuildingSlot);
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
        assertEquals("silo", state.getLastTurnActionBuildings().get(0).frameName);

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
        assertEquals("silo", state.getLastTurnActionBuildings().get(0).frameName);
        assertTrue(com.example.worlddomination.game.rendering.FlashBattleRoutes
                .supportsRocketStrike(events.get(0)));
        assertTrue(events.get(0).casualtiesMillions > 0);
    }

    @Test
    public void resolvedPreparedAircraftAttackCarriesItsAirfieldSlotForNativePlayback() {
        GameState state = new GameState(1, 1211L, false);
        GameState.WeaponState plane = state.getHuman().getWeapons().get("plane1");
        GameState.WeaponState warhead = state.getHuman().getWeapons().get("warhead1");
        plane.active = true;
        plane.inStock = 1;
        warhead.active = true;
        warhead.inStock = 1;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("plane1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedLaunch(1, 3, "warhead1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        GameState.CombatEvent event = state.getLastTurnCombatEvents().get(0);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, event.type);
        assertEquals("plane1", event.weaponName);
        assertEquals(1, event.actionBuildingSlot);
        assertTrue(FlashBattleRoutes.supportsStandalonePlaneStrike(event));
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(event.attackerPlayerId,
                        event.defenderPlayerId, event.cityIndex, event.actionBuildingSlot,
                        event.weaponName, false);
        assertEquals("plane001", route.linkage);
        assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3,
                OriginalAudio.cueForCombatEvent(event));
        assertEquals("explosion_warhead_1",
                com.example.worlddomination.game.rendering.SourceCombatAnimationMapping
                        .linkageFor(event));
    }

    @Test
    public void antiAirCounterRetainsItsActionBuildingSlotOnAirStrikeOutcomes()
            throws Exception {
        boolean sawInterception = false;
        boolean sawImpact = false;
        for (long seed = 1; seed <= 128 && !(sawInterception && sawImpact); seed++) {
            GameState.CombatEvent event = resolveAntiAirPlaneStrike(seed);
            if (event == null) {
                continue;
            }
            assertEquals("antiAir", event.counterWeaponName);
            assertTrue("The human's anti-air building uses source slot one or two",
                    event.counterActionBuildingSlot == 1
                            || event.counterActionBuildingSlot == 2);
            assertTrue(FlashBattleRoutes.supportsStandalonePlaneStrike(event));
            sawInterception |= event.type == GameState.CombatEventType.STRIKE_INTERCEPTED;
            sawImpact |= event.type == GameState.CombatEventType.STRIKE_IMPACT;
        }
        assertTrue("A seeded anti-air counter should exercise the successful interception path",
                sawInterception);
        assertTrue("A seeded anti-air counter should exercise the failed interception path",
                sawImpact);
    }

    @Test
    public void antiRocketCounterRetainsItsActionBuildingSlotOnRocketOutcomes()
            throws Exception {
        boolean sawInterception = false;
        boolean sawImpact = false;
        for (long seed = 1; seed <= 128 && !(sawInterception && sawImpact); seed++) {
            GameState.CombatEvent event = resolveAntiRocketStrike(seed, false);
            if (event == null) {
                continue;
            }
            assertEquals("antiRocket", event.counterWeaponName);
            assertEquals(1, event.counterActionBuildingSlot);
            assertTrue(FlashBattleRoutes.supportsRocketStrike(event));
            if (event.type == GameState.CombatEventType.STRIKE_INTERCEPTED) {
                assertEquals("explosion002",
                        com.example.worlddomination.game.rendering
                                .SourceCombatAnimationMapping.linkageFor(event));
            }
            sawInterception |= event.type == GameState.CombatEventType.STRIKE_INTERCEPTED;
            sawImpact |= event.type == GameState.CombatEventType.STRIKE_IMPACT;
        }
        assertTrue("A seeded anti-rocket defense should exercise an interception",
                sawInterception);
        assertTrue("A seeded anti-rocket defense should exercise a missed interception",
                sawImpact);
    }

    @Test
    public void antiRocketRetainsTheSecondSourceDefenseBuildingSlot() throws Exception {
        GameState.CombatEvent event = null;
        for (long seed = 1; seed <= 128 && event == null; seed++) {
            event = resolveAntiRocketStrike(seed, true);
        }

        assertTrue("The prepared rocket should resolve", event != null);
        assertEquals("antiRocket", event.counterWeaponName);
        assertEquals("The anti-air frame occupies slot one, leaving the distinct rocket frame slot two",
                2, event.counterActionBuildingSlot);
    }

    private static GameState.CombatEvent resolveAntiAirPlaneStrike(long seed)
            throws Exception {
        GameState state = new GameState(1, seed, true);
        for (int playerId = 2; playerId < 5; playerId++) {
            state.getPlayerState(playerId).alive = false;
        }
        GameState.PlayerState attacker = state.getPlayerState(1);
        activate(attacker, "plane1");
        activate(attacker, "warhead1");
        Field preparedCarrier = GameState.PlayerState.class
                .getDeclaredField("preparedCarrierName");
        preparedCarrier.setAccessible(true);
        preparedCarrier.set(attacker, "plane1");

        GameState.WeaponState antiAir = state.getHuman().getWeapons().get("antiAir");
        antiAir.active = true;
        antiAir.inStock = 1;
        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiAir"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        for (GameState.CombatEvent candidate : state.getLastTurnCombatEvents()) {
            if ("plane1".equals(candidate.weaponName)) {
                return candidate;
            }
        }
        return null;
    }

    private static GameState.CombatEvent resolveAntiRocketStrike(long seed,
                                                                 boolean occupyFirstSlot)
            throws Exception {
        GameState state = new GameState(1, seed, true);
        for (int playerId = 2; playerId < 5; playerId++) {
            state.getPlayerState(playerId).alive = false;
        }
        GameState.PlayerState attacker = state.getPlayerState(1);
        activate(attacker, "rocket1");
        activate(attacker, "warhead1");
        Field preparedCarrier = GameState.PlayerState.class
                .getDeclaredField("preparedCarrierName");
        preparedCarrier.setAccessible(true);
        preparedCarrier.set(attacker, "rocket1");

        if (occupyFirstSlot) {
            activateWeapon(state, "antiAir", 1);
            state.selectAction(GameState.Action.DEFENSE);
            assertTrue(state.configureDefense("antiAir"));
            assertTrue(state.acceptSelectedAction());
        }
        activateWeapon(state, "antiRocket", 1);
        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiRocket"));
        assertTrue(state.acceptSelectedAction());
        if (!occupyFirstSlot) {
            queueResources(state);
        }
        state.confirmTurn();

        for (GameState.CombatEvent candidate : state.getLastTurnCombatEvents()) {
            if ("rocket1".equals(candidate.weaponName)) {
                return candidate;
            }
        }
        return null;
    }

    @Test
    public void resolvedPreparedSubmarineAttackEmitsAPlayablePayloadImpact() {
        GameState state = new GameState(1, 1221L, false);
        GameState.WeaponState submarine = state.getHuman().getWeapons().get("sub");
        GameState.WeaponState warhead = state.getHuman().getWeapons().get("warhead1");
        submarine.active = true;
        submarine.inStock = 1;
        warhead.active = true;
        warhead.inStock = 1;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("sub"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedLaunch(1, 2, "warhead1"));
        assertTrue(state.acceptSelectedAction());
        queueResources(state);
        state.confirmTurn();

        List<GameState.CombatEvent> events = state.getLastTurnCombatEvents();
        assertEquals(1, events.size());
        GameState.CombatEvent event = events.get(0);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, event.type);
        assertEquals(0, event.attackerPlayerId);
        assertEquals(1, event.defenderPlayerId);
        assertEquals(2, event.cityIndex);
        assertEquals("sub", event.weaponName);
        assertEquals("warhead1", event.payloadName);
        assertTrue(FlashBattleRoutes.supportsSubmarineRocketStrike(event));
        FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createSubmarineRocketStrike(
                event.attackerPlayerId, event.defenderPlayerId, event.cityIndex,
                event.type == GameState.CombatEventType.STRIKE_INTERCEPTED);
        assertTrue(route.submarineLaunch);
        assertEquals(0.5f, new com.example.worlddomination.game.rendering.FlashRocketAnimation(
                route, 0).getRocket().scaleY, 0f);
        assertEquals("explosion_warhead_1",
                com.example.worlddomination.game.rendering.SourceCombatAnimationMapping
                        .linkageFor(event));
        assertTrue(event.casualtiesMillions > 0);
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

    private static void activate(GameState.PlayerState player, String weaponName) {
        GameState.WeaponState weapon = player.getWeapons().get(weaponName);
        weapon.active = true;
        weapon.inStock = 1;
    }
}
