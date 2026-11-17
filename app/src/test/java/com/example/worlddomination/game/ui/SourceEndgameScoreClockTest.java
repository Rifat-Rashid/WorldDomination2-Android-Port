package com.example.worlddomination.game.ui;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.FinalScoreBreakdown;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.Assert.*;

/** Source phone Lifecycle host ticks; overrides in synthetic captures are explicit. */
public final class SourceEndgameScoreClockTest {
    @Test public void legalZeroScoreVictoryKeepsItsSourceCounterClock() throws Exception {
        replay("legal-zero", "c48118e598b973c3342bd8c5e4e0dcfc6c08c3bb7b9ee5ba9a39f9bd6ab3c38b");
    }

    @Test public void noBonusesRetainTheLastSourceCallbackTick() throws Exception {
        replay("zero-no-flags", "375a2dc0241d929a240fd78a9dd80df6b36f8a90d2086aeb79401fb55e60e059");
    }

    @Test public void fractionalWinCounterMatchesEverySourceDraw() throws Exception {
        replay("fractional-win", "66cf360291218fcd06c27daa229395592ea569f3b965f4c04ef8851efda78d3f");
    }

    @Test public void fractionalDefeatCounterMatchesEverySourceDraw() throws Exception {
        replay("fractional-defeat", "dab93b554c5902cc842d809e34508d30be859f17af388f79d6f20dc08b36f972");
    }

    @Test public void repeatedBonusesMatchSourceLabelsBlinkingAndFinalScore() throws Exception {
        replay("bonus-win", "58cf51211941c93b1e97c3df461c4cf768e8a33617c5d1f522dd556924358de9");
    }

    private static void replay(String name, String sha256) throws Exception {
        byte[] bytes;
        try (InputStream input = SourceEndgameScoreClockTest.class.getResourceAsStream(
                "/source-endgame-clock/" + name + ".json")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            hash.append(String.format(Locale.US, "%02x", value & 255));
        assertEquals(sha256, hash.toString());
        JsonValue capture = new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        JsonValue options = capture.get("overrides");
        assertFalse(options.getBoolean("cheat"));
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(0);
        assertTrue(setup.toggleOpponent(0));
        GameState game = new GameState(setup);
        ReferenceRandom snapshot = game.createOutcomePresentationRandomCopy();
        double canonical = game.createPresentationRandomCopy().nextDouble();
        GameState.MatchStatus outcome = options.getBoolean("defeat")
                ? GameState.MatchStatus.DEFEAT : GameState.MatchStatus.VICTORY;
        FinalScoreBreakdown breakdown = FinalScoreBreakdown.calculate(
                options.getLong("score"), options.getInt("satellites"), options.getInt("flags"));
        MatchOutcomeSequence sequence = new MatchOutcomeSequence();
        sequence.begin(outcome, breakdown, snapshot);
        Field randomField = MatchOutcomeSequence.class.getDeclaredField("presentationRandom");
        randomField.setAccessible(true);
        ReferenceRandom privateRandom = (ReferenceRandom) randomField.get(sequence);
        double start = snapshot.copy().nextDouble();
        assertEquals(capture.get("rows").get(0).getDouble("presentationRngNext"), start, 0d);
        for (JsonValue row : capture.get("rows")) {
            int tick = row.getInt("tick");
            if (tick > 0) sequence.tick();
            String at = name + " tick " + tick + " ";
            assertEquals(at + "visibility", row.getBoolean("visible"), sequence.isEndgameVisible());
            assertEquals(at + "private RNG", row.getDouble("presentationRngNext"),
                    privateRandom.copy().nextDouble(), 0d);
            assertEquals(at + "input snapshot untouched", start, snapshot.copy().nextDouble(), 0d);
            assertEquals(at + "canonical RNG untouched", canonical,
                    game.createPresentationRandomCopy().nextDouble(), 0d);
            boolean scoreStarted = !row.get("scoreCount").isNull();
            assertEquals(at + "score frame", scoreStarted, sequence.isScoreStarted());
            if (!scoreStarted) continue;
            assertEquals(at + "score", row.getLong("scoreCount"), sequence.getScoreCount());
            String text = row.get("scoreText").asString();
            assertEquals(at + "score visibility", !text.isEmpty(), sequence.isScoreVisible());
            assertEquals(at + "score text", text,
                    sequence.isScoreVisible() ? sequence.getScoreText() : "");
            assertEquals(at + "satellites", row.getInt("satelliteCount"), sequence.getSatellitesRevealed());
            assertEquals(at + "flags", row.getInt("flagCount"), sequence.getFlagsRevealed());
            assertEquals(at + "callback lifetime", row.getBoolean("callbackAttached"), sequence.isAnimating());
            assertEquals(at + "bonus labels", normalized(row.getString("bonusText")),
                    normalized(sequence.getSatelliteBonusText() + " " + sequence.getFlagBonusText()));
            assertEquals(at + "outcome message", normalized(row.getString("bodyText")),
                    normalized(MatchOutcomePresentation.message(outcome)));
        }
        assertTrue(sequence.isComplete());
        assertEquals(breakdown.getFinalScore(), sequence.getScoreCount());
    }

    private static String normalized(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
