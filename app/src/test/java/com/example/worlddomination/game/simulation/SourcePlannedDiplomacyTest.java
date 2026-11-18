package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Source canonical prefixes: human acceptance precedes already-planned AI proposal results. */
public final class SourcePlannedDiplomacyTest {
    private static final String FIXTURE = "/source-planned-diplomacy.json.gz";
    private static final String SHA256 = "959bb9383ebfeadf4240d419e36536470c7cf74bc6da27b97d3eabfcd1b440b8";

    @Test
    public void seedZeroKeepsBothPlannedDemandsAfterAcceptingTheFirstProposersOffer() throws Exception {
        GameState game = replay(0L);
        assertEquals(4, game.getTurn());
        assertTrue("Accepted offer establishes a ceasefire before later proposal results",
                game.getHuman().hasCeasefireWith(3));
        assertEquals("Already-planned demand remains the first pending proposal", 3,
                game.getPendingHumanDiplomacyProposal().proposerId);
        assertEquals(5, game.getPendingHumanDiplomacyProposal().optionIndex);
        assertEquals("Other AI's later demand is also retained", 5,
                game.getHuman().getPendingDiplomacyFrom(4).optionIndex);
    }

    @Test
    public void seedTwoKeepsAPlannedDemandAfterThreeHumanAcceptances() throws Exception {
        GameState game = replay(2L);
        assertEquals(2, game.getTurn());
        for (int proposer = 1; proposer <= 3; proposer++) {
            assertTrue("Accepted answer establishes ceasefire with " + proposer,
                    game.getHuman().hasCeasefireWith(proposer));
        }
        assertEquals("AI 1's previously selected new demand survives acceptance", 1,
                game.getPendingHumanDiplomacyProposal().proposerId);
        assertEquals(4, game.getPendingHumanDiplomacyProposal().optionIndex);
    }

    @Test
    public void interactiveProposalStillRejectsAnExistingCeasefire() {
        GameState game = new GameState(1, 31L, false);
        assertTrue(game.submitDiplomacyProposal(1, 0, 0));
        assertTrue(game.respondToDiplomacyProposal(true));
        queueTurn(game, "interactive guard ");
        assertTrue(game.getHuman().hasCeasefireWith(1));
        assertEquals("Human proposer has no cooldown to confound the ceasefire guard", 0,
                game.getHuman().getDiplomacyOfferedLastTurn(1));
        assertNull("No pending proposal to confound the guard",
                game.getPlayerState(1).getPendingDiplomacyFrom(0));
        int money = game.getHuman().moneyMillions;
        double random = game.createPresentationRandomCopy().nextDouble();
        assertFalse("Interactive requests must honor a ceasefire already in force",
                game.submitDiplomacyProposal(0, 1, 3));
        assertNull(game.getPlayerState(1).getPendingDiplomacyFrom(0));
        assertEquals(money, game.getHuman().moneyMillions);
        assertEquals(random, game.createPresentationRandomCopy().nextDouble(), 0d);
    }

    private static GameState replay(long seed) throws Exception {
        JsonValue capture = loadCase(seed);
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(capture.get("setup").getInt("difficulty"));
        for (JsonValue opponent : capture.get("setup").get("opponents")) {
            assertTrue(setup.toggleOpponent(opponent.asInt()));
        }
        GameState game = new GameState(setup);
        assertState(game, capture.get("initial"), prefix(seed, 0));
        for (JsonValue turn : capture.get("turns")) {
            String where = prefix(seed, turn.getInt("turn"));
            for (JsonValue answer : turn.get("answers")) {
                assertResponse(game, answer.get("before"), where + "before answer ");
                GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
                assertNotNull(where + "incoming proposal", proposal);
                assertEquals(where + "incoming proposer", answer.get("proposal").getInt("_leader"),
                        proposal.proposerId);
                assertEquals(where + "incoming option", answer.get("proposal").getInt("_option"),
                        proposal.optionIndex);
                assertTrue(where + "source answer is affordable", game.canAcceptPendingHumanDiplomacy());
                assertTrue(where + "accept source proposal", game.respondToDiplomacyProposal(true));
                assertResponse(game, answer.get("after"), where + "after answer ");
            }
            assertNull(where + "all source incoming proposals answered", game.getPendingHumanDiplomacyProposal());
            queueTurn(game, where);
            assertState(game, turn.get("state"), where);
        }
        assertEquals(capture.getInt("observedThroughTurn"), game.getTurn());
        return game;
    }

    private static void queueTurn(GameState game, String where) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(where + "accept Resources", game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(where + "accept Mass Media", game.acceptSelectedAction());
        game.confirmTurn();
    }

    private static void assertResponse(GameState game, JsonValue expected, String where) {
        assertEquals(where + "canonical RNG", expected.getDouble("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        assertEquals(where + "wallet", expected.getInt("humanMoney"), game.getHuman().moneyMillions);
        assertEquals(where + "reserved spending budget", expected.getInt("humanTurnMoney"),
                game.getHuman().turnMoneyMillions);
        assertPending(game, game.getHuman(), expected.get("pending"), true, where);
        for (int id = 0; id < 5; id++) {
            GameState.PlayerState player = game.getPlayerState(id);
            assertEquals(where + "player " + id + " wallet", expected.get("playerMoney").getInt(id),
                    player.moneyMillions);
            assertBudget(player, expected.get("playerTurnMoney").getDouble(id), where + "player " + id + " ");
            for (int other = 0; other < 5; other++) {
                assertEquals(where + "player " + id + " regard " + other,
                        expected.get("regards").get(id).getDouble(other), player.getRegardTowardExact(other), 0d);
                assertCeasefire(player, other, expected.get("ceasefires").get(id).get(other), where);
            }
        }
    }

    private static void assertState(GameState game, JsonValue state, String where) {
        assertEquals(where + "turn", state.getInt("turn"), game.getTurn());
        assertEquals(where + "canonical RNG", state.getDouble("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        assertEquals(where + "all five players", 5, state.get("players").size);
        for (JsonValue expected : state.get("players")) {
            GameState.PlayerState player = game.getPlayerState(expected.getInt("id"));
            String who = where + "player " + player.id + " ";
            assertPending(game, player, expected.get("pendingDiplomacy"), false, who);
            assertEquals(who + "alive", expected.getBoolean("alive"), player.alive);
            assertEquals(who + "wallet", expected.getInt("money"), player.moneyMillions);
            assertBudget(player, expected.getDouble("turnMoney"), who);
            assertEquals(who + "population", expected.getInt("population"), player.populationMillions);
            assertEquals(who + "religion", expected.getInt("religion"), player.religion);
            assertEquals(who + "score", expected.getLong("score"), player.score);
            for (int other = 0; other < 5; other++) {
                assertEquals(who + "regard " + other, expected.get("regards").getDouble(other),
                        player.getRegardTowardExact(other), 0d);
                assertCeasefire(player, other, expected.get("ceasefires").get(other), who);
                assertEquals(who + "offered cooldown " + other,
                        expected.get("diplomacyOfferedLastTurn").getInt(other), player.getDiplomacyOfferedLastTurn(other));
            }
        }
        if (state.get("humanAction").isNull()) {
            assertNull(where + "no selected action", game.getSelectedAction());
            assertNull(where + "no pending human action", game.getPendingHumanDiplomacyProposal());
        } else {
            assertEquals(where + "source human action", "diplomacyPending", state.getString("humanAction"));
            assertNotNull(where + "pending human action", game.getPendingHumanDiplomacyProposal());
        }
    }

    private static void assertPending(GameState game, GameState.PlayerState target,
                                      JsonValue expected, boolean rawKeys, String where) {
        int count = 0;
        for (int proposer = 0; proposer < 5; proposer++) {
            GameState.DiplomacyProposal actual = target.getPendingDiplomacyFrom(proposer);
            JsonValue source = null;
            for (JsonValue candidate : expected) {
                if (candidate.getInt(rawKeys ? "_leader" : "leader") == proposer) source = candidate;
            }
            if (source == null) assertNull(where + "no proposal from " + proposer, actual);
            else {
                assertNotNull(where + "retained proposal from " + proposer, actual);
                assertEquals(where + "option from " + proposer, source.getInt(rawKeys ? "_option" : "option"),
                        actual.optionIndex);
            }
            if (actual != null) count++;
        }
        assertEquals(where + "pending count", expected.size, count);
        if (target.human && expected.size > 0) {
            GameState.DiplomacyProposal first = game.getPendingHumanDiplomacyProposal();
            assertNotNull(where + "first human pending proposal", first);
            assertEquals(where + "human pending display order",
                    expected.get(0).getInt(rawKeys ? "_leader" : "leader"), first.proposerId);
        }
    }

    private static void assertBudget(GameState.PlayerState player, double expected, String where) {
        assertEquals(where + "invalid AI budget", Double.isNaN(expected), player.hasInvalidAiTurnMoney());
        if (!Double.isNaN(expected)) assertEquals(where + "budget", (int) expected, player.turnMoneyMillions);
    }

    private static void assertCeasefire(GameState.PlayerState player, int other, JsonValue expected, String where) {
        assertEquals(where + "ceasefire status " + player.id + "->" + other,
                expected.getBoolean("status"), player.hasCeasefireWith(other));
        assertEquals(where + "ceasefire turns " + player.id + "->" + other,
                expected.getInt("turnsLeft"), player.getCeasefireTurnsWith(other));
    }

    private static JsonValue loadCase(long seed) throws Exception {
        byte[] bytes;
        try (InputStream stream = SourcePlannedDiplomacyTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull("Independent source fixture", stream);
            bytes = stream.readAllBytes();
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        assertEquals("Exact source gzip bytes", SHA256, hash.toString());
        try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            JsonValue fixture = new JsonReader().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("source-planned-diplomacy-v1", fixture.getString("schema"));
            assertEquals(4, fixture.get("originalCaptureSha256").size);
            assertEquals(2, fixture.get("cases").size);
            for (JsonValue capture : fixture.get("cases")) {
                if (capture.get("setup").getLong("seed") == seed) return capture;
            }
        }
        throw new AssertionError("Missing source seed " + seed);
    }

    private static String prefix(long seed, int turn) { return "seed " + seed + " Hard turn " + turn + " "; }
}
