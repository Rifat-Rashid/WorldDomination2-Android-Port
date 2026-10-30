package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.ai.OpponentAI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Platform independent state and deterministic rules for the native game. */
public final class GameState {
    public enum MatchStatus {
        ONGOING,
        VICTORY,
        DEFEAT
    }

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
            return this == RESOURCES || this == PRODUCTION || this == RESEARCH
                    || this == MASS_MEDIA || this == ATTACK || this == PROPAGANDA
                    || this == DIPLOMACY || this == PREPARED_ATTACK || this == DEFENSE;
        }
    }

    /** A diplomacy proposal waiting for the target nation's response. */
    public static final class DiplomacyProposal {
        public final int proposerId;
        public final int optionIndex;

        private DiplomacyProposal(int proposerId, int optionIndex) {
            this.proposerId = proposerId;
            this.optionIndex = optionIndex;
        }
    }

    /** One payload/city pair in the original carrier-ship three strike. */
    public static final class ShipStrike {
        public final int targetPlayerId;
        public final int cityIndex;
        public final String payloadName;

        public ShipStrike(int targetPlayerId, int cityIndex, String payloadName) {
            this.targetPlayerId = targetPlayerId;
            this.cityIndex = cityIndex;
            this.payloadName = payloadName;
        }
    }

    public static final class PlayerState {
        public final int id;
        public final boolean human;
        private final int leaderId;
        private final String religionCode;
        private final int playerColor;
        private boolean specialPerson;
        private boolean suicidePerson;
        private int primaryEnemyId = -1;
        private int secondaryEnemyId = -1;
        public int populationMillions;
        public int moneyMillions;
        public int turnMoneyMillions;
        public int religion;
        public long score;
        public int spyReadiness = 25;
        public boolean alive = true;
        int satellitesUsed;
        int lastAttackedByPlayerId;
        int largestBattleCasualties;
        private final double[] regardToward = new double[5];
        private final int[] ceasefireTurns = new int[5];
        private final int[] diplomacyOfferedLastTurn = new int[5];
        private final DiplomacyProposal[] pendingDiplomacy = new DiplomacyProposal[5];
        private final List<CityState> cities = new ArrayList<>(5);
        private final List<Action> lastTurnActions = new ArrayList<>(2);
        private final Set<String> inventedTechnologies = new LinkedHashSet<>();
        private final Map<String, WeaponState> weapons = new LinkedHashMap<>();
        private final Map<String, Integer> desiredStock = new LinkedHashMap<>();
        private final Set<Action> bioloadedActions = new LinkedHashSet<>();
        private String preparedCarrierName;
        private boolean bioloaded;

        private PlayerState(int id, boolean human, int populationMillions,
                            int moneyMillions, int religion, int leaderId,
                            String religionCode, int playerColor) {
            this.id = id;
            this.human = human;
            this.leaderId = leaderId;
            this.religionCode = religionCode;
            this.playerColor = playerColor;
            this.populationMillions = populationMillions;
            this.moneyMillions = moneyMillions;
            this.turnMoneyMillions = moneyMillions;
            this.religion = religion;
        }

        public boolean hasTechnology(String name) {
            return inventedTechnologies.contains(name);
        }

        /** Mirrors TechTree.allow_special from the reference game's unlock rule. */
        public boolean allowsSpecialTechnologies() {
            int unlockedSpecialTechCount = hasTechnology("plane2") ? 1 : 0;
            for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
                if (technology.level == 4 && hasTechnology(technology.name)) {
                    unlockedSpecialTechCount++;
                }
            }
            return unlockedSpecialTechCount >= 4;
        }

        public int getLeaderId() {
            return leaderId;
        }

        public String getReligionCode() {
            return religionCode;
        }

        public int getPlayerColor() {
            return playerColor;
        }

        public boolean isSpecialPerson() {
            return specialPerson;
        }

        public boolean isSuicidePerson() {
            return suicidePerson;
        }

        public int getPrimaryEnemyId() {
            return primaryEnemyId;
        }

        public int getSecondaryEnemyId() {
            return secondaryEnemyId;
        }

        public Set<String> getInventedTechnologies() {
            return Collections.unmodifiableSet(inventedTechnologies);
        }

        /** Source AI strength: technology weight, weapon stock strength and funds. */
        public int getStrength() {
            int technologyWeight = 0;
            for (String technologyName : inventedTechnologies) {
                TechnologyCatalog.Definition technology = TechnologyCatalog.byName(technologyName);
                if (technology != null) {
                    technologyWeight += technology.strengthWeight;
                }
            }
            int weaponStrength = 0;
            for (WeaponState weapon : weapons.values()) {
                weaponStrength += weapon.inStock * weapon.definition.level;
            }
            return technologyWeight + weaponStrength + moneyMillions;
        }

        public Map<String, WeaponState> getWeapons() {
            return Collections.unmodifiableMap(weapons);
        }

        public List<CityState> getCities() {
            return Collections.unmodifiableList(cities);
        }

        public List<Action> getLastTurnActions() {
            return Collections.unmodifiableList(lastTurnActions);
        }

        public int getWeaponStockByName(String weaponName) {
            WeaponState weapon = weapons.get(weaponName);
            return weapon == null ? 0 : weapon.inStock;
        }

        public int getSatellitesUsed() {
            return satellitesUsed;
        }

        public int getLastAttackedByPlayerId() {
            return lastAttackedByPlayerId;
        }

        public int getLargestBattleCasualties() {
            return largestBattleCasualties;
        }

        public CityState getCity(int index) {
            return index < 0 || index >= cities.size() ? null : cities.get(index);
        }

        public int getRegardToward(int playerId) {
            return (int) getRegardTowardExact(playerId);
        }

        /** Exact relationship value; combat and third-party reactions can be fractional. */
        public double getRegardTowardExact(int playerId) {
            if (playerId < 0 || playerId >= regardToward.length) {
                throw new IllegalArgumentException("Invalid player index");
            }
            return regardToward[playerId];
        }

        public boolean hasCeasefireWith(int playerId) {
            if (playerId < 0 || playerId >= ceasefireTurns.length) {
                throw new IllegalArgumentException("Invalid player index");
            }
            return ceasefireTurns[playerId] > 0;
        }

        public int getCeasefireTurnsWith(int playerId) {
            if (playerId < 0 || playerId >= ceasefireTurns.length) {
                throw new IllegalArgumentException("Invalid player index");
            }
            return ceasefireTurns[playerId];
        }

        public int getDiplomacyOfferedLastTurn(int playerId) {
            if (playerId < 0 || playerId >= diplomacyOfferedLastTurn.length) {
                throw new IllegalArgumentException("Invalid player index");
            }
            return diplomacyOfferedLastTurn[playerId];
        }

        public int getDesiredStock(String weaponName) {
            Integer desire = desiredStock.get(weaponName);
            return desire == null ? 0 : desire;
        }

        public DiplomacyProposal getPendingDiplomacyFrom(int playerId) {
            if (playerId < 0 || playerId >= pendingDiplomacy.length) {
                throw new IllegalArgumentException("Invalid player index");
            }
            return pendingDiplomacy[playerId];
        }

        /** The source's consumed carrier that is staged for a prepared strike. */
        public String getPreparedCarrierName() {
            return preparedCarrierName;
        }

        public boolean isBioloaded() {
            return bioloaded;
        }

        public boolean isActionBioloaded(Action action) {
            return bioloadedActions.contains(action);
        }

        public Set<Action> getBioloadedActions() {
            return Collections.unmodifiableSet(bioloadedActions);
        }
    }

    public static final class WeaponState {
        public final WeaponCatalog.Definition definition;
        public int inStock;
        public int investmentMillions;
        public boolean active;
        public boolean bioloaded;

        private WeaponState(WeaponCatalog.Definition definition) {
            this.definition = definition;
            this.inStock = definition.initialStock;
        }
    }

    private static final class ActionOrder {
        final int playerId;
        final Action action;
        final Map<String, Integer> productionBudgets;
        final List<String> technologies;
        final int reservedMillions;
        final int targetPlayerId;
        final int targetCityIndex;
        final String weaponName;
        final String payloadName;
        final boolean prepareCarrier;
        final String spyBuilding;
        final int diplomacyOption;
        final List<ShipStrike> shipStrikes;

        ActionOrder(int playerId, Action action,
                    Map<String, Integer> productionBudgets, List<String> technologies,
                    int reservedMillions, int targetPlayerId,
                    int targetCityIndex, String weaponName, String payloadName,
                    boolean prepareCarrier, String spyBuilding, int diplomacyOption) {
            this(playerId, action, productionBudgets, technologies, reservedMillions,
                    targetPlayerId, targetCityIndex, weaponName, payloadName, prepareCarrier,
                    spyBuilding, diplomacyOption, Collections.<ShipStrike>emptyList());
        }

        ActionOrder(int playerId, Action action,
                    Map<String, Integer> productionBudgets, List<String> technologies,
                    int reservedMillions, int targetPlayerId,
                    int targetCityIndex, String weaponName, String payloadName,
                    boolean prepareCarrier, String spyBuilding, int diplomacyOption,
                    List<ShipStrike> shipStrikes) {
            this.playerId = playerId;
            this.action = action;
            this.productionBudgets = productionBudgets;
            this.technologies = technologies;
            this.reservedMillions = reservedMillions;
            this.targetPlayerId = targetPlayerId;
            this.targetCityIndex = targetCityIndex;
            this.weaponName = weaponName;
            this.payloadName = payloadName;
            this.prepareCarrier = prepareCarrier;
            this.spyBuilding = spyBuilding;
            this.diplomacyOption = diplomacyOption;
            this.shipStrikes = Collections.unmodifiableList(new ArrayList<>(shipStrikes));
        }
    }

    private static final class BiologicalEffect {
        final int targetPlayerId;
        final int cityIndex;
        final Set<Action> disabledActions = new LinkedHashSet<>();
        final Set<String> disabledWeapons = new LinkedHashSet<>();

        BiologicalEffect(int targetPlayerId, int cityIndex) {
            this.targetPlayerId = targetPlayerId;
            this.cityIndex = cityIndex;
        }
    }

    private static final class ShipStrikeResult {
        final ShipStrike strike;
        final String payloadName;
        final int rolledCasualties;
        final boolean intercepted;

        ShipStrikeResult(ShipStrike strike, String payloadName, int rolledCasualties,
                         boolean intercepted) {
            this.strike = strike;
            this.payloadName = payloadName;
            this.rolledCasualties = rolledCasualties;
            this.intercepted = intercepted;
        }
    }

    private static final int[] PLAYER_START_MONEY = {80, 65, 55};
    private static final int[] ENEMY_START_MONEY = {50, 65, 60};
    private static final int[] PLAYER_START_POPULATION = {500, 450, 400};
    private static final int[] ENEMY_START_POPULATION = {450, 450, 450};
    private static final int[] PLAYER_RESOURCE_DIVISOR = {9, 11, 13};
    private static final int[] ENEMY_RESOURCE_DIVISOR = {9, 10, 12};
    private static final int[] PLAYER_RELIGION_ADDITION = {30, 30, 25};
    private static final int[] ENEMY_RELIGION_ADDITION = {45, 35, 25};
    private static final String[] STARTING_TECHNOLOGIES = {
            "rocket1", "warhead1", "bioload1", "soldier1", "spy1", "plane1",
            "antiAir", "antiRocket"
    };
    private static final String[] STARTING_PRODUCTION_CATEGORIES = {
            "rockets", "warhead", "bioload", "soldier", "spy", "planes", "special", "defense"
    };
    private static final int[] PRODUCTION_GROUPS = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int PLAYER_STOCK_CAP = 4;
    private static final int MAX_TURN_ACTIONS = 2;
    private static final int[][] SPY_STATISTICS = {
            {5, 20}, {7, 40}, {9, 60}, {12, 80}
    };
    private static final int[] SPY_MONEY_CAP = {90, 120, 150};
    private static final int[] DIPLOMACY_DURATION = {3, 6, 10, 3, 6, 10};
    private static final int[] DIPLOMACY_AMOUNT = {10, 50, 90, 10, 50, 90};
    private static final int[] STARTING_REGARD = {80, 65, 50};
    private static final int DIPLOMACY_BONUS = 3;
    private static final String[] SPY_BUILDINGS = {
            "spyCenter", "tech", "religion", "resources", "factory"
    };
    private static final String[] SPY_ACTION_CATEGORIES = {
            "diplomacy", "factory", "tech", "religion", "resources"
    };
    private static final Action[] BIO_DISABLED_ACTIONS = {
            Action.RESOURCES, Action.PRODUCTION, Action.RESEARCH, Action.MASS_MEDIA,
            Action.PROPAGANDA, Action.DIPLOMACY
    };
    private static final int[] BIO_DISABLED_STOCK_ATTEMPTS = {0, 6, 10, 17, 30};

    private final int difficulty;
    private final boolean opponentTurnsEnabled;
    private final PlayerState[] players = new PlayerState[5];
    private final List<ActionOrder> queuedActions = new ArrayList<>(MAX_TURN_ACTIONS);
    private final List<String> messages = new ArrayList<>();
    private final Random random;
    private Action selectedAction;
    private String selectedProductionCategory = "soldier";
    private int selectedProductionPercent;
    private String selectedTechnology;
    private int selectedTargetPlayer = 1;
    private int selectedTargetCity;
    private String selectedAttackWeapon = "soldier1";
    private String selectedPreparedCarrier;
    private String selectedPreparedPayload;
    private final List<ShipStrike> selectedShipStrikes = new ArrayList<>(3);
    private String selectedDefenseWeapon;
    private String selectedSpyBuilding = "spyCenter";
    private int selectedDiplomacyOption;
    private int turn;
    private int selectedIsland = -1;
    private MatchStatus matchStatus = MatchStatus.ONGOING;
    private FinalScoreBreakdown finalScoreBreakdown;

    /** Difficulty indices follow the recovered source: 0 easy, 1 normal, 2 hard. */
    public GameState(int difficulty) {
        this(difficulty, 0L, true, null);
    }

    /** Seed injection keeps gameplay rules reproducible in JVM tests. */
    public GameState(int difficulty, long seed) {
        this(difficulty, seed, true, null);
    }

    /** Builds a match from the native setup selections. */
    public GameState(GameSetup setup, long seed) {
        this(setupDifficulty(setup), seed, true, setup);
    }

    /** Package-private quiet-opponent mode isolates player-rule tests from AI turns. */
    GameState(int difficulty, long seed, boolean opponentTurnsEnabled) {
        this(difficulty, seed, opponentTurnsEnabled, null);
    }

    private GameState(int difficulty, long seed, boolean opponentTurnsEnabled,
                      GameSetup setup) {
        if (difficulty < 0 || difficulty > 2) {
            throw new IllegalArgumentException("Difficulty must be 0, 1 or 2");
        }
        if (setup != null && !setup.canStart()) {
            throw new IllegalArgumentException("Select at least one opponent before starting");
        }
        this.difficulty = difficulty;
        this.opponentTurnsEnabled = opponentTurnsEnabled;
        this.random = new Random(seed);
        players[0] = new PlayerState(0, true, PLAYER_START_POPULATION[difficulty],
                PLAYER_START_MONEY[difficulty], PLAYER_START_MONEY[difficulty], -1,
                setup == null ? "chr" : setup.getHumanReligionCode(),
                setup == null ? 1 : setup.getPlayerColor());
        List<Integer> opponentIds = setup == null
                ? Collections.<Integer>emptyList() : setup.getSelectedOpponentIds();
        List<Integer> availableColors = new ArrayList<>(5);
        for (int color = 1; color <= 5; color++) {
            if (color != players[0].playerColor) {
                availableColors.add(color);
            }
        }
        boolean specialPersonAssigned = false;
        boolean suicidePersonAssigned = false;
        for (int i = 1; i < players.length; i++) {
            boolean active = setup == null || i <= opponentIds.size();
            int leaderId = setup == null ? -1 : active ? opponentIds.get(i - 1) : -1;
            String religionCode = setup == null ? "chr"
                    : active ? GameSetup.leader(leaderId).religionCode : "chr";
            int color = setup == null ? i + 1
                    : active ? availableColors.remove(random.nextInt(availableColors.size())) : 0;
            players[i] = new PlayerState(i, false, ENEMY_START_POPULATION[difficulty],
                    ENEMY_START_MONEY[difficulty], new int[]{30, 40, 60}[difficulty],
                    leaderId, religionCode, color);
            if (!active) {
                players[i].alive = false;
                players[i].populationMillions = 0;
                players[i].moneyMillions = 0;
                players[i].turnMoneyMillions = 0;
            } else {
                // Presenter_gameMenu.init_main_interface assigns at most one
                // special and one suicide AI, in player order, using 1-in-3 rolls.
                if (random.nextInt(3) + 1 == 2 && !specialPersonAssigned) {
                    players[i].specialPerson = true;
                    specialPersonAssigned = true;
                } else if (random.nextInt(3) + 1 == 2 && !suicidePersonAssigned) {
                    players[i].suicidePerson = true;
                    suicidePersonAssigned = true;
                }
            }
        }
        for (PlayerState player : players) {
            for (int other = 0; other < players.length; other++) {
                player.regardToward[other] = player.specialPerson
                        ? 70 : STARTING_REGARD[difficulty];
            }
            initializeInventory(player);
        }
        for (PlayerState player : players) {
            if (!player.human && player.alive) {
                initializeAiDesires(player);
                initializeAiEnemies(player);
            }
        }
        initializeDifficultyTechnologies();
        if (difficulty == 2) {
            initializeHardDifficultyOpponents();
        }
        initializeSpecialPersonRegard();
        for (PlayerState player : players) {
            initializeCities(player);
        }
        messages.add("Welcome, commander. Select two actions.");
    }

    private void initializeAiEnemies(PlayerState player) {
        player.primaryEnemyId = chooseRandomEnemy(player, -1);
        player.secondaryEnemyId = chooseRandomEnemy(player, player.primaryEnemyId);
        if (player.primaryEnemyId >= 0) {
            player.regardToward[player.primaryEnemyId] = 45;
        }
        if (player.secondaryEnemyId >= 0) {
            player.regardToward[player.secondaryEnemyId] = 50;
        }
    }

    private int chooseRandomEnemy(PlayerState player, int excludedPlayerId) {
        List<PlayerState> candidates = new ArrayList<>(players.length - 1);
        for (PlayerState candidate : players) {
            if (candidate != null && candidate.alive && candidate.id != player.id
                    && candidate.id != excludedPlayerId) {
                candidates.add(candidate);
            }
        }
        // The ActionScript helper falls back to player zero when its filtered list is empty.
        return candidates.isEmpty() ? 0
                : candidates.get(random.nextInt(candidates.size())).id;
    }

    private void reassignDeadAiEnemies() {
        for (PlayerState player : players) {
            if (player.human || !player.alive) {
                continue;
            }
            if (player.primaryEnemyId >= 0 && !players[player.primaryEnemyId].alive) {
                player.primaryEnemyId = chooseRandomEnemy(player, player.secondaryEnemyId);
            }
            if (player.secondaryEnemyId >= 0 && !players[player.secondaryEnemyId].alive) {
                player.secondaryEnemyId = chooseRandomEnemy(player, player.primaryEnemyId);
            }
        }
    }

    private void initializeDifficultyTechnologies() {
        int humanTechnologyLevel = difficulty == 0 ? 2 : 1;
        inventTechnologiesThroughLevel(players[0], humanTechnologyLevel);
        if (difficulty == 1) {
            return;
        }
        for (int playerId = 1; playerId < players.length; playerId++) {
            PlayerState opponent = players[playerId];
            if (!opponent.alive) {
                continue;
            }
            int opponentTechnologyLevel = difficulty == 2 ? 2 : 1;
            inventTechnologiesThroughLevel(opponent, opponentTechnologyLevel);
            if (difficulty == 2) {
                setWeaponStockPerLevel(opponent, 1, 2);
                setWeaponStockPerLevel(opponent, 2, 1);
            }
        }
    }

    private static void inventTechnologiesThroughLevel(PlayerState player, int level) {
        for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
            if (technology.group == 7 || technology.level > level) {
                continue;
            }
            player.inventedTechnologies.add(technology.name);
            WeaponState weapon = player.weapons.get(technology.name);
            if (weapon != null) {
                weapon.active = true;
            }
        }
    }

    private static void setWeaponStockPerLevel(PlayerState player, int level, int stock) {
        for (WeaponState weapon : player.weapons.values()) {
            if (weapon.definition.productionGroup != 8 && weapon.definition.level == level) {
                weapon.inStock = stock;
            }
        }
    }

    private void initializeHardDifficultyOpponents() {
        for (int playerId = 1; playerId < players.length; playerId++) {
            PlayerState opponent = players[playerId];
            if (!opponent.alive) {
                continue;
            }
            opponent.primaryEnemyId = 0;
            opponent.secondaryEnemyId = 0;
            opponent.regardToward[0] = 15 + random.nextInt(46);
        }
    }

    private void initializeSpecialPersonRegard() {
        for (PlayerState special : players) {
            if (!special.specialPerson || !special.alive) {
                continue;
            }
            for (PlayerState other : players) {
                if (other.alive) {
                    other.regardToward[special.id] = 70;
                    special.regardToward[other.id] = 70;
                }
            }
        }
    }

    private static int setupDifficulty(GameSetup setup) {
        if (setup == null) {
            throw new IllegalArgumentException("Game setup cannot be null");
        }
        return setup.getDifficulty();
    }

    private void initializeAiDesires(PlayerState player) {
        boolean rocketCarrier = random.nextBoolean();
        boolean warheadPayload = random.nextBoolean();
        boolean conventionalUnits = random.nextBoolean();
        for (WeaponState weapon : player.weapons.values()) {
            player.desiredStock.put(weapon.definition.name, 0);
        }
        setDesireLine(player, "rocket", rocketCarrier ? new int[]{2, 2, 2, 5}
                : new int[]{0, 0, 0, 0});
        setDesireLine(player, "warhead", warheadPayload ? new int[]{2, 2, 2, 5}
                : new int[]{0, 0, 0, 0});
        setDesireLine(player, "bioload", warheadPayload ? new int[]{0, 0, 0, 0}
                : new int[]{2, 2, 2, 5});
        setDesireLine(player, "soldier", conventionalUnits ? new int[]{1, 1, 1, 5}
                : new int[]{0, 0, 0, 0});
        setDesireLine(player, "spy", new int[]{1, 1, 1, 5});
        player.desiredStock.put("plane1", rocketCarrier ? 0 : 2);
        player.desiredStock.put("plane2", rocketCarrier ? 0 : 5);
        // The recovered ActionScript calls a misspelled random helper for this
        // choice; its fallback profile invests in the space carrier line.
        player.desiredStock.put("sub", 1);
        player.desiredStock.put("ship", 1);
        player.desiredStock.put("space", 5);
        player.desiredStock.put("specialLoad", 5);
        if (player.specialPerson) {
            setPersonalityDesires(player, new int[]{
                    0, 0, 0, 0, 1, 1, 15, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 15, 0, 0
            });
        } else if (player.suicidePerson) {
            setPersonalityDesires(player, new int[]{
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
                    1, 1, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            });
        }
    }

    private static void setPersonalityDesires(PlayerState player, int[] sourceDesires) {
        int index = 0;
        for (WeaponCatalog.Definition definition : WeaponCatalog.all()) {
            if (definition.productionGroup == 8) {
                continue;
            }
            int desire = sourceDesires[index++];
            player.desiredStock.put(definition.name, desire);
            player.desiredStock.put(definition.technology, desire);
        }
        if (index != sourceDesires.length) {
            throw new IllegalStateException("AI personality desire table does not match weapon catalog");
        }
    }

    private static void setDesireLine(PlayerState player, String prefix, int[] desires) {
        for (int i = 0; i < desires.length; i++) {
            player.desiredStock.put(prefix + (i + 1), desires[i]);
        }
    }

    private static void initializeInventory(PlayerState player) {
        Collections.addAll(player.inventedTechnologies, STARTING_TECHNOLOGIES);
        for (WeaponCatalog.Definition definition : WeaponCatalog.all()) {
            WeaponState weapon = new WeaponState(definition);
            weapon.active = player.inventedTechnologies.contains(definition.technology);
            player.weapons.put(definition.name, weapon);
        }
    }

    /** The source distributes each nation's starting population across five cities. */
    private void initializeCities(PlayerState player) {
        int remaining = player.populationMillions;
        int initial = remaining;
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            int population;
            if (cityIndex == 4) {
                population = remaining;
            } else {
                int minimum = Math.min(40, remaining);
                int maximum = Math.min(initial / 4, remaining);
                population = maximum <= minimum
                        ? minimum : minimum + random.nextInt(maximum - minimum + 1);
                remaining -= population;
            }
            player.cities.add(new CityState(player.id * 5 + cityIndex,
                    player.id, cityIndex, population));
        }
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getTurn() {
        return turn;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public FinalScoreBreakdown getFinalScoreBreakdown() {
        return finalScoreBreakdown;
    }

    /** Returns the score shown on the end screen, including source bonuses. */
    public long getFinalScore() {
        return finalScoreBreakdown == null
                ? Math.max(0L, getHuman().score) : finalScoreBreakdown.getFinalScore();
    }

    public PlayerState getHuman() {
        return players[0];
    }

    public PlayerState[] getPlayers() {
        return players.clone();
    }

    public PlayerState getPlayerState(int playerId) {
        return playerById(playerId);
    }

    public String getPreparedCarrierName(int playerId) {
        return playerById(playerId).preparedCarrierName;
    }

    public boolean isActionBioloaded(int playerId, Action action) {
        return action != null && playerById(playerId).bioloadedActions.contains(action);
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
        if (matchStatus != MatchStatus.ONGOING) {
            return;
        }
        if (action == null || (!action.isPorted() && action != Action.PREPARED_ATTACK
                && action != Action.DEFENSE)) {
            cancelSelection();
            return;
        }
        if (isActionBioloaded(0, action)) {
            selectedAction = null;
            messages.add(action.label + " is unavailable due to biological contamination.");
            return;
        }
        if (selectedAction == action) {
            cancelSelection();
            return;
        }
        int queuedIndex = queuedActionIndex(action);
        if (queuedIndex >= 0) {
            removeQueuedAction(queuedIndex);
            selectedAction = null;
            return;
        }
        if (queuedActions.size() >= MAX_TURN_ACTIONS) {
            messages.add("Both action slots are full. Confirm the turn or clear them.");
            selectedAction = null;
            return;
        }
        selectedAction = action;
        selectedTechnology = null;
        selectedTargetPlayer = selectedIsland > 0 ? selectedIsland : 1;
        selectedTargetCity = 0;
        selectedAttackWeapon = firstAvailableAttackWeapon();
        selectedPreparedCarrier = null;
        selectedPreparedPayload = null;
        selectedShipStrikes.clear();
        selectedDefenseWeapon = firstAvailableDefenseWeapon();
        selectedSpyBuilding = "spyCenter";
        selectedDiplomacyOption = 0;
        selectedProductionCategory = "soldier";
        selectedProductionPercent = 0;
    }

    /** Sets one production row as the original interface's percentage sliders do. */
    public void configureProduction(String category, int percent) {
        if (selectedAction != Action.PRODUCTION) {
            throw new IllegalStateException("Select Production before setting its investment");
        }
        if (productionGroup(category) == 0) {
            throw new IllegalArgumentException("Unknown production category: " + category);
        }
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Production percentage must be 0..100");
        }
        selectedProductionCategory = category;
        selectedProductionPercent = percent;
    }

    /** Selects a technology only when its source prerequisite and funds are satisfied. */
    public boolean selectResearchTechnology(String technology) {
        if (selectedAction != Action.RESEARCH || !isResearchAvailable(technology)) {
            return false;
        }
        selectedTechnology = technology;
        return true;
    }

    public List<String> getAvailableResearch() {
        List<String> available = new ArrayList<>();
        for (TechnologyCatalog.Definition definition : TechnologyCatalog.all()) {
            if (isResearchAvailable(definition.name)) {
                available.add(definition.name);
            }
        }
        return Collections.unmodifiableList(available);
    }

    public boolean isResearchAvailable(String technology) {
        TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
        if (definition == null || getHuman().hasTechnology(definition.name)) {
            return false;
        }
        if (definition.group == 7 && !getHuman().allowsSpecialTechnologies()) {
            return false;
        }
        if (queuedTechnology(technology)) {
            return false;
        }
        if (definition.prerequisite != null
                && !getHuman().hasTechnology(definition.prerequisite)) {
            return false;
        }
        return definition.costMillions <= getHuman().turnMoneyMillions;
    }

    public int getTechnologyCost(String technology) {
        TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
        return definition == null ? 0 : definition.costMillions;
    }

    public String getTechnologyLabel(String technology) {
        TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
        return definition == null ? technology : definition.prettyName;
    }

    public String getSelectedTechnology() {
        return selectedTechnology;
    }

    public int getSelectedTargetPlayer() {
        return selectedTargetPlayer;
    }

    public int getSelectedTargetCity() {
        return selectedTargetCity;
    }

    public int getSelectedDiplomacyOption() {
        return selectedDiplomacyOption;
    }

    public String getDiplomacyOptionType(int optionIndex) {
        validateDiplomacyOption(optionIndex);
        return optionIndex < 3 ? "offer" : "demand";
    }

    public int getDiplomacyOptionAmount(int optionIndex) {
        validateDiplomacyOption(optionIndex);
        return DIPLOMACY_AMOUNT[optionIndex];
    }

    public int getDiplomacyOptionDuration(int optionIndex) {
        validateDiplomacyOption(optionIndex);
        return DIPLOMACY_DURATION[optionIndex];
    }

    public int getSelectedDiplomacyAmount() {
        return getDiplomacyOptionAmount(selectedDiplomacyOption);
    }

    public int getSelectedDiplomacyDuration() {
        return getDiplomacyOptionDuration(selectedDiplomacyOption);
    }

    private static void validateDiplomacyOption(int optionIndex) {
        if (optionIndex < 0 || optionIndex >= DIPLOMACY_AMOUNT.length) {
            throw new IllegalArgumentException("Diplomacy option must be 0..5");
        }
    }

    public void cycleDiplomacyOption(int direction) {
        if (direction != 0 && selectedAction == Action.DIPLOMACY) {
            selectedDiplomacyOption = Math.floorMod(selectedDiplomacyOption
                    + Integer.signum(direction), DIPLOMACY_AMOUNT.length);
        }
    }

    public void cycleDiplomacyTarget(int direction) {
        if (direction == 0 || selectedAction != Action.DIPLOMACY) {
            return;
        }
        for (int step = 1; step < players.length; step++) {
            int candidate = 1 + Math.floorMod(selectedTargetPlayer - 1
                    + Integer.signum(direction) * step, players.length - 1);
            if (players[candidate].alive) {
                selectedTargetPlayer = candidate;
                return;
            }
        }
    }

    public boolean canAcceptDiplomacy() {
        if (selectedAction != Action.DIPLOMACY || selectedTargetPlayer <= 0
                || selectedTargetPlayer >= players.length) {
            return false;
        }
        PlayerState target = players[selectedTargetPlayer];
        if (!target.alive || getHuman().hasCeasefireWith(target.id)
                || target.pendingDiplomacy[0] != null) {
            return false;
        }
        return selectedDiplomacyOption >= 3
                || DIPLOMACY_AMOUNT[selectedDiplomacyOption] <= getHuman().turnMoneyMillions;
    }

    public DiplomacyProposal getPendingDiplomacyProposal(int targetPlayerId) {
        return playerById(targetPlayerId).pendingDiplomacy[0];
    }

    public DiplomacyProposal getPendingDiplomacyProposal(int targetPlayerId,
                                                          int proposerPlayerId) {
        return playerById(targetPlayerId).pendingDiplomacy[proposerPlayerId];
    }

    public DiplomacyProposal getPendingHumanDiplomacyProposal() {
        for (int proposerId = 1; proposerId < players.length; proposerId++) {
            DiplomacyProposal proposal = players[0].pendingDiplomacy[proposerId];
            if (proposal != null) {
                return proposal;
            }
        }
        return null;
    }

    public boolean canAcceptPendingHumanDiplomacy() {
        DiplomacyProposal proposal = getPendingHumanDiplomacyProposal();
        return proposal != null && (proposal.optionIndex < 3
                || getHuman().turnMoneyMillions >= DIPLOMACY_AMOUNT[proposal.optionIndex]);
    }

    /**
     * Entry point for a ported opponent AI to send a source-format proposal.
     * Offer costs are paid when the proposal is sent; a rejected offer is refunded.
     */
    public boolean submitDiplomacyProposal(int proposerPlayerId, int targetPlayerId,
                                           int optionIndex) {
        validateDiplomacyOption(optionIndex);
        if (proposerPlayerId == targetPlayerId || proposerPlayerId < 0
                || proposerPlayerId >= players.length || targetPlayerId < 0
                || targetPlayerId >= players.length) {
            return false;
        }
        PlayerState proposer = players[proposerPlayerId];
        PlayerState target = players[targetPlayerId];
        if (!proposer.alive || !target.alive || target.pendingDiplomacy[proposerPlayerId] != null
                || (!proposer.human
                && proposer.diplomacyOfferedLastTurn[targetPlayerId] > 0)
                || proposer.ceasefireTurns[targetPlayerId] > 0) {
            return false;
        }
        int amount = DIPLOMACY_AMOUNT[optionIndex];
        boolean offer = optionIndex < 3;
        if (offer && proposer.moneyMillions < amount) {
            return false;
        }
        if (offer) {
            proposer.moneyMillions -= amount;
        }
        target.pendingDiplomacy[proposerPlayerId] =
                new DiplomacyProposal(proposerPlayerId, optionIndex);
        proposer.diplomacyOfferedLastTurn[targetPlayerId] = 2;
        applyDiplomacySentRegard(proposer, target, optionIndex);
        return true;
    }

    /** Resolves the current human-facing proposal using the target's available funds. */
    public boolean respondToDiplomacyProposal(boolean accept) {
        DiplomacyProposal proposal = getPendingHumanDiplomacyProposal();
        if (proposal == null) {
            return false;
        }
        if (accept && !canAcceptPendingHumanDiplomacy()) {
            return false;
        }
        resolveDiplomacyResponse(players[0], proposal, accept);
        return true;
    }

    public String getSelectedAttackWeapon() {
        return selectedAttackWeapon;
    }

    public int getSpyReadiness() {
        return getHuman().spyReadiness;
    }

    public String getSelectedSpyBuilding() {
        return selectedSpyBuilding;
    }

    public boolean isSelectedAttackSpy() {
        WeaponState weapon = getHuman().weapons.get(selectedAttackWeapon);
        return selectedAction == Action.ATTACK && weapon != null
                && "spy".equals(weapon.definition.type);
    }

    public void cycleSpyBuilding(int direction) {
        if (direction == 0 || !isSelectedAttackSpy()) {
            return;
        }
        int current = 0;
        for (int i = 0; i < SPY_BUILDINGS.length; i++) {
            if (SPY_BUILDINGS[i].equals(selectedSpyBuilding)) {
                current = i;
                break;
            }
        }
        int next = Math.floorMod(current + Integer.signum(direction), SPY_BUILDINGS.length);
        selectedSpyBuilding = SPY_BUILDINGS[next];
    }

    public List<String> getAvailableAttackWeapons() {
        List<String> available = new ArrayList<>();
        for (WeaponState weapon : getHuman().weapons.values()) {
            String type = weapon.definition.type;
            boolean soldier = "soldier".equals(type);
            boolean readySpy = "spy".equals(type)
                    && weapon.definition.level <= getHuman().spyReadiness / 25;
            if (weapon.active && weapon.inStock > 0 && (soldier || readySpy)) {
                available.add(weapon.definition.name);
            }
        }
        return Collections.unmodifiableList(available);
    }

    public boolean configureAttack(int targetPlayerId, int cityIndex, String weaponName) {
        if (selectedAction != Action.ATTACK || targetPlayerId <= 0
                || targetPlayerId >= players.length || cityIndex < 0 || cityIndex >= 5) {
            return false;
        }
        PlayerState target = players[targetPlayerId];
        CityState city = target.getCity(cityIndex);
        WeaponState weapon = getHuman().weapons.get(weaponName);
        boolean soldier = weapon != null && "soldier".equals(weapon.definition.type);
        if (!target.alive || totalPopulation(target) <= 0 || city == null
                || weapon == null || !weapon.active || weapon.inStock <= 0
                || (!soldier && !canUseSpy(getHuman(), weapon))
                || (soldier && city.populationMillions <= 0)
                || (soldier && target.hasCeasefireWith(getHuman().id))) {
            return false;
        }
        selectedTargetPlayer = targetPlayerId;
        selectedTargetCity = cityIndex;
        selectedAttackWeapon = weaponName;
        return true;
    }

    /** Available carrier stock that can be paired with a researched payload. */
    public List<String> getAvailablePreparedCarriers(int playerId) {
        PlayerState player = playerById(playerId);
        List<String> available = new ArrayList<>();
        for (WeaponState weapon : player.weapons.values()) {
            if (isCarrier(weapon) && weapon.active && weapon.inStock > 0
                    && !getAvailablePreparedPayloads(playerId,
                    weapon.definition.name).isEmpty()) {
                available.add(weapon.definition.name);
            }
        }
        return Collections.unmodifiableList(available);
    }

    /** Compatible stocked payloads for a carrier, in source catalog order. */
    public List<String> getAvailablePreparedPayloads(int playerId, String carrierName) {
        PlayerState player = playerById(playerId);
        WeaponState carrier = player.weapons.get(carrierName);
        List<String> available = new ArrayList<>();
        if (!isCarrier(carrier)) {
            return Collections.unmodifiableList(available);
        }
        for (WeaponState payload : player.weapons.values()) {
            if (isCompatiblePayload(carrier, payload) && payload.active
                    && payload.inStock > 0) {
                available.add(payload.definition.name);
            }
        }
        return Collections.unmodifiableList(available);
    }

    /** Anti-air and anti-rocket stocks available for a source defense action. */
    public List<String> getAvailableDefenseWeapons(int playerId) {
        PlayerState player = playerById(playerId);
        List<String> available = new ArrayList<>(2);
        addAvailableDefense(player, "antiAir", available);
        addAvailableDefense(player, "antiRocket", available);
        return Collections.unmodifiableList(available);
    }

    /** Best stocked counter for any enemy carrier currently in launch-ready state. */
    public String getCounterWeaponForPreparedThreat(int defenderId) {
        for (int attackerId : getPreparedThreatPlayerIds(defenderId)) {
            String counter = getCounterWeaponForThreat(defenderId, attackerId);
            if (counter != null) {
                return counter;
            }
        }
        return null;
    }

    /** Counter weapon for one specific prepared enemy, or null if no counter is ready. */
    public String getCounterWeaponForThreat(int defenderId, int attackerId) {
        if (attackerId < 0 || attackerId >= players.length) {
            return null;
        }
        PlayerState defender = playerById(defenderId);
        PlayerState attacker = players[attackerId];
        if (!attacker.alive || attacker.id == defenderId
                || defender.hasCeasefireWith(attacker.id)
                || attacker.preparedCarrierName == null) {
            return null;
        }
        WeaponState carrier = attacker.weapons.get(attacker.preparedCarrierName);
        if (carrier == null || carrier.definition.counteredBy.isEmpty()) {
            return null;
        }
        WeaponState counter = defender.weapons.get(carrier.definition.counteredBy);
        return counter != null && counter.active && counter.inStock > 0
                ? counter.definition.name : null;
    }

    /** Living, non-ceasefire opponents with a carrier currently prepared to launch. */
    public List<Integer> getPreparedThreatPlayerIds(int defenderId) {
        PlayerState defender = playerById(defenderId);
        List<Integer> threats = new ArrayList<>();
        for (PlayerState attacker : players) {
            if (attacker.alive && attacker.id != defenderId
                    && !defender.hasCeasefireWith(attacker.id)
                    && attacker.preparedCarrierName != null) {
                threats.add(attacker.id);
            }
        }
        return Collections.unmodifiableList(threats);
    }

    private static void addAvailableDefense(PlayerState player, String name,
                                            List<String> destination) {
        WeaponState weapon = player.weapons.get(name);
        if (weapon != null && weapon.active && weapon.inStock > 0) {
            destination.add(name);
        }
    }

    private String firstAvailableDefenseWeapon() {
        List<String> available = getAvailableDefenseWeapons(0);
        return available.isEmpty() ? "" : available.get(0);
    }

    /** Selects a carrier for the first half of a strike. */
    public boolean configurePreparedCarrier(String carrierName) {
        if (selectedAction != Action.PREPARED_ATTACK || getHuman().preparedCarrierName != null
                || !getAvailablePreparedCarriers(0).contains(carrierName)) {
            return false;
        }
        selectedPreparedCarrier = carrierName;
        selectedPreparedPayload = null;
        selectedShipStrikes.clear();
        return true;
    }

    /** Selects the target and payload for a previously prepared strike. */
    public boolean configurePreparedLaunch(int targetPlayerId, int cityIndex,
                                           String payloadName) {
        if (selectedAction != Action.PREPARED_ATTACK
                || getHuman().preparedCarrierName == null
                || targetPlayerId <= 0 || targetPlayerId >= players.length
                || cityIndex < 0 || cityIndex >= 5) {
            return false;
        }
        PlayerState target = players[targetPlayerId];
        CityState city = target.getCity(cityIndex);
        WeaponState carrier = getHuman().weapons.get(getHuman().preparedCarrierName);
        WeaponState payload = getHuman().weapons.get(payloadName);
        if (!target.alive || city == null || city.populationMillions <= 0
                || target.hasCeasefireWith(0)
                || carrier == null || "ship".equals(carrier.definition.type)
                || !isCompatiblePayload(carrier, payload) || !payload.active
                || payload.inStock <= 0) {
            return false;
        }
        selectedPreparedCarrier = getHuman().preparedCarrierName;
        selectedPreparedPayload = payloadName;
        selectedTargetPlayer = targetPlayerId;
        selectedTargetCity = cityIndex;
        return true;
    }

    /** Adds one same-nation payload/city pair for a prepared carrier ship. */
    public boolean configurePreparedShipStrike(int targetPlayerId, int cityIndex,
                                                String payloadName) {
        if (selectedAction != Action.PREPARED_ATTACK
                || getHuman().preparedCarrierName == null
                || targetPlayerId <= 0 || targetPlayerId >= players.length
                || cityIndex < 0 || cityIndex >= 5) {
            return false;
        }
        PlayerState target = players[targetPlayerId];
        CityState city = target.getCity(cityIndex);
        WeaponState carrier = getHuman().weapons.get(getHuman().preparedCarrierName);
        WeaponState payload = getHuman().weapons.get(payloadName);
        if (carrier == null || !"ship".equals(carrier.definition.type)
                || !target.alive || city == null || city.populationMillions <= 0
                || target.hasCeasefireWith(0)
                || !selectedShipStrikes.isEmpty()
                && selectedShipStrikes.get(0).targetPlayerId != targetPlayerId
                || !isCompatiblePayload(carrier, payload) || !payload.active
                || availableShipPayloadStock(payloadName,
                selectedShipStrikes.size() == 3) <= 0) {
            return false;
        }
        if (selectedShipStrikes.size() == 3) {
            selectedShipStrikes.remove(0);
        }
        selectedShipStrikes.add(new ShipStrike(targetPlayerId, cityIndex, payloadName));
        selectedPreparedCarrier = getHuman().preparedCarrierName;
        selectedPreparedPayload = null;
        selectedTargetPlayer = targetPlayerId;
        selectedTargetCity = cityIndex;
        return true;
    }

    private int availableShipPayloadStock(String payloadName, boolean replacingOldest) {
        WeaponState payload = getHuman().weapons.get(payloadName);
        if (payload == null) {
            return 0;
        }
        int selected = 0;
        for (ShipStrike strike : selectedShipStrikes) {
            if (strike.payloadName.equals(payloadName)) {
                selected++;
            }
        }
        if (replacingOldest && selectedShipStrikes.get(0).payloadName.equals(payloadName)) {
            selected--;
        }
        return payload.inStock - selected;
    }

    public int getSelectedShipStrikeCount() {
        return selectedShipStrikes.size();
    }

    public List<ShipStrike> getSelectedShipStrikes() {
        return Collections.unmodifiableList(selectedShipStrikes);
    }

    /** Chooses an anti-air or anti-rocket device for this turn's defense slot. */
    public boolean configureDefense(String weaponName) {
        if (selectedAction != Action.DEFENSE
                || !getAvailableDefenseWeapons(0).contains(weaponName)) {
            return false;
        }
        selectedDefenseWeapon = weaponName;
        return true;
    }

    public boolean canAcceptPreparedAttack() {
        if (selectedAction != Action.PREPARED_ATTACK) {
            return false;
        }
        if (getHuman().preparedCarrierName == null) {
            return selectedPreparedCarrier != null
                    && getAvailablePreparedCarriers(0).contains(selectedPreparedCarrier);
        }
        WeaponState carrier = getHuman().weapons.get(getHuman().preparedCarrierName);
        if (carrier != null && "ship".equals(carrier.definition.type)) {
            return canAcceptPreparedShipAttack();
        }
        PlayerState target = selectedTargetPlayer > 0 && selectedTargetPlayer < players.length
                ? players[selectedTargetPlayer] : null;
        CityState city = target == null ? null : target.getCity(selectedTargetCity);
        WeaponState payload = getHuman().weapons.get(selectedPreparedPayload);
        return target != null && target.alive && city != null && city.populationMillions > 0
                && !target.hasCeasefireWith(0)
                && isCompatiblePayload(carrier, payload) && payload.active && payload.inStock > 0;
    }

    private boolean canAcceptPreparedShipAttack() {
        if (selectedShipStrikes.size() != 3) {
            return false;
        }
        int targetId = selectedShipStrikes.get(0).targetPlayerId;
        PlayerState target = playerById(targetId);
        WeaponState carrier = getHuman().weapons.get(getHuman().preparedCarrierName);
        if (!target.alive || target.hasCeasefireWith(0) || carrier == null) {
            return false;
        }
        Map<String, Integer> selectedPayloads = new LinkedHashMap<>();
        for (ShipStrike strike : selectedShipStrikes) {
            CityState city = target.getCity(strike.cityIndex);
            WeaponState payload = getHuman().weapons.get(strike.payloadName);
            if (strike.targetPlayerId != targetId || city == null
                    || city.populationMillions <= 0
                    || !isCompatiblePayload(carrier, payload)
                    || !payload.active) {
                return false;
            }
            Integer count = selectedPayloads.get(strike.payloadName);
            selectedPayloads.put(strike.payloadName, count == null ? 1 : count + 1);
        }
        for (Map.Entry<String, Integer> selected : selectedPayloads.entrySet()) {
            if (getWeaponStock(0, selected.getKey()) < selected.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean canAcceptDefense() {
        return selectedAction == Action.DEFENSE && selectedDefenseWeapon != null
                && getAvailableDefenseWeapons(0).contains(selectedDefenseWeapon);
    }

    private static boolean isCarrier(WeaponState weapon) {
        if (weapon == null) {
            return false;
        }
        String type = weapon.definition.type;
        return "rocket".equals(type) || "plane".equals(type) || "sub".equals(type)
                || "ship".equals(type) || "space".equals(type);
    }

    private static boolean isCompatiblePayload(WeaponState carrier, WeaponState payload) {
        if (!isCarrier(carrier) || payload == null) {
            return false;
        }
        if ("space".equals(carrier.definition.type)) {
            return "specialLoad".equals(payload.definition.type);
        }
        String payloadType = payload.definition.type;
        return "rocketload".equals(payloadType)
                && payload.definition.level <= carrier.definition.level;
    }

    public void cycleAttackTarget(int direction) {
        if (direction == 0 || selectedAction != Action.ATTACK) {
            return;
        }
        for (int step = 1; step < players.length; step++) {
            int candidate = 1 + Math.floorMod(selectedTargetPlayer - 1
                    + Integer.signum(direction) * step, players.length - 1);
            PlayerState player = players[candidate];
            if (!player.alive) {
                continue;
            }
            int cityIndex = firstPopulatedCity(player, selectedTargetCity);
            if (cityIndex >= 0) {
                configureAttack(candidate, cityIndex, selectedAttackWeapon);
                return;
            }
        }
    }

    public void cycleAttackCity(int direction) {
        if (direction == 0 || selectedAction != Action.ATTACK || isSelectedAttackSpy()) {
            return;
        }
        PlayerState target = players[selectedTargetPlayer];
        for (int step = 1; step <= target.cities.size(); step++) {
            int cityIndex = Math.floorMod(selectedTargetCity
                    + Integer.signum(direction) * step, target.cities.size());
            CityState city = target.getCity(cityIndex);
            if (city.populationMillions > 0) {
                configureAttack(selectedTargetPlayer, cityIndex, selectedAttackWeapon);
                return;
            }
        }
    }

    public void cycleAttackWeapon(int direction) {
        if (direction == 0 || selectedAction != Action.ATTACK) {
            return;
        }
        List<String> available = getAvailableAttackWeapons();
        if (available.isEmpty()) {
            return;
        }
        int current = available.indexOf(selectedAttackWeapon);
        int next = Math.floorMod((current < 0 ? 0 : current)
                + Integer.signum(direction), available.size());
        configureAttack(selectedTargetPlayer, selectedTargetCity, available.get(next));
    }

    private static int firstPopulatedCity(PlayerState player, int preferredIndex) {
        for (int offset = 0; offset < player.cities.size(); offset++) {
            int index = Math.floorMod(preferredIndex + offset, player.cities.size());
            if (player.getCity(index).populationMillions > 0) {
                return index;
            }
        }
        return -1;
    }

    public boolean canAcceptAttack() {
        PlayerState target = selectedTargetPlayer > 0 && selectedTargetPlayer < players.length
                ? players[selectedTargetPlayer] : null;
        CityState city = target == null ? null : target.getCity(selectedTargetCity);
        WeaponState weapon = getHuman().weapons.get(selectedAttackWeapon);
        return target != null && target.alive && totalPopulation(target) > 0 && city != null
                && weapon != null && weapon.active && weapon.inStock > 0
                && ("soldier".equals(weapon.definition.type)
                ? city.populationMillions > 0 && !target.hasCeasefireWith(getHuman().id)
                : canUseSpy(getHuman(), weapon));
    }

    private String firstAvailableAttackWeapon() {
        List<String> available = getAvailableAttackWeapons();
        return available.isEmpty() ? "" : available.get(0);
    }

    public int getCityPopulationMillions(int playerId, int cityIndex) {
        CityState city = playerById(playerId).getCity(cityIndex);
        if (city == null) {
            throw new IllegalArgumentException("Invalid city index");
        }
        return city.populationMillions;
    }

    public String getSelectedProductionCategory() {
        return selectedProductionCategory;
    }

    public int getSelectedProductionPercent() {
        return selectedProductionPercent;
    }

    public int getSelectedProductionBudgetMillions() {
        return getHuman().turnMoneyMillions * selectedProductionPercent / 100;
    }

    public int getTurnMoneyMillions() {
        return getHuman().turnMoneyMillions;
    }

    public boolean acceptSelectedAction() {
        if (selectedAction == null || queuedActions.size() >= MAX_TURN_ACTIONS) {
            return false;
        }
        if (isActionBioloaded(0, selectedAction)) {
            messages.add(selectedAction.label + " is unavailable due to biological contamination.");
            return false;
        }
        int reserved = 0;
        if (selectedAction == Action.RESEARCH) {
            if (selectedTechnology == null || !isResearchAvailable(selectedTechnology)) {
                messages.add("Choose an available technology to research.");
                return false;
            }
            reserved = getTechnologyCost(selectedTechnology);
        } else if (selectedAction == Action.PRODUCTION) {
            if (selectedProductionPercent > 0) {
                reserved = getSelectedProductionBudgetMillions();
            }
        } else if (selectedAction == Action.ATTACK && !canAcceptAttack()) {
            messages.add("Choose a living target city and an available unit.");
            return false;
        } else if (selectedAction == Action.PREPARED_ATTACK
                && !canAcceptPreparedAttack()) {
            messages.add(getHuman().preparedCarrierName == null
                    ? "Choose an available carrier to prepare."
                    : "Choose a living target city and compatible payload.");
            return false;
        } else if (selectedAction == Action.DEFENSE && !canAcceptDefense()) {
            messages.add("Choose available anti-air or anti-rocket defense stock.");
            return false;
        } else if (selectedAction == Action.PROPAGANDA && !canAcceptPropaganda()) {
            messages.add("Choose a living opponent and a populated city.");
            return false;
        } else if (selectedAction == Action.DIPLOMACY && !canAcceptDiplomacy()) {
            messages.add("Choose a living nation and an affordable diplomacy option.");
            return false;
        } else if (selectedAction == Action.DIPLOMACY
                && selectedDiplomacyOption < 3) {
            reserved = DIPLOMACY_AMOUNT[selectedDiplomacyOption];
        }
        if (reserved > getHuman().turnMoneyMillions) {
            messages.add("The selected action exceeds the remaining budget.");
            return false;
        }
        Map<String, Integer> productionBudgets = new LinkedHashMap<>();
        if (selectedAction == Action.PRODUCTION && reserved > 0) {
            productionBudgets.put(selectedProductionCategory, reserved);
        }
        List<String> technologies = new ArrayList<>();
        if (selectedAction == Action.RESEARCH && selectedTechnology != null) {
            technologies.add(selectedTechnology);
        }
        ActionOrder order = new ActionOrder(0, selectedAction, productionBudgets,
                technologies, reserved,
                selectedTargetPlayer, selectedTargetCity,
                selectedAction == Action.PREPARED_ATTACK
                        ? selectedPreparedCarrier : (selectedAction == Action.DEFENSE
                        ? selectedDefenseWeapon : selectedAttackWeapon),
                selectedPreparedPayload,
                selectedAction == Action.PREPARED_ATTACK
                        && getHuman().preparedCarrierName == null,
                selectedSpyBuilding, selectedDiplomacyOption, selectedShipStrikes);
        queuedActions.add(order);
        getHuman().turnMoneyMillions -= reserved;
        messages.add(selectedAction.label + " queued.");
        selectedAction = null;
        selectedTechnology = null;
        selectedTargetPlayer = selectedIsland > 0 ? selectedIsland : 1;
        selectedTargetCity = 0;
        selectedAttackWeapon = firstAvailableAttackWeapon();
        selectedPreparedCarrier = null;
        selectedPreparedPayload = null;
        selectedShipStrikes.clear();
        selectedDefenseWeapon = firstAvailableDefenseWeapon();
        selectedDiplomacyOption = 0;
        return true;
    }

    public void cancelSelection() {
        selectedAction = null;
        selectedTechnology = null;
        selectedProductionPercent = 0;
        selectedProductionCategory = "soldier";
        selectedDiplomacyOption = 0;
        selectedPreparedCarrier = null;
        selectedPreparedPayload = null;
        selectedShipStrikes.clear();
        selectedDefenseWeapon = null;
    }

    public void removeQueuedAction(int slot) {
        if (slot >= 0 && slot < queuedActions.size()) {
            ActionOrder removed = queuedActions.remove(slot);
            getHuman().turnMoneyMillions = Math.min(getHuman().moneyMillions,
                    getHuman().turnMoneyMillions + removed.reservedMillions);
            messages.add(removed.action.label + " removed.");
        }
    }

    public void clearQueuedActions() {
        queuedActions.clear();
        getHuman().turnMoneyMillions = getHuman().moneyMillions;
        cancelSelection();
    }

    public int getQueuedActionCount() {
        return queuedActions.size();
    }

    public Action getQueuedAction(int slot) {
        return slot >= 0 && slot < queuedActions.size()
                ? queuedActions.get(slot).action : null;
    }

    public int getResourceIncomeMillions() {
        return resourceIncome(players[0]);
    }

    public int getResourceIncomeMillions(int playerId) {
        if (playerId < 0 || playerId >= players.length) {
            throw new IllegalArgumentException("Invalid player index");
        }
        return resourceIncome(players[playerId]);
    }

    private int resourceIncome(PlayerState player) {
        int[] divisors = player.human ? PLAYER_RESOURCE_DIVISOR : ENEMY_RESOURCE_DIVISOR;
        return Math.round((float) player.populationMillions / divisors[difficulty]);
    }

    public int getMassMediaGain() {
        return PLAYER_RELIGION_ADDITION[difficulty];
    }

    public int getMassMediaGain(int playerId) {
        if (playerId < 0 || playerId >= players.length) {
            throw new IllegalArgumentException("Invalid player index");
        }
        return (players[playerId].human ? PLAYER_RELIGION_ADDITION : ENEMY_RELIGION_ADDITION)
                [difficulty];
    }

    public int getWeaponStock(int playerId, String name) {
        PlayerState player = playerById(playerId);
        WeaponState weapon = player.weapons.get(name);
        return weapon == null ? 0 : weapon.inStock;
    }

    public boolean isWeaponActive(int playerId, String name) {
        PlayerState player = playerById(playerId);
        WeaponState weapon = player.weapons.get(name);
        return weapon != null && weapon.active;
    }

    public int getWeaponStock(String name) {
        return getWeaponStock(0, name);
    }

    private PlayerState playerById(int playerId) {
        if (playerId < 0 || playerId >= players.length) {
            throw new IllegalArgumentException("Invalid player index");
        }
        return players[playerId];
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public String getLatestMessage() {
        return messages.isEmpty() ? "Choose two actions, then confirm."
                : messages.get(messages.size() - 1);
    }

    /** Resolves human and opponent actions using the source turn order. */
    public void confirmTurn() {
        if (queuedActions.size() != MAX_TURN_ACTIONS) {
            throw new IllegalStateException("Choose two actions before confirming the turn");
        }
        for (PlayerState player : players) {
            player.lastTurnActions.clear();
        }
        tickDiplomacyOfferCooldowns();
        decayReligion();
        advanceSpyReadiness();
        resolvePendingAiDiplomacy();
        List<ActionOrder> turnOrders = new ArrayList<>(players.length * MAX_TURN_ACTIONS);
        for (PlayerState player : players) {
            if (player.human) {
                turnOrders.addAll(queuedActions);
                for (ActionOrder order : queuedActions) {
                    player.lastTurnActions.add(order.action);
                }
                continue;
            }
            if (!opponentTurnsEnabled || !player.alive) {
                continue;
            }
            player.turnMoneyMillions = player.moneyMillions;
            if (player.specialPerson) {
                player.moneyMillions += 3;
                player.turnMoneyMillions += 3;
            }
            List<OpponentAI.Decision> decisions = OpponentAI.plan(this, player, random);
            for (OpponentAI.Decision decision : decisions) {
                turnOrders.add(new ActionOrder(player.id, decision.action,
                        decision.productionBudgets, decision.technologies,
                        decision.reservedMillions, decision.targetPlayerId,
                        decision.targetCityIndex,
                        decision.weaponName,
                        decision.payloadName, decision.prepareCarrier, "spyCenter",
                        decision.diplomacyOption, decision.shipStrikes));
                player.lastTurnActions.add(decision.action);
            }
        }
        clearPreviousBiologicalEffects();
        List<ActionOrder> resolvedDefenses = new ArrayList<>();
        List<BiologicalEffect> biologicalEffects = new ArrayList<>();
        for (ActionOrder order : turnOrders) {
            if (order.action == Action.DEFENSE) {
                resolveDefense(order, resolvedDefenses);
            }
        }
        for (ActionOrder order : turnOrders) {
            PlayerState actor = playerById(order.playerId);
            if (!actor.alive) {
                continue;
            }
            switch (order.action) {
                case RESOURCES:
                    resolveResources(actor);
                    break;
                case MASS_MEDIA:
                    resolveMassMedia(actor);
                    break;
                case PRODUCTION:
                    resolveProduction(order);
                    break;
                case RESEARCH:
                    resolveResearch(order);
                    break;
                case ATTACK:
                    resolveAttack(order, turnOrders);
                    break;
                case PREPARED_ATTACK:
                    resolvePreparedAttack(order, resolvedDefenses, biologicalEffects);
                    break;
                case DEFENSE:
                    break;
                case PROPAGANDA:
                    resolvePropaganda(order);
                    break;
                case DIPLOMACY:
                    resolveDiplomacy(order);
                    break;
                default:
                    throw new IllegalStateException("Unported action entered the turn queue: "
                            + order.action);
            }
        }
        applyBiologicalEffects(biologicalEffects);
        clearExpiredPreparedAttacks(turnOrders);
        for (PlayerState player : players) {
            player.religion = Math.min(100, Math.max(0, player.religion));
            player.turnMoneyMillions = player.moneyMillions;
        }
        updateDiplomacyRelations();
        tickCeasefires();
        refreshMatchStatus();
        reassignDeadAiEnemies();
        turn++;
        getHuman().turnMoneyMillions = getHuman().moneyMillions;
        messages.add("Turn " + turn + " complete.");
        queuedActions.clear();
        cancelSelection();
        trimMessages();
    }

    private void resolveDiplomacy(ActionOrder order) {
        PlayerState proposer = playerById(order.playerId);
        if (!submitDiplomacyProposal(proposer.id, order.targetPlayerId,
                order.diplomacyOption)) {
            messages.add("Diplomacy proposal could not be sent.");
            return;
        }
        String type = order.diplomacyOption < 3 ? "offer" : "demand";
        messages.add("Nation " + proposer.id + " sent a diplomacy " + type
                + " to nation " + order.targetPlayerId
                + " for " + DIPLOMACY_DURATION[order.diplomacyOption] + " turns.");
    }

    private void resolvePendingAiDiplomacy() {
        for (int targetId = 1; targetId < players.length; targetId++) {
            PlayerState target = players[targetId];
            if (!target.alive) {
                continue;
            }
            for (int proposerId = 0; proposerId < players.length; proposerId++) {
                DiplomacyProposal proposal = target.pendingDiplomacy[proposerId];
                if (proposal != null) {
                    boolean accept = shouldAiAccept(target, proposal);
                    resolveDiplomacyResponse(target, proposal, accept);
                }
            }
        }
    }

    private boolean shouldAiAccept(PlayerState receiver, DiplomacyProposal proposal) {
        PlayerState proposer = players[proposal.proposerId];
        int optionIndex = proposal.optionIndex;
        int accept = 2;
        int reject = 0;
        if (optionIndex < 3) {
            if (receiver.getRegardTowardExact(proposer.id) > 50) {
                accept++;
            } else {
                reject++;
            }
            accept += optionIndex * 4;
            if (strength(receiver) < strength(proposer)) {
                accept++;
            } else {
                reject++;
            }
            if (proposer.populationMillions > receiver.populationMillions) {
                accept++;
            }
            if (receiver.moneyMillions < 30) {
                accept++;
            }
        } else {
            if (receiver.getRegardTowardExact(proposer.id) > 50) {
                accept++;
            } else {
                reject++;
            }
            reject += Math.round(optionIndex / 2f);
            if (strength(receiver) < strength(proposer)) {
                accept++;
            } else {
                reject++;
            }
            if (strength(receiver) < strength(proposer) / 2f) {
                accept += 2;
            } else {
                reject++;
            }
            if (proposer.populationMillions > receiver.populationMillions) {
                accept++;
            } else {
                reject++;
            }
        }
        return accept > reject && (optionIndex < 3
                || receiver.turnMoneyMillions >= DIPLOMACY_AMOUNT[optionIndex]);
    }

    private int strength(PlayerState player) {
        return player.getStrength();
    }

    private void resolveDiplomacyResponse(PlayerState target, DiplomacyProposal proposal,
                                          boolean accepted) {
        int proposerId = proposal.proposerId;
        PlayerState proposer = players[proposerId];
        int optionIndex = proposal.optionIndex;
        int amount = DIPLOMACY_AMOUNT[optionIndex];
        if (accepted && optionIndex >= 3 && target.moneyMillions < amount) {
            accepted = false;
        }
        target.pendingDiplomacy[proposerId] = null;
        if (accepted) {
            target.regardToward[proposerId] += DIPLOMACY_BONUS;
            proposer.regardToward[target.id] += DIPLOMACY_BONUS;
            if (optionIndex < 3) {
                target.moneyMillions += amount;
                target.turnMoneyMillions += amount;
            } else {
                proposer.moneyMillions += amount;
                proposer.turnMoneyMillions += amount;
                target.moneyMillions -= amount;
                target.turnMoneyMillions = Math.max(0, target.turnMoneyMillions - amount);
            }
            int duration = DIPLOMACY_DURATION[optionIndex];
            target.ceasefireTurns[proposerId] = duration;
            proposer.ceasefireTurns[target.id] = duration;
            messages.add("Nation " + target.id + " accepted diplomacy with nation "
                    + proposer.id + ".");
        } else {
            target.regardToward[proposerId] -= DIPLOMACY_BONUS;
            proposer.regardToward[target.id] -= DIPLOMACY_BONUS;
            if (optionIndex < 3) {
                proposer.moneyMillions += amount;
                proposer.turnMoneyMillions += amount;
            }
            messages.add("Nation " + target.id + " rejected diplomacy with nation "
                    + proposer.id + ".");
        }
    }

    private static void applyDiplomacySentRegard(PlayerState proposer, PlayerState target,
                                                  int optionIndex) {
        int adjustment = optionIndex < 3 ? optionIndex * 5 : -optionIndex;
        proposer.regardToward[target.id] += adjustment;
        target.regardToward[proposer.id] += adjustment;
    }

    /** Keeps the source's greatest-casualty attacker attribution for each flag. */
    private static void recordLargestBattle(PlayerState attacker, PlayerState defender,
                                            int casualties) {
        if (casualties > defender.largestBattleCasualties) {
            defender.largestBattleCasualties = casualties;
            defender.lastAttackedByPlayerId = attacker.id;
        }
    }

    void applyBattleHostilityEffects(PlayerState attacker, PlayerState defender,
                                     int casualties) {
        attacker.regardToward[defender.id] -= casualties;
        defender.regardToward[attacker.id] -= casualties;

        if (defender.specialPerson && !attacker.human) {
            for (PlayerState player : players) {
                if (player.alive) {
                    player.regardToward[attacker.id] -= 7;
                }
            }
        }

        double thirdPartyAdjustment = casualties / 20.0d;
        for (PlayerState observer : players) {
            if (!observer.alive || observer == attacker || observer == defender
                    || !observer.hasCeasefireWith(attacker.id)) {
                continue;
            }
            if (observer.regardToward[defender.id] > observer.regardToward[attacker.id]) {
                observer.regardToward[attacker.id] -= thirdPartyAdjustment;
            } else {
                observer.regardToward[attacker.id] += thirdPartyAdjustment;
            }
        }
    }

    private void applyPropagandaHostilityEffects(PlayerState attacker, PlayerState defender) {
        int regardLoss = random.nextInt(6) + 5;
        attacker.regardToward[defender.id] -= regardLoss;
        defender.regardToward[attacker.id] -= regardLoss;

        double thirdPartyAdjustment = regardLoss / 5.0d;
        for (PlayerState observer : players) {
            if (!observer.alive || observer == attacker || observer == defender) {
                continue;
            }
            if (observer.regardToward[attacker.id] > observer.regardToward[defender.id]) {
                observer.regardToward[attacker.id] += thirdPartyAdjustment;
            } else {
                observer.regardToward[attacker.id] -= thirdPartyAdjustment;
            }
        }
    }

    private void updateDiplomacyRelations() {
        for (PlayerState player : players) {
            if (!player.alive) {
                continue;
            }
            for (int otherId = 0; otherId < players.length; otherId++) {
                double regard = player.regardToward[otherId];
                if (player.ceasefireTurns[otherId] > 0) {
                    regard += 2;
                }
                if (regard < 100) {
                    regard += difficulty == 0 ? 3 : difficulty == 1 ? 2 : 1;
                }
                if (regard < 50) {
                    regard += difficulty == 0 ? 2 : 1;
                }
                if (regard < 30) {
                    regard++;
                }
                player.regardToward[otherId] = Math.min(100, regard);
            }
            if (!player.human) {
                for (int otherId = 1; otherId < players.length; otherId++) {
                    player.regardToward[otherId] += difficulty;
                }
            }
        }
    }

    private void tickDiplomacyOfferCooldowns() {
        for (PlayerState player : players) {
            for (int otherId = 0; otherId < players.length; otherId++) {
                if (player.diplomacyOfferedLastTurn[otherId] > 0) {
                    player.diplomacyOfferedLastTurn[otherId]--;
                }
            }
        }
    }

    private void tickCeasefires() {
        for (PlayerState player : players) {
            for (int otherId = 0; otherId < players.length; otherId++) {
                if (player.ceasefireTurns[otherId] > 0) {
                    player.ceasefireTurns[otherId]--;
                }
            }
        }
    }

    private void decayReligion() {
        for (PlayerState player : players) {
            if (player.religion > 11) {
                player.religion -= 5 + random.nextInt(6);
            }
        }
    }

    private void resolveResources(PlayerState player) {
        int income = resourceIncome(player);
        player.moneyMillions += income;
        player.turnMoneyMillions += income;
        addToHighscore(player, 5, income);
        messages.add("Nation " + player.id + " gained $ " + income
                + ".000.000 from resources.");
    }

    private void resolveMassMedia(PlayerState player) {
        int gain = getMassMediaGain(player.id);
        player.religion += gain;
        addToHighscore(player, 6, gain);
        messages.add("Nation " + player.id + " increased influence by "
                + gain + ".");
    }

    private void resolveResearch(ActionOrder order) {
        PlayerState researcher = playerById(order.playerId);
        List<String> invented = new ArrayList<>();
        for (String technologyName : order.technologies) {
            TechnologyCatalog.Definition technology = TechnologyCatalog.byName(technologyName);
            if (technology == null || researcher.hasTechnology(technology.name)
                    || (technology.group == 7 && !researcher.allowsSpecialTechnologies())
                    || researcher.moneyMillions < technology.costMillions
                    || (technology.prerequisite != null
                    && !researcher.hasTechnology(technology.prerequisite))) {
                continue;
            }
            researcher.inventedTechnologies.add(technology.name);
            WeaponState weapon = researcher.weapons.get(technology.name);
            if (weapon != null) {
                weapon.active = true;
            }
            researcher.moneyMillions -= technology.costMillions;
            addToHighscore(researcher, 4, technology.level);
            invented.add(technology.name.toUpperCase(java.util.Locale.US));
        }
        if (invented.isEmpty()) {
            messages.add("Nation " + researcher.id + " could not complete research.");
        } else {
            messages.add("Nation " + researcher.id + " invented technologies: "
                    + String.join(", ", invented) + ".");
        }
    }

    /** Source attack casualties are a rounded random 50%-100% of unit maxKills. */
    private void resolveAttack(ActionOrder order, List<ActionOrder> turnOrders) {
        PlayerState attacker = playerById(order.playerId);
        PlayerState defender = playerById(order.targetPlayerId);
        CityState city = defender.getCity(order.targetCityIndex);
        WeaponState weapon = attacker.weapons.get(order.weaponName);
        boolean spyMission = weapon != null && "spy".equals(weapon.definition.type);
        if (!defender.alive || city == null
                || (!spyMission && city.populationMillions <= 0)
                || weapon == null || !weapon.active || weapon.inStock <= 0
                || (spyMission && !canUseSpy(attacker, weapon))) {
            messages.add("Attack cancelled: target or weapon is no longer available.");
            return;
        }
        if (spyMission) {
            resolveSpyAttack(attacker, defender, weapon, turnOrders);
            weapon.inStock--;
            messages.add("Spy operation completed against nation " + defender.id + ".");
            return;
        }
        if (defender.hasCeasefireWith(attacker.id)) {
            messages.add("Attack cancelled: a ceasefire is active with nation "
                    + defender.id + ".");
            return;
        }
        int casualties = 0;
        if ("soldier".equals(weapon.definition.type)) {
            float minimum = weapon.definition.maxKillsMillions / 2f;
            float damage = minimum + random.nextFloat() * minimum;
            casualties = Math.min(city.populationMillions, Math.round(damage));
            city.populationMillions -= casualties;
            defender.populationMillions = totalPopulation(defender);
            recordLargestBattle(attacker, defender, casualties);
            applyBattleHostilityEffects(attacker, defender, casualties);
            if (defender.populationMillions <= 0) {
                defender.alive = false;
                messages.add("Nation " + defender.id + " has been eliminated.");
            }
            if (casualties > 0) {
                addToHighscore(attacker, 1, casualties);
            }
        } else {
            throw new IllegalStateException("Direct attack only supports soldier units");
        }
        weapon.inStock--;
        messages.add(casualties + " citizens lost in nation " + defender.id
                + " city " + (city.index + 1) + ".");
    }

    private void resolveDefense(ActionOrder order, List<ActionOrder> resolvedDefenses) {
        PlayerState defender = playerById(order.playerId);
        WeaponState counter = defender.weapons.get(order.weaponName);
        if (counter == null || !counter.active || counter.inStock <= 0
                || !"anti".equals(counter.definition.type)) {
            messages.add("Defense cancelled: counter stock is no longer available.");
            return;
        }
        counter.inStock--;
        resolvedDefenses.add(order);
        messages.add("Nation " + defender.id + " used " + order.weaponName + " defense.");
    }

    private void resolvePreparedAttack(ActionOrder order,
                                       List<ActionOrder> resolvedDefenses,
                                       List<BiologicalEffect> biologicalEffects) {
        PlayerState attacker = playerById(order.playerId);
        if (order.prepareCarrier) {
            WeaponState carrier = attacker.weapons.get(order.weaponName);
            if (attacker.preparedCarrierName != null || !isCarrier(carrier)
                    || !carrier.active || carrier.inStock <= 0
                    || getAvailablePreparedPayloads(attacker.id, order.weaponName).isEmpty()) {
                messages.add("Strike preparation cancelled: carrier or payload is unavailable.");
                return;
            }
            carrier.inStock--;
            attacker.preparedCarrierName = order.weaponName;
            messages.add("Nation " + attacker.id + " prepared " + order.weaponName + ".");
            return;
        }

        PlayerState defender = playerById(order.targetPlayerId);
        WeaponState carrier = attacker.weapons.get(order.weaponName);
        if (carrier != null && "ship".equals(carrier.definition.type)) {
            resolveShipPreparedAttack(order, attacker, defender, carrier,
                    resolvedDefenses, biologicalEffects);
            return;
        }
        CityState city = defender.getCity(order.targetCityIndex);
        WeaponState payload = attacker.weapons.get(order.payloadName);
        if (!order.weaponName.equals(attacker.preparedCarrierName)
                || !defender.alive || city == null || city.populationMillions <= 0
                || defender.hasCeasefireWith(attacker.id)
                || !isCompatiblePayload(carrier, payload) || !payload.active
                || payload.inStock <= 0) {
            messages.add("Prepared strike cancelled: target or payload is unavailable.");
            return;
        }

        attacker.preparedCarrierName = null;
        payload.inStock--;
        if ("space".equals(carrier.definition.type)) {
            attacker.satellitesUsed++;
        }
        int rolledCasualties = sourceStrikeCasualties(payload.definition.maxKillsMillions,
                attacker.human, defender.human);
        boolean intercepted = false;
        String counterName = carrier.definition.counteredBy;
        if (!counterName.isEmpty()) {
            for (ActionOrder defense : resolvedDefenses) {
                if (defense.playerId == defender.id && counterName.equals(defense.weaponName)) {
                    intercepted = counterIntercepts(random);
                    break;
                }
            }
        }
        if (intercepted) {
            messages.add("Nation " + defender.id + " intercepted the prepared strike.");
            return;
        }

        if (order.payloadName.startsWith("bioload")) {
            int bioLevel = order.payloadName.charAt(order.payloadName.length() - 1) - '0';
            biologicalEffects.add(createBiologicalEffect(defender, city, bioLevel));
        }
        int casualties = Math.min(rolledCasualties, city.populationMillions);
        city.populationMillions -= casualties;
        defender.populationMillions = totalPopulation(defender);
        recordLargestBattle(attacker, defender, casualties);
        applyBattleHostilityEffects(attacker, defender, casualties);
        if (defender.populationMillions <= 0) {
            defender.alive = false;
            messages.add("Nation " + defender.id + " has been eliminated.");
        }
        if (casualties > 0) {
            addToHighscore(attacker, 1, casualties);
        }
        messages.add(order.payloadName + " strike caused " + casualties
                + " million casualties in nation " + defender.id + " city "
                + (city.index + 1) + ".");
    }

    private void resolveShipPreparedAttack(ActionOrder order, PlayerState attacker,
                                          PlayerState defender, WeaponState carrier,
                                          List<ActionOrder> resolvedDefenses,
                                          List<BiologicalEffect> biologicalEffects) {
        if (!order.weaponName.equals(attacker.preparedCarrierName)
                || !defender.alive || defender.hasCeasefireWith(attacker.id)
                || order.shipStrikes.size() != 3) {
            messages.add("Ship strike cancelled: target or payload sequence is unavailable.");
            return;
        }
        Map<String, Integer> payloadUse = new LinkedHashMap<>();
        for (ShipStrike strike : order.shipStrikes) {
            CityState city = defender.getCity(strike.cityIndex);
            WeaponState payload = attacker.weapons.get(strike.payloadName);
            if (strike.targetPlayerId != defender.id || city == null
                    || city.populationMillions <= 0
                    || !isCompatiblePayload(carrier, payload) || !payload.active) {
                messages.add("Ship strike cancelled: target or payload sequence is unavailable.");
                return;
            }
            Integer count = payloadUse.get(strike.payloadName);
            payloadUse.put(strike.payloadName, count == null ? 1 : count + 1);
        }
        for (Map.Entry<String, Integer> used : payloadUse.entrySet()) {
            WeaponState payload = attacker.weapons.get(used.getKey());
            if (payload.inStock < used.getValue()) {
                messages.add("Ship strike cancelled: selected payload stock is unavailable.");
                return;
            }
        }

        String counterName = carrier.definition.counteredBy;
        boolean hasMatchingDefense = false;
        for (ActionOrder defense : resolvedDefenses) {
            if (defense.playerId == defender.id && counterName.equals(defense.weaponName)) {
                hasMatchingDefense = true;
                break;
            }
        }
        List<ShipStrikeResult> results = new ArrayList<>(3);
        for (ShipStrike strike : order.shipStrikes) {
            WeaponState payload = attacker.weapons.get(strike.payloadName);
            int rolledCasualties = sourceShipStrikeCasualties(
                    payload.definition.maxKillsMillions, random);
            boolean counterRollIntercepts = shipCounterRollIntercepts(random);
            boolean intercepted = hasMatchingDefense && counterRollIntercepts;
            if (!intercepted && payload.definition.name.startsWith("bioload")) {
                int level = payload.definition.name.charAt(
                        payload.definition.name.length() - 1) - '0';
                biologicalEffects.add(createBiologicalEffect(defender,
                        defender.getCity(strike.cityIndex), level));
            }
            results.add(new ShipStrikeResult(strike, payload.definition.name,
                    rolledCasualties, intercepted));
        }

        attacker.preparedCarrierName = null;
        for (ShipStrike strike : order.shipStrikes) {
            attacker.weapons.get(strike.payloadName).inStock--;
        }
        int totalCasualties = 0;
        for (ShipStrikeResult result : results) {
            ShipStrike strike = result.strike;
            CityState city = defender.getCity(strike.cityIndex);
            int casualties;
            if (result.intercepted) {
                casualties = 0;
                messages.add("Nation " + defender.id + " intercepted one ship payload.");
            } else {
                casualties = Math.min(result.rolledCasualties, city.populationMillions);
                city.populationMillions -= casualties;
                totalCasualties += casualties;
                recordLargestBattle(attacker, defender, casualties);
                if (casualties > 0) {
                    addToHighscore(attacker, 1, casualties);
                }
            }
            messages.add(result.payloadName + " ship payload caused " + casualties
                    + " million casualties in nation " + defender.id + " city "
                    + (city.index + 1) + ".");
        }
        applyBattleHostilityEffects(attacker, defender, totalCasualties);
        defender.populationMillions = totalPopulation(defender);
        if (defender.populationMillions <= 0) {
            defender.alive = false;
            messages.add("Nation " + defender.id + " has been eliminated.");
        }
    }

    /** Source ship counter roll uses randRange(1, 3) == 2: one of three strikes. */
    static boolean shipCounterRollIntercepts(Random random) {
        return random.nextInt(3) + 1 == 2;
    }

    /** Source Tools.randRange(maxKills/2, maxKills), followed by Math.round. */
    static int sourceShipStrikeCasualties(int maximum, Random random) {
        float minimum = maximum / 2f;
        int offset = (int) Math.floor(random.nextFloat() * (maximum - minimum + 1f));
        return Math.round(minimum + offset);
    }

    /** Source Tools.randRange(1, 9) != 5: eight outcomes intercept. */
    static boolean counterIntercepts(Random random) {
        return random.nextInt(9) + 1 != 5;
    }

    /** Mirrors Flash's inclusive integer randRange followed by Math.round. */
    static int sourceStrikeCasualties(int maximum, boolean attackerIsHuman,
                                     boolean targetIsHuman, Random random) {
        boolean aiAttackingHuman = targetIsHuman && !attackerIsHuman;
        float minimum = aiAttackingHuman ? maximum : maximum / 1.5f;
        float upper = aiAttackingHuman ? maximum * 1.2f : maximum;
        int offset = (int) Math.floor(random.nextFloat() * (upper - minimum + 1f));
        return Math.round(minimum + offset);
    }

    private int sourceStrikeCasualties(int maximum, boolean attackerIsHuman,
                                       boolean targetIsHuman) {
        return sourceStrikeCasualties(maximum, attackerIsHuman, targetIsHuman, random);
    }

    private BiologicalEffect createBiologicalEffect(PlayerState target, CityState city,
                                                    int level) {
        if (level < 1 || level >= BIO_DISABLED_STOCK_ATTEMPTS.length) {
            level = 1;
        }
        BiologicalEffect effect = new BiologicalEffect(target.id, city.index);
        for (int i = 0; i < level; i++) {
            effect.disabledActions.add(BIO_DISABLED_ACTIONS[random.nextInt(
                    BIO_DISABLED_ACTIONS.length)]);
        }

        // Sub_model.createBioHazardArr samples the local human's 26-entry weaponArr;
        // Presenter then applies those IDs to the contaminated target's stock.
        List<WeaponState> sourceWeaponArray = new ArrayList<>(26);
        for (WeaponState weapon : getHuman().weapons.values()) {
            if (weapon.definition.productionGroup != 8) {
                sourceWeaponArray.add(weapon);
            }
        }
        int attempts = BIO_DISABLED_STOCK_ATTEMPTS[level];
        for (int i = 0; i < attempts && !sourceWeaponArray.isEmpty(); i++) {
            WeaponState weapon = sourceWeaponArray.get(random.nextInt(sourceWeaponArray.size()));
            if (weapon.active) {
                effect.disabledWeapons.add(weapon.definition.name);
            }
        }
        return effect;
    }

    private void applyBiologicalEffects(List<BiologicalEffect> effects) {
        for (BiologicalEffect effect : effects) {
            PlayerState target = playerById(effect.targetPlayerId);
            CityState city = target.getCity(effect.cityIndex);
            target.bioloaded = true;
            target.bioloadedActions.addAll(effect.disabledActions);
            for (String weaponName : effect.disabledWeapons) {
                WeaponState weapon = target.weapons.get(weaponName);
                if (weapon != null) {
                    weapon.active = false;
                    weapon.bioloaded = true;
                }
            }
            if (city != null) {
                city.bioloaded = true;
            }
            messages.add("Biological agents contaminated nation " + target.id
                    + " after a bioload strike.");
        }
    }

    /** The source clears last turn's temporary action/weapon contamination before resolving. */
    private void clearPreviousBiologicalEffects() {
        for (PlayerState player : players) {
            player.bioloadedActions.clear();
            player.bioloaded = false;
            for (WeaponState weapon : player.weapons.values()) {
                if (weapon.bioloaded) {
                    weapon.bioloaded = false;
                    weapon.active = true;
                }
            }
            for (CityState city : player.cities) {
                city.bioloaded = false;
            }
        }
    }

    private void clearExpiredPreparedAttacks(List<ActionOrder> turnOrders) {
        for (PlayerState player : players) {
            if (player.preparedCarrierName == null) {
                continue;
            }
            boolean usedPreparedAttackAction = false;
            for (ActionOrder order : turnOrders) {
                if (order.playerId == player.id && order.action == Action.PREPARED_ATTACK) {
                    usedPreparedAttackAction = true;
                    break;
                }
            }
            if (!usedPreparedAttackAction) {
                player.preparedCarrierName = null;
                messages.add("Nation " + player.id + " cancelled its unused prepared strike.");
            }
        }
    }

    private void resolveSpyAttack(PlayerState attacker, PlayerState defender,
                                  WeaponState spy, List<ActionOrder> turnOrders) {
        int level = spy.definition.level;
        int[] statistics = SPY_STATISTICS[level - 1];
        int weaponCount = 1 + random.nextInt(statistics[0]);
        int moneyPercent = level * 10
                + random.nextInt(statistics[1] - level * 10 + 1);
        long moneyAmount = Math.round((defender.moneyMillions / 100.0d) * moneyPercent);
        moneyAmount = Math.min(moneyAmount, SPY_MONEY_CAP[difficulty]);
        if (defender.specialPerson) {
            weaponCount = 1;
            moneyAmount = Math.min(moneyAmount, 20L);
        }

        List<String> availableSpyActions = new ArrayList<>();
        Collections.addAll(availableSpyActions, SPY_ACTION_CATEGORIES);
        for (ActionOrder planned : turnOrders) {
            if (planned.playerId == attacker.id) {
                String excluded = spyActionCategory(planned.action);
                if (excluded != null) {
                    availableSpyActions.remove(excluded);
                }
            }
        }
        if (!availableSpyActions.isEmpty()) {
            // The recovered model chooses a spy objective here; it controls the
            // mission report while the stolen technologies/stock are resolved below.
            random.nextInt(availableSpyActions.size());
        }

        List<WeaponState> availableTargets = new ArrayList<>();
        for (WeaponState targetWeapon : defender.weapons.values()) {
            int maximumTargetLevel = defender.specialPerson ? 1 : level;
            if (targetWeapon.inStock > 0 && targetWeapon.definition.level <= maximumTargetLevel
                    && !"spy".equals(targetWeapon.definition.type)) {
                availableTargets.add(targetWeapon);
            }
        }
        weaponCount = Math.min(weaponCount, availableTargets.size());
        int stolenMoney = (int) Math.min(defender.moneyMillions, moneyAmount);
        defender.moneyMillions -= stolenMoney;
        attacker.moneyMillions += stolenMoney;
        if (stolenMoney > 0) {
            messages.add("Spy stole " + stolenMoney + "M from nation " + defender.id + ".");
        }

        for (int i = 0; i < weaponCount; i++) {
            WeaponState targetWeapon = availableTargets.remove(
                    random.nextInt(availableTargets.size()));
            WeaponState matchingWeapon = attacker.weapons.get(targetWeapon.definition.name);
            if (matchingWeapon.active) {
                matchingWeapon.inStock++;
                if (targetWeapon.inStock > 0) {
                    targetWeapon.inStock--;
                }
                messages.add("Spy stole " + targetWeapon.definition.name + " stock.");
            } else {
                matchingWeapon.active = true;
                attacker.inventedTechnologies.add(targetWeapon.definition.technology);
                messages.add("Spy uncovered " + targetWeapon.definition.technology + " technology.");
            }
            addToHighscore(attacker, 2, 1);
        }
        attacker.spyReadiness = 0;
    }

    private static String spyActionCategory(Action action) {
        switch (action) {
            case RESOURCES:
                return "resources";
            case PRODUCTION:
                return "factory";
            case RESEARCH:
                return "tech";
            case MASS_MEDIA:
                return "religion";
            case DIPLOMACY:
                return "diplomacy";
            default:
                return null;
        }
    }

    private void advanceSpyReadiness() {
        for (PlayerState player : players) {
            if (player.alive) {
                player.spyReadiness = Math.min(100, player.spyReadiness + 25);
            }
        }
    }

    private static boolean canUseSpy(PlayerState player, WeaponState weapon) {
        return "spy".equals(weapon.definition.type)
                && weapon.definition.level <= player.spyReadiness / 25;
    }

    /**
     * Recovered Presenter.doTR_from_battle propaganda calculation. Influence is
     * limited by the acting nation's religion and population advantage, then
     * the selected enemy city loses citizens that are distributed across the
     * acting nation's populated cities using the source's rounded per-city share.
     */
    private void resolvePropaganda(ActionOrder order) {
        PlayerState attacker = playerById(order.playerId);
        PlayerState defender = playerById(order.targetPlayerId);
        CityState targetCity = defender.getCity(order.targetCityIndex);
        if (!defender.alive || targetCity == null || targetCity.populationMillions <= 0) {
            messages.add("Propaganda cancelled: target city is no longer available.");
            return;
        }

        int transfer = calculatePropagandaTransfer(attacker, defender, targetCity);

        targetCity.populationMillions = Math.max(0, targetCity.populationMillions - transfer);
        defender.populationMillions = totalPopulation(defender);
        int recipientCount = 0;
        for (CityState city : attacker.cities) {
            if (city.populationMillions > 0) {
                recipientCount++;
            }
        }
        if (recipientCount > 0) {
            int share = Math.round((float) transfer / recipientCount);
            for (CityState city : attacker.cities) {
                if (city.populationMillions > 0) {
                    city.populationMillions += share;
                }
            }
            attacker.populationMillions = totalPopulation(attacker);
        }
        applyPropagandaHostilityEffects(attacker, defender);
        addToHighscore(attacker, 7, transfer);
        if (transfer > 0) {
            messages.add("You gained " + transfer
                    + ".000.000 citizens from propaganda.");
        } else {
            messages.add("Propaganda had no effect in the selected city.");
        }
    }

    public boolean configurePropaganda(int targetPlayerId, int cityIndex) {
        if (selectedAction != Action.PROPAGANDA || targetPlayerId <= 0
                || targetPlayerId >= players.length || cityIndex < 0 || cityIndex >= 5) {
            return false;
        }
        PlayerState target = players[targetPlayerId];
        CityState city = target.getCity(cityIndex);
        if (!target.alive || city == null || city.populationMillions <= 0) {
            return false;
        }
        selectedTargetPlayer = targetPlayerId;
        selectedTargetCity = cityIndex;
        return true;
    }

    public void cyclePropagandaTarget(int direction) {
        if (direction == 0 || selectedAction != Action.PROPAGANDA) {
            return;
        }
        for (int step = 1; step < players.length; step++) {
            int candidate = 1 + Math.floorMod(selectedTargetPlayer - 1
                    + Integer.signum(direction) * step, players.length - 1);
            int cityIndex = firstPopulatedCity(players[candidate], selectedTargetCity);
            if (players[candidate].alive && cityIndex >= 0) {
                configurePropaganda(candidate, cityIndex);
                return;
            }
        }
    }

    public void cyclePropagandaCity(int direction) {
        if (direction == 0 || selectedAction != Action.PROPAGANDA) {
            return;
        }
        PlayerState target = players[selectedTargetPlayer];
        for (int step = 1; step <= target.cities.size(); step++) {
            int cityIndex = Math.floorMod(selectedTargetCity
                    + Integer.signum(direction) * step, target.cities.size());
            if (target.getCity(cityIndex).populationMillions > 0) {
                configurePropaganda(selectedTargetPlayer, cityIndex);
                return;
            }
        }
    }

    public boolean canAcceptPropaganda() {
        PlayerState target = selectedTargetPlayer > 0 && selectedTargetPlayer < players.length
                ? players[selectedTargetPlayer] : null;
        CityState city = target == null ? null : target.getCity(selectedTargetCity);
        return target != null && target.alive && city != null && city.populationMillions > 0;
    }

    /** Source upper bound used by the propaganda action preview, in millions. */
    public int getPropagandaTransferEstimate(int targetPlayerId, int cityIndex) {
        if (targetPlayerId <= 0 || targetPlayerId >= players.length
                || cityIndex < 0 || cityIndex >= 5) {
            return 0;
        }
        PlayerState attacker = getHuman();
        PlayerState defender = players[targetPlayerId];
        CityState city = defender.getCity(cityIndex);
        if (!defender.alive || city.populationMillions <= 0) {
            return 0;
        }
        return calculatePropagandaTransfer(attacker, defender, city);
    }

    private static int calculatePropagandaTransfer(PlayerState attacker,
                                                   PlayerState defender,
                                                   CityState city) {
        int religionAdvantage = Math.max(0, attacker.religion - defender.religion);
        int populationShift = 0;
        int populationAdvantage = attacker.populationMillions - defender.populationMillions;
        if (populationAdvantage > 80) {
            populationShift = -4;
        } else if (populationAdvantage > 40) {
            populationShift = -2;
        } else if (populationAdvantage > 20) {
            populationShift = -1;
        } else if (populationAdvantage < -80) {
            populationShift = 4;
        } else if (populationAdvantage < -40) {
            populationShift = 2;
        } else if (populationAdvantage < -20) {
            populationShift = 1;
        }

        int influence = attacker.religion > defender.religion ? religionAdvantage : 0;
        influence = Math.min(attacker.human ? 15 : 25, influence);
        influence = defender.religion == 100 ? 0 : Math.max(0, influence + populationShift);
        int roundedLoss = Math.min(Math.round((float) city.populationMillions
                * (influence + 5) / 100f), 40);
        return Math.round(roundedLoss / 2f);
    }

    private static int totalPopulation(PlayerState player) {
        int population = 0;
        for (CityState city : player.cities) {
            population += Math.max(0, city.populationMillions);
        }
        return population;
    }

    private void refreshMatchStatus() {
        for (PlayerState player : players) {
            player.populationMillions = totalPopulation(player);
            if (player.populationMillions <= 0) {
                player.alive = false;
            }
        }
        if (!getHuman().alive) {
            matchStatus = MatchStatus.DEFEAT;
            captureFinalScore();
            messages.add("Your nation has been defeated.");
            return;
        }
        for (int playerId = 1; playerId < players.length; playerId++) {
            if (players[playerId].alive) {
                return;
            }
        }
        matchStatus = MatchStatus.VICTORY;
        captureFinalScore();
        messages.add("Victory. All rival nations have fallen.");
    }

    private void captureFinalScore() {
        if (finalScoreBreakdown != null) {
            return;
        }
        int humanCreditedFlags = 0;
        for (PlayerState player : players) {
            if (player.lastAttackedByPlayerId == 0) {
                humanCreditedFlags++;
            }
        }
        finalScoreBreakdown = FinalScoreBreakdown.calculate(getHuman().score,
                getHuman().satellitesUsed, humanCreditedFlags);
    }

    private void resolveProduction(ActionOrder order) {
        PlayerState producer = playerById(order.playerId);
        for (Map.Entry<String, Integer> investment : order.productionBudgets.entrySet()) {
            resolveProductionInvestment(producer, investment.getKey(), investment.getValue());
        }
    }

    private void resolveProductionInvestment(PlayerState producer, String category,
                                             int budget) {
        int group = productionGroup(category);
        if (group == 0 || budget <= 0) {
            return;
        }
        List<WeaponState> candidates = new ArrayList<>();
        int minimumCost = Integer.MAX_VALUE;
        for (WeaponState weapon : producer.weapons.values()) {
            WeaponCatalog.Definition definition = weapon.definition;
            if (definition.productionGroup == group && weapon.active
                    && weapon.inStock < PLAYER_STOCK_CAP
                    && (group == 7 || definition.costMillions < budget)) {
                candidates.add(weapon);
                minimumCost = Math.min(minimumCost, definition.costMillions);
            }
        }
        int remaining = budget;
        int attempts = 0;
        Map<String, Integer> bought = new LinkedHashMap<>();
        while (!candidates.isEmpty() && attempts <= 30) {
            WeaponState weapon = candidates.get(random.nextInt(candidates.size()));
            if (weapon.inStock <= PLAYER_STOCK_CAP) {
                if (group == 7) {
                    int investment = Math.round((float) remaining / (2 + random.nextInt(24)));
                    weapon.investmentMillions += investment;
                    producer.moneyMillions -= investment;
                    remaining -= investment;
                    int unitsBought = 0;
                    while (weapon.investmentMillions >= weapon.definition.costMillions
                            && unitsBought < 15) {
                        weapon.inStock++;
                        weapon.investmentMillions -= weapon.definition.costMillions;
                        Integer count = bought.get(weapon.definition.name);
                        bought.put(weapon.definition.name, count == null ? 1 : count + 1);
                        addToHighscore(producer, 3, weapon.definition.level);
                        unitsBought++;
                    }
                } else if (weapon.definition.costMillions <= remaining) {
                    weapon.inStock++;
                    producer.moneyMillions -= weapon.definition.costMillions;
                    remaining -= weapon.definition.costMillions;
                    Integer count = bought.get(weapon.definition.name);
                    bought.put(weapon.definition.name, count == null ? 1 : count + 1);
                    addToHighscore(producer, 3, weapon.definition.level);
                }
            }
            attempts++;
            if ((group == 7 ? remaining <= 0 : remaining < minimumCost) || attempts > 30) {
                break;
            }
        }
        if (!bought.isEmpty()) {
            StringBuilder summary = new StringBuilder("Nation ")
                    .append(producer.id).append(" produced:");
            for (Map.Entry<String, Integer> entry : bought.entrySet()) {
                summary.append(' ').append(entry.getKey()).append(" x")
                        .append(entry.getValue()).append(';');
            }
            messages.add(summary.toString());
        }
        if (remaining > 0 && !candidates.isEmpty()) {
            messages.add("Money returned from nation " + producer.id
                    + " production: " + remaining + ".000.000");
        }
    }

    private int queuedActionIndex(Action action) {
        for (int index = 0; index < queuedActions.size(); index++) {
            if (queuedActions.get(index).action == action) {
                return index;
            }
        }
        return -1;
    }

    void addToHighscore(PlayerState player, int actionIndex, int multiplier) {
        int[] sourceWeights = {151, 1213, 1001, 2001, 3002, 200, 101, 910};
        long points = sourceWeights[actionIndex] + random.nextInt(51);
        int effectiveMultiplier = multiplier == 0 ? 1 : multiplier;
        player.score += points * effectiveMultiplier * difficulty;
    }

    private boolean queuedTechnology(String technology) {
        for (ActionOrder order : queuedActions) {
            if (order.action == Action.RESEARCH && order.technologies.contains(technology)) {
                return true;
            }
        }
        return false;
    }

    private static int productionGroup(String category) {
        for (int index = 0; index < STARTING_PRODUCTION_CATEGORIES.length; index++) {
            if (STARTING_PRODUCTION_CATEGORIES[index].equals(category)) {
                return PRODUCTION_GROUPS[index];
            }
        }
        return 0;
    }

    private void trimMessages() {
        while (messages.size() > 4) {
            messages.remove(0);
        }
    }
}
