package com.example.worlddomination.game.rendering;

/** Source-timed playback state for the two Flash propaganda beam clips on a city. */
public final class CityPropagandaBeamSequence {
    public static final int SOURCE_FRAME_COUNT = 18;
    public static final int SECOND_BEAM_START_FRAME = 3;

    private final int[] frameIndexes = {-1, -1};
    private final int[] attackerColors = {1, 1};

    /** Starts the source beam selected by the city's zero-based propaganda loss index. */
    public void play(int lossSequenceIndex, int attackerColor) {
        if (lossSequenceIndex < 0 || attackerColor < 1 || attackerColor > 5) {
            throw new IllegalArgumentException("Invalid propaganda beam playback request");
        }
        int beamIndex = lossSequenceIndex == 0 ? 0 : 1;
        attackerColors[beamIndex] = attackerColor;
        frameIndexes[beamIndex] = beamIndex == 0
                ? 0 : SECOND_BEAM_START_FRAME - 1;
    }

    /** Advances both playing clips by one source frame at 24 Hz. */
    public void tick() {
        for (int index = 0; index < frameIndexes.length; index++) {
            if (frameIndexes[index] >= 0) {
                frameIndexes[index] = (frameIndexes[index] + 1) % SOURCE_FRAME_COUNT;
            }
        }
    }

    /** Returns the zero-based frame to render, or {@code -1} when this beam is stopped. */
    public int getFrameIndex(int beamIndex) {
        checkBeamIndex(beamIndex);
        return frameIndexes[beamIndex];
    }

    public int getAttackerColor(int beamIndex) {
        checkBeamIndex(beamIndex);
        return attackerColors[beamIndex];
    }

    public void reset() {
        frameIndexes[0] = -1;
        frameIndexes[1] = -1;
    }

    public boolean isPlaying() {
        return frameIndexes[0] >= 0 || frameIndexes[1] >= 0;
    }

    private static void checkBeamIndex(int beamIndex) {
        if (beamIndex < 0 || beamIndex > 1) {
            throw new IllegalArgumentException("Propaganda beam index must be 0 or 1");
        }
    }
}
