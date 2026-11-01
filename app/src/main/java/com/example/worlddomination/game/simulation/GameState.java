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

    /** Resolved combat outcomes consumed by the native presentation layer. */
    public enum CombatEventType {
        DIRECT_ATTACK,
        STRIKE_IMPACT,
        STRIKE_INTERCEPTED
    }

    public static final class CombatEvent {
        public final CombatEventType type;
        public final int attackerPlayerId;
        public final int defenderPlayerId;
        public final int cityIndex;
        public final String weaponName;
        public final String payloadName;
        public final String counterWeaponName;
        public final int casualtiesMillions;
        /** Source action-building slot used to launch a soldier2 Apache attack (1 or 2). */
        public final int actionBuildingSlot;
        /** Source action-building slot used by the defending anti-air turret (1 or 2). */
        public final int counterActionBuildingSlot;

        private CombatEvent(CombatEventType type, int attackerPlayerId,
                            int defenderPlayerId, int cityIndex, String weaponName,
                            String payloadName, int casualtiesMillions) {
            this(type, attackerPlayerId, defenderPlayerId, cityIndex, weaponName,
                    payloadName, casualtiesMillions, null, -1, -1);
        }

        private CombatEvent(CombatEventType type, int attackerPlayerId,
                            int defenderPlayerId, int cityIndex, String weaponName,
                            String payloadName, int casualtiesMillions,
                            String counterWeaponName) {
            this(type, attackerPlayerId, defenderPlayerId, cityIndex, weaponName,
                    payloadName, casualtiesMillions, counterWeaponName, -1, -1);
        }

        private CombatEvent(CombatEventType type, int attackerPlayerId,
                            int defenderPlayerId, int cityIndex, String weaponName,
                            String payloadName, int casualtiesMillions,
                            String counterWeaponName, int actionBuildingSlot) {
            this(type, attackerPlayerId, defenderPlayerId, cityIndex, weaponName,
                    payloadName, casualtiesMillions, counterWeaponName,
                    actionBuildingSlot, -1);
        }

        private CombatEvent(CombatEventType type, int attackerPlayerId,
                            int defenderPlayerId, int cityIndex, String weaponName,
                            String payloadName, int casualtiesMillions,
                            String counterWeaponName, int actionBuildingSlot,
                            int counterActionBuildingSlot) {
            this.type = type;
            this.attackerPlayerId = attackerPlayerId;
            this.defenderPlayerId = defenderPlayerId;
            this.cityIndex = cityIndex;
            this.weaponName = weaponName;
            this.payloadName = payloadName;
            this.casualtiesMillions = casualtiesMillions;
            this.counterWeaponName = counterWeaponName;
            this.actionBuildingSlot = actionBuildingSlot;
            this.counterActionBuildingSlot = counterActionBuildingSlot;
        }
    }

    /** Resolved spy operations kept for source-specific result presentation. */
    public static final class SpyMissionEvent {
        public final int attackerPlayerId;
        public final int defenderPlayerId;
        public final int actionBuildingSlot;
        public final String actionBuildingFrame;
        public final String religionCode;

        private SpyMissionEvent(int attackerPlayerId, int defenderPlayerId,
                                int actionBuildingSlot, String actionBuildingFrame,
                                String religionCode) {
            this.attackerPlayerId = attackerPlayerId;
            this.defenderPlayerId = defenderPlayerId;
            this.actionBuildingSlot = actionBuildingSlot;
            this.actionBuildingFrame = actionBuildingFrame;
            this.religionCode = religionCode;
        }
    }

    /** Source action-building clip queued for the latest resolved turn. */
    public static final class ActionBuildingEvent {
        public final int playerId;
        public final Action action;
        public final int slot;
        public final String frameName;
        public final String religionCode;
        public final int sequenceIndex;

        private ActionBuildingEvent(int playerId, Action action, int slot,
                                    String frameName, String religionCode,
                                    int sequenceIndex) {
            this.playerId = playerId;
            this.action = action;
            this.slot = slot;
            this.frameName = frameName;
            this.religionCode = religionCode;
            this.sequenceIndex = sequenceIndex;
        }
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

    private static final class AiDiplomacyResponse {
        final PlayerState target;
        final DiplomacyProposal proposal;
        final boolean accepted;

        AiDiplomacyResponse(PlayerState target, DiplomacyProposal proposal,
                            boolean accepted) {
            this.target = target;
            this.proposal = proposal;
            this.accepted = accepted;
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
        int lastAttackedByCasualties;
        private final double[] regardToward = new double[5];
        private final int[] ceasefireTurns = new int[5];
        private final int[] diplomacyOfferedLastTurn = new int[5];
        private final DiplomacyProposal[] pendingDiplomacy = new DiplomacyProposal[5];
        // The Flash source uses diplomacyPendingArr in append order; accepted
        // demands spend the receiver's turn budget in that same order.
        private final List<DiplomacyProposal> pendingDiplomacyOrder = new ArrayList<>();
        private final List<CityState> cities = new ArrayList<>(5);
        private final List<Action> lastTurnActions = new ArrayList<>(2);
        private final List<String> shortMessages = new ArrayList<>();
        private final List<String> shortMessagesView = Collections.unmodifiableList(shortMessages);
        private final Set<String> inventedTechnologies = new LinkedHashSet<>();
        private final Map<String, WeaponState> weapons = new LinkedHashMap<>();
        private final Map<String, Integer> desiredStock = new LinkedHashMap<>();
        private final Set<Action> bioloadedActions = new LinkedHashSet<>();
        private String preparedCarrierName;
        private boolean bioloaded;
        private int turnBattleCasualties;
        private int turnBattleKills;
        private int turnResourcesGained;

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

        /** Source TechTree.techLevel: sum the weights of every invented technology. */
        public int getTechnologyLevel() {
            int level = 0;
            for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
                if (inventedTechnologies.contains(technology.name)) {
                    level += technology.strengthWeight;
                }
            }
            return level;
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
                // Flash Player.weaponStockStrength iterates WeaponStock.weaponArr;
                // antiAir and antiRocket live in the separate counterArr.
                if (!"anti".equals(weapon.definition.type)) {
                    weaponStrength += weapon.inStock * weapon.definition.level;
                }
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

        public int getLastAttackedByCasualties() {
            return lastAttackedByCasualties;
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
        int actionBuildingSlot = -1;
        String actionBuildingFrameName;
        boolean actionBuildingResolved;

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

    /** Population changes settle after every action in the source turn has resolved. */
    private static final class TurnPopulationChanges {
        private final Map<Integer, Integer> battleCasualties = new LinkedHashMap<>();
        private final Map<Integer, List<Integer>> propagandaLosses = new LinkedHashMap<>();
        private final Map<Integer, Integer> propagandaGains = new LinkedHashMap<>();
        private final Set<Integer> propagandaExcluded = new LinkedHashSet<>();

        int remainingAfterBattle(CityState city) {
            return Math.max(0, city.populationMillions - battleCasualtiesFor(city));
        }

        int battleCasualtiesFor(CityState city) {
            Integer casualties = battleCasualties.get(city.id);
            return casualties == null ? 0 : casualties;
        }

        int addBattleCasualties(CityState city, int rolledCasualties) {
            int remaining = remainingAfterBattle(city);
            if (rolledCasualties > remaining) {
                propagandaExcluded.add(city.id);
            }
            int casualties = Math.min(Math.max(0, rolledCasualties), remaining);
            battleCasualties.put(city.id, battleCasualtiesFor(city) + casualties);
            return casualties;
        }

        void addPropagandaLoss(CityState city, int loss) {
            List<Integer> losses = propagandaLosses.get(city.id);
            if (losses == null) {
                losses = new ArrayList<>(2);
                propagandaLosses.put(city.id, losses);
            }
            losses.add(loss);
        }

        List<Integer> propagandaLossesFor(CityState city) {
            List<Integer> losses = propagandaLosses.get(city.id);
            return losses == null ? Collections.<Integer>emptyList() : losses;
        }

        boolean isPropagandaExcluded(CityState city) {
            return propagandaExcluded.contains(city.id);
        }

        void addPropagandaGain(CityState city, int gain) {
            Integer current = propagandaGains.get(city.id);
            propagandaGains.put(city.id, (current == null ? 0 : current) + gain);
        }

        int propagandaGainFor(CityState city) {
            Integer gain = propagandaGains.get(city.id);
            return gain == null ? 0 : gain;
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

    private static final class PlayerMessage {
        final int playerId;
        final String text;

        PlayerMessage(int playerId, String text) {
            this.playerId = playerId;
            this.text = text;
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
    private final List<CombatEvent> lastTurnCombatEvents = new ArrayList<>();
    private final List<SpyMissionEvent> lastTurnSpyMissions = new ArrayList<>();
    private final List<ActionBuildingEvent> lastTurnActionBuildings = new ArrayList<>();
    private List<String> researchChoicesCache = Collections.emptyList();
    private boolean researchChoicesDirty = true;
    private final Random random;
    private Action selectedAction;
    private String selectedProductionCategory = "soldier";
    private final Map<String, Integer> selectedProductionPercentages = new LinkedHashMap<>();
    private final Set<String> selectedResearchTechnologies = new LinkedHashSet<>();
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
    private int selectedMapCityOwner = -1;
    private int selectedMapCityIndex = -1;
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

    /** Starts a match with the source random stream advanced by setup choices. */
    public GameState(GameSetup setup) {
        this(setupDifficulty(setup), setup.copyGameplayRandom(), true, setup);
    }

    /** Package-private explicit stream injection for setup parity tests. */
    GameState(GameSetup setup, Random random) {
        this(setupDifficulty(setup), random, true, setup);
    }

    /** Package-private quiet-opponent mode isolates player-rule tests from AI turns. */
    GameState(int difficulty, long seed, boolean opponentTurnsEnabled) {
        this(difficulty, seed, opponentTurnsEnabled, null);
    }

    private GameState(int difficulty, long seed, boolean opponentTurnsEnabled,
                      GameSetup setup) {
        this(difficulty, new ReferenceRandom(seed), opponentTurnsEnabled, setup);
    }

    /**
     * Builds a simulation with a caller-supplied random stream for deterministic
     * parity tests and reproducible simulations.
     */
    public GameState(int difficulty, Random random) {
        this(difficulty, random, true, null);
    }

    /** Package-private quiet-opponent mode for source upkeep/RNG regression tests. */
    GameState(int difficulty, Random random, boolean opponentTurnsEnabled) {
        this(difficulty, random, opponentTurnsEnabled, null);
    }

    private GameState(int difficulty, Random random, boolean opponentTurnsEnabled,
                      GameSetup setup) {
        if (difficulty < 0 || difficulty > 2) {
            throw new IllegalArgumentException("Difficulty must be 0, 1 or 2");
        }
        if (random == null) {
            throw new IllegalArgumentException("Random stream cannot be null");
        }
        if (setup != null && !setup.canStart()) {
            throw new IllegalArgumentException("Select at least one opponent before starting");
        }
        this.difficulty = difficulty;
        this.opponentTurnsEnabled = opponentTurnsEnabled;
        this.random = random;
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
                initializeAiDesires(player, random);
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
        addMessage(getHuman(), "Welcome, commander. Select two actions.");
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
        return sourceRandomEnemyNumber(candidates, random);
    }

    /**
     * The Flash helper stores the random slot in its filtered opponent list as
     * the enemy number instead of reading that slot's player number. Preserve
     * that quirk because these values drive AI target priorities.
     */
    static int sourceRandomEnemyNumber(List<PlayerState> candidates, Random random) {
        if (candidates.isEmpty()) {
            // Model.get_random_set_enemy still calls Tools.randRange(0, -1).
            // Its zero-width range returns slot zero after consuming Math.random().
            random.nextDouble();
            return 0;
        }
        return random.nextInt(candidates.size());
    }

    private void reassignDeadAiEnemies(PlayerState player) {
        if (player.human || !player.alive) {
            return;
        }
        if (player.primaryEnemyId >= 0 && !players[player.primaryEnemyId].alive) {
            player.primaryEnemyId = chooseRandomEnemy(player, player.secondaryEnemyId);
        }
        if (player.secondaryEnemyId >= 0 && !players[player.secondaryEnemyId].alive) {
            player.secondaryEnemyId = chooseRandomEnemy(player, player.primaryEnemyId);
        }
    }

    private void initializeDifficultyTechnologies() {
        if (difficulty == 1) {
            for (PlayerState player : players) {
                if (!player.alive) {
                    continue;
                }
                inventTechnologiesThroughLevel(player, 1);
                setWeaponStockPerLevel(player, 1, 1);
            }
            return;
        }
        int humanTechnologyLevel = difficulty == 0 ? 2 : 1;
        inventTechnologiesThroughLevel(players[0], humanTechnologyLevel);
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
            // The source helper iterates WeaponStock.weaponArr. The defense
            // counters live in counterArr and retain their constructor stock.
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

    static void initializeAiDesires(PlayerState player, Random random) {
        // The source asks randRange(1, 2) and selects its first option for 1.
        // nextInt(2) is the zero-based equivalent of that inclusive draw.
        boolean rocketCarrier = random.nextInt(2) == 0;
        boolean warheadPayload = random.nextInt(2) == 0;
        boolean conventionalUnits = random.nextInt(2) == 0;
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
        if (player.alive) {
            Collections.addAll(player.inventedTechnologies, STARTING_TECHNOLOGIES);
        }
        for (WeaponCatalog.Definition definition : WeaponCatalog.all()) {
            WeaponState weapon = new WeaponState(definition);
            if (!player.alive) {
                weapon.inStock = 0;
            }
            weapon.active = player.alive
                    && player.inventedTechnologies.contains(definition.technology);
            player.weapons.put(definition.name, weapon);
        }
    }

    /** The source distributes each active nation's starting population across five cities. */
    private void initializeCities(PlayerState player) {
        int[] populations = sourceStartingCityPopulations(player.populationMillions,
                player.alive, random);
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            player.cities.add(new CityState(player.id * 5 + cityIndex,
                    player.id, cityIndex, populations[cityIndex]));
        }
    }

    /** Mirrors the source's five randRange calls, including the discarded final draw. */
    static int[] sourceStartingCityPopulations(int initialPopulation, boolean active,
                                              Random random) {
        if (initialPopulation < 0) {
            throw new IllegalArgumentException("Starting population cannot be negative");
        }
        int[] populations = new int[5];
        if (!active) {
            return populations;
        }

        int remaining = initialPopulation;
        for (int cityIndex = 0; cityIndex < populations.length; cityIndex++) {
            int minimum = Math.min(40, remaining);
            double maximum = Math.min(initialPopulation / 4.0, remaining);
            int drawnPopulation = (int) Math.floor(
                    random.nextDouble() * (maximum - minimum + 1.0)) + minimum;
            if (cityIndex < populations.length - 1) {
                populations[cityIndex] = drawnPopulation;
                remaining -= drawnPopulation;
            } else {
                populations[cityIndex] = remaining;
            }
        }
        return populations;
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

    public int getSelectedMapCityOwner() {
        return selectedMapCityOwner;
    }

    public int getSelectedMapCityIndex() {
        return selectedMapCityIndex;
    }

    public void selectIsland(int island) {
        if (island < 0 || island >= 5) {
            throw new IllegalArgumentException("Island index must be between 0 and 4");
        }
        selectedIsland = island;
        selectedMapCityOwner = -1;
        selectedMapCityIndex = -1;
    }

    /** Selects a populated, living city tapped on the original battlefield. */
    public boolean selectBattlefieldCity(int playerId, int cityIndex) {
        if (playerId < 0 || playerId >= players.length || cityIndex < 0 || cityIndex >= 5) {
            return false;
        }
        PlayerState owner = players[playerId];
        CityState city = owner.getCity(cityIndex);
        if (!owner.alive || city == null || city.populationMillions <= 0) {
            return false;
        }
        selectedIsland = playerId;
        selectedMapCityOwner = playerId;
        selectedMapCityIndex = cityIndex;
        if (playerId > 0) {
            selectedTargetPlayer = playerId;
            selectedTargetCity = cityIndex;
        }
        return true;
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
            addMessage(action.label + " is unavailable due to biological contamination.");
            return;
        }
        if (selectedAction == action) {
            cancelSelection();
            return;
        }
        if (queuedActions.size() >= MAX_TURN_ACTIONS) {
            addMessage("Both action slots are full. Confirm the turn or clear them.");
            selectedAction = null;
            return;
        }
        selectedAction = action;
        selectedResearchTechnologies.clear();
        resetTargetFromMapSelection();
        selectedAttackWeapon = firstAvailableAttackWeapon();
        selectedPreparedCarrier = null;
        selectedPreparedPayload = null;
        selectedShipStrikes.clear();
        selectedDefenseWeapon = firstAvailableDefenseWeapon();
        selectedSpyBuilding = "spyCenter";
        selectedDiplomacyOption = 0;
        resetProductionSelection();
    }

    /** Selects one production row without changing its already allocated share. */
    public void selectProductionCategory(String category) {
        if (selectedAction != Action.PRODUCTION) {
            throw new IllegalStateException("Select Production before choosing a category");
        }
        if (productionGroup(category) == 0) {
            throw new IllegalArgumentException("Unknown production category: " + category);
        }
        selectedProductionCategory = category;
    }

    /** Sets one unlocked category's share while preserving the total 100 percent budget. */
    public boolean configureProduction(String category, int percent) {
        if (selectedAction != Action.PRODUCTION) {
            throw new IllegalStateException("Select Production before setting its investment");
        }
        int group = productionGroup(category);
        if (group == 0) {
            throw new IllegalArgumentException("Unknown production category: " + category);
        }
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Production percentage must be 0..100");
        }
        if (!isProductionGroupUnlocked(getHuman(), group)) {
            addMessage("You have not yet invented this Technology");
            return false;
        }
        if (percent > getProductionMaximumPercent(category)) {
            throw new IllegalArgumentException("Production allocations cannot exceed 100 percent");
        }
        selectedProductionCategory = category;
        if (percent == 0) {
            selectedProductionPercentages.remove(category);
        } else {
            selectedProductionPercentages.put(category, percent);
        }
        return true;
    }

    /** Toggles the next technology in one research group, as the source interface does. */
    public boolean selectResearchTechnology(String technology) {
        if (selectedAction != Action.RESEARCH) {
            return false;
        }
        if (selectedResearchTechnologies.remove(technology)) {
            return true;
        }
        TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
        if (!isResearchChoice(technology)) {
            return false;
        }
        if (definition.group == 7 && !getHuman().allowsSpecialTechnologies()) {
            addMessage("You need to invent 4 complete technologies before you can start "
                    + "inventing special weapons.");
            return false;
        }
        if (definition.costMillions > getHuman().turnMoneyMillions
                - getSelectedResearchCostMillions()) {
            addMessage("Not enough money!");
            return false;
        }
        selectedResearchTechnologies.add(technology);
        return true;
    }

    /** Next unresearched entry in each source research group, including locked choices. */
    public List<String> getResearchChoices() {
        if (!researchChoicesDirty) {
            return researchChoicesCache;
        }
        List<String> choices = new ArrayList<>(7);
        for (int group = 1; group <= 7; group++) {
            String candidate = nextUnresearchedTechnology(group);
            if (candidate != null) {
                choices.add(candidate);
            }
        }
        researchChoicesCache = Collections.unmodifiableList(choices);
        researchChoicesDirty = false;
        return researchChoicesCache;
    }

    public List<String> getAvailableResearch() {
        List<String> available = new ArrayList<>();
        for (String choice : getResearchChoices()) {
            if (isResearchAvailable(choice)) {
                available.add(choice);
            }
        }
        return Collections.unmodifiableList(available);
    }

    public boolean isResearchAvailable(String technology) {
        TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
        if (definition == null || !isResearchChoice(technology)) {
            return false;
        }
        if (definition.group == 7 && !getHuman().allowsSpecialTechnologies()) {
            return false;
        }
        if (selectedResearchTechnologies.contains(technology)) {
            return true;
        }
        int remaining = getHuman().turnMoneyMillions;
        if (selectedAction == Action.RESEARCH) {
            remaining -= getSelectedResearchCostMillions();
        }
        if (definition.prerequisite != null
                && !getHuman().hasTechnology(definition.prerequisite)) {
            return false;
        }
        return definition.costMillions <= remaining;
    }

    private boolean isResearchChoice(String technology) {
        TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
        return definition != null && definition.group >= 1 && definition.group <= 7
                && !getHuman().hasTechnology(definition.name)
                && technology.equals(nextUnresearchedTechnology(definition.group));
    }

    private String nextUnresearchedTechnology(int group) {
        for (TechnologyCatalog.Definition definition : TechnologyCatalog.all()) {
            if (definition.group == group && !getHuman().hasTechnology(definition.name)) {
                return definition.name;
            }
        }
        return null;
    }

    private void invalidateResearchChoices() {
        researchChoicesDirty = true;
    }

    public boolean isResearchTechnologySelected(String technology) {
        return selectedResearchTechnologies.contains(technology);
    }

    public List<String> getSelectedResearchTechnologies() {
        return Collections.unmodifiableList(
                new ArrayList<>(selectedResearchTechnologies));
    }

    public int getSelectedResearchCostMillions() {
        int total = 0;
        for (String technology : selectedResearchTechnologies) {
            total += getTechnologyCost(technology);
        }
        return total;
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
        return selectedResearchTechnologies.isEmpty()
                ? null : selectedResearchTechnologies.iterator().next();
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
        for (DiplomacyProposal proposal : players[0].pendingDiplomacyOrder) {
            if (proposal != null && proposal.proposerId > 0) {
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
        DiplomacyProposal proposal = new DiplomacyProposal(proposerPlayerId, optionIndex);
        target.pendingDiplomacy[proposerPlayerId] = proposal;
        target.pendingDiplomacyOrder.add(proposal);
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

    /** Selects a stocked soldier or ready spy from the equipment panel. */
    public boolean selectAttackWeapon(String weaponName) {
        if (selectedAction != Action.ATTACK) {
            return false;
        }
        return configureAttack(selectedTargetPlayer, selectedTargetCity, weaponName);
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
            if (weapon.active && availableWeaponStock(0, weapon) > 0
                    && (soldier || readySpy)) {
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
                || city.populationMillions <= 0
                || weapon == null || !weapon.active || availableWeaponStock(0, weapon) <= 0
                || (!soldier && !canUseSpy(getHuman(), weapon))
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
            if (isCarrier(weapon) && weapon.active
                    && availableWeaponStock(playerId, weapon) > 0
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
                    && availableWeaponStock(playerId, payload) > 0) {
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

    private void addAvailableDefense(PlayerState player, String name,
                                     List<String> destination) {
        WeaponState weapon = player.weapons.get(name);
        if (weapon != null && weapon.active
                && availableWeaponStock(player.id, weapon) > 0) {
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
                || availableWeaponStock(0, payload) <= 0) {
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
        return availableWeaponStock(0, payload) - selected;
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
                && isCompatiblePayload(carrier, payload) && payload.active
                && availableWeaponStock(0, payload) > 0;
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
            WeaponState payload = getHuman().weapons.get(selected.getKey());
            if (availableWeaponStock(0, payload) < selected.getValue()) {
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
                && city.populationMillions > 0
                && weapon != null && weapon.active
                && availableWeaponStock(0, weapon) > 0
                && ("soldier".equals(weapon.definition.type)
                ? !target.hasCeasefireWith(getHuman().id)
                : canUseSpy(getHuman(), weapon));
    }

    private String firstAvailableAttackWeapon() {
        List<String> available = getAvailableAttackWeapons();
        return available.isEmpty() ? "" : available.get(0);
    }

    /** Stock already marked in-use by queued source turn actions. */
    private int availableWeaponStock(int playerId, WeaponState weapon) {
        if (weapon == null) {
            return 0;
        }
        int reserved = 0;
        for (ActionOrder order : queuedActions) {
            if (order.playerId != playerId) {
                continue;
            }
            if ((order.action == Action.ATTACK || order.action == Action.DEFENSE
                    || order.action == Action.PREPARED_ATTACK)
                    && weapon.definition.name.equals(order.weaponName)) {
                reserved++;
            }
            if (order.action == Action.PREPARED_ATTACK) {
                if (weapon.definition.name.equals(order.payloadName)) {
                    reserved++;
                }
                for (ShipStrike strike : order.shipStrikes) {
                    if (weapon.definition.name.equals(strike.payloadName)) {
                        reserved++;
                    }
                }
            }
        }
        return Math.max(0, weapon.inStock - reserved);
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
        return getProductionPercent(selectedProductionCategory);
    }

    public int getProductionPercent(String category) {
        Integer percent = selectedProductionPercentages.get(category);
        return percent == null ? 0 : percent;
    }

    public int getSelectedProductionMaximumPercent() {
        return getProductionMaximumPercent(selectedProductionCategory);
    }

    private int getProductionMaximumPercent(String category) {
        int allocatedElsewhere = 0;
        for (Map.Entry<String, Integer> allocation : selectedProductionPercentages.entrySet()) {
            if (!allocation.getKey().equals(category)) {
                allocatedElsewhere += allocation.getValue();
            }
        }
        return Math.max(0, 100 - allocatedElsewhere);
    }

    public int getSelectedProductionAllocatedPercent() {
        int total = 0;
        for (Integer percent : selectedProductionPercentages.values()) {
            total += percent;
        }
        return total;
    }

    public Map<String, Integer> getSelectedProductionInvestments() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(selectedProductionPercentages));
    }

    public int getSelectedProductionBudgetMillions() {
        return getHuman().turnMoneyMillions * getSelectedProductionPercent() / 100;
    }

    public int getSelectedProductionTotalBudgetMillions() {
        return (int) Math.round(getHuman().turnMoneyMillions
                * (double) getSelectedProductionAllocatedPercent() / 100.0d);
    }

    public int getTurnMoneyMillions() {
        return getHuman().turnMoneyMillions;
    }

    public boolean acceptSelectedAction() {
        if (selectedAction == null || queuedActions.size() >= MAX_TURN_ACTIONS) {
            return false;
        }
        if (isActionBioloaded(0, selectedAction)) {
            addMessage(selectedAction.label + " is unavailable due to biological contamination.");
            return false;
        }
        int reserved = 0;
        if (selectedAction == Action.RESEARCH) {
            if (selectedResearchTechnologies.isEmpty()) {
                addMessage("Choose an available technology to research.");
                return false;
            }
            for (String technology : selectedResearchTechnologies) {
                if (!isResearchAvailable(technology)) {
                    addMessage("Choose an available technology to research.");
                    return false;
                }
            }
            reserved = getSelectedResearchCostMillions();
        } else if (selectedAction == Action.PRODUCTION) {
            if (!selectedProductionPercentages.isEmpty()) {
                for (Map.Entry<String, Integer> investment
                        : selectedProductionPercentages.entrySet()) {
                    if (!isProductionGroupUnlocked(getHuman(),
                            productionGroup(investment.getKey()))) {
                        addMessage("You have not yet invented this Technology");
                        return false;
                    }
                }
                reserved = getSelectedProductionTotalBudgetMillions();
            }
        } else if (selectedAction == Action.ATTACK && !canAcceptAttack()) {
            addMessage("Choose a living target city and an available unit.");
            return false;
        } else if (selectedAction == Action.PREPARED_ATTACK
                && !canAcceptPreparedAttack()) {
            addMessage(getHuman().preparedCarrierName == null
                    ? "Choose an available carrier to prepare."
                    : "Choose a living target city and compatible payload.");
            return false;
        } else if (selectedAction == Action.DEFENSE && !canAcceptDefense()) {
            addMessage("Choose available anti-air or anti-rocket defense stock.");
            return false;
        } else if (selectedAction == Action.PROPAGANDA && !canAcceptPropaganda()) {
            addMessage("Choose a living opponent and a populated city.");
            return false;
        } else if (selectedAction == Action.DIPLOMACY && !canAcceptDiplomacy()) {
            addMessage("Choose a living nation and an affordable diplomacy option.");
            return false;
        } else if (selectedAction == Action.DIPLOMACY
                && selectedDiplomacyOption < 3) {
            reserved = DIPLOMACY_AMOUNT[selectedDiplomacyOption];
        }
        if (reserved > getHuman().turnMoneyMillions) {
            addMessage("The selected action exceeds the remaining budget.");
            return false;
        }
        Map<String, Integer> productionBudgets = new LinkedHashMap<>();
        if (selectedAction == Action.PRODUCTION) {
            for (String category : STARTING_PRODUCTION_CATEGORIES) {
                int percent = getProductionPercent(category);
                if (percent > 0) {
                    productionBudgets.put(category,
                            getHuman().turnMoneyMillions * percent / 100);
                }
            }
        }
        List<String> technologies = new ArrayList<>();
        if (selectedAction == Action.RESEARCH) {
            for (TechnologyCatalog.Definition definition : TechnologyCatalog.all()) {
                if (selectedResearchTechnologies.contains(definition.name)) {
                    technologies.add(definition.name);
                }
            }
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
        if (selectedAction == Action.RESEARCH) {
            invalidateResearchChoices();
        }
        getHuman().turnMoneyMillions -= reserved;
        addMessage(selectedAction.label + " queued.");
        selectedAction = null;
        selectedResearchTechnologies.clear();
        resetTargetFromMapSelection();
        selectedAttackWeapon = firstAvailableAttackWeapon();
        selectedPreparedCarrier = null;
        selectedPreparedPayload = null;
        selectedShipStrikes.clear();
        selectedDefenseWeapon = firstAvailableDefenseWeapon();
        selectedDiplomacyOption = 0;
        resetProductionSelection();
        return true;
    }

    public void cancelSelection() {
        selectedAction = null;
        selectedResearchTechnologies.clear();
        resetTargetFromMapSelection();
        selectedAttackWeapon = firstAvailableAttackWeapon();
        resetProductionSelection();
        selectedDiplomacyOption = 0;
        selectedPreparedCarrier = null;
        selectedPreparedPayload = null;
        selectedShipStrikes.clear();
        selectedDefenseWeapon = null;
        selectedSpyBuilding = "spyCenter";
    }

    private void resetProductionSelection() {
        selectedProductionPercentages.clear();
        selectedProductionCategory = "soldier";
    }

    private void resetTargetFromMapSelection() {
        CityState mapCity = selectedMapCityOwner > 0
                && selectedMapCityOwner < players.length
                && selectedMapCityIndex >= 0 && selectedMapCityIndex < 5
                ? players[selectedMapCityOwner].getCity(selectedMapCityIndex) : null;
        boolean mapCityIsTargetable = mapCity != null
                && players[selectedMapCityOwner].alive && mapCity.populationMillions > 0;
        if (mapCityIsTargetable) {
            selectedTargetPlayer = selectedMapCityOwner;
            selectedTargetCity = selectedMapCityIndex;
            return;
        }
        selectedTargetPlayer = selectedIsland > 0 && selectedIsland < players.length
                && players[selectedIsland].alive
                ? selectedIsland : firstLivingOpponentId();
        selectedTargetCity = 0;
    }

    private int firstLivingOpponentId() {
        for (int playerId = 1; playerId < players.length; playerId++) {
            if (players[playerId].alive) {
                return playerId;
            }
        }
        return 1;
    }

    public void removeQueuedAction(int slot) {
        if (slot >= 0 && slot < queuedActions.size()) {
            ActionOrder removed = queuedActions.remove(slot);
            if (removed.action == Action.RESEARCH) {
                invalidateResearchChoices();
            }
            getHuman().turnMoneyMillions = Math.min(getHuman().moneyMillions,
                    getHuman().turnMoneyMillions + removed.reservedMillions);
            addMessage(removed.action.label + " removed.");
        }
    }

    public void clearQueuedActions() {
        queuedActions.clear();
        invalidateResearchChoices();
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

    /** Returns whether a weapon is temporarily unavailable due to biological contamination. */
    public boolean isWeaponBioloaded(int playerId, String name) {
        WeaponState weapon = playerById(playerId).weapons.get(name);
        return weapon != null && weapon.bioloaded;
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
        return getMessages(0);
    }

    /** Returns the Flash shortMSG_arr equivalent for the requested player. */
    public List<String> getMessages(int playerId) {
        return playerById(playerId).shortMessagesView;
    }

    public String getLatestMessage() {
        List<String> humanMessages = getHuman().shortMessages;
        return humanMessages.isEmpty() ? "Choose two actions, then confirm."
                : humanMessages.get(humanMessages.size() - 1);
    }

    private void addMessage(String message) {
        addMessage(getHuman(), message);
    }

    private void addMessage(PlayerState player, String message) {
        player.shortMessages.add(message);
    }

    private void addMessage(int playerId, String message) {
        addMessage(playerById(playerId), message);
    }

    /** Returns the combat outcomes from the most recently resolved turn. */
    public List<CombatEvent> getLastTurnCombatEvents() {
        return Collections.unmodifiableList(lastTurnCombatEvents);
    }

    /** Returns resolved spy missions from the latest turn in source order. */
    public List<SpyMissionEvent> getLastTurnSpyMissions() {
        return Collections.unmodifiableList(lastTurnSpyMissions);
    }

    /** Returns source-ordered original action-building clips for the last turn. */
    public List<ActionBuildingEvent> getLastTurnActionBuildings() {
        return Collections.unmodifiableList(lastTurnActionBuildings);
    }

    /**
     * Copies the reference-compatible RNG state for deterministic battle presentation.
     * Presentation draws remain isolated from future simulation turns.
     */
    public ReferenceRandom createPresentationRandomCopy() {
        return ((ReferenceRandom) random).copy();
    }

    /** Resolves human and opponent actions using the source turn order. */
    public void confirmTurn() {
        if (queuedActions.size() != MAX_TURN_ACTIONS) {
            throw new IllegalStateException("Choose two actions before confirming the turn");
        }
        // The Flash clears every player's shortMSG_arr before presenting results.
        for (PlayerState player : players) {
            player.shortMessages.clear();
            player.turnBattleCasualties = 0;
            player.turnBattleKills = 0;
            player.turnResourcesGained = 0;
        }
        lastTurnCombatEvents.clear();
        lastTurnSpyMissions.clear();
        lastTurnActionBuildings.clear();
        for (PlayerState player : players) {
            player.lastTurnActions.clear();
            player.lastAttackedByCasualties = 0;
        }
        initializeAiTurnMoney();
        List<AiDiplomacyResponse> diplomacyResponses = resolvePendingAiDiplomacy();
        List<ActionOrder> turnOrders = new ArrayList<>(players.length * MAX_TURN_ACTIONS);
        turnOrders.addAll(queuedActions);
        for (ActionOrder order : queuedActions) {
            getHuman().lastTurnActions.add(order.action);
        }
        if (opponentTurnsEnabled) {
            for (OpponentAI.Decision decision : OpponentAI.planTurn(this, random)) {
                PlayerState player = playerById(decision.playerId);
                turnOrders.add(new ActionOrder(player.id, decision.action,
                        decision.productionBudgets, decision.technologies,
                        decision.reservedMillions, decision.targetPlayerId,
                        decision.targetCityIndex, decision.weaponName,
                        decision.payloadName, decision.prepareCarrier,
                        decision.spyBuilding == null ? "spyCenter" : decision.spyBuilding,
                        decision.diplomacyOption, decision.shipStrikes));
                player.lastTurnActions.add(decision.action);
            }
        }
        assignActionBuildingSlots(turnOrders);
        // Model.doTurn plans every AI action before Sub_presenter.turnResults
        // advances readiness, relations, religion, and offer cooldowns.
        updateTurnUpkeep();
        for (AiDiplomacyResponse response : diplomacyResponses) {
            resolveDiplomacyResponse(response.target, response.proposal,
                    response.accepted, false);
        }
        clearPreviousBiologicalEffects();
        List<ActionOrder> resolvedDefenses = new ArrayList<>();
        List<BiologicalEffect> biologicalEffects = new ArrayList<>();
        List<ActionOrder> propagandaOrders = new ArrayList<>();
        TurnPopulationChanges populationChanges = new TurnPopulationChanges();
        List<PlayerMessage> spyResultMessages = new ArrayList<>();
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
                case ATTACK: {
                    resolveAttack(order, turnOrders, populationChanges, spyResultMessages);
                    break;
                }
                case PREPARED_ATTACK:
                    resolvePreparedAttack(order, resolvedDefenses, biologicalEffects,
                            populationChanges);
                    break;
                case DEFENSE:
                    break;
                case PROPAGANDA:
                    propagandaOrders.add(order);
                    break;
                case DIPLOMACY:
                    resolveDiplomacy(order);
                    break;
                default:
                    throw new IllegalStateException("Unported action entered the turn queue: "
                            + order.action);
            }
        }
        for (ActionOrder order : propagandaOrders) {
            resolvePropaganda(order, populationChanges);
        }
        applyBiologicalEffects(biologicalEffects);
        applyPopulationChanges(populationChanges);
        clearExpiredPreparedAttacks(turnOrders);
        for (PlayerState player : players) {
            player.religion = Math.min(100, Math.max(0, player.religion));
            player.turnMoneyMillions = player.moneyMillions;
        }
        tickCeasefires();
        refreshMatchStatus();
        appendTurnSummaryMessages();
        for (PlayerState player : players) {
            // Presenter.updatePlayerVars_4 clears this comparison value after
            // all turn results have been applied. Keep the attacker itself.
            player.lastAttackedByCasualties = 0;
        }
        collectActionBuildingEvents(turnOrders);
        turn++;
        getHuman().turnMoneyMillions = getHuman().moneyMillions;
        addMessage(getHuman(), "Turn " + turn + " complete.");
        for (PlayerMessage message : spyResultMessages) {
            addMessage(message.playerId, message.text);
        }
        queuedActions.clear();
        invalidateResearchChoices();
        cancelSelection();
    }

    /** Adds the per-player economy and battle summaries built by Presenter.updatePlayerVars_4. */
    private void appendTurnSummaryMessages() {
        for (PlayerState player : players) {
            if (player.turnBattleCasualties > 0) {
                addMessage(player, "You LOST " + player.turnBattleCasualties
                        + ".000.000 citizens in battle.");
            }
            if (player.turnBattleKills > 0) {
                addMessage(player, "You KILLED " + player.turnBattleKills
                        + ".000.000 citizens in battle.");
            }
            if (player.turnResourcesGained > 0) {
                addMessage(player, "You GAINED $ " + player.turnResourcesGained
                        + ".000.000 from resources");
            }
        }
    }

    private void resolveDiplomacy(ActionOrder order) {
        PlayerState proposer = playerById(order.playerId);
        if (!submitDiplomacyProposal(proposer.id, order.targetPlayerId,
                order.diplomacyOption)) {
            addMessage(proposer, "Diplomacy proposal could not be sent.");
            return;
        }
        String type = order.diplomacyOption < 3 ? "offer" : "demand";
        addMessage(proposer, "Nation " + proposer.id + " sent a diplomacy " + type
                + " to nation " + order.targetPlayerId
                + " for " + DIPLOMACY_DURATION[order.diplomacyOption] + " turns.");
    }

    void initializeAiTurnMoney() {
        for (PlayerState player : players) {
            if (player.human || !player.alive) {
                continue;
            }
            player.turnMoneyMillions = player.moneyMillions;
        }
    }

    /** Applies the source special-leader income after all opponent plans are chosen. */
    void applySpecialAiIncome() {
        for (PlayerState player : players) {
            applySpecialAiIncome(player);
        }
    }

    private void applySpecialAiIncome(PlayerState player) {
        if (opponentTurnsEnabled && player.alive && !player.human
                && player.specialPerson) {
            player.moneyMillions += 3;
        }
    }

    private List<AiDiplomacyResponse> resolvePendingAiDiplomacy() {
        List<AiDiplomacyResponse> responses = new ArrayList<>();
        for (int targetId = 1; targetId < players.length; targetId++) {
            PlayerState target = players[targetId];
            if (!target.alive) {
                continue;
            }
            // A fixed proposer-id scan changes which demands fit the recipient's
            // remaining turn money when several proposals arrive together.
            List<DiplomacyProposal> pendingInArrivalOrder =
                    new ArrayList<>(target.pendingDiplomacyOrder);
            target.pendingDiplomacyOrder.clear();
            for (DiplomacyProposal proposal : pendingInArrivalOrder) {
                int proposerId = proposal.proposerId;
                if (target.pendingDiplomacy[proposerId] == proposal) {
                    boolean accept = shouldAiAccept(target, proposal);
                    if (accept && proposal.optionIndex >= 3) {
                        target.turnMoneyMillions -= DIPLOMACY_AMOUNT[proposal.optionIndex];
                    }
                    target.pendingDiplomacy[proposerId] = null;
                    responses.add(new AiDiplomacyResponse(target, proposal, accept));
                }
            }
        }
        return responses;
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
        resolveDiplomacyResponse(target, proposal, accepted, true);
    }

    private void resolveDiplomacyResponse(PlayerState target, DiplomacyProposal proposal,
                                          boolean accepted, boolean updateTurnMoney) {
        int proposerId = proposal.proposerId;
        PlayerState proposer = players[proposerId];
        int optionIndex = proposal.optionIndex;
        int amount = DIPLOMACY_AMOUNT[optionIndex];
        if (accepted && optionIndex >= 3 && target.moneyMillions < amount) {
            accepted = false;
        }
        target.pendingDiplomacy[proposerId] = null;
        target.pendingDiplomacyOrder.remove(proposal);
        if (accepted) {
            target.regardToward[proposerId] += DIPLOMACY_BONUS;
            proposer.regardToward[target.id] += DIPLOMACY_BONUS;
            if (optionIndex < 3) {
                target.moneyMillions += amount;
                if (updateTurnMoney) {
                    target.turnMoneyMillions += amount;
                }
            } else {
                proposer.moneyMillions += amount;
                target.moneyMillions -= amount;
                if (updateTurnMoney) {
                    proposer.turnMoneyMillions += amount;
                    target.turnMoneyMillions = Math.max(0,
                            target.turnMoneyMillions - amount);
                }
            }
            int duration = DIPLOMACY_DURATION[optionIndex];
            target.ceasefireTurns[proposerId] = duration;
            proposer.ceasefireTurns[target.id] = duration;
            addMessage(target, "Nation " + target.id + " accepted diplomacy with nation "
                    + proposer.id + ".");
        } else {
            target.regardToward[proposerId] -= DIPLOMACY_BONUS;
            proposer.regardToward[target.id] -= DIPLOMACY_BONUS;
            if (optionIndex < 3) {
                proposer.moneyMillions += amount;
                if (updateTurnMoney) {
                    proposer.turnMoneyMillions += amount;
                }
            }
            addMessage(target, "Nation " + target.id + " rejected diplomacy with nation "
                    + proposer.id + ".");
        }
    }

    private static void applyDiplomacySentRegard(PlayerState proposer, PlayerState target,
                                                  int optionIndex) {
        int adjustment = optionIndex < 3 ? optionIndex * 5 : -optionIndex;
        proposer.regardToward[target.id] += adjustment;
        target.regardToward[proposer.id] += adjustment;
    }

    /** Tracks source attacker attribution and the per-player casualties shown at turn end. */
    private static void recordLargestBattle(PlayerState attacker, PlayerState defender,
                                            int casualties) {
        attacker.turnBattleKills += casualties;
        defender.turnBattleCasualties += casualties;
        if (casualties > defender.lastAttackedByCasualties) {
            defender.lastAttackedByCasualties = casualties;
            defender.lastAttackedByPlayerId = attacker.id;
        }
    }

    void applyBattleHostilityEffects(PlayerState attacker, PlayerState defender,
                                     int casualties) {
        // Model.calcBattle applies this fixed AI hostility penalty before
        // Presenter.turnResults adjusts regard by the casualty count.
        if (!attacker.human && attacker.regardToward[defender.id] > 0) {
            attacker.regardToward[defender.id] -= 8;
        }
        if (!defender.human && defender.regardToward[attacker.id] > 0) {
            defender.regardToward[attacker.id] -= 8;
        }
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

    /** Applies source upkeep in active-player order after AI planning. */
    private void updateTurnUpkeep() {
        for (PlayerState player : players) {
            if (!player.alive) {
                continue;
            }
            advanceSpyReadiness(player);
            updateDiplomacyRelations(player);
            reassignDeadAiEnemies(player);
            decayReligion(player);
            applySpecialAiIncome(player);
            tickDiplomacyOfferCooldowns(player);
        }
    }

    private void updateDiplomacyRelations(PlayerState player) {
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

    private void tickDiplomacyOfferCooldowns(PlayerState player) {
        for (int otherId = 0; otherId < players.length; otherId++) {
            if (player.diplomacyOfferedLastTurn[otherId] > 0) {
                player.diplomacyOfferedLastTurn[otherId]--;
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

    private void decayReligion(PlayerState player) {
        if (player.religion > 11) {
            player.religion -= 5 + random.nextInt(6);
        }
    }

    private void resolveResources(PlayerState player) {
        int income = resourceIncome(player);
        player.moneyMillions += income;
        player.turnMoneyMillions += income;
        addToHighscore(player, 5, income);
        player.turnResourcesGained += income;
    }

    private void resolveMassMedia(PlayerState player) {
        int gain = getMassMediaGain(player.id);
        player.religion += gain;
        addToHighscore(player, 6, gain);
        addMessage(player, "Nation " + player.id + " increased influence by "
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
            if (researcher.id == 0) {
                invalidateResearchChoices();
            }
            WeaponState weapon = researcher.weapons.get(technology.name);
            if (weapon != null) {
                weapon.active = true;
            }
            researcher.moneyMillions -= technology.costMillions;
            addToHighscore(researcher, 4, technology.level);
            invented.add(technology.name.toUpperCase(java.util.Locale.US));
        }
        if (invented.isEmpty()) {
            addMessage(researcher, "Nation " + researcher.id + " could not complete research.");
        } else {
            addMessage(researcher, "Nation " + researcher.id + " invented technologies: "
                    + String.join(", ", invented) + ".");
        }
    }

    /** Source attack casualties are a rounded random 50%-100% of unit maxKills. */
    private void resolveAttack(ActionOrder order, List<ActionOrder> turnOrders,
                               TurnPopulationChanges populationChanges,
                               List<PlayerMessage> spyResultMessages) {
        PlayerState attacker = playerById(order.playerId);
        PlayerState defender = playerById(order.targetPlayerId);
        CityState city = defender.getCity(order.targetCityIndex);
        WeaponState weapon = attacker.weapons.get(order.weaponName);
        boolean spyMission = weapon != null && "spy".equals(weapon.definition.type);
        // Readiness constrains mission selection. The source's high-cash spy-alert
        // override can dispatch the highest stocked spy regardless of readiness, and
        // the battle result handler does not revalidate readiness for queued missions.
        if (!defender.alive || city == null
                || (!spyMission && city.populationMillions <= 0)
                || weapon == null || !weapon.active || weapon.inStock <= 0) {
            addMessage(attacker, "Attack cancelled: target or weapon is no longer available.");
            return;
        }
        if (spyMission) {
            resolveSpyAttack(attacker, defender, weapon, turnOrders, spyResultMessages);
            weapon.inStock--;
            order.actionBuildingResolved = true;
            lastTurnSpyMissions.add(new SpyMissionEvent(attacker.id, defender.id,
                    order.actionBuildingSlot,
                    order.actionBuildingFrameName == null
                            ? order.spyBuilding : order.actionBuildingFrameName,
                    attacker.religionCode));
            addMessage(attacker, "Spy operation completed against nation " + defender.id + ".");
            return;
        }
        if (defender.hasCeasefireWith(attacker.id)) {
            addMessage(attacker, "Attack cancelled: a ceasefire is active with nation "
                    + defender.id + ".");
            return;
        }
        int casualties = 0;
        if ("soldier".equals(weapon.definition.type)) {
            casualties = populationChanges.addBattleCasualties(city,
                    sourceDirectAttackCasualties(weapon.definition.maxKillsMillions, random));
            lastTurnCombatEvents.add(new CombatEvent(CombatEventType.DIRECT_ATTACK,
                    attacker.id, defender.id, city.index, weapon.definition.name, null,
                    casualties, null, order.actionBuildingSlot));
            recordLargestBattle(attacker, defender, casualties);
            applyBattleHostilityEffects(attacker, defender, casualties);
            if (casualties > 0) {
                addToHighscore(attacker, 1, casualties);
            }
        } else {
            throw new IllegalStateException("Direct attack only supports soldier units");
        }
        weapon.inStock--;
    }

    /** Assigns source action-building frames and the two per-player Flash positions. */
    private void assignActionBuildingSlots(List<ActionOrder> turnOrders) {
        for (ActionOrder order : turnOrders) {
            if (order.playerId < 0 || order.playerId >= players.length) {
                continue;
            }
            WeaponState weapon = playerById(order.playerId).weapons.get(order.weaponName);
            order.actionBuildingFrameName = sourceActionBuildingFrame(order, weapon);
        }

        // Presenter.initBattle replaces a duplicate selected spy frame with the
        // generic center clip before it checks for same-player duplicate frames.
        for (ActionOrder spyOrder : turnOrders) {
            if (spyOrder.action != Action.ATTACK || spyOrder.actionBuildingFrameName == null
                    || !isSpyWeapon(spyOrder)) {
                continue;
            }
            for (ActionOrder other : turnOrders) {
                if (other != spyOrder && other.playerId == spyOrder.playerId
                        && spyOrder.actionBuildingFrameName.equals(
                        other.actionBuildingFrameName)) {
                    spyOrder.actionBuildingFrameName = "spyCenter";
                    break;
                }
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Integer>[] slotsByPlayer = new Map[players.length];
        int[] nextSlotByPlayer = new int[players.length];
        for (ActionOrder order : sourceActionBuildingOrder(turnOrders)) {
            if (order.playerId < 0 || order.playerId >= players.length
                    || order.actionBuildingFrameName == null) {
                continue;
            }
            Map<String, Integer> slots = slotsByPlayer[order.playerId];
            if (slots == null) {
                slots = new LinkedHashMap<>();
                slotsByPlayer[order.playerId] = slots;
            }
            Integer existingSlot = slots.get(order.actionBuildingFrameName);
            if (existingSlot != null) {
                order.actionBuildingSlot = existingSlot;
            } else {
                int slot = nextSlotByPlayer[order.playerId] % 2 + 1;
                nextSlotByPlayer[order.playerId]++;
                slots.put(order.actionBuildingFrameName, slot);
                order.actionBuildingSlot = slot;
            }
        }
    }

    private static String sourceActionBuildingFrame(ActionOrder order, WeaponState weapon) {
        switch (order.action) {
            case RESOURCES:
                return "resources";
            case PRODUCTION:
                return "factory";
            case RESEARCH:
                return "tech";
            case MASS_MEDIA:
                return "religion";
            case PROPAGANDA:
                return "propaganda";
            case DIPLOMACY:
                return "diplomacy";
            case ATTACK:
                if (weapon == null) {
                    return null;
                }
                if ("spy".equals(weapon.definition.type)) {
                    return order.spyBuilding == null ? "spyCenter" : order.spyBuilding;
                }
                return "soldier".equals(weapon.definition.type)
                        ? sourceDirectAttackBuildingFrame(weapon.definition.name) : null;
            case PREPARED_ATTACK:
                if (weapon == null) {
                    return null;
                }
                if ("rocket".equals(weapon.definition.type)) {
                    return "silo";
                }
                if ("plane".equals(weapon.definition.type)) {
                    return "airfield";
                }
                if ("sub".equals(weapon.definition.type)) {
                    return "sub";
                }
                if ("ship".equals(weapon.definition.type)) {
                    return "ship";
                }
                if ("space".equals(weapon.definition.type)) {
                    return "space";
                }
                return null;
            case DEFENSE:
                return weapon == null ? null
                        : sourceDefenseBuildingFrame(weapon.definition.name);
            default:
                return null;
        }
    }

    private boolean isSpyWeapon(ActionOrder order) {
        WeaponState weapon = playerById(order.playerId).weapons.get(order.weaponName);
        return weapon != null && "spy".equals(weapon.definition.type);
    }

    private void collectActionBuildingEvents(List<ActionOrder> turnOrders) {
        Set<String> seen = new LinkedHashSet<>();
        for (ActionOrder order : sourceActionBuildingOrder(turnOrders)) {
            if (order.actionBuildingFrameName == null || order.actionBuildingSlot < 1
                    || order.actionBuildingSlot > 2) {
                continue;
            }
            if (order.action == Action.ATTACK && isSpyWeapon(order)
                    && !order.actionBuildingResolved) {
                continue;
            }
            String key = order.playerId + ":" + order.actionBuildingFrameName;
            if (!seen.add(key)) {
                continue;
            }
            lastTurnActionBuildings.add(new ActionBuildingEvent(order.playerId,
                    order.action, order.actionBuildingSlot,
                    order.actionBuildingFrameName,
                    playerById(order.playerId).getReligionCode(),
                    lastTurnActionBuildings.size()));
        }
    }

    /**
     * Presenter.initBattle concatenates the source init, last, in-battle, and
     * spawned-building queues. Keep the corresponding order here so per-player
     * slot assignment does not depend on the order in which the player tapped.
     */
    private static List<ActionOrder> sourceActionBuildingOrder(List<ActionOrder> turnOrders) {
        List<ActionOrder> ordered = new ArrayList<>(turnOrders);
        Collections.sort(ordered, (left, right) -> Integer.compare(
                sourceActionBuildingPriority(left), sourceActionBuildingPriority(right)));
        return ordered;
    }

    private static int sourceActionBuildingPriority(ActionOrder order) {
        switch (order.action) {
            case ATTACK:
                return 0;
            case PREPARED_ATTACK:
                // A prepared carrier enters the source last-building queue;
                // a launch result enters the initial battle queue.
                return order.prepareCarrier ? 1 : 0;
            case PRODUCTION:
                return 2;
            case RESEARCH:
                return 3;
            case DEFENSE:
                return 4;
            case PROPAGANDA:
                return 5;
            case DIPLOMACY:
                return 6;
            case RESOURCES:
                return 7;
            case MASS_MEDIA:
                return 8;
            default:
                return Integer.MAX_VALUE;
        }
    }

    private static ActionOrder findCounterDefense(List<ActionOrder> defenses,
                                                   int defenderId,
                                                   String counterName) {
        if (counterName == null || counterName.isEmpty()) {
            return null;
        }
        for (ActionOrder defense : defenses) {
            if (defense.playerId == defenderId && counterName.equals(defense.weaponName)) {
                return defense;
            }
        }
        return null;
    }

    private static String sourceDirectAttackBuildingFrame(String weaponName) {
        if ("soldier1".equals(weaponName)) {
            return "base";
        }
        if ("soldier2".equals(weaponName) || "soldier3".equals(weaponName)) {
            return "helipad";
        }
        if ("soldier4".equals(weaponName)) {
            return "hatch";
        }
        return null;
    }

    static String sourceDefenseBuildingFrame(String weaponName) {
        if ("antiAir".equals(weaponName)) {
            return "antiAir_fire";
        }
        if ("antiRocket".equals(weaponName)) {
            return "antiRocket_fire";
        }
        return null;
    }

    private void resolveDefense(ActionOrder order, List<ActionOrder> resolvedDefenses) {
        PlayerState defender = playerById(order.playerId);
        WeaponState counter = defender.weapons.get(order.weaponName);
        if (counter == null || !counter.active || counter.inStock <= 0
                || !"anti".equals(counter.definition.type)) {
            addMessage(defender, "Defense cancelled: counter stock is no longer available.");
            return;
        }
        counter.inStock--;
        resolvedDefenses.add(order);
        addMessage(defender, "Nation " + defender.id + " used " + order.weaponName + " defense.");
    }

    private void resolvePreparedAttack(ActionOrder order,
                                       List<ActionOrder> resolvedDefenses,
                                       List<BiologicalEffect> biologicalEffects,
                                       TurnPopulationChanges populationChanges) {
        PlayerState attacker = playerById(order.playerId);
        if (order.prepareCarrier) {
            WeaponState carrier = attacker.weapons.get(order.weaponName);
            if (attacker.preparedCarrierName != null || !isCarrier(carrier)
                    || !carrier.active || carrier.inStock <= 0
                    || getAvailablePreparedPayloads(attacker.id, order.weaponName).isEmpty()) {
                addMessage(attacker, "Strike preparation cancelled: carrier or payload is unavailable.");
                return;
            }
            carrier.inStock--;
            attacker.preparedCarrierName = order.weaponName;
            addMessage(attacker, "Nation " + attacker.id + " prepared " + order.weaponName + ".");
            return;
        }

        PlayerState defender = playerById(order.targetPlayerId);
        WeaponState carrier = attacker.weapons.get(order.weaponName);
        if (carrier != null && "ship".equals(carrier.definition.type)) {
            resolveShipPreparedAttack(order, attacker, defender, carrier,
                    resolvedDefenses, biologicalEffects, populationChanges);
            return;
        }
        CityState city = defender.getCity(order.targetCityIndex);
        WeaponState payload = attacker.weapons.get(order.payloadName);
        if (!order.weaponName.equals(attacker.preparedCarrierName)
                || !defender.alive || city == null || city.populationMillions <= 0
                || defender.hasCeasefireWith(attacker.id)
                || !isCompatiblePayload(carrier, payload) || !payload.active
                || payload.inStock <= 0) {
            addMessage(attacker, "Prepared strike cancelled: target or payload is unavailable.");
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
        ActionOrder counterDefense = findCounterDefense(resolvedDefenses, defender.id,
                counterName);
        if (counterDefense != null) {
            intercepted = counterIntercepts(random);
        }
        if (intercepted) {
            lastTurnCombatEvents.add(new CombatEvent(CombatEventType.STRIKE_INTERCEPTED,
                    attacker.id, defender.id, city.index, carrier.definition.name,
                    payload.definition.name, 0, counterName, order.actionBuildingSlot,
                    counterDefense.actionBuildingSlot));
            addMessage(defender, "Nation " + defender.id + " intercepted the prepared strike.");
            return;
        }

        if (order.payloadName.startsWith("bioload")) {
            int bioLevel = order.payloadName.charAt(order.payloadName.length() - 1) - '0';
            biologicalEffects.add(createBiologicalEffect(defender, city, bioLevel));
        }
        int casualties = populationChanges.addBattleCasualties(city, rolledCasualties);
        lastTurnCombatEvents.add(new CombatEvent(CombatEventType.STRIKE_IMPACT,
                attacker.id, defender.id, city.index, carrier.definition.name,
                payload.definition.name, casualties,
                counterDefense == null ? null : counterName, order.actionBuildingSlot,
                counterDefense == null ? -1 : counterDefense.actionBuildingSlot));
        recordLargestBattle(attacker, defender, casualties);
        applyBattleHostilityEffects(attacker, defender, casualties);
        if (casualties > 0) {
            addToHighscore(attacker, 1, casualties);
        }
    }

    private void resolveShipPreparedAttack(ActionOrder order, PlayerState attacker,
                                          PlayerState defender, WeaponState carrier,
                                          List<ActionOrder> resolvedDefenses,
                                          List<BiologicalEffect> biologicalEffects,
                                          TurnPopulationChanges populationChanges) {
        if (!order.weaponName.equals(attacker.preparedCarrierName)
                || !defender.alive || defender.hasCeasefireWith(attacker.id)
                || order.shipStrikes.size() != 3) {
            addMessage(attacker, "Ship strike cancelled: target or payload sequence is unavailable.");
            return;
        }
        Map<String, Integer> payloadUse = new LinkedHashMap<>();
        for (ShipStrike strike : order.shipStrikes) {
            CityState city = defender.getCity(strike.cityIndex);
            WeaponState payload = attacker.weapons.get(strike.payloadName);
            if (strike.targetPlayerId != defender.id || city == null
                    || city.populationMillions <= 0
                    || !isCompatiblePayload(carrier, payload) || !payload.active) {
                addMessage(attacker, "Ship strike cancelled: target or payload sequence is unavailable.");
                return;
            }
            Integer count = payloadUse.get(strike.payloadName);
            payloadUse.put(strike.payloadName, count == null ? 1 : count + 1);
        }
        for (Map.Entry<String, Integer> used : payloadUse.entrySet()) {
            WeaponState payload = attacker.weapons.get(used.getKey());
            if (payload.inStock < used.getValue()) {
                addMessage(attacker, "Ship strike cancelled: selected payload stock is unavailable.");
                return;
            }
        }

        String counterName = carrier.definition.counteredBy;
        ActionOrder counterDefense = findCounterDefense(resolvedDefenses, defender.id,
                counterName);
        boolean hasMatchingDefense = counterDefense != null;
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
                lastTurnCombatEvents.add(new CombatEvent(
                        CombatEventType.STRIKE_INTERCEPTED, attacker.id, defender.id,
                        city.index, carrier.definition.name, result.payloadName, 0,
                        counterName, -1, counterDefense.actionBuildingSlot));
                addMessage(defender, "Nation " + defender.id + " intercepted one ship payload.");
            } else {
                casualties = populationChanges.addBattleCasualties(city,
                        result.rolledCasualties);
                lastTurnCombatEvents.add(new CombatEvent(CombatEventType.STRIKE_IMPACT,
                        attacker.id, defender.id, city.index, carrier.definition.name,
                        result.payloadName, casualties,
                        hasMatchingDefense ? counterName : null, -1,
                        hasMatchingDefense ? counterDefense.actionBuildingSlot : -1));
                totalCasualties += casualties;
                recordLargestBattle(attacker, defender, casualties);
                if (casualties > 0) {
                    addToHighscore(attacker, 1, casualties);
                }
            }
        }
        applyBattleHostilityEffects(attacker, defender, totalCasualties);
    }

    /** Source ship counter roll uses randRange(1, 3) == 2: one of three strikes. */
    static boolean shipCounterRollIntercepts(Random random) {
        return random.nextInt(3) + 1 == 2;
    }

    /** Source soldier casualties: Math.round(Tools.randRange(maxKills/2, maxKills)). */
    static int sourceDirectAttackCasualties(int maximum, Random random) {
        return sourceRandRangeRounded(maximum / 2f, maximum, random);
    }

    /** Source Tools.randRange(maxKills/2, maxKills), followed by Math.round. */
    static int sourceShipStrikeCasualties(int maximum, Random random) {
        float minimum = maximum / 2f;
        return sourceRandRangeRounded(minimum, maximum, random);
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
        return sourceRandRangeRounded(minimum, upper, random);
    }

    /** Flash Tools.randRange floors an inclusive random offset before Math.round. */
    private static int sourceRandRangeRounded(float minimum, float maximum, Random random) {
        int offset = (int) Math.floor(random.nextDouble() * (maximum - minimum + 1.0));
        return (int) Math.floor(minimum + offset + 0.5);
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
            addMessage(target, "Biological agents contaminated nation " + target.id
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
                addMessage(player, "Nation " + player.id + " cancelled its unused prepared strike.");
            }
        }
    }

    private void resolveSpyAttack(PlayerState attacker, PlayerState defender,
                                  WeaponState spy, List<ActionOrder> turnOrders,
                                  List<PlayerMessage> resultMessages) {
        int level = spy.definition.level;
        int[] statistics = SPY_STATISTICS[level - 1];
        int weaponCount = 1 + random.nextInt(statistics[0]);
        int moneyPercent = level * 10
                + random.nextInt(statistics[1] - level * 10 + 1);
        long moneyAmount = Math.round((defender.moneyMillions / 100.0d) * moneyPercent);
        moneyAmount = Math.min(moneyAmount, SPY_MONEY_CAP[difficulty]);
        if (defender.specialPerson) {
            // The source stores the money amount before its later local $20M adjustment.
            weaponCount = 1;
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
        List<String> attackerDetails = new ArrayList<>(weaponCount);
        List<String> defenderDetails = new ArrayList<>();
        for (int i = 0; i < weaponCount; i++) {
            WeaponState targetWeapon = availableTargets.remove(
                    random.nextInt(availableTargets.size()));
            WeaponState matchingWeapon = attacker.weapons.get(targetWeapon.definition.name);
            String prettyName = getTechnologyLabel(targetWeapon.definition.technology);
            if (matchingWeapon.active) {
                matchingWeapon.inStock++;
                if (targetWeapon.inStock > 0) {
                    targetWeapon.inStock--;
                }
                attackerDetails.add("WEAPON: " + prettyName);
                defenderDetails.add("STOLEN: " + prettyName);
            } else {
                matchingWeapon.active = true;
                attacker.inventedTechnologies.add(targetWeapon.definition.technology);
                if (attacker.id == 0) {
                    invalidateResearchChoices();
                }
                attackerDetails.add("TECHNOLOGY: " + prettyName);
            }
            addToHighscore(attacker, 2, 1);
        }
        attacker.spyReadiness = 0;
        resultMessages.add(new PlayerMessage(attacker.id,
                formatSpyMissionResult(true, false, stolenMoney,
                        attackerDetails, defenderDetails)));
        resultMessages.add(new PlayerMessage(defender.id,
                formatSpyMissionResult(false, true, stolenMoney,
                        attackerDetails, defenderDetails)));
    }

    static String formatSpyMissionResult(boolean attackerIsHuman, boolean defenderIsHuman,
                                         int stolenMoneyMillions,
                                         List<String> attackerDetails,
                                         List<String> defenderDetails) {
        if (attackerIsHuman) {
            if (stolenMoneyMillions > 0) {
                return appendSpyDetails("YOUR SPY STOLE $" + stolenMoneyMillions
                        + ".000.000", attackerDetails, ", ");
            }
            if (!attackerDetails.isEmpty()) {
                return "YOUR SPY STOLE " + String.join(", ", attackerDetails);
            }
            return "YOUR SPY DID NOT STEAL ANYTHING";
        }
        if (defenderIsHuman) {
            if (stolenMoneyMillions > 0) {
                return appendSpyDetails("AN ENEMY SPY STOLE $" + stolenMoneyMillions
                        + ".000.000", defenderDetails, "; ");
            }
            if (!attackerDetails.isEmpty()) {
                return appendSpyDetails("A ENEMY SPY STOLE", defenderDetails, "; ");
            }
            return "AN ENEMY SPY WAS STOPPED";
        }
        return null;
    }

    private static String appendSpyDetails(String message, List<String> details,
                                           String separator) {
        return details.isEmpty() ? message : message + separator + String.join(", ", details);
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

    private void advanceSpyReadiness(PlayerState player) {
        player.spyReadiness = Math.min(100, player.spyReadiness + 25);
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
    private void resolvePropaganda(ActionOrder order,
                                   TurnPopulationChanges populationChanges) {
        PlayerState attacker = playerById(order.playerId);
        PlayerState defender = playerById(order.targetPlayerId);
        CityState targetCity = defender.getCity(order.targetCityIndex);
        if (!defender.alive || targetCity == null || targetCity.populationMillions <= 0) {
            addMessage(attacker, "Propaganda cancelled: target city is no longer available.");
            return;
        }

        int transfer = calculatePropagandaTransfer(attacker, defender,
                populationChanges.remainingAfterBattle(targetCity));
        populationChanges.addPropagandaLoss(targetCity, transfer);
        int recipientCount = 0;
        for (CityState city : attacker.cities) {
            if (city.populationMillions > 0
                    && !populationChanges.isPropagandaExcluded(city)) {
                recipientCount++;
            }
        }
        if (recipientCount > 0) {
            int share = Math.round((float) transfer / recipientCount);
            for (CityState city : attacker.cities) {
                if (city.populationMillions > 0
                        && !populationChanges.isPropagandaExcluded(city)) {
                    populationChanges.addPropagandaGain(city, share);
                }
            }
        }
        applyPropagandaHostilityEffects(attacker, defender);
        addToHighscore(attacker, 7, transfer);
        if (transfer > 0) {
            addMessage(attacker, "You gained " + transfer
                    + ".000.000 citizens from propaganda.");
            addMessage(defender, "You lost " + transfer
                    + ".000.000 citizens from propaganda.");
        } else {
            addMessage(attacker, "Propaganda had no effect in the selected city.");
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
        return calculatePropagandaTransfer(attacker, defender,
                city.populationMillions);
    }

    private static int calculatePropagandaTransfer(PlayerState attacker,
                                                   PlayerState defender,
                                                   int cityPopulationAfterBattle) {
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
        int roundedLoss = Math.min(Math.round((float) cityPopulationAfterBattle
                * (influence + 5) / 100f), 40);
        return Math.round(roundedLoss / 2f);
    }

    private void applyPopulationChanges(TurnPopulationChanges changes) {
        for (PlayerState player : players) {
            for (CityState city : player.cities) {
                city.setPopulationMillions(city.populationMillions
                        - changes.battleCasualtiesFor(city));
                for (int loss : changes.propagandaLossesFor(city)) {
                    if (city.populationMillions <= 0) {
                        break;
                    }
                    city.setPopulationMillions(city.populationMillions - loss);
                }
                city.setPopulationMillions(city.populationMillions
                        + changes.propagandaGainFor(city));
            }
            player.populationMillions = totalPopulation(player);
        }
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
            if (player.populationMillions <= 0 && player.alive) {
                eliminatePlayer(player);
            }
        }
        if (!getHuman().alive) {
            matchStatus = MatchStatus.DEFEAT;
            captureFinalScore();
            addMessage(getHuman(), "Your nation has been defeated.");
            return;
        }
        for (int playerId = 1; playerId < players.length; playerId++) {
            if (players[playerId].alive) {
                return;
            }
        }
        matchStatus = MatchStatus.VICTORY;
        captureFinalScore();
        addMessage(getHuman(), "Victory. All rival nations have fallen.");
    }

    /** Matches Model.killPlayer cleanup and transfers flags from its prior victims. */
    private void eliminatePlayer(PlayerState eliminated) {
        addMessage(eliminated, "Nation " + eliminated.id + " has been eliminated.");
        for (CityState city : eliminated.cities) {
            city.setPopulationMillions(0);
            city.bioloaded = false;
        }
        for (PlayerState defeated : players) {
            if (!defeated.alive
                    && defeated.lastAttackedByPlayerId == eliminated.id) {
                defeated.lastAttackedByPlayerId = eliminated.lastAttackedByPlayerId;
            }
        }
        eliminated.populationMillions = 0;
        eliminated.alive = false;
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
            addMessage(producer, summary.toString());
        }
        if (remaining > 0 && !candidates.isEmpty()) {
            addMessage(producer, "Money returned from nation " + producer.id
                    + " production: " + remaining + ".000.000");
        }
    }

    void addToHighscore(PlayerState player, int actionIndex, int multiplier) {
        int[] sourceWeights = {151, 1213, 1001, 2001, 3002, 200, 101, 910};
        long points = sourceWeights[actionIndex] + random.nextInt(51);
        int effectiveMultiplier = multiplier == 0 ? 1 : multiplier;
        // The source difficulty buttons are numbered 1..3, but the menu stores
        // button number minus one. Preserve that source 0..2 multiplier here.
        player.score += points * effectiveMultiplier * difficulty;
    }

    private static int productionGroup(String category) {
        for (int index = 0; index < STARTING_PRODUCTION_CATEGORIES.length; index++) {
            if (STARTING_PRODUCTION_CATEGORIES[index].equals(category)) {
                return PRODUCTION_GROUPS[index];
            }
        }
        return 0;
    }

    private static boolean isProductionGroupUnlocked(PlayerState player, int group) {
        if (group == 8) {
            return true;
        }
        for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
            if (technology.group == group && player.hasTechnology(technology.name)) {
                return true;
            }
        }
        return false;
    }

}
