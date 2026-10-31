package com.example.worlddomination.game.rendering;

/** 24 Hz satellite approach, laser impact, and source-timed fade sequence. */
public final class FlashSatelliteAnimation {
    private enum Phase {
        APPROACH,
        FIRING,
        FINISHED
    }

    private final FlashBattleRoutes.SatelliteStrike route;
    private int delayTicks;
    private float sourceX;
    private float sourceY;
    private int laserAlphaStart;
    private float laserAlpha;
    private boolean laserAlphaIncreasing = true;
    private boolean started;
    private boolean impactPending;
    private boolean flightCuePending;
    private boolean laserCuePending;
    private boolean flightCueTaken;
    private boolean laserCueTaken;
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

    /** Advances one source frame. Returns true when the display sequence is finished. */
    public boolean tick() {
        if (phase == Phase.FINISHED) {
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
                // View.onEnterFrame advances satellite curX/curY by (+3, +1).
                sourceX += 3f;
                sourceY += 1f;
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
                    phase = Phase.FINISHED;
                }
            }
        }
        return phase == Phase.FINISHED;
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

    public boolean isFiring() {
        return phase == Phase.FIRING;
    }

    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    public float getSourceX() {
        return sourceX;
    }

    public float getSourceY() {
        return sourceY;
    }

    public float getLaserAlpha() {
        return laserAlpha;
    }

    /** Returns the one-based nested Flash frame selected from the satellite's x position. */
    public int getSourceClipFrameNumber() {
        return Math.max(1, Math.round(sourceX / 26f));
    }

    public FlashBattleRoutes.SatelliteStrike getRoute() {
        return route;
    }
}
