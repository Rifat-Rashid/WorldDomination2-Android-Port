package com.example.worlddomination.game.rendering;

/** 24 Hz satellite approach, laser impact, and source-timed fade sequence. */
public final class FlashSatelliteAnimation implements SourceStoppedClipQueue.Clip {
    private enum Phase {
        APPROACH,
        FIRING,
        STOPPED,
        REMOVED
    }

    private final FlashBattleRoutes.SatelliteStrike route;
    private int delayTicks;
    private float sourceX;
    private float sourceY;
    private float displaySourceX;
    private float displaySourceY;
    private int laserAlphaStart;
    private float laserAlpha;
    private boolean laserAlphaIncreasing = true;
    private boolean started;
    private boolean impactPending;
    private boolean flightCuePending;
    private boolean laserCuePending;
    private boolean flightCueTaken;
    private boolean laserCueTaken;
    private int sourceClipFrameNumber = 1;
    private boolean clipVisible = true;
    private boolean queuedForDeletion;
    private boolean endsSourceFrame;
    private Phase phase = Phase.APPROACH;

    public FlashSatelliteAnimation(FlashBattleRoutes.SatelliteStrike route, int delayTicks) {
        if (route == null || delayTicks < 0) {
            throw new IllegalArgumentException("A satellite route and non-negative delay are required");
        }
        this.route = route;
        this.delayTicks = delayTicks;
        sourceX = route.launch.x;
        sourceY = route.launch.y;
    }

    /** Advances one source frame; true means the active wrapper has been removed. */
    public boolean tick() {
        endsSourceFrame = false;
        presentWaitingFrame();
        if (phase == Phase.REMOVED) {
            return true;
        }
        if (phase == Phase.STOPPED) {
            // View.f_37 checks stop before movement on the following tick.
            // Unlike infantry it continues that iteration, restoring action=fire;
            // satellite_explosion's source guard prevents a second impact.
            phase = Phase.REMOVED;
            return true;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return false;
        }

        if (phase == Phase.APPROACH) {
            started = true;
            if (!flightCueTaken) {
                flightCuePending = true;
                flightCueTaken = true;
            }
            if (sourceX < route.orbit.x) {
                // The nested seek uses the old MC._x before View publishes curX.
                sourceClipFrameNumber = Math.max(1, Math.round(displaySourceX / 26f));
                sourceX += 3f;
                sourceY += 1f;
                displaySourceX = sourceX;
                displaySourceY = sourceY;
                return false;
            }
            phase = Phase.FIRING;
            impactPending = true;
            return false;
        }

        if (!laserCueTaken) {
            laserCuePending = true;
            laserCueTaken = true;
        }
        laserAlpha = Math.max(0f, laserAlphaStart / 100f);
        if (laserAlphaIncreasing && laserAlphaStart < 100) {
            laserAlphaStart += 15;
        } else {
            if (laserAlphaStart >= 100) {
                laserAlphaIncreasing = false;
            }
            if (!laserAlphaIncreasing) {
                laserAlphaStart -= 5;
                if (laserAlphaStart <= 0) {
                    phase = Phase.STOPPED;
                    // The source clears the laser, sets stop, then breaks the
                    // unit loop before drawing more lines or visiting later units.
                    endsSourceFrame = true;
                }
            }
        }
        return false;
    }

    public boolean takeImpact() {
        boolean pending = impactPending;
        impactPending = false;
        return pending;
    }

    public boolean takeFlightCue() {
        boolean pending = flightCuePending;
        flightCuePending = false;
        return pending;
    }

    public boolean takeLaserCue() {
        boolean pending = laserCuePending;
        laserCuePending = false;
        return pending;
    }

    public boolean isStarted() {
        return started;
    }

    /** Source View publishes even waiting wrappers while action labels are running. */
    public void presentWaitingFrame() {
        if (!started) {
            displaySourceX = sourceX;
            displaySourceY = sourceY;
        }
    }

    public boolean isFiring() {
        return phase == Phase.FIRING;
    }

    public boolean isFinished() {
        return phase == Phase.REMOVED;
    }

    public boolean didStopSourceFrame() {
        return endsSourceFrame;
    }

    public boolean isClipVisible() {
        return clipVisible;
    }

    /** Queues this stable source wrapper once; its body survives until allUnitsDone. */
    public void registerStoppedClip(SourceStoppedClipQueue queue) {
        if (phase == Phase.REMOVED && !queuedForDeletion) {
            queue.add(this);
            queuedForDeletion = true;
        }
    }

    @Override public void detach() {
        clipVisible = false;
    }

    public float getSourceX() {
        return sourceX;
    }

    public float getSourceY() {
        return sourceY;
    }

    public float getDisplaySourceX() {
        return displaySourceX;
    }

    public float getDisplaySourceY() {
        return displaySourceY;
    }

    public float getLaserAlpha() {
        return laserAlpha;
    }

    /** Returns the one-based nested Flash frame selected from the satellite's x position. */
    public int getSourceClipFrameNumber() {
        return sourceClipFrameNumber;
    }

    public FlashBattleRoutes.SatelliteStrike getRoute() {
        return route;
    }
}
