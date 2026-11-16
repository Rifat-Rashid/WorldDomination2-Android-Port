package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.AntiDefenseAudioPlayback;
import java.util.ArrayList;
import java.util.List;

/** View.updateAnti callbacks for one physical ordinary-rocket defense building. */
public final class SourceRocketDefenseController {
    public static final class UnitVisit {
        public final FlashRocketAnimation animation;
        public final SourceAntiDefensePlayback.UnitState state = new SourceAntiDefensePlayback.UnitState();
        private final AntiDefenseAudioPlayback.Owner audio;
        private double antiX, antiY;
        private UnitVisit(FlashRocketAnimation animation, AntiDefenseAudioPlayback.Owner audio) {
            this.animation = animation;
            this.audio = audio;
        }
        public double getAntiX() { return antiX; }
        public double getAntiY() { return antiY; }
    }

    private final int defenderPlayerId, actionBuildingSlot, childSymbolId;
    private final String religionCode;
    private final FlashActionBuildingAnimation building;
    private final FlashAntiDefenseTrackingAnimation.TargetLock lock;
    private final FlashBattleRoutes.Point position;
    private final List<UnitVisit> units = new ArrayList<>(3);
    private SourceAntiDefensePlayback playback;
    private UnitVisit lockedUnit;
    private FlashAntiDefenseTrackingAnimation tracker;
    private boolean trackerChanged;

    public SourceRocketDefenseController(int defenderPlayerId, int actionBuildingSlot,
            String religionCode, FlashActionBuildingAnimation building,
            FlashAntiDefenseTrackingAnimation.TargetLock lock) {
        if (building == null || building.playerId != defenderPlayerId
                || building.slot != actionBuildingSlot || lock == null)
            throw new IllegalArgumentException("Missing source defense building");
        this.defenderPlayerId = defenderPlayerId;
        this.actionBuildingSlot = actionBuildingSlot;
        this.religionCode = religionCode;
        this.building = building;
        this.lock = lock;
        position = FlashBattleRoutes.actionBuildingPosition(defenderPlayerId, actionBuildingSlot);
        playback = SourceAntiDefensePlayback.forRocketReligion(religionCode);
        switch (religionCode) {
            case "mus": childSymbolId = 5107; break;
            case "jew": childSymbolId = 5072; break;
            case "com": childSymbolId = 4349; break;
            case "chr": childSymbolId = 5035; break;
            case "bhu": childSymbolId = 4998; break;
            default: throw new IllegalArgumentException("Unknown source religion");
        }
        publishFrame();
    }

    public UnitVisit register(FlashRocketAnimation animation, AntiDefenseAudioPlayback.Owner audio) {
        if (animation == null || animation.getSourcePlayback() == null
                || !animation.getSourcePlayback().isOrdinary() || audio == null
                || animation.targetPlayerId() != defenderPlayerId)
            throw new IllegalArgumentException("Invalid source rocket defense Unit");
        UnitVisit unit = new UnitVisit(animation, audio);
        units.add(unit);
        return unit;
    }

    /** Call after core movement, including its final removed visit. */
    public void afterUnitVisit(UnitVisit unit, boolean selfKillPulse, boolean anyKillPulse) {
        if (unit == null || !units.contains(unit))
            throw new IllegalArgumentException("Unregistered source defense Unit");
        SourceRocketPlayback core = unit.animation.getSourcePlayback();
        if (!core.isActiveAndMoving()) return;
        if (selfKillPulse) {
            lock.clear(defenderPlayerId);
            setLockedUnit(null);
        }
        // killUnit updates the turret at pre-movement position even without ownership.
        if (anyKillPulse) {
            unit.state.markKilled();
            invoke(unit, core.getPreviousX(), core.getPreviousY(), true, false);
        }
        if (unit.antiX == 0d && lock.tryLock(defenderPlayerId, unit)) {
            unit.antiX = position.x;
            unit.antiY = position.y;
            core.setAntiPosition(unit.antiX, unit.antiY);
            setLockedUnit(unit);
        }
        if (lock.isLockedTo(defenderPlayerId, unit)) {
            double dx = core.getX() - unit.antiX;
            double dy = core.getY() - unit.antiY;
            if (Math.sqrt(dx * dx + dy * dy) < 400d)
                invoke(unit, core.getX(), core.getY(), !core.isBodyAttached(), true);
        }
        publishFrame();
    }

    private void invoke(UnitVisit unit, double x, double y, boolean dead, boolean postMove) {
        playback.invoke(unit.state, true, x, y, unit.antiX, unit.antiY, dead);
        if (postMove) unit.animation.getSourcePlayback().setRawAngleFromAnti(
                unit.state.getRawBearingDegrees());
        if (playback.isMoveSoundRequested()) unit.audio.requestRotation();
        if (playback.isShootSoundRequested()) unit.audio.requestShot();
    }

    private void setLockedUnit(UnitVisit unit) {
        if (lockedUnit == unit) return;
        lockedUnit = unit;
        tracker = unit == null ? null : FlashAntiDefenseTrackingAnimation.sourceDrivenRocket(
                unit.animation, defenderPlayerId, actionBuildingSlot, religionCode, playback, unit.state);
        trackerChanged = true;
    }

    /** Outer still-to-fire seek recreates its child; same-frame seek retains it. */
    public void notifyFireDefense(boolean outerFrameChanged) {
        if (!outerFrameChanged) return;
        playback = SourceAntiDefensePlayback.forRocketReligion(religionCode);
        if (lockedUnit != null) {
            tracker = FlashAntiDefenseTrackingAnimation.sourceDrivenRocket(lockedUnit.animation,
                    defenderPlayerId, actionBuildingSlot, religionCode, playback, lockedUnit.state);
            trackerChanged = true;
        }
        publishFrame();
    }

    public void advanceHostFrame() { playback.advanceHostFrame(); publishFrame(); }
    private void publishFrame() { building.setSourceAntiFrame(childSymbolId, playback.getTurretFrame()); }
    public boolean takeTrackerChange() { boolean result = trackerChanged; trackerChanged = false; return result; }
    public FlashAntiDefenseTrackingAnimation getTracker() { return tracker; }
    public SourceAntiDefensePlayback getPlayback() { return playback; }
    public FlashAntiDefenseTrackingAnimation.Target getLockedTarget() {
        return lockedUnit != null && lock.isLockedTo(defenderPlayerId, lockedUnit)
                ? lockedUnit.animation : null;
    }
    public int getDefenderPlayerId() { return defenderPlayerId; }
    public int getActionBuildingSlot() { return actionBuildingSlot; }
    public FlashActionBuildingAnimation getBuilding() { return building; }
    public void clear() {
        if (lockedUnit != null && lock.isLockedTo(defenderPlayerId, lockedUnit)) lock.clear(defenderPlayerId);
        units.clear(); lockedUnit = null; tracker = null; trackerChanged = false;
    }
}
