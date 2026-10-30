package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CityStateTest {
    @Test
    public void populationSelectsTheSourceSixCityFrames() {
        assertEquals(0, CityState.frameForPopulation(0));
        assertEquals(0, CityState.frameForPopulation(-1));
        assertEquals(1, CityState.frameForPopulation(1));
        assertEquals(1, CityState.frameForPopulation(15));
        assertEquals(2, CityState.frameForPopulation(16));
        assertEquals(2, CityState.frameForPopulation(30));
        assertEquals(3, CityState.frameForPopulation(31));
        assertEquals(4, CityState.frameForPopulation(46));
        assertEquals(5, CityState.frameForPopulation(61));
        assertEquals(6, CityState.frameForPopulation(76));
        assertEquals(6, CityState.frameForPopulation(450));
    }

    @Test
    public void emptyingCityKeepsItsSourceRuinFrameAndRepopulationRestoresLivingArt() {
        CityState city = new CityState(6, 1, 1, 1);

        assertEquals(1, city.getArtworkFrame());
        city.setPopulationMillions(0);
        assertEquals(10, city.getDeathFrame());
        assertEquals(10, city.getArtworkFrame());

        city.setPopulationMillions(46);
        assertEquals(-1, city.getDeathFrame());
        assertEquals(4, city.getArtworkFrame());
    }
}
