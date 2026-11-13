package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** Source-tick playback for the soldier3 Chinook drop and tank attack. */
public final class FlashChinookTankAnimation {
    private enum Phase {
        CHINOOK_FLIGHT,
        TANK_DROP,
        TANK_DRIVE,
        TANK_FIRE,
        FINISHED
    }

    public static final class Actor {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        public int islandPlayerId;
        public boolean visible;
    }

    private static final int CHINOOK_FRAME_COUNT = 37;
    private static final int TANK_SHOT_COUNT = 60;
    private static final int TANK_DROP_TICKS = 20;
    private static final int CHINOOK_FLY_OFF_COUNT = 250;
    private static final int TANK_PRESHOT_THRESHOLD = 25;
    private static final int TANK_FLASH_RESET_THRESHOLD = 3;
    private static final float CHINOOK_STEP = 8f;
    private static final float TANK_STEP = 2f;
    private static final float ARRIVAL_RANGE = 10f;
    private static final float[][] ISLAND_CENTERS = {
            {420f, 420f}, {160f, 201f}, {563f, 108f}, {260f, 500f}, {562f, 511f}
    };

    private final FlashBattleRoutes.ChinookTankAttack route;
    private final Actor chinook = new Actor();
    private final Actor tank = new Actor();
    private final Actor turret = new Actor();
    private final Actor[] actors = {chinook, tank, turret};
    private final OriginalAudio.Cue[] pendingAudio = new OriginalAudio.Cue[8];
    private int delayTicks;
    private int pendingAudioCount;
    private int pendingAudioIndex;
    private int dropTicks;
    private int chinookFlyOffTicks;
    private int tankWaypointIndex;
    private int tankPreshotCount;
    private int shotCount;
    private boolean tankFireFinished;
    // View has already presented the waiting liftOff frame before activation.
    private int frameOffset = 1;
    private float chinookXStep;
    private float chinookYStep;
    private float tankXStep;
    private float tankYStep;
    private float chinookYDistance;
    private float tankYDistance;
    private float tankPreviousX;
    private float tankPreviousY;
    private float tankArrivalRange = ARRIVAL_RANGE;
    private OriginalAudio.Cue lastAudioCue;
    private Phase phase = Phase.CHINOOK_FLIGHT;

    public FlashChinookTankAnimation(FlashBattleRoutes.ChinookTankAttack route,
                                     int delayTicks) {
        if (route == null || delayTicks < 0 || route.tankWaypoints.length == 0) {
            throw new IllegalArgumentException("Invalid soldier3 animation");
        }
        this.route = route;
        this.delayTicks = delayTicks;
        chinook.linkage = "chinook";
        chinook.sourceX = route.chinookStart.x;
        chinook.sourceY = route.chinookStart.y;
        chinook.islandPlayerId = route.attackerPlayerId;
        tank.linkage = "tankBase";
        tank.sourceX = route.tankStart.x;
        tank.sourceY = route.tankStart.y;
        tank.islandPlayerId = route.targetPlayerId;
        turret.linkage = route.turretLinkage;
        turret.sourceX = route.turretStart.x;
        turret.sourceY = route.turretStart.y;
        turret.islandPlayerId = route.targetPlayerId;
        followChinook();
    }

    /** Advances one original 24 Hz presentation tick. */
    public void tick() {
        if (phase == Phase.FINISHED) {
            return;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        switch (phase) {
            case CHINOOK_FLIGHT:
                if (!chinook.visible) {
                    chinook.visible = true;
                    turret.visible = true;
                    enqueueAudio(OriginalAudio.Cue.TANK_CHOPPER);
                }
                if (moveToward(chinook, route.chinookDrop, CHINOOK_STEP, true)) {
                    phase = Phase.TANK_DROP;
                    dropTicks = 0;
                    enqueueAudio(OriginalAudio.Cue.TANK_CHOPPER);
                    tickTankDrop();
                } else {
                    followChinook();
                }
                aimTurret();
                break;
            case TANK_DROP:
                // The source has no second helicopter waypoint: NaN target
                // calculations leave its existing velocity unchanged.
                continueChinookFlight();
                tickTankDrop();
                aimTurret();
                break;
            case TANK_DRIVE:
                tickChinookFlyOff();
                if (moveTankAlongSourceRoute()) {
                    phase = Phase.TANK_FIRE;
                    tankPreshotCount = 0;
                    shotCount = 0;
                    // View moves the base before the turret. The turret sees
                    // tank_shoot and counts its first firing tick immediately.
                    tickTankFire();
                }
                break;
            case TANK_FIRE:
                tickChinookFlyOff();
                tickTankFire();
                if (tankFireFinished && !chinook.visible) {
                    phase = Phase.FINISHED;
                }
                break;
            case FINISHED:
                break;
            default:
                throw new IllegalStateException("Unknown Chinook/tank phase");
        }
    }

    private void tickTankFire() {
        if (!tankFireFinished) {
            if (shotCount < TANK_SHOT_COUNT) {
                int restingFrame = directionFrame(
                        route.turretAimPoint.x - tank.sourceX,
                        route.turretAimPoint.y - tank.sourceY) * 2 - 2;
                if (turret.sourceFrame == restingFrame
                        && tankPreshotCount > TANK_PRESHOT_THRESHOLD) {
                    turret.sourceFrame = restingFrame + 1;
                    tankPreshotCount = 0;
                    enqueueAudio(OriginalAudio.Cue.TANK_GUN);
                } else if (tankPreshotCount > TANK_FLASH_RESET_THRESHOLD) {
                    turret.sourceFrame = Math.max(0, restingFrame);
                }
                shotCount++;
                // The source increments after resetting: subsequent shots
                // start with count one and keep the flash for four frames.
                tankPreshotCount++;
            } else {
                tank.visible = false;
                turret.visible = false;
                tankFireFinished = true;
            }
        }
    }

    public int getActorCount() {
        return actors.length;
    }

    /** Returns stable actor records without allocating during rendering. */
    public Actor getActor(int index) {
        return actors[index];
    }

    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    public boolean takeAudioCue() {
        if (pendingAudioIndex >= pendingAudioCount) {
            pendingAudioCount = 0;
            pendingAudioIndex = 0;
            return false;
        }
        lastAudioCue = pendingAudio[pendingAudioIndex];
        pendingAudio[pendingAudioIndex++] = null;
        return true;
    }

    public OriginalAudio.Cue getAudioCue() {
        return lastAudioCue;
    }

    private void tickChinookFlyOff() {
        if (!chinook.visible) {
            return;
        }
        if (chinookFlyOffTicks > 0) {
            if (chinookFlyOffTicks < CHINOOK_FLY_OFF_COUNT) {
                chinookFlyOffTicks++;
            } else {
                chinook.visible = false;
            }
        }
        chinookFlyOffTicks++;
        continueChinookFlight();
    }

    private void continueChinookFlight() {
        chinook.sourceX += chinookXStep;
        chinook.sourceY += chinookYStep;
        updateChinookFrame();
    }

    private void followChinook() {
        tankPreviousX = tank.sourceX;
        tankPreviousY = tank.sourceY;
        tank.sourceX = chinook.sourceX + 2f;
        tank.sourceY = chinook.sourceY + 30f;
    }

    private void tickTankDrop() {
        if (dropTicks < TANK_DROP_TICKS) {
            dropTicks++;
            tank.sourceY += 2f;
        } else {
            tank.visible = true;
            turret.visible = true;
            tank.sourceFrame = Math.max(0, directionFrame(tank.sourceX - tankPreviousX,
                    tank.sourceY - tankPreviousY) - 1);
            phase = Phase.TANK_DRIVE;
            enqueueAudio(OriginalAudio.Cue.TANK_DRIVE);
        }
    }

    private void aimTurret() {
        turret.sourceX = tank.sourceX;
        turret.sourceY = tank.sourceY;
        turret.sourceFrame = Math.max(0, directionFrame(route.turretAimPoint.x - turret.sourceX,
                route.turretAimPoint.y - turret.sourceY) * 2 - 2);
    }

    private void updateChinookFrame() {
        chinook.islandPlayerId = nearestIsland(chinook.sourceX, chinook.sourceY);
        chinook.sourceFrame = Math.max(0, Math.min(CHINOOK_FRAME_COUNT - 1,
                (directionFrame(chinookXStep, chinookYStep) - 1) * 2 + frameOffset));
        frameOffset = frameOffset < 2 ? frameOffset + 1 : 1;
    }

    private boolean moveTankAlongSourceRoute() {
        if (tankWaypointIndex >= route.tankWaypoints.length) {
            return true;
        }
        FlashBattleRoutes.Point target = route.tankWaypoints[tankWaypointIndex];
        if (moveToward(tank, target, TANK_STEP, false)) {
            tankWaypointIndex++;
            if (tankWaypointIndex == route.tankWaypoints.length - 1) {
                // The last authored twp is tank_reset_range: the source
                // switches to range 40 before approaching the firing point.
                tankArrivalRange = 40f;
            }
        }
        aimTurret();
        return tankWaypointIndex >= route.tankWaypoints.length;
    }

    private boolean moveToward(Actor actor, FlashBattleRoutes.Point target,
                               float step, boolean animateChinook) {
        float dx = target.x - actor.sourceX;
        float dy = target.y - actor.sourceY;
        double xDistance = Math.abs(dx);
        double oldYDistance = animateChinook ? chinookYDistance : tankYDistance;
        double yDistance = Math.abs(dy);
        // Unit.setStep updates xDist before X, but yDist only before Y.
        // Preserve both the stale distance and asymmetric acceleration.
        double targetXStep = sourceTargetStep(step, xDistance, oldYDistance, dx > 0f);
        double targetYStep = sourceTargetStep(step, yDistance, xDistance, dy > 0f);
        boolean arrived = insideRange(actor.sourceX, actor.sourceY, target,
                animateChinook ? 25f : tankArrivalRange);
        if (animateChinook) {
            chinookXStep = sourceAcceleration(chinookXStep, targetXStep, dx > 0f);
            chinookYStep = sourceAcceleration(chinookYStep, targetYStep, dy > 0f);
            chinookYDistance = (float) yDistance;
            actor.sourceX += chinookXStep;
            actor.sourceY += chinookYStep;
            updateChinookFrame();
        } else {
            tankXStep = sourceAcceleration(tankXStep, targetXStep, dx > 0f);
            tankYStep = sourceAcceleration(tankYStep, targetYStep, dy > 0f);
            tankYDistance = (float) yDistance;
            actor.sourceX += tankXStep;
            actor.sourceY += tankYStep;
            actor.sourceFrame = Math.max(0, directionFrame(tankXStep, tankYStep) - 1);
        }
        return arrived;
    }

    private static double sourceTargetStep(float step, double distance,
                                           double otherDistance, boolean positive) {
        double magnitude = Math.floor((step / 100d)
                * (100d / (distance + otherDistance)) * distance);
        return positive ? magnitude : -magnitude;
    }

    private static float sourceAcceleration(float current, double target, boolean positive) {
        if (positive && target > current) {
            return current + 2f;
        }
        if (!positive && target < current) {
            return current - 2f;
        }
        return current;
    }

    private void enqueueAudio(OriginalAudio.Cue cue) {
        if (pendingAudioCount < pendingAudio.length) {
            pendingAudio[pendingAudioCount++] = cue;
        }
    }

    private static boolean insideRange(float x, float y,
                                       FlashBattleRoutes.Point point, float range) {
        return x < point.x + range && x > point.x - range
                && y < point.y + range && y > point.y - range;
    }

    private static int directionFrame(float dx, float dy) {
        long angle = Math.round(Math.atan2(-dy, dx) / Math.PI * 180d) + 181;
        // Retain the source operation order at half-frame boundaries.
        return (int) Math.round((angle / 360d * 100d) / 100d * 18d);
    }

    private static int nearestIsland(float x, float y) {
        int nearest = 0;
        float nearestDistance = Float.MAX_VALUE;
        for (int player = 0; player < ISLAND_CENTERS.length; player++) {
            float dx = ISLAND_CENTERS[player][0] - x;
            float dy = ISLAND_CENTERS[player][1] - y;
            float distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
