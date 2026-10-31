package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashActionBuildingAnimationTest {
    @Test
    public void everyBuildingBeginsItsFadeOnTheSameSourceTick() {
        List<GameState.ActionBuildingEvent> events = resourceAndMediaEvents();
        assertEquals(2, events.size());

        FlashActionBuildingAnimation first =
                new FlashActionBuildingAnimation(events.get(0));
        FlashActionBuildingAnimation second =
                new FlashActionBuildingAnimation(events.get(1));
        assertEquals(0f, first.getAlpha(), 0f);
        assertEquals(0f, second.getAlpha(), 0f);

        for (int tick = 1; tick <= FlashActionBuildingAnimation.FADE_TICKS; tick++) {
            first.tick();
            second.tick();
            float sourceAlpha = tick * 0.05f;
            assertEquals(sourceAlpha, first.getAlpha(), 0.0001f);
            assertEquals(sourceAlpha, second.getAlpha(), 0.0001f);
        }
    }

    @Test
    public void completedBuildingFadeStaysVisibleAtFullOpacity() {
        FlashActionBuildingAnimation animation = new FlashActionBuildingAnimation(
                resourceAndMediaEvents().get(1));

        for (int tick = 0; tick < FlashActionBuildingAnimation.FADE_TICKS; tick++) {
            animation.tick();
        }

        assertTrue(animation.isFadeComplete());
        assertFalse(animation.isAnimating());
        assertEquals(1f, animation.getAlpha(), 0f);
        assertTrue(animation.tick());
        assertEquals(1f, animation.getAlpha(), 0f);
    }

    private static List<GameState.ActionBuildingEvent> resourceAndMediaEvents() {
        GameState state = new GameState(1, 17L);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        List<GameState.ActionBuildingEvent> humanEvents = new ArrayList<>();
        for (GameState.ActionBuildingEvent event : state.getLastTurnActionBuildings()) {
            if (event.playerId == 0) {
                humanEvents.add(event);
            }
        }
        return humanEvents;
    }
}
