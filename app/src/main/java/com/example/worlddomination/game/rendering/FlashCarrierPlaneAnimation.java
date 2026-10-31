package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** Source-tick playback for one original ship-launched payload aircraft. */
public final class FlashCarrierPlaneAnimation
        implements FlashAntiAirTrackingAnimation.Target {
    public static final class Actor {
        public float sourceX;
        public float sourceY;
        public int sourceFrame;
        public float routeProgress;
        public boolean flippedHorizontally;
        public boolean visible;
    }

    private static final float SOURCE_STEP = 10f;
    private static final float SOURCE_TARGET_RANGE = 40f;
    private static final float X_ACCELERATION = 1f;
    private static final float Y_ACCELERATION = 2f;
    private static final int SOURCE_FRAME_COUNT = 24;
    private static final int SOURCE_TAKEOFF_CUE_TICKS = 37;

    private final FlashBattleRoutes.CarrierPlaneStrike route;
    private final String impactLinkage;
    private final OriginalAudio.Cue impactCue;
    private final Actor actor = new Actor();
    private final float totalRouteLength;
    private int delayTicks;
    private int activeTicks;
    private int nextWaypoint;
    private float xStep;
    private float yStep;
    private float completedRouteLength;
    private long flightSoundId = -1L;
    private boolean takeoffCuePending;
    private boolean flightCuePending;
    private boolean impactPending;
    private boolean finished;

    public FlashCarrierPlaneAnimation(FlashBattleRoutes.CarrierPlaneStrike route,
                                      int delayTicks, String impactLinkage,
                                      OriginalAudio.Cue impactCue) {
        if (route == null || route.waypoints.length == 0 || delayTicks < 0) {
            throw new IllegalArgumentException("A carrier route and nonnegative delay are required");
        }
        this.route = route;
        this.delayTicks = delayTicks;
        this.impactLinkage = impactLinkage;
        this.impactCue = impactCue;
        actor.sourceX = route.start.x;
        actor.sourceY = route.start.y;
        totalRouteLength = routeLength(route);
    }

    /** Advances one 24 Hz source tick and reports the payload impact exactly once. */
    public boolean tick() {
        if (finished) {
            return false;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return false;
        }

        actor.visible = true;
        if (activeTicks == 0) {
            takeoffCuePending = true;
        }
        actor.sourceFrame = activeTicks++ % SOURCE_FRAME_COUNT;
        if (activeTicks == SOURCE_TAKEOFF_CUE_TICKS) {
            flightCuePending = true;
        }
        FlashBattleRoutes.Point target = route.waypoints[nextWaypoint];
        moveToward(target);
        actor.flippedHorizontally = target.x < actor.sourceX;
        updateRouteProgress();

        if (isWithinTargetRange(target)) {
            if (nextWaypoint == route.waypoints.length - 1) {
                actor.sourceX = target.x;
                actor.sourceY = target.y;
                actor.routeProgress = 1f;
                actor.visible = false;
                finished = true;
                if (!impactPending) {
                    impactPending = true;
                    return true;
                }
            } else {
                completedRouteLength += distanceToCurrentWaypoint();
                nextWaypoint++;
                // Unit.setStep carries velocity across waypoints and eases to the next slope.
            }
        }
        return false;
    }

    private void moveToward(FlashBattleRoutes.Point target) {
        float dx = target.x - actor.sourceX;
        float dy = target.y - actor.sourceY;
        float manhattanDistance = Math.abs(dx) + Math.abs(dy);
        if (manhattanDistance == 0f) {
            return;
        }

        float targetXStep = (float) Math.floor(SOURCE_STEP * Math.abs(dx)
                / manhattanDistance) * Math.signum(dx);
        float targetYStep = (float) Math.floor(SOURCE_STEP * Math.abs(dy)
                / manhattanDistance) * Math.signum(dy);
        xStep = approach(xStep, targetXStep, X_ACCELERATION);
        yStep = approach(yStep, targetYStep, Y_ACCELERATION);
        actor.sourceX += xStep;
        actor.sourceY += yStep;
    }

    private boolean isWithinTargetRange(FlashBattleRoutes.Point target) {
        return Math.abs(actor.sourceX - target.x) < SOURCE_TARGET_RANGE
                && Math.abs(actor.sourceY - target.y) < SOURCE_TARGET_RANGE;
    }

    private void updateRouteProgress() {
        float travelled = completedRouteLength + distanceToCurrentWaypoint();
        actor.routeProgress = totalRouteLength == 0f ? 1f
                : Math.min(1f, travelled / totalRouteLength);
    }

    private float distanceToCurrentWaypoint() {
        FlashBattleRoutes.Point segmentStart = nextWaypoint == 0
                ? route.start : route.waypoints[nextWaypoint - 1];
        return distance(segmentStart.x, segmentStart.y, actor.sourceX, actor.sourceY);
    }

    private static float routeLength(FlashBattleRoutes.CarrierPlaneStrike route) {
        float length = 0f;
        float previousX = route.start.x;
        float previousY = route.start.y;
        for (FlashBattleRoutes.Point waypoint : route.waypoints) {
            length += distance(previousX, previousY, waypoint.x, waypoint.y);
            previousX = waypoint.x;
            previousY = waypoint.y;
        }
        return length;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static float approach(float current, float target, float amount) {
        if (current < target) {
            return Math.min(target, current + amount);
        }
        if (current > target) {
            return Math.max(target, current - amount);
        }
        return current;
    }

    public Actor getActor() {
        return actor;
    }

    public FlashBattleRoutes.CarrierPlaneStrike getRoute() {
        return route;
    }

    @Override
    public int targetPlayerId() {
        return route.targetPlayerId;
    }

    @Override
    public float sourceX() {
        return actor.sourceX;
    }

    @Override
    public float sourceY() {
        return actor.sourceY;
    }

    @Override
    public boolean isVisible() {
        return actor.visible;
    }

    public String getImpactLinkage() {
        return impactLinkage;
    }

    public OriginalAudio.Cue getImpactCue() {
        return impactCue;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean takeTakeoffCue() {
        boolean pending = takeoffCuePending;
        takeoffCuePending = false;
        return pending;
    }

    public boolean takeFlightCue() {
        boolean pending = flightCuePending;
        flightCuePending = false;
        return pending;
    }

    public long getFlightSoundId() {
        return flightSoundId;
    }

    public void setFlightSoundId(long flightSoundId) {
        this.flightSoundId = flightSoundId;
    }
}
