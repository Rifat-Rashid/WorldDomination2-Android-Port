package com.example.worlddomination.game.rendering;

import java.util.ArrayList;
import java.util.Objects;

/** Visits battle unit groups in the source traversal order, including frame breaks. */
public final class SourceBattleUnitPlayback {
    @FunctionalInterface public interface FrameAction {
        /** True when the source unit loop ends before visiting later entries. */
        boolean advance();
    }

    private final ArrayList<FrameAction> entries = new ArrayList<>(32);

    /** Register an action already arranged in source View visitation order. */
    public void add(FrameAction action) {
        entries.add(Objects.requireNonNull(action, "action"));
    }

    /** Register in Unit construction order; View visits the active array in reverse. */
    public void addConstructed(FrameAction action) {
        entries.add(0, Objects.requireNonNull(action, "action"));
    }

    public boolean advanceFrame() {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).advance()) return true;
        }
        return false;
    }

    public void reset() { entries.clear(); }

    public int size() { return entries.size(); }
}
