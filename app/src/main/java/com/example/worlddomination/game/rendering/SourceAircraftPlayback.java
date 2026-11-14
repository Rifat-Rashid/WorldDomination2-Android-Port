package com.example.worlddomination.game.rendering;

/** Source Unit/View visit order shared by standalone plane001/plane002 strikes. */
public final class SourceAircraftPlayback {
    private static final double STEP = 12d;
    private static final double RANGE = 40d;

    private final FlashBattleRoutes.StandalonePlaneStrike route;
    private final SourceAircraftTrail trail;
    private final double[] waypointX = new double[3];
    private final double[] waypointY = new double[3];
    private final String[] waypointAction = {"default", "plane_circular_flight", "bomb"};

    private String action = "liftOff";
    private double x = Double.NaN;
    private double y = Double.NaN;
    private double renderX;
    private double renderY;
    private double previousX;
    private double previousY;
    private double xStep;
    private double yStep;
    private double xDistance;
    private double yDistance;
    private double xTargetStep;
    private double yTargetStep;
    private double targetX;
    private double targetY;
    private double adjacentSide;
    private double oppositeSide;
    // View's waiting direction calculation is undefined until the first move.
    // Paired smoke is born before that first move's direction publication.
    private double angle = Double.NaN;
    private double alpha;
    private double scale = 50d;
    private double scaleMatrix = .5d;
    private double shadowYOffset;
    private double circularRadius;
    private double circularAngle;
    private double circularCenterX;
    private double circularCenterY;
    private int frame = 1;
    private int tintFrame = 1;
    private int currentWaypoint;
    private int activeFrames;
    private int freeFlightCount;
    private int circularFrames;
    private int circularAirTime;
    private int circularCountdown = 6;
    private int flyOffCount;
    private boolean circularCenterSet;
    private boolean atTarget;
    private boolean started;
    private boolean removed;
    private boolean impactPending;
    private boolean deathPending;
    private boolean takeoffCuePending;
    private boolean soundStopPending;
    private int sourceSoundVolumePercent = 100;
    private int sourceSoundPanPercent;
    private boolean bodyAttached = true;
    private String unitStatus = "alive";
    private double deathEffectX = Double.NaN;
    private double deathEffectY = Double.NaN;
    private double deathSoundX = Double.NaN;

    /** The caller gates visits with the source battle record clock. */
    public SourceAircraftPlayback(FlashBattleRoutes.StandalonePlaneStrike route,
                                  SourceAircraftTrail trail) {
        if (route == null || !("plane001".equals(route.linkage)
                || "plane002".equals(route.linkage)))
            throw new IllegalArgumentException("A plane001 route is required");
        this.route = route;
        this.trail = trail;
        if (route.intercepted) waypointAction[2] = "speedup_die";
        circularRadius = route.orbitRadius;
        circularAngle = route.orbitStartAngle;
        waypointX[0] = route.liftOff.x;
        waypointY[0] = route.liftOff.y;
        waypointX[1] = route.orbitEntry.x;
        waypointY[1] = route.orbitEntry.y;
        waypointX[2] = route.bombWaypoint.x;
        waypointY[2] = route.bombWaypoint.y;
    }

    /** MovieClipHost autoplays the invisible body while View has not visited it. */
    public void presentWaitingFrame() {
        if (!started && !removed) {
            // The tint's callback copies the body before the latter autoplays.
            tintFrame = frame;
            frame = frame % 24 + 1;
        }
    }

    /** One eligible View visit, including Unit.moveUnit and subsequent display publication. */
    public void tickSourceFrame() {
        if (removed) return;
        if (!started) started = true;
        if ("stop".equals(action)) {
            removed = true;
            // View splices the wrapper, then still visits this retained Unit
            // once. Its final movement and MovieClip publication are observable.
        }
        // View.f_37 splices a dead plane before this visit's moveUnit, then
        // continues through generic display publication for one final step.
        if ("dead".equals(unitStatus)) removed = true;
        // Unit.moveUnit handles the action before movement. The first eight
        // active visits issue takeoff soundAction; the original Sound bridge
        // decides whether that restarts a channel.
        if ("bomb".equals(action)) {
            impactPending = true;
            action = "flyOff";
            if (trail != null) trail.removeTrail();
        } else if ("speedup_die".equals(action)) {
            // Unit.killUnit("rocket_self") captures the explosion at curX/Y,
            // sounds at the old MC X, detaches body/trail, and still moves
            // during this same View visit.
            deathEffectX = x;
            deathEffectY = y;
            deathSoundX = renderX;
            deathPending = true;
            bodyAttached = false;
            unitStatus = "dead";
            soundStopPending = true;
            action = "dying";
            if (trail != null) trail.removeTrail();
        } else if ("flyOff".equals(action)) {
            if (flyOffCount > 0 && flyOffCount < 250) flyOffCount++;
            else if (flyOffCount >= 250) {
                action = "stop";
                soundStopPending = true;
            }
            flyOffCount++;
            if (!"stop".equals(action) && sourceSoundVolumePercent > 0)
                sourceSoundVolumePercent -= 2;
        } else if ("plane_circular_flight".equals(action)) {
            if (circularAirTime > 70) action = "default";
            circularAirTime++;
        }
        if (!"plane_circular_flight".equals(action) && freeFlightCount < 8) {
            freeFlightCount++;
            takeoffCuePending = true;
        } else if (shadowYOffset < 90d) {
            shadowYOffset += 2d;
        }
        activeFrames++;
        if (!"plane_circular_flight".equals(action)) {
            setStep();
            previousX = x;
            previousY = y;
            x += xStep;
            y += yStep;
        }
        if (trail != null)
            trail.visit(true, action, previousX, previousY, x, y, angle);
        if (atTarget) {
            action = waypointAction[currentWaypoint++];
            atTarget = false;
        }
        if ("plane_circular_flight".equals(action)) {
            circularCountdown--;
            if (!circularCenterSet) {
                circularCenterX = x + route.orbitCenterOffset.x;
                circularCenterY = y + route.orbitCenterOffset.y;
                circularCenterSet = true;
            }
            setStep();
            circularStep();
            if (distanceToPotentialTarget() < RANGE && circularCountdown < 0)
                action = "default";
        }
        renderX = x;
        renderY = y;
        if (scale < 80d && freeFlightCount >= 8) {
            double desired = scale + 2d;
            // MovieClipHost's _xscale setter scales the current matrix, then
            // its getter measures the matrix again. The tiny accumulated
            // roundoff lets source make one last 80->82 growth step.
            scaleMatrix *= desired / scale;
            scale = Math.abs(scaleMatrix) * 100d;
        }
        if (alpha < 100d) alpha += 10d;
        if ("plane_circular_flight".equals(action)) circularFrames++;
        if (!"plane_circular_flight".equals(action) || circularFrames > 3)
            updateDirectionFrame();
        // View publishes the chosen direction to tintMC after an eligible visit.
        tintFrame = frame;
    }

    private void setStep() {
        // Unit.updatePan reads the old MovieClip pose before View publishes movement.
        sourceSoundPanPercent = (int) Math.max(-100,
                Math.min(100, Math.round((renderX / 8d - 50d) * 2d)));
        if (Double.isNaN(x)) x = renderX;
        if (Double.isNaN(y)) y = renderY;
        if (currentWaypoint >= waypointX.length) return;
        targetX = waypointX[currentWaypoint];
        targetY = waypointY[currentWaypoint];
        if (targetX > x) {
            xDistance = targetX - x;
            xTargetStep = Math.floor(STEP / 100d * (100d / (xDistance + yDistance) * xDistance));
            if (xTargetStep > xStep) xStep += route.xAcceleration;
        } else {
            xDistance = x - targetX;
            xTargetStep = -Math.floor(STEP / 100d * (100d / (xDistance + yDistance) * xDistance));
            if (xTargetStep < xStep) xStep -= route.xAcceleration;
        }
        if (targetY > y) {
            yDistance = targetY - y;
            yTargetStep = Math.floor(STEP / 100d * (100d / (xDistance + yDistance) * yDistance));
            if (yTargetStep > yStep) yStep += 1d;
        } else {
            yDistance = y - targetY;
            yTargetStep = -Math.floor(STEP / 100d * (100d / (xDistance + yDistance) * yDistance));
            if (yTargetStep < yStep) yStep -= 1d;
        }
        if (x < targetX + RANGE && x > targetX - RANGE
                && y < targetY + RANGE && y > targetY - RANGE) atTarget = true;
    }

    private void circularStep() {
        previousX = x;
        previousY = y;
        double radians = circularAngle * Math.PI / 180d;
        x = circularCenterX + circularRadius * Math.cos(radians);
        y = circularCenterY + circularRadius * Math.sin(radians);
        circularAngle %= 360d;
        circularRadius -= route.orbitRadiusStep;
        circularAngle += route.orbitAngleStep * route.orbitDirection;
    }

    private double distanceToPotentialTarget() {
        // Unit.plane_coords_at_cur_direction uses the previous View direction's
        // adjacent/opposite sides, even while this frame's circle has moved.
        targetX = waypointX[currentWaypoint];
        targetY = waypointY[currentWaypoint];
        double distance = Math.hypot(x - targetX, y - targetY);
        double direction = Math.atan2(adjacentSide, oppositeSide) + 5d;
        double potentialX = Math.cos(direction) * distance + x;
        double potentialY = Math.sin(direction) * distance + y;
        return Math.hypot(targetX - potentialX, targetY - potentialY);
    }

    private void updateDirectionFrame() {
        adjacentSide = x - previousX;
        oppositeSide = -(y - previousY);
        angle = Math.round(Math.atan2(oppositeSide, adjacentSide) / Math.PI * 180d) + 181d;
        // MovieClip.gotoAndStop(0) clamps to the first authored frame.
        frame = Math.max(1, (int) Math.round(angle / 360d * 100d / 100d * 24d));
    }

    public boolean takeImpact() { boolean pulse = impactPending; impactPending = false; return pulse; }
    public int getSourceSoundVolumePercent() { return sourceSoundVolumePercent; }
    public int getSourceSoundPanPercent() { return sourceSoundPanPercent; }
    public boolean takeSoundStop() {
        boolean pulse = soundStopPending; soundStopPending = false; return pulse;
    }
    /** One intercepted hit callback, emitted before the body's final two moves. */
    public boolean takeDeath() { boolean pulse = deathPending; deathPending = false; return pulse; }
    public boolean takeTakeoffCue() {
        boolean pulse = takeoffCuePending; takeoffCuePending = false; return pulse;
    }
    /** Flight audio starts from Sound.onSoundComplete, outside Unit/View movement. */
    public boolean takeFlightCue() { return false; }
    public SourceAircraftTrail getTrail() { return trail; }
    public String getAction() { return action; }
    public boolean isRemoved() { return removed; }
    public boolean isStarted() { return started; }
    /** Unit retains this flag after View removes its wrapper. */
    public boolean isActiveAndMoving() { return started; }
    public boolean isBodyAttached() { return bodyAttached; }
    public String getUnitStatus() { return unitStatus; }
    public double getDeathEffectX() { return deathEffectX; }
    public double getDeathEffectY() { return deathEffectY; }
    public double getDeathSoundX() { return deathSoundX; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getRenderX() { return renderX; }
    public double getRenderY() { return renderY; }
    public double getPreviousX() { return previousX; }
    public double getPreviousY() { return previousY; }
    public double getAlpha() { return alpha; }
    public double getScalePercent() { return scale; }
    public double getShadowYOffset() { return shadowYOffset; }
    public int getFrame() { return frame; }
    public int getTintFrame() { return tintFrame; }
    /** View.updateAnti mutates Unit.angle after body/tint publication, not their frames. */
    public void applySourceAntiDirection(double antiX, double antiY) {
        if (!started) return;
        angle = Math.round(Math.atan2(antiY - y, x - antiX) / Math.PI * 180d) + 181d;
    }
    public int getSourceFrame() { return frame; }
    public int getActiveFrames() { return activeFrames; }
    public int getCurrentWaypoint() { return currentWaypoint; }
    public double getXStep() { return xStep; }
    public double getYStep() { return yStep; }
}
