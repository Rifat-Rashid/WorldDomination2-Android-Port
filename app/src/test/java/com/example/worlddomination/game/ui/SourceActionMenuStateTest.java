package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SourceActionMenuStateTest {
    private GameState game;
    private SourceActionMenuState menu;
    @Before public void create() { game = new GameState(1, 17L); menu = new SourceActionMenuState(); }
    private void begin(GameState.Action action) { game.selectAction(action); menu.begin(game); }
    private void stock(String name, int count) {
        GameState.WeaponState weapon = game.getHuman().getWeapons().get(name);
        weapon.active = true; weapon.inStock = count;
    }
    @Test public void directAttackRequiresExplicitWeaponThenMapCity() {
        stock("soldier1", 1); begin(GameState.Action.ATTACK);
        assertEquals("attackSelect", menu.getKey()); assertFalse(menu.isAcceptVisible());
        assertFalse(menu.chooseCity(game, 1, 0));
        assertTrue(menu.chooseEquipment(game, "soldier1"));
        assertEquals("attack1", menu.getKey()); assertTrue(menu.isMapSelection());
        assertTrue(menu.chooseCity(game, 1, 0)); assertEquals("attack2-soldier1", menu.getKey());
        assertTrue(menu.accept(game)); assertEquals(GameState.Action.ATTACK, game.getQueuedAction(0));
    }
    @Test public void attackPreviewDoesNotUseInvalidPreviousTarget() {
        stock("soldier1", 1); begin(GameState.Action.ATTACK);
        game.getPlayerState(1).alive = false;
        assertTrue(menu.chooseEquipment(game, "soldier1"));
        assertTrue(menu.chooseCity(game, 2, 2));
        assertEquals(2, game.getSelectedTargetPlayer());
    }
    @Test public void spyBuildingSelectionUsesOriginalFiveChoices() {
        begin(GameState.Action.ATTACK); assertTrue(menu.chooseEquipment(game, "spy1"));
        assertTrue(menu.chooseCity(game, 1, 0));
        assertTrue(menu.getKey().startsWith("attack-spy-"));
        assertTrue(menu.chooseSpyBuilding(game, 4)); assertEquals("factory", game.getSelectedSpyBuilding());
        assertFalse(menu.chooseSpyBuilding(game, 5)); assertTrue(menu.accept(game));
    }
    @Test public void spyReadinessErrorCanBeDismissedWithoutQueuing() {
        stock("spy4", 1); begin(GameState.Action.ATTACK);
        assertFalse(menu.chooseEquipment(game, "spy4")); assertEquals("spy_error", menu.getKey());
        assertFalse(menu.isAcceptVisible()); menu.dismissError(game);
        assertEquals("attackSelect", menu.getKey()); assertEquals(0, game.getQueuedActionCount());
    }
    @Test public void propagandaStartsOnMapThenShowsOriginalStrengthCard() {
        begin(GameState.Action.PROPAGANDA); assertEquals("propaganda1", menu.getKey());
        assertTrue(menu.isMapSelection()); assertFalse(menu.accept(game));
        assertTrue(menu.chooseCity(game, 1, 3)); assertEquals("propaganda2", menu.getKey());
        assertTrue(menu.accept(game)); assertEquals(GameState.Action.PROPAGANDA, game.getQueuedAction(0));
    }
    @Test public void propagandaRejectsHumanDeadAndEmptyCities() {
        begin(GameState.Action.PROPAGANDA); assertFalse(menu.chooseCity(game, 0, 0));
        game.getPlayerState(1).alive = false; assertFalse(menu.chooseCity(game, 1, 0));
        game.getPlayerState(2).getCity(0).populationMillions = 0;
        assertFalse(menu.chooseCity(game, 2, 0)); assertFalse(menu.isAcceptVisible());
    }
    @Test public void defenseUsesEquipmentPaletteAndRejectsAttackWeapons() {
        begin(GameState.Action.DEFENSE); assertEquals("defenseSelect", menu.getKey());
        assertFalse(menu.accept(game)); assertFalse(menu.chooseEquipment(game, "spy1"));
        assertTrue(menu.chooseEquipment(game, "antiAir")); assertEquals("defense-antiAir", menu.getKey());
        assertTrue(menu.chooseEquipment(game, "antiRocket")); assertEquals("defense-antiRocket", menu.getKey());
        assertTrue(menu.accept(game)); assertEquals(GameState.Action.DEFENSE, game.getQueuedAction(0));
    }
    @Test public void emptyDefenseStockDoesNotRevealConfirmation() {
        stock("antiAir", 0); begin(GameState.Action.DEFENSE);
        assertFalse(menu.chooseEquipment(game, "antiAir")); assertFalse(menu.isAcceptVisible());
    }
    @Test public void diplomacyRequiresBothPortraitAndOption() {
        begin(GameState.Action.DIPLOMACY); game.getHuman().turnMoneyMillions = 100; assertFalse(menu.isAcceptVisible());
        assertTrue(menu.chooseOption(game, 2)); assertFalse(menu.isAcceptVisible());
        assertTrue(menu.chooseLeader(game, 2)); assertTrue(menu.isAcceptVisible());
        assertEquals(10, game.getSelectedDiplomacyDuration()); assertEquals(90, game.getSelectedDiplomacyAmount());
        assertTrue(menu.accept(game)); assertEquals(GameState.Action.DIPLOMACY, game.getQueuedAction(0));
    }
    @Test public void diplomacyDoesNotAcceptInvalidPortraitOrOption() {
        begin(GameState.Action.DIPLOMACY); assertFalse(menu.chooseLeader(game, 0));
        assertFalse(menu.chooseLeader(game, 5)); game.getPlayerState(1).alive = false;
        assertFalse(menu.chooseLeader(game, 1)); assertFalse(menu.chooseOption(game, -1));
        assertFalse(menu.chooseOption(game, 6)); assertFalse(menu.isAcceptVisible());
    }
    @Test public void unaffordableOfferShowsWarningOnOptionTapWithoutSelectingIt() {
        begin(GameState.Action.DIPLOMACY); game.getHuman().turnMoneyMillions = 0;
        assertTrue(menu.chooseLeader(game, 1)); assertFalse(menu.chooseOption(game, 0));
        assertEquals("diplomacy_error", menu.getKey()); assertFalse(menu.accept(game));
        menu.dismissError(game); assertEquals("diplomacy", menu.getKey());
        assertTrue(menu.hasLeader()); assertFalse(menu.hasOption()); assertEquals(0, game.getQueuedActionCount());
    }
    @Test public void demandDoesNotReserveHumanMoney() {
        begin(GameState.Action.DIPLOMACY); game.getHuman().turnMoneyMillions = 0;
        assertTrue(menu.chooseLeader(game, 1)); assertTrue(menu.chooseOption(game, 5));
        assertTrue(menu.accept(game)); assertEquals(0, game.getHuman().turnMoneyMillions);
    }
    @Test public void carrierPreparationUsesOriginalStepOneAndQueuesRealAction() {
        stock("rocket1", 1); begin(GameState.Action.PREPARED_ATTACK);
        assertEquals("preAttackSelect", menu.getKey()); assertFalse(menu.isAcceptVisible());
        assertTrue(menu.chooseEquipment(game, "rocket1")); assertEquals("preAttack-rocket1", menu.getKey());
        assertTrue(menu.accept(game)); assertEquals(GameState.Action.PREPARED_ATTACK, game.getQueuedAction(0));
    }
    private void readyCarrier(String carrier) {
        stock(carrier, 1); stock("warhead1", 3); stock("bioload1", 1);
        begin(GameState.Action.PREPARED_ATTACK); assertTrue(menu.chooseEquipment(game, carrier));
        assertTrue(menu.accept(game)); game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction()); game.confirmTurn(); begin(GameState.Action.PREPARED_ATTACK);
    }
    @Test public void preparedPayloadRequiresMapTargetBeforeStepTwo() {
        readyCarrier("rocket1"); assertEquals("preAttack2", menu.getKey());
        assertTrue(menu.chooseEquipment(game, "warhead1")); assertEquals("preAttack4", menu.getKey());
        assertTrue(menu.isMapSelection()); assertFalse(menu.isAcceptVisible());
        assertTrue(menu.chooseCity(game, 1, 0)); assertEquals("preAttack3-warhead", menu.getKey());
        assertTrue(menu.accept(game));
    }
    @Test public void preparedCancellationAsksBeforeDiscardingConsumedCarrier() {
        readyCarrier("rocket1"); int money = game.getHuman().moneyMillions;
        assertFalse(menu.cancel(game)); assertEquals("preCancel", menu.getKey());
        assertEquals("rocket1", game.getPreparedCarrierName(0)); assertTrue(menu.accept(game));
        assertNull(game.getPreparedCarrierName(0)); assertEquals(0, game.getWeaponStock(0, "rocket1"));
        assertEquals(money, game.getHuman().moneyMillions); assertEquals(0, game.getQueuedActionCount());
    }
    @Test public void decliningPreparedCancellationKeepsReadyCarrier() {
        readyCarrier("rocket1"); assertFalse(menu.cancel(game)); assertTrue(menu.cancel(game));
        assertEquals("rocket1", game.getPreparedCarrierName(0));
    }
    @Test public void shipSelectionCountsThreePayloadsAndKeepsOneNation() {
        readyCarrier("ship"); assertEquals("ship1-0", menu.getKey());
        for (int city = 0; city < 3; city++) {
            assertTrue(menu.chooseEquipment(game, "warhead1")); assertTrue(menu.isMapSelection());
            if (city > 0) assertFalse(menu.chooseCity(game, 2, city));
            assertTrue(menu.chooseCity(game, 1, city));
        }
        assertEquals("ship2", menu.getKey()); assertTrue(menu.accept(game));
    }
    @Test public void cancelShipSelectionRetainsCarrierAndRestoresPayloadAvailability() {
        readyCarrier("ship"); assertTrue(menu.chooseEquipment(game, "warhead1"));
        assertTrue(menu.chooseCity(game, 1, 0)); assertTrue(menu.cancel(game));
        assertEquals("ship", game.getPreparedCarrierName(0)); assertEquals(0, game.getSelectedShipStrikeCount());
        assertEquals(3, game.getWeaponStock(0, "warhead1"));
    }
    @Test public void newMenuDoesNotReuseOldWeaponAndCityChoices() {
        stock("soldier1", 1); begin(GameState.Action.ATTACK);
        assertTrue(menu.chooseEquipment(game, "soldier1")); assertTrue(menu.chooseCity(game, 1, 0));
        assertTrue(menu.cancel(game)); begin(GameState.Action.ATTACK);
        assertEquals("attackSelect", menu.getKey()); assertFalse(menu.isAcceptVisible());
    }
    @Test public void spaceCarrierInformationUsesSourceClampedSatelliteFrame() {
        stock("space", 1); stock("specialLoad", 1); game.getHuman().religion = 65;
        begin(GameState.Action.PREPARED_ATTACK); assertTrue(menu.chooseEquipment(game, "space"));
        assertEquals("preAttack-space-25", menu.getKey()); assertTrue(menu.isAcceptVisible());
        assertTrue(menu.cancel(game)); game.getHuman().religion = 12;
        begin(GameState.Action.PREPARED_ATTACK); assertTrue(menu.chooseEquipment(game, "space"));
        assertEquals("preAttack-space-12", menu.getKey());
    }
    @Test public void pendingMoneyWarningIsIndependentOfPreviousSpyWarning() {
        stock("spy4", 1); begin(GameState.Action.ATTACK);
        assertFalse(menu.chooseEquipment(game, "spy4")); assertEquals("spy", menu.getError());
        assertTrue(menu.cancel(game)); assertTrue(game.submitDiplomacyProposal(1, 0, 3));
        assertFalse(menu.hasPendingError()); game.getHuman().turnMoneyMillions = 0;
        assertFalse(game.respondToDiplomacyProposal(true)); menu.showMoneyError(game);
        assertTrue(menu.hasPendingError()); menu.dismissError(game); assertFalse(menu.hasPendingError());
        assertNotNull(game.getPendingHumanDiplomacyProposal());
    }

}
