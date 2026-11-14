package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;

/** Source-tick playback for the Flash soldier1 boat landing and three infantry units. */
public final class FlashSoldierOneAnimation {
    public static final class Actor implements SourceStoppedClipQueue.Clip {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        public int islandPlayerId;
        public boolean visible;
        private boolean queuedForDeletion;
        private SourceStoppedClipQueue.Clip boundClip;

        void bindClip(SourceStoppedClipQueue.Clip clip) { boundClip = clip; }

        @Override public void detach() {
            visible = false;
            if (boundClip != null) boundClip.detach();
        }
    }

    /** Called after each source-visited boat or started soldier wrapper. */
    public interface ActorFrameListener {
        void onActorFrame(int nativeActorIndex, double sourceY, boolean bodyChanged);
    }

    private static final int BOAT_ACTOR = 0;
    private static final int FIRST_SOLDIER_ACTOR = 1;
    private static final int SOLDIER_COUNT = 3;
    private static final int BOAT_UNLOAD_TICKS = 10;
    private static final int BOAT_UNLOAD_COUNT = 4;
    private static final int SOLDIER_SHOT_COUNT = 75;
    private static final int SOLDIER_WALK_FRAMES_PER_DIRECTION = 12;
    private static final int SOLDIER_SHOOT_FRAMES_PER_DIRECTION = 4;
    private static final int SOLDIER_SHOOT_FRAME_COUNT = 73;

    private final FlashBattleRoutes.SoldierOneAttack route;
    private final ReferenceRandom presentationRandom;
    private final OriginalAudio.Cue audioCue;
    private final FlashColorTransform tint;
    private final Actor[] actors = new Actor[1 + SOLDIER_COUNT];
    private final SoldierTrack[] soldiers = new SoldierTrack[SOLDIER_COUNT];
    private int delayTicks;
    private int unloadTicks;
    private int unloadNumber;
    private boolean unloading;
    private boolean boatStarted;
    private boolean boatStopped;
    private int boatStoppedFrame;
    private boolean boatFinished;
    private int pendingAudioCues;
    private ActorFrameListener actorFrameListener;

    public void setActorFrameListener(ActorFrameListener listener) {
        actorFrameListener = listener;
    }

    public FlashSoldierOneAnimation(FlashBattleRoutes.SoldierOneAttack route,
                                    ReferenceRandom presentationRandom,
                                    OriginalAudio.Cue audioCue, int delayTicks) {
        this(route, presentationRandom, audioCue, delayTicks, 1);
    }

    public FlashSoldierOneAnimation(FlashBattleRoutes.SoldierOneAttack route,
                                    ReferenceRandom presentationRandom,
                                    OriginalAudio.Cue audioCue, int delayTicks,
                                    int playerColor) {
        if (route == null || presentationRandom == null || delayTicks < 0) {
            throw new IllegalArgumentException("Invalid soldier1 animation");
        }
        this.route = route;
        this.presentationRandom = presentationRandom;
        this.audioCue = audioCue;
        this.tint = FlashColorTransform.forPlayerColor(playerColor);
        this.delayTicks = delayTicks;
        Actor boat = new Actor();
        boat.linkage = "boat001";
        actors[BOAT_ACTOR] = boat;
        // Source Unit.curX/Y starts at the route point, but the attached MC
        // remains at the battle-parent origin until its first View.f_37 visit.
        boat.sourceX = 0f;
        boat.sourceY = 0f;
        boat.islandPlayerId = route.attackerPlayerId;
        boat.visible = true;
        for (int i = 0; i < SOLDIER_COUNT; i++) {
            Actor actor = new Actor();
            actor.linkage = "soldier001";
            actor.islandPlayerId = route.targetPlayerId;
            actors[FIRST_SOLDIER_ACTOR + i] = actor;
            soldiers[i] = new SoldierTrack(route.soldiers[i], actor);
        }
    }

    /** Advances one original 24 Hz presentation tick. */
    public void tick() {
        tickSourceFrame();
    }

    /** Returns true when Flash's stopped-infantry removal ends the source ticker frame. */
    public boolean tickSourceFrame() {
        if (isFinished()) {
            return false;
        }
        if (delayTicks > 0) {
            presentWaitingFrame();
            delayTicks--;
            return false;
        }
        if (!boatStarted) setWaitingBoatPose();
        boatStarted = true;
        // Flash for-in visits the active array in reverse: boat, then
        // tail32, tail31, tail30 (our stable actor1..3 order).
        if (!boatFinished) {
            String previousLinkage = actors[BOAT_ACTOR].linkage;
            tickBoat();
            notifyActorFrame(BOAT_ACTOR, actors[BOAT_ACTOR].sourceY,
                    !previousLinkage.equals(actors[BOAT_ACTOR].linkage));
        }
        for (int i = 0; i < SOLDIER_COUNT; i++) {
            SoldierTrack soldier = soldiers[i];
            if (!soldier.started && unloading && unloadNumber == i) {
                soldier.start(presentationRandom);
                notifyActorFrame(FIRST_SOLDIER_ACTOR + i, soldier.actor.sourceY, false);
                continue; // Activation paints a pose but does not move yet.
            }
            if (soldier.started && !soldier.finished) {
                if (soldier.stopped) {
                    soldier.finished = true;
                    return true; // View returns after moving infantry to deletion.
                }
                String previousLinkage = soldier.actor.linkage;
                if (soldier.tick()) pendingAudioCues++;
                notifyActorFrame(FIRST_SOLDIER_ACTOR + i, soldier.actor.sourceY,
                        !previousLinkage.equals(soldier.actor.linkage));
            }
        }
        return false;
    }

    /** View renders the waiting boat while action labels and earlier records run. */
    public void presentWaitingFrame() {
        if (!boatStarted) {
            setWaitingBoatPose();
            notifyActorFrame(BOAT_ACTOR, route.boatStart.y, false);
        }
    }

    private void setWaitingBoatPose() {
        actors[BOAT_ACTOR].sourceX = route.boatStart.x;
        actors[BOAT_ACTOR].sourceY = route.boatStart.y;
        actors[BOAT_ACTOR].sourceFrame = directionFrame(route.boatStart.x,
                route.boatStart.y) - 1;
    }

    private void notifyActorFrame(int actorIndex, double sourceY, boolean bodyChanged) {
        if (actorFrameListener != null) {
            actorFrameListener.onActorFrame(actorIndex, sourceY, bodyChanged);
        }
    }

    /** Collect wrappers in the order View.f_37 removes them, without new clip objects. */
    public void registerStoppedClips(SourceStoppedClipQueue queue) {
        if (boatFinished) registerStoppedClip(queue, actors[BOAT_ACTOR]);
        for (SoldierTrack soldier : soldiers) {
            if (soldier.finished) registerStoppedClip(queue, soldier.actor);
        }
    }

    private static void registerStoppedClip(SourceStoppedClipQueue queue, Actor actor) {
        if (!actor.queuedForDeletion) {
            queue.add(actor);
            actor.queuedForDeletion = true;
        }
    }

    /** Presenter.allUnitsDone removes clips after the source ticker has stopped. */
    public void clearStoppedActors() {
        if (boatFinished) {
            actors[BOAT_ACTOR].detach();
        }
        for (SoldierTrack soldier : soldiers) {
            if (soldier.finished) {
                soldier.actor.detach();
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

    /** Unit.curX precedes the first View seek of the boat's attached MC. */
    public float getBoatUnitSourceX() {
        return !boatStarted ? route.boatStart.x : actors[BOAT_ACTOR].sourceX;
    }

    /** Unit.curY and the attached MC's render position differ at construction. */
    public float getBoatUnitSourceY() {
        return !boatStarted ? route.boatStart.y : actors[BOAT_ACTOR].sourceY;
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

    public FlashColorTransform getTint() {
        return tint;
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
        if (boatStopped) {
            // View requests frame 6, then its generic drawing path overwrites
            // it with the direction of the boat's last moving frame.
            boat.sourceFrame = boatStoppedFrame;
            boatFinished = true;
            return;
        }
        if (unloading) {
            boat.visible = true;
            boat.linkage = "boat001_still";
            if (unloadTicks == 0 && unloadNumber == 0) {
                boat.sourceFrame = 0;
            }
            // Unit.as:999-1008 tests the old timer before incrementing it.
            if (unloadTicks >= BOAT_UNLOAD_TICKS) {
                unloadTicks = 0;
                unloadNumber++;
                boat.sourceFrame = unloadNumber + 1;
                if (unloadNumber >= BOAT_UNLOAD_COUNT) {
                    boatStopped = true;
                    boat.sourceFrame = boatStoppedFrame;
                }
            } else {
                unloadTicks++;
            }
            return;
        }

        boat.visible = true;
        boat.islandPlayerId = route.boatIslandIds[boatWaypoint];
        int previousFrame = boat.sourceFrame;
        if (!moveToward(boat, route.boatWaypoints, 7f, 2f, 2f, 15f, false)) {
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
                boatStoppedFrame = boat.sourceFrame;
                // View skips its direction-frame update as soon as action=unload.
                boat.sourceFrame = previousFrame;
            }
        }
    }

    private int boatWaypoint;
    private float boatXStep;
    private float boatYStep;
    private double boatXDistance;
    private double boatYDistance;
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
            double targetStep = sourceTargetStep(step, boatXDistance, boatYDistance);
            if (targetStep > boatXStep && !instantTurn) {
                boatXStep += xMultiplier;
            } else if (instantTurn) {
                boatXStep = (float) targetStep;
            }
        } else {
            boatXDistance = actor.sourceX - target.x;
            double targetStep = -sourceTargetStep(step, boatXDistance, boatYDistance);
            if (targetStep < boatXStep && !instantTurn) {
                boatXStep -= xMultiplier;
            } else if (instantTurn) {
                boatXStep = (float) targetStep;
            }
        }
        if (target.y > actor.sourceY) {
            boatYDistance = target.y - actor.sourceY;
            double targetStep = sourceTargetStep(step, boatYDistance, boatXDistance);
            if (targetStep > boatYStep && !instantTurn) {
                boatYStep += yMultiplier;
            } else if (instantTurn) {
                boatYStep = (float) targetStep;
            }
        } else {
            boatYDistance = actor.sourceY - target.y;
            double targetStep = -sourceTargetStep(step, boatYDistance, boatXDistance);
            if (targetStep < boatYStep && !instantTurn) {
                boatYStep -= yMultiplier;
            } else if (instantTurn) {
                boatYStep = (float) targetStep;
            }
        }
        // Unit.setStep evaluates range before View applies xStep/yStep.
        boatTargetReached = actor.sourceX < target.x + range
                && actor.sourceX > target.x - range
                && actor.sourceY < target.y + range
                && actor.sourceY > target.y - range;
        boatPreviousX = actor.sourceX;
        boatPreviousY = actor.sourceY;
        actor.sourceX += boatXStep;
        actor.sourceY += boatYStep;
        actor.sourceFrame = directionFrame(actor.sourceX - boatPreviousX,
                actor.sourceY - boatPreviousY) - 1;
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
        private double xDistance;
        private double yDistance;
        private float previousX;
        private float previousY;
        private boolean started;
        private boolean shooting;
        private boolean invalidStep;
        private boolean stopped;
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
            previousX = actors[BOAT_ACTOR].sourceX;
            previousY = actors[BOAT_ACTOR].sourceY;
            direction = directionFrame(actor.sourceX - previousX, actor.sourceY - previousY);
            actor.sourceFrame = (direction - 1) * SOLDIER_WALK_FRAMES_PER_DIRECTION + frameOffset;
            frameOffset = frameOffset < SOLDIER_WALK_FRAMES_PER_DIRECTION ? frameOffset + 1 : 1;
        }

        boolean tick() {
            if (shooting) {
                if (shotCount < SOLDIER_SHOT_COUNT) {
                    if (shotCount == 0 && invalidStep) {
                        direction = directionFrame(actor.sourceX - previousX, actor.sourceY - previousY);
                        invalidStep = false;
                    }
                    actor.linkage = "soldier001_shoot";
                    // Unit.as:748-776 fires; View.as:1516-1528 then sets the
                    // clip frame from the carried walking frameOffset.
                    actor.sourceFrame = Math.min(SOLDIER_SHOOT_FRAME_COUNT - 1,
                            (direction - 1) * SOLDIER_SHOOT_FRAMES_PER_DIRECTION + frameOffset);
                    frameOffset = frameOffset < SOLDIER_SHOOT_FRAMES_PER_DIRECTION
                            ? frameOffset + 1 : 1;
                    shotCount++;
                    return shotCount == 1;
                }
                // The 75th shot remains visible. The next source tick stops;
                // View removes this stopped wrapper on the following tick.
                shooting = false;
                stopped = true;
                return false;
            }
            if (waypointIndex >= path.waypoints.length) {
                finished = true;
                actor.visible = false;
                return false;
            }

            FlashBattleRoutes.Point target = path.waypoints[waypointIndex];
            boolean reachedTarget = insideRange(target, range);
            moveSoldierStep(target);
            float dx = actor.sourceX - previousX;
            float dy = actor.sourceY - previousY;
            actor.linkage = "soldier001";
            if (!invalidStep) {
                direction = directionFrame(dx, dy);
                actor.sourceFrame = (direction - 1) * SOLDIER_WALK_FRAMES_PER_DIRECTION + frameOffset;
            }
            frameOffset = frameOffset < SOLDIER_WALK_FRAMES_PER_DIRECTION
                    ? frameOffset + 1 : 1;

            if (reachedTarget) {
                if (waypointIndex < path.waypoints.length - 2) {
                    waypointIndex++;
                } else if (waypointIndex == path.waypoints.length - 2) {
                    range = actor.sourceX < path.waypoints[waypointIndex + 1].x ? 100 : 25;
                    waypointIndex++;
                } else {
                    shooting = true;
                    if (!invalidStep) {
                        actor.sourceFrame = (direction - 1) * SOLDIER_WALK_FRAMES_PER_DIRECTION + frameOffset;
                    }
                    frameOffset = frameOffset < SOLDIER_WALK_FRAMES_PER_DIRECTION
                            ? frameOffset + 1 : 1;
                }
            }
            return false;
        }

        private void moveSoldierStep(FlashBattleRoutes.Point target) {
            float dx = target.x - actor.sourceX;
            if (target.x > actor.sourceX) {
                xDistance = target.x - actor.sourceX;
                xStep = (float) sourceTargetStep(4f, xDistance, yDistance);
            } else {
                xDistance = actor.sourceX - target.x;
                xStep = (float) -sourceTargetStep(4f, xDistance, yDistance);
            }
            if (target.y > actor.sourceY) {
                yDistance = target.y - actor.sourceY;
                yStep = (float) sourceTargetStep(4f, yDistance, xDistance);
            } else {
                yDistance = actor.sourceY - target.y;
                yStep = (float) -sourceTargetStep(4f, yDistance, xDistance);
            }
            previousX = actor.sourceX;
            previousY = actor.sourceY;
            invalidStep = !Float.isFinite(xStep) || !Float.isFinite(yStep);
            // The source ClipObject ignores NaN display coordinates and seeks.
            // Unit.setStep restores curX/Y from that retained display pose next tick.
            if (Float.isFinite(xStep)) actor.sourceX += xStep;
            if (Float.isFinite(yStep)) actor.sourceY += yStep;
        }

        private boolean insideRange(FlashBattleRoutes.Point target, int targetRange) {
            return actor.sourceX < target.x + targetRange
                    && actor.sourceX > target.x - targetRange
                    && actor.sourceY < target.y + targetRange
                    && actor.sourceY > target.y - targetRange;
        }
    }

    private static double sourceTargetStep(float step, double distance, double otherDistance) {
        // Preserve Unit.setStep's double arithmetic and multiplication grouping.
        // For example step4, distance97, other0 floors to3, not4.
        return Math.floor((step / 100d) * ((100d / (distance + otherDistance)) * distance));
    }

    private static int directionFrame(float dx, float dy) {
        int angle = Math.round((float) Math.toDegrees(Math.atan2(dy == 0f ? 0f : -dy, dx)));
        angle += 181;
        int frame = Math.round(angle / 360f * 18f);
        return Math.max(1, Math.min(18, frame));
    }
}
