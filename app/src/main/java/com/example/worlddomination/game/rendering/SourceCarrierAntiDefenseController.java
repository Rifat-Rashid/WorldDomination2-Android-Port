package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.AntiDefenseAudioPlayback;

import java.util.ArrayList;
import java.util.List;

/** Source View.updateAnti calls for one shared defender action-building turret. */
public final class SourceCarrierAntiDefenseController {
    public static final class UnitVisit {
        public final FlashCarrierPlaneAnimation animation;
        public final SourceAntiDefensePlayback.UnitState state =
                new SourceAntiDefensePlayback.UnitState();
        private final AntiDefenseAudioPlayback.Owner audio;
        private double antiX;
        private double antiY;

        private UnitVisit(FlashCarrierPlaneAnimation animation,
                          AntiDefenseAudioPlayback.Owner audio) {
            this.animation = animation;
            this.audio = audio;
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
    private boolean trackerPublished;

    public SourceCarrierAntiDefenseController(int defenderPlayerId, int actionBuildingSlot,
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

    /** Each physical carrier Unit owns its countdown, offset and two sounds. */
    public UnitVisit register(FlashCarrierPlaneAnimation animation,
                              AntiDefenseAudioPlayback.Owner audio) {
        if (animation == null || animation.getSourcePlayback() == null || audio == null
                || animation.targetPlayerId() != defenderPlayerId)
            throw new IllegalArgumentException("Invalid source carrier defense Unit");
        UnitVisit unit = new UnitVisit(animation, audio);
        units.add(unit);
        return unit;
    }

    /** Call once after each physical Unit visit, including its final removed visit. */
    public void afterUnitVisit(UnitVisit unit, boolean killedThisVisit) {
        SourceCarrierPlanePlayback core = unit.animation.getSourcePlayback();
        if (!core.isActiveAndMoving()) return;

        // Unit.killUnit calls updateAnti inside moveUnit, before that visit's
        // final movement and before either ship-specific or generic View block.
        if (killedThisVisit) {
            unit.state.markKilled();
            invoke(unit, core.getPreviousX(), core.getPreviousY(), true);
        }

        // Source's per-defender target lock is elected by View visitation,
        // with anti_pos[0] == 0 as this Unit's unassigned sentinel.
        if (unit.antiX == 0d && lockedUnit == null
                && sharedTargetLock.tryLock(defenderPlayerId)) {
            unit.antiX = buildingPosition.x;
            unit.antiY = buildingPosition.y;
            lockedUnit = unit;
            sourceTracker = FlashAntiDefenseTrackingAnimation.sourceDrivenCarrier(
                    unit.animation, defenderPlayerId, actionBuildingSlot, religionCode,
                    playback, unit.state);
        }

        if (lockedUnit == unit) {
            double x = core.getX();
            double y = core.getY();
            boolean dead = !core.isBodyAttached();
            double dx = x - unit.antiX;
            double dy = y - unit.antiY;
            // View's ship-specific call has a strict 400-pixel range guard.
            if (Math.sqrt(dx * dx + dy * dy) < 400d)
                invoke(unit, x, y, dead);
            // ship_plane anti_type is air: generic View block invokes again
            // at any range, even after the wrapper was spliced this visit.
            invoke(unit, x, y, dead);
            core.applySourceAntiDirectionAfterViewVisit(unit.antiX, unit.antiY);
        }
        publishFrame();
    }

    private void invoke(UnitVisit unit, double x, double y, boolean dead) {
        playback.invoke(unit.state, true, x, y, unit.antiX, unit.antiY, dead);
        if (playback.isMoveSoundRequested()) unit.audio.requestRotation();
        if (playback.isShootSoundRequested()) unit.audio.requestShot();
    }

    /** MovieClipHost autoplays once after all callbacks on the global frame. */
    public void advanceHostFrame() {
        playback.advanceHostFrame();
        publishFrame();
    }

    private void publishFrame() {
        building.setSourceAntiFrame(childSymbolId, playback.getTurretFrame());
    }

    /** Preserve the existing native target-reflection contract without overlay drawing. */
    public FlashAntiDefenseTrackingAnimation takeNewTracker() {
        if (sourceTracker == null || trackerPublished) return null;
        trackerPublished = true;
        return sourceTracker;
    }

    public SourceAntiDefensePlayback getPlayback() { return playback; }
    public FlashCarrierPlaneAnimation getLockedAnimation() {
        return lockedUnit == null ? null : lockedUnit.animation;
    }
    public int getDefenderPlayerId() { return defenderPlayerId; }
    public int getActionBuildingSlot() { return actionBuildingSlot; }
    public FlashActionBuildingAnimation getBuilding() { return building; }

    public void clear() {
        // The old isolated host keeps its stopped frame after handoff; its
        // building object is discarded by World rather than reset in place.
        units.clear();
        lockedUnit = null;
        sourceTracker = null;
        trackerPublished = false;
    }
}
