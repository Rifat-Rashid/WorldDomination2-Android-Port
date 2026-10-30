package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.FinalScoreBreakdown;
import com.example.worlddomination.game.simulation.GameState;

import java.util.Random;

/** Frame-timed presentation of the Flash victory and defeat score sequence. */
public final class MatchOutcomeSequence {
    public static final int EVENT_NONE = 0;
    public static final int EVENT_START_SCORE_COUNTER = 1;
    public static final int EVENT_STOP_SCORE_COUNTER = 1 << 1;
    public static final int EVENT_DEFEAT = 1 << 2;
    public static final int EVENT_SATELLITE_BONUS = 1 << 3;
    public static final int EVENT_FLAG_BONUS = 1 << 4;

    public static final int TICKS_BEFORE_ENDGAME = 3 * 24;
    public static final int TICKS_BEFORE_SCORE = 2 * 24;
    private static final int BONUS_CADENCE_TICKS = 41;
    private static final int BONUS_SETTLE_TICKS = 8;

    public enum Phase {
        INACTIVE,
        BEFORE_ENDGAME,
        INTRO,
        BASE_SCORE,
        SATELLITE_BONUS,
        FLAG_BONUS,
        COMPLETE
    }

    private final Random presentationRandom;
    private final long randomSeed;
    private Phase phase = Phase.INACTIVE;
    private GameState.MatchStatus outcome;
    private String scoreText = "0";
    private String satelliteBonusText = "";
    private String flagBonusText = "";
    private long baseScore;
    private long finalScore;
    private long scoreCount;
    private int satelliteCount;
    private int flagBonusCount;
    private int satellitesRevealed;
    private int flagsRevealed;
    private int phaseTicks;
    private boolean scoreVisible;

    public MatchOutcomeSequence(long randomSeed) {
        this.randomSeed = randomSeed;
        presentationRandom = new Random(randomSeed);
    }

    public void begin(GameState.MatchStatus matchOutcome,
                      FinalScoreBreakdown breakdown) {
        if (!MatchOutcomePresentation.isFinished(matchOutcome) || breakdown == null) {
            reset();
            return;
        }
        outcome = matchOutcome;
        baseScore = breakdown.getBaseScore();
        finalScore = breakdown.getFinalScore();
        satelliteCount = breakdown.getSatelliteCount();
        flagBonusCount = breakdown.getFlagBonusCount();
        satellitesRevealed = 0;
        flagsRevealed = 0;
        phaseTicks = 0;
        scoreCount = 0L;
        scoreText = MatchOutcomePresentation.formatScore(scoreCount);
        satelliteBonusText = "";
        flagBonusText = "";
        scoreVisible = false;
        presentationRandom.setSeed(randomSeed ^ baseScore ^ ((long) satelliteCount << 32)
                ^ flagBonusCount ^ outcome.ordinal());
        phase = Phase.BEFORE_ENDGAME;
    }

    /** Advances exactly one original 24 Hz presentation frame. */
    public int tick() {
        switch (phase) {
            case BEFORE_ENDGAME:
                if (++phaseTicks >= TICKS_BEFORE_ENDGAME) {
                    phaseTicks = 0;
                    phase = Phase.INTRO;
                }
                return EVENT_NONE;
            case INTRO:
                if (++phaseTicks >= TICKS_BEFORE_SCORE) {
                    phaseTicks = 0;
                    phase = Phase.BASE_SCORE;
                    return EVENT_START_SCORE_COUNTER
                            | (outcome == GameState.MatchStatus.DEFEAT
                            ? EVENT_DEFEAT : EVENT_NONE);
                }
                return EVENT_NONE;
            case BASE_SCORE:
                return tickBaseScore();
            case SATELLITE_BONUS:
                return tickSatelliteBonus();
            case FLAG_BONUS:
                return tickFlagBonus();
            case INACTIVE:
            case COMPLETE:
            default:
                return EVENT_NONE;
        }
    }

    private int tickBaseScore() {
        if (scoreCount < baseScore) {
            long maxIncrement = Math.max(1L, baseScore / 100L);
            long increment = 1L + nextLong(maxIncrement);
            scoreCount = increment >= baseScore - scoreCount
                    ? baseScore : scoreCount + increment;
            updateScoreText();
        }
        scoreVisible = true;
        if (scoreCount >= baseScore) {
            phase = Phase.SATELLITE_BONUS;
            phaseTicks = 0;
            return EVENT_STOP_SCORE_COUNTER;
        }
        return EVENT_NONE;
    }

    private int tickSatelliteBonus() {
        if (satelliteCount == 0) {
            phase = Phase.FLAG_BONUS;
            phaseTicks = 0;
            scoreVisible = true;
            return EVENT_NONE;
        }

        int events = EVENT_NONE;
        phaseTicks++;
        if (phaseTicks > BONUS_CADENCE_TICKS - 1) {
            phaseTicks = 0;
            satellitesRevealed++;
            satelliteBonusText = "Space satellite multiplier: x " + satellitesRevealed;
            events |= EVENT_SATELLITE_BONUS;
        }
        if (phaseTicks == 2 && satellitesRevealed > 0) {
            scoreCount = scoreCount > Long.MAX_VALUE / 2L
                    ? Long.MAX_VALUE : scoreCount * 2L;
            updateScoreText();
            scoreVisible = false;
        }
        if (phaseTicks == 6 && satellitesRevealed > 0) {
            scoreVisible = true;
        }
        if (satellitesRevealed == satelliteCount
                && phaseTicks >= BONUS_SETTLE_TICKS) {
            phase = Phase.FLAG_BONUS;
            phaseTicks = 0;
            scoreVisible = true;
        }
        return events;
    }

    private int tickFlagBonus() {
        if (flagBonusCount == 0) {
            phase = Phase.COMPLETE;
            scoreVisible = true;
            return EVENT_NONE;
        }

        int events = EVENT_NONE;
        phaseTicks++;
        if (phaseTicks > BONUS_CADENCE_TICKS - 1) {
            phaseTicks = 0;
            flagsRevealed++;
            flagBonusText = "Flag bonus (250.000): x " + flagsRevealed;
            events |= EVENT_FLAG_BONUS;
        }
        if (phaseTicks == 2 && flagsRevealed > 0) {
            long increment = FinalScoreBreakdown.calculateFlagBonusPoints(flagsRevealed)
                    - FinalScoreBreakdown.calculateFlagBonusPoints(flagsRevealed - 1);
            scoreCount = increment > Long.MAX_VALUE - scoreCount
                    ? Long.MAX_VALUE : scoreCount + increment;
            updateScoreText();
            scoreVisible = false;
        }
        if (phaseTicks == 6 && flagsRevealed > 0) {
            scoreVisible = true;
        }
        if (flagsRevealed == flagBonusCount
                && phaseTicks >= BONUS_SETTLE_TICKS) {
            phase = Phase.COMPLETE;
            phaseTicks = 0;
            scoreVisible = true;
        }
        return events;
    }

    private long nextLong(long bound) {
        long mask = bound - 1L;
        long random = presentationRandom.nextLong() >>> 1;
        if ((bound & mask) == 0L) {
            return random & mask;
        }
        long candidate = random % bound;
        while (random + mask - candidate < 0L) {
            random = presentationRandom.nextLong() >>> 1;
            candidate = random % bound;
        }
        return candidate;
    }

    private void updateScoreText() {
        scoreText = MatchOutcomePresentation.formatScore(scoreCount);
    }

    public void reset() {
        phase = Phase.INACTIVE;
        outcome = null;
        baseScore = 0L;
        finalScore = 0L;
        scoreCount = 0L;
        scoreText = "0";
        satelliteBonusText = "";
        flagBonusText = "";
        satelliteCount = 0;
        flagBonusCount = 0;
        satellitesRevealed = 0;
        flagsRevealed = 0;
        phaseTicks = 0;
        scoreVisible = false;
    }

    public Phase getPhase() {
        return phase;
    }

    public GameState.MatchStatus getOutcome() {
        return outcome;
    }

    public boolean isEndgameVisible() {
        return phase == Phase.INTRO || isScoreStarted();
    }

    public boolean isScoreStarted() {
        return phase == Phase.BASE_SCORE || phase == Phase.SATELLITE_BONUS
                || phase == Phase.FLAG_BONUS || phase == Phase.COMPLETE;
    }

    public boolean isScoreVisible() {
        return scoreVisible;
    }

    public boolean isComplete() {
        return phase == Phase.COMPLETE;
    }

    public long getScoreCount() {
        return scoreCount;
    }

    public String getScoreText() {
        return scoreText;
    }

    public String getSatelliteBonusText() {
        return satelliteBonusText;
    }

    public String getFlagBonusText() {
        return flagBonusText;
    }

    public long getFinalScore() {
        return finalScore;
    }

    public int getSatelliteCount() {
        return satelliteCount;
    }

    public int getSatellitesRevealed() {
        return satellitesRevealed;
    }

    public int getFlagBonusCount() {
        return flagBonusCount;
    }

    public int getFlagsRevealed() {
        return flagsRevealed;
    }
}
