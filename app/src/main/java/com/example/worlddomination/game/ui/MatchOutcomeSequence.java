package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.FinalScoreBreakdown;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

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
        FINISH_SCORE,
        CHEATED_SCORE,
        COMPLETE
    }

    private final ReferenceRandom initialRandom;
    private ReferenceRandom presentationRandom;
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
    private boolean scoreCorrupt;

    public MatchOutcomeSequence() {
        this(0L);
    }

    public MatchOutcomeSequence(long randomSeed) {
        initialRandom = new ReferenceRandom(randomSeed);
        presentationRandom = initialRandom.copy();
    }

    public void begin(GameState.MatchStatus matchOutcome,
                      FinalScoreBreakdown breakdown) {
        begin(matchOutcome, breakdown, initialRandom, false);
    }

    public void begin(GameState.MatchStatus matchOutcome,
                      FinalScoreBreakdown breakdown, boolean corruptScore) {
        begin(matchOutcome, breakdown, initialRandom, corruptScore);
    }

    /** Takes ownership of a copy, leaving the match-start snapshot untouched. */
    public void begin(GameState.MatchStatus matchOutcome, FinalScoreBreakdown breakdown,
                      ReferenceRandom matchStartRandom) {
        begin(matchOutcome, breakdown, matchStartRandom, false);
    }

    public void begin(GameState.MatchStatus matchOutcome, FinalScoreBreakdown breakdown,
                      ReferenceRandom matchStartRandom, boolean corruptScore) {
        if (!MatchOutcomePresentation.isFinished(matchOutcome) || breakdown == null) {
            reset();
            return;
        }
        outcome = matchOutcome;
        scoreCorrupt = corruptScore;
        baseScore = breakdown.getBaseScore();
        finalScore = corruptScore ? 0L : breakdown.getFinalScore();
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
        if (matchStartRandom == null) {
            throw new IllegalArgumentException("Match-start presentation random is required");
        }
        presentationRandom = matchStartRandom.copy();
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
                    phase = scoreCorrupt ? Phase.CHEATED_SCORE : Phase.BASE_SCORE;
                    scoreVisible = true;
                    if (scoreCorrupt) {
                        scoreText = "!!CHEATED!!";
                    }
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
            case FINISH_SCORE:
                // Source sequence 4 removes its callback on the following tick.
                phase = Phase.COMPLETE;
                return EVENT_NONE;
            case CHEATED_SCORE:
                // Source omits endGameMC.onEnterFrame for a corrupt score. Its
                // looping score sound is stopped only by leaving the screen.
                return EVENT_NONE;
            case INACTIVE:
            case COMPLETE:
            default:
                return EVENT_NONE;
        }
    }

    private int tickBaseScore() {
        // Tools.randRange(1, score / 100) always consumes a double, even at zero.
        // Keep its fractional bound and original arithmetic operation order.
        double maxPlus = baseScore / 100d;
        long increment = (long) Math.floor(presentationRandom.nextDouble()
                * ((maxPlus - 1d) + 1d)) + 1L;
        scoreCount = increment >= baseScore - scoreCount
                ? baseScore : scoreCount + increment;
        updateScoreText();
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
            phase = Phase.FINISH_SCORE;
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
        scoreCorrupt = false;
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
                || phase == Phase.FLAG_BONUS || phase == Phase.FINISH_SCORE
                || phase == Phase.CHEATED_SCORE || phase == Phase.COMPLETE;
    }

    public boolean isScoreVisible() {
        return scoreVisible;
    }

    public boolean isComplete() {
        return phase == Phase.COMPLETE;
    }

    public boolean isAnimating() {
        return phase != Phase.INACTIVE && phase != Phase.CHEATED_SCORE
                && phase != Phase.COMPLETE;
    }

    public boolean isScoreCorrupt() {
        return scoreCorrupt;
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
