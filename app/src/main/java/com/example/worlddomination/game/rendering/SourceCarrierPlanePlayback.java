package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import java.util.Objects;

/** One ship_plane Unit and its View.f_37 callback state. */
public final class SourceCarrierPlanePlayback {
    private boolean soundStopPending;
    private int sourceSoundVolumePercent = 100;
    private int sourceSoundPanPercent;
    private final FlashBattleRoutes.CarrierPlaneStrike route;
    private final SourceAircraftTrail trail;
    private final double[] waypointX;
    private final double[] waypointY;
    private final String[] waypointAction;
    private double x;
    private double y;
    private double previousX;
    private double previousY;
    private double renderX;
    private double renderY;
    private double xStep;
    private double yStep;
    private double xDistance;
    private double yDistance;
    private double angle;
    private double alpha;
    private double scale = 50d;
    private int sourceFrame = 1;
    private int waypointIndex;
    private int liftOffCount;
    private int stopCount;
    private boolean paintedWaitingPose;
    private boolean activeAndMoving;
    private boolean bodyAttached = true;
    private boolean removed;
    private String action = "waitForLiftOff";

    public SourceCarrierPlanePlayback(FlashBattleRoutes.CarrierPlaneStrike route,
                                      ReferenceRandom sharedBattleRandom) {
        this(route, sharedBattleRandom, 3);
    }

    /** Number of payloads in this carrier record, not the entire turn. */
    public SourceCarrierPlanePlayback(FlashBattleRoutes.CarrierPlaneStrike route,
                                      ReferenceRandom sharedBattleRandom,
                                      int payloadCount) {
        this.route = Objects.requireNonNull(route, "route");
        if (payloadCount < 1 || payloadCount > 3
                || route.planeIndex < 0 || route.planeIndex >= payloadCount) {
            throw new IllegalArgumentException("Invalid source carrier payload count/index");
        }
        trail = new SourceAircraftTrail(SourceAircraftTrail.Origin.SHIP_OFFSET,
                Objects.requireNonNull(sharedBattleRandom, "sharedBattleRandom"));
        // Battle's ASArray for-in enumerates payload keys count-1..0 while
        // its offset counter advances 0..count-1. The later Unit constructor
        // reverses the array: physical unit zero uses offset count-1.
        FlashBattleRoutes.CarrierPlaneStrike physicalRoute =
                FlashBattleRoutes.createCarrierPlaneStrike(route.attackerPlayerId,
                        route.targetPlayerId, route.targetCityIndex,
                        payloadCount - 1 - route.planeIndex, route.intercepted);
        x = sourceDouble(physicalRoute.start.x);
        y = sourceDouble(physicalRoute.start.y);
        waypointX = new double[route.waypoints.length];
        waypointY = new double[route.waypoints.length];
        waypointAction = new String[route.waypoints.length];
        for (int index = 0; index < route.waypoints.length; index++) {
            waypointX[index] = sourceDouble(route.waypoints[index].x);
            waypointY[index] = sourceDouble(route.waypoints[index].y);
            waypointAction[index] = index == route.waypoints.length - 1
                    ? route.intercepted ? "explode" : "ship_plane_bomb" : "default";
        }
    }

    /** Waiting clips first publish their authoring pose on global source frame 1. */
    public void presentWaitingFrame() {
        if (paintedWaitingPose) return;
        renderX = x;
        renderY = y;
        sourceFrame = 4;
        alpha = 100d;
        paintedWaitingPose = true;
    }

    /** One global View.f_37 visit. Activation is supplied by the record clock. */
    public boolean tickSourceFrame(boolean recordActivated) {
        if (removed) return false;
        presentWaitingFrame();
        if (!recordActivated) return false;
        // Unit.activeAndMoving becomes true on the first activated record
        // visit, including the remaining waitForLiftOff visits. View's anti
        // target selection uses this flag before the aircraft starts moving.
        activeAndMoving = true;
        if ("waitForLiftOff".equals(action)) {
            liftOffCount++;
            if (liftOffCount >= route.planeIndex * 20) action = "liftOff";
            if ("waitForLiftOff".equals(action)) sourceFrame = 4;
            else publishDirection();
            return false;
        }

        boolean impact = false;
        if ("dying".equals(action) || "stop".equals(action)) removed = true;
        if ("explode".equals(action)) {
            impact = true;
            soundStopPending = true;
            action = "dying";
            bodyAttached = false;
            trail.removeTrail();
        } else if ("ship_plane_bomb".equals(action)) {
            impact = true;
            action = "flyOff";
            trail.removeTrail();
        } else if ("flyOff".equals(action)) {
            if (stopCount > 0) {
                if (stopCount < 250) stopCount++;
                else {
                    action = "stop";
                    soundStopPending = true;
                }
            }
            stopCount++;
            if (!"stop".equals(action) && sourceSoundVolumePercent > 0)
                sourceSoundVolumePercent -= 2;
        }

        // Unit.moveUnit performs setStep even after killUnit/stop. Its x
        // target calculation uses the PREVIOUS yDist, a source operation-order
        // detail that changes the entire route after the first movement.
        boolean atTarget = setStep();
        previousX = x;
        previousY = y;
        x += xStep;
        y += yStep;
        trail.visit(true, action, previousX, previousY, x, y, angle);
        if (atTarget && waypointIndex < waypointX.length) {
            action = waypointAction[waypointIndex++];
        }
        renderX = x;
        renderY = y;
        if (scale < 70d && !"waitForLiftOff".equals(action)) scale++;
        if (alpha < 100d) alpha = Math.min(100d, alpha + 10d);
        publishDirection();
        return impact;
    }

    private boolean setStep() {
        sourceSoundPanPercent = (int) Math.max(-100,
                Math.min(100, Math.round((renderX / 8d - 50d) * 2d)));
        if (waypointIndex >= waypointX.length) return false;
        double targetX = waypointX[waypointIndex];
        double targetY = waypointY[waypointIndex];
        if (targetX > x) {
            xDistance = targetX - x;
            double target = Math.floor((10d / 100d)
                    * ((100d / (xDistance + yDistance)) * xDistance));
            if (target > xStep) xStep += 1d;
        } else {
            xDistance = x - targetX;
            double target = -Math.floor((10d / 100d)
                    * ((100d / (xDistance + yDistance)) * xDistance));
            if (target < xStep) xStep -= 1d;
        }
        if (targetY > y) {
            yDistance = targetY - y;
            double target = Math.floor((10d / 100d)
                    * ((100d / (xDistance + yDistance)) * yDistance));
            if (target > yStep) yStep += 2d;
        } else {
            yDistance = y - targetY;
            double target = -Math.floor((10d / 100d)
                    * ((100d / (xDistance + yDistance)) * yDistance));
            if (target < yStep) yStep -= 2d;
        }
        return x < targetX + 40d && x > targetX - 40d
                && y < targetY + 40d && y > targetY - 40d;
    }

    private void publishDirection() {
        double radians = Math.atan2(-(y - previousY), x - previousX);
        angle = Math.round((radians / Math.PI) * 180d) + 181d;
        // Preserve source expression order: (angle / 360 * 100) / 100 * 24.
        sourceFrame = Math.max(1,
                (int) Math.round(((angle / 360d * 100d) / 100d) * 24d));
    }

    /** View.updateAnti reads Unit.anti_direction after the Unit's own visit.
     *  The resulting bearing is retained for the following trail birth; the
     *  body frame remains the ordinary unitDirection seek for this visit.
     */
    public void applySourceAntiDirection(double antiX, double antiY) {
        if (!activeAndMoving || removed) return;
        // ship_plane has anti_type "air". View also has a ship-specific
        // updateAnti call under 400 pixels, but its generic air callback runs
        // at any distance. Both assign the same stored raw bearing.
        double radians = Math.atan2(-(y - antiY), x - antiX);
        angle = Math.round((radians / Math.PI) * 180d) + 181d;
    }

    private static double sourceDouble(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    public FlashBattleRoutes.CarrierPlaneStrike getRoute() { return route; }
    public int getSourceSoundVolumePercent() { return sourceSoundVolumePercent; }
    public int getSourceSoundPanPercent() { return sourceSoundPanPercent; }
    public boolean takeSoundStop() {
        boolean pulse = soundStopPending; soundStopPending = false; return pulse;
    }
    public SourceAircraftTrail getTrail() { return trail; }
    public String getAction() { return action; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getPreviousX() { return previousX; }
    public double getPreviousY() { return previousY; }
    public double getRenderX() { return renderX; }
    public double getRenderY() { return renderY; }
    public double getXStep() { return xStep; }
    public double getYStep() { return yStep; }
    public double getAngle() { return angle; }
    public double getAlpha() { return alpha; }
    public double getScale() { return scale; }
    public int getSourceFrame() { return sourceFrame; }
    public int getWaypointIndex() { return waypointIndex; }
    public int getLiftOffCount() { return liftOffCount; }
    public int getStopCount() { return stopCount; }
    public boolean isBodyAttached() { return bodyAttached; }
    public boolean isActiveAndMoving() { return activeAndMoving; }
    public boolean isRemoved() { return removed; }
}
