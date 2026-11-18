package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;
import static org.junit.Assert.*;

/** Geometry expectations come from the independent source field/control capture. */
public final class SourceHiddenHelpRendererTest {
    @Test public void authoredStageFieldsShareTheHelpCropAtTwoTimesScale() {
        Rectangle container = new Rectangle(100, 40, 730, 550), result = new Rectangle();
        assertRect(SourceHiddenHelpRenderer.commandBounds(container, result), 144.1f, 202.9f, 636, 75);
        assertRect(SourceHiddenHelpRenderer.passwordBounds(container, result), 136.3f, 538.1f, 358.9f, 43.8f);
        assertRect(SourceHiddenHelpRenderer.buttonBounds(container, result), 412, 45.1f, 110, 48);
        assertRect(SourceHiddenHelpRenderer.warningOkBounds(container, result), 420.4348f, 253.6324f, 73.1794f, 31.9328f);
        assertRect(SourceHiddenHelpRenderer.warningTextBounds(container, result), 314.1f, 294.3f, 280.8f, 113.6f);
        assertRect(SourceHiddenHelpRenderer.warningHeaderBounds(container, result), 299.9f, 421.3f, 98.4f, 28.6f);
        assertRect(container, 100, 40, 730, 550);
    }

    @Test public void tallContainersLetterboxWithoutStretchingInputOrButton() {
        Rectangle container = new Rectangle(0, 0, 365, 550), result = new Rectangle();
        assertRect(SourceHiddenHelpRenderer.fit(container, result), 0, 137.5f, 365, 275);
        assertRect(SourceHiddenHelpRenderer.commandBounds(container, result), 22.05f, 218.95f, 318, 37.5f);
        assertRect(SourceHiddenHelpRenderer.buttonBounds(container, result), 156, 140.05f, 55, 24);
    }

    @Test public void dynamicRasterClipsPreserveSourceCeiling() {
        Rectangle container = new Rectangle(0, 0, 365, 275), result = new Rectangle();
        assertRect(SourceHiddenHelpRenderer.commandTextClipBounds(container, result), 22.05f, 80.95f, 318, 38);
        assertRect(SourceHiddenHelpRenderer.passwordTextClipBounds(container, result), 18.15f, 248.95f, 180, 22);
        assertRect(SourceHiddenHelpRenderer.warningTextClipBounds(container, result), 107.05f, 126.95f, 141, 57);
        assertRect(SourceHiddenHelpRenderer.warningHeaderTextClipBounds(container, result), 99.95f, 189.95f, 50, 15);
        assertEquals(30, SourceHiddenHelpRenderer.COMMAND_FONT_SIZE, 0f);
        assertEquals(16, SourceHiddenHelpRenderer.PASSWORD_FONT_SIZE, 0f);
        assertEquals(0xff7800ff, SourceHiddenHelpRenderer.COMMAND_RGBA);
    }

    @Test public void emptyDestinationHasNoInteractiveExtent() {
        Rectangle result = SourceHiddenHelpRenderer.commandBounds(new Rectangle(9, 7, 0, 300), new Rectangle());
        assertRect(result, 9, 7, 0, 0);
    }

    private static void assertRect(Rectangle rectangle, float x, float y, float width, float height) {
        assertEquals(x, rectangle.x, .001f); assertEquals(y, rectangle.y, .001f);
        assertEquals(width, rectangle.width, .001f); assertEquals(height, rectangle.height, .001f);
    }
}
