package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** Source-tick playback for the original silo launched rocket and its Flash smoke trail. */
public final class FlashRocketAnimation {
    public static final class Actor {
        /** Current source stage x coordinate. */
        public float sourceX;
        /** Current source stage y coordinate. */
        public float sourceY;
        /** Sprite orientation in libGDX counterclockwise degrees. */
        public float rotationDegrees;
        /** Island used to project this actor through the battlefield crop. */
        public int islandPlayerId;
        /** Whether the rocket sprite is currently visible. */
        public boolean visible;
    }

    private static final int TRAIL_INTERVAL_TICKS = 5;
    private static final int TRAIL_LIFETIME_TICKS = 50;
    private static final int TRAIL_CAPACITY = 10;

    /** Source silo route and target data. */
    private final FlashBattleRoutes.RocketStrike route;
    /** Flash linkage played when this flight reaches its target. */
    private final String impactLinkage;
    /** Original audio played when the impact animation starts. */
    private final OriginalAudio.Cue impactCue;
    /** Reused rocket sprite state. */
    private final Actor rocket = new Actor();
    /** Reused source x coordinates for fading original smoke puffs. */
    private final float[] trailX = new float[TRAIL_CAPACITY];
    /** Reused source y coordinates for fading original smoke puffs. */
    private final float[] trailY = new float[TRAIL_CAPACITY];
    /** Reused source tick ages for fading original smoke puffs. */
    private final int[] trailAge = new int[TRAIL_CAPACITY];
    /** Number of active trail puffs. */
    private int trailCount;
    /** Source ticks remaining before this flight begins. */
    private int delayTicks;
    /** Active source ticks since the rocket launched. */
    private int activeTicks;
    /** Degrees already traveled around the source half ellipse. */
    private float arcProgress;
    /** Source ticks since the last smoke puff was emitted. */
    private int trailCountdown;
    /** Current Flash ellipse angle in degrees. */
    private float angle;
    /** Previous source x coordinate, used to orient the sprite. */
    private float previousX;
    /** Previous source y coordinate, used to orient the sprite. */
    private float previousY;
    /** Whether the rocket launch sequence has started. */
    private boolean launched;
    /** Whether the original launch sound should be played. */
    private boolean launchSoundPending;
    /** Whether the impact animation should be started. */
    private boolean impactPending;
    /** Whether the rocket has reached its source drop threshold. */
    private boolean flightFinished;
    /** Whether all smoke puffs have faded after the rocket impact. */
    private boolean finished;

    /** Creates an unscheduled flight with no downstream effect metadata. */
    public FlashRocketAnimation(FlashBattleRoutes.RocketStrike route, int delayTicks) {
        this(route, delayTicks, null, null);
    }

    /** Creates a source-tick flight and the Flash effect/audio played at impact. */
    public FlashRocketAnimation(FlashBattleRoutes.RocketStrike route, int delayTicks,
                                String impactLinkage, OriginalAudio.Cue impactCue) {
        if (route == null || delayTicks < 0) {
            throw new IllegalArgumentException("Invalid rocket animation data");
        }
        this.route = route;
        this.delayTicks = delayTicks;
        this.impactLinkage = impactLinkage;
        this.impactCue = impactCue;
        angle = route.startAngle;
        rocket.sourceX = route.launch.x;
        rocket.sourceY = route.launch.y;
        rocket.islandPlayerId = route.attackerPlayerId;
    }

    /** Advances one original 24 Hz source tick and reports the exact impact tick once. */
    public boolean tick() {
        if (finished) {
            return false;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return false;
        }
        if (flightFinished) {
            ageTrail();
            finished = trailCount == 0;
            return false;
        }

        if (!launched) {
            launched = true;
            rocket.visible = true;
            launchSoundPending = true;
        }
        previousX = rocket.sourceX;
        previousY = rocket.sourceY;
        if (arcProgress < 180f) {
            setArcPosition(angle);
            float step = Math.min(activeTicks == 0 ? 1f : 3f, 180f - arcProgress);
            float verticalDirection = route.ellipseCenterY < route.launch.y ? -1f : 1f;
            angle += step * route.directionMultiplier * verticalDirection;
            arcProgress += step;
            activeTicks++;
            if (arcProgress >= 180f) {
                setArcPosition(angle);
            }
            setRocketRotation();
        } else {
            rocket.sourceY += 10f;
            float dropLimit = route.intercepted
                    ? route.target.y - 120f : route.target.y - 20f;
            if (rocket.sourceY >= dropLimit) {
                rocket.visible = false;
                flightFinished = true;
                impactPending = true;
            } else {
                setRocketRotation();
            }
            activeTicks++;
        }
        ageTrail();
        if (rocket.visible && ++trailCountdown >= TRAIL_INTERVAL_TICKS) {
            trailCountdown = 0;
            addTrailPoint(rocket.sourceX, rocket.sourceY);
        }
        return takeImpact();
    }

    /** Returns and clears the one-shot launch sound signal. */
    public boolean takeLaunchSound() {
        boolean pending = launchSoundPending;
        launchSoundPending = false;
        return pending;
    }

    /** Returns the recovered original rocket launch sound. */
    public OriginalAudio.Cue getLaunchCue() {
        return OriginalAudio.Cue.WARROCKET;
    }

    /** Returns and clears the one-shot impact signal. */
    public boolean takeImpact() {
        boolean pending = impactPending;
        impactPending = false;
        return pending;
    }

    /** Returns whether the rocket flight and every smoke puff have finished. */
    public boolean isFinished() {
        return finished;
    }

    /** Returns whether the half ellipse is complete and the source drop has begun. */
    public boolean isDropping() {
        return arcProgress >= 180f && !flightFinished;
    }

    /** Returns whether the rocket has hit its source drop threshold. */
    public boolean isFlightFinished() {
        return flightFinished;
    }

    /** Returns the reused rocket sprite state. */
    public Actor getRocket() {
        return rocket;
    }

    /** Returns the immutable source route used for this flight. */
    public FlashBattleRoutes.RocketStrike getRoute() {
        return route;
    }

    /** Returns the Flash explosion linkage for this event, when one is mapped. */
    public String getImpactLinkage() {
        return impactLinkage;
    }

    /** Returns the original explosion cue for this event. */
    public OriginalAudio.Cue getImpactCue() {
        return impactCue;
    }

    /** Returns the source x coordinate where the rocket reached its drop threshold. */
    public float getImpactSourceX() {
        return rocket.sourceX;
    }

    /** Returns the source y coordinate where the rocket reached its drop threshold. */
    public float getImpactSourceY() {
        return rocket.sourceY;
    }

    /** Returns the number of currently visible source smoke puffs. */
    public int getTrailCount() {
        return trailCount;
    }

    /** Returns a trail puff's source x coordinate. */
    public float getTrailX(int index) {
        return trailX[index];
    }

    /** Returns a trail puff's source y coordinate. */
    public float getTrailY(int index) {
        return trailY[index];
    }

    /** Returns a trail puff's remaining alpha from 0 through 1. */
    public float getTrailAlpha(int index) {
        return Math.max(0f, 1f - trailAge[index] / (float) TRAIL_LIFETIME_TICKS);
    }

    private void setArcPosition(float currentAngle) {
        double radians = Math.toRadians(currentAngle);
        rocket.sourceX = route.ellipseCenterX
                + route.ellipseRadiusX * (float) Math.cos(radians);
        rocket.sourceY = route.ellipseCenterY
                + route.ellipseRadiusY * (float) Math.sin(radians);
    }

    private void setRocketRotation() {
        float deltaX = rocket.sourceX - previousX;
        float deltaY = rocket.sourceY - previousY;
        if (deltaX != 0f || deltaY != 0f) {
            rocket.rotationDegrees = (float) Math.toDegrees(Math.atan2(-deltaY, deltaX));
        }
    }

    private void ageTrail() {
        for (int index = 0; index < trailCount; index++) {
            trailAge[index]++;
        }
        while (trailCount > 0 && trailAge[0] >= TRAIL_LIFETIME_TICKS) {
            trailCount--;
            System.arraycopy(trailX, 1, trailX, 0, trailCount);
            System.arraycopy(trailY, 1, trailY, 0, trailCount);
            System.arraycopy(trailAge, 1, trailAge, 0, trailCount);
        }
    }

    private void addTrailPoint(float x, float y) {
        if (trailCount == TRAIL_CAPACITY) {
            trailCount--;
            System.arraycopy(trailX, 1, trailX, 0, trailCount);
            System.arraycopy(trailY, 1, trailY, 0, trailCount);
            System.arraycopy(trailAge, 1, trailAge, 0, trailCount);
        }
        trailX[trailCount] = x;
        trailY[trailCount] = y;
        trailAge[trailCount] = 0;
        trailCount++;
    }
}
