package com.example.worlddomination.game.ui;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SourceBudgetTextTest {
    @Test
    public void matchesActualSourceGettersAtPositiveZeroAndNegativeFunds() throws IOException {
        JsonValue fixture = sourceFixture();
        for (JsonValue sample : fixture.get("samples")) {
            int funds = sample.getInt("funds");
            assertEquals(sample.getString("display_turnMoney"),
                    SourceBudgetText.displayTurnMoney(funds));
            assertEquals(sample.getString("display_textFunds"),
                    SourceBudgetText.displayTextFunds(funds));
        }
        assertEquals("resources", fixture.get("zero_dialog").getString("action"));
        assertEquals(fixture.get("zero_dialog").getString("field_text"),
                SourceBudgetText.displayTextFunds(0));
    }

    @Test
    public void resourcesUsesSourceZeroBudgetWithoutChangingPopulationOrIncome() {
        GameState state = new GameState(1, 0L);
        ResourceMenuText text = new ResourceMenuText();
        state.getHuman().turnMoneyMillions = 0;
        text.update(state);
        assertEquals("Budget: $ 0", text.getBudget());
        assertEquals(0, state.getHuman().turnMoneyMillions);
        assertEquals("450.000.000", text.getPopulation());
        assertEquals("41.000.000", text.getIncome());

        state.getHuman().turnMoneyMillions = -10;
        text.update(state);
        assertEquals("Budget: $ 0", text.getBudget());
        assertEquals(-10, state.getHuman().turnMoneyMillions);
        assertEquals("450.000.000", text.getPopulation());
        assertEquals("41.000.000", text.getIncome());
    }

    private static JsonValue sourceFixture() throws IOException {
        try (InputStream input = SourceBudgetTextTest.class.getResourceAsStream(
                "/source-budget-display.json")) {
            if (input == null) throw new IOException("Missing source budget fixture");
            return new JsonReader().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
