package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

/** City.size_and_frame_in_battle's battle-only frame and dust-name state. */
public final class SourceCityImpactState {
    private static final int FRAME_DIVIDER = 90;
    private static final int NORMAL_FRAMES = 6;
    private static final int DEATH_FRAME_OFFSET = 9;
    private static final int DUST_NAME_MIN = 10000;
    private static final int DUST_NAME_COUNT = 90000;

    public static final class Transition {
        public final int targetFrame;
        public final int randomName;

        private Transition(int targetFrame, int randomName) {
            this.targetFrame = targetFrame;
            this.randomName = randomName;
        }
    }

    private final int snapshotPopulation;
    private double cumulativeCasualties;
    private int targetFrame;
    private int previousFrame;
    private int deathFrame;

    /** Starts at BattlePlayback's snapshot before canonical casualty settlement. */
    public SourceCityImpactState(int snapshotPopulation, int initialArtworkFrame) {
        this.snapshotPopulation = snapshotPopulation;
        targetFrame = initialArtworkFrame;
        previousFrame = initialArtworkFrame;
        if (snapshotPopulation <= 0 && initialArtworkFrame > NORMAL_FRAMES) {
            deathFrame = initialArtworkFrame;
        }
    }

    /** Returns a shrink transition only when City.as would call View.city_shrink_sequence. */
    public Transition apply(double casualties, ReferenceRandom presentationRandom) {
        if (deathFrame != 0) return null;
        cumulativeCasualties += casualties;
        double remaining = snapshotPopulation - cumulativeCasualties;
        if (remaining <= 0) {
            // City.as uses image._currentframe only when _prevFrame is falsy.
            deathFrame = (previousFrame == 0 ? targetFrame : previousFrame)
                    + DEATH_FRAME_OFFSET;
            targetFrame = deathFrame;
            return transition(presentationRandom);
        }

        int nextFrame = (int) Math.ceil(remaining / FRAME_DIVIDER * NORMAL_FRAMES);
        if (nextFrame > NORMAL_FRAMES) {
            // City.as:305-307 updates _prevFrame to six but does not start
            // city_shrink_sequence in the over-cap branch.
            previousFrame = NORMAL_FRAMES;
            return null;
        }
        if (nextFrame == previousFrame) return null;
        previousFrame = nextFrame;
        targetFrame = nextFrame;
        return transition(presentationRandom);
    }

    private Transition transition(ReferenceRandom presentationRandom) {
        return new Transition(targetFrame,
                DUST_NAME_MIN + presentationRandom.nextInt(DUST_NAME_COUNT));
    }

    public int getTargetFrame() {
        return targetFrame;
    }

    public double getCumulativeCasualties() {
        return cumulativeCasualties;
    }
}
