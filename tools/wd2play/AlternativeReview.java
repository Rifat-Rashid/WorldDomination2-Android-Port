package com.example.worlddomination.game.ai;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.example.worlddomination.game.simulation.GameState;

import static com.example.worlddomination.game.ai.DecisionRequest.fields;
import static com.example.worlddomination.game.ai.DecisionRequest.json;

/** Compare previously offered plans from the same state. Outputs are reviewer-only evidence. */
public final class AlternativeReview {
    private AlternativeReview() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Expected: log turn horizon plan_ids_csv future_seeds_csv");
        }
        Path log = Path.of(args[0]);
        int turn = Integer.parseInt(args[1]);
        int horizon = Integer.parseInt(args[2]);
        List<String> alternatives = Arrays.asList(args[3].split(",", -1));
        List<Long> seeds = new ArrayList<>();
        for (String seed : args[4].split(",", -1)) seeds.add(Long.parseLong(seed));
        if (turn < 0 || horizon < 1 || alternatives.size() < 2 ||
                new HashSet<>(alternatives).size() != alternatives.size() ||
                seeds.size() < 2 || seeds.stream().anyMatch(seed -> seed < 0) ||
                new HashSet<>(seeds).size() != seeds.size()) {
            throw new IllegalArgumentException("Require distinct plans and at least two distinct future seeds");
        }
        String observation = null;
        List<Map<String, Object>> results = new ArrayList<>();
        for (String choice : alternatives) {
            for (long seed : seeds) {
                Play.Session branch = Play.restore(log, turn);
                if (branch.done() || branch.focalDead()) throw new IllegalArgumentException("No focal decision");
                branch.prepare();
                if (observation == null) observation = branch.request.user;
                if (!observation.equals(branch.request.user)) throw new IllegalStateException("Observation drift");
                if (!choice.equals("java") && !branch.request.planIds.contains(choice)) {
                    throw new IllegalArgumentException("Plan was not offered: " + choice);
                }
                branch.simulation.reseedFuture(seed);
                boolean offered = !choice.equals("java");
                branch.advance(offered ? choice : "", "paired review", offered);
                for (int i = 1; i < horizon && !branch.done(); i++) {
                    branch.advance("", "Java continuation", false);
                }
                int opposingPopulation = Arrays.stream(branch.game.getPlayers()).filter(p -> p.id != branch.focal)
                        .mapToInt(p -> p.populationMillions).sum();
                boolean terminal = branch.game.getMatchStatus() != GameState.MatchStatus.ONGOING;
                results.add(fields("choice", choice, "future_seed", seed, "turn", branch.game.getTurn(),
                        "alive", !branch.focalDead(), "population", branch.game.getPlayerState(branch.focal).populationMillions,
                        "human_population", branch.game.getHuman().populationMillions,
                        "opposing_population", opposingPopulation, "human_alive", branch.game.getHuman().alive,
                        "terminal", terminal, "truncated", branch.done() && !terminal));
            }
        }
        System.out.println(json(fields("turn", turn, "horizon", horizon, "results", results)));
    }
}
