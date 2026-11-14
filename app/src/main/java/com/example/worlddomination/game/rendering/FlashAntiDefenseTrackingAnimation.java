package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** Source-tick anti-defense turret state driven by a moving Flash aircraft or rocket. */
public final class FlashAntiDefenseTrackingAnimation {
    public enum DefenseType {
        AIR,
        ROCKET
    }

    public interface Target {
        int targetPlayerId();

        float sourceX();

        float sourceY();

        boolean isVisible();

        boolean isFinished();
    }

    /** Keeps the source's single tracked-unit lock for each defending player. */
    public static final class TargetLock {
        private static final Object LEGACY_OWNER = new Object();
        private final Object[] owners = new Object[5];

        public boolean tryLock(int defenderPlayerId) {
            return tryLock(defenderPlayerId, LEGACY_OWNER);
        }

        public boolean tryLock(int defenderPlayerId, Object owner) {
            validatePlayer(defenderPlayerId);
            if (owner == null) throw new IllegalArgumentException("Missing target owner");
            if (owners[defenderPlayerId] != null) return false;
            owners[defenderPlayerId] = owner;
            return true;
        }

        public boolean isLockedTo(int defenderPlayerId, Object owner) {
            validatePlayer(defenderPlayerId);
            return owner != null && owners[defenderPlayerId] == owner;
        }

        /** Unit.speedup_die clears the defender field without checking its owner. */
        public void clear(int defenderPlayerId) {
            validatePlayer(defenderPlayerId);
            owners[defenderPlayerId] = null;
        }
    }

    public static final int TRACK_RANGE = 400;
    public static final int FIRE_RANGE = 170;
    public static final int INITIAL_COUNTDOWN_TICKS = 50;
    public static final int ROTATION_DIRECTIONS = 18;
    private static final int ROTATION_SOUND_COOLDOWN_TICKS = 48;
    private static final int AIR_GUN_SOUND_COOLDOWN_TICKS = 15;
    /** One 1.312-second Flash rocket shot at the 24 Hz simulation rate. */
    private static final int ROCKET_GUN_SOUND_COOLDOWN_TICKS = 32;

    private final Target target;
    private final int defenderPlayerId;
    private final int actionBuildingSlot;
    private final String religionCode;
    private final DefenseType defenseType;
    private final int gunSoundCooldownDurationTicks;
    private final FlashBattleRoutes.Point buildingPosition;
    private final boolean sourceDriven;
    private final SourceAntiDefensePlayback sourcePlayback;
    private final SourceAntiDefensePlayback.UnitState sourceUnitState;
    private int countdownTicks = INITIAL_COUNTDOWN_TICKS;
    private int direction = 1;
    private int antiOffset;
    private int frameIndex;
    private int rotationSoundCooldownTicks;
    private int gunSoundCooldownTicks;
    private float alphaPercent;
    private boolean rotationCuePending;
    private boolean gunCuePending;
    private boolean finished;

    public FlashAntiDefenseTrackingAnimation(Target target, int defenderPlayerId,
                                         int actionBuildingSlot, String religionCode) {
        this(target, defenderPlayerId, actionBuildingSlot, religionCode, DefenseType.AIR);
    }

    public FlashAntiDefenseTrackingAnimation(Target target, int defenderPlayerId,
                                             int actionBuildingSlot, String religionCode,
                                             DefenseType defenseType) {
        this(target, defenderPlayerId, actionBuildingSlot, religionCode,
                defenseType, false, null, null);
    }

    /** The source clock paints through its action building; retain this target handle. */
    static FlashAntiDefenseTrackingAnimation sourceDrivenAir(
            Target target, int defenderPlayerId, int actionBuildingSlot,
            String religionCode, SourceAntiDefensePlayback playback,
            SourceAntiDefensePlayback.UnitState unitState) {
        return new FlashAntiDefenseTrackingAnimation(target, defenderPlayerId,
                actionBuildingSlot, religionCode, DefenseType.AIR, true,
                playback, unitState);
    }

    private FlashAntiDefenseTrackingAnimation(Target target, int defenderPlayerId,
                                              int actionBuildingSlot, String religionCode,
                                              DefenseType defenseType, boolean sourceDriven,
                                              SourceAntiDefensePlayback playback,
                                              SourceAntiDefensePlayback.UnitState unitState) {
        if (target == null || target.targetPlayerId() != defenderPlayerId
                || actionBuildingSlot < 1 || actionBuildingSlot > 2
                || !hasReligionFrame(religionCode) || defenseType == null
                || sourceDriven && (playback == null || unitState == null)) {
            throw new IllegalArgumentException("Invalid anti-defense tracking target");
        }
        validatePlayer(defenderPlayerId);
        this.target = target;
        this.defenderPlayerId = defenderPlayerId;
        this.actionBuildingSlot = actionBuildingSlot;
        this.religionCode = religionCode;
        this.defenseType = defenseType;
        this.sourceDriven = sourceDriven;
        sourcePlayback = playback;
        sourceUnitState = unitState;
        gunSoundCooldownDurationTicks = defenseType == DefenseType.ROCKET
                ? ROCKET_GUN_SOUND_COOLDOWN_TICKS : AIR_GUN_SOUND_COOLDOWN_TICKS;
        buildingPosition = FlashBattleRoutes.actionBuildingPosition(
                defenderPlayerId, actionBuildingSlot);
    }

    /** Advances one 24 Hz source update, preserving the original range and fade cadence. */
    public void tick() {
        if (sourceDriven) return;
        rotationCuePending = false;
        gunCuePending = false;
        rotationSoundCooldownTicks = Math.max(0, rotationSoundCooldownTicks - 1);
        gunSoundCooldownTicks = Math.max(0, gunSoundCooldownTicks - 1);
        if (finished) {
            return;
        }
        if (target.isFinished()) {
            finished = true;
            return;
        }
        if (!target.isVisible()) {
            return;
        }

        float distance = distanceToTarget();
        if (distance >= TRACK_RANGE) {
            return;
        }

        int nextDirection = sourceDirection(buildingPosition.x, buildingPosition.y,
                target.sourceX(), target.sourceY());
        if (direction != nextDirection && rotationSoundCooldownTicks == 0) {
            rotationCuePending = true;
            rotationSoundCooldownTicks = ROTATION_SOUND_COOLDOWN_TICKS;
        }
        direction = nextDirection;

        if (distance < FIRE_RANGE) {
            antiOffset = antiOffset == 1 ? 2 : 1;
        }
        boolean firing = antiOffset == 1;
        if (firing && gunSoundCooldownTicks == 0) {
            gunCuePending = true;
            gunSoundCooldownTicks = gunSoundCooldownDurationTicks;
        }
        frameIndex = (direction - 1) * 2 + (firing ? 1 : 0);

        if (countdownTicks <= 0) {
            alphaPercent = Math.min(100f, alphaPercent + 10f);
        } else {
            countdownTicks--;
        }
    }

    /** Matches Unit.anti_direction's rounded 18-way angle and source y-axis convention. */
    public static int sourceDirection(float buildingX, float buildingY,
                                      float targetX, float targetY) {
        float dx = targetX - buildingX;
        float invertedY = buildingY - targetY;
        float roundedAngle = Math.round((float) Math.toDegrees(
                Math.atan2(invertedY, dx))) + 181f;
        int direction = Math.round(roundedAngle / 360f * ROTATION_DIRECTIONS);
        return Math.max(1, Math.min(ROTATION_DIRECTIONS, direction));
    }

    public static int religionFrameIndex(String religionCode) {
        if ("mus".equals(religionCode)) {
            return 0;
        }
        if ("jew".equals(religionCode)) {
            return 1;
        }
        if ("com".equals(religionCode)) {
            return 2;
        }
        if ("chr".equals(religionCode)) {
            return 3;
        }
        if ("bhu".equals(religionCode)) {
            return 4;
        }
        throw new IllegalArgumentException("Unknown original religion: " + religionCode);
    }

    private float distanceToTarget() {
        return (float) Math.hypot(target.sourceX() - buildingPosition.x,
                target.sourceY() - buildingPosition.y);
    }

    private static boolean hasReligionFrame(String religionCode) {
        return "mus".equals(religionCode) || "jew".equals(religionCode)
                || "com".equals(religionCode) || "chr".equals(religionCode)
                || "bhu".equals(religionCode);
    }

    private static void validatePlayer(int playerId) {
        if (playerId < 0 || playerId >= 5) {
            throw new IllegalArgumentException("Invalid anti-defense player id");
        }
    }

    public int getDefenderPlayerId() {
        return defenderPlayerId;
    }

    public int getActionBuildingSlot() {
        return actionBuildingSlot;
    }

    public String getReligionCode() {
        return religionCode;
    }

    public DefenseType getDefenseType() {
        return defenseType;
    }

    /** Anti-rocket uses its own source shot; both defenses share the air-turret rotation cue. */
    public OriginalAudio.Cue getGunCue() {
        return defenseType == DefenseType.ROCKET
                ? OriginalAudio.Cue.ANTI_ROCKET_FIRE : OriginalAudio.Cue.ANTI_AIR_GUN;
    }

    public int getReligionFrameIndex() {
        return religionFrameIndex(religionCode);
    }

    public FlashBattleRoutes.Point getBuildingPosition() {
        return buildingPosition;
    }

    public int getCountdownTicks() {
        return sourceDriven ? sourceUnitState.getCountdown() : countdownTicks;
    }

    public int getDirection() {
        if (!sourceDriven) return direction;
        int degrees = sourceUnitState.getRawBearingDegrees();
        return (int) Math.round(((degrees / 360d * 100d) / 100d) * ROTATION_DIRECTIONS);
    }

    /** Zero-based child timeline frame; source alternates between the direction's two frames. */
    public int getFrameIndex() {
        return sourceDriven ? sourcePlayback.getTurretFrame() - 1 : frameIndex;
    }

    public float getAlpha() {
        return sourceDriven ? (float) (sourcePlayback.getTurretAlphaPercent() / 100d)
                : alphaPercent / 100f;
    }

    public boolean isVisible() {
        return sourceDriven ? sourcePlayback.getTurretAlphaPercent() > 0d
                : !finished && target.isVisible() && alphaPercent > 0f;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isSourceDriven() { return sourceDriven; }

    public boolean takeRotationCue() {
        boolean pending = rotationCuePending;
        rotationCuePending = false;
        return pending;
    }

    public boolean takeGunCue() {
        boolean pending = gunCuePending;
        gunCuePending = false;
        return pending;
    }
}
