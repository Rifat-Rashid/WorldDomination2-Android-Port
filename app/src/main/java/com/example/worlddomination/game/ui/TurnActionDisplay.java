package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import java.util.List;

/** Source curActionDisplay: one action caption and the nations performing that action. */
public final class TurnActionDisplay {
    private GameState.Action action;
    private int participatingPlayers;
    private int alphaPercent;
    private boolean visible;

    public void reveal(GameState.Action next, List<GameState.ActionBuildingEvent> buildings) {
        if (next == null) return;
        action = next;
        participatingPlayers = 0;
        for (GameState.ActionBuildingEvent building : buildings) {
            if (building.action == next) participatingPlayers |= 1 << building.playerId;
        }
        visible = true;
    }

    /** View.fadeClip uses 20 percentage points per source frame and targets 80%. */
    public void tick() {
        alphaPercent = visible ? Math.min(80, alphaPercent + 20) : Math.max(0, alphaPercent - 20);
    }

    public void hide() { visible = false; }
    public GameState.Action getAction() { return action; }
    public boolean includesPlayer(int playerId) { return (participatingPlayers & (1 << playerId)) != 0; }
    public float getAlpha() { return alphaPercent / 100f; }
    public boolean isAnimating() { return alphaPercent != (visible ? 80 : 0); }
    public void reset() { action = null; participatingPlayers = alphaPercent = 0; visible = false; }
}
