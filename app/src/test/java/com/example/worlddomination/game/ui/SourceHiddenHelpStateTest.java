package com.example.worlddomination.game.ui;

import org.junit.Test;
import static org.junit.Assert.*;

/** Captured Presenter_interface Index callbacks and TextEntry field lifetime. */
public final class SourceHiddenHelpStateTest {
    @Test public void tenIndexClicksRevealOnlyTheAuthoredField() {
        SourceHelpState state = new SourceHelpState();
        state.open();
        for (int i = 1; i <= 10; i++) {
            assertEquals(i, state.getInputFrame());
            assertFalse(state.hasInputField());
            assertTrue(state.activate("btnIndex_c"));
        }
        assertTrue(state.hasInputField());
        assertFalse(state.isCommandVisible());
        assertFalse(state.isUnlocked());
        state.activate("btnIndex_c");
        assertEquals(11, state.getInputFrame());
    }

    @Test public void leavingHelpBeforeUnlockDestroysItsInputClip() {
        SourceHelpState state = new SourceHelpState();
        state.open();
        for (int i = 0; i < 5; i++) state.activate("btnIndex_c");
        assertEquals(6, state.getInputFrame());
        state.close(); state.open();
        assertEquals(1, state.getInputFrame());
        state.activate("btnIndex_c");
        assertEquals(2, state.getInputFrame());
    }

    @Test public void unlockedMatchReentersCheatOnItsFirstIndexPress() {
        SourceHelpState state = unlocked();
        assertTrue(state.isCommandVisible());
        assertEquals("", state.getInputText());
        state.close(); state.open();
        assertFalse(state.isCommandVisible());
        assertEquals(1, state.getInputFrame());
        state.activate("btnIndex_c");
        assertTrue(state.isCommandVisible());
        state.resetMatch(); state.open(); state.activate("btnIndex_c");
        assertFalse(state.isUnlocked());
        assertEquals(2, state.getInputFrame());
    }

    @Test public void passwordRequiresExactCaseAndWhitespace() {
        for (String value : new String[]{"Youda", "youda ", " youda"}) {
            SourceHelpState state = exposed();
            type(state, value);
            state.activate("btnIndex_c");
            assertFalse(state.isUnlocked());
            assertEquals(value, state.getInputText());
        }
    }

    @Test public void messageOkRecreatesAnEmptyCommandField() {
        SourceHelpState state = unlocked();
        type(state, "#2dawgz");
        state.showCommandMessage("## 2 dawgz / 2 pirates. No ninja's. ##");
        assertFalse(state.hasInputField());
        assertFalse(state.isInputFocused());
        assertTrue(state.isCommandVisible());
        state.dismissCommandMessage();
        assertNull(state.getCommandMessage());
        assertTrue(state.hasInputField());
        assertEquals("", state.getInputText());
    }

    @Test public void editingPreservesLiteralTextAndRequiresVisibleFocus() {
        SourceHelpState state = exposed();
        assertFalse(state.type('x'));
        type(state, "trmp ");
        state.moveCaret(2); state.type('u');
        assertEquals("trump ", state.getInputText());
        state.moveCaret(6); state.type('\b');
        assertEquals("trump", state.getInputText());
        state.selectAllInput(); state.type('x');
        assertEquals("x", state.getInputText());
        state.hide(); assertFalse(state.type('y'));
        state.show(); state.blurInput(); assertFalse(state.type('z'));
    }

    private static SourceHelpState exposed() {
        SourceHelpState state = new SourceHelpState(); state.open();
        for (int i = 0; i < 10; i++) state.activate("btnIndex_c");
        return state;
    }
    private static SourceHelpState unlocked() {
        SourceHelpState state = exposed(); type(state, "youda");
        state.activate("btnIndex_c"); return state;
    }
    private static void type(SourceHelpState state, String text) {
        assertTrue(state.focusInput());
        for (int i = 0; i < text.length(); i++) assertTrue(state.type(text.charAt(i)));
    }
}
