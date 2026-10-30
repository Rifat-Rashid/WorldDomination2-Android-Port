package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** Source-tick presentation for the soldier4 suicide bomber and city explosion. */
public final class FlashSuicideBomberAnimation {
    private enum Phase {
        SCREAM,
        FADE_IN,
        WALKING,
        EXPLOSION,
        FINISHED
    }

    public static final class Actor {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        public float alpha = 1f;
        public int islandPlayerId;
        public boolean visible;
    }

    private static final int SCREAM_TICKS = 25;
    private static final int BOMBER_FRAMES_PER_DIRECTION = 12;
    private static final int EXPLOSION_FRAME_COUNT = 21;
    private static final float WALK_STEP = 4f;
    private static final float WALK_X_ACCELERATION = 0.5f;
    private static final float WALK_Y_ACCELERATION = 0.4f;
    private static final float TARGET_RANGE = 10f;
    private static final float[][] ISLAND_CENTERS = {
            {420f, 420f}, {160f, 201f}, {563f, 108f}, {260f, 500f}, {562f, 511f}
    };

    private final FlashBattleRoutes.SuicideBomberAttack route;
    private final OriginalAudio.Cue screamCue;
    private final Actor bomber = new Actor();
    private final Actor explosion = new Actor();
    private final Actor[] actors = {bomber, explosion};
    private final OriginalAudio.Cue[] pendingAudio = new OriginalAudio.Cue[2];
    private int delayTicks;
    private int screamTicks;
    private int waypointIndex;
    private int walkFrameOffset = 1;
    private int explosionFrame;
    private float xStep;
    private float yStep;
    private int pendingAudioCount;
    private int pendingAudioIndex;
    private OriginalAudio.Cue lastAudioCue;
    private Phase phase = Phase.SCREAM;

    public FlashSuicideBomberAnimation(FlashBattleRoutes.SuicideBomberAttack route,
                                       OriginalAudio.Cue screamCue, int delayTicks) {
        if (route == null || screamCue == null || delayTicks < 0) {
            throw new IllegalArgumentException("Invalid suicide-bomber animation");
        }
        this.route = route;
        this.screamCue = screamCue;
        this.delayTicks = delayTicks;
        bomber.linkage = "suicide_bomber";
        bomber.sourceX = route.spawn.x;
        bomber.sourceY = route.spawn.y;
        bomber.islandPlayerId = route.attackerPlayerId;
        explosion.linkage = "explosion001";
        explosion.sourceX = route.waypoints[route.waypoints.length - 1].x;
        explosion.sourceY = route.waypoints[route.waypoints.length - 1].y;
        explosion.islandPlayerId = route.targetPlayerId;
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
            case SCREAM:
                if (screamTicks == 0) {
                    enqueueAudio(screamCue);
                }
                screamTicks++;
                if (screamTicks >= SCREAM_TICKS) {
                    bomber.visible = true;
                    bomber.alpha = 0f;
                    bomber.sourceX = route.spawn.x;
                    bomber.sourceY = route.spawn.y;
                    phase = Phase.FADE_IN;
                }
                break;
            case FADE_IN:
                bomber.alpha = Math.min(1f, bomber.alpha + 0.01f);
                if (bomber.alpha >= 1f) {
                    phase = Phase.WALKING;
                }
                break;
            case WALKING:
                tickWalking();
                break;
            case EXPLOSION:
                explosion.sourceFrame = explosionFrame++;
                if (explosionFrame >= EXPLOSION_FRAME_COUNT) {
                    explosion.visible = false;
                    phase = Phase.FINISHED;
                }
                break;
            case FINISHED:
                break;
            default:
                throw new IllegalStateException("Unknown suicide-bomber phase");
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

    private void tickWalking() {
        if (waypointIndex >= route.waypoints.length) {
            bomber.visible = false;
            explosion.visible = true;
            explosionFrame = 0;
            enqueueAudio(OriginalAudio.Cue.EXPLOSION_SMALL3);
            phase = Phase.EXPLOSION;
            return;
        }

        FlashBattleRoutes.Point target = route.waypoints[waypointIndex];
        float dx = target.x - bomber.sourceX;
        float dy = target.y - bomber.sourceY;
        float xDistance = Math.abs(dx);
        float yDistance = Math.abs(dy);
        float distance = xDistance + yDistance;
        float targetXStep = distance == 0f ? 0f
                : (float) Math.floor(WALK_STEP * xDistance / distance) * Math.signum(dx);
        float targetYStep = distance == 0f ? 0f
                : (float) Math.floor(WALK_STEP * yDistance / distance) * Math.signum(dy);
        xStep = approach(xStep, targetXStep, WALK_X_ACCELERATION);
        yStep = approach(yStep, targetYStep, WALK_Y_ACCELERATION);
        float previousX = bomber.sourceX;
        float previousY = bomber.sourceY;
        bomber.sourceX += xStep;
        bomber.sourceY += yStep;
        bomber.islandPlayerId = nearestIsland(bomber.sourceX, bomber.sourceY);

        int direction = directionFrame(bomber.sourceX - previousX,
                bomber.sourceY - previousY);
        bomber.sourceFrame = (direction - 1) * BOMBER_FRAMES_PER_DIRECTION
                + walkFrameOffset - 1;
        walkFrameOffset = walkFrameOffset < BOMBER_FRAMES_PER_DIRECTION
                ? walkFrameOffset + 1 : 1;

        if (insideRange(bomber.sourceX, bomber.sourceY, target, TARGET_RANGE)) {
            waypointIndex++;
        }
    }

    private void enqueueAudio(OriginalAudio.Cue cue) {
        if (pendingAudioCount < pendingAudio.length) {
            pendingAudio[pendingAudioCount++] = cue;
        }
    }

    private static float approach(float value, float target, float acceleration) {
        if (value < target) {
            return Math.min(target, value + acceleration);
        }
        if (value > target) {
            return Math.max(target, value - acceleration);
        }
        return value;
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
