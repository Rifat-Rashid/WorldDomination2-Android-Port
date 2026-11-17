package com.example.worlddomination.game.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** The expected carousel is one source crop decoded independently from shape 5813. */
@RunWith(AndroidJUnit4.class)
public final class SourceSetupCarouselInstrumentedTest {
    private static final int[][] SIZES = {{800, 600}, {1194, 552}};

    @Test public void cropAndArrowsMatchSourceAtBothSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Field batchField = WorldDominationGame.class.getDeclaredField("batch");
            batchField.setAccessible(true);
            SpriteBatch batch = (SpriteBatch) batchField.get(app);
            Field assetsField = WorldDominationGame.class.getDeclaredField("flashAnimationAssets");
            assetsField.setAccessible(true);
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) assetsField.get(app);
            Method arrow = WorldDominationGame.class.getDeclaredMethod(
                    "drawSourceCarouselArrow", Rectangle.class, int.class);
            arrow.setAccessible(true);
            Field meshVerticesField = SourceSetupPanelRenderer.class.getDeclaredField(
                    "carouselVertices");
            meshVerticesField.setAccessible(true);
            Field textureField = SourceSetupPanelRenderer.class.getDeclaredField("texture");
            textureField.setAccessible(true);
            SourceSetupPanelRenderer renderer = new SourceSetupPanelRenderer();
            Texture source = decodedSource();
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            try {
                for (int id : new int[]{5819, 5822}) {
                    assertNotNull(assets.getShape(id));
                    assertEquals(12, assets.getShape(id).getRegionWidth());
                    assertEquals(44, assets.getShape(id).getRegionHeight());
                    assertEquals(0f, assets.getShapeOriginX(id), 0f);
                    assertEquals(0f, assets.getShapeOriginY(id), 0f);
                }
                for (int[] size : SIZES) {
                    int w = size[0], h = size[1];
                    // The 800-stage case is exact source identity. The phone case
                    // scales and translates all four carousel rectangles uniformly.
                    float scale = w == 800 ? 1f : 1.3f;
                    float dx = w == 800 ? 0f : 100f;
                    float dy = w == 800 ? 0f : 20f;
                    Rectangle frame = place(147, 101, 488, 180, scale, dx, dy);
                    Rectangle[] wells = new Rectangle[6];
                    for (int i = 0; i < wells.length; i++)
                        wells[i] = place(173 + 74 * i, 191, 64, 70, scale, dx, dy);
                    Rectangle inset = place(176, 123, 430, 49, scale, dx, dy);
                    // Arrows are independent source shapes, placed outside the crop
                    // so they cannot mask any source-5813 pixel discrepancies.
                    Rectangle previous = place(120, 198, 12, 44, scale, dx, dy);
                    Rectangle next = place(650, 198, 12, 44, scale, dx, dy);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                        fb.begin(); clear(); batch.begin(); batch.setColor(.2f, .4f, .6f, .8f);
                        float saved = batch.getPackedColor();
                        renderer.drawCarousel(batch, frame, wells, inset);
                        arrow.invoke(app, previous, 5819);
                        arrow.invoke(app, next, 5822);
                        assertEquals(saved, batch.getPackedColor(), 0f);
                        batch.end();
                        byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        byte[] expected = reference(fb, batch, source, assets,
                                frame, previous, next, w, h, false);
                        byte[] wrong = reference(fb, batch, source, assets,
                                frame, previous, next, w, h, true);
                        String prefix = "source-setup-carousel-" + w + "x" + h;
                        evidence(prefix + "-actual.png", actual, w, h);
                        evidence(prefix + "-expected.png", expected, w, h);
                        evidence(prefix + "-wrong.png", wrong, w, h);
                        assertArrayEquals("one decoded source crop", expected, actual);
                        if (w == 800) {
                            // drawCarousel may use one quad for uniform geometry.
                            // Exercise its cached production mesh against the same
                            // independent source crop at exact native coordinates.
                            float[] meshVertices = (float[]) meshVerticesField.get(renderer);
                            Texture meshTexture = (Texture) textureField.get(renderer);
                            assertTrue("cached carousel mesh", meshVertices.length > 20);
                            fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
                            batch.draw(meshTexture, meshVertices, 0, meshVertices.length);
                            arrow.invoke(app, previous, 5819);
                            arrow.invoke(app, next, 5822);
                            batch.end();
                            byte[] meshActual = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                            fb.end();
                            evidence("source-setup-carousel-native-mesh-actual.png",
                                    meshActual, w, h);
                            evidence("source-setup-carousel-native-mesh-expected.png",
                                    expected, w, h);
                            assertArrayEquals("native cached mesh versus decoded source crop",
                                    expected, meshActual);
                        }
                        assertInteriorPainted(actual, frame, w, h);
                        assertInteriorDifference(expected, wrong, frame, w, h);
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    } finally { fb.dispose(); }
                }
            } finally {
                batch.setProjectionMatrix(oldProjection);
                batch.setPackedColor(oldColor);
                source.dispose();
                renderer.dispose();
            }
        });
    }

    @Test public void responsiveLayoutsPaintContinuousCarousel() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Field batchField = WorldDominationGame.class.getDeclaredField("batch");
            batchField.setAccessible(true);
            SpriteBatch batch = (SpriteBatch) batchField.get(app);
            SourceSetupPanelRenderer renderer = new SourceSetupPanelRenderer();
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            try {
                for (int[] size : SIZES) {
                    int w = size[0], h = size[1];
                    SetupScreenLayout layout = new SetupScreenLayout();
                    layout.update(w, h, 0, 0, 0, 0);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                        fb.begin(); clear(); batch.begin();
                        renderer.drawCarousel(batch, layout.leaderCarouselPanel,
                                layout.leaderCards, layout.leaderDescription);
                        batch.end();
                        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                        fb.end();
                        evidence("source-setup-carousel-responsive-" + w + "x" + h + ".png",
                                pixels, w, h);
                        assertInteriorPainted(pixels, layout.leaderCarouselPanel, w, h);
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    } finally { fb.dispose(); }
                }
            } finally {
                batch.setProjectionMatrix(oldProjection);
                batch.setPackedColor(oldColor);
                renderer.dispose();
            }
        });
    }

    private static Texture decodedSource() throws Exception {
        byte[] svg;
        try (InputStream zipped = new GZIPInputStream(Gdx.files.internal(
                "original/runtime/shapes/5813.svgz").read());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = zipped.read(buffer)) != -1) out.write(buffer, 0, count);
            svg = out.toByteArray();
        }
        Matcher embedded = Pattern.compile("data:image/webp;base64,([^\"]+)")
                .matcher(new String(svg, StandardCharsets.UTF_8));
        assertTrue("shape 5813 embedded WebP", embedded.find());
        String encoded = embedded.group(1);
        assertFalse("shape 5813 has one bitmap fill", embedded.find());
        byte[] webp = Base64.decode(encoded, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(webp, 0, webp.length);
        assertNotNull("decoded embedded WebP", bitmap);
        assertEquals(798, bitmap.getWidth());
        assertEquals(601, bitmap.getHeight());
        Pixmap pixmap = new Pixmap(bitmap.getWidth(), bitmap.getHeight(), Pixmap.Format.RGBA8888);
        for (int y = 0; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int argb = bitmap.getPixel(x, y);
                pixmap.drawPixel(x, y, (argb << 8) | (argb >>> 24));
            }
        bitmap.recycle();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static byte[] reference(FrameBuffer fb, SpriteBatch batch, Texture source,
            OriginalFlashAnimationAssets assets, Rectangle frame, Rectangle previous,
            Rectangle next, int w, int h, boolean oldComposition) {
        fb.begin(); clear(); batch.begin(); batch.setColor(1, 1, 1, 1);
        if (oldComposition) oldStripAndOverlays(batch, source, frame);
        else batch.draw(new TextureRegion(source, 147, 319, 488, 180),
                frame.x, frame.y, frame.width, frame.height);
        drawArrow(batch, assets, previous, 5819);
        drawArrow(batch, assets, next, 5822);
        batch.end();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        fb.end();
        return pixels;
    }

    // Negative control: the retired clear-metal strip and separately overlaid wells.
    // Arrows and geometry match the correct reference, isolating this composition.
    private static void oldStripAndOverlays(SpriteBatch batch, Texture source, Rectangle frame) {
        float sx = frame.width / 488f, sy = frame.height / 180f;
        float left = 25f * sx, cap = 12f * sy;
        int[] xx = {147, 172, 610}, ww = {25, 438, 25};
        int[] yy = {319, 331, 487}, hh = {12, 156, 12};
        float[] dx = {frame.x, frame.x + left, frame.x + frame.width - left};
        float[] dy = {frame.y + frame.height - cap, frame.y + cap, frame.y};
        float[] dw = {left, frame.width - 2f * left, left};
        float[] dh = {cap, frame.height - 2f * cap, cap};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            TextureRegion part = row == 1 && col == 1
                    ? new TextureRegion(source, 250, 414, 300, 10)
                    : new TextureRegion(source, xx[col], yy[row], ww[col], hh[row]);
            batch.draw(part, dx[col], dy[row], dw[col], dh[row]);
        }
        for (int i = 0; i < 6; i++)
            batch.draw(new TextureRegion(source, 173 + 74 * i, 339, 64, 70),
                    frame.x + (26 + 74 * i) * sx, frame.y + 90f * sy,
                    64f * sx, 70f * sy);
        batch.draw(new TextureRegion(source, 176, 428, 430, 49),
                frame.x + 29f * sx, frame.y + 22f * sy, 430f * sx, 49f * sy);
    }

    private static void drawArrow(SpriteBatch batch, OriginalFlashAnimationAssets assets,
            Rectangle target, int id) {
        float scale = Math.min(target.width / 12f, target.height / 44f);
        batch.draw(assets.getShape(id), target.x + (target.width - 12f * scale) / 2f,
                target.y + (target.height - 44f * scale) / 2f, 12f * scale, 44f * scale);
    }

    private static Rectangle place(float x, float y, float width, float height,
            float scale, float dx, float dy) {
        return new Rectangle(dx + x * scale, dy + y * scale, width * scale, height * scale);
    }

    private static void assertInteriorPainted(byte[] pixels, Rectangle frame, int w, int h) {
        int left = (int)Math.ceil(frame.x + 1f);
        int right = (int)Math.floor(frame.x + frame.width - 1f);
        int bottom = (int)Math.ceil(frame.y + 1f);
        int top = (int)Math.floor(frame.y + frame.height - 1f);
        assertTrue(right > left && top > bottom);
        for (int y = bottom; y < top; y++) for (int x = left; x < right; x++) {
            assertEquals("incomplete carousel coverage at " + x + "," + y,
                    255, pixels[((h - 1 - y) * w + x) * 4 + 3] & 255);
        }
    }

    private static void assertInteriorDifference(byte[] expected, byte[] wrong,
            Rectangle frame, int w, int h) {
        int changed = 0;
        for (int y = (int)frame.y + 20; y < frame.y + frame.height - 20; y++)
            for (int x = (int)frame.x + 30; x < frame.x + frame.width - 30; x++) {
                int k = ((h - 1 - y) * w + x) * 4;
                if (expected[k] != wrong[k] || expected[k + 1] != wrong[k + 1]
                        || expected[k + 2] != wrong[k + 2] || expected[k + 3] != wrong[k + 3])
                    changed++;
            }
        assertTrue("retired clear strip must differ inside the frame", changed > 0);
    }

    private static void clear() {
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private static void evidence(String name, byte[] bytes, int w, int h) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);
        assertTrue(dir.exists() || dir.mkdirs());
        Pixmap image = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(bytes);
            image.getPixels().position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }
}
