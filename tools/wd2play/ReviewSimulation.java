package com.example.worlddomination.game.simulation;

/** Development harness only. Owns the stream so a reviewer can fork an unobserved future. */
public final class ReviewSimulation {
    public final GameState game;
    private final ReferenceRandom random;

    public ReviewSimulation(GameSetup setup) {
        random = setup.copyGameplayRandom();
        game = new GameState(setup, random);
    }

    public void reseedFuture(long seed) {
        random.setSeed(seed);
    }
}
