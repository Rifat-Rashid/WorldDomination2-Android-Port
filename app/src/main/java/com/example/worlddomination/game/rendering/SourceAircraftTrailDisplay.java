package com.example.worlddomination.game.rendering;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Physical battle-root children attached by one aircraft's Unit.updateTrail. */
public final class SourceAircraftTrailDisplay {
    public final SourceAircraftTrail trail;
    public final int attackerPlayerId;
    public final int targetPlayerId;
    private static final DoubleSupplier NO_CROP_BLEND = () -> 0d;
    private final DoubleSupplier cropBlend;
    private final float[] birthCropBlend = new float[50];
    @SuppressWarnings("unchecked")
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item>[] entries =
            new SourceDisplayList.Entry[50];
    private final BattlefieldDisplayState display;
    private final String unitName;

    public SourceAircraftTrailDisplay(BattlefieldDisplayState display,
                                      SourceAircraftTrail trail, int attackerPlayerId,
                                      int precedingUnitParameter) {
        this(display, trail, attackerPlayerId, precedingUnitParameter,
                attackerPlayerId, NO_CROP_BLEND);
    }

    /** Each physical puff retains the owner's projection at its birth. */
    public SourceAircraftTrailDisplay(BattlefieldDisplayState display,
                                      SourceAircraftTrail trail, int attackerPlayerId,
                                      int precedingUnitParameter, int targetPlayerId,
                                      DoubleSupplier cropBlend) {
        this.display = Objects.requireNonNull(display, "display");
        this.trail = Objects.requireNonNull(trail, "trail");
        this.attackerPlayerId = attackerPlayerId;
        this.targetPlayerId = targetPlayerId;
        this.cropBlend = Objects.requireNonNull(cropBlend, "cropBlend");
        unitName = "unit" + (precedingUnitParameter + 5) / 5;
    }

    public float getBirthCropBlend(int slot) {
        if (slot < 0 || slot >= trail.getSlotCount())
            throw new IndexOutOfBoundsException("Source smoke slot");
        return birthCropBlend[slot];
    }

    public void afterUnitFrame() {
        int groupSize = trail.isPaired() ? 2 : 1;
        for (int slot = 0; slot < trail.getSlotCount(); slot += groupSize) {
            // Paired children both attach before either can expire. Otherwise
            // the right child could wrongly reuse an immediately removed left depth.
            for (int member = slot; member < slot + groupSize; member++) attachBorn(member);
            for (int member = slot; member < slot + groupSize; member++) {
                if (!trail.isAttached(member) && entries[member] != null)
                    display.remove(entries[member]);
            }
        }
    }

    private void attachBorn(int slot) {
        if (trail.isCreated(slot) && entries[slot] == null) {
            double sampledBlend = cropBlend.getAsDouble();
            birthCropBlend[slot] = Double.isFinite(sampledBlend)
                    ? (float) Math.max(0d, Math.min(1d, sampledBlend)) : 0f;
            int depth = display.nextDepth();
            trail.setDepth(slot, depth);
            entries[slot] = display.attach(BattlefieldDisplayState.Kind.EFFECT,
                    slot, unitName + trail.getClipNameSuffix(slot), this, depth);
        }
    }
}
