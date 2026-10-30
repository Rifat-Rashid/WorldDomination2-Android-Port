package com.example.worlddomination.game.simulation;

/** Source end-game score after satellite and flag bonuses are revealed. */
public final class FinalScoreBreakdown {
    public static final long FLAG_BONUS_POINTS = 250_000L;

    private final long baseScore;
    private final int satelliteCount;
    private final int flagBonusCount;
    private final long finalScore;

    private FinalScoreBreakdown(long baseScore, int satelliteCount,
                                int flagBonusCount, long finalScore) {
        this.baseScore = baseScore;
        this.satelliteCount = satelliteCount;
        this.flagBonusCount = flagBonusCount;
        this.finalScore = finalScore;
    }

    /**
     * The Flash end-game sequence doubles the base score once per used space
     * satellite. Its flag animation adds 250,000 times the running number of
     * credited flags each time a new flag is revealed, producing a triangular
     * bonus total.
     */
    public static FinalScoreBreakdown calculate(long baseScore, int satelliteCount,
                                                 int flagBonusCount) {
        long nonNegativeScore = Math.max(0L, baseScore);
        int satellites = Math.max(0, satelliteCount);
        int flags = Math.max(0, flagBonusCount);

        long multipliedScore = nonNegativeScore;
        if (multipliedScore > 0 && satellites > 0) {
            int doublings = Math.min(satellites, 63);
            multipliedScore = doublings == 63
                    || multipliedScore > (Long.MAX_VALUE >> doublings)
                    ? Long.MAX_VALUE : multipliedScore << doublings;
        }
        long flagPoints = calculateFlagBonusPoints(flags);
        long total = multipliedScore > Long.MAX_VALUE - flagPoints
                ? Long.MAX_VALUE : multipliedScore + flagPoints;
        return new FinalScoreBreakdown(nonNegativeScore, satellites, flags, total);
    }

    /** Returns the exact cumulative flag score used by the Flash score animation. */
    public static long calculateFlagBonusPoints(int flagBonusCount) {
        int flags = Math.max(0, flagBonusCount);
        long firstFactor = flags;
        long secondFactor = (long) flags + 1L;
        if ((firstFactor & 1L) == 0L) {
            firstFactor /= 2L;
        } else {
            secondFactor /= 2L;
        }
        long triangularCount = multiplySaturated(firstFactor, secondFactor);
        return multiplySaturated(triangularCount, FLAG_BONUS_POINTS);
    }

    private static long multiplySaturated(long left, long right) {
        if (left == 0L || right == 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    public long getBaseScore() {
        return baseScore;
    }

    public int getSatelliteCount() {
        return satelliteCount;
    }

    public int getFlagBonusCount() {
        return flagBonusCount;
    }

    public long getFinalScore() {
        return finalScore;
    }
}
