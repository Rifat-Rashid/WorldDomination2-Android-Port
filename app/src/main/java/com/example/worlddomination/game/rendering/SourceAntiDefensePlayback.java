package com.example.worlddomination.game.rendering;

/** One invocation of Flash View.updateAnti; the caller owns host-frame visits and audio. */
public final class SourceAntiDefensePlayback {
    public static final int INITIAL_COUNTDOWN = 50;

    /** Original buildings_antiAir nested mc timelines: mus 55; others 50. */
    public static SourceAntiDefensePlayback forReligion(String religionCode) {
        int frames;
        switch (religionCode) {
            case "mus": frames = 55; break;
            case "jew": case "com": case "chr": case "bhu": frames = 50; break;
            default: throw new IllegalArgumentException("Unknown source religion");
        }
        return new SourceAntiDefensePlayback(frames, 1, 100d);
    }

    /** Unit fields belong to each aircraft or rocket, even when clips share a turret. */
    public static final class UnitState {
        private int countdown = INITIAL_COUNTDOWN;
        private int offset;
        private int rawBearingDegrees;

        public int getCountdown() { return countdown; }
        public int getOffset() { return offset; }
        public int getRawBearingDegrees() { return rawBearingDegrees; }

        /** Unit.killUnit sets anti_offset before calling View.updateAnti once more. */
        public void markKilled() { offset = 2; }
    }

    private final int totalFrames;
    private int turretFrame;
    private double turretAlphaPercent;
    private boolean turretPlaying;
    private boolean moveSoundRequested;
    private boolean shootSoundRequested;

    public SourceAntiDefensePlayback(int totalFrames, int initialTurretFrame,
                                     double initialTurretAlphaPercent) {
        if (totalFrames < 1 || initialTurretFrame < 1 || initialTurretFrame > totalFrames)
            throw new IllegalArgumentException("Invalid source turret timeline");
        this.totalFrames = totalFrames;
        turretFrame = initialTurretFrame;
        turretAlphaPercent = initialTurretAlphaPercent;
    }

    /** Publish the actual host autoplay frame before invoking source callbacks. */
    public void publishHostFrame(int frame) {
        if (frame < 1 || frame > totalFrames)
            throw new IllegalArgumentException("Invalid source turret frame");
        turretFrame = frame;
    }

    /** MovieClipHost advances playing clips once after all onEnterFrame callbacks. */
    public void advanceHostFrame() {
        if (!turretPlaying) return;
        turretFrame = turretFrame % totalFrames + 1;
        // All five authored nested timelines have a frame-1 stop action.
        if (turretFrame == 1) turretPlaying = false;
    }

    /**
     * View.updateAnti. A ship-plane may invoke this twice during one Unit visit;
     * Unit.killUnit may invoke it once more. Do not fold invocations into a tick.
     */
    public void invoke(UnitState unit, boolean antiActive, double unitX, double unitY,
                       double antiX, double antiY, boolean unitDead) {
        if (unit == null) throw new IllegalArgumentException("Missing source Unit state");
        moveSoundRequested = false;
        shootSoundRequested = false;
        if (!antiActive) return;

        int direction = sourceDirection(unit, unitX, unitY, antiX, antiY);
        moveSoundRequested = turretFrame != direction;
        turretFrame = clampFrame(direction * 2 - 1);
        turretPlaying = true;

        double dx = unitX - antiX;
        double dy = unitY - antiY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 170d) unit.offset = unit.offset == 1 ? 2 : 1;
        if (unit.offset == 1) {
            turretFrame = Math.min(totalFrames, turretFrame + 1);
            turretPlaying = false;
            shootSoundRequested = true;
        }
        if (unit.countdown <= 0) {
            if (turretAlphaPercent < 100d) turretAlphaPercent += 10d;
        } else {
            unit.countdown--;
        }
        // Flash checks frame / floor(frame/2) == 2, which is true for even frames.
        if (unitDead && turretFrame % 2 == 0) {
            turretFrame = Math.max(1, turretFrame - 1);
            turretPlaying = false;
        }
    }

    private int clampFrame(int frame) { return Math.max(1, Math.min(totalFrames, frame)); }

    /** Unit.anti_direction mutates Unit.angle; retain its exact arithmetic order. */
    public static int sourceDirection(UnitState unit, double unitX, double unitY,
                                      double antiX, double antiY) {
        if (unit == null) throw new IllegalArgumentException("Missing source Unit state");
        double dx = unitX - antiX;
        double invertedY = -1d * (unitY - antiY);
        int degrees = (int) Math.round((Math.atan2(invertedY, dx) / Math.PI) * 180d) + 181;
        unit.rawBearingDegrees = degrees;
        return (int) Math.round((((degrees / 360d) * 100d) / 100d) * 18d);
    }

    public int getTurretFrame() { return turretFrame; }
    public double getTurretAlphaPercent() { return turretAlphaPercent; }
    public boolean isTurretPlaying() { return turretPlaying; }
    public boolean isMoveSoundRequested() { return moveSoundRequested; }
    public boolean isShootSoundRequested() { return shootSoundRequested; }
}
