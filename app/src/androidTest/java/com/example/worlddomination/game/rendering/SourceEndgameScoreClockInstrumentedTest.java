package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.MatchOutcomeSequence;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** The legal source victory's isolated score clock, exercised through the real World. */
@RunWith(AndroidJUnit4.class)
public final class SourceEndgameScoreClockInstrumentedTest {
    private static final String CLOCK = "source-endgame-clock/legal-zero.json";
    private static final String CLOCK_SHA = "c48118e598b973c3342bd8c5e4e0dcfc6c08c3bb7b9ee5ba9a39f9bd6ab3c38b";
    private static final String CAMPAIGN = "source-campaign-seed0-victory.json";
    private static final String CAMPAIGN_SHA = "f4f832d6e2950078dd3f2ca111513c3cbbc9c68f9fe60e84bd7f79b18541c6f3";

    @Test public void naturalVictoryMatchesSourceScoreClockAndFrozenRandomStreams() throws Exception {
        JsonValue trace = readJson(CLOCK, CLOCK_SHA);
        JsonValue campaign = readJson(CAMPAIGN, CAMPAIGN_SHA);
        assertEquals(172, trace.get("rows").size);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L);
            setup.setDifficulty(0);
            assertTrue(setup.toggleOpponent(0));
            GameState game = new GameState(setup);
            assertEquals("match-start presentation stream", trace.get("rows").get(0)
                    .getDouble("presentationRngNext"),
                    game.createOutcomePresentationRandomCopy().nextDouble(), 0d);
            JsonValue turns = campaign.get("turns");
            assertEquals(31, turns.size);
            for (JsonValue turn : turns) {
                int number = turn.getInt("turn");
                for (JsonValue control : turn.get("controls")) {
                    if (control.has("declinedPending")) {
                        assertNotNull("offer turn " + number, game.getPendingHumanDiplomacyProposal());
                        assertTrue(game.respondToDiplomacyProposal(false));
                        continue;
                    }
                    String[] request = control.getString("requested").split(" ");
                    switch (request[0]) {
                        case "RESEARCH":
                            game.selectAction(GameState.Action.RESEARCH);
                            assertTrue(game.selectResearchTechnology(request[1]));
                            break;
                        case "PRODUCTION":
                            game.selectAction(GameState.Action.PRODUCTION);
                            assertTrue(game.configureProductionSlider(request[1], Integer.parseInt(request[2])));
                            break;
                        case "ATTACK":
                            game.selectAction(GameState.Action.ATTACK);
                            assertTrue(game.configureAttack(Integer.parseInt(request[2]),
                                    Integer.parseInt(request[3]), request[1]));
                            break;
                        case "RESOURCES": game.selectAction(GameState.Action.RESOURCES); break;
                        default: fail("Unexpected source action " + control.getString("requested"));
                    }
                    assertTrue("accept turn " + number, game.acceptSelectedAction());
                }
                assertNull("unexpected offer turn " + number, game.getPendingHumanDiplomacyProposal());
                game.confirmTurn();
                assertEquals("canonical RNG turn " + number, turn.get("state").getDouble("canonicalRngNext"),
                        game.createPresentationRandomCopy().nextDouble(), 0d);
            }
            assertEquals(GameState.MatchStatus.VICTORY, game.getMatchStatus());
            assertEquals(31, game.getTurn());
            assertEquals(0L, game.getHuman().score);
            assertEquals(trace.get("overrides").getInt("flags"),
                    game.getFinalScoreBreakdown().getFlagBonusCount());
            assertEquals(250_000L, game.getFinalScore());
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class, "cityRenderer").get(app)).reset(game);
            // Do not persist the test match's personal best in app preferences.
            Object store = field(WorldDominationGame.class, "personalBestStore").get(app);
            field(WorldDominationGame.class, "personalBestStore").set(app, null);
            try {
                invoke(app, "synchronizeOutcomeSequence");
                MatchOutcomeSequence sequence = (MatchOutcomeSequence)
                        field(WorldDominationGame.class, "outcomeSequence").get(app);
                ReferenceRandom presentation = (ReferenceRandom)
                        field(MatchOutcomeSequence.class, "presentationRandom").get(sequence);
                for (JsonValue row : trace.get("rows")) {
                    int tick = row.getInt("tick");
                    if (tick > 0) invoke(app, "advanceOutcomeSequence");
                    String context = "tick " + tick + " ";
                    assertEquals(context + "endgame visible", row.getBoolean("visible"), sequence.isEndgameVisible());
                    assertEquals(context + "score started", tick >= 120, sequence.isScoreStarted());
                    assertEquals(context + "presentation RNG", row.getDouble("presentationRngNext"),
                            presentation.copy().nextDouble(), 0d);
                    assertEquals(context + "canonical RNG", row.getDouble("canonicalRngNext"),
                            game.createPresentationRandomCopy().nextDouble(), 0d);
                    if (tick >= 120) {
                        JsonValue scoreText = row.get("scoreText");
                        String expectedText = scoreText.asString();
                        assertEquals(context + "score count", row.getLong("scoreCount"), sequence.getScoreCount());
                        assertEquals(context + "score visibility", !expectedText.isEmpty(), sequence.isScoreVisible());
                        if (!expectedText.isEmpty()) assertEquals(context + "score text", expectedText, sequence.getScoreText());
                        assertEquals(context + "satellite count", row.getInt("satelliteCount"), sequence.getSatellitesRevealed());
                        assertEquals(context + "flag count", row.getInt("flagCount"), sequence.getFlagsRevealed());
                        assertEquals(context + "score callback", row.getBoolean("callbackAttached"), sequence.isAnimating());
                    } else {
                        assertFalse(context + "score visibility", sequence.isScoreVisible());
                    }
                }
            } finally {
                field(WorldDominationGame.class, "personalBestStore").set(app, store);
            }
        });
    }

    private static JsonValue readJson(String name, String expectedSha) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
        }
        byte[] bytes = output.toByteArray();
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            hash.append(String.format(Locale.US, "%02x", b & 0xff));
        assertEquals("fixture " + name, expectedSha, hash.toString());
        return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
    }
    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
}
