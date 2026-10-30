package com.example.worlddomination.game.simulation;

/** City population state used by source-style attacks and elimination checks. */
public final class CityState {
    private static final int BASE_FRAME_COUNT = 6;
    private static final int FRAME_DIVIDER = 90;

    public final int id;
    public final int ownerId;
    public final int index;
    public int populationMillions;
    private int deathFrame = -1;
    /** True while a successful biological strike's contamination is shown. */
    public boolean bioloaded;

    CityState(int id, int ownerId, int index, int populationMillions) {
        this.id = id;
        this.ownerId = ownerId;
        this.index = index;
        this.populationMillions = populationMillions;
    }

    /** Source City.size_and_frame: six building sizes across a 90M city population. */
    public static int frameForPopulation(int populationMillions) {
        if (populationMillions <= 0) {
            return 0;
        }
        int frame = (int) Math.ceil(populationMillions * (double) BASE_FRAME_COUNT
                / FRAME_DIVIDER);
        return Math.min(BASE_FRAME_COUNT, Math.max(1, frame));
    }

    /** Current original city art frame, or its source ruin frame after destruction. */
    public int getArtworkFrame() {
        return populationMillions > 0 ? frameForPopulation(populationMillions) : deathFrame;
    }

    public int getDeathFrame() {
        return deathFrame;
    }

    /** Keeps the model-derived ruin frame when source turn resolution empties a city. */
    void setPopulationMillions(int population) {
        if (populationMillions > 0 && population <= 0 && deathFrame < 0) {
            deathFrame = frameForPopulation(populationMillions) + 9;
        } else if (population > 0) {
            deathFrame = -1;
        }
        populationMillions = population;
    }
}
