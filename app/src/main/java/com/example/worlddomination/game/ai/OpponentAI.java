package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import com.example.worlddomination.game.simulation.WeaponCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Source-derived action scoring and target selection for computer nations. */
public final class OpponentAI {
    // Mirrors DecisionPoints.set_points_to_difficulty for the source's 0..2
    // difficulty ids: 0 and 2 use the default 86, while 1 uses 80.
    private static final int[] ATTACK_BASE = {86, 80, 86};
    private static final int[] SPY_ALERT_MONEY = {180, 150, 100};
    private static final int[] AI_SPY_STOCK_REQUIREMENTS = {10, 8, 5, 3};
    private static final String[] SPY_BUILDINGS = {
            "spyCenter", "tech", "religion", "resources", "factory"
    };
    private static final String[] PRODUCTION_CATEGORIES = {
            "rockets", "warhead", "bioload", "soldier", "spy", "planes", "special"
    };

    private OpponentAI() { }

    /** One action chosen by the opponent planner, including its reserved spending. */
    public static final class Decision {
        public final int playerId;
        public final GameState.Action action;
        public final Map<String, Integer> productionBudgets;
        public final List<String> technologies;
        public final int reservedMillions;
        public final int targetPlayerId;
        public final int targetCityIndex;
        public final String weaponName;
        public final String payloadName;
        public final boolean prepareCarrier;
        public final int diplomacyOption;
        public final List<GameState.ShipStrike> shipStrikes;
        public final String spyBuilding;

        private Decision(int playerId, GameState.Action action,
                         Map<String, Integer> productionBudgets,
                         List<String> technologies, int reservedMillions,
                         int targetPlayerId, int targetCityIndex, String weaponName,
                         int diplomacyOption) {
            this(playerId, action, productionBudgets, technologies, reservedMillions,
                    targetPlayerId, targetCityIndex, weaponName, null, false,
                    diplomacyOption);
        }

        private Decision(int playerId, GameState.Action action,
                         Map<String, Integer> productionBudgets,
                         List<String> technologies, int reservedMillions,
                         int targetPlayerId, int targetCityIndex, String weaponName,
                         String payloadName, boolean prepareCarrier, int diplomacyOption) {
            this(playerId, action, productionBudgets, technologies, reservedMillions,
                    targetPlayerId, targetCityIndex, weaponName, payloadName, prepareCarrier,
                    diplomacyOption, Collections.<GameState.ShipStrike>emptyList(), null);
        }

        private Decision(int playerId, GameState.Action action,
                         Map<String, Integer> productionBudgets,
                         List<String> technologies, int reservedMillions,
                         int targetPlayerId, int targetCityIndex, String weaponName,
                         String payloadName, boolean prepareCarrier, int diplomacyOption,
                         List<GameState.ShipStrike> shipStrikes) {
            this(playerId, action, productionBudgets, technologies, reservedMillions,
                    targetPlayerId, targetCityIndex, weaponName, payloadName, prepareCarrier,
                    diplomacyOption, shipStrikes, null);
        }

        private Decision(int playerId, GameState.Action action,
                         Map<String, Integer> productionBudgets,
                         List<String> technologies, int reservedMillions,
                         int targetPlayerId, int targetCityIndex, String weaponName,
                         String payloadName, boolean prepareCarrier, int diplomacyOption,
                         List<GameState.ShipStrike> shipStrikes, String spyBuilding) {
            this.playerId = playerId;
            this.action = action;
            this.productionBudgets = Collections.unmodifiableMap(
                    new LinkedHashMap<>(productionBudgets));
            this.technologies = Collections.unmodifiableList(new ArrayList<>(technologies));
            this.reservedMillions = reservedMillions;
            this.targetPlayerId = targetPlayerId;
            this.targetCityIndex = targetCityIndex;
            this.weaponName = weaponName;
            this.payloadName = payloadName;
            this.prepareCarrier = prepareCarrier;
            this.diplomacyOption = diplomacyOption;
            this.shipStrikes = Collections.unmodifiableList(new ArrayList<>(shipStrikes));
            this.spyBuilding = spyBuilding;
        }
    }

    static final class ScoredAction {
        final GameState.Action action;
        final double score;

        ScoredAction(GameState.Action action, double score) {
            this.action = action;
            this.score = score;
        }
    }

    private static final class SpyTarget {
        final int playerId;
        final int spyLevel;

        SpyTarget(int playerId, int spyLevel) {
            this.playerId = playerId;
            this.spyLevel = spyLevel;
        }
    }

    /** Plans the source-ranked action slots before any nation's actions resolve. */
    public static List<Decision> plan(GameState state, GameState.PlayerState player,
                                      Random random) {
        return planScoredActions(state, player, random,
                rankedActions(state, player, random));
    }

    /**
     * Mirrors Model.doTurn: score every living opponent first, then select any
     * opponent's targets and action slots in player order. Score calculation
     * consumes random draws, so interleaving planning here changes later scores.
     */
    public static List<Decision> planTurn(GameState state, Random random) {
        List<GameState.PlayerState> opponents = new ArrayList<>();
        Map<Integer, List<ScoredAction>> rankedByPlayer = new LinkedHashMap<>();
        for (GameState.PlayerState player : state.getPlayers()) {
            if (!player.human && player.alive) {
                opponents.add(player);
            }
        }
        // Model.doTurn scores with `for-in` over activePlayerArr. The recovered
        // ActionScript array iterator visits keys from last to first; target
        // assignment below still runs in player order.
        GameState.PlayerState[] players = state.getPlayers();
        for (int playerId = players.length - 1; playerId >= 0; playerId--) {
            GameState.PlayerState player = players[playerId];
            if (!player.human && player.alive) {
                rankedByPlayer.put(player.id, rankedActions(state, player, random));
            }
        }

        List<Decision> decisions = new ArrayList<>(opponents.size() * 2);
        for (GameState.PlayerState opponent : opponents) {
            decisions.addAll(planScoredActions(state, opponent, random,
                    rankedByPlayer.get(opponent.id)));
        }
        return decisions;
    }

    private static List<ScoredAction> rankedActions(GameState state,
                                                    GameState.PlayerState player,
                                                    Random random) {
        List<ScoredAction> scores = scoreActions(state, player, random);
        scores.sort(Comparator.comparingDouble((ScoredAction item) -> item.score).reversed());
        return scores;
    }

    static List<Decision> planScoredActions(GameState state,
                                            GameState.PlayerState player,
                                            Random random,
                                            List<ScoredAction> scores) {

        List<Decision> decisions = new ArrayList<>(2);
        List<ScoredAction> availableScores = new ArrayList<>(scores.size());
        for (ScoredAction scored : scores) {
            if (!state.isActionBioloaded(player.id, scored.action)) {
                availableScores.add(scored);
            }
        }
        scores = availableScores;
        if (scores.isEmpty()) {
            return decisions;
        }

        Map<String, Integer> plannedWeaponUse = new LinkedHashMap<>();
        ScoredAction first = scores.get(0);
        ScoredAction second = scores.size() > 1 ? scores.get(1) : null;
        boolean duplicateAttack = second != null
                && first.action == GameState.Action.ATTACK
                && first.score - 50 > second.score;
        int attackSlot = first.action == GameState.Action.ATTACK ? 0
                : second != null && second.action == GameState.Action.ATTACK ? 1 : -1;

        if (attackSlot >= 0) {
            ScoredAction companion = duplicateAttack ? null
                    : scores.size() > 1 ? scores.get(1 - attackSlot) : null;
            List<Decision> attackPlan = planSourceAttack(state, player, random,
                    duplicateAttack, plannedWeaponUse);
            if (!attackPlan.isEmpty()) {
                boolean preparingCarrier = attackPlan.size() == 1
                        && attackPlan.get(0).action == GameState.Action.PREPARED_ATTACK
                        && attackPlan.get(0).prepareCarrier;
                boolean readySingleAttack = player.getPreparedCarrierName() != null
                        && !"ship".equals(player.getWeapons().get(
                        player.getPreparedCarrierName()).definition.type)
                        && attackPlan.size() == 1;
                if (attackSlot == 1 && companion != null && readySingleAttack) {
                    Decision precedingOrder = planRankedAction(state, player, companion,
                            random, 1, plannedWeaponUse);
                    if (precedingOrder != null) {
                        decisions.add(precedingOrder);
                        reserve(player, precedingOrder);
                        markPlannedUse(precedingOrder, plannedWeaponUse);
                    }
                }
                for (Decision decision : attackPlan) {
                    decisions.add(decision);
                    reserve(player, decision);
                    markPlannedUse(decision, plannedWeaponUse);
                }
                // A new carrier prep may displace the scored attack's original
                // slot; the source planner then fills the freed slot from the
                // remaining ranked actions. Seeded source traces include this
                // follow-up action after a carrier prep in slot two.
                if (attackSlot == 1 && companion != null && preparingCarrier) {
                    Decision secondOrder = planRankedAction(state, player, companion,
                            random, 2, plannedWeaponUse);
                    if (secondOrder != null) {
                        decisions.add(secondOrder);
                        reserve(player, secondOrder);
                        markPlannedUse(secondOrder, plannedWeaponUse);
                    }
                    return decisions;
                }
                // A not-ready attack resets its companion slot in the reference.
                // A ready carrier's single attack leaves the other scored action intact.
                boolean attackResetsCompanion = !readySingleAttack;
                if (attackSlot == 0 && companion != null && !attackResetsCompanion) {
                    Decision secondOrder = planRankedAction(state, player, companion,
                            random, 2, plannedWeaponUse);
                    if (secondOrder != null) {
                        decisions.add(secondOrder);
                        reserve(player, secondOrder);
                        markPlannedUse(secondOrder, plannedWeaponUse);
                    }
                }
                return decisions;
            }
            if (attackSlot == 1) {
                Decision precedingOrder = companion == null ? null
                        : planRankedAction(state, player, companion, random, 1,
                        plannedWeaponUse);
                if (precedingOrder != null) {
                    decisions.add(precedingOrder);
                    reserve(player, precedingOrder);
                    markPlannedUse(precedingOrder, plannedWeaponUse);
                    List<Decision> retryAttackPlan = planSourceAttack(state, player,
                            random, false, plannedWeaponUse);
                    if (!retryAttackPlan.isEmpty()) {
                        for (Decision decision : retryAttackPlan) {
                            if (decisions.size() == 2) {
                                break;
                            }
                            addPlannedDecision(player, decision, decisions,
                                    plannedWeaponUse);
                        }
                        return decisions;
                    }
                    return planRemainingRankedActions(state, player, scores, random,
                            decisions, plannedWeaponUse, 2);
                }
            }
        }

        return planRemainingRankedActions(state, player, scores, random,
                decisions, plannedWeaponUse, 0);
    }

    /**
     * The Flash planner advances through its ranked actions whenever getTarget
     * rejects an unavailable order. Keep trying lower-ranked orders until both
     * slots are filled or the source action list is exhausted.
     */
    private static List<Decision> planRemainingRankedActions(
            GameState state, GameState.PlayerState player, List<ScoredAction> scores,
            Random random, List<Decision> decisions,
            Map<String, Integer> plannedWeaponUse, int firstActionIndex) {
        for (int index = firstActionIndex; index < scores.size()
                && decisions.size() < 2; index++) {
            ScoredAction scored = scores.get(index);
            if (scored.action == GameState.Action.ATTACK) {
                boolean duplicateAttack = index == 0 && scores.size() > 1
                        && scored.score - 50 > scores.get(1).score;
                List<Decision> attackPlan = planSourceAttack(state, player, random,
                        duplicateAttack, plannedWeaponUse);
                for (Decision decision : attackPlan) {
                    if (decisions.size() == 2) {
                        break;
                    }
                    addPlannedDecision(player, decision, decisions, plannedWeaponUse);
                    if (decision.action == GameState.Action.PREPARED_ATTACK
                            && decision.prepareCarrier) {
                        return decisions;
                    }
                }
                continue;
            }

            Decision decision = planRankedAction(state, player, scored, random,
                    decisions.size() + 1, plannedWeaponUse);
            if (decision != null) {
                addPlannedDecision(player, decision, decisions, plannedWeaponUse);
                if (decision.action == GameState.Action.PREPARED_ATTACK
                        && decision.prepareCarrier) {
                    return decisions;
                }
            }
        }
        return decisions;
    }

    private static void addPlannedDecision(GameState.PlayerState player,
                                           Decision decision,
                                           List<Decision> decisions,
                                           Map<String, Integer> plannedWeaponUse) {
        decisions.add(decision);
        reserve(player, decision);
        markPlannedUse(decision, plannedWeaponUse);
    }

    private static Decision planRankedAction(GameState state,
                                             GameState.PlayerState player,
                                             ScoredAction scored, Random random,
                                             int slot,
                                             Map<String, Integer> plannedWeaponUse) {
        if (scored.action == GameState.Action.ATTACK) {
            return null;
        }
        return planAction(state, player, scored.action, random, slot, plannedWeaponUse);
    }

    private static void markPlannedUse(Decision decision,
                                       Map<String, Integer> plannedWeaponUse) {
        if (decision.weaponName != null && (decision.action == GameState.Action.ATTACK
                || decision.action == GameState.Action.PREPARED_ATTACK
                && decision.prepareCarrier)) {
            Integer count = plannedWeaponUse.get(decision.weaponName);
            plannedWeaponUse.put(decision.weaponName, count == null ? 1 : count + 1);
        }
        if (decision.action == GameState.Action.PREPARED_ATTACK) {
            plannedWeaponUse.put("@prepared", 1);
        } else if (decision.action == GameState.Action.DEFENSE) {
            plannedWeaponUse.put("@defense", 1);
        }
    }

    private static void reserve(GameState.PlayerState player, Decision decision) {
        player.turnMoneyMillions = Math.max(0,
                player.turnMoneyMillions - decision.reservedMillions);
    }

    static List<ScoredAction> scoreActions(GameState state,
                                           GameState.PlayerState player,
                                           Random random) {
        List<GameState.PlayerState> alive = new ArrayList<>();
        int largestPopulation = 0;
        int largestCity = 0;
        for (GameState.PlayerState candidate : state.getPlayers()) {
            if (!candidate.alive) {
                continue;
            }
            alive.add(candidate);
            largestPopulation = Math.max(largestPopulation, candidate.populationMillions);
            for (CityState city : candidate.getCities()) {
                largestCity = Math.max(largestCity, city.populationMillions);
            }
        }
        if (largestPopulation == 0) {
            largestPopulation = 1;
        }

        double lowestRegard = lowestRegardForScore(state, player);
        double attack = baseAttackScore(state, player, lowestRegard);
        double attackTransferredToProduction = 0;
        String preparedCarrier = player.getPreparedCarrierName();
        if (preparedCarrier != null) {
            if (state.getAvailablePreparedPayloads(player.id, preparedCarrier).isEmpty()) {
                attackTransferredToProduction = attack;
                attack = -999;
            } else {
                attack += 200;
            }
        } else if (!hasAvailableAttackForScoring(state, player)) {
            attackTransferredToProduction = attack;
            attack = -999;
        }
        if (player.isSpecialPerson()
                && (player.getWeaponStockByName("ship") < 0
                || player.getWeaponStockByName("warhead3") < 3)) {
            attackTransferredToProduction += attack;
            attack = -999;
        }
        if (player.isSuicidePerson() && player.getWeaponStockByName("soldier4") < 2) {
            attackTransferredToProduction += attack;
            attack = -999;
        }

        double preCounter = preCounterScore(state, player,
                state.getPreparedThreatPlayerIds(player.id));
        double religion = 20 + Math.round(player.populationMillions
                / (double) largestPopulation * 100) - player.religion * 2.0;
        double propaganda = 42;
        if (player.religion < 30) {
            religion += 42;
        }
        if (player.religion > 90) {
            propaganda += 20;
        } else if (player.religion > 60) {
            propaganda += 10;
        }

        double diplomacy = randomInclusive(random, 0, 90);
        if (randomInclusive(random, 1, 3) == 2) {
            diplomacy = 0;
        }

        double stockLevel = weaponStockLevel(player);
        double resources = 0;
        if (player.moneyMillions < 150) {
            resources = 75 - Math.round((float) stockLevel)
                    - player.moneyMillions / 10000.0;
        }
        if (player.isSpecialPerson()) {
            if (player.getWeaponStockByName("ship") < 1) {
                resources += 250;
            }
            if (player.getWeaponStockByName("warhead3") < 3) {
                resources += 250;
            }
        }

        double production = attackTransferredToProduction
                + 55 - Math.round((float) stockLevel);
        for (GameState.PlayerState other : alive) {
            if (weaponStockLevel(other) > stockLevel) {
                production += 15;
            }
        }
        production += player.moneyMillions;
        if (player.moneyMillions <= 20) {
            resources += production;
            production = 0;
        }

        double personalityResearchBonus = 0;
        if (player.isSpecialPerson() && production > 0) {
            if (!player.hasTechnology("ship") || !player.hasTechnology("warhead3")) {
                personalityResearchBonus += production;
                production = -10;
            }
        }
        if (player.isSuicidePerson() && production > 0
                && !player.hasTechnology("soldier4")) {
            personalityResearchBonus += production;
        }

        double nuclearKill = bestInventedPayloadKills(player, "warhead");
        double biologicalKill = bestInventedPayloadKills(player, "bioload");
        int techCount = player.getInventedTechnologies().size();
        double research = Math.min(60, largestCity - nuclearKill - biologicalKill)
                + researchTurnRatio(state.getTurn(), techCount)
                + player.moneyMillions + personalityResearchBonus;
        int resourceThreshold = Math.round((float) player.populationMillions
                / new int[]{9, 10, 12}[state.getDifficulty()]) * 2;
        if (player.moneyMillions <= resourceThreshold) {
            resources += research;
            research = 10;
        }
        if (player.isSpecialPerson() && player.hasTechnology("ship")
                && player.hasTechnology("warhead3") && research > 0) {
            production += research;
            research = -100;
        }

        // Model.calcPoints snapshots actionArr before adding 50 to a local attack
        // accumulator. That late source bonus does not change the ranked score.
        List<ScoredAction> scores = new ArrayList<>(8);
        scores.add(new ScoredAction(GameState.Action.ATTACK, attack));
        scores.add(new ScoredAction(GameState.Action.DEFENSE, preCounter));
        scores.add(new ScoredAction(GameState.Action.PROPAGANDA, propaganda));
        scores.add(new ScoredAction(GameState.Action.DIPLOMACY, diplomacy));
        scores.add(new ScoredAction(GameState.Action.MASS_MEDIA, religion));
        scores.add(new ScoredAction(GameState.Action.RESOURCES, resources));
        scores.add(new ScoredAction(GameState.Action.PRODUCTION, production));
        scores.add(new ScoredAction(GameState.Action.RESEARCH, research));
        for (int index = 0; index < scores.size(); index++) {
            ScoredAction scored = scores.get(index);
            if (state.isActionBioloaded(player.id, scored.action)) {
                scores.set(index, new ScoredAction(scored.action, -999));
            }
        }
        return scores;
    }

    static double scoreForAction(GameState state, GameState.PlayerState player,
                                 Random random, GameState.Action action) {
        if (action == GameState.Action.PREPARED_ATTACK) {
            return -999;
        }
        for (ScoredAction scored : scoreActions(state, player, random)) {
            if (scored.action == action) {
                return scored.score;
            }
        }
        throw new IllegalArgumentException("Action is not scored: " + action);
    }

    /** The reference caps the research time contribution at 60 turns. */
    static double researchTurnRatio(int turn, int technologyCount) {
        return Math.min(60, turn) / (double) Math.max(1, technologyCount) * 9;
    }

    static double baseAttackScore(GameState state, GameState.PlayerState player) {
        return baseAttackScore(state, player, lowestRegardForScore(state, player));
    }

    private static double baseAttackScore(GameState state, GameState.PlayerState player,
                                          double lowestRegard) {
        return Math.max(0, ATTACK_BASE[state.getDifficulty()] - lowestRegard / 2.0);
    }

    private static double lowestRegardForScore(GameState state, GameState.PlayerState player) {
        double lowestRegard = 100;
        for (GameState.PlayerState other : state.getPlayers()) {
            if (other != null) {
                lowestRegard = Math.min(lowestRegard,
                        player.getRegardTowardExact(other.id));
            }
        }
        return lowestRegard;
    }

    static double preCounterScore(GameState state, GameState.PlayerState defender,
                                  List<Integer> preparedThreatPlayerIds) {
        double highestThreatScore = 0;
        for (int threatPlayerId : preparedThreatPlayerIds) {
            double threatScore = preparedThreatScore(state, defender, threatPlayerId);
            highestThreatScore = Math.max(highestThreatScore, threatScore);
        }
        return highestThreatScore;
    }

    static int mostThreateningPreparedEnemy(GameState state,
                                            GameState.PlayerState defender,
                                            List<Integer> threatPlayerIds) {
        List<Integer> highestScoringThreats = highestScoringPreparedThreats(
                state, defender, threatPlayerIds);
        return highestScoringThreats.isEmpty() ? -1 : highestScoringThreats.get(0);
    }

    private static List<Integer> highestScoringPreparedThreats(
            GameState state, GameState.PlayerState defender, List<Integer> threatPlayerIds) {
        List<Integer> highestScoringThreats = new ArrayList<>();
        double highestScore = 0;
        for (int threatPlayerId : threatPlayerIds) {
            double score = preparedThreatScore(state, defender, threatPlayerId);
            if (score > highestScore) {
                highestScore = score;
                highestScoringThreats.clear();
                highestScoringThreats.add(threatPlayerId);
            } else if (score == highestScore && score >= 0) {
                highestScoringThreats.add(threatPlayerId);
            }
        }
        highestScoringThreats.sort(Comparator.comparingInt(
                (Integer playerId) -> state.getPlayerState(playerId).getStrength())
                .reversed());
        return highestScoringThreats;
    }

    private static double preparedThreatScore(GameState state,
                                              GameState.PlayerState defender,
                                              int threatPlayerId) {
        GameState.PlayerState threat = state.getPlayerState(threatPlayerId);
        double score = 86 - defender.getRegardTowardExact(threat.id);
        if (defender.populationMillions < 20) {
            score += 10;
        }
        return score;
    }

    static double weaponStockLevel(GameState.PlayerState player) {
        int researched = 0;
        int satisfied = 0;
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (weapon.definition.productionGroup == 8
                    || !player.hasTechnology(weapon.definition.technology)) {
                continue;
            }
            researched++;
            if (weapon.inStock >= player.getDesiredStock(weapon.definition.name)) {
                satisfied++;
            }
        }
        return researched == 0 ? 0 : satisfied / (double) researched * 100;
    }

    private static int bestInventedPayloadKills(GameState.PlayerState player, String prefix) {
        int best = 0;
        for (int level = 1; level <= 4; level++) {
            String name = prefix + level;
            GameState.WeaponState weapon = player.getWeapons().get(name);
            if (weapon != null && (level == 1 || player.hasTechnology(name))) {
                best = Math.max(best, weapon.definition.maxKillsMillions);
            }
        }
        return best;
    }

    static Decision planAction(GameState state, GameState.PlayerState player,
                               GameState.Action action, Random random, int slot,
                               Map<String, Integer> plannedWeaponUse) {
        if (state.isActionBioloaded(player.id, action)) {
            return null;
        }
        switch (action) {
            case ATTACK:
                List<Decision> sourceAttack = planSourceAttack(state, player, random,
                        false, plannedWeaponUse);
                return sourceAttack.isEmpty() ? null : sourceAttack.get(0);
            case PREPARED_ATTACK:
                return planPreparedAttack(state, player, random, plannedWeaponUse);
            case DEFENSE:
                return planDefense(state, player, plannedWeaponUse);
            case PROPAGANDA:
                return planPropaganda(state, player);
            case DIPLOMACY:
                return planDiplomacy(state, player, random);
            case MASS_MEDIA:
                return simple(player.id, action);
            case RESOURCES:
                return player.moneyMillions < 150 ? simple(player.id, action) : null;
            case PRODUCTION:
                return planProduction(state, player, random);
            case RESEARCH:
                return planResearch(state, player, random, slot);
            default:
                return null;
        }
    }

    /** Applies getTarget's carrier/direct choice and companion-slot behavior. */
    private static List<Decision> planSourceAttack(GameState state,
                                                   GameState.PlayerState attacker,
                                                   Random random,
                                                   boolean duplicateAttack,
                                                   Map<String, Integer> plannedUse) {
        List<Decision> decisions = new ArrayList<>(2);
        GameState.PlayerState target = attackTarget(state, attacker, random);
        if (target == null) {
            return decisions;
        }

        String readyCarrier = attacker.getPreparedCarrierName();
        if (readyCarrier != null) {
            List<String> payloads = state.getAvailablePreparedPayloads(
                    attacker.id, readyCarrier);
            if (payloads.isEmpty()) {
                return decisions;
            }
            GameState.WeaponState carrier = attacker.getWeapons().get(readyCarrier);
            if ("ship".equals(carrier.definition.type)) {
                Decision ship = planShipPreparedAttack(state, attacker, readyCarrier,
                        target, random, plannedUse);
                if (ship == null) {
                    return decisions;
                }
                decisions.add(ship);
                GameState.WeaponState conventional = strongestAvailableSoldier(
                        attacker, plannedUse);
                Integer cityIndex = firstPopulatedCity(target);
                if (conventional != null && cityIndex != null) {
                    decisions.add(attackDecision(attacker.id, target.id, cityIndex,
                            conventional.definition.name));
                }
                return decisions;
            }

            GameState.WeaponState payload = highestKillPayload(state, attacker,
                    readyCarrier, plannedUse);
            if (payload == null) {
                return decisions;
            }
            GameState.WeaponState conventional = strongestAvailableSoldier(
                    attacker, plannedUse);
            if (duplicateAttack && conventional != null) {
                CityState pairedCity = largestCityBelowCombinedKills(target,
                        payload.definition.maxKillsMillions
                                + conventional.definition.maxKillsMillions);
                int preparedCity;
                int conventionalCity;
                if (pairedCity != null) {
                    preparedCity = pairedCity.index;
                    conventionalCity = pairedCity.index;
                } else {
                    preparedCity = randomPopulatedCity(target, random);
                    conventionalCity = randomPopulatedCity(target, random);
                }
                if (preparedCity >= 0 && conventionalCity >= 0) {
                    decisions.add(preparedDecision(attacker.id, target.id, preparedCity,
                            readyCarrier, payload.definition.name));
                    decisions.add(attackDecision(attacker.id, target.id, conventionalCity,
                            conventional.definition.name));
                    return decisions;
                }
            }

            int cityIndex = lastCityAtOrBelowKills(target,
                    payload.definition.maxKillsMillions);
            if (cityIndex < 0) {
                cityIndex = randomPopulatedCity(target, random);
            }
            if (cityIndex >= 0) {
                decisions.add(preparedDecision(attacker.id, target.id, cityIndex,
                        readyCarrier, payload.definition.name));
            }
            return decisions;
        }

        List<String> carriers = sourceActiveCarriers(attacker, plannedUse);
        List<String> logicalCarriers = sourceLogicalCarriers(attacker);
        boolean allowPre = !carriers.isEmpty();
        boolean hasSpy = hasAvailableSpy(attacker, plannedUse);
        boolean allowDir = hasAvailableSoldier(attacker, plannedUse) || hasSpy;
        if (!allowDir && !allowPre && !hasSpy) {
            return decisions;
        }

        int selectedRoute = allowPre && allowDir ? randomInclusive(random, 1, 2)
                : allowPre ? 1 : 2;
        if (selectedRoute == 1) {
            String carrierName = randomAvailableCarrier(attacker, random, logicalCarriers);
            if (carrierName != null) {
                decisions.add(new Decision(attacker.id, GameState.Action.PREPARED_ATTACK,
                        Collections.<String, Integer>emptyMap(),
                        Collections.<String>emptyList(), 0, target.id, -1,
                        carrierName, null, true, -1));
            }
        } else {
            Decision direct = planDirectAttackForTarget(state, attacker, target,
                    random, plannedUse);
            if (direct != null) {
                decisions.add(direct);
            }
        }
        return decisions;
    }

    private static boolean hasAvailableSoldier(GameState.PlayerState player,
                                               Map<String, Integer> plannedUse) {
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (weapon.active && availableStock(weapon, plannedUse) > 0
                    && "soldier".equals(weapon.definition.type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAvailableSpy(GameState.PlayerState player,
                                           Map<String, Integer> plannedUse) {
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (weapon.active && availableStock(weapon, plannedUse) > 0
                    && "spy".equals(weapon.definition.type)) {
                return true;
            }
        }
        return false;
    }

    private static String randomAvailableCarrier(GameState.PlayerState player,
                                                 Random random,
                                                 List<String> carriers) {
        // WeaponStock.randomAvailableCarrier performs this unused draw first.
        randomInclusive(random, 1, 3);
        if (carriers.isEmpty()) {
            return null;
        }
        String[] typeOrder = {"rocket", "plane", "sub", "ship", "space"};
        List<String> availableTypes = new ArrayList<>(typeOrder.length);
        for (String type : typeOrder) {
            for (String name : carriers) {
                GameState.WeaponState weapon = player.getWeapons().get(name);
                if (type.equals(weapon.definition.type)) {
                    availableTypes.add(type);
                    break;
                }
            }
        }
        if (availableTypes.isEmpty()) {
            return null;
        }
        String selectedType = availableTypes.get(randomInclusive(random, 0,
                availableTypes.size() - 1));
        for (String name : carriers) {
            GameState.WeaponState weapon = player.getWeapons().get(name);
            if (selectedType.equals(weapon.definition.type)) {
                // The source returns the first matching entry in logicalCarrierArr.
                // That array preserves weapon catalog order, even when a later
                // carrier of the same type has a higher level.
                return name;
            }
        }
        return null;
    }

    private static List<String> sourceActiveCarriers(GameState.PlayerState player,
                                                     Map<String, Integer> plannedUse) {
        List<String> carriers = new ArrayList<>();
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (isSourceCarrier(weapon) && weapon.active
                    && availableStock(weapon, plannedUse) > 0) {
                carriers.add(weapon.definition.name);
            }
        }
        return carriers;
    }

    private static List<String> sourceLogicalCarriers(GameState.PlayerState player) {
        List<String> carriers = new ArrayList<>();
        for (GameState.WeaponState carrier : player.getWeapons().values()) {
            if (isSourceCarrier(carrier) && carrier.inStock > 0
                    && sourcePayloadAvailable(player, carrier)) {
                carriers.add(carrier.definition.name);
            }
        }
        return carriers;
    }

    private static boolean sourcePayloadAvailable(GameState.PlayerState player,
                                                  GameState.WeaponState carrier) {
        String type = carrier.definition.type;
        if ("rocket".equals(type)) {
            // WeaponStock's rocket branch checks level without checking the stock
            // or category of the candidate weapon.
            for (GameState.WeaponState weapon : player.getWeapons().values()) {
                if (weapon.definition.level >= carrier.definition.level) {
                    return true;
                }
            }
        } else if ("plane".equals(type)) {
            for (GameState.WeaponState payload : player.getWeapons().values()) {
                if (isSourcePayload(payload) && payload.inStock > 0
                        && carrier.definition.level >= payload.definition.level) {
                    return true;
                }
            }
        } else if ("sub".equals(type) || "ship".equals(type)) {
            for (GameState.WeaponState payload : player.getWeapons().values()) {
                if (isSourcePayload(payload) && payload.inStock > 0) {
                    return true;
                }
            }
        } else if ("space".equals(type)) {
            GameState.WeaponState specialLoad = player.getWeapons().get("specialLoad");
            return specialLoad != null && specialLoad.inStock > 0;
        }
        return false;
    }

    private static boolean isSourceCarrier(GameState.WeaponState weapon) {
        String type = weapon.definition.type;
        return "rocket".equals(type) || "plane".equals(type) || "sub".equals(type)
                || "ship".equals(type) || "space".equals(type);
    }

    private static boolean isSourcePayload(GameState.WeaponState weapon) {
        return "rocketload".equals(weapon.definition.type);
    }

    private static GameState.WeaponState highestKillPayload(GameState state,
                                                            GameState.PlayerState player,
                                                            String carrierName,
                                                            Map<String, Integer> plannedUse) {
        GameState.WeaponState highest = null;
        for (String name : state.getAvailablePreparedPayloads(player.id, carrierName)) {
            GameState.WeaponState payload = player.getWeapons().get(name);
            if (availableStock(payload, plannedUse) > 0 && (highest == null
                    || payload.definition.maxKillsMillions
                    > highest.definition.maxKillsMillions)) {
                highest = payload;
            }
        }
        return highest;
    }

    private static GameState.WeaponState strongestAvailableSoldier(
            GameState.PlayerState player, Map<String, Integer> plannedUse) {
        GameState.WeaponState strongest = null;
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (!weapon.active || availableStock(weapon, plannedUse) <= 0
                    || !"soldier".equals(weapon.definition.type)) {
                continue;
            }
            if (strongest == null || weapon.definition.maxKillsMillions
                    > strongest.definition.maxKillsMillions) {
                strongest = weapon;
            }
        }
        return strongest;
    }

    private static int lastCityAtOrBelowKills(GameState.PlayerState target, int maxKills) {
        int selected = -1;
        for (CityState city : target.getCities()) {
            if (city.populationMillions <= maxKills && city.populationMillions > 0) {
                selected = city.index;
            }
        }
        return selected;
    }

    private static CityState largestCityBelowCombinedKills(GameState.PlayerState target,
                                                            int combinedKills) {
        CityState selected = null;
        for (CityState city : target.getCities()) {
            if (city.populationMillions > 0 && city.populationMillions < combinedKills
                    && (selected == null || city.populationMillions
                    > selected.populationMillions)) {
                selected = city;
            }
        }
        return selected;
    }

    private static Integer firstPopulatedCity(GameState.PlayerState target) {
        for (CityState city : target.getCities()) {
            if (city.populationMillions > 0) {
                return city.index;
            }
        }
        return null;
    }

    private static int randomPopulatedCity(GameState.PlayerState target, Random random) {
        List<Integer> populated = new ArrayList<>();
        for (CityState city : target.getCities()) {
            if (city.populationMillions > 0) {
                populated.add(city.index);
            }
        }
        return populated.isEmpty() ? -1
                : populated.get(randomInclusive(random, 0, populated.size() - 1));
    }

    private static Decision preparedDecision(int playerId, int targetId, int cityIndex,
                                             String carrierName, String payloadName) {
        return new Decision(playerId, GameState.Action.PREPARED_ATTACK,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                0, targetId, cityIndex, carrierName, payloadName, false, -1);
    }

    private static Decision attackDecision(int playerId, int targetId, int cityIndex,
                                           String weaponName) {
        return attackDecision(playerId, targetId, cityIndex, weaponName, null);
    }

    private static Decision attackDecision(int playerId, int targetId, int cityIndex,
                                           String weaponName, String spyBuilding) {
        return new Decision(playerId, GameState.Action.ATTACK,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                0, targetId, cityIndex, weaponName, null, false, -1,
                Collections.<GameState.ShipStrike>emptyList(), spyBuilding);
    }

    private static String randomSpyBuilding(Random random) {
        // Model.getTarget uses randRange(1, 5); the source array's index zero
        // is a sentinel followed by these five mission-building names.
        return SPY_BUILDINGS[randomInclusive(random, 0, SPY_BUILDINGS.length - 1)];
    }

    private static Decision planDirectAttackForTarget(GameState state,
                                                       GameState.PlayerState attacker,
                                                       GameState.PlayerState target,
                                                       Random random,
                                                       Map<String, Integer> plannedUse) {
        List<GameState.WeaponState> availableSoldiers = new ArrayList<>(4);
        for (GameState.WeaponState weapon : attacker.getWeapons().values()) {
            if (weapon.active && availableStock(weapon, plannedUse) > 0
                    && "soldier".equals(weapon.definition.type)) {
                availableSoldiers.add(weapon);
            }
        }
        availableSoldiers.sort(Comparator.comparingInt(
                (GameState.WeaponState item) -> item.definition.maxKillsMillions)
                .reversed());
        GameState.WeaponState selected = null;
        int initialCity = -1;
        if (!availableSoldiers.isEmpty()) {
            initialCity = randomPopulatedCity(target, random);
            if (initialCity < 0) {
                return null;
            }
            int sourceIndex = randomInclusive(random, 0, 3);
            if (sourceIndex < availableSoldiers.size()) {
                selected = availableSoldiers.get(sourceIndex);
            }
        }
        SpyTarget spyTarget = findSpyTarget(state, attacker, random);
        boolean useSpy = spyTarget != null
                && (selected == null || randomInclusive(random, 0, 6) == 3);
        if (useSpy) {
            selected = attacker.getWeapons().get("spy" + spyTarget.spyLevel);
            target = state.getPlayerState(spyTarget.playerId);
            initialCity = randomPopulatedCity(target, random);
            String spyBuilding = randomSpyBuilding(random);
            return initialCity < 0 ? null : attackDecision(attacker.id, target.id,
                    initialCity, selected.definition.name, spyBuilding);
        }
        return selected == null || initialCity < 0 ? null
                : attackDecision(attacker.id, target.id, initialCity,
                selected.definition.name);
    }

    static Decision planDefense(GameState state, GameState.PlayerState defender,
                                Map<String, Integer> plannedUse) {
        if (plannedUse.containsKey("@defense")) {
            return null;
        }
        List<Integer> threats = highestScoringPreparedThreats(state, defender,
                state.getPreparedThreatPlayerIds(defender.id));
        for (int threatId : threats) {
            String counter = state.getCounterWeaponForThreat(defender.id, threatId);
            if (counter != null) {
                return new Decision(defender.id, GameState.Action.DEFENSE,
                        Collections.<String, Integer>emptyMap(),
                        Collections.<String>emptyList(), 0, -1, -1, counter, -1);
            }
        }
        return null;
    }

    private static Decision planPreparedAttack(GameState state,
                                               GameState.PlayerState attacker,
                                               Random random,
                                               Map<String, Integer> plannedUse) {
        if (plannedUse.containsKey("@prepared")) {
            return null;
        }
        String carrierName = attacker.getPreparedCarrierName();
        boolean prepare = carrierName == null;
        if (prepare) {
            for (String candidate : state.getAvailablePreparedCarriers(attacker.id)) {
                GameState.WeaponState carrier = attacker.getWeapons().get(candidate);
                if (availableStock(carrier, plannedUse) > 0
                        && (carrierName == null || carrier.definition.level
                        > attacker.getWeapons().get(carrierName).definition.level)) {
                    carrierName = candidate;
                }
            }
        }
        if (carrierName == null) {
            return null;
        }
        if (prepare) {
            return new Decision(attacker.id, GameState.Action.PREPARED_ATTACK,
                    Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                    0, -1, -1, carrierName, null, true, -1);
        }
        GameState.WeaponState carrier = attacker.getWeapons().get(carrierName);
        if ("ship".equals(carrier.definition.type)) {
            return planShipPreparedAttack(state, attacker, carrierName, random,
                    plannedUse);
        }
        List<String> payloads = state.getAvailablePreparedPayloads(attacker.id, carrierName);
        GameState.WeaponState selectedPayload = null;
        for (String payloadName : payloads) {
            GameState.WeaponState payload = attacker.getWeapons().get(payloadName);
            if (selectedPayload == null || payload.definition.maxKillsMillions
                    > selectedPayload.definition.maxKillsMillions) {
                selectedPayload = payload;
            }
        }
        GameState.PlayerState target = mostHostileTarget(state, attacker, false);
        if (selectedPayload == null || target == null) {
            return null;
        }
        List<Integer> populated = new ArrayList<>();
        for (CityState city : target.getCities()) {
            if (city.populationMillions > 0) {
                populated.add(city.index);
            }
        }
        if (populated.isEmpty()) {
            return null;
        }
        int cityIndex = populated.get(randomInclusive(random, 0, populated.size() - 1));
        return new Decision(attacker.id, GameState.Action.PREPARED_ATTACK,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                0, target.id, cityIndex, carrierName,
                selectedPayload.definition.name, false, -1);
    }

    private static Decision planShipPreparedAttack(GameState state,
                                                   GameState.PlayerState attacker,
                                                   String carrierName, Random random,
                                                   Map<String, Integer> plannedWeaponUse) {
        GameState.PlayerState target = mostHostileTarget(state, attacker, false);
        return planShipPreparedAttack(state, attacker, carrierName, target, random,
                plannedWeaponUse);
    }

    private static Decision planShipPreparedAttack(GameState state,
                                                   GameState.PlayerState attacker,
                                                   String carrierName,
                                                   GameState.PlayerState target,
                                                   Random random,
                                                   Map<String, Integer> plannedWeaponUse) {
        if (target == null) {
            return null;
        }
        List<GameState.WeaponState> availablePayloadUnits = new ArrayList<>();
        for (String payloadName : state.getAvailablePreparedPayloads(
                attacker.id, carrierName)) {
            GameState.WeaponState payload = attacker.getWeapons().get(payloadName);
            int available = availableStock(payload, plannedWeaponUse);
            for (int stock = 0; stock < available; stock++) {
                availablePayloadUnits.add(payload);
            }
        }
        availablePayloadUnits.sort(Comparator.comparingInt(
                (GameState.WeaponState weapon) -> weapon.definition.maxKillsMillions)
                .reversed());
        if (availablePayloadUnits.size() < 3) {
            return null;
        }
        List<Integer> populated = new ArrayList<>();
        for (CityState city : target.getCities()) {
            if (city.populationMillions > 0) {
                populated.add(city.index);
            }
        }
        if (populated.isEmpty()) {
            return null;
        }
        List<GameState.ShipStrike> strikes = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            GameState.WeaponState payload = availablePayloadUnits.get(index);
            int cityIndex = populated.get(randomInclusive(random, 0, populated.size() - 1));
            strikes.add(new GameState.ShipStrike(target.id, cityIndex,
                    payload.definition.name));
        }
        return new Decision(attacker.id, GameState.Action.PREPARED_ATTACK,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                0, target.id, strikes.get(0).cityIndex, carrierName,
                null, false, -1, strikes);
    }

    static Decision planAttack(GameState state, GameState.PlayerState attacker,
                               Random random, Map<String, Integer> plannedUse) {
        GameState.PlayerState conventionalTarget = attackTarget(state, attacker, random);
        List<GameState.WeaponState> availableSoldiers = new ArrayList<>(4);
        for (GameState.WeaponState weapon : attacker.getWeapons().values()) {
            if (!weapon.active || availableStock(weapon, plannedUse) <= 0
                    || !"soldier".equals(weapon.definition.type)) {
                continue;
            }
            availableSoldiers.add(weapon);
        }
        availableSoldiers.sort(Comparator.comparingInt(
                (GameState.WeaponState item) -> item.definition.maxKillsMillions)
                .reversed());
        GameState.WeaponState selected = null;
        if (!availableSoldiers.isEmpty()) {
            if (plannedUse.containsKey("@prepared")
                    || attacker.getPreparedCarrierName() != null) {
                selected = availableSoldiers.get(0);
            } else {
                // The source selects a soldier from the max-kill-sorted list using
                // randRange(0, 3), even when fewer than four soldiers are available.
                int sourceIndex = randomInclusive(random, 0, 3);
                if (sourceIndex < availableSoldiers.size()) {
                    selected = availableSoldiers.get(sourceIndex);
                }
            }
        }

        SpyTarget spyTarget = findSpyTarget(state, attacker, random);
        boolean useSpy = spyTarget != null
                && (selected == null || randomInclusive(random, 0, 6) == 3);
        GameState.PlayerState target = conventionalTarget;
        String spyBuilding = null;
        if (useSpy) {
            selected = attacker.getWeapons().get("spy" + spyTarget.spyLevel);
            target = state.getPlayerState(spyTarget.playerId);
        }
        if (selected == null || target == null) {
            return null;
        }
        List<Integer> populated = new ArrayList<>();
        for (CityState city : target.getCities()) {
            if (city.populationMillions > 0) {
                populated.add(city.index);
            }
        }
        int cityIndex = populated.isEmpty() ? -1
                : populated.get(randomInclusive(random, 0, populated.size() - 1));
        if (useSpy) {
            // The Flash chooses the mission building after it picks a city.
            spyBuilding = randomSpyBuilding(random);
        }
        if (cityIndex < 0) {
            return null;
        }
        return new Decision(attacker.id, GameState.Action.ATTACK,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                0, target.id, cityIndex, selected.definition.name, null, false,
                -1, Collections.<GameState.ShipStrike>emptyList(), spyBuilding);
    }

    private static SpyTarget findSpyTarget(GameState state, GameState.PlayerState attacker,
                                           Random random) {
        boolean spyAvailable = false;
        for (GameState.WeaponState weapon : attacker.getWeapons().values()) {
            if ("spy".equals(weapon.definition.type) && weapon.active
                    && weapon.inStock > 0) {
                spyAvailable = true;
                break;
            }
        }
        if (!spyAvailable) {
            return null;
        }

        int bestEnemyStock = 0;
        int bestSpyLevel = 0;
        int bestTargetId = -1;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (!target.alive || target.id == attacker.id) {
                continue;
            }
            for (int level = 1; level <= AI_SPY_STOCK_REQUIREMENTS.length; level++) {
                int enemyStockAtLevel = stockAtLevel(target, level);
                GameState.WeaponState spy = attacker.getWeapons().get("spy" + level);
                if (enemyStockAtLevel > bestEnemyStock && level > bestSpyLevel
                        && enemyStockAtLevel >= AI_SPY_STOCK_REQUIREMENTS[level - 1]
                        // The source checks active stock once via spyAvailable, but
                        // availableSpies_obj (used for this per-level test) sums stock
                        // without filtering on active state.
                        && spy != null && spy.inStock > 0
                        && attacker.spyReadiness / 25 >= level) {
                    bestEnemyStock = enemyStockAtLevel;
                    bestSpyLevel = level;
                    bestTargetId = target.id;
                }
            }
        }

        int alertThreshold = randomInclusive(random, SPY_ALERT_MONEY[state.getDifficulty()],
                240);
        GameState.PlayerState human = state.getHuman();
        if (human.moneyMillions > alertThreshold) {
            int highestSpyLevel = highestAvailableSpyLevel(attacker);
            if (highestSpyLevel > 0) {
                return new SpyTarget(human.id, highestSpyLevel);
            }
        }
        return bestTargetId < 0 ? null : new SpyTarget(bestTargetId, bestSpyLevel);
    }

    private static int stockAtLevel(GameState.PlayerState player, int level) {
        int stock = 0;
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (weapon.definition.level == level) {
                stock += weapon.inStock;
            }
        }
        return stock;
    }

    private static int highestAvailableSpyLevel(GameState.PlayerState player) {
        int highestLevel = 0;
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if ("spy".equals(weapon.definition.type) && weapon.active
                    && weapon.inStock > 0) {
                highestLevel = Math.max(highestLevel, weapon.definition.level);
            }
        }
        return highestLevel;
    }

    private static int availableStock(GameState.WeaponState weapon,
                                      Map<String, Integer> plannedUse) {
        Integer reserved = plannedUse.get(weapon.definition.name);
        return weapon.inStock - (reserved == null ? 0 : reserved);
    }

    static boolean hasAvailableAttackForScoring(GameState state,
                                                GameState.PlayerState player) {
        String preparedCarrier = player.getPreparedCarrierName();
        if (preparedCarrier != null) {
            GameState.WeaponState carrier = player.getWeapons().get(preparedCarrier);
            return carrier != null && sourcePayloadAvailable(player, carrier);
        }
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (!weapon.active || weapon.inStock <= 0
                    || "anti".equals(weapon.definition.type)
                    || isSourceCarrier(weapon)) {
                continue;
            }
            if ("spy".equals(weapon.definition.type)) {
                // WeaponStock.spyAvailable is active stock, independent of readiness.
                return true;
            }
            if ("rocketload".equals(weapon.definition.type)) {
                if (sourcePayloadHasCarrierStock(player, weapon)) {
                    return true;
                }
            } else if ("specialLoad".equals(weapon.definition.type)) {
                GameState.WeaponState space = player.getWeapons().get("space");
                if (space != null && space.inStock > 0) {
                    return true;
                }
            } else {
                // The source includes active stocked non-carrier weapons here.
                return true;
            }
        }
        return false;
    }

    private static boolean sourcePayloadHasCarrierStock(GameState.PlayerState player,
                                                        GameState.WeaponState payload) {
        int level = payload.definition.level;
        GameState.WeaponState carrier = player.getWeapons().get("rocket" + level);
        if (carrier != null && carrier.inStock > 0) {
            return true;
        }
        for (String carrierName : new String[]{"plane1", "plane2", "ship", "sub"}) {
            carrier = player.getWeapons().get(carrierName);
            if (carrier != null && carrier.inStock > 0
                    && level <= carrier.definition.level) {
                return true;
            }
        }
        return false;
    }

    private static Decision planPropaganda(GameState state,
                                           GameState.PlayerState attacker) {
        GameState.PlayerState bestTarget = null;
        CityState bestCity = null;
        double bestValue = 0;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (!target.alive || target.id == attacker.id
                    || attacker.hasCeasefireWith(target.id)) {
                continue;
            }
            CityState largest = largestCity(target);
            if (largest == null || largest.populationMillions <= 0) {
                continue;
            }
            double value = 100 - target.religion + largest.populationMillions
                    - attacker.getRegardTowardExact(target.id);
            if (value > bestValue) {
                bestValue = value;
                bestTarget = target;
                bestCity = largest;
            }
        }
        return bestTarget == null ? null : new Decision(attacker.id,
                GameState.Action.PROPAGANDA, Collections.<String, Integer>emptyMap(),
                Collections.<String>emptyList(), 0, bestTarget.id, bestCity.index,
                null, -1);
    }

    static Decision planDiplomacy(GameState state, GameState.PlayerState actor,
                                  Random random) {
        GameState.PlayerState poorestPopulationTarget = null;
        int lowestTargetPopulation = 0;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (!isLivingDiplomacyOpponent(actor, target)) {
                continue;
            }
            if (poorestPopulationTarget == null || lowestTargetPopulation == 0
                    || target.populationMillions < lowestTargetPopulation) {
                poorestPopulationTarget = target;
                lowestTargetPopulation = target.populationMillions;
            }
        }

        // The source reuses its minimum-population local as the floor for the
        // random 1..5 offer-target roll. This intentionally preserves its
        // behavior where ordinary, populated rivals do not pass that floor.
        Integer randomOfferScoreFloor = poorestPopulationTarget == null
                ? null : lowestTargetPopulation;
        GameState.PlayerState randomOfferTarget = null;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (!isLivingDiplomacyOpponent(actor, target)
                    || actor.getDiplomacyOfferedLastTurn(target.id) != 0) {
                continue;
            }
            int candidateScore = randomInclusive(random, 1, 5);
            if (randomOfferScoreFloor == null || randomOfferScoreFloor == 0
                    || candidateScore > randomOfferScoreFloor) {
                randomOfferScoreFloor = candidateScore;
                randomOfferTarget = target;
            }
        }

        GameState.PlayerState offerTarget = randomOfferTarget != null
                && randomOfferTarget.populationMillions > actor.populationMillions
                ? randomOfferTarget : null;
        GameState.PlayerState demandTarget = poorestPopulationTarget != null
                && poorestPopulationTarget.moneyMillions > actor.moneyMillions
                ? poorestPopulationTarget : null;

        GameState.PlayerState human = state.getHuman();
        if (!actor.hasCeasefireWith(human.id)
                && actor.getDiplomacyOfferedLastTurn(human.id) == 0) {
            if (human.moneyMillions > 50 && actor.getRegardTowardExact(human.id) < 50) {
                demandTarget = human;
            }
            if (actor.moneyMillions > 50 && actor.getRegardTowardExact(human.id) > 50) {
                offerTarget = human;
            }
        }
        boolean offer = offerTarget != null;
        boolean demand = demandTarget != null;
        if (!offer && !demand) {
            return null;
        }
        boolean chooseOffer = offer && (!demand || randomInclusive(random, 0, 1) == 1);
        GameState.PlayerState target = chooseOffer ? offerTarget : demandTarget;
        int option = -1;
        if (chooseOffer) {
            for (int candidate = 0; candidate < 3; candidate++) {
                if (state.getDiplomacyOptionAmount(candidate) < actor.turnMoneyMillions) {
                    option = candidate;
                }
            }
        } else {
            for (int candidate = 3; candidate < 6; candidate++) {
                if (state.getDiplomacyOptionAmount(candidate) < target.moneyMillions) {
                    option = candidate;
                }
            }
        }
        if (option < 0) {
            return null;
        }
        // Model.getTarget checks an offer against turnMoney but does not deduct
        // it there. The Presenter deducts the cash only when the proposal is
        // submitted, so the AI may still plan its second action from this budget.
        int reserved = 0;
        return new Decision(actor.id, GameState.Action.DIPLOMACY,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                reserved, target.id, -1, null, option);
    }

    private static boolean isLivingDiplomacyOpponent(GameState.PlayerState actor,
                                                      GameState.PlayerState target) {
        return target.alive && target.id != actor.id
                && !actor.hasCeasefireWith(target.id);
    }

    static Decision planProduction(GameState state, GameState.PlayerState player,
                                   Random random) {
        List<int[]> deficits = new ArrayList<>(8);
        int activeCategories = 0;
        // Model.getTarget builds sorted deficits for categories 0 through 7.
        // Category zero has no weapon rows, but its stable leading zero score
        // can consume one of the source's randomly selected category slots.
        for (int group = 0; group <= 7; group++) {
            int deficit = 0;
            boolean active = false;
            for (GameState.WeaponState weapon : player.getWeapons().values()) {
                if (weapon.definition.productionGroup == group) {
                    active |= weapon.active;
                    deficit += Math.max(0, player.getDesiredStock(weapon.definition.name)
                            - weapon.inStock);
                }
            }
            if (active) {
                activeCategories++;
            } else {
                // The Flash source totals every weapon's desire in an active
                // category, but inactive categories receive a zero deficit.
                deficit = 0;
            }
            deficits.add(new int[]{group, deficit});
        }
        deficits.sort((left, right) -> Integer.compare(right[1], left[1]));

        int categoryCount = randomInclusive(random, 1, 4);
        if (player.isSpecialPerson()) {
            categoryCount = 2;
        } else if (player.isSuicidePerson()) {
            categoryCount = 1;
        }
        int defensePercent = 0;
        if (player.getWeaponStockByName("antiAir") < 1
                || player.getWeaponStockByName("antiRocket") < 1) {
            defensePercent = randomInclusive(random, 20, 35);
        }
        int targetPercent = (int) Math.round(randomInclusive(random, 80, 100) / 10.0) * 10;
        int percentAssigned = defensePercent;
        Map<String, Integer> percentages = new LinkedHashMap<>();
        for (int index = 0; index < categoryCount; index++) {
            int group = deficits.get(index)[0];
            if (!hasActiveCategory(player, group)) {
                continue;
            }
            int percentage = activeCategories < 4
                    ? (int) Math.round(randomInclusive(random, 40, 60) / 10.0) * 10
                    : (int) Math.round(randomInclusive(random, 0, 60) / 10.0) * 10;
            if (player.isSpecialPerson()) {
                percentage = 50;
            }
            if (percentAssigned + percentage > targetPercent) {
                percentage = targetPercent - percentAssigned;
                percentAssigned = targetPercent;
            } else {
                percentAssigned += percentage;
            }
            percentages.put(categoryForGroup(group), percentage);
        }
        if (defensePercent > 0) {
            percentages.put("defense", defensePercent);
        }

        Map<String, Integer> budgets = new LinkedHashMap<>();
        int reserved = 0;
        for (Map.Entry<String, Integer> percentage : percentages.entrySet()) {
            int budget = player.turnMoneyMillions * percentage.getValue() / 100;
            if (budget > 0) {
                budgets.put(percentage.getKey(), budget);
                reserved += budget;
            }
        }
        return new Decision(player.id, GameState.Action.PRODUCTION, budgets,
                Collections.<String>emptyList(), reserved, -1, -1, null, -1);
    }

    private static boolean hasActiveCategory(GameState.PlayerState player, int group) {
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (weapon.definition.productionGroup == group && weapon.active) {
                return true;
            }
        }
        return false;
    }

    private static String categoryForGroup(int group) {
        return group > 0 && group <= PRODUCTION_CATEGORIES.length
                ? PRODUCTION_CATEGORIES[group - 1] : "";
    }

    private static Decision planResearch(GameState state, GameState.PlayerState player,
                                        Random random, int slot) {
        int budget = slot == 2 ? player.turnMoneyMillions
                : (int) Math.round(player.turnMoneyMillions
                * randomInclusive(random, 20, 80) / 100.0);
        if (player.isSpecialPerson()) {
            budget = player.turnMoneyMillions;
        }
        List<TechnologyCatalog.Definition> candidates = new ArrayList<>();
        for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
            if ((technology.group == 7 && !player.allowsSpecialTechnologies())
                    || player.hasTechnology(technology.name)
                    || player.moneyMillions <= technology.costMillions
                    || (technology.prerequisite != null
                    && !player.hasTechnology(technology.prerequisite))) {
                continue;
            }
            candidates.add(technology);
        }
        candidates.sort(Comparator.comparingInt(
                (TechnologyCatalog.Definition item) -> player.getDesiredStock(item.name))
                .reversed());
        List<String> technologies = new ArrayList<>();
        int reserved = 0;
        int remaining = budget;
        for (TechnologyCatalog.Definition technology : candidates) {
            if (remaining > technology.costMillions
                    && (player.isSpecialPerson()
                    || player.getDesiredStock(technology.name) > 0)) {
                technologies.add(technology.name);
                remaining -= technology.costMillions;
                reserved += technology.costMillions;
            }
        }
        if (technologies.isEmpty()) {
            return null;
        }
        return new Decision(player.id, GameState.Action.RESEARCH,
                Collections.<String, Integer>emptyMap(), technologies, reserved,
                -1, -1, null, -1);
    }

    static GameState.PlayerState mostHostileTarget(GameState state,
                                                   GameState.PlayerState actor,
                                                   boolean includeCeasefire) {
        GameState.PlayerState best = null;
        double bestHostility = Double.NEGATIVE_INFINITY;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (!target.alive || target.id == actor.id
                    || (!includeCeasefire && actor.hasCeasefireWith(target.id))) {
                continue;
            }
            double hostility = 100 - actor.getRegardTowardExact(target.id)
                    + (target.id == actor.getPrimaryEnemyId() ? 1 : 0);
            if (hostility > bestHostility) {
                bestHostility = hostility;
                best = target;
            }
        }
        return best;
    }

    static GameState.PlayerState attackTarget(GameState state,
                                              GameState.PlayerState actor,
                                              Random random) {
        GameState.PlayerState[] players = state.getPlayers();
        List<GameState.PlayerState> activePlayers = new ArrayList<>(players.length);
        List<GameState.PlayerState> candidates = new ArrayList<>(players.length - 1);
        for (GameState.PlayerState player : players) {
            if (player == null || !player.alive) {
                continue;
            }
            activePlayers.add(player);
        }
        // getTarget's first hostile-target pass iterates activePlayerArr with
        // ActionScript for-in, which visits the array from its last key.
        for (int playerId = players.length - 1; playerId >= 0; playerId--) {
            GameState.PlayerState player = players[playerId];
            if (player == null || !player.alive) {
                continue;
            }
            if (player.id != actor.id && !actor.hasCeasefireWith(player.id)) {
                candidates.add(player);
            }
        }

        GameState.PlayerState target = null;
        double highestHostility = 0;
        for (GameState.PlayerState candidate : candidates) {
            double hostility = 100 - actor.getRegardTowardExact(candidate.id)
                    + (candidate.id == actor.getPrimaryEnemyId() ? 1 : 0);
            if (hostility > highestHostility) {
                highestHostility = hostility;
                target = candidate;
            }
        }
        if (target != null) {
            return target;
        }

        // The source iterates a filtered rival array but indexes activePlayerArr.
        // Preserve that selection order and its population filter for full parity.
        double lowestRegard = 100;
        for (int index = 0; index < candidates.size(); index++) {
            GameState.PlayerState indexed = activePlayers.get(index);
            if (indexed.id != actor.id && indexed.populationMillions > 0
                    && actor.getRegardTowardExact(indexed.id) <= lowestRegard
                    && !actor.hasCeasefireWith(indexed.id)) {
                lowestRegard = actor.getRegardTowardExact(indexed.id);
                target = indexed;
            }
        }
        if (target == null && !candidates.isEmpty()) {
            target = candidates.get(randomInclusive(random, 0, candidates.size() - 1));
        }
        return target;
    }

    private static CityState largestCity(GameState.PlayerState player) {
        CityState largest = null;
        for (CityState city : player.getCities()) {
            if (largest == null || city.populationMillions > largest.populationMillions) {
                largest = city;
            }
        }
        return largest;
    }

    private static Decision simple(int playerId, GameState.Action action) {
        return new Decision(playerId, action, Collections.<String, Integer>emptyMap(),
                Collections.<String>emptyList(), 0, -1, -1, null, -1);
    }

    private static int randomInclusive(Random random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
