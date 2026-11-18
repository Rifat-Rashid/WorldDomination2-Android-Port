package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Independent source campaigns, including the ready-ship companion rejection on turn 22. */
@RunWith(Parameterized.class)
public final class SourceLeaderCampaignTest {
    private final JsonValue campaign;

    public SourceLeaderCampaignTest(long seed, int difficulty, JsonValue campaign) {
        this.campaign = campaign;
    }

    @Parameterized.Parameters(name = "seed={0}, difficulty={1}")
    public static Collection<Object[]> campaigns() throws Exception {
        byte[] compressed;
        try (InputStream input = SourceLeaderCampaignTest.class.getResourceAsStream(
                "/source-leader-campaigns.json.gz")) {
            assertNotNull("independent source campaigns", input);
            compressed = input.readAllBytes();
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(compressed))
            hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        assertEquals("source capture bytes",
                "ef27edb0561bdb03a78e21a8da1ab3bbec0efed3704012713eaa2ace66c97a7b", hash.toString());
        JsonValue fixture;
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            fixture = new JsonReader().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals("source-leader-campaigns-v1", fixture.getString("schema"));
        List<Object[]> cases = new ArrayList<>();
        Set<Integer> leaders = new HashSet<>();
        int turns = 0;
        for (JsonValue capture : fixture.get("campaigns")) {
            JsonValue setup = capture.get("setup");
            cases.add(new Object[]{setup.getLong("seed"), setup.getInt("difficulty"), capture});
            for (JsonValue leader : setup.get("opponents")) leaders.add(leader.asInt());
            turns += capture.get("turns").size;
        }
        assertEquals("five distinct setups at all three difficulties", 15, cases.size());
        assertEquals("all source UI-eligible captured turns", 423, turns);
        assertEquals("every selectable leader represented", 19, leaders.size());
        return cases;
    }

    @Test
    public void legalCampaignPreservesCanonicalStateAndRandomStream() {
        // The source headless queue can bypass the phone's biological action
        // locks. Reject such input instead of calling it a legal UI regression.
        JsonValue previous = campaign.get("initial");
        for (JsonValue turn : campaign.get("turns")) {
            Set<String> disabled = new HashSet<>();
            for (JsonValue action : previous.get("players").get(0).get("bioloadedActions"))
                disabled.add(action.asString());
            for (JsonValue action : turn.get("humanQueuedActions"))
                assertFalse("source UI action available at turn " + turn.getInt("turn"),
                        disabled.contains(action.asString()));
            previous = turn.get("state");
        }
        JsonValue setup = campaign.get("setup");
        new SourceMultiSeedCampaignTest(setup.getLong("seed"), setup.getInt("difficulty"), campaign)
                .canonicalCampaignMatchesIndependentSourceAtEveryTurn();
    }
}
