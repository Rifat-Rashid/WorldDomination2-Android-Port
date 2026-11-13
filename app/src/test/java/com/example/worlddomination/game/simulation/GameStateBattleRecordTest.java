package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Source battle records have a clock independent of visible combat and building clips. */
public final class GameStateBattleRecordTest {
    @Test
    public void resourceAndReligionRecordsUseSourceGroupsRatherThanSelectionOrder() {
        GameState state = new GameState(1, 17L, false);
        queue(state, GameState.Action.MASS_MEDIA);
        queue(state, GameState.Action.RESOURCES);
        state.confirmTurn();

        List<GameState.BattlePresentationRecord> records = state.getLastTurnBattleRecords();
        assertEquals(2, records.size());
        assertRecord(records.get(0), 1, 0, GameState.Action.RESOURCES, true);
        assertRecord(records.get(1), 2, 0, GameState.Action.MASS_MEDIA, true);
        assertEquals(1, state.getLastTurnActionBuildings().get(0).battleStartNum);
        assertEquals(2, state.getLastTurnActionBuildings().get(1).battleStartNum);
    }

    @Test
    public void duplicateBuildingStillKeepsTwoRecordsAndSnapshotSurvivesNextTurn() {
        GameState state = new GameState(1, 19L, false);
        queue(state, GameState.Action.RESOURCES);
        queue(state, GameState.Action.RESOURCES);
        state.confirmTurn();

        List<GameState.BattlePresentationRecord> first = state.getLastTurnBattleRecords();
        assertEquals(2, first.size());
        assertRecord(first.get(0), 1, 0, GameState.Action.RESOURCES, true);
        assertRecord(first.get(1), 2, 0, GameState.Action.RESOURCES, true);
        assertEquals(1, state.getLastTurnActionBuildings().size());
        assertEquals(1, state.getLastTurnActionBuildings().get(0).battleStartNum);
        assertThrows(UnsupportedOperationException.class, () -> first.clear());

        queue(state, GameState.Action.SKIP);
        queue(state, GameState.Action.SKIP);
        state.confirmTurn();
        assertTrue(state.getLastTurnBattleRecords().isEmpty());
        assertEquals(2, first.size());
    }

    @Test
    public void acceptedAttackCancelledBeforeResolutionStillOccupiesRecord() {
        GameState state = new GameState(1, 123L, false);
        GameState.WeaponState soldier = state.getHuman().getWeapons().get("soldier1");
        soldier.active = true;
        soldier.inStock = 1;
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
        queue(state, GameState.Action.RESOURCES);
        soldier.active = false;
        state.confirmTurn();

        List<GameState.BattlePresentationRecord> records = state.getLastTurnBattleRecords();
        assertEquals(2, records.size());
        assertRecord(records.get(0), 1, 0, GameState.Action.RESOURCES, true);
        assertRecord(records.get(1), 2, 0, GameState.Action.ATTACK, false);
        assertTrue(state.getLastTurnCombatEvents().isEmpty());
        assertEquals(2, state.getLastTurnActionBuildings().get(0).battleStartNum);
    }

    @Test
    public void battlePrecedesPropagandaAndPropagandaPrecedesDiplomacy() {
        GameState battle = new GameState(1, 124L, false);
        GameState.WeaponState soldier = battle.getHuman().getWeapons().get("soldier1");
        soldier.active = true;
        soldier.inStock = 1;
        battle.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(battle.configurePropaganda(1, 0));
        assertTrue(battle.acceptSelectedAction());
        battle.selectAction(GameState.Action.ATTACK);
        assertTrue(battle.configureAttack(1, 0, "soldier1"));
        assertTrue(battle.acceptSelectedAction());
        battle.confirmTurn();
        assertRecord(battle.getLastTurnBattleRecords().get(0), 1, 0,
                GameState.Action.ATTACK, false);
        assertRecord(battle.getLastTurnBattleRecords().get(1), 2, 0,
                GameState.Action.PROPAGANDA, true);
        assertEquals(1, battle.getLastTurnCombatEvents().get(0).battleStartNum);

        GameState diplomacy = new GameState(1, 29L, false);
        diplomacy.selectAction(GameState.Action.DIPLOMACY);
        assertTrue(diplomacy.canAcceptDiplomacy());
        assertTrue(diplomacy.acceptSelectedAction());
        diplomacy.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(diplomacy.configurePropaganda(1, 0));
        assertTrue(diplomacy.acceptSelectedAction());
        diplomacy.confirmTurn();
        assertRecord(diplomacy.getLastTurnBattleRecords().get(0), 1, 0,
                GameState.Action.PROPAGANDA, true);
        assertRecord(diplomacy.getLastTurnBattleRecords().get(1), 2, 0,
                GameState.Action.DIPLOMACY, true);
    }

    @Test
    public void shipPreparationHasSilentRecordAndLaunchPayloadsShareNextRecord() {
        GameState state = new GameState(1, 130L, false);
        GameState.WeaponState ship = state.getHuman().getWeapons().get("ship");
        GameState.WeaponState warhead = state.getHuman().getWeapons().get("warhead1");
        ship.active = true;
        ship.inStock = 1;
        warhead.active = true;
        warhead.inStock = 2;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("ship"));
        assertTrue(state.acceptSelectedAction());
        queue(state, GameState.Action.RESOURCES);
        state.confirmTurn();
        List<GameState.BattlePresentationRecord> preparation = state.getLastTurnBattleRecords();
        assertEquals(2, preparation.size());
        assertRecord(preparation.get(1), 2, 0, GameState.Action.PREPARED_ATTACK, false);
        assertTrue(preparation.get(1).prepareCarrier);
        assertEquals("ship", preparation.get(1).weaponName);

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedShipStrike(1, 0, "warhead1"));
        assertTrue(state.configurePreparedShipStrike(1, 1, "warhead1"));
        assertTrue(state.acceptSelectedAction());
        queue(state, GameState.Action.RESOURCES);
        state.confirmTurn();
        List<GameState.BattlePresentationRecord> launch = state.getLastTurnBattleRecords();
        assertEquals(2, launch.size());
        assertRecord(launch.get(1), 2, 0, GameState.Action.PREPARED_ATTACK, false);
        assertFalse(launch.get(1).prepareCarrier);
        List<GameState.CombatEvent> payloads = state.getLastTurnCombatEvents();
        assertEquals(2, payloads.size());
        assertEquals(2, payloads.get(0).battleStartNum);
        assertEquals(2, payloads.get(1).battleStartNum);
        assertEquals(1, payloads.get(0).shipPayloadIndex);
        assertEquals(0, payloads.get(1).shipPayloadIndex);
    }

    @Test
    public void onlySubAndShipPreparationJoinBattleRecords() {
        for (String carrierName : new String[]{"sub", "ship", "rocket1", "plane1", "space"}) {
            GameState state = new GameState(1, 137L, false);
            GameState.WeaponState carrier = state.getHuman().getWeapons().get(carrierName);
            carrier.active = true;
            carrier.inStock = 1;
            state.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(carrierName, state.configurePreparedCarrier(carrierName));
            assertTrue(state.acceptSelectedAction());
            queue(state, GameState.Action.RESOURCES);
            state.confirmTurn();
            List<GameState.BattlePresentationRecord> records = state.getLastTurnBattleRecords();
            boolean included = "sub".equals(carrierName) || "ship".equals(carrierName);
            assertEquals(carrierName, included ? 2 : 1, records.size());
            if (included) {
                assertRecord(records.get(1), 2, 0, GameState.Action.PREPARED_ATTACK,
                        "sub".equals(carrierName));
            }
        }
    }

    @Test
    public void aiRecordsRemainSortedBySourceGroupThenPlayer() {
        GameState state = new GameState(1, 41L, true);
        queue(state, GameState.Action.MASS_MEDIA);
        queue(state, GameState.Action.RESOURCES);
        state.confirmTurn();
        List<GameState.BattlePresentationRecord> records = state.getLastTurnBattleRecords();
        assertTrue(records.size() >= 2);
        boolean sawOpponentRecord = false;
        assertRecord(records.get(0), 1, 0, GameState.Action.RESOURCES, true);
        int priorPriority = -1;
        int priorPlayer = -1;
        for (int index = 0; index < records.size(); index++) {
            GameState.BattlePresentationRecord record = records.get(index);
            sawOpponentRecord |= record.playerId > 0;
            assertEquals(index + 1, record.startNum);
            int priority = priority(record.action);
            assertTrue(priority >= priorPriority);
            if (priority == priorPriority) assertTrue(record.playerId >= priorPlayer);
            priorPriority = priority;
            priorPlayer = record.playerId;
        }
        assertTrue("Seeded AI turn must exercise cross-player record order",
                sawOpponentRecord);
    }

    private static int priority(GameState.Action action) {
        switch (action) {
            case RESOURCES: return 0;
            case MASS_MEDIA: return 1;
            case ATTACK:
            case PREPARED_ATTACK: return 2;
            case PROPAGANDA: return 3;
            case DIPLOMACY: return 4;
            default: throw new AssertionError("Not a battle-script action: " + action);
        }
    }

    private static void queue(GameState state, GameState.Action action) {
        state.selectAction(action);
        assertTrue(state.acceptSelectedAction());
    }

    private static void assertRecord(GameState.BattlePresentationRecord record, int startNum,
                                     int playerId, GameState.Action action,
                                     boolean pendingBuilding) {
        assertEquals(startNum, record.startNum);
        assertEquals(playerId, record.playerId);
        assertEquals(action, record.action);
        assertEquals(pendingBuilding, record.pendingBuilding);
    }
}
