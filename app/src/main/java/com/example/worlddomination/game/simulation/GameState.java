package com.example.worlddomination.game.simulation;

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
                    || this == MASS_MEDIA || this == ATTACK || this == PROPAGANDA;
        }
    }

    public static final class PlayerState {
        public final int id;
        public final boolean human;
        public int populationMillions;
        public int moneyMillions;
        public int turnMoneyMillions;
        public int religion;
        public int score;
        public int spyReadiness = 25;
        public boolean alive = true;
        private final List<CityState> cities = new ArrayList<>(5);
        private final Set<String> inventedTechnologies = new LinkedHashSet<>();
        private final Map<String, WeaponState> weapons = new LinkedHashMap<>();

        private PlayerState(int id, boolean human, int populationMillions,
                            int moneyMillions, int religion) {
            this.id = id;
            this.human = human;
            this.populationMillions = populationMillions;
            this.moneyMillions = moneyMillions;
            this.turnMoneyMillions = moneyMillions;
            this.religion = religion;
        }

        public boolean hasTechnology(String name) {
            return inventedTechnologies.contains(name);
        }

        public Set<String> getInventedTechnologies() {
            return Collections.unmodifiableSet(inventedTechnologies);
        }

        public Map<String, WeaponState> getWeapons() {
            return Collections.unmodifiableMap(weapons);
        }

        public List<CityState> getCities() {
            return Collections.unmodifiableList(cities);
        }

        public CityState getCity(int index) {
            return index < 0 || index >= cities.size() ? null : cities.get(index);
        }
    }

    public static final class WeaponState {
        public final WeaponCatalog.Definition definition;
        public int inStock;
        public int investmentMillions;
        public boolean active;

        private WeaponState(WeaponCatalog.Definition definition) {
            this.definition = definition;
            this.inStock = definition.initialStock;
        }
    }

    private static final class ActionOrder {
        final Action action;
        final String productionCategory;
        final int productionPercent;
        final String technology;
        final int reservedMillions;
        final int targetPlayerId;
        final int targetCityIndex;
        final String weaponName;
        final String spyBuilding;

        ActionOrder(Action action, String productionCategory, int productionPercent,
                    String technology, int reservedMillions, int targetPlayerId,
                    int targetCityIndex, String weaponName, String spyBuilding) {
            this.action = action;
            this.productionCategory = productionCategory;
            this.productionPercent = productionPercent;
            this.technology = technology;
            this.reservedMillions = reservedMillions;
            this.targetPlayerId = targetPlayerId;
            this.targetCityIndex = targetCityIndex;
            this.weaponName = weaponName;
            this.spyBuilding = spyBuilding;
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
    private static final String[] SPY_BUILDINGS = {
            "spyCenter", "tech", "religion", "resources", "factory"
    };
    private static final String[] SPY_ACTION_CATEGORIES = {
            "diplomacy", "factory", "tech", "religion", "resources"
    };

    private final int difficulty;
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
    private String selectedSpyBuilding = "spyCenter";
    private int turn;
    private int selectedIsland = -1;
    private MatchStatus matchStatus = MatchStatus.ONGOING;

    /** Difficulty indices follow the recovered source: 0 easy, 1 normal, 2 hard. */
    public GameState(int difficulty) {
        this(difficulty, 0L);
    }

    /** Seed injection keeps gameplay rules reproducible in JVM tests. */
    public GameState(int difficulty, long seed) {
        if (difficulty < 0 || difficulty > 2) {
            throw new IllegalArgumentException("Difficulty must be 0, 1 or 2");
        }
        this.difficulty = difficulty;
        this.random = new Random(seed);
        players[0] = new PlayerState(0, true, PLAYER_START_POPULATION[difficulty],
                PLAYER_START_MONEY[difficulty], PLAYER_START_MONEY[difficulty]);
        for (int i = 1; i < players.length; i++) {
            players[i] = new PlayerState(i, false, ENEMY_START_POPULATION[difficulty],
                    ENEMY_START_MONEY[difficulty], new int[]{30, 40, 60}[difficulty]);
        }
        for (PlayerState player : players) {
            initializeInventory(player);
            initializeCities(player);
        }
        messages.add("Welcome, commander. Select two actions.");
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

    public PlayerState getHuman() {
        return players[0];
    }

    public PlayerState[] getPlayers() {
        return players.clone();
    }

    public PlayerState getPlayerState(int playerId) {
        return playerById(playerId);
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
        if (action == null || !action.isPorted()) {
            cancelSelection();
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
        selectedSpyBuilding = "spyCenter";
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
                || (soldier && city.populationMillions <= 0)) {
            return false;
        }
        selectedTargetPlayer = targetPlayerId;
        selectedTargetCity = cityIndex;
        selectedAttackWeapon = weaponName;
        return true;
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
                ? city.populationMillions > 0 : canUseSpy(getHuman(), weapon));
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
        } else if (selectedAction == Action.PROPAGANDA && !canAcceptPropaganda()) {
            messages.add("Choose a living opponent and a populated city.");
            return false;
        }
        if (reserved > getHuman().turnMoneyMillions) {
            messages.add("The selected action exceeds the remaining budget.");
            return false;
        }
        ActionOrder order = new ActionOrder(selectedAction, selectedProductionCategory,
                selectedProductionPercent, selectedTechnology, reserved,
                selectedTargetPlayer, selectedTargetCity, selectedAttackWeapon,
                selectedSpyBuilding);
        queuedActions.add(order);
        getHuman().turnMoneyMillions -= reserved;
        messages.add(selectedAction.label + " queued.");
        selectedAction = null;
        selectedTechnology = null;
        selectedTargetPlayer = selectedIsland > 0 ? selectedIsland : 1;
        selectedTargetCity = 0;
        selectedAttackWeapon = firstAvailableAttackWeapon();
        return true;
    }

    public void cancelSelection() {
        selectedAction = null;
        selectedTechnology = null;
        selectedProductionPercent = 0;
        selectedProductionCategory = "soldier";
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

    /** Resolves the supported actions in their queued order using source tables. */
    public void confirmTurn() {
        if (queuedActions.size() != MAX_TURN_ACTIONS) {
            throw new IllegalStateException("Choose two actions before confirming the turn");
        }
        decayReligion();
        advanceSpyReadiness();
        for (ActionOrder order : queuedActions) {
            switch (order.action) {
                case RESOURCES:
                    resolveResources(getHuman());
                    break;
                case MASS_MEDIA:
                    resolveMassMedia(getHuman());
                    break;
                case PRODUCTION:
                    resolveProduction(order);
                    break;
                case RESEARCH:
                    resolveResearch(order);
                    break;
                case ATTACK:
                    resolveAttack(order);
                    break;
                case PROPAGANDA:
                    resolvePropaganda(order);
                    break;
                default:
                    throw new IllegalStateException("Unported action entered the turn queue: "
                            + order.action);
            }
        }
        for (PlayerState player : players) {
            player.religion = Math.min(100, Math.max(0, player.religion));
        }
        refreshMatchStatus();
        turn++;
        getHuman().turnMoneyMillions = getHuman().moneyMillions;
        messages.add("Turn " + turn + " complete.");
        queuedActions.clear();
        cancelSelection();
        trimMessages();
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
        messages.add("You GAINED $ " + income + ".000.000 from resources");
    }

    private void resolveMassMedia(PlayerState player) {
        player.religion += getMassMediaGain(player.id);
        messages.add("Mass Media increased influence by " + getMassMediaGain(player.id) + ".");
    }

    private void resolveResearch(ActionOrder order) {
        TechnologyCatalog.Definition technology = TechnologyCatalog.byName(order.technology);
        if (technology == null || getHuman().hasTechnology(technology.name)
                || getHuman().moneyMillions < technology.costMillions
                || (technology.prerequisite != null
                && !getHuman().hasTechnology(technology.prerequisite))) {
            messages.add("Research could not be completed: " + order.technology + ".");
            return;
        }
        getHuman().inventedTechnologies.add(technology.name);
        WeaponState weapon = getHuman().weapons.get(technology.name);
        if (weapon != null) {
            weapon.active = true;
        }
        getHuman().moneyMillions -= technology.costMillions;
        addToHighscore(getHuman(), 4, technology.level);
        messages.add("Technologies invented: " + technology.name.toUpperCase());
    }

    /** Source attack casualties are a rounded random 50%-100% of unit maxKills. */
    private void resolveAttack(ActionOrder order) {
        PlayerState attacker = getHuman();
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
            resolveSpyAttack(attacker, defender, weapon);
            weapon.inStock--;
            messages.add("Spy operation completed against nation " + defender.id + ".");
            return;
        }
        int casualties = 0;
        if ("soldier".equals(weapon.definition.type)) {
            float minimum = weapon.definition.maxKillsMillions / 2f;
            float damage = minimum + random.nextFloat() * minimum;
            casualties = Math.min(city.populationMillions, Math.round(damage));
            city.populationMillions -= casualties;
            defender.populationMillions = totalPopulation(defender);
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

    private void resolveSpyAttack(PlayerState attacker, PlayerState defender,
                                  WeaponState spy) {
        int level = spy.definition.level;
        int[] statistics = SPY_STATISTICS[level - 1];
        int weaponCount = 1 + random.nextInt(statistics[0]);
        int moneyPercent = level * 10
                + random.nextInt(statistics[1] - level * 10 + 1);
        long moneyAmount = Math.round((defender.moneyMillions / 100.0d) * moneyPercent);
        moneyAmount = Math.min(moneyAmount, SPY_MONEY_CAP[difficulty]);

        List<String> availableSpyActions = new ArrayList<>();
        Collections.addAll(availableSpyActions, SPY_ACTION_CATEGORIES);
        for (ActionOrder order : queuedActions) {
            String excluded = spyActionCategory(order.action);
            if (excluded != null) {
                availableSpyActions.remove(excluded);
            }
        }
        if (!availableSpyActions.isEmpty()) {
            // The recovered model chooses a spy objective here; it controls the
            // mission report while the stolen technologies/stock are resolved below.
            random.nextInt(availableSpyActions.size());
        }

        List<WeaponState> availableTargets = new ArrayList<>();
        for (WeaponState targetWeapon : defender.weapons.values()) {
            if (targetWeapon.inStock > 0 && targetWeapon.definition.level <= level
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
        PlayerState attacker = getHuman();
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
            messages.add("Your nation has been defeated.");
            return;
        }
        for (int playerId = 1; playerId < players.length; playerId++) {
            if (players[playerId].alive) {
                return;
            }
        }
        matchStatus = MatchStatus.VICTORY;
        messages.add("Victory. All rival nations have fallen.");
    }

    private void resolveProduction(ActionOrder order) {
        int group = productionGroup(order.productionCategory);
        int budget = order.reservedMillions;
        if (group == 0 || budget <= 0) {
            return;
        }
        List<WeaponState> candidates = new ArrayList<>();
        int minimumCost = Integer.MAX_VALUE;
        for (WeaponState weapon : getHuman().weapons.values()) {
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
                    getHuman().moneyMillions -= investment;
                    remaining -= investment;
                    int unitsBought = 0;
                    while (weapon.investmentMillions >= weapon.definition.costMillions
                            && unitsBought < 15) {
                        weapon.inStock++;
                        weapon.investmentMillions -= weapon.definition.costMillions;
                        Integer count = bought.get(weapon.definition.name);
                        bought.put(weapon.definition.name, count == null ? 1 : count + 1);
                        addToHighscore(getHuman(), 3, weapon.definition.level);
                        unitsBought++;
                    }
                } else if (weapon.definition.costMillions <= remaining) {
                    weapon.inStock++;
                    getHuman().moneyMillions -= weapon.definition.costMillions;
                    remaining -= weapon.definition.costMillions;
                    Integer count = bought.get(weapon.definition.name);
                    bought.put(weapon.definition.name, count == null ? 1 : count + 1);
                    addToHighscore(getHuman(), 3, weapon.definition.level);
                }
            }
            attempts++;
            if ((group == 7 ? remaining <= 0 : remaining < minimumCost) || attempts > 30) {
                break;
            }
        }
        if (!bought.isEmpty()) {
            StringBuilder summary = new StringBuilder("Weapons produced:");
            for (Map.Entry<String, Integer> entry : bought.entrySet()) {
                summary.append(' ').append(entry.getKey()).append(" x")
                        .append(entry.getValue()).append(';');
            }
            messages.add(summary.toString());
        }
        if (remaining > 0 && !candidates.isEmpty()) {
            messages.add("Money returned from production: " + remaining + ".000.000");
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

    private void addToHighscore(PlayerState player, int actionIndex, int multiplier) {
        int[] sourceWeights = {151, 1213, 1001, 2001, 3002, 200, 101, 910};
        int points = sourceWeights[actionIndex] + random.nextInt(51);
        player.score += points * multiplier * difficulty;
    }

    private boolean queuedTechnology(String technology) {
        for (ActionOrder order : queuedActions) {
            if (order.action == Action.RESEARCH && technology.equals(order.technology)) {
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
