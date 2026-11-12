package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** Transient source infoLine movie-clip text, timed independently of action selection. */
public final class SourceActionInstruction {
    public static final int DURATION_TICKS = 108; // 4,500 ms at the source's 24 Hz.
    public static final String SELECT_WEAPON = "Select a weapon from your weapon stock on the right...";
    public static final String SELECT_CITY = "Select a city from the map...";
    public static final String WELCOME = "Welcome to World Domination II, commander";
    public static final String OWN_CITY = "You cannot target your own city...";
    public static final String CEASEFIRE = "You have a cease-fire treaty with this player...";

    private String text;
    private int ticksRemaining;

    public String getText() { return text; }
    public int getTicksRemaining() { return ticksRemaining; }

    public void resetWelcome() { show(WELCOME); }

    /** Called when the source would update center-screen content, never from draw. */
    public void onSelectionUpdate(GameState.Action action, String menuKey) {
        if (action == null || menuKey == null) return;
        if (action == GameState.Action.ATTACK && "attack1".equals(menuKey)
                || action == GameState.Action.PREPARED_ATTACK
                && ("preAttackSelect".equals(menuKey) || "preAttack4".equals(menuKey)
                || "preAttack4-ship".equals(menuKey))
                || action == GameState.Action.DEFENSE && "defenseSelect".equals(menuKey)) {
            show(SELECT_WEAPON);
        } else if (action == GameState.Action.PREPARED_ATTACK
                && ("preAttack2".equals(menuKey) || menuKey.startsWith("ship1-"))
                || action == GameState.Action.PROPAGANDA && "propaganda1".equals(menuKey)) {
            show(SELECT_CITY);
        }
    }

    /** Source citySelect warns after any valid selection update; own-city wins. */
    public void onCityAttempt(int owner, boolean ceasefire) {
        if (owner == 0) show(OWN_CITY);
        else if (ceasefire) show(CEASEFIRE);
    }

    public void tick() {
        if (ticksRemaining == 0) return;
        if (--ticksRemaining == 0) text = null;
    }

    private void show(String next) {
        text = next;
        ticksRemaining = DURATION_TICKS;
    }
}
