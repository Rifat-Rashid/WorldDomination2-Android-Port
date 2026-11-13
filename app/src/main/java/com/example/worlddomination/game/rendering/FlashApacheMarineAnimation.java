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
        STOPPED,
        FINISHED
    }

    public static final class Actor implements SourceStoppedClipQueue.Clip {
        public String linkage;
        public int sourceFrame;
        public float sourceX;
        public float sourceY;
        public float scale = 1f;
        public float alpha = 1f;
        public int islandPlayerId;
        public boolean visible;

        @Override public void detach() { visible = false; }
    }

    private static final int MARINE_COUNT = 3;
    private static final int LIFTOFF_SETUP_TICKS = 2;
    private static final int LIFTOFF_TICKS = 25;
    // Unit.heli_unload tests unloadTimer >= 10 before incrementing it.
    private static final int UNLOAD_TICKS = 11;
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
    private boolean apacheQueued;
    private boolean waitingFramePresented;
    private final boolean[] marineQueued = new boolean[MARINE_COUNT];
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
        apache.scale = 0.5f;
        // The attached source MC starts at the container origin. View moves it
        // when the action building supplies heliStart on the next host tick.
        apache.visible = true;
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
        tickSourceFrame();
    }

    /** True when a removed marine ends View.f_37 before later wrappers. */
    public boolean tickSourceFrame() {
        if (isFinished()) {
            return false;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return false;
        }

        tickApache();
        for (MarineTrack marine : marines) {
            if (marine.started && !marine.finished) {
                if (marine.tick()) pendingAudioCues++;
                if (marine.removedThisTick) return true;
            }
        }
        return false;
    }

    /** View.waitForHeliStart runs during action labels, before record activation. */
    public void presentWaitingFrame() {
        if (waitingFramePresented) return;
        waitingFramePresented = true;
        Actor apache = actors[0];
        apache.sourceX = route.apacheStart.x;
        apache.sourceY = route.apacheStart.y;
        apache.scale = 0.5f;
        apache.sourceFrame = 6; // Source frame 7 at heliStart.
        apachePhase = ApachePhase.LIFTOFF;
        liftOffTicks = 0;
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

    /** Queues stable wrapper clips in physical View visitation order, once. */
    public void registerStoppedClips(SourceStoppedClipQueue queue) {
        if (apachePhase == ApachePhase.FINISHED && !apacheQueued) {
            queue.add(actors[0]);
            apacheQueued = true;
        }
        for (int i = 0; i < MARINE_COUNT; i++) {
            if (marines[i].finished && !marineQueued[i]) {
                queue.add(actors[i + 1]);
                marineQueued[i] = true;
            }
        }
    }

    public boolean isApacheStopped() {
        return apachePhase == ApachePhase.STOPPED || apachePhase == ApachePhase.FINISHED;
    }

    public boolean isApacheRemoved() { return apachePhase == ApachePhase.FINISHED; }

    public boolean isMarineStopped(int index) { return marines[index].stopped; }

    public boolean isMarineRemoved(int index) { return marines[index].finished; }

    private void tickApache() {
        Actor apache = actors[0];
        switch (apachePhase) {
            case HELIPAD:
                presentWaitingFrame();
                break;
            case LIFTOFF:
                if (liftOffTicks < LIFTOFF_SETUP_TICKS) {
                    // setStep caches the pre-movement direction during the
                    // first two active Apache frames. Its View frame uses
                    // that direction with the current two-frame offset.
                    apacheFixedFrame = directionFrame(apache.sourceX - apachePreviousX,
                            apache.sourceY - apachePreviousY);
                    int activeOffset = apacheFrameOffset;
                    moveApacheToward(apache, route.unloadPoint);
                    apache.sourceFrame = (apacheFixedFrame - 1) * 2
                            + activeOffset;
                    liftOffTicks++;
                    break;
                }
                if (liftOffTicks - LIFTOFF_SETUP_TICKS < LIFTOFF_TICKS) {
                    int verticalTick = liftOffTicks - LIFTOFF_SETUP_TICKS;
                    apachePreviousX = apache.sourceX;
                    apachePreviousY = apache.sourceY;
                    apacheYDistance = Math.abs(route.unloadPoint.y - apache.sourceY);
                    apacheXStep = 0f;
                    apacheYStep = verticalTick < LIFTOFF_TICKS - 8 ? -3f : -1f;
                    apache.sourceY += apacheYStep;
                    apache.scale = Math.min(1f, apache.scale + 0.01f);
                    apache.sourceFrame = (apacheFixedFrame - 1) * 2
                            + apacheFrameOffset;
                    advanceApacheFrameOffset();
                    liftOffTicks++;
                    if (liftOffTicks == LIFTOFF_SETUP_TICKS + LIFTOFF_TICKS) {
                        apachePhase = ApachePhase.FLIGHT;
                        // The final lift-off frame changes action to default;
                        // unitDirection's three-frame fixed-direction cache
                        // has already served its first frame here.
                        apachePostLiftFrameCount = 1;
                    }
                    break;
                }
                throw new IllegalStateException("Apache lift off passed its final frame");
            case FLIGHT:
                int flightOffset = apacheFrameOffset;
                if (moveApacheToward(apache, route.unloadPoint)) {
                    apachePhase = ApachePhase.UNLOAD;
                    unloadTicks = 0;
                    unloadNumber = 0;
                    apacheUnloadInitialY = apache.sourceY;
                    marines[0].start(apache.sourceX, apache.sourceY, presentationRandom);
                }
                if (apachePostLiftFrameCount < 3) {
                    apache.sourceFrame = (apacheFixedFrame - 1) * 2 + flightOffset;
                    apachePostLiftFrameCount++;
                }
                break;
            case UNLOAD:
                if (apache.sourceY < apacheUnloadInitialY + 20f) {
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
                        // Source switches to flyOff inside moveUnit and advances
                        // once on that same tick with the retained flight step.
                        continueApacheFlight(apache);
                        break;
                    }
                }
                // heli_unload omits setStep, retaining the last flight prevX/Y.
                // View selects one frame after any flyOff transition/movement.
                setApacheFrame(apache, apache.sourceX - apachePreviousX,
                        apache.sourceY - apachePreviousY, apacheFrameOffset);
                advanceApacheFrameOffset();
                break;
            case FLY_OFF:
                // Unit.flyOff increments curCountToStop twice on ordinary ticks.
                if (flyOffTicks > 0 && flyOffTicks < 250) flyOffTicks++;
                else if (flyOffTicks >= 250) apachePhase = ApachePhase.STOPPED;
                flyOffTicks++;
                continueApacheFlight(apache);
                break;
            case STOPPED:
                // View splices the wrapper, then still runs moveUnit because
                // Apache does not take the soldier/marine early return.
                continueApacheFlight(apache);
                apachePhase = ApachePhase.FINISHED;
                break;
            case FINISHED:
                break;
            default:
                throw new IllegalStateException("Unknown Apache presentation phase");
        }
    }

    private void continueApacheFlight(Actor apache) {
        apache.sourceX += apacheXStep;
        apache.sourceY += apacheYStep;
        setApacheFrame(apache, apacheXStep, apacheYStep, apacheFrameOffset);
        advanceApacheFrameOffset();
    }

    private boolean moveApacheToward(Actor apache, FlashBattleRoutes.Point target) {
        float dx = target.x - apache.sourceX;
        float dy = target.y - apache.sourceY;
        float xDistance = Math.abs(dx);
        float yDistance = Math.abs(dy);
        // Unit.setStep computes X against last tick's Y distance, then Y
        // against this tick's X distance. Arrival is tested before movement.
        float xDenominator = xDistance + apacheYDistance;
        float targetXStep = xDenominator == 0f ? 0f
                : (float) Math.floor(8f * xDistance / xDenominator) * Math.signum(dx);
        float yDenominator = xDistance + yDistance;
        float targetYStep = yDenominator == 0f ? 0f
                : (float) Math.floor(8f * yDistance / yDenominator) * Math.signum(dy);
        apacheYDistance = yDistance;
        if (dx > 0f && targetXStep > apacheXStep) {
            apacheXStep += 2f;
        } else if (dx <= 0f && targetXStep < apacheXStep) {
            apacheXStep -= 2f;
        }
        if (dy > 0f && targetYStep > apacheYStep) {
            apacheYStep += 2f;
        } else if (dy <= 0f && targetYStep < apacheYStep) {
            apacheYStep -= 2f;
        }
        boolean reached = insideRange(apache.sourceX, apache.sourceY, target, 25f);
        apachePreviousX = apache.sourceX;
        apachePreviousY = apache.sourceY;
        apache.sourceX += apacheXStep;
        apache.sourceY += apacheYStep;
        setApacheFrame(apache, apache.sourceX - apachePreviousX,
                apache.sourceY - apachePreviousY, apacheFrameOffset);
        advanceApacheFrameOffset();
        return reached;
    }

    private void advanceApacheFrameOffset() {
        apacheFrameOffset = apacheFrameOffset < 2 ? apacheFrameOffset + 1 : 1;
    }

    private float apacheXStep;
    private float apacheYStep;
    private float apacheYDistance;
    private float apachePreviousX;
    private float apachePreviousY;
    private int apacheFixedFrame;
    private int apachePostLiftFrameCount;
    private float apacheUnloadInitialY;
    private int apacheFrameOffset;

    private static void setApacheFrame(Actor apache, float dx, float dy, int phase) {
        int direction = directionFrame(dx, dy);
        apache.sourceFrame = (direction - 1) * 2 + phase;
    }

    private static boolean insideRange(double x, double y,
                                       FlashBattleRoutes.Point target, float range) {
        return x < target.x + range && x > target.x - range
                && y < target.y + range && y > target.y - range;
    }

    private static int directionFrame(double dx, double dy) {
        return directionFrame(dx, dy, false);
    }

    private static int directionFrame(double dx, double dy, boolean yDifferenceIsFloat) {
        // The source runtime keeps Python int and float arithmetic distinct.
        // A zero int difference times -1 remains +0; a zero float difference
        // becomes -0.0, selecting the opposite side of atan2's branch cut.
        double opposite = dy == 0d
                ? (yDifferenceIsFloat ? -0.0d : 0d) : -dy;
        int angle = (int) Math.floor(Math.toDegrees(Math.atan2(opposite, dx)) + 0.5d);
        angle += 181;
        // The source's /360*100/100*18 order matters at exactly 330 degrees:
        // binary double gives 16.499999999999996, which AS Math.round maps to 16.
        double frameValue = angle / 360d * 100d / 100d * 18d;
        int frame = (int) Math.floor(frameValue + 0.5d);
        return frame;
    }

    private static final class MarineTrack {
        private final FlashBattleRoutes.SoldierPath path;
        private final Actor actor;
        private int waypointIndex;
        private int frameOffset = 1;
        private int range = 10;
        private int shotCount;
        private int direction = 1;
        private double x;
        private double y;
        private double xStep;
        private double yStep;
        private double xDistance;
        private double yDistance;
        private double previousX;
        private double previousY;
        private boolean yIsFloat;
        private boolean previousYIsFloat;
        private boolean yStepIsFloat;
        private boolean shooting;
        private boolean started;
        private boolean stopped;
        private boolean finished;
        private boolean removedThisTick;
        private boolean startedThisTick;

        MarineTrack(FlashBattleRoutes.SoldierPath path, Actor actor) {
            this.path = path;
            this.actor = actor;
        }

        void start(float apacheX, float apacheY, ReferenceRandom random) {
            started = true;
            startedThisTick = true;
            actor.visible = true;
            FlashBattleRoutes.Point spawn = path.waypoints[0];
            previousX = apacheX;
            previousY = apacheY;
            previousYIsFloat = true;
            x = spawn.x + random.nextInt(21);
            frameOffset = random.nextInt(7);
            y = spawn.y + random.nextInt(16);
            // marine_landUnitsArr holds integer bases for the five canonical
            // islands. Preserve a fractional authored route when supplied.
            yIsFloat = spawn.y != Math.rint(spawn.y);
            actor.sourceX = (float) x;
            actor.sourceY = (float) y;
            range = 5 + random.nextInt(6);
        }

        boolean tick() {
            removedThisTick = false;
            if (startedThisTick) {
                startedThisTick = false;
                direction = directionFrame(x - previousX, y - previousY,
                        yIsFloat || previousYIsFloat);
                actor.sourceFrame = Math.max(0,
                        (direction - 1) * WALK_FRAMES_PER_DIRECTION + frameOffset);
                frameOffset = frameOffset < WALK_FRAMES_PER_DIRECTION
                        ? frameOffset + 1 : 1;
                return false;
            }
            if (stopped) {
                finished = true;
                removedThisTick = true;
                return false;
            }
            if (shooting) {
                if (shotCount >= MARINE_SHOT_COUNT) {
                    // Unit.shoot sets stop one tick after its final shot.
                    stopped = true;
                    return false;
                }
                actor.linkage = "soldier001_shoot";
                actor.sourceFrame = (direction - 1) * SHOOT_FRAMES_PER_DIRECTION
                        + frameOffset;
                actor.sourceFrame = Math.max(0, actor.sourceFrame);
                frameOffset = frameOffset < SHOOT_FRAMES_PER_DIRECTION
                        ? frameOffset + 1 : 1;
                shotCount++;
                return shotCount == 1;
            }
            if (waypointIndex >= path.waypoints.length) {
                stopped = true;
                return false;
            }

            FlashBattleRoutes.Point target = path.waypoints[waypointIndex];
            boolean reached = insideRange(x, y, target, range);
            moveToward(target);
            direction = directionFrame(x - previousX, y - previousY,
                    yIsFloat || previousYIsFloat);
            actor.linkage = "marine";
            actor.sourceFrame = Math.max(0,
                    (direction - 1) * WALK_FRAMES_PER_DIRECTION + frameOffset);
            frameOffset = frameOffset < WALK_FRAMES_PER_DIRECTION
                    ? frameOffset + 1 : 1;

            if (reached) {
                if (waypointIndex < path.waypoints.length - 2) {
                    waypointIndex++;
                } else if (waypointIndex == path.waypoints.length - 2) {
                    range = x < path.waypoints[waypointIndex + 1].x
                            ? 100 : 25;
                    waypointIndex++;
                } else {
                    shooting = true;
                    shotCount = 0;
                }
            }
            if (shooting) {
                // View.f_37 renders the moving marine once above, then runs
                // its separate action==shoot frame block on this entry tick.
                // The body is still the walking linkage until the next tick.
                actor.sourceFrame = Math.max(0,
                        (direction - 1) * WALK_FRAMES_PER_DIRECTION + frameOffset);
                frameOffset = frameOffset < WALK_FRAMES_PER_DIRECTION
                        ? frameOffset + 1 : 1;
            }
            return false;
        }

        private void moveToward(FlashBattleRoutes.Point target) {
            if (target.x > x) {
                xDistance = target.x - x;
                double denominator = xDistance + yDistance;
                double targetStep = denominator == 0d ? 0d
                        : Math.floor(4d * xDistance / denominator);
                if (targetStep > xStep) xStep += 0.5d;
            } else {
                xDistance = x - target.x;
                double denominator = xDistance + yDistance;
                double targetStep = denominator == 0d ? 0d
                        : -Math.floor(4d * xDistance / denominator);
                if (targetStep < xStep) xStep -= 0.5d;
            }
            if (target.y > y) {
                yDistance = target.y - y;
                double denominator = xDistance + yDistance;
                double targetStep = denominator == 0d ? 0d
                        : Math.floor(4d * yDistance / denominator);
                if (targetStep > yStep) {
                    yStep += 0.4d;
                    yStepIsFloat = true;
                }
            } else {
                yDistance = y - target.y;
                double denominator = xDistance + yDistance;
                double targetStep = denominator == 0d ? 0d
                        : -Math.floor(4d * yDistance / denominator);
                if (targetStep < yStep) {
                    yStep -= 0.4d;
                    yStepIsFloat = true;
                }
            }
            previousX = x;
            previousY = y;
            previousYIsFloat = yIsFloat;
            x += xStep;
            y += yStep;
            yIsFloat |= yStepIsFloat;
            actor.sourceX = (float) x;
            actor.sourceY = (float) y;
        }
    }
}
