package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.ui.BattlefieldFlagState;
import org.junit.Test;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Actual source callback captures with synthetic prior attribution/populations. */
public final class SourceDefeatedFlagCreditTest {
    @Test public void freshSelectedNationsHaveNoAttackerCredit() throws Exception {
        GameState game = match();
        assertState(game, fixture("prior_ai").get("initial"));
        for (int id = 0; id < 3; id++)
            assertEquals(id, BattlefieldFlagState.ownerPlayerId(game, id));
    }

    @Test public void killingConquerorPreservesOriginalVictimsFlag() throws Exception {
        replay("prior_ai");
    }

    @Test public void directHumanCreditSurvivesOtherEliminations() throws Exception {
        replay("prior_human");
    }

    @Test public void samePhaseEliminationsKeepTheirOriginalCredits() throws Exception {
        replay("same_phase");
    }

    @Test public void unsetAttributionAwardsNoFlags() throws Exception {
        replay("unattributed");
    }

    private static void replay(String name) throws Exception {
        JsonValue trace = fixture(name);
        GameState game = match();
        assertState(game, trace.get("initial"));
        for (JsonValue player : trace.get("before").get("players")) {
            if (!player.getBoolean("camelIsUndefined"))
                game.getPlayerState(player.getInt("id")).lastAttackedByPlayerId = player.getInt("camel");
        }
        assertState(game, trace.get("before"));
        if ("same_phase".equals(name)) {
            for (int id = 1; id <= 2; id++) {
                for (CityState city : game.getPlayerState(id).getCities()) city.populationMillions = 0;
                game.getPlayerState(id).populationMillions = 0;
            }
            invoke(game, "refreshMatchStatus");
        } else {
            eliminate(game, 2);
            assertState(game, trace.get("afterFirst"));
            eliminate(game, 1);
        }
        assertState(game, trace.get("after"));
        invoke(game, "captureFinalScore");
        assertEquals(trace.getInt("sourceViewFlagBonus"), game.getFinalScoreBreakdown().getFlagBonusCount());
        for (int id = 1; id <= 2; id++) {
            int owner = trace.get("after").get("players").get(id).getBoolean("camelIsUndefined")
                    ? -1 : trace.get("after").get("players").get(id).getInt("camel");
            assertEquals(owner, BattlefieldFlagState.ownerPlayerId(game, id));
            assertEquals(owner < 0 ? 0 : game.getPlayerState(owner).getPlayerColor(),
                    BattlefieldFlagState.color(game, id));
        }
    }

    private static void assertState(GameState game, JsonValue state) {
        assertEquals(state.getDouble("rngNext"), game.createPresentationRandomCopy().nextDouble(), 0d);
        for (JsonValue expected : state.get("players")) {
            GameState.PlayerState actual = game.getPlayerState(expected.getInt("id"));
            assertEquals(expected.getBoolean("alive"), actual.alive);
            assertEquals(expected.getBoolean("camelIsUndefined") ? -1 : expected.getInt("camel"),
                    actual.getLastAttackedByPlayerId());
        }
    }

    private static JsonValue fixture(String name) throws Exception {
        try (InputStream stream = SourceDefeatedFlagCreditTest.class.getResourceAsStream("/parity/defeated-flag-credit.json")) {
            assertNotNull(stream);
            byte[] bytes = stream.readAllBytes();
            StringBuilder digest = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) digest.append(String.format("%02x", b & 255));
            assertEquals("208b5ee9e4a583197edb6b6a55f27ae5e567b3f418e9f6337597448b13b82b24", digest.toString());
            for (JsonValue result : new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8)).get("results"))
                if (name.equals(result.getString("case"))) return result;
            throw new AssertionError("Missing source case " + name);
        }
    }

    private static GameState match() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(0);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }

    private static void eliminate(GameState game, int player) throws Exception {
        Method method = GameState.class.getDeclaredMethod("eliminatePlayer", GameState.PlayerState.class);
        method.setAccessible(true);
        method.invoke(game, game.getPlayerState(player));
    }

    private static void invoke(GameState game, String name) throws Exception {
        Method method = GameState.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(game);
    }
}
