package com.example.worlddomination.game.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ScrollPaneStateTest {
    @Test
    public void panesClampAndScrollIndependently() {
        ScrollPaneState navigation = new ScrollPaneState();
        ScrollPaneState equipment = new ScrollPaneState();
        navigation.setRange(500f, 200f);
        equipment.setRange(700f, 300f);

        navigation.dragBy(-80f);
        assertEquals(80f, navigation.getOffset(), 0.001f);
        assertEquals(0f, equipment.getOffset(), 0.001f);

        equipment.dragBy(-120f);
        assertEquals(80f, navigation.getOffset(), 0.001f);
        assertEquals(120f, equipment.getOffset(), 0.001f);

        navigation.moveBy(1000f);
        assertEquals(300f, navigation.getOffset(), 0.001f);
    }

    @Test
    public void flingDecaysOnFixedTicks() {
        ScrollPaneState pane = new ScrollPaneState();
        pane.setRange(800f, 200f);
        pane.endGesture(-600f);
        pane.tick(1f / 24f);
        assertEquals(25f, pane.getOffset(), 0.001f);
        pane.tick(0.2f);
        assertTrue(pane.getOffset() > 25f);
        assertTrue(pane.getOffset() < 125f);
    }

    @Test
    public void resizingContentClampsOffsetAndStopsOutOfRangeFling() {
        ScrollPaneState pane = new ScrollPaneState();
        pane.setRange(500f, 200f);
        pane.dragBy(-250f);
        assertEquals(250f, pane.getOffset(), 0.001f);
        pane.endGesture(-900f);

        pane.setRange(230f, 200f);
        assertEquals(30f, pane.getLimit(), 0.001f);
        assertEquals(30f, pane.getOffset(), 0.001f);
        pane.tick(0.5f);
        assertEquals(30f, pane.getOffset(), 0.001f);
    }

    @Test
    public void shortContentDisablesScrollingAndClampsBothDirections() {
        ScrollPaneState pane = new ScrollPaneState();
        pane.setRange(100f, 200f);
        pane.dragBy(-80f);
        assertEquals(0f, pane.getLimit(), 0.001f);
        assertEquals(0f, pane.getOffset(), 0.001f);

        pane.setRange(500f, 200f);
        pane.dragBy(75f);
        assertEquals(0f, pane.getOffset(), 0.001f);
        pane.dragBy(-500f);
        assertEquals(pane.getLimit(), pane.getOffset(), 0.001f);
    }

    @Test
    public void flingStopsAtTheEndOfTheScrollRange() {
        ScrollPaneState pane = new ScrollPaneState();
        pane.setRange(500f, 200f);
        pane.endGesture(-1200f);

        pane.tick(2f);
        assertEquals(pane.getLimit(), pane.getOffset(), 0.001f);
        pane.tick(1f);
        assertEquals(pane.getLimit(), pane.getOffset(), 0.001f);
    }

    @Test
    public void zeroAndNegativeTicksDoNotAdvanceTheFling() {
        ScrollPaneState pane = new ScrollPaneState();
        pane.setRange(500f, 200f);
        pane.endGesture(-240f);

        pane.tick(0f);
        pane.tick(-0.1f);
        assertEquals(0f, pane.getOffset(), 0.001f);

        pane.tick(1f / 24f);
        assertTrue(pane.getOffset() > 0f);
    }

    @Test
    public void directDragStopsAnExistingFling() {
        ScrollPaneState pane = new ScrollPaneState();
        pane.setRange(800f, 200f);
        pane.endGesture(-600f);
        pane.tick(0.1f);
        assertTrue(pane.getOffset() > 0f);

        pane.dragBy(-15f);
        float draggedOffset = pane.getOffset();
        pane.tick(0.5f);

        assertEquals(draggedOffset, pane.getOffset(), 0.001f);
    }
}
