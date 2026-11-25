package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameState.CombatEvent;
import com.example.worlddomination.game.simulation.GameState.PlayerState;
import com.example.worlddomination.game.simulation.TurnPlan;
import com.example.worlddomination.game.simulation.ReviewSimulation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.worlddomination.game.ai.DecisionRequest.fields;
import static com.example.worlddomination.game.ai.DecisionRequest.json;

/** Development-only, replay-verified access to the Android rules engine. */
public final class Play {
    private static final int SCHEMA = 1;

    private Play() { }

    public static void main(String[] args) {
        try {
            if (args.length < 2 || args[1].isEmpty()) throw new IllegalArgumentException("A log path is required");
            Path file = Path.of(args[1]);
            if ("init".equals(args[0])) {
                init(file, args);
                return;
            }
            if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Episode log does not exist");
            Path lockFile = file.resolveSibling(file.getFileName().toString() + ".lock");
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE); FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IllegalStateException("Episode log is in use");
                Session session = restore(file,
                        "checkpoint".equals(args[0]) ? Integer.parseInt(args[2]) : -1);
                handle(session, file, args[0], args.length >= 3 ? args[2] : "",
                        args.length >= 4 ? args[3] : "", args.length >= 5 ? args[4] : "");
            }
        } catch (Exception failure) {
            System.out.println(json(fields("type", "error", "message", failure.toString())));
            System.exit(1);
        }
    }

    private static void init(Path file, String[] args) throws Exception {
        String episode = args[2];
        String family = args[3];
        long seed = Long.parseLong(args[4]);
        int difficulty = Integer.parseInt(args[5]);
        int actor = Integer.parseInt(args[6]);
        String policy = oneOf(args[7], "baseline", "stochastic");
        String human = oneOf(args[8], "pressure", "development", "stochastic");
        String preference = args[9];
        String leaders = args[10];
        String teacher = args[11];
        int maxTurns = Integer.parseInt(args[12]);
        String commit = args[13];
        if (episode.isBlank() || family.isBlank() || maxTurns < 1) {
            throw new IllegalArgumentException("Episode, family, and a positive turn cap are required");
        }
        String sourceHash;
        try (InputStream manifest = Play.class.getResourceAsStream("/wd2-engine-sources.sha256")) {
            if (manifest == null) throw new IllegalStateException("Build the harness to generate its source fingerprint");
            sourceHash = new String(manifest.readAllBytes(), StandardCharsets.UTF_8);
        }
        Session session = new Session(episode, family, seed, difficulty, actor, policy, human,
                preference, leaders, teacher, maxTurns, commit, DecisionRequest.VERSION, sourceHash);
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, session.configRow() + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        System.out.println(session.observe());
    }

    private static String oneOf(String value, String... options) {
        for (String option : options) if (option.equals(value)) return value;
        throw new IllegalArgumentException("Unknown policy " + value);
    }

    static Session restore(Path file, int stopBeforeTurn) throws Exception {
        try (BufferedReader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = input.readLine();
            if (header == null || !"episode".equals(field(header, "type"))
                    || integer(header, "schema") != SCHEMA) {
                throw new IllegalArgumentException("Unsupported episode log");
            }
            int version = integer(header, "decision_contract");
            if (version < 1 || version > DecisionRequest.VERSION) {
                throw new IllegalArgumentException("Unsupported decision contract " + version);
            }
            Session session = new Session(field(header, "episode"), field(header, "family"),
                    Long.parseLong(field(header, "seed")), integer(header, "difficulty"),
                    integer(header, "actor"), field(header, "policy"), field(header, "human_policy"),
                    field(header, "preference"), field(header, "leaders"), field(header, "teacher"),
                    integer(header, "max_turns"), field(header, "engine_commit"), version,
                    version >= 2 ? field(header, "engine_source_sha256") : "");
            String row;
            boolean terminalSeen = false;
            while (session.game.getTurn() != stopBeforeTurn && (row = input.readLine()) != null) {
                String type = field(row, "type");
                if (terminalSeen && !type.equals("rejection")) {
                    throw new IllegalStateException("Unexpected row after terminal evidence");
                }
                switch (type) {
                    case "decision":
                    case "continuation":
                        String regenerated = session.advance(field(row, "selected"), field(row, "reason"));
                        if (!row.equals(regenerated)) {
                            throw new IllegalStateException("Replay diverged at turn " + (session.game.getTurn() - 1));
                        }
                        break;
                    case "evaluation":
                        String choice = field(row, "selected");
                        if (!row.equals(session.advance(choice, field(row, "reason"), !choice.isEmpty()))) {
                            throw new IllegalStateException("Evaluation diverged at turn " + (session.game.getTurn() - 1));
                        }
                        break;
                    case "terminal":
                        if (!session.done() || !row.equals(session.statusRow())) {
                            throw new IllegalStateException("Terminal evidence diverged");
                        }
                        terminalSeen = true;
                        break;
                    case "rejection":
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown episode row type: " + type);
                }
            }
            if (stopBeforeTurn >= 0 && session.game.getTurn() != stopBeforeTurn) {
                throw new IllegalArgumentException("Checkpoint turn does not occur in this episode");
            }
            if (stopBeforeTurn < 0 && session.done() && !terminalSeen) {
                throw new IllegalStateException("Episode is missing its terminal row");
            }
            return session;
        }
    }

    private static void handle(Session session, Path file, String command, String value, String reason,
                               String expectedDecision)
            throws Exception {
        switch (command) {
            case "observe":
                System.out.println(session.observe());
                break;
            case "choose":
                System.out.println(choose(session, file, value, reason, expectedDecision));
                break;
            case "fallback":
                if (!session.isEvaluation() || session.done() || session.focalDead()
                        || !session.decisionId().equals(expectedDecision)) {
                    throw new IllegalStateException("No exact evaluation decision for original AI fallback");
                }
                String fallback = session.advance("", reason, false);
                append(file, session.done() ? fallback + "\n" + session.statusRow() : fallback);
                System.out.println(fallback);
                break;
            case "status":
            case "replay":
                System.out.println(session.statusRow());
                break;
            case "checkpoint":
                if (session.done()) throw new IllegalStateException("Checkpoint must be preterminal");
                Path output = Path.of(reason);
                if (output.getParent() != null) Files.createDirectories(output.getParent());
                byte[] checkpoint = GameSaveCodec.encode(session.game);
                if (GameSaveCodec.decode(checkpoint).getTurn() != session.game.getTurn()) {
                    throw new IllegalStateException("Checkpoint round-trip failed");
                }
                Files.write(output, checkpoint, StandardOpenOption.CREATE_NEW);
                System.out.println(json(fields("type", "checkpoint", "turn", session.game.getTurn(),
                        "path", output.toAbsolutePath().toString())));
                break;
            case "continue":
                if (!session.focalDead() && !session.done()) {
                    throw new IllegalStateException("Focal actor is still alive; submit a teacher choice");
                }
                System.out.println(continueMatch(session, file));
                break;
            case "stream":
                stream(session, file);
                break;
            case "compare":
                compareFocalPolicies(session);
                break;
            default:
                throw new IllegalArgumentException("Unknown command " + command);
        }
    }

    private static void compareFocalPolicies(Session episode) {
        if (episode.game.getMatchStatus() == GameState.MatchStatus.ONGOING) {
            throw new IllegalStateException("Complete the teacher episode before running comparisons");
        }
        Map<String, Object> comparisons = new LinkedHashMap<>();
        comparisons.put("teacher", episode.outcome());
        for (String focalPolicy : new String[]{"existing_ai", "legal_stochastic"}) {
            Session baseline = new Session(episode.episode, episode.family, episode.seed,
                    episode.difficulty, episode.focal, episode.policy, episode.human,
                    episode.preference, episode.leaders, focalPolicy, episode.maxTurns,
                    episode.commit, episode.version, episode.sourceHash);
            boolean stochastic = "legal_stochastic".equals(focalPolicy);
            while (!baseline.done()) {
                baseline.prepare();
                String id = stochastic && !baseline.focalDead()
                        ? baseline.selectPolicy(baseline.request, "stochastic") : "";
                baseline.advance(id, focalPolicy, stochastic);
            }
            comparisons.put(focalPolicy, baseline.outcome());
        }
        System.out.println(json(fields("type", "matched_comparison", "episode", episode.episode,
                "family", episode.family, "difficulty", episode.difficulty,
                "actor", episode.focal, "outcomes", comparisons)));
    }

    private static void stream(Session session, Path file) throws Exception {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            for (String line; (line = input.readLine()) != null; ) {
                String[] parts = line.split("\t", 4);
                if ("quit".equals(parts[0])) return;
                try {
                    oneOf(parts[0], "observe", "choose", "status", "continue", "fallback");
                    handle(session, file, parts[0], parts.length > 1 ? parts[1] : "",
                            parts.length > 2 ? parts[2] : "", parts.length > 3 ? parts[3] : "");
                } catch (Exception failure) {
                    System.out.println(json(fields("type", "error", "message", failure.toString())));
                }
                System.out.flush();
            }
        }
    }

    private static String choose(Session session, Path file, String selection, String reason,
                                 String expectedDecision) throws Exception {
        if (session.done() || session.focalDead()) {
            throw new IllegalStateException("No focal decision is pending");
        }
        session.prepare();
        boolean stale = !expectedDecision.isEmpty() && !expectedDecision.equals(session.decisionId());
        if (stale || session.request.selectId(selection) == null) {
            String rejection = json(fields("type", "rejection", "schema", SCHEMA,
                    "episode", session.episode, "decision_id", session.decisionId(),
                    "selected", selection, "reason", reason, "expected", expectedDecision,
                    "error", stale ? "stale_decision" : "unknown_plan_id"));
            append(file, rejection);
            return rejection;
        }
        String row = session.advance(selection, reason);
        append(file, session.done() ? row + "\n" + session.statusRow() : row);
        return row;
    }

    private static String continueMatch(Session session, Path file) throws Exception {
        while (!session.done()) {
            String row = session.advance("", "source AI continuation after elimination");
            append(file, session.done() ? row + "\n" + session.statusRow() : row);
        }
        return session.statusRow();
    }

    private static void append(Path path, String content) throws Exception {
        Files.writeString(path, content + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static int integer(String row, String key) {
        return Integer.parseInt(field(row, key));
    }

    /** Reads scalar audit fields written before nested observations and evidence. */
    private static String field(String row, String key) {
        Matcher match = Pattern.compile("\"" + Pattern.quote(key)
                + "\":(?:\"((?:\\\\.|[^\"\\\\])*)\"|(-?[0-9]+))").matcher(row);
        if (!match.find()) throw new IllegalArgumentException("Missing audit field " + key);
        if (match.group(1) == null) return match.group(2);
        String encoded = match.group(1);
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < encoded.length(); i++) {
            char letter = encoded.charAt(i);
            if (letter != '\\') {
                decoded.append(letter);
                continue;
            }
            switch (encoded.charAt(++i)) {
                case 'n': decoded.append('\n'); break;
                case 'r': decoded.append('\r'); break;
                case 't': decoded.append('\t'); break;
                case 'u':
                    decoded.append((char) Integer.parseInt(encoded.substring(i + 1, i + 5), 16));
                    i += 4;
                    break;
                case '"': decoded.append('"'); break;
                case '\\': decoded.append('\\'); break;
                default: throw new IllegalArgumentException("Invalid audit escape");
            }
        }
        return decoded.toString();
    }

    static final class Session {
        final String episode;
        final String family;
        final long seed;
        final int difficulty;
        final int focal;
        final String policy;
        final String human;
        final String preference;
        final String leaders;
        final String teacher;
        final int maxTurns;
        final String commit;
        final int version;
        final String sourceHash;
        final GameState game;
        final ReviewSimulation simulation;
        DecisionRequest request;
        String humanSelection;
        final List<Boolean> humanAnswers = new ArrayList<>();

        Session(String episode, String family, long seed, int difficulty, int focal, String policy,
                String human, String preference, String leaders, String teacher, int maxTurns,
                String commit, int version, String sourceHash) {
            this.episode = episode;
            this.family = family;
            this.seed = seed;
            this.difficulty = difficulty;
            this.focal = focal;
            this.policy = policy;
            this.human = human;
            this.preference = preference;
            this.leaders = leaders;
            this.teacher = teacher;
            this.maxTurns = maxTurns;
            this.commit = commit;
            this.version = version;
            this.sourceHash = sourceHash;
            GameSetup setup = new GameSetup(seed);
            setup.setDifficulty(difficulty);
            for (String leader : leaders.split(",")) {
                if (!setup.toggleOpponent(Integer.parseInt(leader))) {
                    throw new IllegalArgumentException("Choose one to four distinct leaders");
                }
            }
            simulation = new ReviewSimulation(setup);
            game = simulation.game;
            if (focal < 1 || focal > setup.getSelectedOpponentIds().size()) {
                throw new IllegalArgumentException("Focal actor must be an occupied AI seat");
            }
        }

        String configRow() {
            Map<String, Object> metadata = fields("type", "episode", "schema", SCHEMA, "episode", episode,
                    "family", family, "seed", seed, "difficulty", difficulty, "actor", focal,
                    "policy", policy, "human_policy", human, "preference", preference,
                    "leaders", leaders, "teacher", teacher, "max_turns", maxTurns,
                    "engine_commit", commit, "decision_contract", version);
            if (version >= 2) metadata.put("engine_source_sha256", sourceHash);
            return json(metadata);
        }

        boolean focalDead() {
            return !game.getPlayerState(focal).alive;
        }

        boolean isEvaluation() {
            return teacher.startsWith("evaluator:");
        }

        boolean done() {
            return game.getMatchStatus() != GameState.MatchStatus.ONGOING || game.getTurn() >= maxTurns;
        }

        String decisionId() {
            return episode + ":" + game.getTurn() + ":" + focal;
        }

        void prepare() {
            if (game.isOpponentPlanningPending()) return;
            humanAnswers.clear();
            GameState.DiplomacyProposal proposal;
            while ((proposal = game.getPendingHumanDiplomacyProposal()) != null) {
                boolean accept = proposal.optionIndex < 3;
                if (!accept && "development".equals(human)) {
                    accept = game.canAcceptPendingHumanDiplomacy()
                            && game.getPreparedCarrierName(proposal.proposerId) != null;
                }
                if (!game.respondToDiplomacyProposal(accept)) throw new IllegalStateException("Cannot answer proposal");
                humanAnswers.add(accept);
            }
            DecisionRequest humanRequest = DecisionRequest.capture(game, 0, human, version);
            humanSelection = selectPolicy(humanRequest, human);
            if (!game.queueHumanPlan(humanRequest.selectId(humanSelection))) {
                throw new IllegalStateException("Human policy failed to queue two actions");
            }
            game.beginOpponentPlanning();
            request = focalDead() ? null : DecisionRequest.capture(game, focal, preference, version);
        }

        String observe() {
            if (done() || focalDead()) return statusRow();
            prepare();
            return withCandidates(fields("type", "observation", "schema", SCHEMA,
                    "episode", episode, "family", family, "actor", focal,
                    "decision_id", decisionId(), "turn", game.getTurn(), "system", DecisionRequest.SYSTEM,
                    "user", request.user), request);
        }

        String advance(String selectedId, String reason) {
            return advance(selectedId, reason, true);
        }

        String advance(String selectedId, String reason, boolean externalFocal) {
            if (done()) throw new IllegalStateException("Episode is over");
            boolean continuation = focalDead();
            prepare();
            DecisionRequest previous = request;
            TurnPlan selected = previous == null ? null : previous.selectId(selectedId);
            if (!continuation && externalFocal && selected == null) {
                throw new IllegalArgumentException("Unknown plan ID " + selectedId);
            }
            Map<Integer, TurnPlan> assignments = new LinkedHashMap<>();
            Map<Integer, String> stochasticChoices = new LinkedHashMap<>();
            if (selected != null) assignments.put(focal, selected);
            if (!continuation && "stochastic".equals(policy)) {
                for (PlayerState actor : game.getPlayers()) {
                    if (actor.human || !actor.alive || actor.id == focal) continue;
                    DecisionRequest rival = DecisionRequest.capture(game, actor.id, "stochastic", version);
                    String choice = selectPolicy(rival, "stochastic");
                    assignments.put(actor.id, rival.selectId(choice));
                    stochasticChoices.put(actor.id, choice);
                }
            }

            int turn = game.getTurn();
            String decision = decisionId();
            if (!game.finishOpponentPlanning(assignments).equals(assignments.keySet())) {
                throw new IllegalStateException("A replay plan became illegal before settlement");
            }
            request = null;
            String rowType = continuation ? "continuation" : (isEvaluation() ? "evaluation" : "decision");
            Map<String, Object> row = fields("type", rowType,
                    "schema", SCHEMA, "episode", episode, "family", family, "decision_id", decision,
                    "actor", focal, "role", "opponent", "turn", turn, "difficulty", difficulty,
                    "selected", selectedId, "reason", reason, "teacher", teacher, "policy", policy,
                    "engine_commit", commit, "collection_version", SCHEMA, "preference", preference,
                    "human_selected", humanSelection, "human_answers", humanAnswers,
                    "stochastic_selected", stochasticChoices,
                    "transition", transition(), "outcome", outcome());
            if (version >= 2) {
                row.put("engine_source_sha256", sourceHash);
                row.put("committed_orders", committedOrders());
                PlayerState focalActor = game.getPlayerState(focal);
                if (focalActor.alive && game.getMatchStatus() == GameState.MatchStatus.ONGOING) {
                    row.put("next_observation", DecisionRequest.capture(game, focal, preference, version).user);
                }
            }
            if (continuation) return json(row);
            row.put("system", DecisionRequest.SYSTEM);
            row.put("user", previous.user);
            return withCandidates(row, previous);
        }

        Map<String, Object> committedOrders() {
            Map<String, Object> all = new LinkedHashMap<>();
            for (PlayerState actor : game.getPlayers()) {
                List<Object> orders = new ArrayList<>();
                for (TurnPlan.Order order : game.getLastCommittedOrders(actor.id)) {
                    orders.add(DecisionRequest.orderData(game, order));
                }
                if (!orders.isEmpty()) all.put(Integer.toString(actor.id), orders);
            }
            return all;
        }

        String selectPolicy(DecisionRequest candidate, String policyName) {
            if (candidate.planIds.isEmpty()) throw new IllegalStateException("No legal plan for actor " + candidate.actorId);
            if ("stochastic".equals(policyName)) {
                Random choices = new Random(seed ^ (game.getTurn() * 15485863L) ^ (candidate.actorId * 2477L));
                return candidate.planIds.get(choices.nextInt(candidate.planIds.size()));
            }
            return Collections.max(candidate.planIds, Comparator.comparingDouble(id ->
                    rateHuman(candidate.selectId(id), "development".equals(policyName))));
        }

        double rateHuman(TurnPlan plan, boolean development) {
            PlayerState actor = game.getHuman();
            double score = 0;
            for (TurnPlan.Order order : plan.orders) {
                switch (order.action) {
                    case ATTACK:
                        if (order.spyBuilding != null) {
                            score += 4;
                        } else {
                            int power = actor.getWeapons().get(order.weaponName).definition.maxKillsMillions;
                            score += 12 + power / 2d;
                        }
                        break;
                    case PREPARED_ATTACK:
                        boolean canLoad = !game.getAvailablePreparedPayloads(0, order.weaponName).isEmpty();
                        score += order.prepareCarrier ? (canLoad ? 25 : -5) : 70;
                        break;
                    case RESOURCES:
                        score += actor.moneyMillions < 20 ? 18 : (actor.moneyMillions < 50 ? 10 : 2);
                        break;
                    case PRODUCTION:
                        score += order.productionBudgets.containsKey("soldier") ? 25 : 14;
                        break;
                    case RESEARCH:
                        score += 15 + (development ? 10 : 0);
                        if (order.technologies.stream().anyMatch(name -> name.startsWith("soldier"))) score += 5;
                        break;
                    case PROPAGANDA:
                        score += actor.religion / 4d;
                        break;
                    case MASS_MEDIA:
                        score += actor.religion < 70 ? (development ? 13 : 8) : 0;
                        break;
                    case DEFENSE:
                        if (order.weaponName.equals(game.getCounterWeaponForPreparedThreat(0))) score += 26;
                        break;
                    default:
                        break;
                }
            }
            return score;
        }

        Map<String, Object> transition() {
            List<Object> events = new ArrayList<>();
            for (CombatEvent combat : game.getLastTurnCombatEvents()) {
                if (combat.attackerPlayerId != focal && combat.defenderPlayerId != focal) continue;
                events.add(fields("event", combat.type, "from", combat.attackerPlayerId,
                        "to", combat.defenderPlayerId, "city", combat.cityIndex,
                        "weapon", combat.weaponName, "payload", combat.payloadName,
                        "casualties", combat.casualtiesMillions));
            }
            PlayerState actor = game.getPlayerState(focal);
            return fields("actor_messages", game.getMessages(focal), "combat", events,
                    "own_population", actor.populationMillions, "own_cash", actor.moneyMillions,
                    "own_alive", actor.alive);
        }

        Map<String, Object> outcome() {
            List<Object> nations = new ArrayList<>();
            boolean terminal = game.getMatchStatus() != GameState.MatchStatus.ONGOING;
            for (PlayerState country : game.getPlayers()) {
                Map<String, Object> nation = fields("actor", country.id, "alive", country.alive,
                        "population", country.populationMillions);
                if (terminal) nation.put("score", country.score);
                nations.add(nation);
            }
            String reason = terminal ? (game.getMatchStatus() == GameState.MatchStatus.VICTORY
                    ? "all_rival_nations_eliminated" : "human_nation_eliminated")
                    : (done() ? "turn_cap_truncation" : "ongoing");
            return fields("status", game.getMatchStatus(), "reason", reason,
                    "terminal", terminal, "truncated", !terminal && done(), "turn", game.getTurn(),
                    "human_final_score", terminal ? game.getFinalScore() : null, "nations", nations);
        }

        String statusRow() {
            return json(fields("type", done() ? "terminal" : "status", "schema", SCHEMA,
                    "episode", episode, "family", family, "actor", focal,
                    "focal_alive", !focalDead(), "outcome", outcome()));
        }
    }

    private static String withCandidates(Map<String, Object> row, DecisionRequest request) {
        String serialized = json(row);
        return serialized.substring(0, serialized.length() - 1) + ",\"candidates\":"
                + request.candidateJson + "}";
    }
}
