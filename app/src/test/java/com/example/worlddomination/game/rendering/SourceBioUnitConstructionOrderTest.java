package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.BiologicalBattleModelTest;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.junit.Assert.*;

/** Six legal UI captures of Battle's record expansion and Presenter's reverse Unit walk. */
public final class SourceBioUnitConstructionOrderTest {
    private static final String FIXTURE = "source-bio-unit-order.json";
    private static final String FIXTURE_SHA =
            "c98866641e7a0d0d82daae65232978ad9a5bc92debe04c17e4d35970d2428e39";

    @Test
    public void sourceRecordAndUnitArraysPreserveBothReverseEnumerations() throws IOException {
        JsonValue cases = fixture().get("cases");
        assertEquals(6, cases.size);
        for (JsonValue capture = cases.child; capture != null; capture = capture.next) {
            String label = capture.getString("case");
            JsonValue results = capture.get("turnResultsBattle");
            JsonValue tmp = capture.get("tmpBattle");
            JsonValue ordered = capture.get("randOrder");
            assertEquals(label, results.size, tmp.size);
            assertEquals(label, tmp.size, ordered.size);
            for (int i = 0; i < results.size; i++) {
                assertEquals(label + " player", results.get(results.size - 1 - i).getInt("player"),
                        tmp.get(i).getInt("player"));
                assertEquals(label + " weapon", results.get(results.size - 1 - i).getInt("weaponId"),
                        tmp.get(i).getInt("weaponId"));
                assertEquals(label + " sorted battle group", tmp.get(i).getInt("weaponId"),
                        ordered.get(i).getInt("weaponId"));
            }
            JsonValue stored = capture.get("spawnStored");
            JsonValue physical = capture.get("activePhysical");
            assertEquals(label + " Unit expansion", stored.size, physical.size);
            for (int i = 0; i < physical.size; i++) {
                JsonValue sourceUnit = physical.get(i);
                assertEquals(label + " physical Unit name", "unit" + (i + 1),
                        sourceUnit.getString("unitName"));
                String storedMc = stored.get(stored.size - 1 - i).getString("forceMc");
                assertEquals(label + " reverse Unit construction",
                        "plane1".equals(storedMc) ? "plane001" : storedMc,
                        sourceUnit.getString("mcId"));
            }
        }
    }

    @Test
    public void javaPresentationEventsMatchSourcePhysicalRecordGroups() throws Exception {
        compareSingleton("plane-biological",
                SourceAircraftTrailDisplayTest.gameReadyForPlane(true), "plane1");
        compareSingleton("plane-warhead",
                SourceAircraftTrailDisplayTest.gameReadyForPlane(false), "plane1");
        compareShip("ship-intercept", SourceAircraftTrailDisplayTest.gameReadyForShip(1));
        compareShip("ship-success", SourceAircraftTrailDisplayTest.gameReadyForShip(2));
        compareSingleton("sub-biological",
                BiologicalBattleModelTest.legalSeventeenTurnBioLaunchQueued(), "sub");
        compareSingleton("rocket-biological", gameReadyForRocket(), "rocket1");
    }

    private static void compareSingleton(String label, GameState game, String weapon)
            throws IOException {
        game.confirmTurn();
        JsonValue capture = sourceCase(label);
        assertEquals(label, 1, capture.get("activePhysical").size);
        List<GameState.CombatEvent> events =
                game.getLastTurnCombatEventsInUnitConstructionOrder();
        assertEquals(label + " record count", 1, events.size());
        assertEquals(label + " weapon", weapon, events.get(0).weaponName);
        assertEquals(label + " attacker", capture.get("randOrder").get(0).getInt("player"),
                events.get(0).attackerPlayerId);
    }

    private static void compareShip(String label, GameState game) throws IOException {
        game.confirmTurn();
        JsonValue capture = sourceCase(label);
        assertEquals(label, 6, capture.get("activePhysical").size);
        assertEquals(label + " first source record", 0,
                capture.get("randOrder").get(0).getInt("player"));
        assertEquals(label + " second source record", 1,
                capture.get("randOrder").get(1).getInt("player"));
        List<GameState.CombatEvent> events =
                game.getLastTurnCombatEventsInUnitConstructionOrder();
        assertEquals(label + " three ship payloads and one tank record", 4, events.size());
        for (int i = 0; i < 3; i++) {
            JsonValue sourceUnit = capture.get("activePhysical").get(i);
            GameState.CombatEvent event = events.get(i);
            assertEquals(label + " unit" + (i + 1), "ship_plane",
                    sourceUnit.getString("mcId"));
            assertEquals(label + " record" + i, "ship", event.weaponName);
            assertEquals(label + " source payload target" + i,
                    sourceUnit.getInt("wildCity"),
                    event.defenderPlayerId * 5 + event.cityIndex);
            // Battle.as reverse-enumerates the three payloads, then
            // Presenter.as reverses the expanded Unit array again. Java's
            // sourcePlaneIndex records the offset (2,1,0), not wildcard _number.
            assertEquals(label + " source launch offset" + i,
                    2 - i, event.shipPayloadIndex);
        }
        assertEquals(label + " following AI tank record", "soldier3",
                events.get(3).weaponName);
        assertEquals(label + " AI tank attacker", 1, events.get(3).attackerPlayerId);
    }

    private static GameState gameReadyForRocket() {
        GameState game = SourceAircraftTrailDisplayTest.newGame();
        SourceAircraftTrailDisplayTest.rejectDiplomacy(game);
        SourceAircraftTrailDisplayTest.prepare(game, "rocket1");
        SourceAircraftTrailDisplayTest.resources(game);
        game.confirmTurn();
        SourceAircraftTrailDisplayTest.rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 2, "bioload1"));
        assertTrue(game.acceptSelectedAction());
        SourceAircraftTrailDisplayTest.resources(game);
        return game;
    }

    private static JsonValue sourceCase(String name) throws IOException {
        JsonValue cases = fixture().get("cases");
        for (JsonValue capture = cases.child; capture != null; capture = capture.next) {
            if (name.equals(capture.getString("case"))) return capture;
        }
        throw new AssertionError("Missing source case " + name);
    }

    private static JsonValue fixture() throws IOException {
        byte[] bytes;
        try (InputStream input = SourceBioUnitConstructionOrderTest.class
                .getClassLoader().getResourceAsStream(FIXTURE)) {
            if (input == null) throw new IOException("Missing source Unit order fixture");
            bytes = input.readAllBytes();
        }
        assertEquals("source Unit order fixture bytes", FIXTURE_SHA, sha256(bytes));
        return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
