package com.example.worlddomination.game.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MessageHistoryScrollStateTest {
    @Test
    public void usesFlashOneBasedMaxScrollAndStartsAtTheFirstLine() {
        MessageHistoryScrollState state = new MessageHistoryScrollState();

        state.setLineRange(20, 4, true);

        assertEquals(17, state.getMaxScroll());
        assertEquals(1, state.getScroll());
        assertEquals(0, state.getFirstVisibleLine());
    }

    @Test
    public void upAndDownMovesOneLineAndClampAtBothEnds() {
        MessageHistoryScrollState state = new MessageHistoryScrollState();
        state.setLineRange(8, 3, true);

        assertFalse(state.scrollBy(-1));
        assertTrue(state.scrollBy(1));
        assertEquals(2, state.getScroll());
        assertTrue(state.scrollTo(999));
        assertEquals(state.getMaxScroll(), state.getScroll());
        assertFalse(state.scrollBy(1));
        assertTrue(state.scrollBy(-1));
        assertEquals(state.getMaxScroll() - 1, state.getScroll());
    }

    @Test
    public void contentAndViewportChangesClampOrResetThePosition() {
        MessageHistoryScrollState state = new MessageHistoryScrollState();
        state.setLineRange(30, 4, true);
        state.scrollTo(20);

        state.setLineRange(10, 4, false);
        assertEquals(7, state.getMaxScroll());
        assertEquals(7, state.getScroll());

        state.setLineRange(10, 12, false);
        assertEquals(1, state.getMaxScroll());
        assertEquals(1, state.getScroll());

        state.setLineRange(25, 4, true);
        assertEquals(1, state.getScroll());
    }

    @Test
    public void emptyContentStillHasAValidTopPosition() {
        MessageHistoryScrollState state = new MessageHistoryScrollState();

        state.setLineRange(0, 0, true);

        assertEquals(1, state.getMaxScroll());
        assertEquals(1, state.getScroll());
    }

    @Test
    public void upwardDragRevealsLaterLinesAndDownwardDragReturnsToEarlierLines() {
        MessageHistoryScrollState state = new MessageHistoryScrollState();
        state.setLineRange(12, 3, true);

        assertTrue(state.scrollFromDrag(1, 14f, 12f));
        assertEquals(2, state.getScroll());
        assertTrue(state.scrollFromDrag(2, -14f, 12f));
        assertEquals(1, state.getScroll());
        assertFalse(state.scrollFromDrag(1, 20f, 0f));
    }
}
