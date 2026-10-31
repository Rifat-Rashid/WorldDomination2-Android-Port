package com.example.worlddomination.game.rendering;

/** A source-tick playback of the original submarine hull and player-tinted wake clips. */
public final class FlashSubmarineAnimation {
    public static final int SOURCE_FRAME_COUNT = 66;
    public static final int TINT_FRAME_COUNT = 65;
    /** View.submarineSequence releases one queued hull clip every 8,000 ms at 24 Hz. */
    public static final int SOURCE_SEQUENCE_INTERVAL_TICKS = 8 * 24;
    private static final float STAGE_WIDTH = 800f;
    private static final float TINT_CLIP_X_OFFSET = -6.15f;

    private final FlashBattleRoutes.RocketStrike route;
    private final FlashColorTransform tint;
    private int delayTicks;
    private int sourceFrameIndex = -1;
    private boolean started;
    private boolean finished;
    private boolean surfaceCuePending;

    /** Converts the source's one-based 8-second interval into the first 24 Hz tick. */
    public static int delayForSequenceIndex(int oneBasedSequenceIndex) {
        if (oneBasedSequenceIndex < 1) {
            throw new IllegalArgumentException("Submarine sequence index must be positive");
        }
        return oneBasedSequenceIndex * SOURCE_SEQUENCE_INTERVAL_TICKS - 1;
    }

    public FlashSubmarineAnimation(FlashBattleRoutes.RocketStrike route,
                                   int playerColor, int delayTicks) {
        if (route == null || !route.submarineLaunch || delayTicks < 0) {
            throw new IllegalArgumentException("A submarine route and non-negative delay are required");
        }
        this.route = route;
        tint = FlashColorTransform.forPlayerColor(playerColor);
        this.delayTicks = delayTicks;
    }

    /** Advances one original 24 Hz frame and reports whether the movie clip began. */
    public boolean tick() {
        if (finished) {
            return false;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return false;
        }
        if (!started) {
            started = true;
            sourceFrameIndex = 0;
            surfaceCuePending = true;
            return true;
        }
        sourceFrameIndex++;
        if (sourceFrameIndex >= SOURCE_FRAME_COUNT) {
            sourceFrameIndex = -1;
            finished = true;
        }
        return false;
    }

    public boolean takeSurfaceCue() {
        boolean pending = surfaceCuePending;
        surfaceCuePending = false;
        return pending;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isFinished() {
        return finished;
    }

    public int getSourceFrameIndex() {
        return sourceFrameIndex;
    }

    public FlashBattleRoutes.RocketStrike getRoute() {
        return route;
    }

    public FlashColorTransform getTint() {
        return tint;
    }

    /** Flash pans the splash from the nested tint clip's x coordinate across its 800px stage. */
    public float getSurfaceSoundPan() {
        float sourceX = route.silo.x + TINT_CLIP_X_OFFSET;
        return Math.max(-1f, Math.min(1f, sourceX * 2f / STAGE_WIDTH - 1f));
    }
}
