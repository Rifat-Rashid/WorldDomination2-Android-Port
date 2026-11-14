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
            int depth = display.nextDepth();
            trail.setDepth(slot, depth);
            entries[slot] = display.attach(BattlefieldDisplayState.Kind.EFFECT,
                    slot, unitName + trail.getClipNameSuffix(slot), this, depth);
        }
    }
}
