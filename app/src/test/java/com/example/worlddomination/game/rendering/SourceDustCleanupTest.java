package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class SourceDustCleanupTest {
    @Test public void realHiddenAttachmentsFollowSourceGlobalOrderAndCleanupLeavesBoom() throws Exception {
        GameState game = game();
        BattlefieldCityRenderer cities = renderer(game);
        ReferenceRandom random = new ReferenceRandom(0);
        for (int tick = 1; tick <= 816; tick++) {
            cities.tickSourceCities(game);
            if (tick % 36 == 0 && tick <= 720)
                cities.destroySourceHiddenCity(game, 25 - tick / 36, random);
        }
        List<?> before = new ArrayList<>(queue(cities));
        List<String> order = new ArrayList<>();
        for (Object child : before) order.add("city" + address(child));
        JsonValue source = fixture().get("legal");
        List<String> expected = new ArrayList<>();
        for (JsonValue city : source.get("attachmentCityOrder")) expected.add(city.asString());
        assertEquals(expected, order);
        cities.removeSourceDustAtAllUnitsDone();
        assertRetained(before, queue(cities), source.get("retainedIndices"));
        for (int i = 0; i < before.size(); i++) {
            int address = address(before.get(i));
            assertEquals(queue(cities).contains(before.get(i)), cities.getCityDustFrame(address) >= 0);
            assertEquals("boom is not a dust cleanup target", 90, cities.getHiddenExplosionFrame(address));
        }
        cities.endBattle();
        assertTrue(queue(cities).isEmpty());
    }

    @Test public void callbackBoundaryMatchesOriginalPostRemovalIndexCap() throws Exception {
        BattlefieldCityRenderer cities = renderer(game());
        List<Object> queue = queue(cities);
        List<List<Object>> children = children(cities);
        Constructor<?> constructor = Class.forName(BattlefieldCityRenderer.class.getName() + "$CityDust")
                .getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        int count = fixture().get("cap").getInt("count");
        // Callback-only synthetic boundary, like the independent source fixture.
        for (int index = 0; index < count; index++) {
            Object child = constructor.newInstance(0);
            queue.add(child);
            children.get(0).add(child);
        }
        List<Object> before = new ArrayList<>(queue);
        cities.removeSourceDustAtAllUnitsDone();
        assertRetained(before, queue, fixture().get("cap").get("retainedIndices"));
        assertEquals(queue, children.get(0));
    }

    @Test public void detachedReferencesStillConsumeTheirSourceQueuePosition() throws Exception {
        GameState game = game();
        BattlefieldCityRenderer cities = renderer(game);
        ReferenceRandom random = new ReferenceRandom(0);
        cities.beginSourceTankCity(game, 7);
        cities.applySourceTankImpact(7, game.getBattlePresentationCity(1, 2).populationMillions - 30, random);
        cities.tickSourceCities(game);
        assertEquals(0, cities.getCityDustFrame(7));
        assertEquals(1, queue(cities).size());
        for (int i = 0; i < 8; i++) cities.tickSourceCities(game);
        assertEquals(-1, cities.getCityDustFrame(7)); // Existing normal backward-seek behavior.
        assertEquals(1, queue(cities).size()); // Source retains the detached reference.
        cities.destroySourceHiddenCity(game, 4, random);
        cities.tickSourceCities(game);
        assertEquals(2, queue(cities).size());
        cities.removeSourceDustAtAllUnitsDone();
        assertEquals(1, queue(cities).size());
        assertEquals(4, address(queue(cities).get(0)));
        assertEquals(0, cities.getCityDustFrame(4));
        assertEquals(1, cities.getHiddenExplosionFrame(4));
    }

    @Test public void ordinaryDustRemainsUntilExplicitCleanupCallback() throws Exception {
        GameState game = game();
        BattlefieldCityRenderer cities = renderer(game);
        cities.beginSourceTankCity(game, 7);
        cities.applySourceTankImpact(7, game.getBattlePresentationCity(1, 2).populationMillions - 30,
                new ReferenceRandom(0));
        cities.tickSourceCities(game);
        assertEquals(0, cities.getCityDustFrame(7));
        cities.removeSourceDustAtAllUnitsDone();
        assertEquals(-1, cities.getCityDustFrame(7));
        assertTrue(queue(cities).isEmpty());
    }

    private static GameState game() {
        GameSetup setup = new GameSetup(0);
        for (int leader : new int[]{0, 1, 2, 4}) assertTrue(setup.toggleOpponent(leader));
        GameState game = new GameState(setup);
        for (int i = 0; i < 2; i++) {
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
        }
        game.confirmTurn();
        return game;
    }

    private static BattlefieldCityRenderer renderer(GameState game) {
        BattlefieldCityRenderer cities = new BattlefieldCityRenderer(null, null);
        cities.reset(game);
        cities.beginBattle(game);
        return cities;
    }

    private static void assertRetained(List<?> before, List<?> after, JsonValue expected) {
        List<Integer> actual = new ArrayList<>();
        for (Object child : after) actual.add(before.indexOf(child));
        List<Integer> indices = new ArrayList<>();
        for (JsonValue index : expected) indices.add(index.asInt());
        assertEquals(indices, actual);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static int address(Object child) throws Exception { return (Integer) field(child, "address"); }
    @SuppressWarnings("unchecked")
    private static List<Object> queue(BattlefieldCityRenderer cities) throws Exception {
        return (List<Object>) field(cities, "dustCleanupQueue");
    }
    @SuppressWarnings("unchecked")
    private static List<List<Object>> children(BattlefieldCityRenderer cities) throws Exception {
        return (List<List<Object>>) field(cities, "sourceDust");
    }
    private static JsonValue fixture() {
        java.io.InputStream input = SourceDustCleanupTest.class.getResourceAsStream("/source-dust-cleanup.json");
        assertNotNull(input);
        return new JsonReader().parse(input);
    }
}
