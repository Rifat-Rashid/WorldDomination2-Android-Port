package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public final class MessagePanelLayoutTest {
    @Test
    public void wrappedMessagesStayInsideTheRecessedWellAcrossLandscapeWidths() {
        for (float width : new float[]{225f, 340f, 465f}) {
            Rectangle panel = new Rectangle(315f, 5f, width, 64f);
            Rectangle viewport = new Rectangle();
            MessagePanelLayout.updateViewport(panel, viewport);
            Rectangle well = new Rectangle(panel.x + 6f, panel.y + 6f,
                    panel.width - 12f, panel.height - 12f);
            assertTrue(well.contains(viewport));
            assertTrue("Three reference-size message rows should fit", viewport.height >= 40f);
            assertTrue(viewport.width >= 200f);
        }
    }
}
