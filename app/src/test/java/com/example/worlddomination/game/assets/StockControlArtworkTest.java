package com.example.worlddomination.game.assets;

import com.example.worlddomination.game.ui.SourceStockControlState;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source switch frames and counter have distinct, correctly sized raster assets. */
public final class StockControlArtworkTest {
    @Test public void sourceFrameMappingAndPackagedShapes() throws Exception {
        assertEquals(0, SourceStockControlState.switchIndex(SourceStockControlState.UNAVAILABLE));
        assertEquals(1, SourceStockControlState.switchIndex(SourceStockControlState.ELIGIBLE));
        assertEquals(2, SourceStockControlState.switchIndex(SourceStockControlState.SELECTED));
        assertEquals(0, SourceStockControlState.switchIndex(SourceStockControlState.INACTIVE));
        byte[] counter = image("counter");
        byte[] unavailable = image("unavailable");
        byte[] eligible = image("eligible");
        byte[] selected = image("selected");
        assertDimensions(counter, 96, 78);
        for (byte[] switchImage : new byte[][]{unavailable, eligible, selected})
            assertDimensions(switchImage, 90, 78);
        assertFalse(Arrays.equals(MessageDigest.getInstance("SHA-256").digest(unavailable),
                MessageDigest.getInstance("SHA-256").digest(eligible)));
        assertFalse(Arrays.equals(MessageDigest.getInstance("SHA-256").digest(eligible),
                MessageDigest.getInstance("SHA-256").digest(selected)));
    }

    private static byte[] image(String name) throws Exception {
        return Files.readAllBytes(Paths.get("src/main/assets/original/ui/stock-controls",
                name + ".png"));
    }

    private static void assertDimensions(byte[] png, int width, int height) {
        assertTrue(png.length > 32);
        assertEquals(0x89, png[0] & 255);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
        assertEquals(width, intAt(png, 16));
        assertEquals(height, intAt(png, 20));
    }

    private static int intAt(byte[] bytes, int position) {
        return ((bytes[position] & 255) << 24) | ((bytes[position + 1] & 255) << 16)
                | ((bytes[position + 2] & 255) << 8) | (bytes[position + 3] & 255);
    }
}
