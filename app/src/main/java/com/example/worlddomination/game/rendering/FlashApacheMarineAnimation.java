package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;

/** Source-tick playback for the soldier2 Apache insertion and three marines. */
public final class FlashApacheMarineAnimation {
    private enum ApachePhase {
        HELIPAD,
        LIFTOFF,
        FLIGHT,
        UNLOAD,
        FLY_OFF,
        FINISHED
    }

    public static final class Actor {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        public float scale = 1f;
        public float alpha = 1f;
        public int islandPlayerId;
        public boolean visible;
    }

    private static final int MARINE_COUNT = 3;
    private static final int LIFTOFF_SETUP_TICKS = 2;
    private static final int LIFTOFF_TICKS = 25;
    private static final int UNLOAD_TICKS = 10;
    private static final int APACHE_UNLOAD_COUNT = 4;
    private static final int MARINE_SHOT_COUNT = 60;
    private static final int WALK_FRAMES_PER_DIRECTION = 12;
    private static final int SHOOT_FRAMES_PER_DIRECTION = 4;

    private final FlashBattleRoutes.ApacheMarineAttack route;
    private final ReferenceRandom presentationRandom;
    private final OriginalAudio.Cue audioCue;
    private final Actor[] actors = new Actor[1 + MARINE_COUNT];
    private final MarineTrack[] marines = new MarineTrack[MARINE_COUNT];
    private int delayTicks;
    private int liftOffTicks;
    private int unloadTicks;
    private int unloadNumber;
    private int flyOffTicks;
    private int pendingAudioCues;
    private ApachePhase apachePhase = ApachePhase.HELIPAD;

    public FlashApacheMarineAnimation(FlashBattleRoutes.ApacheMarineAttack route,
                                      ReferenceRandom presentationRandom,
                                      OriginalAudio.Cue audioCue, int delayTicks) {
        if (route == null || presentationRandom == null || delayTicks < 0) {
            throw new IllegalArgumentException("Invalid soldier2 animation");
        }
        this.route = route;
        this.presentationRandom = presentationRandom;
        this.audioCue = audioCue;
        this.delayTicks = delayTicks;

        Actor apache = new Actor();
        apache.linkage = "apache";
        apache.sourceX = route.apacheStart.x;
        apache.sourceY = route.apacheStart.y;
        apache.islandPlayerId = route.attackerPlayerId;
        actors[0] = apache;
        for (int i = 0; i < MARINE_COUNT; i++) {
            Actor marine = new Actor();
            marine.linkage = "marine";
            marine.islandPlayerId = route.targetPlayerId;
            actors[i + 1] = marine;
            marines[i] = new MarineTrack(route.marines[i], marine);
        }
    }

    /** Advances one Flash presentation tick. */
    public void tick() {
        if (isFinished()) {
            return;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        tickApache();
        for (MarineTrack marine : marines) {
            if (marine.started && !marine.finished && marine.tick()) {
                pendingAudioCues++;
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
        if (apachePhase != ApachePhase.FINISHED) {
            return false;
        }
        for (MarineTrack marine : marines) {
            if (!marine.finished) {
                return false;
            }
        }
        return true;
    }

    private void tickApache() {
        Actor apache = actors[0];
        switch (apachePhase) {
            case HELIPAD:
                apache.visible = true;
                apache.scale = 0.5f;
                apachePhase = ApachePhase.LIFTOFF;
                liftOffTicks = 0;
                setApacheFrame(apache, route.unloadPoint.x - apache.sourceX,
                        route.unloadPoint.y - apache.sourceY, 0);
                break;
            case LIFTOFF:
                if (liftOffTicks < LIFTOFF_SETUP_TICKS) {
                    setApacheFrame(apache, route.unloadPoint.x - apache.sourceX,
                            route.unloadPoint.y - apache.sourceY, 0);
                    liftOffTicks++;
                    break;
                }
                if (liftOffTicks - LIFTOFF_SETUP_TICKS < LIFTOFF_TICKS) {
                    int verticalTick = liftOffTicks - LIFTOFF_SETUP_TICKS;
                    apache.sourceY -= verticalTick < LIFTOFF_TICKS - 8 ? 3f : 1f;
                    apache.scale = Math.min(1f, apache.scale + 0.01f);
                    setApacheFrame(apache, route.unloadPoint.x - apache.sourceX,
                            route.unloadPoint.y - apache.sourceY, 0);
                    liftOffTicks++;
                    break;
                }
                apachePhase = ApachePhase.FLIGHT;
                break;
            case FLIGHT:
                if (moveApacheToward(apache, route.unloadPoint)) {
                    apachePhase = ApachePhase.UNLOAD;
                    unloadTicks = 0;
                    unloadNumber = 0;
                    marines[0].start(apache.sourceX, apache.sourceY, presentationRandom);
                }
                break;
            case UNLOAD:
                if (apache.sourceY < route.unloadPoint.y + 20f) {
                    apache.sourceY += 2f;
                }
                unloadTicks++;
                if (unloadTicks >= UNLOAD_TICKS) {
                    unloadTicks = 0;
                    unloadNumber++;
                    if (unloadNumber < MARINE_COUNT) {
                        marines[unloadNumber].start(apache.sourceX, apache.sourceY,
                                presentationRandom);
                    }
                    if (unloadNumber >= APACHE_UNLOAD_COUNT) {
                        apachePhase = ApachePhase.FLY_OFF;
                        flyOffTicks = 0;
                    }
                }
                break;
            case FLY_OFF:
                // Unit.flyOff retains the Apache during its source 250-tick stop countdown.
                flyOffTicks++;
                if (flyOffTicks >= 250) {
                    apache.visible = false;
                    apachePhase = ApachePhase.FINISHED;
                }
                break;
            case FINISHED:
                apache.visible = false;
                break;
            default:
                throw new IllegalStateException("Unknown Apache presentation phase");
        }
    }

    private boolean moveApacheToward(Actor apache, FlashBattleRoutes.Point target) {
        float dx = target.x - apache.sourceX;
        float dy = target.y - apache.sourceY;
        float xDistance = Math.abs(dx);
        float yDistance = Math.abs(dy);
        float totalDistance = xDistance + yDistance;
        float targetXStep = totalDistance == 0f ? 0f
                : (float) Math.floor(8f * xDistance / totalDistance) * Math.signum(dx);
        float targetYStep = totalDistance == 0f ? 0f
                : (float) Math.floor(8f * yDistance / totalDistance) * Math.signum(dy);
        if (targetXStep > apacheXStep) {
            apacheXStep += 2f;
        } else if (targetXStep < apacheXStep) {
            apacheXStep -= 2f;
        }
        if (targetYStep > apacheYStep) {
            apacheYStep += 2f;
        } else if (targetYStep < apacheYStep) {
            apacheYStep -= 2f;
        }
        float previousX = apache.sourceX;
        float previousY = apache.sourceY;
        apache.sourceX += apacheXStep;
        apache.sourceY += apacheYStep;
        setApacheFrame(apache, apache.sourceX - previousX,
                apache.sourceY - previousY, apacheFrameOffset);
        apacheFrameOffset = (apacheFrameOffset + 1) % 2;
        return insideRange(apache.sourceX, apache.sourceY, target, 25f);
    }

    private float apacheXStep;
    private float apacheYStep;
    private int apacheFrameOffset;

    private static void setApacheFrame(Actor apache, float dx, float dy, int phase) {
        int direction = directionFrame(dx, dy);
        apache.sourceFrame = (direction - 1) * 2 + phase;
    }

    private static boolean insideRange(float x, float y,
                                       FlashBattleRoutes.Point target, float range) {
        return x < target.x + range && x > target.x - range
                && y < target.y + range && y > target.y - range;
    }

    private static int directionFrame(float dx, float dy) {
        int angle = Math.round((float) Math.toDegrees(Math.atan2(-dy, dx)));
        angle += 181;
        int frame = Math.round(angle / 360f * 18f);
        return Math.max(1, Math.min(18, frame));
    }

    private static final class MarineTrack {
        private final FlashBattleRoutes.SoldierPath path;
        private final Actor actor;
        private int waypointIndex;
        private int frameOffset = 1;
        private int range = 10;
        private int shotCount;
        private int direction = 1;
        private float xStep;
        private float yStep;
        private float xDistance;
        private float yDistance;
        private float previousX;
        private float previousY;
        private boolean shooting;
        private boolean started;
        private boolean finished;

        MarineTrack(FlashBattleRoutes.SoldierPath path, Actor actor) {
            this.path = path;
            this.actor = actor;
        }

        void start(float apacheX, float apacheY, ReferenceRandom random) {
            started = true;
            actor.visible = true;
            FlashBattleRoutes.Point spawn = path.waypoints[0];
            previousX = apacheX;
            previousY = apacheY;
            actor.sourceX = spawn.x + random.nextInt(21);
            actor.sourceY = spawn.y + random.nextInt(16);
            frameOffset = random.nextInt(7);
            range = 5 + random.nextInt(6);
        }

        boolean tick() {
            if (shooting) {
                actor.linkage = "soldier001_shoot";
                actor.sourceFrame = (direction - 1) * SHOOT_FRAMES_PER_DIRECTION
                        + (Math.max(1, frameOffset) - 1) % SHOOT_FRAMES_PER_DIRECTION;
                frameOffset = frameOffset < SHOOT_FRAMES_PER_DIRECTION
                        ? frameOffset + 1 : 1;
                shotCount++;
                if (shotCount >= MARINE_SHOT_COUNT) {
                    actor.visible = false;
                    finished = true;
                }
                return shotCount == 1;
            }
            if (waypointIndex >= path.waypoints.length) {
                actor.visible = false;
                finished = true;
                return false;
            }

            FlashBattleRoutes.Point target = path.waypoints[waypointIndex];
            moveToward(target);
            direction = directionFrame(actor.sourceX - previousX,
                    actor.sourceY - previousY);
            actor.linkage = "marine";
            int animationOffset = Math.min(WALK_FRAMES_PER_DIRECTION,
                    Math.max(1, frameOffset));
            actor.sourceFrame = (direction - 1) * WALK_FRAMES_PER_DIRECTION
                    + animationOffset - 1;
            frameOffset = frameOffset < WALK_FRAMES_PER_DIRECTION
                    ? frameOffset + 1 : 1;

            if (insideRange(actor.sourceX, actor.sourceY, target, range)) {
                if (waypointIndex < path.waypoints.length - 2) {
                    waypointIndex++;
                } else if (waypointIndex == path.waypoints.length - 2) {
                    range = actor.sourceX < path.waypoints[waypointIndex + 1].x
                            ? 100 : 25;
                    waypointIndex++;
                } else {
                    shooting = true;
                    actor.linkage = "soldier001_shoot";
                    actor.sourceFrame = SHOOT_FRAMES_PER_DIRECTION;
                    frameOffset = 1;
                    shotCount = 0;
                }
            }
            return false;
        }

        private void moveToward(FlashBattleRoutes.Point target) {
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
    }
}
