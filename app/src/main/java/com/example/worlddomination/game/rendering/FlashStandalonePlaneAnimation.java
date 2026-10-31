package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** Source-tick flight of a fighter or bomber launched from a nation's airfield. */
public final class FlashStandalonePlaneAnimation {
    public enum Phase {
        TAKEOFF,
        CLIMB_TO_ORBIT,
        ORBIT,
        APPROACH,
        FINISHED
    }

    public static final class Actor {
        public float sourceX;
        public float sourceY;
        public float previousX;
        public float previousY;
        public float shadowYOffset;
        public float alpha;
        public float scale;
        public float routeProgress;
        public int sourceFrame;
        public boolean visible;
    }

    private static final float SOURCE_STEP = 12f;
    private static final float SOURCE_TARGET_RANGE = 40f;
    private static final float Y_ACCELERATION = 1f;
    private static final int PLANE_FRAME_COUNT = 24;
    private static final int PLANE_COUNT_TO_FREE_FLIGHT = 8;
    private static final int FIGHTER_TAKEOFF_TICKS = 60;
    private static final int BOMBER_TAKEOFF_TICKS = 37;
    private static final float ORBIT_ALIGNMENT_LIMIT = 40f;

    private final FlashBattleRoutes.StandalonePlaneStrike route;
    private final FlashColorTransform tint;
    private final String impactLinkage;
    private final OriginalAudio.Cue impactCue;
    private final OriginalAudio.Cue takeoffCue;
    private final OriginalAudio.Cue flightCue;
    private final Actor actor = new Actor();
    private int delayTicks;
    private int activeTicks;
    private int orbitTicks;
    private int countdownToLeaveOrbit = 6;
    private float xStep;
    private float yStep;
    private float orbitAngle;
    private float orbitRadius;
    private float orbitCenterX;
    private float orbitCenterY;
    private float distanceTravelled;
    private long flightSoundId = -1L;
    private boolean takeoffCuePending;
    private boolean flightCuePending;
    private boolean impactPending;
    private boolean finished;
    private Phase phase = Phase.TAKEOFF;

    public FlashStandalonePlaneAnimation(FlashBattleRoutes.StandalonePlaneStrike route,
                                         int playerColor, int delayTicks,
                                         String impactLinkage,
                                         OriginalAudio.Cue impactCue) {
        if (route == null || playerColor < 1 || playerColor > 5 || delayTicks < 0) {
            throw new IllegalArgumentException("A plane route and nonnegative delay are required");
        }
        this.route = route;
        tint = FlashColorTransform.forPlayerColor(playerColor);
        this.impactLinkage = impactLinkage;
        this.impactCue = impactCue;
        this.delayTicks = delayTicks;
        actor.sourceX = route.start.x;
        actor.sourceY = route.start.y;
        actor.previousX = actor.sourceX;
        actor.previousY = actor.sourceY;
        actor.scale = 0.5f;
        actor.alpha = 0f;
        actor.visible = delayTicks == 0;
        takeoffCue = "plane001".equals(route.linkage)
                ? OriginalAudio.Cue.FIGHTER_TAKEOFF
                : OriginalAudio.Cue.CARRIERJET_TAKEOFF;
        flightCue = "plane001".equals(route.linkage)
                ? OriginalAudio.Cue.FIGHTER_FLIGHT
                : OriginalAudio.Cue.CARRIERJET_FLIGHT;
        orbitAngle = route.orbitStartAngle;
        orbitRadius = route.orbitRadius;
    }

    /** Advances one source tick (24 Hz), returning true once for the payload result. */
    public boolean tick() {
        if (finished) {
            return false;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return false;
        }

        activeTicks++;
        if (activeTicks == 1) {
            takeoffCuePending = true;
        }
        int takeoffTicks = "plane001".equals(route.linkage)
                ? FIGHTER_TAKEOFF_TICKS : BOMBER_TAKEOFF_TICKS;
        if (activeTicks == takeoffTicks) {
            flightCuePending = true;
        }

        actor.visible = true;
        actor.previousX = actor.sourceX;
        actor.previousY = actor.sourceY;
        if (phase == Phase.TAKEOFF) {
            moveToward(route.liftOff);
            if (isWithinTargetRange(route.liftOff)) {
                phase = Phase.CLIMB_TO_ORBIT;
            }
        } else if (phase == Phase.CLIMB_TO_ORBIT) {
            moveToward(route.orbitEntry);
            if (isWithinTargetRange(route.orbitEntry)) {
                phase = Phase.ORBIT;
                orbitCenterX = actor.sourceX + route.orbitCenterOffset.x;
                orbitCenterY = actor.sourceY + route.orbitCenterOffset.y;
                orbitAngle = route.orbitStartAngle;
                orbitRadius = route.orbitRadius;
            }
        } else if (phase == Phase.ORBIT) {
            updateOrbit();
        } else if (phase == Phase.APPROACH) {
            moveToward(route.bombWaypoint);
            if (isWithinTargetRange(route.bombWaypoint)) {
                actor.sourceX = route.bombWaypoint.x;
                actor.sourceY = route.bombWaypoint.y;
                actor.routeProgress = 1f;
                actor.visible = false;
                phase = Phase.FINISHED;
                finished = true;
                impactPending = true;
                return true;
            }
        }

        float moved = distance(actor.previousX, actor.previousY,
                actor.sourceX, actor.sourceY);
        distanceTravelled += moved;
        actor.alpha = Math.min(1f, actor.alpha + 0.1f);
        if (activeTicks > PLANE_COUNT_TO_FREE_FLIGHT) {
            updateTakeoffHeight();
            actor.scale = Math.min(0.8f, actor.scale + 0.02f);
        }
        actor.sourceFrame = directionFrame(actor.sourceX - actor.previousX,
                actor.sourceY - actor.previousY);
        updateRouteProgress();
        return false;
    }

    private void updateTakeoffHeight() {
        if (actor.shadowYOffset < 90f) {
            actor.shadowYOffset = Math.min(90f, actor.shadowYOffset + 2f);
        }
    }

    private void updateOrbit() {
        countdownToLeaveOrbit--;
        float radians = (float) Math.toRadians(orbitAngle);
        actor.sourceX = orbitCenterX + orbitRadius * (float) Math.cos(radians);
        actor.sourceY = orbitCenterY + orbitRadius * (float) Math.sin(radians);
        orbitRadius = Math.max(0f, orbitRadius - route.orbitRadiusStep);
        orbitAngle = (orbitAngle + route.orbitAngleStep * route.orbitDirection) % 360f;
        orbitTicks++;

        // Unit.plane_coords_at_cur_direction predicts whether the plane's current heading
        // points within 40 source pixels of the bomb waypoint.
        float dx = actor.sourceX - actor.previousX;
        float dy = -(actor.sourceY - actor.previousY);
        if (dx != 0f || dy != 0f) {
            float direction = (float) Math.atan2(dx, dy) + 5f;
            float remainingDistance = distance(actor.sourceX, actor.sourceY,
                    route.bombWaypoint.x, route.bombWaypoint.y);
            float projectedX = actor.sourceX + (float) Math.cos(direction) * remainingDistance;
            float projectedY = actor.sourceY + (float) Math.sin(direction) * remainingDistance;
            if (countdownToLeaveOrbit < 0 && distance(projectedX, projectedY,
                    route.bombWaypoint.x, route.bombWaypoint.y) < ORBIT_ALIGNMENT_LIMIT) {
                phase = Phase.APPROACH;
            }
        }
    }

    private void moveToward(FlashBattleRoutes.Point target) {
        float dx = target.x - actor.sourceX;
        float dy = target.y - actor.sourceY;
        float manhattanDistance = Math.abs(dx) + Math.abs(dy);
        if (manhattanDistance <= 0f) {
            return;
        }
        float targetXStep = (float) Math.floor(SOURCE_STEP * Math.abs(dx)
                / manhattanDistance) * Math.signum(dx);
        float targetYStep = (float) Math.floor(SOURCE_STEP * Math.abs(dy)
                / manhattanDistance) * Math.signum(dy);
        xStep = approach(xStep, targetXStep, route.xAcceleration);
        yStep = approach(yStep, targetYStep, Y_ACCELERATION);
        actor.sourceX += xStep;
        actor.sourceY += yStep;
    }

    private boolean isWithinTargetRange(FlashBattleRoutes.Point target) {
        return Math.abs(actor.sourceX - target.x) < SOURCE_TARGET_RANGE
                && Math.abs(actor.sourceY - target.y) < SOURCE_TARGET_RANGE;
    }

    private void updateRouteProgress() {
        float startToTarget = distance(route.start.x, route.start.y,
                route.target.x, route.target.y);
        float expectedOrbitDistance = 48f * route.orbitRadius * (float) Math.PI / 180f;
        float expectedLength = distance(route.start.x, route.start.y,
                route.liftOff.x, route.liftOff.y)
                + distance(route.liftOff.x, route.liftOff.y,
                route.orbitEntry.x, route.orbitEntry.y)
                + expectedOrbitDistance
                + distance(route.orbitEntry.x, route.orbitEntry.y,
                route.target.x, route.target.y);
        float total = Math.max(startToTarget, expectedLength);
        actor.routeProgress = total == 0f ? 1f
                : Math.min(1f, distanceTravelled / total);
        if (phase == Phase.APPROACH) {
            float remaining = distance(actor.sourceX, actor.sourceY,
                    route.target.x, route.target.y);
            actor.routeProgress = Math.max(actor.routeProgress,
                    1f - remaining / Math.max(startToTarget, 1f));
        }
    }

    private static int directionFrame(float dx, float dy) {
        if (dx == 0f && dy == 0f) {
            return 0;
        }
        double angle = Math.toDegrees(Math.atan2(-dy, dx)) + 181d;
        int frame = (int) Math.round(angle / 360d * PLANE_FRAME_COUNT);
        return Math.floorMod(frame - 1, PLANE_FRAME_COUNT);
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

    public FlashBattleRoutes.StandalonePlaneStrike getRoute() {
        return route;
    }

    public FlashColorTransform getTint() {
        return tint;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean takeImpact() {
        boolean pending = impactPending;
        impactPending = false;
        return pending;
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

    public OriginalAudio.Cue getTakeoffCue() {
        return takeoffCue;
    }

    public OriginalAudio.Cue getFlightCue() {
        return flightCue;
    }

    public String getImpactLinkage() {
        return impactLinkage;
    }

    public OriginalAudio.Cue getImpactCue() {
        return impactCue;
    }

    public boolean loopsFlightCue() {
        return route.linkage.equals("plane002");
    }

    public long getFlightSoundId() {
        return flightSoundId;
    }

    public void setFlightSoundId(long soundId) {
        flightSoundId = soundId;
    }
}
