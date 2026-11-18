package com.example.worlddomination.game.ui;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MassMediaTextTest {
    @Test public void presenterPopulationMatchesCapturedRoundingBoundaries() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/source-mass-media-population.json")) {
            assertNotNull(input);
            JsonValue fixture = new JsonReader().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            JsonValue samples = fixture.get("samples");
            assertEquals(11, samples.size);
            for (JsonValue sample = samples.child; sample != null; sample = sample.next) {
                assertEquals("Captured derived population equals the requested input",
                        sample.getInt("population"), sample.getInt("observed_population"));
                String expected = sample.getString("display_population");
                assertNotEquals("undefined", expected);
                assertEquals("Source presenter population " + sample.getInt("population"),
                        expected, MassMediaText.population(sample.getInt("population")));
            }
        }
    }

    @Test public void massMediaFormatterDoesNotChangeResourcePopulationOrCanonicalState() {
        int[] populations = {500, 450, 400};
        String[] display = {"50.000.000", "45.000.000", "40.000.000"};
        ResourceMenuText resources = new ResourceMenuText();
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameState game = new GameState(difficulty, 0L);
            assertEquals(display[difficulty], MassMediaText.population(game.getHuman().populationMillions));
            resources.update(game);
            assertEquals(populations[difficulty] + ".000.000", resources.getPopulation());
            assertEquals(populations[difficulty], game.getHuman().populationMillions);
        }
    }
}
