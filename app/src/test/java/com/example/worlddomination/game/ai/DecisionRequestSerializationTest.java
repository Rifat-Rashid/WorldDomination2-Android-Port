package com.example.worlddomination.game.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DecisionRequestSerializationTest {
    @Test public void actorIndexedAuditHasStringKeys() {
        Map<Integer, String> selections = new LinkedHashMap<>();
        selections.put(2, "P07");
        selections.put(4, "P01");
        assertEquals("{\"stochastic_selected\":{\"2\":\"P07\",\"4\":\"P01\"}}",
                DecisionRequest.json(DecisionRequest.fields("stochastic_selected", selections)));
    }
}
