package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import java.util.Objects;

/** Unit.updateTrail's shared-stream smoke branches for plane001/ship_plane/plane002. */
public final class SourceAircraftTrail {
    public enum Origin { PREVIOUS_UNIT_POSITION, SHIP_OFFSET, PAIRED_PLANE }

    private static final int SLOT_COUNT = 50;
    private static final int PAIRED_COUNT = 22;
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
    private final Slot[] slots;
    private int framesActive;
    private int createdCount;
    private int attachedCount;
    private boolean trailActive;

    public SourceAircraftTrail(Origin origin, ReferenceRandom sharedBattleRandom) {
        this.origin = Objects.requireNonNull(origin, "origin");
        random = Objects.requireNonNull(sharedBattleRandom, "sharedBattleRandom");
        slots = new Slot[isPaired() ? PAIRED_COUNT * 2 : SLOT_COUNT];
        for (int index = 0; index < slots.length; index++) slots[index] = new Slot();
    }

    /** One Unit.moveUnit visit; the caller supplies the pose before View publishes it. */
    public void visit(boolean activeAndMoving, String action,
                      double previousX, double previousY, double currentX,
                      double currentY, double unitAngleDegrees) {
        if (!activeAndMoving) return;
        framesActive++;
        if (framesActive == (isPaired() ? 1 : START_FRAME)) trailActive = true;
        if ("taxi_a".equals(action) || "taxi_b".equals(action)) return;
        if (isPaired()) {
            visitPaired(currentX, currentY, unitAngleDegrees);
            return;
        }
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

    /**
     * Unit.py:1269-1303, the trailCount==2 arm. One logical pair is born per
     * visit, but both physical children attach before either is updated. The
     * draw order is left/right alpha, left growth/fade, right growth/fade.
     */
    private void visitPaired(double currentX, double currentY,
                             double unitAngleDegrees) {
        boolean born = false;
        for (int pair = 0; pair < PAIRED_COUNT; pair++) {
            Slot left = slots[pair * 2];
            Slot right = slots[pair * 2 + 1];
            if (trailActive && !left.created && !born) {
                born = true;
                left.created = true;
                right.created = true;
                createdCount += 2;
                left.attached = true;
                right.attached = true;
                attachedCount += 2;

                int low = PAIRED_COUNT - pair;
                int high = 100 - pair;
                left.alphaFactor = (100d - randomInclusive(low, high)) / 100d;
                right.alphaFactor = (100d - randomInclusive(low, high)) / 100d;
                double leftRadians = (unitAngleDegrees - 90d) / 180d * Math.PI * -1d;
                double rightRadians = (unitAngleDegrees + 90d) / 180d * Math.PI * -1d;
                left.x = finiteOrZero(currentX + Math.cos(leftRadians) * 15d);
                left.y = finiteOrZero(currentY + Math.sin(leftRadians) * 15d);
                right.x = finiteOrZero(currentX + Math.cos(rightRadians) * 15d);
                right.y = finiteOrZero(currentY + Math.sin(rightRadians) * 15d);
                left.sizePlus = randomInclusive(1, 2);
                left.alphaMin = randomInclusive(1, 4);
                right.sizePlus = randomInclusive(1, 2);
                right.alphaMin = randomInclusive(1, 4);
            }

            if (left.attached) {
                // Source tests left and right independently, and updates right
                // even after left's removal detached the whole pair.
                growAndFade(left);
                boolean removePair = left.alpha() <= 0d;
                growAndFade(right);
                if (removePair || right.alpha() <= 0d) {
                    left.attached = false;
                    right.attached = false;
                    attachedCount -= 2;
                }
            }
        }
    }

    private static void growAndFade(Slot slot) {
        slot.height += slot.sizePlus;
        slot.width += slot.sizePlus;
        slot.alphaFactor = (slot.alpha() - slot.alphaMin) / 100d;
    }

    private static double finiteOrZero(double coordinate) {
        // The source MovieClip host normalizes a NaN position to zero. The
        // first plane002 visit has an undefined previous View angle.
        return Double.isFinite(coordinate) ? coordinate : 0d;
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
    public boolean isPaired() { return origin == Origin.PAIRED_PLANE; }
    public String getClipNameSuffix(int slot) {
        if (slot < 0 || slot >= slots.length) throw new IndexOutOfBoundsException();
        return isPaired() ? (slot % 2 == 0 ? "trailLeft" : "trailRight")
                + (slot / 2) : "trail" + slot;
    }
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
