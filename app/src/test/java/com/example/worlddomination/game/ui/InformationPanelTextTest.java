package com.example.worlddomination.game.ui;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/** Values observed from real source draw_stats and update_statistics callbacks. */
public final class InformationPanelTextTest {
    @Test public void fundsMatchPhoneSourceIncludingZeroNegativeAndIntLimits() throws Exception {
        JsonValue rows=fixture().get("fundsLabels");
        assertEquals(8,rows.size);
        for(JsonValue row:rows) assertEquals(row.get("label").getString("text"),
                InformationPanelText.funds(row.getInt("inputTurnMoneyMillions")));
    }

    @Test public void phoneGroupingDoesNotDependOnAndroidLocale() {
        Locale original=Locale.getDefault();
        try {
            for(Locale locale:new Locale[]{Locale.GERMANY,Locale.FRANCE,new Locale("ar")}) {
                Locale.setDefault(locale);
                assertEquals("$ 1,000,000,000",InformationPanelText.funds(1000));
            }
        } finally { Locale.setDefault(original); }
    }

    @Test public void populationRetainsSourceDivideFirstFraction() throws Exception {
        JsonValue rows=fixture().get("populationMasks");
        assertEquals(5,rows.size);
        for(JsonValue row:rows) assertEquals(row.getDouble("populationMaskXScale"),
                SourceStatisticBar.populationPercentage(row.getLong("requestedHumanPopulation"),
                        row.getLong("requestedActivePopulation")),0d);
        assertEquals(0d,SourceStatisticBar.populationPercentage(0,0),0d);
        assertEquals(50d,SourceStatisticBar.populationPercentage(2_000_000_000L,
                4_000_000_000L),0d);
    }

    @Test public void fractionalMaskChangesTheActualNarrowScreenColumnBoundary() throws Exception {
        double mask=fixture().get("populationMasks").get(0).getDouble("populationMaskXScale");
        // Measured 800x600 World panel: source mask fills 47 columns, rounded 53 fills 48.
        assertEquals(47,SourceStatisticBar.filledColumns(89.7324d,mask));
        assertEquals(48,SourceStatisticBar.filledColumns(89.7324d,Math.round(mask)));
        assertEquals(44,SourceStatisticBar.filledColumns(83.05938d,mask));
        assertEquals(0,SourceStatisticBar.filledColumns(100d,-.5d));
        assertEquals(100,SourceStatisticBar.filledColumns(100d,100.5d));
    }

    private static JsonValue fixture() throws Exception {
        try(InputStream input=InformationPanelTextTest.class.getResourceAsStream(
                "/source-information-values.json")) {
            assertNotNull(input);byte[] bytes=input.readAllBytes();
            StringBuilder sha=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))
                sha.append(String.format(Locale.ROOT,"%02x",b&255));
            assertEquals("c31361a0563674cfde34c029b810bd230f858e9e051144a5ce0774505aa7689c",sha.toString());
            return new JsonReader().parse(new String(bytes,StandardCharsets.UTF_8));
        }
    }
}
