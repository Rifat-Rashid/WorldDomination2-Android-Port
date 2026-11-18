package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

/** ART replay of the source logical campaign; no UI or presentation assertions. */
@RunWith(AndroidJUnit4.class)
public final class SourceLegalEasyCampaignInstrumentedTest {
    private static final String FIXTURE = "source-campaign-seed11-easy.json";
    private static final String SHA256 = "dc576662e45428c300f3a693e08f215cfc721e4d7efd553e4d097031534f3eff";

    @Test public void easyFourOpponentCampaignMatchesEveryCapturedState() throws Exception {
        byte[] bytes;
        try (InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(FIXTURE);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            bytes = output.toByteArray();
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hash = new StringBuilder();
        for (byte b : digest) hash.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        assertEquals("source logical capture bytes", SHA256, hash.toString());
        JsonValue capture = new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        GameSetup setup = new GameSetup(11L);
        setup.setDifficulty(0);
        assertTrue(setup.toggleOpponent(5));
        assertTrue(setup.toggleOpponent(9));
        assertTrue(setup.toggleOpponent(13));
        assertTrue(setup.toggleOpponent(18));
        GameState game = new GameState(setup);
        assertState(game, capture.get("initial"));
        JsonValue turns = capture.get("turns");
        assertEquals(60, turns.size);
        for (JsonValue turn : turns) {
            int number = turn.getInt("turn");
            for (JsonValue rejected : turn.get("offersRejected")) {
                GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
                assertNotNull("turn " + number + " expected pending offer", proposal);
                JsonValue expected = rejected.get("sourceProposal");
                assertEquals("proposer turn " + number, expected.getInt("_leader"), proposal.proposerId);
                assertEquals("option turn " + number, expected.getInt("_option"), proposal.optionIndex);
                assertEquals("RNG before rejection turn " + number,
                        rejected.getDouble("rngBefore"), game.createPresentationRandomCopy().nextDouble(), 0d);
                assertTrue("reject pending offer", game.respondToDiplomacyProposal(false));
            }
            assertNull("unexpected pending offer turn " + number, game.getPendingHumanDiplomacyProposal());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue("resources turn " + number, game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue("mass media turn " + number, game.acceptSelectedAction());
            game.confirmTurn();
            assertState(game, turn.get("state"));
        }
        assertEquals(GameState.MatchStatus.ONGOING, game.getMatchStatus());
        assertEquals(60, game.getTurn());
        assertEquals(0L, game.getHuman().score);
        // Source turnResults contains mixed raw Flash records. GameState has no
        // lossless raw-record API, so those records are retained in the fixture
        // for diagnosis rather than being misrepresented as canonical state.
    }

    @Test public void seed29EasyShipRetryCampaignMatchesEveryCapturedState() throws Exception {
        // Android's asset merger expands .gz source fixtures and strips the suffix.
        final String fixture = "source-leader-campaigns.json";
        final String expectedHash = "acb4bcb73336384ce4193f26e97339324fd71f7bf4b493c24c3373f59d2b9ddf";
        byte[] bytes;
        try (InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(fixture);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            bytes = output.toByteArray();
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hash = new StringBuilder();
        for (byte b : digest) hash.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        assertEquals("source leader campaign fixture bytes", expectedHash, hash.toString());

        JsonValue matrix = new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        JsonValue capture = null;
        for (JsonValue candidate : matrix.get("campaigns")) {
            JsonValue setup = candidate.get("setup");
            if (setup.getInt("seed") == 29 && setup.getInt("difficulty") == 0) {
                assertNull("unique seed 29 easy campaign", capture);
                capture = candidate;
            }
        }
        assertNotNull("seed 29 easy campaign", capture);
        JsonValue opponents = capture.get("setup").get("opponents");
        assertEquals(4, opponents.size);
        int[] expectedOpponents = {4, 5, 6, 7};
        GameSetup setup = new GameSetup(29L);
        setup.setDifficulty(0);
        for (int i = 0; i < expectedOpponents.length; i++) {
            assertEquals("source opponent slot " + i, expectedOpponents[i], opponents.getInt(i));
            assertTrue("select opponent " + expectedOpponents[i], setup.toggleOpponent(expectedOpponents[i]));
        }
        GameState game = new GameState(setup);
        assertState(game, capture.get("initial"));
        JsonValue turns = capture.get("turns");
        assertEquals("all legal source turns", 30, turns.size);
        for (JsonValue turn : turns) {
            int number = turn.getInt("turn");
            assertEquals("source turn order", game.getTurn() + 1, number);
            JsonValue queued = turn.get("humanQueuedActions");
            assertEquals("two source actions turn " + number, 2, queued.size);
            assertEquals("first source action turn " + number, "resources", queued.getString(0));
            assertEquals("second source action turn " + number, "religion", queued.getString(1));
            for (JsonValue rejected : turn.get("offersRejected")) {
                GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
                assertNotNull("turn " + number + " expected pending offer", proposal);
                JsonValue expected = rejected.get("sourceProposal");
                assertEquals("proposer turn " + number, expected.getInt("_leader"), proposal.proposerId);
                assertEquals("option turn " + number, expected.getInt("_option"), proposal.optionIndex);
                assertEquals("RNG before rejection turn " + number,
                        rejected.getDouble("rngBefore"), game.createPresentationRandomCopy().nextDouble(), 0d);
                assertTrue("reject pending offer turn " + number, game.respondToDiplomacyProposal(false));
            }
            assertNull("unexpected pending offer turn " + number, game.getPendingHumanDiplomacyProposal());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue("resources turn " + number, game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue("mass media turn " + number, game.acceptSelectedAction());
            game.confirmTurn();
            assertState(game, turn.get("state"));
        }
        assertEquals("ship retry turn included", 30, game.getTurn());
        assertEquals(GameState.MatchStatus.ONGOING, game.getMatchStatus());
        assertEquals("source human score", capture.get("terminal").getLong("humanScore"), game.getHuman().score);
    }

    private static void assertState(GameState game, JsonValue state) {
        int turn = state.getInt("turn");
        String where = "turn " + turn + " ";
        assertEquals(where + "turn", turn, game.getTurn());
        assertEquals(where + "canonical RNG", state.getDouble("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        String expectedAction = state.get("humanAction").isNull() ? null : state.getString("humanAction");
        if ("diplomacyPending".equals(expectedAction)) {
            assertNotNull(where + "pending human action", game.getPendingHumanDiplomacyProposal());
        } else {
            assertNull(where + "unexpected source human action " + expectedAction, expectedAction);
            assertNull(where + "selected action", game.getSelectedAction());
        }
        JsonValue players = state.get("players");
        assertEquals(where + "player count", 5, players.size);
        for (JsonValue expected : players) {
            int id = expected.getInt("id");
            String prefix = where + "player " + id + " ";
            GameState.PlayerState actual = game.getPlayerState(id);
            assertEquals(prefix + "alive", expected.getBoolean("alive"), actual.alive);
            assertEquals(prefix + "money", expected.getInt("money"), actual.moneyMillions);
            double expectedBudget = expected.getDouble("turnMoney");
            if (Double.isNaN(expectedBudget)) {
                assertTrue(prefix + "source NaN spendable budget", actual.hasInvalidAiTurnMoney());
            } else {
                assertFalse(prefix + "finite spendable budget", actual.hasInvalidAiTurnMoney());
                assertEquals(prefix + "turnMoney", (int) expectedBudget, actual.turnMoneyMillions);
            }
            assertEquals(prefix + "population", expected.getInt("population"), actual.populationMillions);
            assertEquals(prefix + "religion", expected.getInt("religion"), actual.religion);
            assertEquals(prefix + "score", expected.getLong("score"), actual.score);
            JsonValue cities = expected.get("cityPopulations");
            assertEquals(prefix + "city count", 5, cities.size);
            for (int i = 0; i < 5; i++)
                assertEquals(prefix + "city " + i, cities.getInt(i), actual.getCity(i).populationMillions);
            Map<String, Integer> stocks = new TreeMap<>();
            for (JsonValue stock : expected.get("stocks")) stocks.put(stock.name, stock.asInt());
            Map<String, Integer> nativeStocks = new TreeMap<>();
            actual.getWeapons().forEach((name, weapon) -> {
                if (!"anti".equals(weapon.definition.type)) nativeStocks.put(name, weapon.inStock);
            });
            assertEquals(prefix + "non-anti stocks", stocks, nativeStocks);
            for (JsonValue anti : expected.get("antiStocks"))
                assertEquals(prefix + anti.name, anti.asInt(), actual.getWeaponStockByName(anti.name));
            List<String> technologies = new ArrayList<>();
            for (JsonValue tech : expected.get("inventedTech")) technologies.add(tech.asString());
            List<String> nativeTechnologies = new ArrayList<>(actual.getInventedTechnologies());
            Collections.sort(nativeTechnologies);
            assertEquals(prefix + "invented technology", technologies, nativeTechnologies);
        }
    }
}
