package com.example.worlddomination.game.rendering;

import java.util.Arrays;

/** Private-battle clocks for View.changeOver's hidden creature and explosion effects. */
public final class SourceHiddenBattleEffects {
    public static final int ORCA_SYMBOL = 5765;
    public static final int ORCA_DEPTH = 318;
    public static final float ORCA_X = 341f, ORCA_Y = 71f;
    public static final int NESSY_SYMBOL = 5720;
    public static final int NESSY_DEPTH = 316;
    public static final float NESSY_X = 152.8f, NESSY_Y = 341f;

    public interface Listener {
        /** Attach explosion_warhead_4 as "boom" at next child depth, then apply the
         * city's current population as casualties (with shrink RNG), then zero it.
         * The address belongs exclusively to the private presentation snapshot. */
        void explodeCity(int address);
        /** View deletes the battle ticker before scheduling allUnitsDone. */
        void stopBattleTicker();
        /** Presenter.allUnitsDone: remove retained unit/explosion clips. */
        void allUnitsDone();
        /** BattlePlayback.finish: discard the isolated snapshot and all timers. */
        void endBattle();
    }

    private static final int KILL = 1, SELF = 2, CLEANUP = 3, END = 4;
    // Deadlines are milliseconds * 24: exact source Fraction timing, without rounding.
    private final long[] deadline = new long[8], period = new long[8];
    private final int[] kind = new int[8], id = new int[8];
    private int nextId, explosionHandle, cleanupHandle, cursor;
    private int[] queue = new int[0];
    private long now;
    private int orcaFrame = 1, nessyFrame = 1;
    private boolean orcaPlaying, nessyPlaying, active, killAll, timersPending;

    /** Inputs must already follow source descending active-array/city-array keys;
     * enemy enumeration stops at the human entry, rather than skipping it.
     * Only cities populated at changeOver are included. Both flags deliberately
     * share the overwritten queue and interval handle, as Presenter.as does. */
    public void begin(boolean orca, boolean nessy, boolean killAll, boolean bush,
                      int[] enemyCities, int[] humanCities) {
        if (active) throw new IllegalStateException("Hidden battle already active");
        Arrays.fill(kind, 0);
        nextId = explosionHandle = cleanupHandle = cursor = 0;
        now = 0;
        orcaFrame = nessyFrame = 1;
        orcaPlaying = orca;
        nessyPlaying = nessy;
        active = true;
        timersPending = false;
        this.killAll = killAll;
        queue = new int[0];
        if (killAll) {
            queue = Arrays.copyOf(enemyCities, enemyCities.length);
            explosionHandle = schedule(KILL, 1500);
        }
        if (bush) {
            queue = Arrays.copyOf(humanCities, humanCities.length);
            explosionHandle = schedule(SELF, 1500);
        }
    }

    /** Call once per 24 Hz frame with the other retained timeline advancement.
     * Existing callbacks run before autoplay; intervals run after autoplay. */
    public void advanceTimelines() {
        requireActive();
        if (timersPending) throw new IllegalStateException("Dispatch previous frame timers first");
        now += 1000;
        if (orcaPlaying && ++orcaFrame > 45) { orcaFrame = 1; orcaPlaying = false; }
        if (nessyPlaying && ++nessyFrame > 62) { nessyFrame = 1; nessyPlaying = false; }
        timersPending = true;
    }

    /** Call after this frame's callbacks/autoplay. New timers are ineligible until
     * the next frame, matching PresentationClock.step's eligible-ID snapshot. */
    public void dispatchIntervals(Listener listener) {
        requireActive();
        if (!timersPending) throw new IllegalStateException("Advance timelines first");
        timersPending = false;
        int eligible = nextId;
        while (active) {
            int slot = -1;
            for (int i = 0; i < kind.length; i++) {
                if (kind[i] != 0 && id[i] <= eligible && deadline[i] <= now
                        && (slot < 0 || deadline[i] < deadline[slot]
                        || deadline[i] == deadline[slot] && id[i] < id[slot])) slot = i;
            }
            if (slot < 0) return;
            int event = kind[slot];
            deadline[slot] += period[slot]; // Source reschedules before invoking.
            if (event == KILL && cursor == queue.length) {
                clear(explosionHandle);
                listener.stopBattleTicker();
                cleanupHandle = schedule(CLEANUP, 2500);
            } else if (event == KILL || event == SELF) {
                // AS undefined-city calls are no-ops when bush runs past its queue.
                if (cursor < queue.length) listener.explodeCity(queue[cursor++]);
            } else if (event == CLEANUP) {
                clear(cleanupHandle);
                listener.allUnitsDone();
                cleanupHandle = schedule(END, 25);
            } else if (event == END) {
                finish();
                listener.endBattle();
            }
        }
    }

    public int getOrcaFrame() { return orcaFrame; }
    public int getNessyFrame() { return nessyFrame; }
    public boolean isOrcaPlaying() { return orcaPlaying; }
    public boolean isNessyPlaying() { return nessyPlaying; }
    public boolean suppressesNormalTickerCompletion() { return active && killAll; }
    public long getElapsedTicks() { return now / 1000; }
    public boolean isActive() { return active; }

    /** Also called when normal battle completion wins (notably bush alone). */
    public void finish() {
        active = false;
        timersPending = false;
        Arrays.fill(kind, 0);
    }

    private int schedule(int event, int milliseconds) {
        for (int i = 0; i < kind.length; i++) if (kind[i] == 0) {
            kind[i] = event;
            id[i] = ++nextId;
            period[i] = milliseconds * 24L;
            deadline[i] = now + period[i];
            return id[i];
        }
        throw new IllegalStateException("Source hidden timer capacity exceeded");
    }

    private void clear(int handle) {
        for (int i = 0; i < kind.length; i++) if (id[i] == handle) kind[i] = 0;
    }

    private void requireActive() {
        if (!active) throw new IllegalStateException("No hidden battle active");
    }
}
