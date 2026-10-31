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
        private final boolean[] lockedPlayers = new boolean[5];

        public boolean tryLock(int defenderPlayerId) {
            validatePlayer(defenderPlayerId);
            if (lockedPlayers[defenderPlayerId]) {
                return false;
            }
            lockedPlayers[defenderPlayerId] = true;
            return true;
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
        if (target == null || target.targetPlayerId() != defenderPlayerId
                || actionBuildingSlot < 1 || actionBuildingSlot > 2
                || !hasReligionFrame(religionCode) || defenseType == null) {
            throw new IllegalArgumentException("Invalid anti-defense tracking target");
        }
        validatePlayer(defenderPlayerId);
        this.target = target;
        this.defenderPlayerId = defenderPlayerId;
        this.actionBuildingSlot = actionBuildingSlot;
        this.religionCode = religionCode;
        this.defenseType = defenseType;
        gunSoundCooldownDurationTicks = defenseType == DefenseType.ROCKET
                ? ROCKET_GUN_SOUND_COOLDOWN_TICKS : AIR_GUN_SOUND_COOLDOWN_TICKS;
        buildingPosition = FlashBattleRoutes.actionBuildingPosition(
                defenderPlayerId, actionBuildingSlot);
    }

    /** Advances one 24 Hz source update, preserving the original range and fade cadence. */
    public void tick() {
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
        return countdownTicks;
    }

    public int getDirection() {
        return direction;
    }

    /** Zero-based child timeline frame; source alternates between the direction's two frames. */
    public int getFrameIndex() {
        return frameIndex;
    }

    public float getAlpha() {
        return alphaPercent / 100f;
    }

    public boolean isVisible() {
        return !finished && target.isVisible() && alphaPercent > 0f;
    }

    public boolean isFinished() {
        return finished;
    }

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
