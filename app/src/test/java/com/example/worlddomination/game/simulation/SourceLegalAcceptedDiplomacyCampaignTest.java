package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

/** Seeded SetupMenu UI capture, instant logical turns; not a playback or endgame-screen oracle. */
public final class SourceLegalAcceptedDiplomacyCampaignTest {
    private static final String FIXTURE = "/source-campaign-seed11-accept-diplomacy.json";
    private static final String SHA256 = "d7b64ad3a1c1eb574de7f560e8eab6021a608eb3d635e57e87dae174898f0c38";

    @Test public void acceptedDiplomacyCampaignMatchesEveryCapturedState() throws Exception {
        byte[] bytes;
        try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull("source capture resource", stream);
            bytes = stream.readAllBytes();
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
        int offerCount = 0;
        int previousHumanMoney = capture.get("initial").get("players").get(0).getInt("money");
        for (JsonValue turn : turns) {
            int number = turn.getInt("turn");
            for (JsonValue response : turn.get("offersHandled")) {
                offerCount++;
                GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
                assertNotNull("turn " + number + " expected pending offer", proposal);
                JsonValue expected = response.get("sourceProposal");
                assertEquals("proposer turn " + number, expected.getInt("_leader"), proposal.proposerId);
                assertEquals("option turn " + number, expected.getInt("_option"), proposal.optionIndex);
                assertEquals("RNG before response turn " + number,
                        response.getDouble("rngBefore"), game.createPresentationRandomCopy().nextDouble(), 0d);
                assertEquals("money before response turn " + number, previousHumanMoney, game.getHuman().moneyMillions);
                assertEquals("turnMoney before response turn " + number, response.getInt("turnMoneyBefore"), game.getHuman().turnMoneyMillions);
                JsonValue option = response.get("sourceOption");
                boolean affordable = "offer".equals(option.getString(0))
                        || ("demand".equals(option.getString(0))
                        && game.getHuman().turnMoneyMillions >= option.getInt(2));
                assertEquals("source affordability turn " + number, response.getBoolean("eligibleBySourceRule"), affordable);
                assertEquals("native affordability turn " + number, affordable, game.canAcceptPendingHumanDiplomacy());
                assertEquals("source response turn " + number, affordable ? "btnAccept" : "btnCancel", response.getString("response"));
                assertEquals("source accepted count turn " + number, affordable ? 1 : 0, response.getInt("acceptedCountDelta"));
                assertEquals("source rejected count turn " + number, affordable ? 0 : 1, response.getInt("rejectedCountDelta"));
                assertTrue("respond pending offer turn " + number, game.respondToDiplomacyProposal(affordable));
                assertEquals("money after response turn " + number, previousHumanMoney, game.getHuman().moneyMillions);
                assertEquals("RNG after response turn " + number, response.getDouble("rngAfter"), game.createPresentationRandomCopy().nextDouble(), 0d);
                assertEquals("turnMoney after response turn " + number, response.getInt("turnMoneyAfter"), game.getHuman().turnMoneyMillions);
            }
            assertNull("unexpected pending offer turn " + number, game.getPendingHumanDiplomacyProposal());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue("resources turn " + number, game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue("mass media turn " + number, game.acceptSelectedAction());
            game.confirmTurn();
            assertState(game, turn.get("state"));
            previousHumanMoney = turn.get("state").get("players").get(0).getInt("money");
        }
        assertEquals("captured offer count", 5, offerCount);
        assertEquals(GameState.MatchStatus.ONGOING, game.getMatchStatus());
        assertEquals(60, game.getTurn());
        assertEquals(0L, game.getHuman().score);
        // Source turnResults contains mixed raw Flash records. GameState has no
        // lossless raw-record API, so those records are retained in the fixture
        // for diagnosis rather than being misrepresented as canonical state.
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
            JsonValue regards = expected.get("regard");
            JsonValue ceasefires = expected.get("ceasefires");
            for (int i = 0; i < 5; i++) {
                assertEquals(prefix + "regard " + i, regards.getDouble(i), actual.getRegardTowardExact(i), 0d);
                JsonValue cease = ceasefires.get(i);
                assertEquals(prefix + "ceasefire status " + i, cease.getBoolean("_status"), actual.hasCeasefireWith(i));
                assertEquals(prefix + "ceasefire turns " + i, cease.getInt("_turnsLeft"), actual.getCeasefireTurnsWith(i));
            }

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
