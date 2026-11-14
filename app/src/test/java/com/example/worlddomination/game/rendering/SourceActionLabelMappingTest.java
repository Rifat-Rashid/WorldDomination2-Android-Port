package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class SourceActionLabelMappingTest {
    @Test
    public void allSourceFrameMappingsAndUnmappedFrames() throws Exception {
        JsonValue source = fixture();
        assertEquals("source table size", 18, source.get("mappedFrames").size);
        for (JsonValue row : source.get("mappedFrames")) {
            assertEquals(row.getString("frame"), GameState.Action.valueOf(
                    row.getString("action")), SourceActionLabelMapping.actionForFrame(
                    row.getString("frame")));
        }
        for (JsonValue row : source.get("excludedFrames")) {
            assertNull("unmapped source frame " + row.asString(),
                    SourceActionLabelMapping.actionForFrame(row.asString()));
        }
        assertNull(SourceActionLabelMapping.actionForFrame(null));
    }

    @Test
    public void firedDefenseBuildingDoesNotExtendInterceptedShipLabels() throws Exception {
        JsonValue source = fixture();
        assertSourceGate(source.get("interceptedShipFrames"),
                source.getInt("interceptedDistinctLabels"),
                source.getInt("interceptedGateOpenTick"));
        assertSourceGate(source.get("successfulShipFrames"),
                source.getInt("successfulDistinctLabels"),
                source.getInt("successfulGateOpenTick"));
        assertEquals(GameState.Action.DEFENSE,
                SourceActionLabelMapping.actionForFrame("antiAir_still"));
        assertNull(SourceActionLabelMapping.actionForFrame("antiAir_fire"));
    }

    @Test
    public void legalShipTurnsChooseFiredOrStillDefenseBeforeLabelClock() throws Exception {
        JsonValue source = fixture();
        for (JsonValue expected : source.get("legalShipDefenseCases")) {
            int target = expected.getInt("target");
            GameState game = gameReadyForShip(target);
            game.confirmTurn();
            assertEquals("legal source ship turn", 21, game.getTurn());
            int defenseCount = 0;
            List<GameState.Action> labels = new ArrayList<>();
            for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings()) {
                if (event.playerId == expected.getInt("defender")
                        && event.action == GameState.Action.DEFENSE) {
                    defenseCount++;
                    assertEquals("source defense building for target " + target,
                            expected.getString("frame"), event.frameName);
                }
                GameState.Action label = SourceActionLabelMapping.actionForFrame(
                        event.frameName);
                if (label != null) labels.add(label);
            }
            assertEquals("one source defender frame", 1, defenseCount);
            assertLabelsAndGate(labels, expected.getInt("distinctLabels"),
                    expected.getInt("gateOpenTick"));
        }
    }

    private static void assertSourceGate(JsonValue frames, int expectedDistinctLabels,
                                         int expectedGateTick) {
        List<GameState.Action> labels = new ArrayList<>();
        for (JsonValue frame : frames) {
            GameState.Action action = SourceActionLabelMapping.actionForFrame(
                    frame.asString());
            if (action != null) {
                labels.add(action);
            }
        }
        assertLabelsAndGate(labels, expectedDistinctLabels, expectedGateTick);
    }

    private static void assertLabelsAndGate(List<GameState.Action> labels,
                                            int expectedDistinctLabels,
                                            int expectedGateTick) {
        EnumSet<GameState.Action> distinct = EnumSet.noneOf(GameState.Action.class);
        distinct.addAll(labels);
        assertEquals("source distinct action labels", expectedDistinctLabels, distinct.size());
        TurnPlaybackSequence playback = new TurnPlaybackSequence();
        playback.begin(labels);
        for (int tick = 1; tick < expectedGateTick; tick++) {
            playback.advance(1f / 24f);
            assertEquals("still showing labels on tick " + tick,
                    TurnPlaybackSequence.Phase.ACTION_LABELS, playback.getPhase());
        }
        playback.advance(1f / 24f);
        assertEquals("source gate tick " + expectedGateTick,
                TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
    }

    private static GameState gameReadyForShip(int target) {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        for (String group : new String[]{"rocket", "warhead", "bioload", "soldier"}) {
            for (int level = 2; level <= 4; level++) {
                rejectDiplomacy(game);
                game.selectAction(GameState.Action.RESEARCH);
                assertTrue(game.selectResearchTechnology(group + level));
                assertTrue(game.acceptSelectedAction());
                resources(game);
                game.confirmTurn();
            }
        }
        for (String technology : new String[]{"sub", "ship"}) {
            rejectDiplomacy(game);
            game.selectAction(GameState.Action.RESEARCH);
            assertTrue(game.selectResearchTechnology(technology));
            assertTrue(game.acceptSelectedAction());
            resources(game);
            game.confirmTurn();
        }
        rejectDiplomacy(game);
        production(game, "special");
        resources(game);
        game.confirmTurn();
        for (int turn = 0; turn < 4; turn++) {
            rejectDiplomacy(game);
            production(game, "bioload");
            resources(game);
            game.confirmTurn();
        }
        rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("ship"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        assertEquals(20, game.getTurn());
        rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        for (int city = 0; city < 3; city++)
            assertTrue(game.configurePreparedShipStrike(target, city, "bioload3"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        return game;
    }

    private static void rejectDiplomacy(GameState game) {
        while (game.getPendingHumanDiplomacyProposal() != null)
            assertTrue(game.respondToDiplomacyProposal(false));
    }

    private static void production(GameState game, String category) {
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction(category, 100));
        assertTrue(game.acceptSelectedAction());
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static JsonValue fixture() throws Exception {
        try (InputStream stream = SourceActionLabelMappingTest.class.getClassLoader()
                .getResourceAsStream("source-bio-action-labels.json")) {
            assertNotNull("source action label provenance fixture", stream);
            return new JsonReader().parse(new InputStreamReader(stream,
                    StandardCharsets.UTF_8));
        }
    }
}
