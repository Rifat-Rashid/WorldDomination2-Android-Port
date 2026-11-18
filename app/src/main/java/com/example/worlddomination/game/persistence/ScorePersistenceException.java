package com.example.worlddomination.game.persistence;

/** A score could not be committed to durable storage. The match may still finish. */
public final class ScorePersistenceException extends RuntimeException {
    public ScorePersistenceException(String message) {
        super(message);
    }
}
