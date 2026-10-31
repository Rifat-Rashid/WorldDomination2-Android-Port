package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GameStateActionBuildingEventTest {
    @Test
    public void resourceAndMediaActionsUseOriginalFramesAndAlternateSlots() {
        GameState state = new GameState(1, 17L, false);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());

        state.confirmTurn();

        List<GameState.ActionBuildingEvent> buildings = state.getLastTurnActionBuildings();
        assertEquals(2, buildings.size());
        assertEvent(buildings.get(0), GameState.Action.RESOURCES,
                "resources", 1, 0);
        assertEvent(buildings.get(1), GameState.Action.MASS_MEDIA,
                "religion", 2, 1);
        assertEquals(state.getHuman().getReligionCode(), buildings.get(1).religionCode);
    }

    @Test
    public void resourceAndMediaSlotsFollowSourceQueueRatherThanTapOrder() {
        GameState state = new GameState(1, 18L, false);
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());

        state.confirmTurn();

        List<GameState.ActionBuildingEvent> buildings = state.getLastTurnActionBuildings();
        assertEquals(2, buildings.size());
        assertEvent(buildings.get(0), GameState.Action.RESOURCES,
                "resources", 1, 0);
        assertEvent(buildings.get(1), GameState.Action.MASS_MEDIA,
                "religion", 2, 1);
    }

    @Test
    public void duplicateSamePlayerFrameUsesOnePersistentBuildingSlot() {
        GameState state = new GameState(1, 19L, false);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());

        state.confirmTurn();

        List<GameState.ActionBuildingEvent> buildings = state.getLastTurnActionBuildings();
        assertEquals(1, buildings.size());
        assertEvent(buildings.get(0), GameState.Action.RESOURCES,
                "resources", 1, 0);
    }

    @Test
    public void productionAndResearchActionsMapToTheirOriginalFlashLabels() {
        GameState state = new GameState(1, 23L, false);
        state.selectAction(GameState.Action.PRODUCTION);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESEARCH);
        List<String> choices = state.getAvailableResearch();
        assertTrue("The initial state should have at least one available technology",
                !choices.isEmpty());
        assertTrue(state.selectResearchTechnology(choices.get(0)));
        assertTrue(state.acceptSelectedAction());

        state.confirmTurn();

        List<GameState.ActionBuildingEvent> buildings = state.getLastTurnActionBuildings();
        assertEquals(2, buildings.size());
        assertEvent(buildings.get(0), GameState.Action.PRODUCTION,
                "factory", 1, 0);
        assertEvent(buildings.get(1), GameState.Action.RESEARCH,
                "tech", 2, 1);
    }

    @Test
    public void diplomacyAndPropagandaUseSourceQueueOrderAndOriginalFlashLabels() {
        GameState state = new GameState(1, 29L, false);
        state.selectAction(GameState.Action.DIPLOMACY);
        assertTrue(state.canAcceptDiplomacy());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(state.configurePropaganda(1, 0));
        assertTrue(state.acceptSelectedAction());

        state.confirmTurn();

        List<GameState.ActionBuildingEvent> buildings = state.getLastTurnActionBuildings();
        assertEquals(2, buildings.size());
        assertEvent(buildings.get(0), GameState.Action.PROPAGANDA,
                "propaganda", 1, 0);
        assertEvent(buildings.get(1), GameState.Action.DIPLOMACY,
                "diplomacy", 2, 1);
    }

    @Test
    public void resolvedSpyMissionUsesItsSelectedFlashBuilding() {
        GameState state = new GameState(1, 31L, false);
        GameState.WeaponState spy = state.getHuman().getWeapons().get("spy1");
        spy.active = true;
        spy.inStock = 1;
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "spy1"));
        state.cycleSpyBuilding(1);
        assertEquals("tech", state.getSelectedSpyBuilding());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());

        state.confirmTurn();

        List<GameState.ActionBuildingEvent> buildings = state.getLastTurnActionBuildings();
        assertEquals(2, buildings.size());
        assertEvent(buildings.get(0), GameState.Action.ATTACK, "tech", 1, 0);
        assertEvent(buildings.get(1), GameState.Action.RESOURCES,
                "resources", 2, 1);
    }

    @Test
    public void unavailableSpyMissionDoesNotQueueAnActionBuilding() {
        GameState state = new GameState(1, 32L, false);
        GameState.WeaponState spy = state.getHuman().getWeapons().get("spy1");
        spy.active = true;
        spy.inStock = 1;
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "spy1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        spy.active = false;

        state.confirmTurn();

        List<GameState.ActionBuildingEvent> buildings = state.getLastTurnActionBuildings();
        assertEquals(1, buildings.size());
        assertFalse(buildings.get(0).action == GameState.Action.ATTACK);
        assertEquals(GameState.Action.RESOURCES, buildings.get(0).action);
    }

    private static void assertEvent(GameState.ActionBuildingEvent event,
                                    GameState.Action action, String frame,
                                    int slot, int sequenceIndex) {
        assertEquals(0, event.playerId);
        assertEquals(action, event.action);
        assertEquals(frame, event.frameName);
        assertEquals(slot, event.slot);
        assertEquals(sequenceIndex, event.sequenceIndex);
    }
}
