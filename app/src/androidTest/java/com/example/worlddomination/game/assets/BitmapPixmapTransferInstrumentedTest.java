package com.example.worlddomination.game.assets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compares the actual Android Bitmap transfer with the previous byte-wise algorithm. */
@RunWith(AndroidJUnit4.class)
public final class BitmapPixmapTransferInstrumentedTest {
    @Test public void exactTransferForPremultipliedAndUnpremultipliedPixels() {
        GdxNativesLoader.load();
        int[] colors = {
                0x00000000, 0x00ffffff, 0x0100ff80, 0x017fff00,
                0x7f123456, 0x8000ff01, 0xfeff0080, 0xffffffff,
                0xff000000, 0xffff0000, 0xff00ff00, 0xff0000ff,
                0x40010203, 0x80abcdef, 0xfe0102fd, 0x01010101
        };
        for (boolean premultiplied : new boolean[]{true, false}) {
            Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            try {
                bitmap.setPremultiplied(premultiplied);
                bitmap.setPixels(colors, 0, 4, 0, 0, 4, 4);
                assertTransfer(bitmap, "premultiplied=" + premultiplied);
            } finally {
                bitmap.recycle();
            }
        }
    }

    @Test public void exactTransferForPackagedOriginalBitmap() throws Exception {
        GdxNativesLoader.load();
        Bitmap bitmap;
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getAssets().open("original/ui/navigation-panel.png")) {
            bitmap = BitmapFactory.decodeStream(input);
        }
        assertNotNull("Packaged source-derived navigation artwork decodes", bitmap);
        try {
            assertEquals(180, bitmap.getWidth());
            assertEquals(301, bitmap.getHeight());
            assertTransfer(bitmap, "source navigation-panel.png");
        } finally {
            bitmap.recycle();
        }
    }

    private static void assertTransfer(Bitmap bitmap, String label) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);
        Pixmap legacy = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        Pixmap actual = null;
        try {
            ByteBuffer expected = legacy.getPixels();
            for (int pixel : argb) {
                expected.put((byte) (pixel >>> 16));
                expected.put((byte) (pixel >>> 8));
                expected.put((byte) pixel);
                expected.put((byte) (pixel >>> 24));
            }
            expected.position(0);
            actual = OriginalFlashAnimationAssets.copyBitmapToPixmap(bitmap);
            assertEquals(label + " width", width, actual.getWidth());
            assertEquals(label + " height", height, actual.getHeight());
            ByteBuffer received = actual.getPixels();
            assertEquals(label + " initial buffer position", 0, received.position());
            assertEquals(label + " bytes", expected.capacity(), received.capacity());
            for (int i = 0; i < expected.capacity(); i++) {
                assertEquals(label + " byte " + i, expected.get(i), received.get(i));
            }
        } finally {
            legacy.dispose();
            if (actual != null) actual.dispose();
        }
    }
}
