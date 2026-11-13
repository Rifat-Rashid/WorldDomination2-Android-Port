package com.example.worlddomination.testing;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

import com.example.worlddomination.MainActivity;

/** Closes the game while libGDX can still dispose its managed GL resources. */
public final class GameActivityScenario implements AutoCloseable {
    private final ActivityScenario<MainActivity> scenario;
    private boolean closed;

    private GameActivityScenario(ActivityScenario<MainActivity> scenario) {
        this.scenario = scenario;
    }

    public static GameActivityScenario launch(Class<MainActivity> activityClass) {
        return new GameActivityScenario(ActivityScenario.launch(activityClass));
    }

    public void onActivity(ActivityScenario.ActivityAction<MainActivity> action) {
        scenario.onActivity(action);
    }

    public void moveToState(Lifecycle.State state) {
        scenario.moveToState(state);
    }

    public Lifecycle.State getState() {
        return scenario.getState();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (scenario.getState() != Lifecycle.State.DESTROYED) {
                scenario.moveToState(Lifecycle.State.RESUMED);
                scenario.onActivity(MainActivity::finish);
            }
        } finally {
            scenario.close();
        }
    }
}
