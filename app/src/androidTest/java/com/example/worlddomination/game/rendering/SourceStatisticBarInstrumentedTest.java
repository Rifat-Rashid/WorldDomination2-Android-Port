package com.example.worlddomination.game.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual World bar raster versus independent Pygame RGBA strip captures. */
@RunWith(AndroidJUnit4.class)
public final class SourceStatisticBarInstrumentedTest {
    private static final String FIXTURE = "source-statistic-bars/";

    @Test public void allFiftyFourBarsMatchPhoneStripsAndRejectEightBands() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            JSONObject fixture = new JSONObject(new String(testAsset(FIXTURE + "cases.json"),
                    java.nio.charset.StandardCharsets.UTF_8));
            JSONArray dimensions = fixture.getJSONArray("imageSize");
            int w = dimensions.getInt(0), h = dimensions.getInt(1);
            assertEquals(256, w);
            assertEquals(464, h);
            JSONArray cases = fixture.getJSONArray("cases");
            assertEquals(54, cases.length());
            byte[] expected = decodedPng(testAsset(FIXTURE + "expected.png"), w, h);
            ShapeRenderer shapes = (ShapeRenderer) field("shapes").get(app);
            Method draw = WorldDominationGame.class.getDeclaredMethod(
                    "drawInformationBar", float.class, float.class, double.class, double.class);
            draw.setAccessible(true);
            Matrix4 oldProjection = new Matrix4(shapes.getProjectionMatrix());
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            try {
                shapes.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                fb.begin(); clear(); shapes.begin(ShapeRenderer.ShapeType.Filled);
                try {
                    for (int i = 0; i < cases.length(); i++) {
                        JSONObject item = cases.getJSONObject(i);
                        draw.invoke(app, 16.75f, 16.75f + i * 8,
                                item.getDouble("width"), item.getInt("value"));
                    }
                } finally { shapes.end(); }
                byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                fb.end();
                fb.begin(); clear(); shapes.begin(ShapeRenderer.ShapeType.Filled);
                try {
                    for (int i = 0; i < cases.length(); i++) {
                        JSONObject item = cases.getJSONObject(i);
                        legacyEightBands(shapes, 16.75f, 16 + i * 8,
                                (float)item.getDouble("width"), item.getInt("value"));
                    }
                } finally { shapes.end(); }
                byte[] wrong = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                fb.end();
                evidence("source-statistic-bars-actual.png", actual, w, h);
                evidence("source-statistic-bars-expected.png", expected, w, h);
                evidence("source-statistic-bars-eight-band.png", wrong, w, h);
                assertArrayEquals("all 54 source RGBA strips, including transparent margins",
                        expected, actual);
                int prematureYellow = 0;
                for (int i = 0; i < cases.length(); i++) {
                    if (cases.getJSONObject(i).getInt("value") != 25) continue;
                    int y = 16 + i * 8 + 3;
                    int start = ((h - 1 - y) * w + 16) * 4;
                    int limit = ((h - 1 - y) * w + 160) * 4;
                    for (int k = start; k < limit; k += 4)
                        if ((expected[k] & 255) == 255 && (wrong[k] & 255) == 255
                                && (wrong[k + 1] & 255) > (expected[k + 1] & 255) + 50)
                            prematureYellow++;
                }
                assertTrue("legacy eight-band gradient turns yellow too soon at 25%",
                        prematureYellow > 0);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                fb.dispose();
                shapes.setProjectionMatrix(oldProjection);
            }
        });
    }

    @Test public void zeroFillDoesNotPaintLegacyFractionalSliver() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            ShapeRenderer shapes = (ShapeRenderer) field("shapes").get(app);
            Method draw = WorldDominationGame.class.getDeclaredMethod(
                    "drawInformationBar", float.class, float.class, double.class, double.class);
            draw.setAccessible(true);
            Matrix4 oldProjection = new Matrix4(shapes.getProjectionMatrix());
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, 32, 16, false);
            try {
                shapes.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 32, 16));
                fb.begin(); clear(); shapes.begin(ShapeRenderer.ShapeType.Filled);
                try { draw.invoke(app, 16.4f, 4f, 10d, 0); }
                finally { shapes.end(); }
                byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, 32, 16, true);
                fb.end();
                fb.begin(); clear(); shapes.begin(ShapeRenderer.ShapeType.Filled);
                try { legacyEightBands(shapes, 16.4f, 4f, 10f, 0); }
                finally { shapes.end(); }
                byte[] old = ScreenUtils.getFrameBufferPixels(0, 0, 32, 16, true);
                fb.end();
                int pixel = ((15 - 6) * 32 + 16) * 4;
                assertEquals("zero fill retains dark track red", 5, actual[pixel] & 255);
                assertEquals("zero fill retains dark track green", 13, actual[pixel + 1] & 255);
                assertEquals("zero fill retains dark track blue", 17, actual[pixel + 2] & 255);
                assertEquals("legacy sliver is filled red, not just a different track",
                        255, old[pixel] & 255);
                assertTrue("retired +0.2 band paints a zero-value sliver",
                        old[pixel] != actual[pixel] || old[pixel + 1] != actual[pixel + 1]
                                || old[pixel + 2] != actual[pixel + 2]);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                fb.dispose();
                shapes.setProjectionMatrix(oldProjection);
            }
        });
    }

    private static void legacyEightBands(ShapeRenderer shapes, float x, float y,
            float width, int statistic) {
        int value = Math.max(0, Math.min(100, statistic));
        shapes.setColor(.02f, .055f, .07f, 1f);
        shapes.rect(x, y, width, 6f);
        float filledWidth = width * value / 100f;
        for (int band = 0; band < 8; band++) {
            float left = x + filledWidth * band / 8f;
            float segment = filledWidth / 8f + .2f;
            float blend = band / 7f;
            shapes.setColor(1f, .19f + blend * .68f, .02f + blend * .01f, 1f);
            shapes.rect(left, y, segment, 6f);
        }
    }

    private static byte[] testAsset(String path) throws Exception {
        try (InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static byte[] decodedPng(byte[] encoded, int w, int h) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
        assertNotNull(bitmap);
        assertEquals(w, bitmap.getWidth());
        assertEquals(h, bitmap.getHeight());
        byte[] result = new byte[w * h * 4];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = bitmap.getPixel(x, y);
            int at = (y * w + x) * 4;
            result[at] = (byte)(argb >>> 16);
            result[at + 1] = (byte)(argb >>> 8);
            result[at + 2] = (byte)argb;
            result[at + 3] = (byte)(argb >>> 24);
        }
        bitmap.recycle();
        return result;
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void clear() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private static void evidence(String name, byte[] bytes, int w, int h) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(bytes).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }
}
