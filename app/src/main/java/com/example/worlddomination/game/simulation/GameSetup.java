package com.example.worlddomination.game.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Player selections recovered from the original game's setup menu. */
public final class GameSetup {
    public static final int MAX_OPPONENTS = 4;

    public static final class Leader {
        public final int id;
        public final String name;
        public final String religionCode;
        public final int portraitFrame;
        public final String displayName;
        public final String displayReligionCode;

        private Leader(int id, String name, String religionCode, int portraitFrame) {
            this.id = id;
            this.name = name;
            this.religionCode = religionCode;
            this.portraitFrame = portraitFrame;
            this.displayName = name.toUpperCase(java.util.Locale.US);
            this.displayReligionCode = religionCode.toUpperCase(java.util.Locale.US);
        }
    }

    private static final Leader[] LEADERS = {
            new Leader(0, "The Bombki", "chr", 1),
            new Leader(1, "M. Headinajar", "mus", 2),
            new Leader(2, "Col. Gedoffi", "mus", 3),
            new Leader(3, "Reh Moose Hunt", "bhu", 4),
            new Leader(4, "Asama Bin Loader", "mus", 5),
            new Leader(5, "Any Kissonme", "bhu", 6),
            new Leader(6, "Psyman Para's", "jew", 7),
            new Leader(7, "Ehum Oldbert", "jew", 8),
            new Leader(8, "Ehum Bareass", "jew", 9),
            new Leader(9, "Gladinme Putit", "com", 10),
            new Leader(10, "Keen to Chill", "com", 11),
            new Leader(11, "Angola Market", "chr", 12),
            new Leader(12, "Ballony Stare", "chr", 13),
            new Leader(13, "Who in There", "bhu", 14),
            new Leader(14, "JP Barksender", "chr", 15),
            new Leader(15, "Jack Searock", "chr", 16),
            new Leader(16, "GW Blush", "chr", 17),
            new Leader(17, "Taxi Isinawater", "bhu", 18),
            new Leader(18, "Bossi Isasad", "mus", 19)
    };
    private static final List<Leader> LEADER_VIEW;
    private static final String[] COLORS = {"blue", "red", "orange", "yellow", "green"};
    private static final String[] BUILDING_NAMES = {
            "Buddhist", "Christian", "Communist", "Jewish", "Muslim"
    };
    private static final String[] BUILDING_RELIGIONS = {"bhu", "chr", "com", "jew", "mus"};

    static {
        List<Leader> leaders = new ArrayList<>(LEADERS.length);
        Collections.addAll(leaders, LEADERS);
        LEADER_VIEW = Collections.unmodifiableList(leaders);
    }

    private final List<Integer> selectedOpponentIds = new ArrayList<>(MAX_OPPONENTS);
    private final List<Integer> selectedOpponentIdsView =
            Collections.unmodifiableList(selectedOpponentIds);
    private final List<Leader> selectedOpponents = new ArrayList<>(MAX_OPPONENTS);
    private final List<Leader> selectedOpponentsView = Collections.unmodifiableList(selectedOpponents);
    private int difficulty = 1;
    private int playerColor = 1;
    private int startingBuilding = 2;
    private int candidateLeaderId;
    private ReferenceRandom gameplayRandom;

    /** Keeps the deterministic setup fixture used by non-UI simulation tests. */
    public GameSetup() {
    }

    /**
     * Initializes the full-version setup selections from the source RNG. The
     * remaining random stream continues into game initialization.
     */
    public GameSetup(long seed) {
        this(new ReferenceRandom(seed));
    }

    GameSetup(ReferenceRandom random) {
        if (random == null) {
            throw new IllegalArgumentException("Random stream cannot be null");
        }
        gameplayRandom = random;
        playerColor = random.nextInt(COLORS.length) + 1;
        startingBuilding = random.nextInt(BUILDING_NAMES.length) + 1;
    }

    public static List<Leader> leaders() {
        return LEADER_VIEW;
    }

    public static Leader leader(int leaderId) {
        if (leaderId < 0 || leaderId >= LEADERS.length) {
            throw new IllegalArgumentException("Leader id must be 0..18");
        }
        return LEADERS[leaderId];
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        if (difficulty < 0 || difficulty > 2) {
            throw new IllegalArgumentException("Difficulty must be 0, 1 or 2");
        }
        this.difficulty = difficulty;
    }

    public int getPlayerColor() {
        return playerColor;
    }

    public void setPlayerColor(int playerColor) {
        if (playerColor < 1 || playerColor > COLORS.length) {
            throw new IllegalArgumentException("Player color must be 1..5");
        }
        this.playerColor = playerColor;
    }

    public String getPlayerColorName() {
        return COLORS[playerColor - 1];
    }

    /** Resolves the source color id stored on each player to its Flash library suffix. */
    public static String colorName(int playerColor) {
        if (playerColor < 1 || playerColor > COLORS.length) {
            throw new IllegalArgumentException("Player color must be 1..5");
        }
        return COLORS[playerColor - 1];
    }

    public int getStartingBuilding() {
        return startingBuilding;
    }

    public void setStartingBuilding(int building) {
        if (building < 1 || building > BUILDING_NAMES.length) {
            throw new IllegalArgumentException("Starting building must be 1..5");
        }
        startingBuilding = building;
    }

    public String getStartingBuildingName() {
        return BUILDING_NAMES[startingBuilding - 1];
    }

    public String getHumanReligionCode() {
        return BUILDING_RELIGIONS[startingBuilding - 1];
    }

    public int getCandidateLeaderId() {
        return candidateLeaderId;
    }

    public Leader getCandidateLeader() {
        return leader(candidateLeaderId);
    }

    /** Selects the leader displayed in the source setup menu's portrait carousel. */
    public void selectCandidateLeader(int leaderId) {
        leader(leaderId);
        candidateLeaderId = leaderId;
    }

    public void cycleCandidateLeader(int direction) {
        if (direction != 0) {
            candidateLeaderId = Math.floorMod(candidateLeaderId + Integer.signum(direction),
                    LEADERS.length);
        }
    }

    /** Adds a distinct rival, or removes it when selected again. */
    public boolean toggleOpponent(int leaderId) {
        leader(leaderId);
        int selectedIndex = selectedOpponentIds.indexOf(leaderId);
        if (selectedIndex >= 0) {
            selectedOpponentIds.remove(selectedIndex);
            selectedOpponents.remove(selectedIndex);
            return true;
        }
        if (selectedOpponentIds.size() >= MAX_OPPONENTS) {
            return false;
        }
        selectedOpponentIds.add(leaderId);
        selectedOpponents.add(leader(leaderId));
        return true;
    }

    public boolean removeOpponentAt(int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= selectedOpponentIds.size()) {
            return false;
        }
        selectedOpponentIds.remove(selectedIndex);
        selectedOpponents.remove(selectedIndex);
        return true;
    }

    public List<Integer> getSelectedOpponentIds() {
        return selectedOpponentIdsView;
    }

    public List<Leader> getSelectedOpponents() {
        return selectedOpponentsView;
    }

    public boolean canStart() {
        return !selectedOpponentIds.isEmpty();
    }

    ReferenceRandom copyGameplayRandom() {
        if (gameplayRandom == null) {
            throw new IllegalStateException("Seeded setup is required to continue its random stream");
        }
        return gameplayRandom.copy();
    }

    /**
     * Restarts the setup menu from its own source RNG stream. The match receives
     * a copy of this stream, so gameplay draws must not affect the next menu.
     */
    public GameSetup createNextSetup() {
        return new GameSetup(copyGameplayRandom());
    }
}
