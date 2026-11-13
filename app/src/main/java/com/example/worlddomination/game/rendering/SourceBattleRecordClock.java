package com.example.worlddomination.game.rendering;

/**
 * Counts eligible 24 Hz battle frames after the source action-label gate opens.
 * The caller owns the gate and calls {@link #tick()} once per eligible frame.
 *
 * <p>Source: Presenter.endBattle carries battleCounter=20; View.f_37 decrements
 * it before scanning records. Every pending building resets it to zero during
 * the scan, including a building whose start number has not yet arrived.
 */
public final class SourceBattleRecordClock {
    private static final int END_BATTLE_COUNTER = 20;

    private int carriedCounter;
    private int recordCount;
    private int firstRecordDelay;
    private int activatedRecordCount;
    private long elapsedTicks;
    private boolean battleActive;

    /** Starts a new match, discarding any prior battle and carried counter. */
    public void reset() {
        carriedCounter = 0;
        recordCount = 0;
        firstRecordDelay = 0;
        activatedRecordCount = 0;
        elapsedTicks = 0;
        battleActive = false;
    }

    /** Prepares a battle; ticking begins only when the caller opens the source gate. */
    public void begin(int recordCount, boolean hasPendingBuilding) {
        if (battleActive) {
            throw new IllegalStateException("Finish the current battle before beginning another");
        }
        if (recordCount < 0 || recordCount > Integer.MAX_VALUE - END_BATTLE_COUNTER) {
            throw new IllegalArgumentException("Invalid record count: " + recordCount);
        }
        if (hasPendingBuilding && recordCount == 0) {
            throw new IllegalArgumentException("A pending building requires a record");
        }
        this.recordCount = recordCount;
        // With a carried 20, a pending building zeroes the counter on the
        // first scan. Otherwise the first record waits for 20 decrements.
        firstRecordDelay = carriedCounter == 0 ? 0 : hasPendingBuilding ? 1 : carriedCounter;
        activatedRecordCount = 0;
        elapsedTicks = 0;
        battleActive = true;
    }

    /** Number of eligible ticks before this one-based record activates. */
    public int delayForRecord(int oneBasedStartNum) {
        if (!battleActive) {
            throw new IllegalStateException("No battle has begun");
        }
        if (oneBasedStartNum < 1 || oneBasedStartNum > recordCount) {
            throw new IllegalArgumentException("Invalid start number: " + oneBasedStartNum);
        }
        return firstRecordDelay + oneBasedStartNum - 1;
    }

    /** Advances one eligible frame; returns the newly activated start number, or zero. */
    public int tick() {
        if (!battleActive) {
            throw new IllegalStateException("No battle has begun");
        }
        elapsedTicks++;
        if (activatedRecordCount < recordCount
                && elapsedTicks == firstRecordDelay + (long) activatedRecordCount + 1L) {
            return ++activatedRecordCount;
        }
        return 0;
    }

    public boolean hasPendingRecords() {
        return battleActive && activatedRecordCount < recordCount;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    public int getActivatedRecordCount() {
        return activatedRecordCount;
    }

    /** Ends the source ticker, which can leave silent or watchdog-aborted records unstarted. */
    public void finishBattle() {
        if (!battleActive) {
            throw new IllegalStateException("No battle has begun");
        }
        carriedCounter = END_BATTLE_COUNTER;
        battleActive = false;
    }
}
