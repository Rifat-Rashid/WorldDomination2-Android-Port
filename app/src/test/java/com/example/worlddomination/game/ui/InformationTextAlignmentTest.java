package com.example.worlddomination.game.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class InformationTextAlignmentTest {
    @Test
    public void capitalsAndDescendersAreCenteredByTheirRenderedInk() {
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
        data.ascent = 1.8f;
        data.scaleY = 0.2f;
        for (char c : "FUNDS$65.0technolgypradsu".toCharArray()) {
            BitmapFont.Glyph glyph = new BitmapFont.Glyph();
            glyph.height = Character.isUpperCase(c) || "lthd".indexOf(c) >= 0 ? 35 : 26;
            glyph.yoffset = -44;
            if ("gpy".indexOf(c) >= 0) {
                glyph.height = 36;
                glyph.yoffset = -54;
            }
            data.setGlyph(c, glyph);
        }
        assertEquals(43.5f, InformationTextAlignment.drawY(data, "FUNDS", 40f), 0.001f);
        assertEquals(44.5f, InformationTextAlignment.drawY(data, "technology", 40f), 0.001f);
        assertEquals(45.4f, InformationTextAlignment.drawY(data, "spy", 40f), 0.001f);
    }

    @Test
    public void fiveRowBlockAndBarsRemainSymmetricInsideTheirFrame() {
        Rectangle panel = new Rectangle(15f, 10f, 190f, 82f);
        float top = InformationPanelLayout.rowCenter(panel, 0);
        float bottom = InformationPanelLayout.rowCenter(panel, 4);
        assertEquals(panel.y + panel.height / 2f, (top + bottom) / 2f, 0.001f);
        for (int row = 1; row < 5; row++) {
            assertEquals(InformationPanelLayout.rowCenter(panel, row),
                    InformationPanelLayout.barY(panel, row) + InformationPanelLayout.BAR_HEIGHT / 2f,
                    0.001f);
        }
    }
}
