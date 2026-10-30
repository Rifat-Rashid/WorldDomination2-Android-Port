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
    private static final int CHINOOK_FLY_OFF_TICKS = 250;
    private static final int GUN_SOUND_INTERVAL = 26;
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
    private int firingTicks;
    private int shotCount;
    private boolean tankFireFinished;
    private int frameOffset;
    private float chinookXStep;
    private float chinookYStep;
    private float tankXStep;
    private float tankYStep;
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
                    chinook.sourceFrame = 7; // Source liftOff fixedFrame=4, offsetFrames=2.
                    enqueueAudio(OriginalAudio.Cue.TANK_CHOPPER);
                }
                if (moveToward(chinook, route.chinookDrop, CHINOOK_STEP, true)) {
                    phase = Phase.TANK_DROP;
                    dropTicks = 0;
                    enqueueAudio(OriginalAudio.Cue.TANK_CHOPPER);
                }
                break;
            case TANK_DROP:
                chinook.sourceY += 2f;
                chinookFlyOffTicks++;
                dropTicks++;
                if (dropTicks >= TANK_DROP_TICKS) {
                    tank.visible = true;
                    turret.visible = true;
                    tank.sourceX = route.tankStart.x;
                    tank.sourceY = route.tankStart.y + 40f; // Source shadow offset at deploy.
                    turret.sourceX = tank.sourceX + 2f;
                    turret.sourceY = tank.sourceY + 30f;
                    phase = Phase.TANK_DRIVE;
                    enqueueAudio(OriginalAudio.Cue.TANK_DRIVE);
                }
                break;
            case TANK_DRIVE:
                tickChinookFlyOff();
                if (moveTankAlongSourceRoute()) {
                    phase = Phase.TANK_FIRE;
                    firingTicks = 0;
                    shotCount = 0;
                }
                break;
            case TANK_FIRE:
                tickChinookFlyOff();
                if (!tankFireFinished) {
                    firingTicks++;
                    shotCount++;
                    if (firingTicks > GUN_SOUND_INTERVAL) {
                        firingTicks = 0;
                        enqueueAudio(OriginalAudio.Cue.TANK_GUN);
                        turret.sourceFrame = (turret.sourceFrame & 1) == 0
                                ? turret.sourceFrame + 1 : turret.sourceFrame - 1;
                    }
                    if (shotCount >= TANK_SHOT_COUNT) {
                        tank.visible = false;
                        turret.visible = false;
                        tankFireFinished = true;
                    }
                }
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
        chinookFlyOffTicks++;
        if (chinookFlyOffTicks >= CHINOOK_FLY_OFF_TICKS) {
            chinook.visible = false;
        }
    }

    private boolean moveTankAlongSourceRoute() {
        if (tankWaypointIndex >= route.tankWaypoints.length) {
            return true;
        }
        FlashBattleRoutes.Point target = route.tankWaypoints[tankWaypointIndex];
        if (moveToward(tank, target, TANK_STEP, false)) {
            tankWaypointIndex++;
            if (tankWaypointIndex >= route.tankWaypoints.length) {
                turret.sourceX = tank.sourceX + 2f;
                turret.sourceY = tank.sourceY + 30f;
                return true;
            }
        }
        turret.sourceX = tank.sourceX + 2f;
        turret.sourceY = tank.sourceY + 30f;
        turret.sourceFrame = directionFrame(route.tankFirePoint.x - turret.sourceX,
                route.tankFirePoint.y - turret.sourceY) * 2 - 2;
        return false;
    }

    private boolean moveToward(Actor actor, FlashBattleRoutes.Point target,
                               float step, boolean animateChinook) {
        float dx = target.x - actor.sourceX;
        float dy = target.y - actor.sourceY;
        float xDistance = Math.abs(dx);
        float yDistance = Math.abs(dy);
        float totalDistance = xDistance + yDistance;
        float targetXStep = totalDistance == 0f ? 0f
                : (float) Math.floor(step * xDistance / totalDistance) * Math.signum(dx);
        float targetYStep = totalDistance == 0f ? 0f
                : (float) Math.floor(step * yDistance / totalDistance) * Math.signum(dy);
        if (animateChinook) {
            chinookXStep = approach(chinookXStep, targetXStep, 2f);
            chinookYStep = approach(chinookYStep, targetYStep, 2f);
            dx = chinookXStep;
            dy = chinookYStep;
        } else {
            tankXStep = targetXStep;
            tankYStep = targetYStep;
            dx = tankXStep;
            dy = tankYStep;
        }
        actor.sourceX += dx;
        actor.sourceY += dy;
        actor.islandPlayerId = nearestIsland(actor.sourceX, actor.sourceY);
        int direction = directionFrame(dx, dy);
        if (animateChinook) {
            actor.sourceFrame = (direction - 1) * 2 + (frameOffset++ % 2);
            if (actor.sourceFrame >= CHINOOK_FRAME_COUNT) {
                actor.sourceFrame = CHINOOK_FRAME_COUNT - 1;
            }
        } else {
            actor.sourceFrame = direction - 1;
        }
        return insideRange(actor.sourceX, actor.sourceY, target, animateChinook ? 25f : ARRIVAL_RANGE);
    }

    private void enqueueAudio(OriginalAudio.Cue cue) {
        if (pendingAudioCount < pendingAudio.length) {
            pendingAudio[pendingAudioCount++] = cue;
        }
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        if (current > target) {
            return Math.max(target, current - step);
        }
        return current;
    }

    private static boolean insideRange(float x, float y,
                                       FlashBattleRoutes.Point point, float range) {
        return x < point.x + range && x > point.x - range
                && y < point.y + range && y > point.y - range;
    }

    private static int directionFrame(float dx, float dy) {
        int angle = Math.round((float) Math.toDegrees(Math.atan2(-dy, dx))) + 181;
        return Math.max(1, Math.min(18, Math.round(angle / 360f * 18f)));
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
