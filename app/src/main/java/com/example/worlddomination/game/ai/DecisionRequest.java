package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameState.PlayerState;
import com.example.worlddomination.game.simulation.GameState.WeaponState;
import com.example.worlddomination.game.simulation.TurnPlan;
import com.example.worlddomination.game.simulation.TurnPlanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compact actor-only request shared by the Android model and headless gameplay. */
public final class DecisionRequest {
    public static final int VERSION = 2;
    public static final String SYSTEM = "Choose one listed legal World Domination 2 plan for your nation. "
            + "Both ordered actions are committed before any resolve. Stock and money here are private "
            + "to your nation. Money and population are in millions; city indices start at 0. "
            + "A ceasefire prevents conventional attacks and propaganda. A prepared carrier must launch "
            + "this turn or expire. Income arrives during settlement. Return only JSON {\"plan_id\":\"P01\"} "
            + "using the chosen plan's actual ID.";
    private static final Pattern RESULT = Pattern.compile("\\s*\\{\\s*\"plan_id\"\\s*:\\s*\"(P[0-9]{2})\"\\s*\\}\\s*");

    public final int actorId;
    public final int turn;
    public final String user;
    public final List<String> planIds;
    private final Map<String, TurnPlan> plans;
    public final String candidateJson;

    private DecisionRequest(int actorId, int turn, Map<String, TurnPlan> plans,
                            Map<String, Object> payload) {
        this.actorId = actorId;
        this.turn = turn;
        this.plans = Collections.unmodifiableMap(plans);
        this.planIds = Collections.unmodifiableList(new ArrayList<>(plans.keySet()));
        this.user = json(payload);
        this.candidateJson = json(payload.get("plans"));
    }

    public TurnPlan select(String jsonOutput) {
        if (jsonOutput == null || jsonOutput.length() > 80) return null;
        Matcher result = RESULT.matcher(jsonOutput);
        return result.matches() ? selectId(result.group(1)) : null;
    }

    public TurnPlan selectId(String id) {
        return plans.get(id);
    }

    public static DecisionRequest capture(GameState game, int actorId, String strategicPreference) {
        return capture(game, actorId, strategicPreference, VERSION);
    }

    static DecisionRequest capture(GameState game, int actorId, String strategicPreference, int version) {
        PlayerState actor = game.getPlayerState(actorId);
        List<TurnPlan> candidates = TurnPlanner.candidates(game, actorId, version);
        Map<String, TurnPlan> identifiers = new LinkedHashMap<>();
        Map<String, Object> choices = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            int number = index + 1;
            String id = (number < 10 ? "P0" : "P") + number;
            TurnPlan plan = candidates.get(index);
            identifiers.put(id, plan);
            choices.put(id, Arrays.asList(orderData(game, plan.orders.get(0)),
                    orderData(game, plan.orders.get(1))));
        }

        Map<String, Object> stock = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();
        for (WeaponState weapon : actor.getWeapons().values()) {
            if (weapon.active || weapon.inStock != 0) stock.put(weapon.definition.name, weapon.inStock);
            if (weapon.bioloaded) unavailable.add(weapon.definition.name);
        }
        List<Object> opponents = new ArrayList<>();
        for (PlayerState rival : game.getPlayers()) {
            if (rival.id == actorId) continue;
            Map<String, Object> observation = fields("id", rival.id, "alive", rival.alive,
                    "cities", cities(rival));
            if (rival.alive) {
                observation.putAll(fields("regard", actor.getRegardToward(rival.id),
                        "ceasefire", actor.getCeasefireTurnsWith(rival.id),
                        "prepared", rival.getPreparedCarrierName()));
                if (version >= 2 && actor.hasCeasefireWith(rival.id)
                        && actor.getCeasefireTurnsWith(rival.id) == 0) {
                    observation.put("ceasefire_active_at_zero", true);
                }
            }
            opponents.add(observation);
        }
        String preference = strategicPreference == null ? "" : strategicPreference.trim();
        if (preference.length() > 160) preference = preference.substring(0, 160);
        Map<String, Object> payload = fields("v", version, "turn", game.getTurn(),
                "actor", actorId, "difficulty", game.getDifficulty());
        payload.put("own", fields("cash", actor.moneyMillions, "budget", actor.turnMoneyMillions,
                "cities", cities(actor), "income", game.getResourceIncomeMillions(actorId),
                "media_gain", game.getMassMediaGain(actorId), "influence", actor.religion,
                "tech", new ArrayList<>(actor.getInventedTechnologies()), "stock", stock,
                "spy_ready", actor.spyReadiness, "prepared", actor.getPreparedCarrierName(),
                "bio_actions", actor.getBioloadedActions(), "bio_stock", unavailable));
        payload.put("rivals", opponents);
        payload.put("rival_private", fields("cash", null, "stock", null, "tech", null,
                "influence", null, "orders", null));
        Map<String, Object> memory = fields("own_last", game.getPriorOwnActions(actorId),
                "last_attacker", actor.getLastAttackedByPlayerId(), "priority", preference);
        if (version >= 2) {
            List<GameState.CombatEvent> previous = game.getPriorKnownCombat(actorId);
            List<Object> recent = new ArrayList<>(3);
            for (int index = Math.max(0, previous.size() - 3); index < previous.size(); index++) {
                GameState.CombatEvent combat = previous.get(index);
                recent.add(fields("event", combat.type, "from", combat.attackerPlayerId,
                        "to", combat.defenderPlayerId, "city", combat.cityIndex,
                        "casualties", combat.casualtiesMillions));
            }
            memory.put("last_combat", recent);
        }
        payload.put("memory", memory);
        payload.put("plans", choices);
        return new DecisionRequest(actorId, game.getTurn(), identifiers, payload);
    }

    private static List<Integer> cities(PlayerState actor) {
        List<Integer> populations = new ArrayList<>(5);
        for (CityState city : actor.getCities()) populations.add(city.populationMillions);
        return populations;
    }

    static Map<String, Object> orderData(GameState game, TurnPlan.Order order) {
        Map<String, Object> result = fields("action", order.action,
                "reserve", order.reservedMillions);
        if (!order.productionBudgets.isEmpty()) result.put("spend", order.productionBudgets);
        if (!order.technologies.isEmpty()) result.put("tech", order.technologies);
        if (order.targetPlayerId >= 0) result.put("target", order.targetPlayerId);
        if (order.targetCityIndex >= 0) result.put("city", order.targetCityIndex);
        if (order.weaponName != null) result.put("weapon", order.weaponName);
        if (order.payloadName != null) result.put("payload", order.payloadName);
        if (order.prepareCarrier) result.put("prepare", true);
        if (order.spyBuilding != null) result.put("spy_building", order.spyBuilding);
        if (!order.shipStrikes.isEmpty()) {
            List<List<Object>> strikes = new ArrayList<>();
            for (GameState.ShipStrike strike : order.shipStrikes) {
                strikes.add(Arrays.asList(strike.targetPlayerId, strike.cityIndex, strike.payloadName));
            }
            result.put("strikes", strikes);
        }
        if (order.diplomacyOption >= 0) {
            int option = order.diplomacyOption;
            result.putAll(fields("option", option, "diplomacy", game.getDiplomacyOptionType(option),
                    "amount", game.getDiplomacyOptionAmount(option),
                    "duration", game.getDiplomacyOptionDuration(option)));
        }
        return result;
    }

    static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put((String) keyValues[index], keyValues[index + 1]);
        }
        return result;
    }

    static String json(Object value) {
        StringBuilder output = new StringBuilder();
        appendJson(output, value);
        return output.toString();
    }

    private static void appendJson(StringBuilder output, Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map) {
            output.append('{');
            boolean comma = false;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (comma) output.append(',');
                appendJson(output, entry.getKey().toString());
                output.append(':');
                appendJson(output, entry.getValue());
                comma = true;
            }
            output.append('}');
        } else if (value instanceof Iterable) {
            output.append('[');
            boolean comma = false;
            for (Object item : (Iterable<?>) value) {
                if (comma) output.append(',');
                appendJson(output, item);
                comma = true;
            }
            output.append(']');
        } else {
            output.append('"');
            for (char letter : value.toString().toCharArray()) {
                switch (letter) {
                    case '"': output.append("\\\""); break;
                    case '\\': output.append("\\\\"); break;
                    case '\n': output.append("\\n"); break;
                    case '\r': output.append("\\r"); break;
                    case '\t': output.append("\\t"); break;
                    default:
                        if (letter < 32) {
                            output.append("\\u00").append(Character.forDigit(letter >>> 4, 16))
                                    .append(Character.forDigit(letter & 15, 16));
                        } else {
                            output.append(letter);
                        }
                }
            }
            output.append('"');
        }
    }
}
