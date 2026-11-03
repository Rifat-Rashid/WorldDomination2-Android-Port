package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CityPropagandaBeamSequenceTest {
    @Test
    public void firstPropagandaLossPlaysAndLoopsTheFirstFlashBeam() {
        CityPropagandaBeamSequence sequence = new CityPropagandaBeamSequence();

        sequence.play(0, 3);

        assertEquals(0, sequence.getFrameIndex(0));
        assertEquals(-1, sequence.getFrameIndex(1));
        assertEquals(3, sequence.getAttackerColor(0));
        assertTrue(sequence.isPlaying());
        sequence.tick();
        assertEquals(1, sequence.getFrameIndex(0));
        for (int tick = 1; tick < CityPropagandaBeamSequence.SOURCE_FRAME_COUNT; tick++) {
            sequence.tick();
        }
        assertEquals(0, sequence.getFrameIndex(0));
    }

    @Test
    public void subsequentLossStartsTheSecondFlashBeamAtItsSourceFrameThree() {
        CityPropagandaBeamSequence sequence = new CityPropagandaBeamSequence();

        sequence.play(0, 2);
        sequence.play(1, 5);

        assertEquals(0, sequence.getFrameIndex(0));
        assertEquals(CityPropagandaBeamSequence.SECOND_BEAM_START_FRAME - 1,
                sequence.getFrameIndex(1));
        assertEquals(5, sequence.getAttackerColor(1));
        sequence.tick();
        assertEquals(1, sequence.getFrameIndex(0));
        assertEquals(CityPropagandaBeamSequence.SECOND_BEAM_START_FRAME,
                sequence.getFrameIndex(1));
    }

    @Test
    public void endOfBattleResetStopsBothBeamClips() {
        CityPropagandaBeamSequence sequence = new CityPropagandaBeamSequence();
        sequence.play(0, 1);
        sequence.play(1, 4);

        sequence.reset();

        assertEquals(-1, sequence.getFrameIndex(0));
        assertEquals(-1, sequence.getFrameIndex(1));
        assertFalse(sequence.isPlaying());
    }
}
