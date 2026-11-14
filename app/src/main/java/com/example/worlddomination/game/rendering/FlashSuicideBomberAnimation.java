package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** Source-tick presentation for the soldier4 suicide bomber and city explosion. */
public final class FlashSuicideBomberAnimation {
    public interface FrameListener {
        void onWaitingFrame(double sourceY);
        void onMovingFrame(double sourceY);
        void onExplosionAttached();
    }
    private enum Phase {
        WAITING,
        FADE_OUT,
        DISAPPEAR,
        TELEPORT_TO_HOME,
        FADE_IN,
        WALKING,
        EXPLOSION,
        DYING
    }

    public static final class Actor {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        /** Actual MovieClip pose; killUnit attaches before the logical post-move step. */
        public float renderX;
        public float renderY;
        public float alpha = 1f;
        public int islandPlayerId;
        public boolean visible;
    }

    private static final int DISAPPEAR_WAIT_MAX = 25;
    private static final int BOMBER_FRAMES_PER_DIRECTION = 12;
    private static final int EXPLOSION_FRAME_COUNT = 21;
    private static final double WALK_STEP = 4d;
    private static final double WALK_X_ACCELERATION = 0.5d;
    private static final double WALK_Y_ACCELERATION = 0.4d;
    private static final float TARGET_RANGE = 10f;
    private static final float[][] ISLAND_CENTERS = {
            {420f, 420f}, {160f, 201f}, {563f, 108f}, {260f, 500f}, {562f, 511f}
    };

    private final FlashBattleRoutes.SuicideBomberAttack route;
    private final OriginalAudio.Cue screamCue;
    private final Actor bomber = new Actor();
    private final Actor tint = new Actor();
    private final Actor explosion = new Actor();
    private final Actor[] actors = {bomber, tint, explosion};
    private final OriginalAudio.Cue[] pendingAudio = new OriginalAudio.Cue[2];
    private int delayTicks;
    private int disappearTicks;
    private int waypointIndex;
    private int walkFrameOffset = 1;
    private double logicalX;
    private double logicalY;
    private double xStep;
    private double yStep;
    private double rawAlphaPercent = 100d;
    private int pendingAudioCount;
    private int pendingAudioIndex;
    private OriginalAudio.Cue lastAudioCue;
    private Phase phase = Phase.WAITING;
    private FrameListener frameListener;
    private SourceStoppedClipQueue.Clip stoppedClip;
    private boolean stoppedClipQueued;
    private boolean cityImpact;
    private boolean removed;
    private boolean firstWaitingFrame = true;
    private boolean insideRangeBeforeMove;
    private double previousYDistance;

    public FlashSuicideBomberAnimation(FlashBattleRoutes.SuicideBomberAttack route,
                                       OriginalAudio.Cue screamCue, int delayTicks) {
        this(route, screamCue, delayTicks, 2);
    }

    public FlashSuicideBomberAnimation(FlashBattleRoutes.SuicideBomberAttack route,
                                       OriginalAudio.Cue screamCue, int delayTicks,
                                       int playerColor) {
        if (route == null || screamCue == null || delayTicks < 0) {
            throw new IllegalArgumentException("Invalid suicide-bomber animation");
        }
        this.route = route;
        this.screamCue = screamCue;
        this.delayTicks = delayTicks;
        bomber.linkage = "suicide_bomber";
        bomber.visible = true;
        // Unit constructor has logical and displayed origin (0,0). View seeks
        // heliStart during the waiting-frame traversal, including action labels.
        bomber.islandPlayerId = route.attackerPlayerId;
        tint.linkage = "suicide_bomber_tint";
        tint.visible = true;
        tint.alpha = FlashColorTransform.forPlayerColor(playerColor).alphaMultiplier;
        tint.islandPlayerId = route.attackerPlayerId;
        explosion.linkage = "explosion001";
        explosion.visible = false;
        explosion.islandPlayerId = route.targetPlayerId;
    }

    public void setFrameListener(FrameListener listener) { frameListener = listener; }
    public void bindStoppedClip(SourceStoppedClipQueue.Clip clip) { stoppedClip = clip; }
    public int getAttackerPlayerId() { return route.attackerPlayerId; }
    public FlashBattleRoutes.SuicideBomberAttack getRoute() { return route; }

    /** View.waitForHeliStart runs even before this Unit can be visited. */
    public void presentWaitingFrame() {
        if (phase != Phase.WAITING) return;
        logicalX = route.initialPosition.x;
        logicalY = route.initialPosition.y;
        bomber.sourceFrame = 49; // stopped Flash frame 50
        publishBodyPose();
        tint.sourceFrame = bomber.sourceFrame;
        phase = Phase.FADE_OUT;
        if (firstWaitingFrame && frameListener != null) {
            frameListener.onWaitingFrame(logicalY);
        }
        firstWaitingFrame = false;
    }

    /** One visited source Unit frame; true means the wrapper leaves active units. */
    public boolean tickSourceFrame() {
        if (removed) return false;
        if (delayTicks > 0) {
            delayTicks--;
            return false;
        }
        if (phase == Phase.WAITING) {
            presentWaitingFrame();
            return false;
        }
        if (phase == Phase.DYING) {
            moveAfterDeath();
            removed = true;
            return true;
        }
        switch (phase) {
            case FADE_OUT:
                if (rawAlphaPercent > 0d) fadeBomber(-2d);
                moveToward(route.disappearPosition);
                if (insideRangeBeforeMove) phase = Phase.DISAPPEAR;
                break;
            case DISAPPEAR:
                if (disappearTicks == 0) enqueueAudio(screamCue);
                if (disappearTicks < DISAPPEAR_WAIT_MAX) {
                    disappearTicks++;
                    logicalX = route.disappearPosition.x;
                    logicalY = route.disappearPosition.y;
                    bomber.sourceFrame = 49;
                    tint.sourceFrame = 49;
                    publishBodyPose();
                } else if (disappearTicks == DISAPPEAR_WAIT_MAX) {
                    disappearTicks++;
                    // Unit.setStep visits the duplicated disappear waypoint:
                    // its x/y distances become zero before the move. The next
                    // waypoint's X step uses this saved zero Y distance.
                    previousYDistance = 0d;
                    logicalX -= 2d;
                    logicalY -= 2d;
                    publishMovingFrame();
                } else {
                    // The duplicate disappear waypoint seeks the target
                    // island's source spawn, then retains its movement step.
                    logicalX = route.spawn.x + xStep;
                    logicalY = route.spawn.y + yStep;
                    publishMovingFrame();
                    phase = Phase.TELEPORT_TO_HOME;
                }
                break;
            case TELEPORT_TO_HOME:
                phase = Phase.FADE_IN;
                moveToward(route.waypoints[0]);
                break;
            case FADE_IN:
                if (rawAlphaPercent < 100d) fadeBomber(1d);
                moveToward(route.waypoints[waypointIndex]);
                if (insideRangeBeforeMove) {
                    waypointIndex++;
                    phase = waypointIndex >= route.waypoints.length
                            ? Phase.EXPLOSION : Phase.WALKING;
                }
                break;
            case WALKING:
                moveToward(route.waypoints[waypointIndex]);
                if (insideRangeBeforeMove && ++waypointIndex >= route.waypoints.length) {
                    phase = Phase.EXPLOSION;
                }
                break;
            case EXPLOSION:
                // Unit.suicide_explode invokes the city presenter before killUnit.
                cityImpact = true;
                enqueueAudio(OriginalAudio.Cue.SUICIDE_EXPLODE);
                explosion.renderX = bomber.renderX;
                explosion.renderY = bomber.renderY;
                explosion.sourceFrame = 0;
                explosion.visible = true;
                if (frameListener != null) frameListener.onExplosionAttached();
                phase = Phase.DYING;
                moveAfterDeath();
                break;
            default:
                throw new IllegalStateException("Unexpected suicide action " + phase);
        }
        if (frameListener != null && phase != Phase.DYING) {
            frameListener.onMovingFrame(logicalY);
        }
        return false;
    }

    /** Compatibility for callers not yet integrated with source visitation. */
    public void tick() { tickSourceFrame(); }

    /** Direct-root explosion001 autoplays after its owner wrapper is removed. */
    public void advanceAttachedTimelines() {
        if (explosion.visible && explosion.sourceFrame < EXPLOSION_FRAME_COUNT - 1) {
            explosion.sourceFrame++;
        }
    }

    public void registerStoppedClips(SourceStoppedClipQueue queue) {
        if (removed && !stoppedClipQueued && stoppedClip != null) {
            queue.add(stoppedClip);
            stoppedClipQueued = true;
        }
    }

    public boolean takeCityImpact() {
        boolean pulse = cityImpact;
        cityImpact = false;
        return pulse;
    }

    public int getActorCount() {
        return actors.length;
    }

    /** Returns stable actor records without allocating during rendering. */
    public Actor getActor(int index) {
        return actors[index];
    }

    public boolean isFinished() {
        return removed;
    }

    public boolean isRemoved() { return removed; }

    public String getSourceAction() {
        switch (phase) {
            case WAITING: return "waitForHeliStart";
            case FADE_OUT: return "suicide_fade_out";
            case DISAPPEAR: return "suicide_dissapear";
            case TELEPORT_TO_HOME: return "suicide_appear";
            case FADE_IN: return "suicide_fade_in";
            case WALKING: return "default";
            case EXPLOSION: return "suicide_explode";
            case DYING: return "dying";
            default: return "stop";
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

    private void moveToward(FlashBattleRoutes.Point target) {
        double dx = target.x - logicalX;
        double dy = target.y - logicalY;
        double xDistance = Math.abs(dx);
        // Source Unit.setStep calculates X against the previous Y distance.
        double desiredX = Math.floor(WALK_STEP * xDistance
                / (xDistance + previousYDistance)) * (dx > 0d ? 1d : -1d);
        if (dx > 0f && desiredX > xStep) xStep += WALK_X_ACCELERATION;
        else if (dx <= 0f && desiredX < xStep) xStep -= WALK_X_ACCELERATION;
        double yDistance = Math.abs(dy);
        double desiredY = Math.floor(WALK_STEP * yDistance
                / (xDistance + yDistance)) * (dy > 0d ? 1d : -1d);
        if (dy > 0f && desiredY > yStep) yStep += WALK_Y_ACCELERATION;
        else if (dy <= 0f && desiredY < yStep) yStep -= WALK_Y_ACCELERATION;
        previousYDistance = yDistance;
        // The strict source rectangle is evaluated before the move.
        insideRangeBeforeMove = insideRange(logicalX, logicalY, target, TARGET_RANGE);
        logicalX += xStep;
        logicalY += yStep;
        publishMovingFrame();
    }

    private void moveAfterDeath() {
        logicalX += xStep;
        logicalY += yStep;
        publishMovingFrame();
        explosion.sourceX = bomber.sourceX;
        explosion.sourceY = bomber.sourceY;
    }

    private void publishMovingFrame() {
        int direction = directionFrame(xStep, yStep);
        // View.as:1412 computes one-based frame dir*12 - 11 + offset.
        bomber.sourceFrame = (direction - 1) * BOMBER_FRAMES_PER_DIRECTION
                + walkFrameOffset;
        walkFrameOffset = walkFrameOffset < BOMBER_FRAMES_PER_DIRECTION
                ? walkFrameOffset + 1 : 1;
        publishBodyPose();
        tint.sourceFrame = bomber.sourceFrame;
    }

    private void publishBodyPose() {
        bomber.sourceX = (float) logicalX;
        bomber.sourceY = (float) logicalY;
        bomber.renderX = bomber.sourceX;
        bomber.renderY = bomber.sourceY;
        bomber.islandPlayerId = nearestIsland(bomber.sourceX, bomber.sourceY);
        tint.sourceX = bomber.sourceX;
        tint.sourceY = bomber.sourceY;
        tint.renderX = bomber.renderX;
        tint.renderY = bomber.renderY;
        tint.islandPlayerId = bomber.islandPlayerId;
        explosion.sourceX = bomber.sourceX;
        explosion.sourceY = bomber.sourceY;
    }

    private void fadeBomber(double percent) {
        rawAlphaPercent += percent;
        bomber.alpha = (float) (rawAlphaPercent / 100d);
        tint.alpha = bomber.alpha;
    }

    private void enqueueAudio(OriginalAudio.Cue cue) {
        if (pendingAudioCount < pendingAudio.length) {
            pendingAudio[pendingAudioCount++] = cue;
        }
    }

    private static boolean insideRange(double x, double y,
                                       FlashBattleRoutes.Point point, float range) {
        return x < point.x + range && x > point.x - range
                && y < point.y + range && y > point.y - range;
    }

    private static int directionFrame(double dx, double dy) {
        int angle = (int) Math.floor(Math.atan2(-dy, dx) / Math.PI * 180d + 0.5d)
                + 181;
        // Preserve the source's seemingly redundant operations. At angle 330,
        // /360*100/100*18 is slightly below 16.5 and Math.round returns 16.
        double sourceFrame = (angle / 360d * 100d / 100d) * 18d;
        return (int) Math.floor(sourceFrame + 0.5d);
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
