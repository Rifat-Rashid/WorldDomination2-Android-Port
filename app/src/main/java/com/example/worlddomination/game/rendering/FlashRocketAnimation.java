package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;

/** Source-tick playback for the original silo launched rocket and its Flash smoke trail. */
public final class FlashRocketAnimation implements FlashAntiDefenseTrackingAnimation.Target {
    public static final class Actor {
        /** Current source stage x coordinate. */
        public float sourceX;
        /** Current source stage y coordinate. */
        public float sourceY;
        /** Sprite orientation in libGDX counterclockwise degrees. */
        public float rotationDegrees;
        /** Original sub_rocket movie clip uses a 50 percent vertical scale. */
        public float scaleY = 1f;
        /** Island used to project this actor through the battlefield crop. */
        public int islandPlayerId;
        /** Whether the rocket sprite is currently visible. */
        public boolean visible;
        /** One-based source movie frame; submarine launch switches from 1 to 3. */
        public int sourceFrame = 1;
    }

    /** Unit.wait_for_sub_launch releases the rocket after 35 source ticks. */
    public static final int SUBMARINE_LAUNCH_DELAY_TICKS = 35;

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
    private SourceRocketPlayback sourcePlayback;
    private long sourceLaunchSoundId = -1L;

    public long getSourceLaunchSoundId() { return sourceLaunchSoundId; }
    public void setSourceLaunchSoundId(long soundId) { sourceLaunchSoundId = soundId; }
    private FlashColorTransform sourceTint;
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
        rocket.scaleY = route.submarineLaunch ? 0.5f : 1f;
    }

    /** Source Unit/View path sharing the battle's isolated Python MT stream. */
    public static FlashRocketAnimation forSourceSubmarine(
            FlashBattleRoutes.RocketStrike route, String impactLinkage,
            OriginalAudio.Cue impactCue, ReferenceRandom sharedBattleRandom) {
        return forSourceSubmarine(route, impactLinkage, impactCue, sharedBattleRandom, 5);
    }

    public static FlashRocketAnimation forSourceSubmarine(
            FlashBattleRoutes.RocketStrike route, String impactLinkage,
            OriginalAudio.Cue impactCue, ReferenceRandom sharedBattleRandom,
            int attackerPlayerColor) {
        FlashRocketAnimation animation = new FlashRocketAnimation(
                route, 0, impactLinkage, impactCue);
        animation.sourcePlayback = new SourceRocketPlayback(route, sharedBattleRandom);
        animation.sourceTint = FlashColorTransform.forPlayerColor(attackerPlayerColor);
        animation.rocket.sourceX = 50f;
        animation.rocket.sourceY = 0f;
        animation.rocket.visible = true;
        return animation;
    }

    /** Visits an eligible source frame; readiness comes from the prior hull frame. */
    public boolean tickSourceFrame(boolean submarineLaunchReady) {
        if (sourcePlayback == null) {
            throw new IllegalStateException("Not a source rocket animation");
        }
        boolean impact = sourcePlayback.tick(submarineLaunchReady);
        rocket.sourceX = (float) sourcePlayback.getRenderX();
        rocket.sourceY = (float) sourcePlayback.getRenderY();
        rocket.rotationDegrees = (float) sourcePlayback.getRotation();
        rocket.sourceFrame = sourcePlayback.getSourceFrame();
        rocket.visible = sourcePlayback.isBodyAttached();
        launchSoundPending = sourcePlayback.takeLaunchSound();
        impactPending = impact;
        return takeImpact();
    }

    public SourceRocketPlayback getSourcePlayback() { return sourcePlayback; }
    public FlashColorTransform getSourceTint() { return sourceTint; }

    /** Advances one original 24 Hz source tick and reports the exact impact tick once. */
    public boolean tick() {
        if (sourcePlayback != null) {
            throw new IllegalStateException("Use tickSourceFrame with submarine readiness");
        }
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
    @Override
    public boolean isFinished() {
        return sourcePlayback == null ? finished : sourcePlayback.isRemoved();
    }

    /** Returns whether the half ellipse is complete and the source drop has begun. */
    public boolean isDropping() {
        return sourcePlayback == null ? arcProgress >= 180f && !flightFinished
                : sourcePlayback.isDropping();
    }

    /** Returns whether the rocket has hit its source drop threshold. */
    public boolean isFlightFinished() {
        return sourcePlayback == null ? flightFinished : !sourcePlayback.isBodyAttached();
    }

    /** Returns the reused rocket sprite state. */
    public Actor getRocket() {
        return rocket;
    }

    /** Returns the immutable source route used for this flight. */
    public FlashBattleRoutes.RocketStrike getRoute() {
        return route;
    }

    @Override
    public int targetPlayerId() {
        return route.targetPlayerId;
    }

    @Override
    public float sourceX() {
        return rocket.sourceX;
    }

    @Override
    public float sourceY() {
        return rocket.sourceY;
    }

    /** Keep the defense clip alive through the source rocket's post-impact trail cleanup. */
    @Override
    public boolean isVisible() {
        return sourcePlayback == null ? launched && !finished
                : sourcePlayback.isBodyAttached();
    }

    /** Returns the Flash explosion linkage for this event, when one is mapped. */
    public String getImpactLinkage() {
        return sourcePlayback != null && sourcePlayback.isSelfImpact()
                ? "explosion003" : impactLinkage;
    }

    /** Returns the original explosion cue for this event. */
    public OriginalAudio.Cue getImpactCue() {
        return sourcePlayback != null && sourcePlayback.isSelfImpact()
                ? OriginalAudio.Cue.EXPLOSION_SMALL3 : impactCue;
    }

    /** Returns the source x coordinate where the rocket reached its drop threshold. */
    public float getImpactSourceX() {
        return sourcePlayback == null ? rocket.sourceX
                : (float) sourcePlayback.getImpactEffectX();
    }

    /** Returns the source y coordinate where the rocket reached its drop threshold. */
    public float getImpactSourceY() {
        return sourcePlayback == null ? rocket.sourceY
                : (float) sourcePlayback.getImpactEffectY();
    }

    /** Returns the number of currently visible source smoke puffs. */
    public int getTrailCount() {
        return sourcePlayback == null ? trailCount : sourcePlayback.getAttachedTrailCount();
    }

    /** Returns a trail puff's source x coordinate. */
    public float getTrailX(int index) {
        return sourcePlayback == null ? trailX[index]
                : (float) sourcePlayback.getTrailX(sourceTrailSlot(index));
    }

    /** Returns a trail puff's source y coordinate. */
    public float getTrailY(int index) {
        return sourcePlayback == null ? trailY[index]
                : (float) sourcePlayback.getTrailY(sourceTrailSlot(index));
    }

    /** Returns a trail puff's remaining alpha from 0 through 1. */
    public float getTrailAlpha(int index) {
        return sourcePlayback == null
                ? Math.max(0f, 1f - trailAge[index] / (float) TRAIL_LIFETIME_TICKS)
                : (float) Math.max(0d, sourcePlayback.getTrailAlpha(sourceTrailSlot(index)) / 100d);
    }

    private int sourceTrailSlot(int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= sourcePlayback.getAttachedTrailCount()) {
            throw new IndexOutOfBoundsException("Visible source smoke index");
        }
        for (int slot = 0; slot < sourcePlayback.getTrailSlotCount(); slot++) {
            if (sourcePlayback.isTrailAttached(slot) && visibleIndex-- == 0) return slot;
        }
        throw new IllegalStateException("Source smoke count changed during draw");
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
