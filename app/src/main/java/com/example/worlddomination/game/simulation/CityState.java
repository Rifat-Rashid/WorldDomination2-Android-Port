package com.example.worlddomination.game.simulation;

/** City population state used by source-style attacks and elimination checks. */
public final class CityState {
    public final int id;
    public final int ownerId;
    public final int index;
    public int populationMillions;

    CityState(int id, int ownerId, int index, int populationMillions) {
        this.id = id;
        this.ownerId = ownerId;
        this.index = index;
        this.populationMillions = populationMillions;
    }
}
