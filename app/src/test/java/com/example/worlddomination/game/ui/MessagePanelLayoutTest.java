package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void spareViewportPixelsDoNotMakeTheFourthRowVisibleOrUnreachable() {
        Rectangle viewport = new Rectangle();
        MessagePanelLayout.updateViewport(new Rectangle(0, 0, 340, 64), viewport);
        // The packaged Arial font has a 55-pixel pitch and a 35-pixel cap height;
        // the HUD scales its capitals to 8.5 logical points.
        float pitch = 55f * 8.5f / 35f;
        int visible = MessagePanelLayout.visibleLineCount(viewport.height, pitch);
        assertEquals(3, visible);
        assertTrue(viewport.height - visible * pitch > 5f);

        MessageHistoryScrollState scroll = new MessageHistoryScrollState();
        scroll.setLineRange(4, visible, true);
        assertEquals(2, scroll.getMaxScroll());
        assertTrue(scroll.scrollBy(1));
        assertEquals(4, scroll.getFirstVisibleLine() + visible);
        assertEquals(2, MessagePanelLayout.visibleLineCount(pitch * 3 - .01f, pitch));
        assertEquals(3, MessagePanelLayout.visibleLineCount(pitch * 3, pitch));
    }
}
