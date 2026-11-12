package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SourceActionInstructionTest {
    private final SourceActionInstruction instruction = new SourceActionInstruction();

    @Test public void exactLiveMovieClipStringsAndTickBoundary() {
        instruction.resetWelcome();
        assertEquals("Welcome to World Domination II, commander", instruction.getText());
        assertEquals("Select a weapon from your weapon stock on the right...",
                SourceActionInstruction.SELECT_WEAPON);
        assertEquals("Select a city from the map...", SourceActionInstruction.SELECT_CITY);
        assertEquals("You cannot target your own city...", SourceActionInstruction.OWN_CITY);
        assertEquals("You have a cease-fire treaty with this player...",
                SourceActionInstruction.CEASEFIRE);
        for (int i = 0; i < 107; i++) instruction.tick();
        assertEquals(SourceActionInstruction.WELCOME, instruction.getText());
        instruction.tick();
        assertNull(instruction.getText());
    }

    @Test public void sourceSelectionBranchesAndUnassignedFrames() {
        prompt(GameState.Action.ATTACK, "attackSelect", null);
        prompt(GameState.Action.ATTACK, "attack1", SourceActionInstruction.SELECT_WEAPON);
        prompt(GameState.Action.PREPARED_ATTACK, "preAttackSelect", SourceActionInstruction.SELECT_WEAPON);
        prompt(GameState.Action.PREPARED_ATTACK, "preAttack2", SourceActionInstruction.SELECT_CITY);
        prompt(GameState.Action.PREPARED_ATTACK, "preAttack4", SourceActionInstruction.SELECT_WEAPON);
        prompt(GameState.Action.PREPARED_ATTACK, "ship1-0", SourceActionInstruction.SELECT_CITY);
        prompt(GameState.Action.PREPARED_ATTACK, "ship1-1", SourceActionInstruction.SELECT_CITY);
        prompt(GameState.Action.PREPARED_ATTACK, "ship1-2", SourceActionInstruction.SELECT_CITY);
        prompt(GameState.Action.PREPARED_ATTACK, "preAttack4-ship", SourceActionInstruction.SELECT_WEAPON);
        prompt(GameState.Action.DEFENSE, "defenseSelect", SourceActionInstruction.SELECT_WEAPON);
        prompt(GameState.Action.PROPAGANDA, "propaganda1", SourceActionInstruction.SELECT_CITY);
    }

    @Test public void repeatRearmsButIdleQueueAndCancelDoNotReplacePrompt() {
        instruction.onSelectionUpdate(GameState.Action.DEFENSE, "defenseSelect");
        for (int i = 0; i < 60; i++) instruction.tick();
        instruction.onSelectionUpdate(GameState.Action.DEFENSE, "defenseSelect");
        assertEquals(108, instruction.getTicksRemaining());
        instruction.onSelectionUpdate(null, null); // resetBtns after queue/cancel
        instruction.onSelectionUpdate(GameState.Action.DIPLOMACY, "diplomacy");
        assertEquals(SourceActionInstruction.SELECT_WEAPON, instruction.getText());
        for (int i = 0; i < 107; i++) instruction.tick();
        assertEquals(SourceActionInstruction.SELECT_WEAPON, instruction.getText());
        instruction.tick();
        assertNull(instruction.getText());
    }

    @Test public void completedAndUnrelatedMenusLeavePreviousTransientAlone() {
        instruction.onSelectionUpdate(GameState.Action.PREPARED_ATTACK, "preAttack2");
        instruction.tick();
        String[] completedPrepared = {"preAttack-rocket1", "preAttack3-warhead",
                "preAttack_space", "ship2", "preCancel"};
        for (String key : completedPrepared)
            instruction.onSelectionUpdate(GameState.Action.PREPARED_ATTACK, key);
        instruction.onSelectionUpdate(GameState.Action.ATTACK, "attackSelect");
        instruction.onSelectionUpdate(GameState.Action.ATTACK, "attack2-soldier1");
        instruction.onSelectionUpdate(GameState.Action.ATTACK, "attack-spy-bhu-spyCenter");
        instruction.onSelectionUpdate(GameState.Action.RESOURCES, "resources");
        instruction.onSelectionUpdate(GameState.Action.PRODUCTION, "production");
        instruction.onSelectionUpdate(GameState.Action.RESEARCH, "research");
        instruction.onSelectionUpdate(GameState.Action.MASS_MEDIA, "religion-bhu");
        instruction.onSelectionUpdate(GameState.Action.DIPLOMACY, "diplomacy");
        instruction.onSelectionUpdate(GameState.Action.SKIP, "skip_action");
        assertEquals(SourceActionInstruction.SELECT_CITY, instruction.getText());
        assertEquals(107, instruction.getTicksRemaining());
    }

    @Test public void invalidCityWarningOverridesOldPromptAndOwnCityWins() {
        instruction.onSelectionUpdate(GameState.Action.PROPAGANDA, "propaganda1");
        instruction.onCityAttempt(2, true);
        assertEquals(SourceActionInstruction.CEASEFIRE, instruction.getText());
        instruction.onCityAttempt(0, true);
        assertEquals(SourceActionInstruction.OWN_CITY, instruction.getText());
        instruction.onCityAttempt(3, false);
        assertEquals(SourceActionInstruction.OWN_CITY, instruction.getText());
    }

    @Test public void sourceCityWarningsAlsoApplyWhenNoActionIsSelected() {
        instruction.onCityAttempt(0, false);
        assertEquals(SourceActionInstruction.OWN_CITY, instruction.getText());
        instruction.onCityAttempt(1, true);
        assertEquals(SourceActionInstruction.CEASEFIRE, instruction.getText());
    }

    private void prompt(GameState.Action action, String key, String expected) {
        SourceActionInstruction fresh = new SourceActionInstruction();
        fresh.onSelectionUpdate(action, key);
        assertEquals(key, expected, fresh.getText());
    }
}
