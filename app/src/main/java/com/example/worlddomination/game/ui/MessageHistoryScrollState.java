package com.example.worlddomination.game.ui;

/** One-based scroll position for the source game's wrapped short-message field. */
public final class MessageHistoryScrollState {
    private int scroll = 1;
    private int maxScroll = 1;

    public int getScroll() {
        return scroll;
    }

    public int getMaxScroll() {
        return maxScroll;
    }

    /** First wrapped line currently visible, using a zero-based line index. */
    public int getFirstVisibleLine() {
        return scroll - 1;
    }

    /** Updates the range using Flash TextField's one-based maxscroll convention. */
    public void setLineRange(int totalLines, int visibleLines, boolean resetToTop) {
        int safeVisibleLines = Math.max(1, visibleLines);
        maxScroll = Math.max(1, Math.max(0, totalLines) - safeVisibleLines + 1);
        scroll = resetToTop ? 1 : clamp(scroll, 1, maxScroll);
    }

    /** Sets a requested source-style scroll position, clamped to the current range. */
    public boolean scrollTo(int requestedScroll) {
        int next = clamp(requestedScroll, 1, maxScroll);
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    /** Moves by wrapped lines; positive values reveal later messages. */
    public boolean scrollBy(int lines) {
        long requested = (long) scroll + lines;
        int next = (int) Math.max(1L, Math.min((long) maxScroll, requested));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    /** Maps an upward finger drag to later wrapped lines from its initial position. */
    public boolean scrollFromDrag(int gestureStartScroll, float fingerDeltaY,
                                  float lineHeight) {
        if (!(lineHeight > 0f) || Float.isNaN(fingerDeltaY)
                || Float.isInfinite(fingerDeltaY)) {
            return false;
        }
        long lineDelta = Math.round(fingerDeltaY / lineHeight);
        long requested = (long) gestureStartScroll + lineDelta;
        int next = (int) Math.max(1L, Math.min((long) maxScroll, requested));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
