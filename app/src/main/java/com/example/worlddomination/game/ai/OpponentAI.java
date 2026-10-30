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
    private static final int[] ATTACK_BASE = {80, 86, 90};
    private static final int[] SPY_ALERT_MONEY = {180, 150, 100};
    private static final int[] AI_SPY_STOCK_REQUIREMENTS = {10, 8, 5, 3};
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
                    diplomacyOption, Collections.<GameState.ShipStrike>emptyList());
        }

        private Decision(int playerId, GameState.Action action,
                         Map<String, Integer> productionBudgets,
                         List<String> technologies, int reservedMillions,
                         int targetPlayerId, int targetCityIndex, String weaponName,
                         String payloadName, boolean prepareCarrier, int diplomacyOption,
                         List<GameState.ShipStrike> shipStrikes) {
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
        }
    }

    private static final class ScoredAction {
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

    /** Plans the two source-style actions before any nation's actions resolve. */
    public static List<Decision> plan(GameState state, GameState.PlayerState player,
                                      Random random) {
        List<ScoredAction> scores = scoreActions(state, player, random);
        scores.sort(Comparator.comparingDouble((ScoredAction item) -> item.score).reversed());

        List<Decision> decisions = new ArrayList<>(2);
        Map<String, Integer> plannedWeaponUse = new LinkedHashMap<>();
        int firstValidScoreIndex = -1;
        for (int slot = 1; slot <= 2; slot++) {
            Decision chosen = null;
            int chosenIndex = -1;

            boolean sourceAttackRepeat = slot == 2 && firstValidScoreIndex == 0
                    && scores.get(0).action == GameState.Action.ATTACK
                    && scores.get(0).score - 50 > scores.get(1).score;
            if (sourceAttackRepeat) {
                chosen = planAttack(state, player, random, plannedWeaponUse);
                if (chosen != null) {
                    chosenIndex = 0;
                }
            }

            if (chosen == null) {
                int startIndex = slot == 1 ? 0 : firstValidScoreIndex + 1;
                for (int index = startIndex; index < scores.size(); index++) {
                    ScoredAction candidate = scores.get(index);
                    Decision decision = planAction(state, player, candidate.action,
                            random, slot, plannedWeaponUse);
                    if (decision != null) {
                        chosen = decision;
                        chosenIndex = index;
                        break;
                    }
                }
            }

            if (chosen == null) {
                chosen = fallbackAction(state, player, random, slot, plannedWeaponUse);
            }
            decisions.add(chosen);
            if (slot == 1) {
                firstValidScoreIndex = chosenIndex;
            }
            reserve(player, chosen);
            if (chosen.action == GameState.Action.ATTACK && chosen.weaponName != null) {
                Integer count = plannedWeaponUse.get(chosen.weaponName);
                plannedWeaponUse.put(chosen.weaponName, count == null ? 1 : count + 1);
            } else if (chosen.action == GameState.Action.PREPARED_ATTACK
                    && chosen.prepareCarrier && chosen.weaponName != null) {
                Integer count = plannedWeaponUse.get(chosen.weaponName);
                plannedWeaponUse.put(chosen.weaponName, count == null ? 1 : count + 1);
            }
            if (chosen.action == GameState.Action.PREPARED_ATTACK) {
                plannedWeaponUse.put("@prepared", 1);
            } else if (chosen.action == GameState.Action.DEFENSE) {
                plannedWeaponUse.put("@defense", 1);
            }
        }
        return decisions;
    }

    private static Decision fallbackAction(GameState state, GameState.PlayerState player,
                                           Random random, int slot,
                                           Map<String, Integer> plannedWeaponUse) {
        GameState.Action[] fallbackOrder = {
                GameState.Action.MASS_MEDIA, GameState.Action.RESOURCES,
                GameState.Action.ATTACK, GameState.Action.PREPARED_ATTACK,
                GameState.Action.DEFENSE, GameState.Action.PROPAGANDA,
                GameState.Action.DIPLOMACY, GameState.Action.PRODUCTION,
                GameState.Action.RESEARCH
        };
        for (GameState.Action action : fallbackOrder) {
            if (state.isActionBioloaded(player.id, action)) {
                continue;
            }
            Decision fallback = planAction(state, player, action, random, slot,
                    plannedWeaponUse);
            if (fallback != null) {
                return fallback;
            }
        }
        // Combat actions are never bio-disabled; a no-target defense resolves as a safe no-op.
        return simple(player.id, GameState.Action.DEFENSE);
    }

    private static void reserve(GameState.PlayerState player, Decision decision) {
        player.turnMoneyMillions = Math.max(0,
                player.turnMoneyMillions - decision.reservedMillions);
    }

    private static List<ScoredAction> scoreActions(GameState state,
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

        int lowestRegard = lowestRegardForScore(state, player);
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
        double preparedAttack = Math.max(0,
                ATTACK_BASE[state.getDifficulty()] - lowestRegard / 2.0);
        if (preparedCarrier != null) {
            // A ready strike is represented by the source's boosted attack action.
            preparedAttack = -999;
        } else if (state.getAvailablePreparedCarriers(player.id).isEmpty()) {
            preparedAttack = -999;
        } else {
            preparedAttack += 30;
        }
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

        List<ScoredAction> scores = new ArrayList<>(9);
        scores.add(new ScoredAction(GameState.Action.ATTACK, attack));
        scores.add(new ScoredAction(GameState.Action.PREPARED_ATTACK, preparedAttack));
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
        for (ScoredAction scored : scoreActions(state, player, random)) {
            if (scored.action == action) {
                return scored.score;
            }
        }
        throw new IllegalArgumentException("Action is not scored: " + action);
    }

    /** The reference uses its current turn directly; its calculated 60-turn cap is unused. */
    static double researchTurnRatio(int turn, int technologyCount) {
        return turn / (double) Math.max(1, technologyCount) * 9;
    }

    static double baseAttackScore(GameState state, GameState.PlayerState player) {
        return baseAttackScore(state, player, lowestRegardForScore(state, player));
    }

    private static double baseAttackScore(GameState state, GameState.PlayerState player,
                                          int lowestRegard) {
        return Math.max(0, ATTACK_BASE[state.getDifficulty()] - lowestRegard / 2.0);
    }

    private static int lowestRegardForScore(GameState state, GameState.PlayerState player) {
        int lowestRegard = 100;
        for (GameState.PlayerState other : state.getPlayers()) {
            if (other != null) {
                lowestRegard = Math.min(lowestRegard, player.getRegardToward(other.id));
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
        int bestThreatId = -1;
        double bestScore = 0;
        int bestStrength = -1;
        for (int threatPlayerId : threatPlayerIds) {
            double score = preparedThreatScore(state, defender, threatPlayerId);
            int strength = state.getPlayerState(threatPlayerId).getStrength();
            if (score > bestScore || (score == bestScore && score >= 0
                    && strength > bestStrength)) {
                bestScore = score;
                bestStrength = strength;
                bestThreatId = threatPlayerId;
            }
        }
        return bestThreatId;
    }

    private static double preparedThreatScore(GameState state,
                                              GameState.PlayerState defender,
                                              int threatPlayerId) {
        GameState.PlayerState threat = state.getPlayerState(threatPlayerId);
        double score = 86 - defender.getRegardToward(threat.id);
        if (defender.populationMillions < 20) {
            score += 10;
        }
        return score;
    }

    private static double weaponStockLevel(GameState.PlayerState player) {
        int active = 0;
        int satisfied = 0;
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (!weapon.active || weapon.definition.productionGroup == 8) {
                continue;
            }
            active++;
            if (weapon.inStock >= player.getDesiredStock(weapon.definition.name)) {
                satisfied++;
            }
        }
        return active == 0 ? 0 : satisfied / (double) active * 100;
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
                if (player.getPreparedCarrierName() != null) {
                    return planPreparedAttack(state, player, random, plannedWeaponUse);
                }
                Decision attack = planAttack(state, player, random, plannedWeaponUse);
                if (attack != null) {
                    return attack;
                }
                return plannedWeaponUse.containsKey("@prepared") ? null
                        : planPreparedAttack(state, player, random, plannedWeaponUse);
            case PREPARED_ATTACK:
                return planPreparedAttack(state, player, random, plannedWeaponUse);
            case DEFENSE:
                return planDefense(state, player, plannedWeaponUse);
            case PROPAGANDA:
                return planPropaganda(state, player);
            case DIPLOMACY:
                return planDiplomacy(state, player, random);
            case MASS_MEDIA:
            case RESOURCES:
                return simple(player.id, action);
            case PRODUCTION:
                return planProduction(state, player, random);
            case RESEARCH:
                return planResearch(state, player, random, slot);
            default:
                return null;
        }
    }

    private static Decision planDefense(GameState state, GameState.PlayerState defender,
                                        Map<String, Integer> plannedUse) {
        if (plannedUse.containsKey("@defense")) {
            return null;
        }
        int threatId = mostThreateningPreparedEnemy(state, defender,
                state.getPreparedThreatPlayerIds(defender.id));
        String counter = threatId < 0 ? null
                : state.getCounterWeaponForThreat(defender.id, threatId);
        return counter == null ? null : new Decision(defender.id, GameState.Action.DEFENSE,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                0, -1, -1, counter, -1);
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
        if (populated.isEmpty()) {
            return null;
        }
        int cityIndex = populated.get(randomInclusive(random, 0, populated.size() - 1));
        return new Decision(attacker.id, GameState.Action.ATTACK,
                Collections.<String, Integer>emptyMap(), Collections.<String>emptyList(),
                0, target.id, cityIndex, selected.definition.name, -1);
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
            return !state.getAvailablePreparedPayloads(player.id, preparedCarrier).isEmpty();
        }
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            if (weapon.active && weapon.inStock > 0
                    && ("soldier".equals(weapon.definition.type)
                    // The source gates action scoring with spyAvailable, which
                    // deliberately ignores readiness; getTarget checks readiness later.
                    || "spy".equals(weapon.definition.type))) {
                return true;
            }
        }
        return !state.getAvailablePreparedCarriers(player.id).isEmpty();
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
                    - attacker.getRegardToward(target.id);
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
            if (human.moneyMillions > 50 && actor.getRegardToward(human.id) < 50) {
                demandTarget = human;
            }
            if (actor.moneyMillions > 50 && actor.getRegardToward(human.id) > 50) {
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
        int reserved = chooseOffer ? state.getDiplomacyOptionAmount(option) : 0;
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
        for (int group = 1; group <= 7; group++) {
            int deficit = 0;
            boolean active = false;
            for (GameState.WeaponState weapon : player.getWeapons().values()) {
                if (weapon.definition.productionGroup == group) {
                    active |= weapon.active;
                    if (weapon.active) {
                        deficit += Math.max(0, player.getDesiredStock(weapon.definition.name)
                                - weapon.inStock);
                    }
                }
            }
            if (active) {
                activeCategories++;
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
        int bestHostility = Integer.MIN_VALUE;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (!target.alive || target.id == actor.id
                    || (!includeCeasefire && actor.hasCeasefireWith(target.id))) {
                continue;
            }
            int hostility = 100 - actor.getRegardToward(target.id)
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
            if (player.id != actor.id && !actor.hasCeasefireWith(player.id)) {
                candidates.add(player);
            }
        }

        GameState.PlayerState target = null;
        int highestHostility = 0;
        for (GameState.PlayerState candidate : candidates) {
            int hostility = 100 - actor.getRegardToward(candidate.id)
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
        int lowestRegard = 100;
        for (int index = 0; index < candidates.size(); index++) {
            GameState.PlayerState indexed = activePlayers.get(index);
            if (indexed.id != actor.id && indexed.populationMillions > 0
                    && actor.getRegardToward(indexed.id) <= lowestRegard
                    && !actor.hasCeasefireWith(indexed.id)) {
                lowestRegard = actor.getRegardToward(indexed.id);
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
