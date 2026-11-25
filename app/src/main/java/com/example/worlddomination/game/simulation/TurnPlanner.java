package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.simulation.GameState.Action;
import com.example.worlddomination.game.simulation.GameState.PlayerState;
import com.example.worlddomination.game.simulation.GameState.ShipStrike;
import com.example.worlddomination.game.simulation.GameState.WeaponState;
import com.example.worlddomination.game.simulation.TurnPlan.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Source order rules and bounded alternatives for selecting both slots at once. */
public final class TurnPlanner {
    private static final int LIMIT = 10;
    private static final String[] PRODUCTION = {
            "soldier", "warhead", "rockets", "planes", "bioload", "special", "defense", "spy"
    };
    private static final Order RESOURCES = Order.simple(Action.RESOURCES);
    private static final Order MEDIA = Order.simple(Action.MASS_MEDIA);
    private static final Order SKIP = Order.simple(Action.SKIP);

    private TurnPlanner() { }

    public static List<TurnPlan> candidates(GameState state, int actorId, int contractVersion) {
        PlayerState actor = state.getPlayerState(actorId);
        if (!mayPlan(state, actor)) return Collections.emptyList();

        List<TurnPlan> plans = new ArrayList<>(LIMIT);
        List<Order> attacks = directAttacks(state, actor, false);
        List<Order> spies = directAttacks(state, actor, true);
        List<Order> prepared = preparedAttacks(state, actor);
        List<Order> research = researchChoices(actor);
        List<Order> production = productionChoices(actor, actor.turnMoneyMillions, 85);
        List<Order> influence = propagandaChoices(state, actor);
        List<Order> diplomacy = diplomacyChoices(state, actor);
        List<Order> defense = new ArrayList<>();
        for (String counter : state.getAvailableDefenseWeapons(actorId)) {
            if (actor.human || state.getPreparedThreatPlayerIds(actorId).stream().anyMatch(
                    threat -> counter.equals(state.getCounterWeaponForThreat(actorId, threat)))) {
                defense.add(Order.targeted(Action.DEFENSE, -1, -1, counter, null, null));
            }
        }

        // Each category gets a slot before reserve alternatives add more targets and loadouts.
        List<Order> launchCompanions = contractVersion >= 2 && !prepared.isEmpty()
                ? launchCompanions(prepared.get(0), attacks, state) : attacks;
        addFirst(plans, state, actor, prepared, launchCompanions, influence, list(RESOURCES));
        addFirst(plans, state, actor, attacks, attacks, production, list(RESOURCES));
        addFirst(plans, state, actor, research, production, list(RESOURCES), attacks);
        addFirst(plans, state, actor, production, attacks, list(RESOURCES));
        addFirst(plans, state, actor, list(MEDIA), influence, list(RESOURCES), attacks);
        addFirst(plans, state, actor, defense, prepared, attacks, list(RESOURCES));
        addFirst(plans, state, actor, spies, attacks, list(RESOURCES), production);
        addFirst(plans, state, actor, diplomacy, attacks, list(RESOURCES), defense);
        if (contractVersion >= 2 && actor.religion < 50 && !attacks.isEmpty()) {
            addFirst(plans, state, actor, attacks, list(RESOURCES));
        } else {
            addFirst(plans, state, actor, influence, attacks, list(RESOURCES));
        }
        addFirst(plans, state, actor, list(RESOURCES), list(RESOURCES));

        List<Order> variations = new ArrayList<>();
        for (List<Order> category : java.util.Arrays.asList(attacks, prepared, research, production,
                influence, diplomacy, spies, defense)) {
            if (category.size() > 1) {
                Order first = category.get(0);
                Order alternate = category.get(1);
                if (contractVersion >= 2 && first.targetPlayerId >= 0) {
                    for (Order other : category) {
                        if (other.targetPlayerId >= 0 && other.targetPlayerId != first.targetPlayerId) {
                            alternate = other;
                            break;
                        }
                    }
                }
                variations.add(alternate);
            }
        }
        if (!variations.isEmpty()) {
            Collections.rotate(variations, Math.floorMod(state.getTurn() + actor.id,
                    variations.size()));
        }
        for (Order alternative : variations) {
            if (plans.size() == LIMIT) break;
            addFirst(plans, state, actor, list(alternative), attacks, list(RESOURCES), list(MEDIA));
        }
        addFirst(plans, state, actor, list(RESOURCES), list(MEDIA));
        addFirst(plans, state, actor, list(MEDIA), list(MEDIA));
        if (plans.isEmpty()) {
            addFirst(plans, state, actor, list(RESOURCES, MEDIA, SKIP), list(RESOURCES, MEDIA, SKIP));
        }
        // Stable across replay, independent of all simulator random draws.
        Collections.shuffle(plans, new Random(0x8c1723b4L + 439L * state.getTurn()
                + 1627L * actor.id + 31L * actor.populationMillions));
        return Collections.unmodifiableList(plans);
    }

    private static List<Order> launchCompanions(Order launch, List<Order> attacks, GameState state) {
        if (launch.payloadName == null) return attacks;
        CityState target = state.getPlayerState(launch.targetPlayerId).getCity(launch.targetCityIndex);
        int minimumDamage = WeaponCatalog.byName(launch.payloadName).maxKillsMillions * 2 / 3;
        if (target.populationMillions > minimumDamage) return attacks;
        List<Order> companions = new ArrayList<>(attacks);
        companions.removeIf(attack -> attack.targetPlayerId == launch.targetPlayerId
                && attack.targetCityIndex == launch.targetCityIndex);
        return companions;
    }

    private static List<Order> list(Order... orders) {
        return java.util.Arrays.asList(orders);
    }

    @SafeVarargs
    private static void addFirst(List<TurnPlan> plans, GameState state, PlayerState actor,
                                 List<Order> firstChoices, List<Order>... companions) {
        if (plans.size() >= LIMIT) return;
        for (Order first : firstChoices) {
            for (List<Order> group : companions) {
                for (Order secondChoice : group) {
                    Order second = secondChoice;
                    if (second.action == Action.PRODUCTION && first.reservedMillions > 0) {
                        List<Order> revised = productionChoices(actor,
                                actor.turnMoneyMillions - first.reservedMillions, 85);
                        if (revised.isEmpty()) continue;
                        second = revised.get(0);
                    }
                    TurnPlan plan = new TurnPlan(state, actor.id, first, second);
                    if (isLegal(state, actor.id, plan) && !containsPlan(plans, first, second)) {
                        plans.add(plan);
                        return;
                    }
                }
            }
        }
    }

    private static boolean containsPlan(List<TurnPlan> plans, Order first, Order second) {
        for (TurnPlan plan : plans) {
            if (sameOrder(plan.orders.get(0), first) && sameOrder(plan.orders.get(1), second)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameOrder(Order left, Order right) {
        if (left.action != right.action || left.reservedMillions != right.reservedMillions
                || left.targetPlayerId != right.targetPlayerId
                || left.targetCityIndex != right.targetCityIndex
                || left.diplomacyOption != right.diplomacyOption
                || left.prepareCarrier != right.prepareCarrier
                || !left.technologies.equals(right.technologies)
                || !left.productionBudgets.equals(right.productionBudgets)
                || !Objects.equals(left.weaponName, right.weaponName)
                || !Objects.equals(left.payloadName, right.payloadName)
                || !Objects.equals(left.spyBuilding, right.spyBuilding)
                || left.shipStrikes.size() != right.shipStrikes.size()) return false;
        for (int i = 0; i < left.shipStrikes.size(); i++) {
            ShipStrike a = left.shipStrikes.get(i);
            ShipStrike b = right.shipStrikes.get(i);
            if (a.targetPlayerId != b.targetPlayerId || a.cityIndex != b.cityIndex
                    || !a.payloadName.equals(b.payloadName)) return false;
        }
        return true;
    }

    private static List<Order> directAttacks(GameState state, PlayerState actor, boolean spy) {
        List<RankedOrder> choices = new ArrayList<>();
        for (PlayerState target : state.getPlayers()) {
            if (!target.alive || target.id == actor.id || (!spy && actor.hasCeasefireWith(target.id))) {
                continue;
            }
            for (WeaponState weapon : actor.getWeapons().values()) {
                if (!weapon.active || weapon.inStock <= 0 || (spy
                        ? !GameState.canUseSpy(actor, weapon) : !"soldier".equals(weapon.definition.type))) {
                    continue;
                }
                for (CityState city : target.getCities()) {
                    if (city.populationMillions <= 0) continue;
                    String weaponName = weapon.definition.name;
                    double value = spy ? weapon.definition.level * 10 + city.populationMillions / 20d
                            : battleValue(actor, target, city, weapon.definition.maxKillsMillions);
                    choices.add(new RankedOrder(Order.targeted(Action.ATTACK, target.id, city.index,
                            weaponName, null, spy ? "spyCenter" : null), value));
                    if (spy) break;
                }
            }
        }
        return ranked(choices);
    }

    private static double battleValue(PlayerState actor, PlayerState target, CityState city, int cap) {
        int estimated = cap >= 1000 ? city.populationMillions : (cap + cap * 2 / 3) / 2;
        double finish = city.populationMillions <= estimated ? 8 : 0;
        return Math.min(city.populationMillions, estimated) + finish
                + (100 - actor.getRegardToward(target.id)) / 8d
                + (target.id == actor.getLastAttackedByPlayerId() ? 3 : 0)
                + (target.id == actor.getPrimaryEnemyId() ? 2 : 0);
    }

    private static List<Order> preparedAttacks(GameState state, PlayerState actor) {
        String carrierName = actor.getPreparedCarrierName();
        List<RankedOrder> choices = new ArrayList<>();
        if (carrierName == null) {
            List<String> carriers = actor.human ? state.getAvailablePreparedCarrierStock(actor.id)
                    : state.getAvailablePreparedCarriers(actor.id);
            for (String carrier : carriers) {
                int power = 0;
                for (String payload : state.getAvailablePreparedPayloads(actor.id, carrier)) {
                    power = Math.max(power, WeaponCatalog.byName(payload).maxKillsMillions);
                }
                choices.add(new RankedOrder(Order.prepare(carrier), power));
            }
            return ranked(choices);
        }

        List<String> payloads = state.getAvailablePreparedPayloads(actor.id, carrierName);
        for (PlayerState target : state.getPlayers()) {
            if (!isHostile(actor, target)) continue;
            if ("ship".equals(carrierName)) {
                addShipPlan(actor, target, carrierName, payloads, choices);
                continue;
            }
            for (CityState city : target.getCities()) {
                if (city.populationMillions <= 0) continue;
                for (String payload : payloads) {
                    int power = WeaponCatalog.byName(payload).maxKillsMillions;
                    choices.add(new RankedOrder(Order.targeted(Action.PREPARED_ATTACK,
                            target.id, city.index, carrierName, payload, null),
                            battleValue(actor, target, city, power)));
                }
            }
        }
        return ranked(choices);
    }

    private static void addShipPlan(PlayerState actor, PlayerState target, String carrier,
                                    List<String> payloads, List<RankedOrder> choices) {
        List<CityState> cities = new ArrayList<>(target.getCities());
        cities.sort(Comparator.comparingInt((CityState city) -> city.populationMillions).reversed());
        List<String> strongest = new ArrayList<>(payloads);
        strongest.sort(Comparator.comparingInt((String payload) ->
                WeaponCatalog.byName(payload).maxKillsMillions).reversed());
        List<ShipStrike> strikes = new ArrayList<>();
        int index = 0;
        double value = 0;
        for (String payload : strongest) {
            for (int copy = 0; copy < actor.getWeaponStockByName(payload) && index < 3; copy++) {
                CityState city = cities.get(index);
                if (city.populationMillions <= 0) city = cities.get(0);
                if (city.populationMillions <= 0) continue;
                strikes.add(new ShipStrike(target.id, city.index, payload));
                value += battleValue(actor, target, city,
                        WeaponCatalog.byName(payload).maxKillsMillions);
                index++;
            }
        }
        if (!strikes.isEmpty()) choices.add(new RankedOrder(Order.ship(carrier, strikes), value));
    }

    private static List<Order> researchChoices(PlayerState actor) {
        List<RankedOrder> choices = new ArrayList<>();
        List<TechnologyCatalog.Definition> eligible = new ArrayList<>();
        for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
            if (!canResearch(actor, technology) || technology.costMillions > actor.turnMoneyMillions) {
                continue;
            }
            eligible.add(technology);
            choices.add(new RankedOrder(Order.research(Collections.singletonList(technology.name),
                    technology.costMillions), researchValue(actor, technology)));
        }
        eligible.sort(Comparator.comparingDouble((TechnologyCatalog.Definition technology) ->
                researchValue(actor, technology)).reversed());
        if (eligible.size() >= 2) {
            TechnologyCatalog.Definition first = eligible.get(0);
            TechnologyCatalog.Definition second = eligible.get(1);
            int combined = first.costMillions + second.costMillions;
            if (combined + 5 <= actor.turnMoneyMillions) {
                choices.add(new RankedOrder(Order.research(java.util.Arrays.asList(first.name,
                        second.name), combined), researchValue(actor, first) + 2));
            }
        }
        return ranked(choices);
    }

    private static double researchValue(PlayerState actor, TechnologyCatalog.Definition technology) {
        return 6 + technology.strengthWeight + actor.getDesiredStock(technology.name) / 4d
                + (technology.group == 4 ? 8 : 0)
                + (technology.group == 2 && actor.hasTechnology("rocket1") ? 6 : 0)
                + (technology.group == 1 && actor.hasTechnology("warhead1") ? 5 : 0);
    }

    private static boolean canResearch(PlayerState actor, TechnologyCatalog.Definition technology) {
        return technology != null && !actor.hasTechnology(technology.name)
                && (!actor.human || technology.group <= 7)
                && (technology.group != 7 || actor.allowsSpecialTechnologies())
                && (technology.prerequisite == null || actor.hasTechnology(technology.prerequisite));
    }

    private static List<Order> productionChoices(PlayerState actor, int remaining, int percent) {
        List<RankedOrder> choices = new ArrayList<>();
        int allocation = GameState.sourceProductionBudgetMillions(remaining, percent);
        for (String category : PRODUCTION) {
            int group = GameState.productionGroup(category);
            if (!GameState.isProductionGroupUnlocked(actor, group)) continue;
            double value = 0;
            for (WeaponState weapon : actor.getWeapons().values()) {
                if (weapon.definition.productionGroup == group && weapon.active && weapon.inStock < 4
                        && allocation > weapon.definition.costMillions) {
                    double shortage = Math.max(0, actor.getDesiredStock(weapon.definition.name)
                            - weapon.inStock) / 10d;
                    value = Math.max(value, (4 - weapon.inStock) * 2 + shortage
                            + weapon.definition.level + (group == 4 ? 6 : 0));
                }
            }
            if (value > 0) {
                int reserved = actor.human ? (int) Math.round(remaining / 100d * percent) : allocation;
                choices.add(new RankedOrder(Order.production(category, allocation, reserved), value));
            }
        }
        return ranked(choices);
    }

    private static List<Order> propagandaChoices(GameState state, PlayerState actor) {
        List<RankedOrder> choices = new ArrayList<>();
        for (PlayerState target : state.getPlayers()) {
            if (!isHostile(actor, target)) continue;
            for (CityState city : target.getCities()) {
                if (city.populationMillions > 0) {
                    // Use visible city size and the actor's known relationship.
                    choices.add(new RankedOrder(Order.targeted(Action.PROPAGANDA,
                            target.id, city.index, null, null, null), city.populationMillions
                            - actor.getRegardToward(target.id) / 4d));
                }
            }
        }
        return ranked(choices);
    }

    private static List<Order> diplomacyChoices(GameState state, PlayerState actor) {
        List<RankedOrder> choices = new ArrayList<>();
        List<Integer> threats = state.getPreparedThreatPlayerIds(actor.id);
        for (PlayerState target : state.getPlayers()) {
            if (!isHostile(actor, target) || (!actor.human
                    && actor.getDiplomacyOfferedLastTurn(target.id) > 0)) continue;
            for (int option = 0; option < 6; option++) {
                int amount = state.getDiplomacyOptionAmount(option);
                if (option < 3 && amount > actor.turnMoneyMillions) continue;
                double score = (threats.contains(target.id) ? 35 : 0)
                        + (target.populationMillions - actor.populationMillions) / 12d
                        + actor.getRegardToward(target.id) / 4d - amount / 6d
                        + (option < 3 ? 14 : 0);
                int reserved = actor.human && option < 3 ? amount : 0;
                choices.add(new RankedOrder(Order.diplomacy(target.id, option, reserved), score));
            }
        }
        return ranked(choices);
    }

    private static boolean isHostile(PlayerState actor, PlayerState target) {
        return target.alive && target.id != actor.id && !actor.hasCeasefireWith(target.id);
    }

    private static final class RankedOrder {
        final Order order;
        final double value;

        RankedOrder(Order order, double value) {
            this.order = order;
            this.value = value;
        }
    }

    private static List<Order> ranked(List<RankedOrder> source) {
        source.sort(Comparator.comparingDouble((RankedOrder option) -> option.value).reversed());
        List<Order> options = new ArrayList<>(source.size());
        for (RankedOrder option : source) options.add(option.order);
        return options;
    }

    private static boolean mayPlan(GameState state, PlayerState actor) {
        return actor.alive && state.getMatchStatus() == GameState.MatchStatus.ONGOING
                && (actor.human ? !state.isOpponentPlanningPending() && state.getQueuedActionCount() == 0
                : state.isOpponentPlanningPending());
    }

    public static boolean isLegal(GameState state, int actorId, TurnPlan plan) {
        if (plan == null || plan.actorId != actorId || plan.matchIdentity != state.planIdentity
                || plan.turn != state.getTurn()) return false;
        PlayerState actor = state.getPlayerState(actorId);
        if (!mayPlan(state, actor)) return false;
        int budget = actor.turnMoneyMillions;
        int offerCost = 0;
        Set<String> research = new HashSet<>();
        Map<String, Integer> stockUses = new HashMap<>();
        Order first = plan.orders.get(0);
        Order second = plan.orders.get(1);
        if (first.action == second.action && (first.action == Action.PREPARED_ATTACK
                || first.action == Action.DIPLOMACY || (!actor.human && first.action == Action.DEFENSE))) {
            return false;
        }
        if (!actor.human && first.action == Action.PRODUCTION && (second.action == Action.RESEARCH
                || (second.action == Action.DIPLOMACY && second.diplomacyOption < 3))) return false;
        for (Order order : plan.orders) {
            if ((order.reservedMillions != 0 && order.reservedMillions > budget)
                    || (actor.human && actor.isActionBioloaded(order.action))) return false;
            if (!validateOrder(state, actor, order, budget, research, stockUses)) return false;
            if (!actor.human && order.action == Action.DIPLOMACY && order.diplomacyOption < 3) {
                offerCost += state.getDiplomacyOptionAmount(order.diplomacyOption);
            }
            budget -= order.reservedMillions;
        }
        return offerCost == 0 || offerCost <= budget;
    }

    private static boolean validateOrder(GameState state, PlayerState actor, Order order, int budget,
                                         Set<String> research, Map<String, Integer> stockUses) {
        PlayerState target = order.targetPlayerId < 0 ? null : state.getPlayerState(order.targetPlayerId);
        boolean other = target != null && target.alive && target.id != actor.id;
        boolean hostile = other && isHostile(actor, target);
        boolean city = other && order.targetCityIndex >= 0 && populated(target, order.targetCityIndex);
        WeaponState weapon = actor.getWeapons().get(order.weaponName);
        switch (order.action) {
            case RESOURCES:
            case MASS_MEDIA:
            case SKIP:
                return true;
            case RESEARCH:
                int cost = 0;
                for (String name : order.technologies) {
                    TechnologyCatalog.Definition technology = TechnologyCatalog.byName(name);
                    if (!canResearch(actor, technology) || !research.add(name)) return false;
                    cost += technology.costMillions;
                }
                return cost > 0 && cost == order.reservedMillions
                        && (actor.human ? cost <= budget : cost < budget);
            case PRODUCTION:
                int total = 0;
                for (Map.Entry<String, Integer> investment : order.productionBudgets.entrySet()) {
                    int group = GameState.productionGroup(investment.getKey());
                    if (!GameState.isProductionGroupUnlocked(actor, group) || investment.getValue() <= 0) {
                        return false;
                    }
                    total += investment.getValue();
                }
                return total > 0 && total <= order.reservedMillions;
            case ATTACK:
                return city && weapon != null && ((hostile && "soldier".equals(weapon.definition.type))
                        || GameState.canUseSpy(actor, weapon)) && reserve(weapon, stockUses);
            case DEFENSE:
                return weapon != null && "anti".equals(weapon.definition.type)
                        && (actor.human || state.getPreparedThreatPlayerIds(actor.id).stream().anyMatch(
                        threat -> order.weaponName.equals(state.getCounterWeaponForThreat(actor.id, threat))))
                        && reserve(weapon, stockUses);
            case PROPAGANDA:
                return hostile && city;
            case DIPLOMACY:
                if (!hostile || order.diplomacyOption < 0 || order.diplomacyOption >= 6) return false;
                if (!actor.human && actor.getDiplomacyOfferedLastTurn(target.id) > 0) return false;
                if (order.diplomacyOption >= 3) return true;
                int amount = state.getDiplomacyOptionAmount(order.diplomacyOption);
                return actor.human ? amount <= budget : amount < budget;
            case PREPARED_ATTACK:
                if (!GameState.isCarrier(weapon)) return false;
                if (order.prepareCarrier) return actor.getPreparedCarrierName() == null
                        && (actor.human || !state.getAvailablePreparedPayloads(actor.id, order.weaponName).isEmpty())
                        && reserve(weapon, stockUses);
                if (!hostile || !city || !order.weaponName.equals(actor.getPreparedCarrierName())) return false;
                if ("ship".equals(weapon.definition.type)) {
                    if (order.shipStrikes.isEmpty() || order.shipStrikes.size() > 3) return false;
                    for (ShipStrike strike : order.shipStrikes) {
                        if (strike.targetPlayerId != target.id || !populated(target, strike.cityIndex)
                                || !reservePayload(actor, stockUses, weapon, strike.payloadName)) return false;
                    }
                    return true;
                }
                return reservePayload(actor, stockUses, weapon, order.payloadName);
            default:
                return false;
        }
    }

    private static boolean populated(PlayerState target, int cityIndex) {
        CityState city = target.getCity(cityIndex);
        return city != null && city.populationMillions > 0;
    }

    private static boolean reservePayload(PlayerState actor, Map<String, Integer> stockUses,
                                          WeaponState carrier, String payloadName) {
        WeaponState payload = actor.getWeapons().get(payloadName);
        return GameState.isCompatiblePayload(carrier, payload) && reserve(payload, stockUses);
    }

    private static boolean reserve(WeaponState weapon, Map<String, Integer> stockUses) {
        return weapon.active && stockUses.merge(weapon.definition.name, 1, Integer::sum) <= weapon.inStock;
    }
}
