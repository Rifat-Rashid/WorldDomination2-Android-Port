package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;

/** Source-tick playback for the Flash soldier1 boat landing and three infantry units. */
public final class FlashSoldierOneAnimation {
    public static final class Actor {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        public int islandPlayerId;
        public boolean visible;
    }

    private static final int BOAT_ACTOR = 0;
    private static final int FIRST_SOLDIER_ACTOR = 1;
    private static final int SOLDIER_COUNT = 3;
    private static final int BOAT_UNLOAD_TICKS = 10;
    private static final int BOAT_UNLOAD_COUNT = 4;
    private static final int SOLDIER_SHOT_COUNT = 75;
    private static final int SOLDIER_WALK_FRAMES_PER_DIRECTION = 12;
    private static final int SOLDIER_SHOOT_FRAMES_PER_DIRECTION = 4;

    private final FlashBattleRoutes.SoldierOneAttack route;
    private final ReferenceRandom presentationRandom;
    private final OriginalAudio.Cue audioCue;
    private final Actor[] actors = new Actor[1 + SOLDIER_COUNT];
    private final SoldierTrack[] soldiers = new SoldierTrack[SOLDIER_COUNT];
    private int delayTicks;
    private int unloadTicks;
    private int unloadNumber;
    private boolean unloading;
    private boolean boatFinished;
    private int pendingAudioCues;

    public FlashSoldierOneAnimation(FlashBattleRoutes.SoldierOneAttack route,
                                    ReferenceRandom presentationRandom,
                                    OriginalAudio.Cue audioCue, int delayTicks) {
        if (route == null || presentationRandom == null || delayTicks < 0) {
            throw new IllegalArgumentException("Invalid soldier1 animation");
        }
        this.route = route;
        this.presentationRandom = presentationRandom;
        this.audioCue = audioCue;
        this.delayTicks = delayTicks;
        Actor boat = new Actor();
        boat.linkage = "boat001";
        actors[BOAT_ACTOR] = boat;
        boat.sourceX = route.boatStart.x;
        boat.sourceY = route.boatStart.y;
        boat.islandPlayerId = route.attackerPlayerId;
        for (int i = 0; i < SOLDIER_COUNT; i++) {
            Actor actor = new Actor();
            actor.linkage = "soldier";
            actor.islandPlayerId = route.targetPlayerId;
            actors[FIRST_SOLDIER_ACTOR + i] = actor;
            soldiers[i] = new SoldierTrack(route.soldiers[i], actor);
        }
    }

    /** Advances one original 24 Hz presentation tick. */
    public void tick() {
        if (isFinished()) {
            return;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        if (!boatFinished) {
            tickBoat();
        }
        if (unloading) {
            for (int i = 0; i < SOLDIER_COUNT; i++) {
                if (!soldiers[i].started && unloadNumber == i) {
                    soldiers[i].start(presentationRandom);
                }
            }
        }
        for (SoldierTrack soldier : soldiers) {
            if (soldier.started && !soldier.finished) {
                if (soldier.tick()) {
                    pendingAudioCues++;
                }
            }
        }
    }

    public int getActorCount() {
        return actors.length;
    }

    /** Returns a stable actor view; no object is allocated during rendering. */
    public Actor getActor(int index) {
        return actors[index];
    }

    public boolean takeAudioCue() {
        if (pendingAudioCues == 0) {
            return false;
        }
        pendingAudioCues--;
        return audioCue != null;
    }

    public OriginalAudio.Cue getAudioCue() {
        return audioCue;
    }

    public boolean isFinished() {
        if (!boatFinished) {
            return false;
        }
        for (SoldierTrack soldier : soldiers) {
            if (!soldier.finished) {
                return false;
            }
        }
        return true;
    }

    private void tickBoat() {
        Actor boat = actors[BOAT_ACTOR];
        if (unloading) {
            boat.visible = true;
            boat.sourceFrame = Math.min(18, unloadNumber + 1);
            unloadTicks++;
            if (unloadTicks >= BOAT_UNLOAD_TICKS) {
                unloadTicks = 0;
                unloadNumber++;
                if (unloadNumber >= BOAT_UNLOAD_COUNT) {
                    boatFinished = true;
                    boat.visible = false;
                }
            }
            return;
        }

        boat.visible = true;
        boat.islandPlayerId = route.boatIslandIds[boatWaypoint];
        if (!moveToward(boat, route.boatWaypoints, 7f, 2f, 2f, 15f, false)) {
            boat.visible = false;
            boatFinished = true;
            return;
        }
        if (boatTargetReached) {
            boatTargetReached = false;
            boatWaypoint++;
            if (boatWaypoint >= route.boatWaypoints.length) {
                unloading = true;
                unloadNumber = 0;
                unloadTicks = 0;
            }
        }
    }

    private int boatWaypoint;
    private float boatXStep;
    private float boatYStep;
    private float boatXDistance;
    private float boatYDistance;
    private boolean boatTargetReached;
    private float boatPreviousX;
    private float boatPreviousY;

    private boolean moveToward(Actor actor, FlashBattleRoutes.Point[] points,
                               float step, float xMultiplier, float yMultiplier,
                               float range, boolean instantTurn) {
        if (boatWaypoint >= points.length) {
            return false;
        }
        FlashBattleRoutes.Point target = points[boatWaypoint];
        float dx = target.x - actor.sourceX;
        if (target.x > actor.sourceX) {
            boatXDistance = target.x - actor.sourceX;
            float denominator = boatXDistance + boatYDistance;
            float targetStep = denominator == 0f ? 0f
                    : (float) Math.floor(step * boatXDistance / denominator);
            if (targetStep > boatXStep && !instantTurn) {
                boatXStep += xMultiplier;
            } else if (instantTurn) {
                boatXStep = targetStep;
            }
        } else {
            boatXDistance = actor.sourceX - target.x;
            float denominator = boatXDistance + boatYDistance;
            float targetStep = denominator == 0f ? 0f
                    : -(float) Math.floor(step * boatXDistance / denominator);
            if (targetStep < boatXStep && !instantTurn) {
                boatXStep -= xMultiplier;
            } else if (instantTurn) {
                boatXStep = targetStep;
            }
        }
        if (target.y > actor.sourceY) {
            boatYDistance = target.y - actor.sourceY;
            float denominator = boatXDistance + boatYDistance;
            float targetStep = denominator == 0f ? 0f
                    : (float) Math.floor(step * boatYDistance / denominator);
            if (targetStep > boatYStep && !instantTurn) {
                boatYStep += yMultiplier;
            } else if (instantTurn) {
                boatYStep = targetStep;
            }
        } else {
            boatYDistance = actor.sourceY - target.y;
            float denominator = boatXDistance + boatYDistance;
            float targetStep = denominator == 0f ? 0f
                    : -(float) Math.floor(step * boatYDistance / denominator);
            if (targetStep < boatYStep && !instantTurn) {
                boatYStep -= yMultiplier;
            } else if (instantTurn) {
                boatYStep = targetStep;
            }
        }
        boatPreviousX = actor.sourceX;
        boatPreviousY = actor.sourceY;
        actor.sourceX += boatXStep;
        actor.sourceY += boatYStep;
        actor.sourceFrame = directionFrame(actor.sourceX - boatPreviousX,
                actor.sourceY - boatPreviousY) - 1;
        boatTargetReached = actor.sourceX < target.x + range
                && actor.sourceX > target.x - range
                && actor.sourceY < target.y + range
                && actor.sourceY > target.y - range;
        return true;
    }

    private final class SoldierTrack {
        private final FlashBattleRoutes.SoldierPath path;
        private final Actor actor;
        private int waypointIndex;
        private int frameOffset = 1;
        private int range = 2;
        private int shotCount;
        private int direction = 1;
        private float xStep;
        private float yStep;
        private float xDistance;
        private float yDistance;
        private float previousX;
        private float previousY;
        private boolean started;
        private boolean shooting;
        private boolean finished;

        SoldierTrack(FlashBattleRoutes.SoldierPath path, Actor actor) {
            this.path = path;
            this.actor = actor;
        }

        void start(ReferenceRandom random) {
            started = true;
            actor.visible = true;
            FlashBattleRoutes.Point spawn = path.waypoints[0];
            actor.sourceX = spawn.x + random.nextInt(6);
            actor.sourceY = spawn.y + random.nextInt(6);
            frameOffset = random.nextInt(7);
            range = 5 + random.nextInt(4);
        }

        boolean tick() {
            if (shooting) {
                actor.linkage = "soldier001_shoot";
                actor.sourceFrame = (direction - 1) * SOLDIER_SHOOT_FRAMES_PER_DIRECTION
                        + (Math.max(1, frameOffset) - 1) % SOLDIER_SHOOT_FRAMES_PER_DIRECTION;
                frameOffset = frameOffset < SOLDIER_SHOOT_FRAMES_PER_DIRECTION
                        ? frameOffset + 1 : 1;
                shotCount++;
                if (shotCount >= SOLDIER_SHOT_COUNT) {
                    finished = true;
                    actor.visible = false;
                }
                return shotCount == 1;
            }
            if (waypointIndex >= path.waypoints.length) {
                finished = true;
                actor.visible = false;
                return false;
            }

            FlashBattleRoutes.Point target = path.waypoints[waypointIndex];
            moveSoldierStep(target);
            float dx = actor.sourceX - previousX;
            float dy = actor.sourceY - previousY;
            direction = directionFrame(dx, dy);
            actor.linkage = "soldier";
            int animationFrameOffset = Math.min(SOLDIER_WALK_FRAMES_PER_DIRECTION,
                    Math.max(1, frameOffset));
            actor.sourceFrame = (direction - 1) * SOLDIER_WALK_FRAMES_PER_DIRECTION
                    + animationFrameOffset - 1;
            frameOffset = frameOffset < SOLDIER_WALK_FRAMES_PER_DIRECTION
                    ? frameOffset + 1 : 1;

            if (insideRange(target, range)) {
                if (waypointIndex < path.waypoints.length - 2) {
                    waypointIndex++;
                } else if (waypointIndex == path.waypoints.length - 2) {
                    range = actor.sourceX < path.waypoints[waypointIndex + 1].x ? 100 : 25;
                    waypointIndex++;
                } else {
                    shooting = true;
                    actor.linkage = "soldier001_shoot";
                    actor.sourceFrame = 4;
                    frameOffset = 1;
                    shotCount = 0;
                }
            }
            return false;
        }

        private void moveSoldierStep(FlashBattleRoutes.Point target) {
            float dx = target.x - actor.sourceX;
            if (target.x > actor.sourceX) {
                xDistance = target.x - actor.sourceX;
                float denominator = xDistance + yDistance;
                xStep = denominator == 0f ? 0f
                        : (float) Math.floor(4f * xDistance / denominator);
            } else {
                xDistance = actor.sourceX - target.x;
                float denominator = xDistance + yDistance;
                xStep = denominator == 0f ? 0f
                        : -(float) Math.floor(4f * xDistance / denominator);
            }
            if (target.y > actor.sourceY) {
                yDistance = target.y - actor.sourceY;
                float denominator = xDistance + yDistance;
                yStep = denominator == 0f ? 0f
                        : (float) Math.floor(4f * yDistance / denominator);
            } else {
                yDistance = actor.sourceY - target.y;
                float denominator = xDistance + yDistance;
                yStep = denominator == 0f ? 0f
                        : -(float) Math.floor(4f * yDistance / denominator);
            }
            previousX = actor.sourceX;
            previousY = actor.sourceY;
            actor.sourceX += xStep;
            actor.sourceY += yStep;
        }

        private boolean insideRange(FlashBattleRoutes.Point target, int targetRange) {
            return actor.sourceX < target.x + targetRange
                    && actor.sourceX > target.x - targetRange
                    && actor.sourceY < target.y + targetRange
                    && actor.sourceY > target.y - targetRange;
        }
    }

    private static int directionFrame(float dx, float dy) {
        int angle = Math.round((float) Math.toDegrees(Math.atan2(-dy, dx)));
        angle += 181;
        int frame = Math.round(angle / 360f * 18f);
        return Math.max(1, Math.min(18, frame));
    }
}
