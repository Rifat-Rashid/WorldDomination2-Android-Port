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

    public static final class Actor implements SourceStoppedClipQueue.Clip {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        public int islandPlayerId;
        public boolean visible;
        private Actor pairedShadow;

        @Override public void detach() {
            visible = false;
            if (pairedShadow != null) pairedShadow.visible = false;
        }
    }

    private static final int CHINOOK_FRAME_COUNT = 37;
    private static final int TANK_SHOT_COUNT = 60;
    private static final int TANK_DROP_TICKS = 20;
    private static final int CHINOOK_FLY_OFF_COUNT = 250;
    private static final int TANK_PRESHOT_THRESHOLD = 25;
    private static final int TANK_FLASH_RESET_THRESHOLD = 3;
    private static final float CHINOOK_STEP = 8f;
    private static final float CHINOOK_ARRIVAL_RANGE = 25f;
    private static final float TANK_STEP = 2f;
    private static final float ARRIVAL_RANGE = 10f;
    private static final float[][] ISLAND_CENTERS = {
            {420f, 420f}, {160f, 201f}, {563f, 108f}, {260f, 500f}, {562f, 511f}
    };

    private final FlashBattleRoutes.ChinookTankAttack route;
    private final Actor chinook = new Actor();
    private final Actor tank = new Actor();
    private final Actor turret = new Actor();
    private final Actor chinookShadow = new Actor();
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
    private boolean chinookStopped;
    private boolean chinookRemoved;
    private boolean tankStopped;
    private boolean tankRemoved;
    private boolean turretStopped;
    private boolean turretRemoved;
    private boolean chinookQueued;
    private boolean tankQueued;
    private boolean turretQueued;
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
    private float tankShadowOffsetY = 40f;
    private float cropProgress;
    private OriginalAudio.Cue lastAudioCue;
    private Phase phase = Phase.CHINOOK_FLIGHT;

    public FlashChinookTankAnimation(FlashBattleRoutes.ChinookTankAttack route,
                                     int delayTicks) {
        if (route == null || delayTicks < 0 || route.tankWaypoints.length == 0) {
            throw new IllegalArgumentException("Invalid soldier3 animation");
        }
        this.route = route;
        this.delayTicks = delayTicks;
        cropProgress = startDistance() <= CHINOOK_ARRIVAL_RANGE ? 1f : 0f;
        chinook.linkage = "chinook";
        chinook.sourceX = route.chinookStart.x;
        chinook.sourceY = route.chinookStart.y;
        chinook.islandPlayerId = route.attackerPlayerId;
        // Flash's export lookup is case-insensitive; symbol 714 is exported
        // as tank_noshade while Unit uses tank_noShade.
        tank.linkage = "tank_noshade";
        tank.sourceX = route.tankStart.x;
        tank.sourceY = route.tankStart.y;
        tank.islandPlayerId = route.targetPlayerId;
        turret.linkage = route.turretLinkage;
        turret.sourceX = route.turretStart.x;
        turret.sourceY = route.turretStart.y;
        turret.islandPlayerId = route.targetPlayerId;
        chinookShadow.linkage = "chinook_shadow";
        chinook.pairedShadow = chinookShadow;
        followChinook();
        // The waiting tail has no movement; View holds Flash direction frame 9.
        tank.sourceFrame = 8;
        updateChinookShadow();
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
                    tank.visible = true;
                    turret.visible = true;
                    enqueueAudio(OriginalAudio.Cue.TANK_CHOPPER);
                }
                boolean arrived = moveToward(chinook, route.chinookDrop, CHINOOK_STEP, true);
                updateCropProgress();
                if (arrived) {
                    // Arrival is tested before the source position advances. Keep
                    // the target crop through descent even if that step overshoots.
                    cropProgress = 1f;
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
                if (tankRemoved && turretRemoved && chinookRemoved) {
                    phase = Phase.FINISHED;
                }
                break;
            case FINISHED:
                break;
            default:
                throw new IllegalStateException("Unknown Chinook/tank phase");
        }
        updateChinookShadow();
    }

    private void tickTankFire() {
        if (tankStopped) {
            // View checks stop before the next moveUnit call, then splices both
            // stable wrappers. Their clips remain until Presenter.allUnitsDone.
            tankRemoved = true;
            turretRemoved = true;
            tankFireFinished = true;
        } else if (!tankFireFinished) {
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
                // Unit.tank_top_shoot sets the base and turret actions to stop
                // together on the post-60-shot tick; View removes them next tick.
                tankStopped = true;
                turretStopped = true;
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

    /** Separate source shadow clip, with its inner mc following the carrier frame. */
    public Actor getChinookShadow() {
        return chinookShadow;
    }

    public boolean isTankDeployed() {
        return phase == Phase.TANK_DRIVE || phase == Phase.TANK_FIRE || phase == Phase.FINISHED;
    }

    public int getAttackerPlayerId() {
        return route.attackerPlayerId;
    }

    public int getTargetPlayerId() {
        return route.targetPlayerId;
    }

    /** Monotone presentation progress; source movement and timing are unchanged. */
    public float getCropProgress() {
        return cropProgress;
    }

    private float startDistance() {
        return Math.max(Math.abs(route.chinookStart.x - route.chinookDrop.x),
                Math.abs(route.chinookStart.y - route.chinookDrop.y));
    }

    // Phone-only reflow: blend island crop shifts along the unchanged source
    // route. Chebyshev distance matches the source's two-axis arrival rectangle.
    private void updateCropProgress() {
        float initial = startDistance();
        if (initial <= CHINOOK_ARRIVAL_RANGE) {
            cropProgress = 1f;
            return;
        }
        float remaining = Math.max(Math.abs(chinook.sourceX - route.chinookDrop.x),
                Math.abs(chinook.sourceY - route.chinookDrop.y));
        float progress = Math.max(0f, Math.min(1f,
                (initial - remaining) / (initial - CHINOOK_ARRIVAL_RANGE)));
        cropProgress = Math.max(cropProgress, progress);
    }

    public boolean isTankShadowVisible() {
        return phase == Phase.TANK_DROP && dropTicks > 0;
    }

    /** Script offset from the authored child placement, including parent-frame rewinds. */
    public float getTankShadowOffsetY() {
        return tankShadowOffsetY;
    }

    public boolean isFinished() {
        return chinookRemoved && tankRemoved && turretRemoved;
    }

    public boolean isChinookStopped() { return chinookStopped; }
    public boolean isChinookRemoved() { return chinookRemoved; }
    public boolean isTankStopped() { return tankStopped; }
    public boolean isTurretStopped() { return turretStopped; }
    public boolean isTankRemoved() { return tankRemoved; }
    public boolean isTurretRemoved() { return turretRemoved; }

    /** Physical View.for-in removal order is Chinook, tank base, then turret. */
    public void registerStoppedClips(SourceStoppedClipQueue queue) {
        if (chinookRemoved && !chinookQueued) {
            queue.add(chinook);
            chinookQueued = true;
        }
        if (tankRemoved && !tankQueued) {
            queue.add(tank);
            tankQueued = true;
        }
        if (turretRemoved && !turretQueued) {
            queue.add(turret);
            turretQueued = true;
        }
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
        if (chinookRemoved) {
            return;
        }
        if (chinookStopped) {
            // Unlike soldier/marine, View continues processing the wrapper
            // after splicing it, so activeAndMoving advances one final step.
            continueChinookFlight();
            chinookRemoved = true;
            return;
        }
        if (chinookFlyOffTicks > 0) {
            if (chinookFlyOffTicks < CHINOOK_FLY_OFF_COUNT) {
                chinookFlyOffTicks++;
            } else {
                chinookStopped = true;
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
        tank.islandPlayerId = chinook.islandPlayerId;
        updateAttachedTankFrame();
    }

    private void tickTankDrop() {
        if (dropTicks < TANK_DROP_TICKS) {
            dropTicks++;
            tank.sourceY += 2f;
            tankShadowOffsetY -= 2f;
            updateAttachedTankFrame();
        } else {
            tank.linkage = "tankBase";
            tank.visible = true;
            turret.visible = true;
            tank.islandPlayerId = route.targetPlayerId;
            tank.sourceFrame = Math.max(0, directionFrame(tank.sourceX - tankPreviousX,
                    tank.sourceY - tankPreviousY) - 1);
            phase = Phase.TANK_DRIVE;
            enqueueAudio(OriginalAudio.Cue.TANK_DRIVE);
        }
    }

    private void aimTurret() {
        turret.sourceX = tank.sourceX;
        turret.sourceY = tank.sourceY;
        turret.islandPlayerId = tank.islandPlayerId;
        turret.sourceFrame = Math.max(0, directionFrame(route.turretAimPoint.x - turret.sourceX,
                route.turretAimPoint.y - turret.sourceY) * 2 - 2);
    }

    private void updateAttachedTankFrame() {
        int frame = Math.max(0, directionFrame(tank.sourceX - tankPreviousX,
                tank.sourceY - tankPreviousY) - 1);
        // Reference gotoAndStop rewinds reapply the authored child matrix,
        // while the script-set visibility survives the seek.
        if (frame < tank.sourceFrame) tankShadowOffsetY = 0f;
        tank.sourceFrame = frame;
    }

    private void updateChinookShadow() {
        chinookShadow.sourceX = chinook.sourceX;
        chinookShadow.sourceY = chinook.sourceY + 70f;
        chinookShadow.sourceFrame = chinook.sourceFrame;
        chinookShadow.islandPlayerId = chinook.islandPlayerId;
        chinookShadow.visible = chinook.visible;
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
                animateChinook ? CHINOOK_ARRIVAL_RANGE : tankArrivalRange);
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
