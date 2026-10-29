package com.example.worlddomination.game.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Platform-independent state for the first source-driven Android rules slice. */
public final class GameState {
    public enum Action {
        RESOURCES("Resources"),
        PRODUCTION("Production"),
        RESEARCH("Technology"),
        MASS_MEDIA("Mass Media"),
        ATTACK("Direct Attack"),
        PREPARED_ATTACK("Prepared Attack"),
        DEFENSE("Defense"),
        PROPAGANDA("Propaganda"),
        DIPLOMACY("Diplomacy");

        public final String label;

        Action(String label) {
            this.label = label;
        }

        public boolean isPorted() {
            return this == RESOURCES || this == MASS_MEDIA;
        }
    }

    public static final class PlayerState {
        public final int id;
        public final boolean human;
        public final int populationMillions;
        public int moneyMillions;
        public int religion;
        public int score;

        private PlayerState(int id, boolean human, int populationMillions,
                            int moneyMillions, int religion) {
            this.id = id;
            this.human = human;
            this.populationMillions = populationMillions;
            this.moneyMillions = moneyMillions;
            this.religion = religion;
        }
    }

    private static final int[] PLAYER_START_MONEY = {80, 65, 55};
    private static final int[] ENEMY_START_MONEY = {50, 65, 60};
    private static final int[] PLAYER_START_POPULATION = {500, 450, 400};
    private static final int[] ENEMY_START_POPULATION = {450, 450, 450};
    private static final int[] PLAYER_START_RELIGION = {80, 60, 20};
    private static final int[] ENEMY_START_RELIGION = {30, 40, 60};
    private static final int[] PLAYER_RESOURCE_DIVISOR = {9, 11, 13};
    private static final int[] PLAYER_RELIGION_ADDITION = {30, 30, 25};
    private static final int MAX_TURN_ACTIONS = 2;

    private final int difficulty;
    private final PlayerState[] players = new PlayerState[5];
    private final List<Action> queuedActions = new ArrayList<>(MAX_TURN_ACTIONS);
    private final List<String> messages = new ArrayList<>();
    private Action selectedAction;
    private int turn;
    private int selectedIsland = -1;

    /** Difficulty indices match the recovered source: 0 easy, 1 normal, 2 hard. */
    public GameState(int difficulty) {
        if (difficulty < 0 || difficulty > 2) {
            throw new IllegalArgumentException("Difficulty must be 0, 1 or 2");
        }
        this.difficulty = difficulty;
        players[0] = new PlayerState(0, true, PLAYER_START_POPULATION[difficulty],
                PLAYER_START_MONEY[difficulty], PLAYER_START_RELIGION[difficulty]);
        for (int i = 1; i < players.length; i++) {
            players[i] = new PlayerState(i, false, ENEMY_START_POPULATION[difficulty],
                    ENEMY_START_MONEY[difficulty], ENEMY_START_RELIGION[difficulty]);
        }
        messages.add("Welcome, commander. Select two actions.");
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getTurn() {
        return turn;
    }

    public PlayerState getHuman() {
        return players[0];
    }

    public PlayerState[] getPlayers() {
        return players.clone();
    }

    public Action getSelectedAction() {
        return selectedAction;
    }

    public int getSelectedIsland() {
        return selectedIsland;
    }

    public void selectIsland(int island) {
        if (island < 0 || island >= 5) {
            throw new IllegalArgumentException("Island index must be between 0 and 4");
        }
        selectedIsland = island;
    }

    public void selectAction(Action action) {
        if (action == null || !action.isPorted()) {
            selectedAction = null;
            return;
        }
        if (selectedAction == action) {
            selectedAction = null;
            return;
        }
        int queuedIndex = queuedActions.indexOf(action);
        if (queuedIndex >= 0) {
            queuedActions.remove(queuedIndex);
            messages.add(action.label + " removed.");
            selectedAction = null;
            return;
        }
        if (queuedActions.size() >= MAX_TURN_ACTIONS) {
            messages.add("Both action slots are full. Confirm the turn or clear them.");
            selectedAction = null;
            return;
        }
        selectedAction = action;
    }

    public boolean acceptSelectedAction() {
        if (selectedAction == null || queuedActions.size() >= MAX_TURN_ACTIONS) {
            return false;
        }
        queuedActions.add(selectedAction);
        messages.add(selectedAction.label + " queued.");
        selectedAction = null;
        return true;
    }

    public void cancelSelection() {
        selectedAction = null;
    }

    public void removeQueuedAction(int slot) {
        if (slot >= 0 && slot < queuedActions.size()) {
            Action removed = queuedActions.remove(slot);
            messages.add(removed.label + " removed.");
        }
    }

    public void clearQueuedActions() {
        queuedActions.clear();
        selectedAction = null;
    }

    public int getQueuedActionCount() {
        return queuedActions.size();
    }

    public Action getQueuedAction(int slot) {
        return slot >= 0 && slot < queuedActions.size() ? queuedActions.get(slot) : null;
    }

    public int getResourceIncomeMillions() {
        return Math.round((float) players[0].populationMillions
                / PLAYER_RESOURCE_DIVISOR[difficulty]);
    }

    public int getMassMediaGain() {
        return PLAYER_RELIGION_ADDITION[difficulty];
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public String getLatestMessage() {
        return messages.isEmpty() ? "Choose two actions, then confirm."
                : messages.get(messages.size() - 1);
    }

    /** Resolves only the two currently ported source actions. */
    public void confirmTurn() {
        if (queuedActions.size() != MAX_TURN_ACTIONS) {
            throw new IllegalStateException("Choose two actions before confirming the turn");
        }
        int resourcesGained = 0;
        int massMediaGained = 0;
        for (Action action : queuedActions) {
            if (action == Action.RESOURCES) {
                resourcesGained += getResourceIncomeMillions();
            } else if (action == Action.MASS_MEDIA) {
                massMediaGained += getMassMediaGain();
            }
        }
        players[0].moneyMillions += resourcesGained;
        players[0].religion = Math.min(100, players[0].religion + massMediaGained);
        if (resourcesGained > 0) {
            messages.add("You GAINED $ " + resourcesGained + ".000.000 from resources");
        }
        if (massMediaGained > 0) {
            messages.add("Mass Media increased influence by " + massMediaGained + ".");
        }
        turn++;
        messages.add("Turn " + turn + " complete.");
        queuedActions.clear();
        selectedAction = null;
        while (messages.size() > 4) {
            messages.remove(0);
        }
    }
}
