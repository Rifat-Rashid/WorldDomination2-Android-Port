package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ActionSlotAnimationTest {
    @Test
    public void bothEmptySlotsStartOpenWithoutInventingQueuedActions() {
        GameState state = new GameState(1, 17L);
        ActionSlotAnimation animation = new ActionSlotAnimation();
        animation.synchronize(state, false, false, 0);
        assertEquals(10, animation.getFrame(0));
        assertEquals(10, animation.getFrame(1));
        assertNull(animation.getDisplayedAction(0));
        assertNull(animation.getDisplayedAction(1));
        assertEquals(0, state.getQueuedActionCount());
        assertFalse(animation.isAnimating());
    }

    @Test
    public void selectingPreviewsTheFirstFreeSlotAndAcceptingKeepsItsLabel() {
        GameState state = new GameState(1, 17L);
        ActionSlotAnimation animation = new ActionSlotAnimation();
        state.selectAction(GameState.Action.RESOURCES);
        animation.synchronize(state, false, false, 0);
        assertEquals(GameState.Action.RESOURCES, animation.getDisplayedAction(0));
        assertNull(animation.getDisplayedAction(1));
        assertEquals(0, state.getQueuedActionCount());
        for (int tick = 0; tick < 9; tick++) {
            animation.tick();
        }
        assertEquals(1f, animation.getLabelAlpha(0), 0f);
        assertTrue(state.acceptSelectedAction());
        animation.synchronize(state, false, false, 0);
        animation.tick();
        assertEquals(11, animation.getFrame(0));
        assertEquals(GameState.Action.RESOURCES, animation.getDisplayedAction(0));
        state.selectAction(GameState.Action.MASS_MEDIA);
        animation.synchronize(state, false, false, 0);
        assertEquals(GameState.Action.RESOURCES, animation.getDisplayedAction(0));
        assertEquals(GameState.Action.MASS_MEDIA, animation.getDisplayedAction(1));
        assertEquals(1, state.getQueuedActionCount());
    }

    @Test
    public void cancellingPreviewClearsItsLabelAndKeepsTheSlotOpen() {
        GameState state = new GameState(1, 17L);
        ActionSlotAnimation animation = new ActionSlotAnimation();
        state.selectAction(GameState.Action.PRODUCTION);
        animation.synchronize(state, false, false, 0);
        assertEquals(GameState.Action.PRODUCTION, animation.getDisplayedAction(0));
        state.cancelSelection();
        animation.synchronize(state, false, false, 0);
        animation.tick();
        assertNull(animation.getDisplayedAction(0));
        assertEquals(10, animation.getFrame(0));
        assertEquals(0, state.getQueuedActionCount());
    }

    @Test
    public void bothAcceptedSlotsRemainSetAndClearingReturnsThemToOpen() {
        GameState state = new GameState(1, 17L);
        ActionSlotAnimation animation = new ActionSlotAnimation();
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        animation.synchronize(state, false, false, 0);
        animation.tick();
        assertEquals(11, animation.getFrame(0));
        assertEquals(11, animation.getFrame(1));
        state.clearQueuedActions();
        animation.synchronize(state, false, false, 0);
        animation.tick();
        assertEquals(10, animation.getFrame(0));
        assertEquals(10, animation.getFrame(1));
        assertNull(animation.getDisplayedAction(0));
        assertNull(animation.getDisplayedAction(1));
    }

    @Test
    public void confirmationClosesFirstTubeThenSecondAfterTwoHundredMilliseconds() {
        GameState state = new GameState(1, 17L);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        ActionSlotAnimation animation = new ActionSlotAnimation();
        animation.synchronize(state, false, false, 0);
        animation.tick();
        animation.synchronize(state, false, true, 0);
        animation.tick();
        assertEquals(10, animation.getFrame(0));
        assertEquals(11, animation.getFrame(1));
        animation.synchronize(state, false, true, 0.2f);
        animation.tick();
        assertEquals(9, animation.getFrame(0));
        assertEquals(10, animation.getFrame(1));
        for (int i = 0; i < 10; i++) animation.tick();
        assertEquals(1, animation.getFrame(0));
        assertEquals(1, animation.getFrame(1));
        assertEquals(0f, animation.getLabelAlpha(0), 0f);
        assertEquals(2, state.getQueuedActionCount());
    }

    @Test
    public void sourceBattleKeepsHumanTubesClosedAndNewTurnRestoresDefaultOpen() {
        GameState state = new GameState(1, 17L);
        ActionSlotAnimation animation = new ActionSlotAnimation();
        animation.synchronize(state, true, false, 0);
        for (int tick = 0; tick < 10; tick++) animation.tick();
        assertEquals(1, animation.getFrame(0));
        assertEquals(1, animation.getFrame(1));
        assertNull(animation.getDisplayedAction(0));
        assertNull(animation.getDisplayedAction(1));
        assertFalse(animation.isAnimating());
        animation.synchronize(state, false, false, 0);
        for (int tick = 0; tick < 9; tick++) animation.tick();
        assertEquals(10, animation.getFrame(0));
        assertEquals(10, animation.getFrame(1));
    }
}
