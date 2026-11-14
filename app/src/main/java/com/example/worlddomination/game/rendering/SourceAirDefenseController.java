package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.AntiDefenseAudioPlayback;

import java.util.ArrayList;
import java.util.List;

/** Source View.updateAnti calls for one physical defender action-building turret. */
public final class SourceAirDefenseController {
    public static final class UnitVisit {
        public final FlashCarrierPlaneAnimation animation;
        public final FlashStandalonePlaneAnimation airfieldAnimation;
        public final SourceAntiDefensePlayback.UnitState state =
                new SourceAntiDefensePlayback.UnitState();
        private final AntiDefenseAudioPlayback.Owner audio;
        private double antiX;
        private double antiY;

        private UnitVisit(FlashCarrierPlaneAnimation carrier,
                          FlashStandalonePlaneAnimation airfield,
                          AntiDefenseAudioPlayback.Owner audio) {
            animation = carrier;
            airfieldAnimation = airfield;
            this.audio = audio;
        }

        public FlashAntiDefenseTrackingAnimation.Target getTarget() {
            return animation != null ? animation : airfieldAnimation;
        }

        public double getAntiX() { return antiX; }
        public double getAntiY() { return antiY; }
    }

    private final int defenderPlayerId;
    private final int actionBuildingSlot;
    private final String religionCode;
    private final FlashActionBuildingAnimation building;
    private final FlashAntiDefenseTrackingAnimation.TargetLock sharedTargetLock;
    private final FlashBattleRoutes.Point buildingPosition;
    private final SourceAntiDefensePlayback playback;
    private final int childSymbolId;
    private final List<UnitVisit> units = new ArrayList<>(3);
    private UnitVisit lockedUnit;
    private FlashAntiDefenseTrackingAnimation sourceTracker;
    private boolean trackerChanged;

    public SourceAirDefenseController(int defenderPlayerId, int actionBuildingSlot,
                                      String religionCode,
                                      FlashActionBuildingAnimation building,
                                      FlashAntiDefenseTrackingAnimation.TargetLock sharedTargetLock) {
        if (building == null || building.playerId != defenderPlayerId
                || building.slot != actionBuildingSlot || sharedTargetLock == null)
            throw new IllegalArgumentException("Missing source defense building");
        this.defenderPlayerId = defenderPlayerId;
        this.actionBuildingSlot = actionBuildingSlot;
        this.religionCode = religionCode;
        this.building = building;
        this.sharedTargetLock = sharedTargetLock;
        buildingPosition = FlashBattleRoutes.actionBuildingPosition(
                defenderPlayerId, actionBuildingSlot);
        playback = SourceAntiDefensePlayback.forReligion(religionCode);
        childSymbolId = sourceChildSymbol(religionCode);
        publishFrame();
    }

    private static int sourceChildSymbol(String religion) {
        switch (religion) {
            case "mus": return 5407;
            case "jew": return 4422;
            case "com": return 5480;
            case "chr": return 5553;
            case "bhu": return 5624;
            default: throw new IllegalArgumentException("Unknown source religion");
        }
    }

    /** Each physical carrier Unit owns its anti fields and two sound instances. */
    public UnitVisit register(FlashCarrierPlaneAnimation animation,
                              AntiDefenseAudioPlayback.Owner audio) {
        if (animation == null || animation.getSourcePlayback() == null || audio == null
                || animation.targetPlayerId() != defenderPlayerId)
            throw new IllegalArgumentException("Invalid source carrier defense Unit");
        return add(new UnitVisit(animation, null, audio));
    }

    /** Standalone plane001/002 uses the same defender turret and target lock. */
    public UnitVisit register(FlashStandalonePlaneAnimation animation,
                              AntiDefenseAudioPlayback.Owner audio) {
        if (animation == null || animation.getSourcePlayback() == null || audio == null
                || animation.targetPlayerId() != defenderPlayerId)
            throw new IllegalArgumentException("Invalid source airfield defense Unit");
        return add(new UnitVisit(null, animation, audio));
    }

    private UnitVisit add(UnitVisit unit) {
        units.add(unit);
        return unit;
    }

    /** Call once after each physical View Unit visit, including its final removed visit. */
    public void afterUnitVisit(UnitVisit unit, boolean killedThisVisit) {
        if (unit == null || !units.contains(unit))
            throw new IllegalArgumentException("Unregistered source defense Unit");
        SourceCarrierPlanePlayback carrier = unit.animation == null
                ? null : unit.animation.getSourcePlayback();
        SourceAircraftPlayback airfield = unit.airfieldAnimation == null
                ? null : unit.airfieldAnimation.getSourcePlayback();
        if (!(carrier != null ? carrier.isActiveAndMoving() : airfield.isActiveAndMoving())) return;

        // Unit.speedup_die clears the defender field before killUnit. The
        // clear is unconditional: it can release a different physical Unit.
        if (killedThisVisit && airfield != null) {
            sharedTargetLock.clear(defenderPlayerId);
            setLockedUnit(null);
        }

        // Unit.killUnit calls View.updateAnti before its final movement, even
        // if this Unit is not the defender's current target.
        if (killedThisVisit) {
            unit.state.markKilled();
            invoke(unit, carrier != null ? carrier.getPreviousX() : airfield.getDeathEffectX(),
                    carrier != null ? carrier.getPreviousY() : airfield.getDeathEffectY(), true);
        }

        // View's ship-specific block precedes the generic anti_type=air block.
        // The latter can elect a dead but still active, unassigned Unit.
        electIfFree(unit);
        if (sharedTargetLock.isLockedTo(defenderPlayerId, unit)) {
            double x = carrier != null ? carrier.getX() : airfield.getX();
            double y = carrier != null ? carrier.getY() : airfield.getY();
            boolean dead = !(carrier != null ? carrier.isBodyAttached() : airfield.isBodyAttached());
            if (carrier != null) {
                double dx = x - unit.antiX;
                double dy = y - unit.antiY;
                if (Math.sqrt(dx * dx + dy * dy) < 400d)
                    invoke(unit, x, y, dead);
            }
            // Generic air block has no range or alive/status condition.
            invoke(unit, x, y, dead);
            if (carrier != null)
                carrier.applySourceAntiDirectionAfterViewVisit(unit.antiX, unit.antiY);
            else
                airfield.applySourceAntiDirection(unit.antiX, unit.antiY);
        }
        publishFrame();
    }

    private void electIfFree(UnitVisit unit) {
        if (unit.antiX != 0d || !sharedTargetLock.tryLock(defenderPlayerId, unit)) return;
        unit.antiX = buildingPosition.x;
        unit.antiY = buildingPosition.y;
        setLockedUnit(unit);
    }

    private void setLockedUnit(UnitVisit unit) {
        if (lockedUnit == unit) return;
        lockedUnit = unit;
        sourceTracker = unit == null ? null
                : FlashAntiDefenseTrackingAnimation.sourceDrivenAir(
                        unit.getTarget(), defenderPlayerId, actionBuildingSlot,
                        religionCode, playback, unit.state);
        trackerChanged = true;
    }

    private void invoke(UnitVisit unit, double x, double y, boolean dead) {
        playback.invoke(unit.state, true, x, y, unit.antiX, unit.antiY, dead);
        if (playback.isMoveSoundRequested()) unit.audio.requestRotation();
        if (playback.isShootSoundRequested()) unit.audio.requestShot();
    }

    /** MovieClipHost autoplays once after every Unit callback on the global frame. */
    public void advanceHostFrame() {
        playback.advanceHostFrame();
        publishFrame();
    }

    private void publishFrame() {
        building.setSourceAntiFrame(childSymbolId, playback.getTurretFrame());
    }

    /** True once after a lock election or release; getTracker() may then be null. */
    public boolean takeTrackerChange() {
        boolean changed = trackerChanged;
        trackerChanged = false;
        return changed;
    }

    public FlashAntiDefenseTrackingAnimation getTracker() { return sourceTracker; }

    public SourceAntiDefensePlayback getPlayback() { return playback; }
    public FlashAntiDefenseTrackingAnimation.Target getLockedTarget() {
        return lockedUnit == null || !sharedTargetLock.isLockedTo(defenderPlayerId, lockedUnit)
                ? null : lockedUnit.getTarget();
    }
    public int getDefenderPlayerId() { return defenderPlayerId; }
    public int getActionBuildingSlot() { return actionBuildingSlot; }
    public FlashActionBuildingAnimation getBuilding() { return building; }

    public void clear() {
        if (lockedUnit != null && sharedTargetLock.isLockedTo(defenderPlayerId, lockedUnit))
            sharedTargetLock.clear(defenderPlayerId);
        units.clear();
        lockedUnit = null;
        sourceTracker = null;
        trackerChanged = false;
    }
}
