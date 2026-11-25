package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameState.CombatEvent;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.example.worlddomination.game.ai.DecisionRequest.fields;
import static com.example.worlddomination.game.ai.DecisionRequest.json;

/** Offline teacher. Scores legal pairs on isolated, matched future streams. */
public final class RolloutTeacher {
    private static final String POLICY = "rollout-v1";
    private static final String PREFERENCE =
            "Protect vulnerable cities, build sustainable combat stock, and pressure exposed rivals.";
    private static final Field RANDOM = gameField("random");
    private static final Field COMBAT = gameField("lastTurnCombatEvents");

    private RolloutTeacher() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Expected: setups output commit collect|pilot");
        boolean pilot = args[3].equals("pilot");
        if (!pilot && !args[3].equals("collect")) throw new IllegalArgumentException("Unknown mode");
        String sourceHash;
        try (InputStream manifest = RolloutTeacher.class.getResourceAsStream("/wd2-engine-sources.sha256")) {
            if (manifest == null) throw new IllegalStateException("Missing source manifest");
            sourceHash = new String(manifest.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8);
             var writer = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] config = line.split("\t");
                String id = config[0];
                Play.Session game = new Play.Session(id, id, Long.parseLong(config[1]),
                        Integer.parseInt(config[2]), Integer.parseInt(config[3]), "baseline", config[4],
                        PREFERENCE, config[5], POLICY, 200, args[2], DecisionRequest.VERSION, sourceHash);
                List<Object> rows = new ArrayList<>();
                List<String> transcript = new ArrayList<>();
                play(game, rows, transcript, pilot);
                Play.Session replay = new Play.Session(game, null);
                for (String choice : transcript) replay.resolveTurn(choice, !choice.isEmpty());
                if (!json(replay.outcome()).equals(json(game.outcome()))) {
                    throw new IllegalStateException("Replay diverged: " + id);
                }
                Map<String, Object> result = fields("config", game.configRow(), "terminal", game.outcome(),
                        "pairs", transcript, "decisions", rows);
                if (pilot) {
                    Play.Session java = new Play.Session(game, null);
                    while (!java.done()) java.resolveTurn("", false);
                    result.put("java", java.outcome());
                }
                writer.write(json(result));
                writer.newLine();
                writer.flush();
            }
        }
    }

    private static void play(Play.Session game, List<Object> rows, List<String> transcript,
                             boolean pilot) throws Exception {
        while (!game.done()) {
            if (game.focalDead()) {
                game.resolveTurn("", false);
                transcript.add("");
                continue;
            }
            byte[] settled = GameSaveCodec.encode(game.game);
            List<CombatEvent> recent = List.copyOf(game.game.getLastTurnCombatEvents());
            game.prepare();
            String observation = game.request.user;
            int turn = game.game.getTurn();
            // Probe seeds differ from the real gameplay stream and are shared across candidate pairs.
            long namespace = 75424645L + 104729L * turn + 32452843L * game.focal;
            Random futures = new Random(namespace);
            long screenSeed = futures.nextLong();
            long firstSeed = futures.nextLong();
            long secondSeed = futures.nextLong();
            Map<String, Double> shortScores = new LinkedHashMap<>();
            for (String id : game.request.planIds) {
                shortScores.put(id, simulate(game, settled, recent, observation, id, screenSeed, 1));
            }
            List<String> finalists = new ArrayList<>(shortScores.keySet());
            finalists.sort(Comparator.<String>comparingDouble(shortScores::get).reversed());
            Map<String, List<Double>> scores = new LinkedHashMap<>();
            String selected = null;
            double best = Double.NEGATIVE_INFINITY;
            double runnerUp = Double.NEGATIVE_INFINITY;
            for (String id : finalists.subList(0, Math.min(4, finalists.size()))) {
                double first = simulate(game, settled, recent, observation, id, firstSeed, 3);
                double second = simulate(game, settled, recent, observation, id, secondSeed, 3);
                scores.put(id, List.of(first, second));
                double score = first + second - 0.25 * Math.abs(first - second);
                if (score > best) {
                    runnerUp = best;
                    best = score;
                    selected = id;
                } else {
                    runnerUp = Math.max(runnerUp, score);
                }
            }
            if (finalists.size() > 1 && best - runnerUp >= 0.25 && (!pilot || turn < 12)) {
                rows.add(fields("id", game.decisionId(), "turn", turn, "system", DecisionRequest.SYSTEM,
                        "user", observation, "selected", selected, "screen", shortScores, "paired", scores));
            }
            // Compare the unmodified fork to the real turn before keeping a candidate.
            Play.Session check = fork(game, settled, recent, observation);
            check.resolveTurn(selected, true);
            game.resolveTurn(selected, true);
            if (!json(check.outcome()).equals(json(game.outcome())) ||
                    !json(check.committedOrders()).equals(json(game.committedOrders()))) {
                throw new IllegalStateException("Nominal fork diverged: " + game.episode + ":" + turn);
            }
            transcript.add(selected);
        }
    }

    private static double simulate(Play.Session game, byte[] snapshot, List<CombatEvent> recent,
                                   String observation, String id, long seed, int horizon) throws Exception {
        Play.Session fork = fork(game, snapshot, recent, observation);
        ((Random) RANDOM.get(fork.game)).setSeed(seed);
        fork.resolveTurn(id, true);
        double immediate = value(fork);
        for (int turn = 1; turn < horizon && !fork.done(); turn++) fork.resolveTurn("", false);
        return immediate * 0.35 + value(fork) * 0.65;
    }

    @SuppressWarnings("unchecked")
    private static Play.Session fork(Play.Session original, byte[] snapshot, List<CombatEvent> recent,
                                     String observation) throws Exception {
        Play.Session fork = new Play.Session(original, GameSaveCodec.decode(snapshot));
        // Saves omit presentation history. Last round's combat events are also actor-visible.
        ((List<CombatEvent>) COMBAT.get(fork.game)).addAll(recent);
        fork.prepare();
        if (!observation.equals(fork.request.user)) {
            throw new IllegalStateException("Fork observation differs: " + original.decisionId());
        }
        return fork;
    }

    private static Field gameField(String name) {
        try {
            Field field = GameState.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static double value(Play.Session session) {
        GameState.PlayerState actor = session.game.getPlayerState(session.focal);
        if (!actor.alive) return -700;
        GameState.PlayerState human = session.game.getHuman();
        double value = actor.populationMillions - 0.52 * human.populationMillions;
        if (!human.alive) value += 180;
        for (GameState.PlayerState rival : session.game.getPlayers()) {
            if (rival.id != actor.id && !rival.human) value -= 0.08 * rival.populationMillions;
        }
        value += 3.8 * Math.sqrt(Math.max(0, actor.moneyMillions));
        value += 0.32 * actor.religion;
        int soldiers = 0;
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            if (weapon.definition.type.equals("soldier")) {
                soldiers += Math.max(0, weapon.inStock) * weapon.definition.level;
            }
        }
        value += 7.0 * Math.sqrt(soldiers);
        value += 3.3 * actor.getInventedTechnologies().size();
        if (actor.getPreparedCarrierName() != null) value += 8;
        return value;
    }
}
