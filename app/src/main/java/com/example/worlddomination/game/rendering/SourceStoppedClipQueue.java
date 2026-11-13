package com.example.worlddomination.game.rendering;

import java.util.ArrayList;
import java.util.Objects;

/** Delayed stopped-unit clip cleanup from Presenter.allUnitsDone (source lines 188-205). */
public final class SourceStoppedClipQueue {
    public interface Clip {
        void detach();
    }

    private static final int MAX_CLIPS_PER_CLEANUP = 21;
    private final ArrayList<Clip> clips = new ArrayList<>(64);
    private boolean cleanupDone;

    public void add(Clip clip) {
        clips.add(Objects.requireNonNull(clip, "clip"));
    }

    /** Runs once per turn, newest wrapper first; source stops after processing item 21. */
    public int removeAtAllUnitsDone() {
        if (cleanupDone) {
            return 0;
        }
        cleanupDone = true;
        int removed = 0;
        for (int index = clips.size() - 1; index >= 0 && removed < MAX_CLIPS_PER_CLEANUP;
             index--) {
            clips.get(index).detach();
            removed++;
        }
        return removed;
    }

    /** Starts a new battle and drops references to the previous deletion list. */
    public void reset() {
        clips.clear();
        cleanupDone = false;
    }

    /** Source deletion-list size; entries remain queued after clip detachment. */
    public int size() {
        return clips.size();
    }
}
