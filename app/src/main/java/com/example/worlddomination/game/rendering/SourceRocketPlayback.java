package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

/** AVM1 Unit/View frame actions for a submarine launched rocket. */
public final class SourceRocketPlayback {
    private static final int TRAIL_SLOTS = 50;
    private static final class Trail {
        boolean created;
        boolean attached;
        double x;
        double y;
        double alphaFactor;
        double nominalSize = 8d;
        double sizePlus;
        double alphaMin;

        double alpha() { return alphaFactor * 100d; }
    }

    private final FlashBattleRoutes.RocketStrike route;
    private final ReferenceRandom random;
    private final Trail[] trails = new Trail[TRAIL_SLOTS];
    private final double launchX;
    private final double launchY;
    private final double targetX;
    private final double targetY;
    private final double centerX;
    private final double centerY;
    private final double radiusX;
    private final double radiusY;
    private final int directionMultiplier;
    private final double targetAngle;
    private double x;
    private double y;
    private double previousX;
    private double previousY;
    private double xStep;
    private double yStep;
    private double ellipseAngle;
    private double maskOffsetX;
    private double rotation;
    private double impactEffectX;
    private double impactEffectY;
    private double renderX = 50d;
    private double renderY;
    private int sourceFrame = 1;
    private int framesActive;
    private int launchTimer;
    private int createdTrails;
    private int attachedTrails;
    private int selfExplosionCount;
    private int trailDepthBase = -1;
    private boolean activeAndMoving;
    private boolean trailActive;
    private boolean bodyAttached = true;
    private boolean impact;
    private boolean selfImpact;
    private boolean rocketFollowEllipse;
    private boolean rocketDoDrop;
    private boolean launchSound;
    private boolean removed;
    private String action = "wait_for_sub_launch";

    public SourceRocketPlayback(FlashBattleRoutes.RocketStrike route,
                                ReferenceRandom sharedBattleRandom) {
        if (route == null || !route.submarineLaunch || sharedBattleRandom == null) {
            throw new IllegalArgumentException("Source submarine route and random required");
        }
        this.route = route;
        random = sharedBattleRandom;
        launchX = sourceDouble(route.launch.x);
        launchY = sourceDouble(route.launch.y);
        targetX = sourceDouble(route.target.x);
        targetY = sourceDouble(route.target.y);
        centerX = sourceDouble(route.ellipseCenterX);
        centerY = sourceDouble(route.ellipseCenterY);
        radiusX = sourceDouble(route.ellipseRadiusX);
        radiusY = sourceDouble(route.ellipseRadiusY);
        directionMultiplier = (int) route.directionMultiplier;
        targetAngle = sourceDouble(route.targetAngle);
        ellipseAngle = sourceDouble(route.startAngle);
        x = launchX;
        y = launchY;
        previousX = x;
        previousY = y + 5d;
        for (int index = 0; index < trails.length; index++) trails[index] = new Trail();
    }

    /** One eligible View.f_37 unit visit. Pass the hull's prior-frame surface state. */
    public boolean tick(boolean submarineLaunchReady) {
        impact = false;
        launchSound = false;
        if (removed) return false;
        if ("dying".equals(action)) {
            removed = true;
            return false;
        }
        activeAndMoving = true;
        switch (action) {
            case "wait_for_sub_launch":
                if (submarineLaunchReady) action = "sub_rocket_countDown_1";
                return false;
            case "sub_rocket_countDown_1":
                if (launchTimer > 34) action = "sub_rocket_launch";
                launchTimer++;
                return false;
            case "sub_rocket_launch":
                if (launchTimer > 34) {
                    action = "default";
                    launchSound = true;
                }
                break;
            case "rocket_follow_ellipse":
                action = "none";
                sourceFrame = 3;
                rocketFollowEllipse = true;
                break;
            case "rocket_explode_self":
                // killUnit("rocket_self") attaches explosion003 at pre-move
                // Unit.curX/Y, then activeAndMoving still visits its movement branch.
                impactEffectX = x;
                impactEffectY = y;
                selfImpact = true;
                selfExplosionCount++;
                action = "dying";
                impact = true;
                bodyAttached = false;
                removeTrail();
                break;
            case "rocket_explode_city_from_sub":
                impactEffectX = targetX;
                impactEffectY = targetY;
                action = "dying";
                impact = true;
                bodyAttached = false;
                removeTrail();
                break;
            default:
                break;
        }
        framesActive++;
        if (framesActive == 5) trailActive = true;
        if ("default".equals(action)) {
            ascentStep();
        } else if (rocketFollowEllipse) {
            ellipseStep();
        } else if (rocketDoDrop) {
            dropStep();
        }
        updateTrail();
        updateRotation();
        renderX = x;
        renderY = y;
        // View.f_37 checks every rocket, even without anti_active. A submarine
        // has anti_pos=(0,0), so a failed result uses origin distance here.
        if (route.intercepted && Math.hypot(x, y) < 150d) {
            action = "rocket_explode_self";
        }
        // View removes a dead wrapper before this visit's moveUnit call. Its
        // captured loop variable still receives the second self-kill at t518.
        if (selfExplosionCount == 2) removed = true;
        return impact;
    }

    private void ascentStep() {
        previousX = x;
        previousY = y;
        double xDistance = Math.abs(launchX - x);
        double yDistance = Math.abs(centerY - y);
        double distance = xDistance + yDistance;
        double xTargetStep = Math.floor(4d / 100d * (100d / distance * xDistance));
        double yTargetStep = Math.floor(4d / 100d * (100d / distance * yDistance));
        xTargetStep *= launchX > x ? 1d : -1d;
        yTargetStep *= centerY > y ? 1d : -1d;
        // Unit.setStep accelerates only in the target's signed direction.
        // A rounded target of -3 while already moving -4 does not slow it.
        if (launchX > x) {
            if (xTargetStep > xStep) xStep += 3d;
        } else if (xTargetStep < xStep) {
            xStep -= 3d;
        }
        if (centerY > y) {
            if (yTargetStep > yStep) yStep += 2d;
        } else if (yTargetStep < yStep) {
            yStep -= 2d;
        }
        // Unit.setStep shifts the authored maskMC child while frame 1 plays.
        maskOffsetX -= yStep;
        boolean atTarget = Math.abs(x - launchX) < 14d && Math.abs(y - centerY) < 14d;
        x += xStep;
        y += yStep;
        if (atTarget) action = "rocket_follow_ellipse";
    }

    private void ellipseStep() {
        previousX = x;
        previousY = y;
        double radians = ellipseAngle * (Math.PI / 180d);
        x = centerX + radiusX * Math.cos(radians);
        y = centerY + radiusY * Math.sin(radians);
        ellipseAngle += Math.round((xStep + yStep) / 1.5d) * directionMultiplier;
        ellipseAngle %= 360d;
        if ((directionMultiplier == -1
                && (ellipseAngle >= targetAngle || ellipseAngle < 50d))
                || (directionMultiplier == 1
                && (ellipseAngle <= targetAngle || ellipseAngle < -180d))) {
            // The source retains the last ellipse pose, then drops next frame.
            ellipseAngle = 0d;
            rocketFollowEllipse = false;
            rocketDoDrop = true;
        }
    }

    private void dropStep() {
        previousX = x;
        previousY = y;
        y += 10d;
        if (!impact && y >= targetY - 20d) action = "rocket_explode_city_from_sub";
    }

    private void updateRotation() {
        double degrees = Math.atan2(-(y - previousY), x - previousX) / Math.PI * 180d;
        rotation = -(Math.round(degrees) + 181d);
    }

    private void updateTrail() {
        boolean first = false;
        boolean second = false;
        boolean third = false;
        boolean fourth = false;
        for (int index = 0; index < trails.length; index++) {
            Trail trail = trails[index];
            if (trailActive && !trail.created && !fourth
                    && !"rocket_follow_ellipse".equals(action)) {
                trail.created = true;
                first = true;
                if (first && !second) second = true;
                else if (second && !third) third = true;
                else if (third && !fourth) fourth = true;
                // Two unused randRange values still advance the battle stream.
                randomInclusive(20, 45);
                randomInclusive(28, 32);
                // Unit.updateTrail runs before View copies logical curX/Y into
                // MC. smokeBase is at (27.45,-0.3) in authored rocket frame 1;
                // sub_rocket's parent has vertical scale 50%.
                double radians = Math.toRadians(rotation);
                double cosine = Math.cos(radians);
                double sine = Math.sin(radians);
                trail.x = renderX + cosine * 27.45d - sine * (-0.15d) + 4d;
                trail.y = renderY + sine * 27.45d + cosine * (-0.15d);
                trail.sizePlus = randomInclusive(5, 11) / 12d;
                trail.alphaMin = randomInclusive(3, 15) / 3d;
                trail.alphaFactor = randomInclusive(50 - index, 100 - index) / 100d;
                trail.attached = true;
                createdTrails++;
                attachedTrails++;
            }
            if (trail.attached) {
                trail.nominalSize += trail.sizePlus;
                trail.alphaFactor = (trail.alpha() - trail.alphaMin) / 100d;
                if (trail.alpha() <= 0d) {
                    trail.attached = false;
                    attachedTrails--;
                }
            }
        }
    }

    private void removeTrail() {
        trailActive = false;
        for (Trail trail : trails) trail.attached = false;
        attachedTrails = 0;
    }

    private int randomInclusive(int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static double sourceDouble(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    public String getAction() { return action; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getPreviousX() { return previousX; }
    public double getPreviousY() { return previousY; }
    public double getXStep() { return xStep; }
    public double getYStep() { return yStep; }
    public double getEllipseAngle() { return ellipseAngle; }
    public double getMaskOffsetX() { return maskOffsetX; }
    public double getRotation() { return rotation; }
    public double getRenderX() { return renderX; }
    public double getRenderY() { return renderY; }
    public int getSourceFrame() { return sourceFrame; }
    public boolean takeLaunchSound() {
        boolean pending = launchSound;
        launchSound = false;
        return pending;
    }
    public int getFramesActive() { return framesActive; }
    public int getLaunchTimer() { return launchTimer; }
    public int getCreatedTrailCount() { return createdTrails; }
    public int getAttachedTrailCount() { return attachedTrails; }
    public boolean isActiveAndMoving() { return activeAndMoving; }
    public boolean isBodyAttached() { return bodyAttached; }
    public boolean isRemoved() { return removed; }
    public boolean isDropping() {
        return rocketDoDrop;
    }
    public boolean isSelfImpact() { return selfImpact; }
    public int getSelfExplosionCount() { return selfExplosionCount; }
    public double getImpactEffectX() { return impactEffectX; }
    public double getImpactEffectY() { return impactEffectY; }
    public int getTrailSlotCount() { return trails.length; }
    /** Battle-parent allocator's next free depth immediately before first smoke. */
    public void setTrailDepthBase(int depth) {
        if (trailDepthBase >= 0 || depth < 0) {
            throw new IllegalStateException("Set source smoke depth only once");
        }
        trailDepthBase = depth;
    }
    public int getTrailDepth(int index) {
        return trailDepthBase < 0 ? -1 : trailDepthBase + index;
    }
    public boolean isTrailCreated(int index) { return trails[index].created; }
    public boolean isTrailAttached(int index) { return trails[index].attached; }
    public double getTrailX(int index) { return trails[index].x; }
    public double getTrailY(int index) { return trails[index].y; }
    public double getTrailAlpha(int index) { return trails[index].alpha(); }
    public double getTrailNominalSize(int index) { return trails[index].nominalSize; }
    public double getTrailScalePercent(int index) { return 100d; }
    public double getTrailSizePlus(int index) { return trails[index].sizePlus; }
    public double getTrailAlphaMin(int index) { return trails[index].alphaMin; }
}
