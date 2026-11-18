package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

/** Original private seed0 Medium human-city4 callback trace; no GL resources required. */
public final class SourceHiddenCityExplosionTest {
    @Test public void hiddenCityChildrenMatchSourceForwardDeathSeekAndRemainPrivate() throws Exception {
        GameSetup setup = new GameSetup(0);
        for (int leader : new int[]{0, 1, 2, 4}) assertTrue(setup.toggleOpponent(leader));
        GameState game = new GameState(setup);
        for (int i = 0; i < 2; i++) {
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
        }
        game.confirmTurn();
        GameState.BattleCitySnapshot snapshot = game.getBattlePresentationCity(0, 4);
        assertEquals(154, snapshot.populationMillions);
        assertEquals(6, snapshot.artworkFrame);
        int canonicalPopulation = game.getPlayerState(0).getCity(4).populationMillions;
        int canonicalFrame = game.getPlayerState(0).getCity(4).getArtworkFrame();
        ReferenceRandom canonicalRandom = game.createPresentationRandomCopy();
        BattlefieldCityRenderer cities = new BattlefieldCityRenderer(null, null);
        cities.reset(game);
        cities.beginBattle(game);
        ReferenceRandom random = new ReferenceRandom(0);
        for (int i = 0; i < 73; i++) random.nextDouble();
        cities.destroySourceHiddenCity(game, 4, random);
        assertEquals(0, cities.getHiddenExplosionFrame(4));
        assertEquals(69099, cities.getCityDustName(4));
        int tick = 36;
        try (InputStream input = getClass().getResourceAsStream("/source-hidden-city-explosion.json")) {
            assertNotNull(input);
            for (JsonValue row : new JsonReader().parse(input).get("rows")) {
                int targetTick = row.getInt("tick");
                while (tick < targetTick) { cities.tickSourceCities(game); tick++; }
                assertEquals("city frame at " + tick, row.getInt("frame"), cities.getSourceCityFrame(4));
                assertEquals("alpha at " + tick, row.getDouble("alpha"), cities.getSourceCityAlpha(4), 1e-9);
                assertEquals("boom at " + tick, row.getInt("boomFrame") - 1, cities.getHiddenExplosionFrame(4));
                assertEquals("dust at " + tick, row.getInt("dustFrame") - 1, cities.getCityDustFrame(4));
                if (row.getInt("dustFrame") > 0)
                    assertEquals(cities.getHiddenExplosionDepth(4) + 1, cities.getCityDustDepth(4));
            }
        }
        assertEquals(canonicalPopulation, game.getPlayerState(0).getCity(4).populationMillions);
        assertEquals(canonicalFrame, game.getPlayerState(0).getCity(4).getArtworkFrame());
        assertEquals(canonicalRandom.nextDouble(), game.createPresentationRandomCopy().nextDouble(), 0);
        cities.endBattle();
        assertEquals(-1, cities.getHiddenExplosionFrame(4));
        assertEquals(-1, cities.getCityDustFrame(4));
        assertEquals(canonicalFrame, cities.getSourceCityFrame(4));
    }
}
