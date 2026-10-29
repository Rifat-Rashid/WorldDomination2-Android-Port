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
}
