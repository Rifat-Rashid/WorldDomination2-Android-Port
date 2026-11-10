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
    public void resourcePumpMatchesTheReadOnlySourceFrameTraceAndKeepsLoopingAfterFade() {
        FlashActionBuildingAnimation animation = new FlashActionBuildingAnimation(
                eventFor(GameState.Action.RESOURCES));
        int[] ticks = {0, 1, 10, 24, 31, 32, 48, 64};
        int[] sourceFrames = {1, 2, 11, 25, 1, 2, 18, 3};
        assertFrameTrace(animation, FlashActionBuildingAnimation.RESOURCE_PUMP_SYMBOL_ID,
                31, ticks, sourceFrames);
        assertTrue(animation.isFadeComplete());
        assertTrue(animation.isAnimating());
    }

    @Test
    public void researchLightMatchesTheReadOnlySourceTwentyFiveFrameLoop() {
        FlashActionBuildingAnimation animation = new FlashActionBuildingAnimation(
                eventFor(GameState.Action.RESEARCH));
        int[] ticks = {0, 1, 10, 24, 31, 32, 48, 64};
        int[] sourceFrames = {1, 2, 11, 25, 7, 8, 24, 15};
        assertFrameTrace(animation, FlashActionBuildingAnimation.TECHNOLOGY_LIGHT_SYMBOL_ID,
                25, ticks, sourceFrames);
        assertTrue(animation.isAnimating());
        assertEquals("Religion selector frames must remain externally controlled", 0,
                animation.getNestedFrameIndex(3716, 5));
    }

    @Test
    public void factorySmokeWaitsForItsActionLabelThenStopsAtSourceFrameFortyOne() {
        FlashActionBuildingAnimation animation = new FlashActionBuildingAnimation(
                eventFor(GameState.Action.PRODUCTION));
        // Isolated source playback: tick 76 is stopped at frame 1; the Production
        // label at tick 77 calls play(); ticks 78/116/117 show frames 2/40/41.
        for (int tick = 1; tick <= 77; tick++) {
            animation.tick();
        }
        animation.revealAction(GameState.Action.RESOURCES);
        assertEquals(0, animation.getNestedFrameIndex(3996, 50));
        assertFalse(animation.isAnimating());
        animation.revealAction(GameState.Action.PRODUCTION);
        assertTrue(animation.isAnimating());
        assertFrameTrace(animation, 3996, 50,
                new int[]{0, 1, 39, 40, 83}, new int[]{1, 2, 40, 41, 41});
        assertFalse(animation.isAnimating());
    }

    @Test
    public void reusingTheSameBuildingPreservesItsNestedClockAndRestartsOnlyTheFade() {
        GameState.ActionBuildingEvent event = eventFor(GameState.Action.RESOURCES);
        FlashActionBuildingAnimation previous = new FlashActionBuildingAnimation(event);
        for (int tick = 0; tick < 48; tick++) {
            previous.tick();
        }
        FlashActionBuildingAnimation reused = new FlashActionBuildingAnimation(event, previous);
        assertEquals(0f, reused.getAlpha(), 0f);
        assertEquals(17, reused.getNestedFrameIndex(5334, 31));
        reused.tick();
        assertEquals(18, reused.getNestedFrameIndex(5334, 31));
        FlashActionBuildingAnimation changed = new FlashActionBuildingAnimation(
                eventFor(GameState.Action.RESEARCH), previous);
        assertEquals(0, changed.getNestedFrameIndex(3707, 25));
    }

    @Test
    public void replayingStoppedFactorySmokeContinuesFromFortyOneAndStopsOnWrap() {
        GameState.ActionBuildingEvent event = eventFor(GameState.Action.PRODUCTION);
        FlashActionBuildingAnimation previous = new FlashActionBuildingAnimation(event);
        previous.revealAction(GameState.Action.PRODUCTION);
        for (int tick = 0; tick < 40; tick++) {
            previous.tick();
        }
        FlashActionBuildingAnimation reused = new FlashActionBuildingAnimation(event, previous);
        reused.revealAction(GameState.Action.PRODUCTION);
        assertFrameTrace(reused, 3996, 50,
                new int[]{0, 1, 9, 10, 25}, new int[]{41, 42, 50, 1, 1});
        assertTrue("The selection highlight is still playing after factory smoke stops", reused.isAnimating());
        for (int tick = 25; tick < 30; tick++) reused.tick();
        assertFalse(reused.isAnimating());
    }

    @Test
    public void eachNationsSelectionRingPlaysThirtyFramesOnlyForItsAnnouncedAction() {
        FlashActionBuildingAnimation animation = new FlashActionBuildingAnimation(
                eventFor(GameState.Action.MASS_MEDIA));
        for (int i = 0; i < 30; i++) animation.tick();
        animation.revealAction(GameState.Action.RESOURCES);
        assertEquals(0, animation.getNestedFrameIndex(5277, 30));
        animation.revealAction(GameState.Action.MASS_MEDIA);
        assertTrue(animation.isAnimating());
        for (int expected = 1; expected < 30; expected++) {
            animation.tick();
            assertEquals(expected, animation.getNestedFrameIndex(5277, 30));
        }
        animation.tick();
        assertEquals(0, animation.getNestedFrameIndex(5277, 30));
        assertFalse(animation.isAnimating());
    }

    private static void assertFrameTrace(FlashActionBuildingAnimation animation,
                                         int symbolId, int frameCount,
                                         int[] ticks, int[] oneBasedSourceFrames) {
        int elapsed = 0;
        for (int index = 0; index < ticks.length; index++) {
            while (elapsed < ticks[index]) {
                animation.tick();
                elapsed++;
            }
            assertEquals("Source nested frame at tick " + ticks[index],
                    oneBasedSourceFrames[index] - 1,
                    animation.getNestedFrameIndex(symbolId, frameCount));
        }
    }

    private static GameState.ActionBuildingEvent eventFor(GameState.Action action) {
        GameState state = new GameState(1, 17L);
        state.selectAction(action);
        if (action == GameState.Action.RESEARCH) {
            assertTrue(state.selectResearchTechnology("soldier2"));
        } else if (action == GameState.Action.PRODUCTION) {
            state.configureProduction("soldier", 20);
        }
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        for (GameState.ActionBuildingEvent event : state.getLastTurnActionBuildings()) {
            if (event.playerId == 0 && event.action == action) {
                return event;
            }
        }
        throw new AssertionError("Missing source action-building event " + action);
    }

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
