package com.example.worlddomination.game.simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A pair of committed source actions. Neither action resolves during selection. */
public final class TurnPlan {
    final Object matchIdentity;
    public final int actorId;
    public final int turn;
    public final List<Order> orders;

    TurnPlan(GameState state, int actorId, Order first, Order second) {
        this.matchIdentity = state.planIdentity;
        this.actorId = actorId;
        this.turn = state.getTurn();
        this.orders = Collections.unmodifiableList(Arrays.asList(first, second));
    }

    public static final class Order {
        public final GameState.Action action;
        public final int reservedMillions;
        public final Map<String, Integer> productionBudgets;
        public final List<String> technologies;
        public final int targetPlayerId;
        public final int targetCityIndex;
        public final String weaponName;
        public final String payloadName;
        public final boolean prepareCarrier;
        public final int diplomacyOption;
        public final List<GameState.ShipStrike> shipStrikes;
        public final String spyBuilding;

        Order(GameState.Action action, int reservedMillions,
                      Map<String, Integer> productionBudgets, List<String> technologies,
                      int targetPlayerId, int targetCityIndex, String weaponName,
                      String payloadName, boolean prepareCarrier, int diplomacyOption,
                      List<GameState.ShipStrike> shipStrikes, String spyBuilding) {
            this.action = action;
            this.reservedMillions = reservedMillions;
            this.productionBudgets = Collections.unmodifiableMap(
                    new LinkedHashMap<>(productionBudgets));
            this.technologies = Collections.unmodifiableList(new ArrayList<>(technologies));
            this.targetPlayerId = targetPlayerId;
            this.targetCityIndex = targetCityIndex;
            this.weaponName = weaponName;
            this.payloadName = payloadName;
            this.prepareCarrier = prepareCarrier;
            this.diplomacyOption = diplomacyOption;
            this.shipStrikes = Collections.unmodifiableList(new ArrayList<>(shipStrikes));
            this.spyBuilding = spyBuilding;
        }

        static Order simple(GameState.Action action) {
            return targeted(action, -1, -1, null, null, null);
        }

        static Order targeted(GameState.Action action, int target, int city, String weapon,
                              String payload, String spyBuilding) {
            return new Order(action, 0, Collections.emptyMap(), Collections.emptyList(),
                    target, city, weapon, payload, false, -1, Collections.emptyList(),
                    spyBuilding);
        }

        static Order research(List<String> technologies, int budget) {
            return new Order(GameState.Action.RESEARCH, budget, Collections.emptyMap(),
                    technologies, -1, -1, null, null, false, -1, Collections.emptyList(), null);
        }

        static Order production(String category, int budget, int reserved) {
            return new Order(GameState.Action.PRODUCTION, reserved,
                    Collections.singletonMap(category, budget), Collections.emptyList(),
                    -1, -1, null, null, false, -1, Collections.emptyList(), null);
        }

        static Order diplomacy(int target, int option, int reserved) {
            return new Order(GameState.Action.DIPLOMACY, reserved, Collections.emptyMap(),
                    Collections.emptyList(), target, -1, null, null, false, option,
                    Collections.emptyList(), null);
        }

        static Order prepare(String carrier) {
            return new Order(GameState.Action.PREPARED_ATTACK, 0, Collections.emptyMap(),
                    Collections.emptyList(), -1, -1, carrier, null, true, -1,
                    Collections.emptyList(), null);
        }

        static Order ship(String carrier, List<GameState.ShipStrike> strikes) {
            return new Order(GameState.Action.PREPARED_ATTACK, 0, Collections.emptyMap(),
                    Collections.emptyList(), strikes.get(0).targetPlayerId,
                    strikes.get(0).cityIndex, carrier, null, false, -1, strikes, null);
        }
    }
}
