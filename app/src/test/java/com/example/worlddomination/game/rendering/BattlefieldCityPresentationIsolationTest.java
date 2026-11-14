package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public final class BattlefieldCityPresentationIsolationTest {
    private static final int CITY = 7;

    @Test
    public void legalFourTurnImpactKeepsPostResolutionCanonicalCityFrozen() {
        GameState game = legalSuicideAttack();
        game.confirmTurn();
        assertEquals(4, game.getBattlePresentationCity(1, 2).artworkFrame);
        assertEquals(2, game.getPlayerState(1).getCity(2).getArtworkFrame());

        BattlefieldCityRenderer cities = new BattlefieldCityRenderer(null, null);
        cities.reset(game);
        assertEquals(2, cities.getSourceCityFrame(CITY));
        assertEquals(100, cities.getSourceCityAlpha(CITY));
        cities.beginBattle(game);
        assertTrue(cities.isBattleActive());
        assertEquals(4, cities.getSourceCityFrame(CITY));
        assertEquals(100, cities.getSourceCityAlpha(CITY));
        cities.beginSourceTankCity(game, CITY);
        cities.applySourceTankImpact(CITY, 18, game.createBattlePresentationRandomCopy());
        cities.tickSourceCities();
        assertEquals(85, cities.getSourceCityAlpha(CITY));
        assertTrue(cities.getCityDustName(CITY) >= 10000);
        assertEquals(0, cities.getCityDustFrame(CITY));

        cities.endBattle();
        assertFalse(cities.isBattleActive());
        assertEquals(2, cities.getSourceCityFrame(CITY));
        assertEquals(100, cities.getSourceCityAlpha(CITY));
        assertEquals(-1, cities.getCityDustName(CITY));
        assertEquals(-1, cities.getCityDustFrame(CITY));
        cities.tick(game);
        assertEquals(2, cities.getSourceCityFrame(CITY));
        assertEquals(100, cities.getSourceCityAlpha(CITY));
    }

    @Test
    public void secondBattleStartsFreshAndResetCancelsActivePresentation() {
        GameState game = legalSuicideAttack();
        game.confirmTurn();
        BattlefieldCityRenderer cities = new BattlefieldCityRenderer(null, null);
        cities.reset(game);
        cities.beginBattle(game);
        cities.beginSourceTankCity(game, CITY);
        cities.applySourceTankImpact(CITY, 18, game.createBattlePresentationRandomCopy());
        cities.tickSourceCities();
        cities.endBattle();

        resources(game);
        resources(game);
        game.confirmTurn();
        cities.beginBattle(game);
        assertEquals(game.getBattlePresentationCity(1, 2).artworkFrame,
                cities.getSourceCityFrame(CITY));
        assertEquals(100, cities.getSourceCityAlpha(CITY));
        assertEquals(-1, cities.getCityDustFrame(CITY));
        cities.reset(game);
        assertFalse(cities.isBattleActive());
        assertEquals(game.getPlayerState(1).getCity(2).getArtworkFrame(),
                cities.getSourceCityFrame(CITY));
    }

    @Test
    public void mutableCloudAndCityClocksUseSeparateBundles() throws Exception {
        GameState game = legalSuicideAttack();
        game.confirmTurn();
        BattlefieldCityRenderer cities = new BattlefieldCityRenderer(null, null);
        cities.reset(game);
        Object canonicalSequence = ((Object[]) field(cities, "citySequences"))[CITY];
        Object canonicalClouds = field(field(cities, "bioloadCloudRenderer"), "cityClouds");
        cities.beginBattle(game);
        assertNotSame(canonicalSequence, ((Object[]) field(cities, "citySequences"))[CITY]);
        assertNotSame(canonicalClouds,
                field(field(cities, "bioloadCloudRenderer"), "cityClouds"));
        cities.endBattle();
        assertTrue(canonicalSequence == ((Object[]) field(cities, "citySequences"))[CITY]);
        assertTrue(canonicalClouds == field(field(cities, "bioloadCloudRenderer"), "cityClouds"));
    }

    @Test
    public void battleCreationFreezesExistingCloudsWithoutEagerlyStartingNewContamination()
            throws Exception {
        GameState game = legalSuicideAttack();
        game.getPlayerState(1).getCity(0).bioloaded = true;
        BattlefieldCityRenderer cities = new BattlefieldCityRenderer(null, null);
        cities.reset(game);
        Object clouds = field(cities, "bioloadCloudRenderer");
        BioloadCloudAnimation[] canonical =
                (BioloadCloudAnimation[]) field(clouds, "cityClouds");
        BioloadCloudAnimation existing = canonical[5];
        float canonicalY = existing.getY(0);
        game.getPlayerState(1).getCity(1).bioloaded = true;
        cities.beginBattle(game);
        BioloadCloudAnimation[] battle =
                (BioloadCloudAnimation[]) field(clouds, "cityClouds");
        assertNotSame(existing, battle[5]);
        assertTrue("new contamination waits for the existing tick path", battle[6] == null);
        cities.tick(game);
        assertTrue(battle[6] != null);
        assertEquals("battle ticks do not move canonical clouds", canonicalY,
                existing.getY(0), 0f);
        assertTrue(canonical[6] == null);
        cities.endBattle();
        assertTrue(canonical == field(clouds, "cityClouds"));
        assertTrue(existing == canonical[5]);
        assertEquals(canonicalY, existing.getY(0), 0f);
        assertTrue(canonical[6] == null);
        cities.tick(game);
        assertTrue("canonical callbacks resume only after handoff", canonical[6] != null);
        assertEquals(canonicalY - 2f, existing.getY(0), 0f);
    }

    @Test
    public void legacyUpdateTargetsStillTracksModelWithoutBattleActivation() throws Exception {
        GameState game = legalSuicideAttack();
        BattlefieldCityRenderer cities = new BattlefieldCityRenderer(null, null);
        cities.reset(game);
        assertEquals(4, cities.getSourceCityFrame(CITY));
        game.confirmTurn();
        cities.updateTargets(game);
        assertFalse(cities.isBattleActive());
        assertEquals(2, ((int[]) field(cities, "trackedTargets"))[CITY]);
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static GameState legalSuicideAttack() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        research(game, "soldier2"); resources(game); game.confirmTurn();
        research(game, "soldier3"); resources(game); game.confirmTurn();
        research(game, "soldier4");
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 100));
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 2, "soldier4"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        return game;
    }

    private static void research(GameState game, String tech) {
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology(tech));
        assertTrue(game.acceptSelectedAction());
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }
}
