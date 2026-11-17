package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent guarded Python BattlePlayback positions versus retained Java clips. */
public final class ActionRevealSlotParityTest {
    @Test public void resourcesProduction() throws Exception { check("resources_production"); }
    @Test public void researchResources() throws Exception { check("research_resources"); }
    @Test public void propagandaResources() throws Exception { check("propaganda_resources"); }
    @Test public void diplomacyResources() throws Exception { check("diplomacy_resources"); }
    @Test public void religionResources() throws Exception { check("religion_resources"); }
    @Test public void attackHatch() throws Exception { check("attack_hatch"); }
    @Test public void preparedAirfield() throws Exception { check("prepared_airfield"); }
    @Test public void preparedSilo() throws Exception { check("prepared_silo"); }
    @Test public void preparedSub() throws Exception { check("prepared_sub"); }
    @Test public void preparedSpace() throws Exception { check("prepared_space"); }

    private static void check(String name) throws Exception {
        JsonValue fixture;
        try (InputStreamReader reader = new InputStreamReader(
                ActionRevealSlotParityTest.class.getResourceAsStream(
                        "/source-action-building-audio/action-building-trace.json"), StandardCharsets.UTF_8)) {
            fixture = new JsonReader().parse(reader);
        }
        JsonValue source = null;
        for (JsonValue candidate : fixture.get("legal")) {
            if (name.equals(candidate.getString("name"))) source = candidate;
        }
        assertNotNull(name, source);
        GameSetup setup = new GameSetup(source.getLong("seed"));
        setup.setDifficulty(1);
        setup.setStartingBuilding(2);
        assertTrue(setup.toggleOpponent(2));
        assertTrue(setup.toggleOpponent(9));
        GameState game = new GameState(setup);
        for (JsonValue order : source.get("orders")) {
            String action = order.getString("action");
            if ("attack".equals(action) || "preAttack".equals(action)) {
                GameState.WeaponState weapon = game.getHuman().getWeapons().get(order.getString("weapon"));
                assertNotNull(name, weapon);
                weapon.active = true;
                weapon.inStock = 2;
            }
            switch (action) {
                case "resources": game.selectAction(GameState.Action.RESOURCES); break;
                case "production":
                    game.selectAction(GameState.Action.PRODUCTION);
                    assertTrue(name, game.configureProduction(order.getString("category"), order.getInt("percent")));
                    break;
                case "research":
                    game.selectAction(GameState.Action.RESEARCH);
                    assertTrue(name, game.selectResearchTechnology(order.getString("tech")));
                    break;
                case "propaganda":
                    game.selectAction(GameState.Action.PROPAGANDA);
                    assertTrue(name, game.configurePropaganda(order.getInt("target"), order.getInt("city")));
                    break;
                case "diplomacy":
                    game.selectAction(GameState.Action.DIPLOMACY);
                    assertTrue(name, game.selectDiplomacyTarget(order.getInt("target")));
                    assertTrue(name, game.selectDiplomacyOption(order.getInt("diplomacy")));
                    break;
                case "religion": game.selectAction(GameState.Action.MASS_MEDIA); break;
                case "attack":
                    game.selectAction(GameState.Action.ATTACK);
                    assertTrue(name, game.configureAttack(order.getInt("target"), order.getInt("city"), order.getString("weapon")));
                    break;
                case "preAttack":
                    game.selectAction(GameState.Action.PREPARED_ATTACK);
                    assertTrue(name, game.configurePreparedCarrier(order.getString("weapon")));
                    break;
                default: fail("Unknown source order " + action);
            }
            assertTrue(name + " accepts " + action, game.acceptSelectedAction());
        }
        game.confirmTurn();
        FlashActionBuildingAnimation[][] retained = new FlashActionBuildingAnimation[5][2];
        for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings()) {
            FlashActionBuildingAnimation previous = retained[event.playerId][event.slot - 1];
            retained[event.playerId][event.slot - 1] = new FlashActionBuildingAnimation(event, previous);
        }
        JsonValue positions = source.get("positions").get("indexOrder");
        assertEquals(name + " source positions", 10, positions.size);
        for (int i = 0; i < 10; i++) {
            JsonValue row = positions.get(i);
            assertEquals(name + " index", i, row.getInt("index"));
            assertEquals(name + " player", i / 2, row.getInt("player"));
            int slot = i % 2 == 0 ? 2 : 1;
            FlashActionBuildingAnimation actual = retained[i / 2][slot - 1];
            JsonValue building = row.get("building");
            if (building == null || building.isNull()) {
                assertNull(name + " source index " + i + " must be empty", actual);
            } else {
                assertNotNull(name + " source index " + i + " missing", actual);
                assertEquals(name + " source index " + i + " frame", building.asString(), actual.frameName);
                assertEquals(name + " source index " + i + " clip", row.getString("clipName"),
                        "ab" + actual.playerId + "_" + actual.slot);
            }
        }
    }
}
