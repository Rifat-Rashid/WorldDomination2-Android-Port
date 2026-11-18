package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Independently captured incoming-answer campaigns; canonical/controller phases only. */
@RunWith(Parameterized.class)
public final class SourceAcceptedIncomingCampaignTest {
    private static final String FIXTURE = "/source-accepted-campaigns.json.gz";
    private static final String SHA256 =
            "ec615093548a5f401c82242ccb3694a2088b5a93b360b6db254fedc8dbc1cf9a";

    private final long seed;
    private final int difficulty;
    private final JsonValue capture;

    public SourceAcceptedIncomingCampaignTest(long seed, int difficulty, JsonValue capture) {
        this.seed = seed;
        this.difficulty = difficulty;
        this.capture = capture;
    }

    @Parameterized.Parameters(name = "seed={0}, difficulty={1}")
    public static Collection<Object[]> campaigns() throws Exception {
        byte[] compressed;
        try (InputStream stream = SourceAcceptedIncomingCampaignTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull("source matrix resource", stream);
            compressed = stream.readAllBytes();
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(compressed)) {
            hash.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        assertEquals("independently captured source matrix bytes", SHA256, hash.toString());
        JsonValue matrix;
        try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            matrix = new JsonReader().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals("source-accepted-campaigns-v1", matrix.getString("schema"));
        List<Object[]> cases = new ArrayList<>();
        Set<String> pairs = new HashSet<>();
        int totalTurns = 0;
        int totalResponses = 0;
        int insufficientResponses = 0;
        for (JsonValue campaign : matrix.get("campaigns")) {
            JsonValue setup = campaign.get("setup");
            assertTrue("unique captured seed/difficulty pair",
                    pairs.add(setup.getLong("seed") + ":" + setup.getInt("difficulty")));
            cases.add(new Object[]{setup.getLong("seed"), setup.getInt("difficulty"), campaign});
            assertEquals("each captured campaign reaches turn25", 25, campaign.get("turns").size);
            totalTurns += campaign.get("turns").size;
            totalResponses += campaign.getInt("responseCount");
            insufficientResponses += campaign.getInt("insufficientCount");
        }
        assertEquals("all captured seed/difficulty pairs", 4, cases.size());
        assertEquals(Set.of("0:1", "0:2", "2:1", "2:2"), pairs);
        assertEquals("retain every captured source turn", 100, totalTurns);
        assertEquals("retain every incoming response", 28, totalResponses);
        assertEquals("retain every insufficient-money branch", 6, insufficientResponses);
        return cases;
    }

    @Test
    public void acceptedIncomingCampaignMatchesEverySourcePhase() throws Exception {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(difficulty);
        for (JsonValue opponent : capture.get("setup").get("opponents")) {
            assertTrue(where(0) + "select opponent " + opponent.asInt(), setup.toggleOpponent(opponent.asInt()));
        }
        GameState game = new GameState(setup);
        SourceActionMenuState menu = new SourceActionMenuState();
        assertState(game, capture.get("initial"));
        int responses = 0;
        int insufficient = 0;
        for (JsonValue turn : capture.get("turns")) {
            String prefix = where(turn.getInt("turn"));
            int responseIndex = 0;
            for (JsonValue answer : turn.get("offersHandled")) {
                String context = prefix + "response " + responseIndex++ + " ";
                assertResponse(game, menu, answer.get("before"), context + "before ");
                GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
                assertNotNull(context + "proposal", proposal);
                assertEquals(context + "proposer", answer.get("proposal").getInt("_leader"), proposal.proposerId);
                assertEquals(context + "option", answer.get("proposal").getInt("_option"), proposal.optionIndex);
                boolean accepted = "accepted".equals(answer.getString("response"));
                if (!accepted) assertEquals(context + "source response kind",
                        "insufficient-accept-then-reject", answer.getString("response"));
                assertEquals(context + "affordability", accepted, game.canAcceptPendingHumanDiplomacy());
                assertEquals(context + "accept result", accepted, game.respondToDiplomacyProposal(true));
                if (accepted) game.cancelSelection();
                else menu.showMoneyError(game);
                assertResponse(game, menu, answer.get("afterAcceptAttempt"), context + "after accept attempt ");
                if (!accepted) {
                    insufficient++;
                    menu.dismissError(game);
                    assertResponse(game, menu, answer.get("afterErrorAcknowledged"), context + "after error OK ");
                    assertTrue(context + "reject after failed accept", game.respondToDiplomacyProposal(false));
                    game.cancelSelection();
                }
                assertResponse(game, menu, answer.get("afterFinalResponse"), context + "after final response ");
                responses++;
            }
            assertNull(prefix + "no extra proposal", game.getPendingHumanDiplomacyProposal());
            assertEquals(prefix + "two recorded actions", 2, turn.get("humanQueuedActions").size);
            for (JsonValue action : turn.get("humanQueuedActions")) {
                GameState.Action nativeAction;
                switch (action.asString()) {
                    case "resources": nativeAction = GameState.Action.RESOURCES; break;
                    case "religion": nativeAction = GameState.Action.MASS_MEDIA; break;
                    case "skip_action": nativeAction = GameState.Action.SKIP; break;
                    default: throw new AssertionError(prefix + "unknown recorded action " + action.asString());
                }
                game.selectAction(nativeAction);
                assertTrue(prefix + "accept " + action.asString(), game.acceptSelectedAction());
            }
            game.confirmTurn();
            assertState(game, turn.get("state"));
        }
        assertEquals(where(25) + "all source responses", capture.getInt("responseCount"), responses);
        assertEquals(where(25) + "all insufficient branches", capture.getInt("insufficientCount"), insufficient);
        assertTerminal(game, capture.get("terminal"));
    }

    private static void assertResponse(GameState game, SourceActionMenuState menu,
                                       JsonValue expected, String prefix) throws Exception {
        assertEquals(prefix + "turn", expected.getInt("turn"), game.getTurn());
        assertEquals(prefix + "RNG", expected.getDouble("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        assertEquals(prefix + "human money", expected.getInt("humanMoney"), game.getHuman().moneyMillions);
        assertEquals(prefix + "human budget", expected.getDouble("humanTurnMoney"),
                game.getHuman().turnMoneyMillions, 0d);
        assertEquals(prefix + "selected action", expected.get("humanAction").isNull() ? null
                : expected.getString("humanAction"), game.getPendingHumanDiplomacyProposal() == null ? null : "diplomacyPending");
        assertNull(prefix + "no ordinary selected action during answer", game.getSelectedAction());
        assertEquals(prefix + "error presentation", expected.get("interfaceDiplomacyError").isNull() ? null
                : expected.getString("interfaceDiplomacyError"), menu.hasPendingError() ? "Not enough money" : null);
        assertProposalList(game.getHuman(), "pendingDiplomacyOrder", expected.get("pending"), true, prefix);
        assertProposalList(game, "acceptedHumanDiplomacy", expected.get("accepted"), true, prefix);
        assertProposalList(game, "rejectedHumanDiplomacy", expected.get("rejected"), true, prefix);
        for (int id = 0; id < 5; id++) {
            GameState.PlayerState player = game.getPlayerState(id);
            String who = prefix + "player " + id + " ";
            assertEquals(who + "money", expected.get("playerMoney").getInt(id), player.moneyMillions);
            double budget = expected.get("playerTurnMoney").getDouble(id);
            assertEquals(who + "invalid budget", Double.isNaN(budget), player.hasInvalidAiTurnMoney());
            if (!Double.isNaN(budget)) assertEquals(who + "budget", budget, player.turnMoneyMillions, 0d);
            for (int other = 0; other < 5; other++) {
                assertEquals(who + "regard " + other, expected.get("regards").get(id).getDouble(other),
                        player.getRegardTowardExact(other), 0d);
                JsonValue ceasefire = expected.get("ceasefires").get(id).get(other);
                assertEquals(who + "ceasefire status " + other, ceasefire.getBoolean("status"), player.hasCeasefireWith(other));
                assertEquals(who + "ceasefire turns " + other, ceasefire.getInt("turnsLeft"), player.getCeasefireTurnsWith(other));
            }
        }
    }

    /** Read-only observation of ordered queues/buffers; never injects state or changes production API. */
    private static void assertProposalList(Object owner, String fieldName, JsonValue expected,
                                           boolean rawKeys, String prefix) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(owner);
        assertTrue(prefix + fieldName + " list", value instanceof List<?>);
        List<?> actual = (List<?>) value;
        assertEquals(prefix + fieldName + " size", expected.size, actual.size());
        for (int index = 0; index < actual.size(); index++) {
            assertTrue(prefix + fieldName + " proposal " + index,
                    actual.get(index) instanceof GameState.DiplomacyProposal);
            GameState.DiplomacyProposal proposal = (GameState.DiplomacyProposal) actual.get(index);
            assertEquals(prefix + fieldName + " proposer at " + index,
                    expected.get(index).getInt(rawKeys ? "_leader" : "leader"), proposal.proposerId);
            assertEquals(prefix + fieldName + " option at " + index,
                    expected.get(index).getInt(rawKeys ? "_option" : "option"), proposal.optionIndex);
        }
    }

    private void assertState(GameState game, JsonValue state) throws Exception {
        int turn = state.getInt("turn");
        String prefix = where(turn);
        assertEquals(prefix + "turn", turn, game.getTurn());
        assertEquals(prefix + "canonical RNG", state.getDouble("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        JsonValue humanAction = state.get("humanAction");
        if (!humanAction.isNull() && "diplomacyPending".equals(humanAction.asString())) {
            assertNotNull(prefix + "pending human action", game.getPendingHumanDiplomacyProposal());
        } else {
            assertTrue(prefix + "no unexpected source action", humanAction.isNull());
            assertNull(prefix + "selected action", game.getSelectedAction());
            assertNull(prefix + "no pending human action", game.getPendingHumanDiplomacyProposal());
        }
        assertEquals(prefix + "player count", 5, state.get("players").size);
        for (JsonValue expected : state.get("players")) {
            GameState.PlayerState player = game.getPlayerState(expected.getInt("id"));
            assertPlayer(player, expected, prefix + "player " + player.id + " ");
            assertProposalList(player, "pendingDiplomacyOrder", expected.get("pendingDiplomacy"), false, prefix + "player " + player.id + " ");
        }
    }

    private void assertPlayer(GameState.PlayerState player,
                              JsonValue expected, String prefix) {
        assertEquals(prefix + "alive", expected.getBoolean("alive"), player.alive);
        assertEquals(prefix + "money", expected.getInt("money"), player.moneyMillions);
        double budget = expected.getDouble("turnMoney");
        assertEquals(prefix + "invalid AI budget", Double.isNaN(budget), player.hasInvalidAiTurnMoney());
        if (!Double.isNaN(budget)) {
            assertEquals(prefix + "turnMoney", budget, player.turnMoneyMillions, 0d);
        }
        assertEquals(prefix + "population", expected.getInt("population"), player.populationMillions);
        assertEquals(prefix + "religion", expected.getInt("religion"), player.religion);
        assertEquals(prefix + "score", expected.getLong("score"), player.score);
        JsonValue populations = expected.get("cityPopulations");
        JsonValue biologicalCities = expected.get("cityBiological");
        assertEquals(prefix + "city count", 5, populations.size);
        assertEquals(prefix + "city biological count", 5, biologicalCities.size);
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            CityState city = player.getCity(cityIndex);
            String cityPrefix = prefix + "city " + cityIndex + " ";
            assertEquals(cityPrefix + "population", populations.getInt(cityIndex), city.populationMillions);
            JsonValue biological = biologicalCities.get(cityIndex);
            assertEquals(cityPrefix + "bioloaded", biological.getBoolean("bioloaded"), city.bioloaded);
            assertEquals(cityPrefix + "battle flag", biological.getBoolean("bioloadInBattle"),
                    city.bioloadInBattle);
        }
        assertStocksAndFlags(player, expected, prefix);
        List<String> technologies = new ArrayList<>();
        for (JsonValue technology : expected.get("inventedTech")) technologies.add(technology.asString());
        List<String> actualTechnologies = new ArrayList<>(player.getInventedTechnologies());
        Collections.sort(actualTechnologies);
        assertEquals(prefix + "invented technology", technologies, actualTechnologies);
        assertRelations(player, expected, prefix);
        assertEquals(prefix + "bioloaded", expected.getBoolean("bioloaded"), player.isBioloaded());
        Set<GameState.Action> actions = EnumSet.noneOf(GameState.Action.class);
        for (JsonValue action : expected.get("bioloadedActions")) actions.add(biologicalAction(action.asString()));
        // Player.isBioloaded searches membership; source array display order is not gameplay state.
        assertEquals(prefix + "biological action membership", actions, player.getBioloadedActions());
    }

    private static void assertStocksAndFlags(GameState.PlayerState player,
                                             JsonValue expected, String prefix) {
        Map<String, Integer> stocks = new TreeMap<>();
        Map<String, Integer> antiStocks = new TreeMap<>();
        for (Map.Entry<String, GameState.WeaponState> entry : player.getWeapons().entrySet()) {
            GameState.WeaponState weapon = entry.getValue();
            ("anti".equals(weapon.definition.type) ? antiStocks : stocks).put(entry.getKey(), weapon.inStock);
        }
        assertEquals(prefix + "regular stocks", stockMap(expected.get("stocks")), stocks);
        assertEquals(prefix + "anti stocks", stockMap(expected.get("antiStocks")), antiStocks);
        JsonValue flags = expected.get("weaponsFlags");
        assertEquals(prefix + "weapon flag count", player.getWeapons().size(), flags.size);
        for (JsonValue flag : flags) {
            GameState.WeaponState weapon = player.getWeapons().get(flag.name);
            assertNotNull(prefix + flag.name, weapon);
            assertEquals(prefix + flag.name + " active", flag.getBoolean("active"), weapon.active);
            assertEquals(prefix + flag.name + " bioloaded", flag.getBoolean("bioloaded"), weapon.bioloaded);
        }
    }

    private static Map<String, Integer> stockMap(JsonValue source) {
        Map<String, Integer> result = new TreeMap<>();
        for (JsonValue stock : source) result.put(stock.name, stock.asInt());
        return result;
    }

    private static void assertRelations(GameState.PlayerState player, JsonValue expected, String prefix) {
        assertEquals(prefix + "regard count", 5, expected.get("regards").size);
        assertEquals(prefix + "ceasefire count", 5, expected.get("ceasefires").size);
        assertEquals(prefix + "offer cooldown count", 5, expected.get("diplomacyOfferedLastTurn").size);
        for (int other = 0; other < 5; other++) {
            assertEquals(prefix + "regard toward " + other, expected.get("regards").getDouble(other),
                    player.getRegardTowardExact(other), 0d);
            JsonValue ceasefire = expected.get("ceasefires").get(other);
            assertEquals(prefix + "ceasefire status toward " + other, ceasefire.getBoolean("status"),
                    player.hasCeasefireWith(other));
            assertEquals(prefix + "ceasefire turns toward " + other, ceasefire.getInt("turnsLeft"),
                    player.getCeasefireTurnsWith(other));
            assertEquals(prefix + "offer cooldown toward " + other,
                    expected.get("diplomacyOfferedLastTurn").getInt(other),
                    player.getDiplomacyOfferedLastTurn(other));
        }
    }

    private void assertTerminal(GameState game, JsonValue terminal) {
        String prefix = where(game.getTurn());
        assertEquals(prefix + "terminal human alive", terminal.getBoolean("humanAlive"), game.getHuman().alive);
        assertEquals(prefix + "terminal score", terminal.getLong("humanScore"), game.getHuman().score);
        int survivors = 0;
        for (GameState.PlayerState player : game.getPlayers()) {
            if (!player.human && player.alive) survivors++;
        }
        assertEquals(prefix + "surviving opponents", terminal.getInt("survivingOpponents"), survivors);
        GameState.MatchStatus status = !terminal.getBoolean("humanAlive")
                ? GameState.MatchStatus.DEFEAT : terminal.getInt("survivingOpponents") == 0
                ? GameState.MatchStatus.VICTORY : GameState.MatchStatus.ONGOING;
        assertEquals(prefix + "derived terminal status", status, game.getMatchStatus());
    }

    private static GameState.Action biologicalAction(String name) {
        switch (name) {
            case "resources": return GameState.Action.RESOURCES;
            case "production": return GameState.Action.PRODUCTION;
            case "technology": return GameState.Action.RESEARCH;
            case "religion": return GameState.Action.MASS_MEDIA;
            case "propaganda": return GameState.Action.PROPAGANDA;
            case "diplomacy": return GameState.Action.DIPLOMACY;
            default: throw new AssertionError("Unknown source biological action " + name);
        }
    }

    private String where(int turn) {
        return "seed " + seed + " difficulty " + difficulty + " turn " + turn + " ";
    }
}
