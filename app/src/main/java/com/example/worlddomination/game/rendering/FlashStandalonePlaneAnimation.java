package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import java.util.Random;

/** Source-tick flight of a fighter or bomber launched from a nation's airfield. */
public final class FlashStandalonePlaneAnimation {
    public enum Phase {
        TAKEOFF,
        CLIMB_TO_ORBIT,
        ORBIT,
        APPROACH,
        FLY_OFF,
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
    private static final int FLY_OFF_COUNTER_LIMIT = 250;
    private static final int FLY_OFF_DURATION_TICKS = calculateFlashFlyOffDurationTicks();
    private static final int SINGLE_TRAIL_PUFFS_PER_TICK = 3;
    private static final int MAX_TRAIL_PUFFS = 50;
    private static final float ORBIT_ALIGNMENT_LIMIT = 40f;

    private final FlashBattleRoutes.StandalonePlaneStrike route;
    private final FlashColorTransform tint;
    private final String impactLinkage;
    private final OriginalAudio.Cue impactCue;
    private final OriginalAudio.Cue takeoffCue;
    private final OriginalAudio.Cue flightCue;
    private final Random trailRandom;
    private final float[] trailX = new float[MAX_TRAIL_PUFFS];
    private final float[] trailY = new float[MAX_TRAIL_PUFFS];
    private final float[] trailProgress = new float[MAX_TRAIL_PUFFS];
    private final float[] trailSize = new float[MAX_TRAIL_PUFFS];
    private final float[] trailSizeStep = new float[MAX_TRAIL_PUFFS];
    private final float[] trailAlpha = new float[MAX_TRAIL_PUFFS];
    private final float[] trailAlphaStep = new float[MAX_TRAIL_PUFFS];
    private final boolean pairedTrail;
    private final int trailStartTicks;
    private final int trailSlotLimit;
    private final Actor actor = new Actor();
    private int delayTicks;
    private int activeTicks;
    private int flyOffTicks;
    private int orbitTicks;
    private int trailSlotCount;
    private int activeTrailCount;
    private int nextTrailIndex;
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
    private boolean trailEmissionEnabled = true;
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
        pairedTrail = "plane002".equals(route.linkage);
        trailStartTicks = pairedTrail ? 1 : 5;
        trailSlotLimit = pairedTrail ? 22 : 50;
        trailRandom = new Random(trailSeed(route, delayTicks));
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

        if (phase == Phase.FLY_OFF) {
            advanceFlyOff();
            return false;
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
                clearTrail();
                impactPending = true;
                if (route.intercepted) {
                    actor.visible = false;
                    phase = Phase.FINISHED;
                    finished = true;
                } else {
                    phase = Phase.FLY_OFF;
                }
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
        updateTrail();
        return false;
    }

    private void advanceFlyOff() {
        actor.previousX = actor.sourceX;
        actor.previousY = actor.sourceY;
        actor.sourceX += xStep;
        actor.sourceY += yStep;
        distanceTravelled += distance(actor.previousX, actor.previousY,
                actor.sourceX, actor.sourceY);
        actor.sourceFrame = directionFrame(actor.sourceX - actor.previousX,
                actor.sourceY - actor.previousY);
        updateRouteProgress();

        // Unit.moveUnit increments curCountToStop twice on ordinary flyOff ticks.
        flyOffTicks++;
        if (flyOffTicks >= FLY_OFF_DURATION_TICKS) {
            actor.visible = false;
            phase = Phase.FINISHED;
            finished = true;
        }
    }

    private void updateTrail() {
        if (trailEmissionEnabled && activeTicks >= trailStartTicks
                && nextTrailIndex < trailSlotLimit) {
            if (pairedTrail) {
                createPairedTrailPuff(nextTrailIndex++);
            } else {
                for (int emitted = 0; emitted < SINGLE_TRAIL_PUFFS_PER_TICK
                        && nextTrailIndex < trailSlotLimit; emitted++) {
                    createSingleTrailPuff(nextTrailIndex++);
                }
            }
        }

        for (int index = 0; index < trailSlotCount; index++) {
            if (trailAlpha[index] <= 0f) {
                continue;
            }
            trailSize[index] += trailSizeStep[index];
            trailAlpha[index] -= trailAlphaStep[index];
            if (trailAlpha[index] <= 0f) {
                trailAlpha[index] = 0f;
                activeTrailCount--;
            }
        }
    }

    private void createSingleTrailPuff(int trailIndex) {
        addTrailPuff(trailIndex, actor.previousX, actor.previousY, 8f,
                randomBetween(50 - trailIndex, 100 - trailIndex),
                randomBetween(5, 11) / 12f,
                randomBetween(3, 15) / 3f);
    }

    private void createPairedTrailPuff(int trailIndex) {
        float dx = actor.sourceX - actor.previousX;
        float dy = -(actor.sourceY - actor.previousY);
        float angle = dx == 0f && dy == 0f ? 0f
                : -(Math.round((float) Math.toDegrees(Math.atan2(dy, dx))) + 181f);
        float leftAngle = (angle - 90f) / 180f * (float) Math.PI * -1f;
        float rightAngle = (angle + 90f) / 180f * (float) Math.PI * -1f;
        float leftAlpha = 100 - randomBetween(22 - trailIndex, 100 - trailIndex);
        float rightAlpha = 100 - randomBetween(22 - trailIndex, 100 - trailIndex);
        addTrailPuff(trailIndex * 2, actor.sourceX + (float) Math.cos(leftAngle) * 15f,
                actor.sourceY + (float) Math.sin(leftAngle) * 15f, 5f, leftAlpha,
                randomBetween(1, 2), randomBetween(1, 4));
        addTrailPuff(trailIndex * 2 + 1,
                actor.sourceX + (float) Math.cos(rightAngle) * 15f,
                actor.sourceY + (float) Math.sin(rightAngle) * 15f, 5f, rightAlpha,
                randomBetween(1, 2), randomBetween(1, 4));
    }

    private void addTrailPuff(int index, float x, float y, float size,
                              float alpha, float sizeStep, float alphaStep) {
        if (index >= trailX.length) {
            return;
        }
        trailX[index] = x;
        trailY[index] = y;
        trailProgress[index] = actor.routeProgress;
        trailSize[index] = size;
        trailSizeStep[index] = sizeStep;
        trailAlpha[index] = Math.max(0f, alpha);
        trailAlphaStep[index] = alphaStep;
        trailSlotCount = Math.max(trailSlotCount, index + 1);
        if (trailAlpha[index] > 0f) {
            activeTrailCount++;
        }
    }

    private void clearTrail() {
        trailEmissionEnabled = false;
        activeTrailCount = 0;
        for (int index = 0; index < trailSlotCount; index++) {
            trailAlpha[index] = 0f;
        }
    }

    private int randomBetween(int minimum, int maximum) {
        return minimum + trailRandom.nextInt(maximum - minimum + 1);
    }

    private static long trailSeed(FlashBattleRoutes.StandalonePlaneStrike route,
                                  int delayTicks) {
        long seed = 0x9E3779B97F4A7C15L;
        seed = seed * 31L + route.attackerPlayerId;
        seed = seed * 31L + route.targetPlayerId;
        seed = seed * 31L + route.targetCityIndex;
        seed = seed * 31L + route.actionBuildingSlot;
        seed = seed * 31L + route.linkage.hashCode();
        return seed * 31L + delayTicks;
    }

    static int flashFlyOffDurationTicks() {
        return FLY_OFF_DURATION_TICKS;
    }

    private static int calculateFlashFlyOffDurationTicks() {
        int count = 0;
        int ticks = 0;
        while (true) {
            ticks++;
            if (count <= 0) {
                count++;
            } else if (count < FLY_OFF_COUNTER_LIMIT) {
                count += 2;
            } else {
                return ticks;
            }
        }
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

    public int getTrailCount() {
        return activeTrailCount;
    }

    public int getTrailSlotCount() {
        return trailSlotCount;
    }

    public float getTrailX(int index) {
        return trailX[index];
    }

    public float getTrailY(int index) {
        return trailY[index];
    }

    public float getTrailProgress(int index) {
        return trailProgress[index];
    }

    public float getTrailSize(int index) {
        return trailSize[index];
    }

    public float getTrailAlpha(int index) {
        return Math.max(0f, Math.min(1f, trailAlpha[index] / 100f));
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
