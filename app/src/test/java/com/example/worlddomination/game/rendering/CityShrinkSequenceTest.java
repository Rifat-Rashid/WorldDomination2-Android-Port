package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CityShrinkSequenceTest {
    @Test
    public void switchesTargetFrameOnlyAfterFadingOutAndCompletesInSeventeenTicks() {
        CityShrinkSequence sequence = new CityShrinkSequence();
        sequence.reset(2);
        sequence.target(5);

        int[] fadeOutAlpha = {85, 70, 55, 40, 25, 10, 0};
        int tickCount = 0;
        for (int alpha : fadeOutAlpha) {
            sequence.tick();
            tickCount++;
            assertEquals(alpha, sequence.getAlpha());
            assertEquals(2, sequence.getFrameNumber());
            assertEquals(CityShrinkSequence.Phase.FADE_OUT, sequence.getPhase());
        }

        sequence.tick();
        tickCount++;
        assertEquals(CityShrinkSequence.Phase.SWITCH_FRAME, sequence.getPhase());
        assertEquals(2, sequence.getFrameNumber());
        sequence.tick();
        tickCount++;
        assertEquals(5, sequence.getFrameNumber());
        assertEquals(CityShrinkSequence.Phase.FADE_IN, sequence.getPhase());

        int[] fadeInAlpha = {15, 30, 45, 60, 75, 90, 100};
        for (int alpha : fadeInAlpha) {
            sequence.tick();
            tickCount++;
            assertEquals(alpha, sequence.getAlpha());
            assertEquals(5, sequence.getFrameNumber());
            assertEquals(CityShrinkSequence.Phase.FADE_IN, sequence.getPhase());
        }
        sequence.tick();
        tickCount++;
        assertEquals(CityShrinkSequence.Phase.IDLE, sequence.getPhase());
        assertEquals(17, tickCount);
    }

    @Test
    public void changingTargetDuringFadeOutKeepsCurrentAlphaAndUsesLatestFrame() {
        CityShrinkSequence sequence = new CityShrinkSequence();
        sequence.reset(1);
        sequence.target(4);
        sequence.tick();
        sequence.tick();
        assertEquals(70, sequence.getAlpha());

        sequence.target(6);
        assertEquals(70, sequence.getAlpha());
        assertEquals(1, sequence.getFrameNumber());
        assertEquals(6, sequence.getTargetFrame());
        sequence.tick();
        assertEquals(55, sequence.getAlpha());
        assertEquals(1, sequence.getFrameNumber());
    }

    @Test
    public void changingTargetDuringFadeInFadesFromCurrentAlphaBeforeSwitchingAgain() {
        CityShrinkSequence sequence = new CityShrinkSequence();
        sequence.reset(1);
        sequence.target(2);
        for (int i = 0; i < 9; i++) {
            sequence.tick();
        }
        sequence.tick();
        assertEquals(15, sequence.getAlpha());
        assertEquals(2, sequence.getFrameNumber());

        sequence.target(3);
        sequence.tick();
        assertEquals(0, sequence.getAlpha());
        assertEquals(2, sequence.getFrameNumber());
        for (int i = 0; i < 3; i++) {
            sequence.tick();
        }
        assertEquals(3, sequence.getFrameNumber());
    }

    @Test
    public void resetStartsNewMatchAtFullAlphaWithoutPendingTransition() {
        CityShrinkSequence sequence = new CityShrinkSequence();
        sequence.reset(1);
        sequence.target(12);
        sequence.tick();
        sequence.reset(4);
        sequence.tick();

        assertEquals(4, sequence.getFrameNumber());
        assertEquals(4, sequence.getTargetFrame());
        assertEquals(100, sequence.getAlpha());
        assertEquals(CityShrinkSequence.Phase.IDLE, sequence.getPhase());
    }

    @Test
    public void unchangedTargetDoesNotStartAnAnimation() {
        CityShrinkSequence sequence = new CityShrinkSequence();
        sequence.reset(6);
        sequence.target(6);
        sequence.tick();

        assertEquals(6, sequence.getFrameNumber());
        assertEquals(100, sequence.getAlpha());
        assertEquals(CityShrinkSequence.Phase.IDLE, sequence.getPhase());
    }
}
