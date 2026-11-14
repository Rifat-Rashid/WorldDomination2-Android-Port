package com.example.worlddomination.game.rendering;

/** Physical battle-root children attached by one aircraft's Unit.updateTrail. */
public final class SourceAircraftTrailDisplay {
    public final SourceAircraftTrail trail;
    public final int attackerPlayerId;
    @SuppressWarnings("unchecked")
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item>[] entries =
            new SourceDisplayList.Entry[50];
    private final BattlefieldDisplayState display;
    private final String unitName;

    public SourceAircraftTrailDisplay(BattlefieldDisplayState display,
                                      SourceAircraftTrail trail, int attackerPlayerId,
                                      int precedingUnitParameter) {
        this.display = display;
        this.trail = trail;
        this.attackerPlayerId = attackerPlayerId;
        unitName = "unit" + (precedingUnitParameter + 5) / 5;
    }

    public void afterUnitFrame() {
        for (int slot = 0; slot < trail.getSlotCount(); slot++) {
            if (trail.isCreated(slot) && entries[slot] == null) {
                int depth = display.nextDepth();
                trail.setDepth(slot, depth);
                entries[slot] = display.attach(BattlefieldDisplayState.Kind.EFFECT,
                        slot, unitName + "trail" + slot, this, depth);
            }
            if (!trail.isAttached(slot) && entries[slot] != null)
                display.remove(entries[slot]);
        }
    }
}
