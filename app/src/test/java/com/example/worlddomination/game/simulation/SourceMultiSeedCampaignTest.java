package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Actual source SetupMenu campaigns; canonical state only, not battle or endgame playback. */
@RunWith(Parameterized.class)
public final class SourceMultiSeedCampaignTest {
    private static final String FIXTURE = "/source-campaign-matrix.json.gz";
    private static final String SHA256 =
            "791caa57f36ac88a25b2afadd9deefa29c5041233bed6d088bb4a12a943dc7b1";

    private final long seed;
    private final int difficulty;
    private final JsonValue capture;

    public SourceMultiSeedCampaignTest(long seed, int difficulty, JsonValue capture) {
        this.seed = seed;
        this.difficulty = difficulty;
        this.capture = capture;
    }

    @Parameterized.Parameters(name = "seed={0}, difficulty={1}")
    public static Collection<Object[]> campaigns() throws Exception {
        byte[] compressed;
        try (InputStream stream = SourceMultiSeedCampaignTest.class.getResourceAsStream(FIXTURE)) {
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
        assertEquals("source-campaign-matrix-v1", matrix.getString("schema"));
        List<Object[]> cases = new ArrayList<>();
        int totalTurns = 0;
        for (JsonValue campaign : matrix.get("campaigns")) {
            JsonValue setup = campaign.get("setup");
            cases.add(new Object[]{setup.getLong("seed"), setup.getInt("difficulty"), campaign});
            totalTurns += campaign.get("turns").size;
        }
        assertEquals("all captured seed/difficulty pairs", 12, cases.size());
        assertEquals("retain every source turn, including later stock collisions", 289, totalTurns);
        return cases;
    }

    @Test
    public void canonicalCampaignMatchesIndependentSourceAtEveryTurn() {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(difficulty);
        for (JsonValue opponent : capture.get("setup").get("opponents")) {
            assertTrue(where(0) + "select opponent " + opponent.asInt(),
                    setup.toggleOpponent(opponent.asInt()));
        }
        GameState game = new GameState(setup);
        assertState(game, capture.get("initial"));
        for (JsonValue turn : capture.get("turns")) {
            int number = turn.getInt("turn");
            String prefix = where(number);
            for (JsonValue rejected : turn.get("offersRejected")) {
                GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
                assertNotNull(prefix + "source pending offer", proposal);
                JsonValue sourceProposal = rejected.get("sourceProposal");
                assertEquals(prefix + "pending offer proposer", sourceProposal.getInt("_leader"),
                        proposal.proposerId);
                assertEquals(prefix + "pending offer option", sourceProposal.getInt("_option"),
                        proposal.optionIndex);
                assertEquals(prefix + "RNG before source rejection", rejected.getDouble("rngBefore"),
                        game.createPresentationRandomCopy().nextDouble(), 0d);
                assertEquals(prefix + "source response", "btnCancel", rejected.getString("response"));
                assertTrue(prefix + "reject pending offer", game.respondToDiplomacyProposal(false));
            }
            assertNull(prefix + "no extra pending offer", game.getPendingHumanDiplomacyProposal());
            JsonValue actions = turn.get("humanQueuedActions");
            assertEquals(prefix + "source action count", 2, actions.size);
            assertEquals(prefix + "first source action", "resources", actions.getString(0));
            assertEquals(prefix + "second source action", "religion", actions.getString(1));
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(prefix + "accept resources", game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue(prefix + "accept mass media", game.acceptSelectedAction());
            game.confirmTurn();
            assertState(game, turn.get("state"));
        }
        assertTerminal(game, capture.get("terminal"));
    }

    private void assertState(GameState game, JsonValue state) {
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
        }
        assertEquals(prefix + "player count", 5, state.get("players").size);
        for (JsonValue expected : state.get("players")) {
            GameState.PlayerState player = game.getPlayerState(expected.getInt("id"));
            assertPlayer(player, expected, prefix + "player " + player.id + " ");
        }
    }

    private void assertPlayer(GameState.PlayerState player,
                              JsonValue expected, String prefix) {
        assertEquals(prefix + "alive", expected.getBoolean("alive"), player.alive);
        assertEquals(prefix + "money", expected.getInt("money"), player.moneyMillions);
        double budget = expected.getDouble("turnMoney");
        assertEquals(prefix + "invalid AI budget", Double.isNaN(budget), player.hasInvalidAiTurnMoney());
        if (!Double.isNaN(budget)) {
            assertEquals(prefix + "turnMoney", (int) budget, player.turnMoneyMillions);
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
