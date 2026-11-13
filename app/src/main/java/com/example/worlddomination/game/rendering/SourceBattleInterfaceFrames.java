package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import java.util.Objects;

/**
 * Advances the hidden interface timeline that runs during recovered battle playback.
 *
 * <p>The authored centerScreen (sprite 4444) places buildingsMC (4108) at
 * frame 95. Its mc3 child (4085) places five religion-flame sprites on frames
 * 1..5. Each flame's first-frame action calls Tools.randRange(2, 110), then
 * gotoAndPlay with the result. Source: scene.json.gz placements and generated
 * script_209..213.py / Tools.py. The recovered BattlePlayback host does not
 * isolate its visual frame RNG, so these actions consume the presentation
 * stream before infantry activation, even though interfaceMC is hidden.
 */
public final class SourceBattleInterfaceFrames {
    public static final int FIRST_FLAME_PARENT_FRAME = 95;
    private static final int RETURN_TO_FIRST_FRAME = 134;
    private static final int[] FLAME_CHARACTER_IDS = {4010, 4015, 4018, 4023, 4028};
    private static final int FLAME_START_FRAME_MINIMUM = 2;
    private static final int FLAME_START_FRAME_COUNT = 109;

    private final int[] selectedStartFrames = new int[FLAME_CHARACTER_IDS.length];
    private int elapsedTicks;
    private boolean running;

    /** Starts a fresh battle presentation at centerScreen frame 1. */
    public void begin() {
        reset();
        running = true;
    }

    /** Clears the timeline and its recorded frame-action results. */
    public void reset() {
        running = false;
        elapsedTicks = 0;
        for (int index = 0; index < selectedStartFrames.length; index++) {
            selectedStartFrames[index] = 0;
        }
    }

    /**
     * Advances one global 24 Hz presentation tick, including label and cleanup
     * ticks. Returns the flame character ID whose frame action ran, or zero.
     */
    public int tick(ReferenceRandom presentationRandom) {
        if (!running) {
            throw new IllegalStateException("Battle interface timeline has not begun");
        }
        Objects.requireNonNull(presentationRandom, "presentationRandom");
        elapsedTicks++;
        int flameIndex = getCenterScreenFrame() - FIRST_FLAME_PARENT_FRAME;
        if (flameIndex < 0 || flameIndex >= FLAME_CHARACTER_IDS.length) {
            return 0;
        }
        selectedStartFrames[flameIndex] = FLAME_START_FRAME_MINIMUM
                + presentationRandom.nextInt(FLAME_START_FRAME_COUNT);
        return FLAME_CHARACTER_IDS[flameIndex];
    }

    public boolean isRunning() {
        return running;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public int getCenterScreenFrame() {
        // Authored frame 134 calls gotoAndStop(1); the hidden parent then stops.
        return elapsedTicks < RETURN_TO_FIRST_FRAME - 1 ? 1 + elapsedTicks : 1;
    }

    public static int getFlameCharacterId(int zeroBasedIndex) {
        return FLAME_CHARACTER_IDS[zeroBasedIndex];
    }

    public static int getFlameCount() {
        return FLAME_CHARACTER_IDS.length;
    }

    /** Zero until that flame's first-frame action has run. */
    public int getSelectedStartFrame(int characterId) {
        for (int index = 0; index < FLAME_CHARACTER_IDS.length; index++) {
            if (FLAME_CHARACTER_IDS[index] == characterId) {
                return selectedStartFrames[index];
            }
        }
        throw new IllegalArgumentException("Unknown flame character ID: " + characterId);
    }
}
