package com.example.worlddomination.game.simulation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

/** Authored command data and callback semantics from the read-only source. */
public final class SourceHiddenCommandsTest {
    private static final String FIXTURE_SHA256 =
            "f2988d633767f9dfe352acd7efc02f1a7f1a2d0f90c4ff5f7803e2589744d3e2";
    @Test public void authoredCommandsMatchSourceAndToggleExactly() throws Exception {
        JsonValue fixture = fixture();
        assertEquals(9, fixture.get("toggles").size);
        assertEquals(6, fixture.get("messages").size);
        for (JsonValue row = fixture.get("toggles").child; row != null; row = row.next) {
            SourceHiddenCommands commands = new SourceHiddenCommands();
            SourceHiddenCommands.Flag flag = SourceHiddenCommands.Flag.valueOf(row.getString("flag"));
            SourceHiddenCommands.Submission first = commands.submit(row.getString("input"));
            assertTrue(first.recognized);
            assertNull(first.message);
            assertTrue(commands.isActive(flag));
            assertTrue(commands.isScoreCorrupt());
            SourceHiddenCommands.Submission second = commands.submit(row.getString("input"));
            assertTrue(second.recognized);
            assertNull(second.message);
            assertFalse(commands.isActive(flag));
            assertTrue(commands.isScoreCorrupt());
        }
        for (JsonValue row = fixture.get("messages").child; row != null; row = row.next) {
            SourceHiddenCommands commands = new SourceHiddenCommands();
            SourceHiddenCommands.Submission submission = commands.submit(row.getString("input"));
            assertTrue(submission.recognized);
            assertEquals(row.getString("message"), submission.message);
            assertTrue(commands.isScoreCorrupt());
            for (SourceHiddenCommands.Flag flag : SourceHiddenCommands.Flag.values()) {
                assertFalse(commands.isActive(flag));
            }
        }
    }

    @Test public void unknownCaseAndWhitespaceStillCorruptScore() {
        for (String input : new String[] {"Trump", "trump ", " youda", "unknown", ""}) {
            SourceHiddenCommands commands = new SourceHiddenCommands();
            SourceHiddenCommands.Submission submission = commands.submit(input);
            assertFalse(submission.recognized);
            assertNull(submission.message);
            assertTrue(commands.isScoreCorrupt());
            for (SourceHiddenCommands.Flag flag : SourceHiddenCommands.Flag.values()) {
                assertFalse(commands.isActive(flag));
            }
        }
    }

    @Test public void copyIsolatesPrivateBattleFlagsAndScoreState() {
        SourceHiddenCommands canonical = new SourceHiddenCommands();
        canonical.submit("dexter");
        canonical.submit("gimmegimme");
        canonical.submit("trump");
        canonical.submit("free willy");
        SourceHiddenCommands battle = canonical.copy();
        battle.submit("dexter");
        battle.submit("gimmegimme");
        battle.submit("trump");
        assertFalse(battle.isActive(SourceHiddenCommands.Flag.TECH));
        assertFalse(battle.isActive(SourceHiddenCommands.Flag.STOCK));
        assertFalse(battle.isActive(SourceHiddenCommands.Flag.CASH));
        assertTrue(battle.isActive(SourceHiddenCommands.Flag.ORCA));
        assertTrue(canonical.isActive(SourceHiddenCommands.Flag.TECH));
        assertTrue(canonical.isActive(SourceHiddenCommands.Flag.STOCK));
        assertTrue(canonical.isActive(SourceHiddenCommands.Flag.CASH));
        assertTrue(canonical.isScoreCorrupt());
        SourceHiddenCommands clean = new SourceHiddenCommands();
        assertFalse(clean.isScoreCorrupt());
    }

    private static JsonValue fixture() throws Exception {
        try (InputStream in = SourceHiddenCommandsTest.class.getResourceAsStream(
                "/source-hidden-commands.json")) {
            assertNotNull(in);
            byte[] bytes = in.readAllBytes();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            assertEquals(FIXTURE_SHA256, hex.toString());
            return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }

}
