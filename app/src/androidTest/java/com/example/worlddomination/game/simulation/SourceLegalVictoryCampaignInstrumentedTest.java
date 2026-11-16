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

/** Seeded SetupMenu UI capture, instant logical turns; not a playback or endgame-screen oracle. */
@RunWith(AndroidJUnit4.class)
public final class SourceLegalVictoryCampaignInstrumentedTest {
    private static final String FIXTURE = "source-campaign-seed0-victory.json";
    private static final String SHA256 = "f4f832d6e2950078dd3f2ca111513c3cbbc9c68f9fe60e84bd7f79b18541c6f3";

    @Test public void legalCombatCampaignReachesSourceVictory() throws Exception {
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
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(0);
        assertTrue(setup.toggleOpponent(0));
        GameState game = new GameState(setup);
        assertState(game, capture.get("initial"));
        JsonValue turns = capture.get("turns");
        assertEquals(31, turns.size);
        for (JsonValue turn : turns) {
            int number = turn.getInt("turn");
            for (JsonValue control : turn.get("controls")) {
                if (control.has("declinedPending")) {
                    assertNotNull("turn " + number + " pending offer", game.getPendingHumanDiplomacyProposal());
                    assertTrue("reject offer", game.respondToDiplomacyProposal(false));
                    continue;
                }
                String[] request = control.getString("requested").split(" ");
                switch (request[0]) {
                    case "RESEARCH":
                        game.selectAction(GameState.Action.RESEARCH);
                        assertTrue("select research turn " + number, game.selectResearchTechnology(request[1]));
                        break;
                    case "PRODUCTION":
                        game.selectAction(GameState.Action.PRODUCTION);
                        assertTrue("slider turn " + number, game.configureProductionSlider(request[1], Integer.parseInt(request[2])));
                        break;
                    case "ATTACK":
                        game.selectAction(GameState.Action.ATTACK);
                        assertTrue("attack turn " + number, game.configureAttack(Integer.parseInt(request[2]), Integer.parseInt(request[3]), request[1]));
                        break;
                    case "RESOURCES": game.selectAction(GameState.Action.RESOURCES); break;
                    default: fail("unexpected control " + control.getString("requested"));
                }
                assertTrue("accept turn " + number + " " + control.getString("requested"), game.acceptSelectedAction());
            }
            assertNull("unexpected offer turn " + number, game.getPendingHumanDiplomacyProposal());
            game.confirmTurn();
            assertState(game, turn.get("state"));
        }
        assertEquals(GameState.MatchStatus.VICTORY, game.getMatchStatus());
        assertEquals(31, game.getTurn());
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
        assertEquals(where + "source player count", 2, players.size);
        for (int i = 2; i < 5; i++) assertFalse(where + "unused native slot " + i, game.getPlayerState(i).alive);
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
