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
public final class SourceLegalHardCampaignTest {
    private static final String FIXTURE = "/source-campaign-seed7-hard.json";
    private static final String SHA256 = "579f120e730e52cf4f906d19768340827b7672ccc1da4dfb679bd8534e88abad";

    @Test public void hardFourOpponentCampaignPreservesRejectedAttackSlotState() throws Exception {
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
        GameSetup setup = new GameSetup(7L);
        setup.setDifficulty(2);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        assertTrue(setup.toggleOpponent(2));
        assertTrue(setup.toggleOpponent(4));
        GameState game = new GameState(setup);
        assertState(game, capture.get("initial"));
        JsonValue turns = capture.get("turns");
        assertEquals(21, turns.size);
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
        assertEquals(GameState.MatchStatus.DEFEAT, game.getMatchStatus());
        assertEquals(21, game.getTurn());
        assertEquals(346948L, game.getHuman().score);
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
