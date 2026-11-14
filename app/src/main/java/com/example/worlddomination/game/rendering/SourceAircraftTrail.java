package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import java.util.Objects;

/** Unit.updateTrail's shared-stream, single smoke branch for plane001/ship_plane. */
public final class SourceAircraftTrail {
    public enum Origin { PREVIOUS_UNIT_POSITION, SHIP_OFFSET }

    private static final int SLOT_COUNT = 50;
    private static final int START_FRAME = 5;
    private static final double SHIP_TRAIL_OFFSET = 20d;

    private static final class Slot {
        boolean created;
        boolean attached;
        double x;
        double y;
        double alphaFactor;
        double width = 8d;
        double height = 8d;
        double sizePlus;
        double alphaMin;
        int depth = -1;

        double alpha() { return alphaFactor * 100d; }
    }

    private final ReferenceRandom random;
    private final Origin origin;
    private final Slot[] slots = new Slot[SLOT_COUNT];
    private int framesActive;
    private int createdCount;
    private int attachedCount;
    private boolean trailActive;

    public SourceAircraftTrail(Origin origin, ReferenceRandom sharedBattleRandom) {
        this.origin = Objects.requireNonNull(origin, "origin");
        random = Objects.requireNonNull(sharedBattleRandom, "sharedBattleRandom");
        for (int index = 0; index < slots.length; index++) slots[index] = new Slot();
    }

    /** One Unit.moveUnit visit; the caller supplies the pose before View publishes it. */
    public void visit(boolean activeAndMoving, String action,
                      double previousX, double previousY, double currentX,
                      double currentY, double unitAngleDegrees) {
        if (!activeAndMoving) return;
        framesActive++;
        if (framesActive == START_FRAME) trailActive = true;
        if ("taxi_a".equals(action) || "taxi_b".equals(action)) return;
        int births = 0;
        for (int index = 0; index < slots.length; index++) {
            Slot slot = slots[index];
            if (trailActive && !slot.created && births < 3) {
                slot.created = true;
                births++;
                // Source computes these even for nonrocket aircraft, then
                // discards both values before choosing the clip's position.
                randomInclusive(20, 45);
                randomInclusive(28, 32);
                if (origin == Origin.SHIP_OFFSET) {
                    double radians = (unitAngleDegrees / 180d) * Math.PI * -1d;
                    slot.x = currentX + Math.cos(radians) * SHIP_TRAIL_OFFSET;
                    slot.y = currentY + 5d
                            + Math.sin(radians) * SHIP_TRAIL_OFFSET;
                } else {
                    slot.x = previousX;
                    slot.y = previousY;
                }
                slot.sizePlus = randomInclusive(5, 11) / 12d;
                slot.alphaMin = randomInclusive(3, 15) / 3d;
                slot.alphaFactor = randomInclusive(SLOT_COUNT - index,
                        100 - index) / 100d;
                slot.attached = true;
                createdCount++;
                attachedCount++;
            }
            if (slot.attached) {
                slot.height += slot.sizePlus;
                slot.width += slot.sizePlus;
                slot.alphaFactor = (slot.alpha() - slot.alphaMin) / 100d;
                if (slot.alpha() <= 0d) {
                    slot.attached = false;
                    attachedCount--;
                }
            }
        }
    }

    /** Convenience for an eligible aircraft movement with no taxi suppression. */
    public void tick(double previousX, double previousY, double currentX,
                     double currentY, double unitAngleDegrees) {
        visit(true, "default", previousX, previousY, currentX, currentY,
                unitAngleDegrees);
    }

    /** Unit.removeTrail removes attached children but retains occupied slots. */
    public void removeTrail() {
        trailActive = false;
        for (Slot slot : slots) slot.attached = false;
        attachedCount = 0;
    }

    /** The battle-root display allocator assigns depth at physical attachment. */
    public void setDepth(int slot, int depth) {
        Slot item = slots[slot];
        if (!item.created || item.depth >= 0 || depth < 0)
            throw new IllegalStateException("Assign each created smoke depth once");
        item.depth = depth;
    }

    private int randomInclusive(int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    public int getSlotCount() { return slots.length; }
    public int getFramesActive() { return framesActive; }
    public boolean isTrailActive() { return trailActive; }
    public int getCreatedCount() { return createdCount; }
    public int getAttachedCount() { return attachedCount; }
    public boolean isCreated(int slot) { return slots[slot].created; }
    public boolean isAttached(int slot) { return slots[slot].attached; }
    public int getDepth(int slot) { return slots[slot].depth; }
    public double getX(int slot) { return slots[slot].x; }
    public double getY(int slot) { return slots[slot].y; }
    public double getWidth(int slot) { return slots[slot].width; }
    public double getHeight(int slot) { return slots[slot].height; }
    public double getAlpha(int slot) { return slots[slot].alpha(); }
    public double getSizePlus(int slot) { return slots[slot].sizePlus; }
    public double getAlphaMin(int slot) { return slots[slot].alphaMin; }
}
