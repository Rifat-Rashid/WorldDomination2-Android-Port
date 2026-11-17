package com.example.worlddomination.game.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ScrollPaneState;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Eight equipment frames compared with original SVG path and bitmap registration. */
@RunWith(AndroidJUnit4.class)
public final class SourceEquipmentCardsInstrumentedTest {
    private static final int[] IDS = {4503, 4480, 4514, 4525, 4536, 4547, 4558, 4569};
    private static final String[] NAMES = {"rockets", "aircraft", "special", "warheads",
            "bioloads", "infantry", "intelligence", "defense"};
    private static final int[] PNG_HEIGHTS = {73, 42, 117, 69, 79, 69, 69, 48};
    private static final int[] VISIBLE_HEIGHTS = {73, 42, 117, 69, 69, 69, 69, 48};
    private static final int[][] TITLE_X = {{78, 120}, {78, 120}, {60, 138}, {73, 123},
            {74, 124}, {72, 125}, {66, 131}, {65, 132}};

    @Test public void allEightCardsRetainIndependentSvgArtworkAndClip() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch = (SpriteBatch) field("batch").get(app);
            Texture[] packaged = new Texture[IDS.length];
            Texture[] source = new Texture[IDS.length];
            Texture sourceBitmap = null;
            Pixmap flatPixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            flatPixel.drawPixel(0, 0, 0x0c1920ff);
            Texture missingArt = new Texture(flatPixel);
            flatPixel.dispose();
            FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, 256, 700, false);
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            try {
                TextureRegion[] regions = new TextureRegion[IDS.length];
                for (int i = 0; i < IDS.length; i++) {
                    packaged[i] = new Texture(Gdx.files.internal(
                            "original/ui/equipment-cards/" + NAMES[i] + ".png"));
                    packaged[i].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    assertEquals(197, packaged[i].getWidth());
                    assertEquals(PNG_HEIGHTS[i], packaged[i].getHeight());
                    regions[i] = new TextureRegion(packaged[i]);
                    SourceShape decoded = decodeSourceShape(IDS[i], PNG_HEIGHTS[i]);
                    source[i] = decoded.clipped;
                    if (sourceBitmap == null) sourceBitmap = decoded.bitmap;
                    else decoded.bitmap.dispose();
                }
                assertNotNull(sourceBitmap);
                assertBioloadBottomTransparent(packaged[4]);
                SourceEquipmentCards renderer = new SourceEquipmentCards(regions);
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 256, 700));
                byte[] actual = atlas(fb, batch, renderer, source, sourceBitmap,
                        missingArt, 0);
                byte[] expected = atlas(fb, batch, renderer, source, sourceBitmap,
                        missingArt, 1);
                byte[] wrongBand = atlas(fb, batch, renderer, source, sourceBitmap,
                        missingArt, 2);
                byte[] missing = atlas(fb, batch, renderer, source, sourceBitmap,
                        missingArt, 3);
                evidence("source-equipment-cards-actual.png", actual, 256, 700);
                evidence("source-equipment-cards-expected.png", expected, 256, 700);
                evidence("source-equipment-cards-wrong-band.png", wrongBand, 256, 700);
                evidence("source-equipment-cards-missing-art.png", missing, 256, 700);
                int top = 20;
                for (int group = 0; group < IDS.length; group++) {
                    int h = VISIBLE_HEIGHTS[group];
                    int sourcePixels = 0, missingDifferences = 0, wrongBandDifferences = 0;
                    int rgbDifferences = 0, rgbAboveOne = 0;
                    int alphaDifferences = 0, aircraftTopQuantization = 0;
                    int maximumRgbDelta = 0, maximumAircraftTopAlphaDelta = 0;
                    for (int sy = 0; sy < h; sy++) for (int sx = 0; sx < 197; sx++) {
                        int y = top + h - 1 - sy, x = 30 + sx;
                        int k = ((699 - y) * 256 + x) * 4;
                        int alphaDelta = Math.abs((actual[k + 3] & 255)
                                - (expected[k + 3] & 255));
                        if (alphaDelta != 0) {
                            // Aircraft shape 4480 starts at fractional source
                            // y70.75. Resvg and Canvas round its top clipped
                            // coverage by one source alpha level (191 vs 192).
                            // SpriteBatch's SRC_ALPHA framebuffer blend squares
                            // that alpha: 191²/255 -> 143, 192²/255 -> 145.
                            // The measured difference is confined to this row.
                            if (group == 1 && sy == 0 && alphaDelta <= 2) {
                                aircraftTopQuantization++;
                                maximumAircraftTopAlphaDelta = Math.max(
                                        maximumAircraftTopAlphaDelta, alphaDelta);
                            } else alphaDifferences++;
                        }
                        // Compare every source pixel outside the exact title
                        // patch, including bolts, borders and clipped edges.
                        if (sy < 14 && sx >= TITLE_X[group][0]
                                && sx < TITLE_X[group][1]) continue;
                        boolean different = false, aboveOne = false;
                        for (int c = 0; c < 3; c++) {
                            int delta = Math.abs((expected[k + c] & 255) - (actual[k + c] & 255));
                            if (delta != 0) different = true;
                            if (delta > 1) aboveOne = true;
                            maximumRgbDelta = Math.max(maximumRgbDelta, delta);
                        }
                        if (different) rgbDifferences++;
                        if (aboveOne) rgbAboveOne++;
                        sourcePixels++;
                        if (expected[k] != missing[k] || expected[k + 1] != missing[k + 1]
                                || expected[k + 2] != missing[k + 2]) missingDifferences++;
                        if (expected[k] != wrongBand[k] || expected[k + 1] != wrongBand[k + 1]
                                || expected[k + 2] != wrongBand[k + 2]) wrongBandDifferences++;
                    }
                    assertTrue("source artwork exercised group " + group, sourcePixels > 1000);
                    assertTrue("missing artwork differs group " + group,
                            missingDifferences > 100);
                    if (group > 0) assertTrue("wrong source band differs group " + group,
                            wrongBandDifferences > 100);
                    assertEquals("SVG path alpha/registration group " + group
                                    + ", aircraft top-row quantized pixels "
                                    + aircraftTopQuantization + ", max delta "
                                    + maximumAircraftTopAlphaDelta,
                            0, alphaDifferences);
                    // The measured integer-registration oracle has exact
                    // alpha and at most one RGB level from Android's Bitmap
                    // premultiply/unpremultiply roundtrip. No larger color
                    // error is accepted, including fractional registrations.
                    assertEquals("source SVG RGB group " + group + " compared " + sourcePixels
                            + " pixels, differing " + rgbDifferences + ", max delta "
                            + maximumRgbDelta, 0, rgbAboveOne);
                    top += h + 8;
                }
                verifyResizedCorners(batch, renderer, source, packaged);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                fb.dispose();
                batch.setProjectionMatrix(oldProjection);
                batch.setPackedColor(oldColor);
                for (Texture texture : packaged) if (texture != null) texture.dispose();
                for (Texture texture : source) if (texture != null) texture.dispose();
                if (sourceBitmap != null) sourceBitmap.dispose();
                missingArt.dispose();
            }
        });
    }

    @Test public void worldTopAndBottomScrollKeepEquipmentGeometryAndClip() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L);
            setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0));
            assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup);
            field("game").set(app, game);
            field("modalOpen").setBoolean(app, false);
            ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
            ScrollPaneState equipment = (ScrollPaneState) field("equipmentScroll").get(app);
            ScrollPaneState navigation = (ScrollPaneState) field("navigationScroll").get(app);
            float navigationOffset = navigation.getOffset();
            field("skipNextFrameDelta").setBoolean(app, true);
            app.render();
            Rectangle viewport = (Rectangle) field("equipmentViewport").get(app);
            Rectangle stock = (Rectangle) field("stockPanel").get(app);
            assertEquals(stock, viewport);
            Rectangle[] cards = (Rectangle[]) field("equipmentCards").get(app);
            Rectangle[] rows = (Rectangle[]) field("equipmentRows").get(app);
            Rectangle[] topCards = copy(cards), topRows = copy(rows);
            float topOffset = equipment.getOffset();
            int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
            byte[] topImage = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
            evidence("source-equipment-cards-world-top.png", topImage, w, h);
            equipment.moveBy(equipment.getLimit());
            float bottomOffset = equipment.getOffset();
            assertTrue("equipment has independent scroll range", bottomOffset > topOffset);
            field("skipNextFrameDelta").setBoolean(app, true);
            app.render();
            byte[] bottomImage = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
            evidence("source-equipment-cards-world-bottom.png", bottomImage, w, h);
            assertEquals("navigation scroll unchanged", navigationOffset,
                    navigation.getOffset(), 0f);
            assertEquals("equipment scroll retained", bottomOffset, equipment.getOffset(), 0f);
            for (int group = 0; group < cards.length; group++) {
                assertEquals(viewport.x + 2f, cards[group].x, 0f);
                assertEquals(viewport.width - 8f, cards[group].width, 0f);
                assertEquals(topCards[group].x, cards[group].x, 0f);
                assertEquals(topCards[group].width, cards[group].width, 0f);
                assertEquals(topCards[group].height, cards[group].height, 0f);
                assertEquals(topCards[group].y + bottomOffset - topOffset,
                        cards[group].y, .01f);
            }
            for (int row = 0; row < rows.length; row++) {
                assertEquals(topRows[row].x, rows[row].x, 0f);
                assertEquals(topRows[row].width, rows[row].width, 0f);
                assertEquals(topRows[row].height, rows[row].height, 0f);
                assertEquals(topRows[row].y + bottomOffset - topOffset,
                        rows[row].y, .01f);
            }
            Rectangle finalCard = cards[cards.length - 1];
            assertEquals("last authored frame reaches viewport bottom at max scroll",
                    viewport.y, finalCard.y, .01f);
            assertTrue("last frame top remains visible",
                    finalCard.y + finalCard.height <= viewport.y + viewport.height + .01f);
            int[] starts = (int[]) field("EQUIPMENT_GROUP_STARTS").get(null);
            int[] counts = (int[]) field("EQUIPMENT_GROUP_COUNTS").get(null);
            int finalGroup = cards.length - 1;
            for (int item = 0; item < counts[finalGroup]; item++) {
                Rectangle row = rows[starts[finalGroup] + item + 1];
                assertTrue("final item row inside equipment viewport",
                        row.x >= viewport.x - .01f && row.x + row.width
                                <= viewport.x + viewport.width + .01f
                                && row.y >= viewport.y - .01f && row.y + row.height
                                <= viewport.y + viewport.height + .01f);
                float controlHeight = Math.min(19f, row.height - 5f);
                float controlY = row.y + (row.height - controlHeight) / 2f;
                float counterWidth = controlHeight * 32f / 26f;
                float switchWidth = controlHeight * 30f / 26f;
                float counterX = row.x + row.width - counterWidth - switchWidth - 5f;
                assertTrue("final counter and switch remain fully visible",
                        counterX >= viewport.x - .01f
                                && counterX + counterWidth + 2f + switchWidth
                                <= viewport.x + viewport.width + .01f
                                && controlY >= viewport.y - .01f
                                && controlY + controlHeight
                                <= viewport.y + viewport.height + .01f);
            }
            Viewport worldViewport = (Viewport) field("viewport").get(app);
            Vector2 lower = worldViewport.project(new Vector2(viewport.x, viewport.y));
            Vector2 upper = worldViewport.project(new Vector2(
                    viewport.x + viewport.width, viewport.y + viewport.height));
            int left = (int)Math.floor(lower.x), right = (int)Math.ceil(upper.x);
            int bottom = (int)Math.floor(lower.y), top = (int)Math.ceil(upper.y);
            int insideChanges = 0, outsideChanges = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int k = ((h - 1 - y) * w + x) * 4;
                if (topImage[k] == bottomImage[k] && topImage[k + 1] == bottomImage[k + 1]
                        && topImage[k + 2] == bottomImage[k + 2]
                        && topImage[k + 3] == bottomImage[k + 3]) continue;
                if (x >= left && x < right && y >= bottom && y < top) insideChanges++;
                else outsideChanges++;
            }
            assertTrue("scroll changes visible card/text/control pixels", insideChanges > 100);
            assertEquals("equipment viewport clips scrolled content", 0, outsideChanges);
            Field rendererField = field("equipmentCardsRenderer");
            SourceEquipmentCards production = (SourceEquipmentCards) rendererField.get(app);
            assertNotNull(production);
            Texture[] blankTextures = new Texture[IDS.length];
            TextureRegion[] blankRegions = new TextureRegion[IDS.length];
            byte[] withoutArt;
            try {
                for (int group = 0; group < IDS.length; group++) {
                    Pixmap empty = new Pixmap(197, PNG_HEIGHTS[group], Pixmap.Format.RGBA8888);
                    empty.setColor(0f, 0f, 0f, 0f);
                    empty.fill();
                    blankTextures[group] = new Texture(empty);
                    blankRegions[group] = new TextureRegion(blankTextures[group]);
                    empty.dispose();
                }
                rendererField.set(app, new SourceEquipmentCards(blankRegions));
                field("skipNextFrameDelta").setBoolean(app, true);
                app.render();
                withoutArt = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
            } finally {
                rendererField.set(app, production);
                for (Texture texture : blankTextures) if (texture != null) texture.dispose();
            }
            evidence("source-equipment-cards-world-missing.png", withoutArt, w, h);
            int artChanges = 0, artOutside = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int k = ((h - 1 - y) * w + x) * 4;
                if (bottomImage[k] == withoutArt[k] && bottomImage[k + 1] == withoutArt[k + 1]
                        && bottomImage[k + 2] == withoutArt[k + 2]
                        && bottomImage[k + 3] == withoutArt[k + 3]) continue;
                if (x >= left && x < right && y >= bottom && y < top) artChanges++;
                else artOutside++;
            }
            assertTrue("source cards contribute visible World pixels", artChanges > 100);
            assertEquals("removing equipment art affects only its clipped viewport", 0, artOutside);
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        });
    }

    private static void verifyResizedCorners(SpriteBatch batch, SourceEquipmentCards renderer,
            Texture[] source, Texture[] packaged) {
        // Independent source corner crops establish shape preservation after
        // nonuniform resizing. The old 8px split is a deliberate negative control.
        final int width = 660, height = 1440, cap = 18;
        FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        Matrix4 previous = new Matrix4(batch.getProjectionMatrix());
        byte[][] captures = new byte[3][];
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
            for (int mode = 0; mode < 3; mode++) {
                fb.begin(); clear(); batch.begin(); batch.setColor(1f, 1f, 1f, 1f);
                for (int group = 0; group < IDS.length; group++) {
                    for (int size = 0; size < 2; size++) {
                        Rectangle target = resizedTarget(group, size);
                        if (mode == 0) renderer.draw(batch, group, target);
                        else if (mode == 1) {
                            for (int cy = 0; cy < 2; cy++) for (int cx = 0; cx < 2; cx++) {
                                int sx = cx == 0 ? 0 : 197 - cap;
                                int sy = cy == 0 ? 0 : VISIBLE_HEIGHTS[group] - cap;
                                float dx = cx == 0 ? target.x : target.x + target.width - cap;
                                float dy = cy == 0 ? target.y + target.height - cap : target.y;
                                batch.draw(new TextureRegion(source[group], sx, sy, cap, cap),
                                        dx, dy, cap, cap);
                            }
                        } else drawLegacyEightPixelSlices(batch, packaged[group], group, target);
                    }
                }
                batch.end();
                captures[mode] = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
                fb.end();
            }
            String[] suffixes = {"resized", "source-corners", "split-fasteners"};
            for (int mode = 0; mode < 3; mode++) evidence(
                    "source-equipment-cards-" + suffixes[mode] + ".png",
                    captures[mode], width, height);
            for (int group = 0; group < IDS.length; group++) for (int size = 0; size < 2; size++) {
                Rectangle target = resizedTarget(group, size);
                int distortedPixels = 0;
                for (int cy = 0; cy < 2; cy++) for (int cx = 0; cx < 2; cx++) {
                    int left = (int) (cx == 0 ? target.x : target.x + target.width - cap);
                    int bottom = (int) (cy == 0 ? target.y + target.height - cap : target.y);
                    for (int y = 0; y < cap; y++) for (int x = 0; x < cap; x++) {
                        int k = ((height - 1 - bottom - y) * width + left + x) * 4;
                        int maxLegacyDelta = 0;
                        for (int c = 0; c < 4; c++) {
                            int delta = Math.abs((captures[0][k + c] & 255)
                                    - (captures[1][k + c] & 255));
                            int tolerance = c < 3 ? 1
                                    : group == 1 && cy == 0 && y == cap - 1 ? 2 : 0;
                            assertTrue("resized source corner group " + group + " size " + size
                                    + " corner " + cx + "," + cy + " pixel " + x + "," + y
                                    + " channel " + c + " delta " + delta, delta <= tolerance);
                            maxLegacyDelta = Math.max(maxLegacyDelta,
                                    Math.abs((captures[2][k + c] & 255)
                                            - (captures[1][k + c] & 255)));
                        }
                        if (maxLegacyDelta > 3) distortedPixels++;
                    }
                }
                assertTrue("8px split visibly distorts fasteners group " + group + " size "
                        + size + ": " + distortedPixels, distortedPixels > 100);
            }
        } finally {
            batch.setProjectionMatrix(previous);
            fb.dispose();
        }
    }

    private static Rectangle resizedTarget(int group, int size) {
        return new Rectangle(size == 0 ? 20 : 320, group * 180 + 20,
                size == 0 ? 256 : 300, size == 0 ? 120 : 86);
    }

    private static void drawLegacyEightPixelSlices(SpriteBatch batch, Texture texture,
            int group, Rectangle target) {
        int[] sx = {0, 8, 189}, sy = {0, 8, VISIBLE_HEIGHTS[group] - 8};
        int[] sw = {8, 181, 8}, sh = {8, VISIBLE_HEIGHTS[group] - 16, 8};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            float x = col == 0 ? target.x : col == 1 ? target.x + 8 : target.x + target.width - 8;
            float y = row == 0 ? target.y + target.height - 8 : row == 1 ? target.y + 8 : target.y;
            batch.draw(new TextureRegion(texture, sx[col], sy[row], sw[col], sh[row]), x, y,
                    col == 1 ? target.width - 16 : 8, row == 1 ? target.height - 16 : 8);
        }
    }

    private static byte[] atlas(FrameBuffer fb, SpriteBatch batch, SourceEquipmentCards renderer,
            Texture[] source, Texture raw, Texture missing, int mode) {
        fb.begin(); clear(); batch.begin(); batch.setColor(.2f, .4f, .6f, .8f);
        float saved = batch.getPackedColor();
        if (mode != 0) batch.setColor(1f, 1f, 1f, 1f);
        int y = 20;
        for (int group = 0; group < IDS.length; group++) {
            int height = VISIBLE_HEIGHTS[group];
            Rectangle target = new Rectangle(30, y, 197, height);
            if (mode == 0) renderer.draw(batch, group, target);
            else if (mode == 1) batch.draw(new TextureRegion(source[group], 0, 0, 197, height),
                    target.x, target.y, target.width, target.height);
            else if (mode == 2) batch.draw(new TextureRegion(raw, 0, 0, 197, height),
                    target.x, target.y, target.width, target.height);
            else batch.draw(missing, target.x, target.y, target.width, target.height);
            y += height + 8;
        }
        if (mode != 0) batch.setPackedColor(saved);
        assertEquals("batch color restored by equipment renderer", saved,
                batch.getPackedColor(), 0f);
        batch.end();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, 256, 700, true);
        fb.end();
        return pixels;
    }

    private static void assertBioloadBottomTransparent(Texture texture) {
        Pixmap pixels = new Pixmap(Gdx.files.internal(
                "original/ui/equipment-cards/bioloads.png"));
        try {
            assertEquals(197, pixels.getWidth());
            assertEquals(79, pixels.getHeight());
            for (int y = 69; y < 79; y++) for (int x = 0; x < 197; x++)
                assertEquals("source bioload registration has transparent tail",
                        0, pixels.getPixel(x, y) & 255);
        } finally { pixels.dispose(); }
        assertEquals(79, texture.getHeight());
    }

    private static SourceShape decodeSourceShape(int id, int height) throws Exception {
        byte[] xml;
        try (InputStream zipped = new GZIPInputStream(Gdx.files.internal(
                "original/runtime/shapes/" + id + ".svgz").read());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = zipped.read(buffer)) != -1) out.write(buffer, 0, count);
            xml = out.toByteArray();
        }
        String text = new String(xml, StandardCharsets.UTF_8);
        assertEquals("one SVG path for shape " + id, 1, occurrences(text, "<path "));
        assertEquals("one image fill for shape " + id, 1, occurrences(text, "<image "));
        Matcher match = Pattern.compile("data:image/webp;base64,([^\"]+)").matcher(text);
        assertTrue("source bitmap fill " + id, match.find());
        String encoded = match.group(1);
        assertFalse("one embedded bitmap fill " + id, match.find());
        byte[] webp = Base64.decode(encoded, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(webp, 0, webp.length);
        assertNotNull(bitmap);
        assertEquals(197, bitmap.getWidth());
        assertEquals(554, bitmap.getHeight());
        assertTrue("pixelated source fill " + id,
                text.contains("image-rendering:pixelated"));
        float[] group = matrixAttribute(text, "<g transform");
        float[] pattern = matrixAttribute(text, "patternTransform");
        assertEquals(1f, group[0], 0f); assertEquals(0f, group[1], 0f);
        assertEquals(0f, group[2], 0f); assertEquals(1f, group[3], 0f);
        assertEquals(0f, group[4], 0f);
        if (id == 4480) {
            assertEquals("aircraft fractional registration", -70.75f, group[5], 0f);
            assertTrue("aircraft main edge begins one quarter pixel below top",
                    text.contains("M196.0 71.0 L196.0 70.75"));
        }
        assertEquals(1f, pattern[0], 0f); assertEquals(0f, pattern[1], 0f);
        assertEquals(0f, pattern[2], 0f); assertEquals(1f, pattern[3], 0f);
        assertEquals(0f, pattern[4], 0f); assertEquals(0f, pattern[5], 0f);
        Path clip = sourcePath(text);
        Bitmap clipped = Bitmap.createBitmap(197, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(clipped);
        canvas.save();
        canvas.translate(group[4], group[5]);
        canvas.clipPath(clip);
        Paint nearest = new Paint();
        nearest.setAntiAlias(true);
        nearest.setFilterBitmap(false);
        nearest.setDither(false);
        canvas.drawBitmap(bitmap, 0f, 0f, nearest);
        canvas.restore();
        Texture raw = texture(bitmap);
        Texture rendered = texture(clipped);
        bitmap.recycle();
        clipped.recycle();
        return new SourceShape(rendered, raw);
    }

    private static int occurrences(String text, String needle) {
        int count = 0, at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) { count++; at += needle.length(); }
        return count;
    }

    private static float[] matrixAttribute(String svg, String start) {
        int at = svg.indexOf(start);
        assertTrue("missing SVG transform " + start, at >= 0);
        Matcher value = Pattern.compile("matrix\\(([^)]+)\\)").matcher(svg.substring(at));
        assertTrue(value.find());
        String[] fields = value.group(1).split("[,\\s]+");
        assertEquals(6, fields.length);
        float[] result = new float[6];
        for (int i = 0; i < result.length; i++) result[i] = Float.parseFloat(fields[i]);
        return result;
    }

    private static Path sourcePath(String svg) {
        Matcher attribute = Pattern.compile("<path d=\"([^\"]+)\"").matcher(svg);
        assertTrue("one source path", attribute.find());
        String commands = attribute.group(1);
        assertFalse(attribute.find());
        Pattern tokenPattern = Pattern.compile("[ML]|-?(?:\\d+\\.?\\d*|\\.\\d+)");
        Matcher words = tokenPattern.matcher(commands);
        ArrayList<String> tokens = new ArrayList<>();
        while (words.find()) tokens.add(words.group());
        assertTrue("no unsupported SVG path commands",
                tokenPattern.matcher(commands).replaceAll("").trim().isEmpty());
        Path result = new Path();
        result.setFillType(Path.FillType.EVEN_ODD);
        int count = 0, index = 0;
        String command = null;
        float firstX = 0f, firstY = 0f, lastX = 0f, lastY = 0f;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (token.equals("M") || token.equals("L")) {
                command = token;
                index++;
            }
            assertNotNull("path starts with M", command);
            assertTrue("complete SVG coordinate pair", index + 1 < tokens.size());
            String xToken = tokens.get(index++), yToken = tokens.get(index++);
            assertFalse("x is numeric", xToken.equals("M") || xToken.equals("L"));
            assertFalse("y is numeric", yToken.equals("M") || yToken.equals("L"));
            float x = Float.parseFloat(xToken), y = Float.parseFloat(yToken);
            if (count == 0) {
                assertEquals("first command is M", "M", command);
                result.moveTo(x, y);
                firstX = x; firstY = y;
                command = "L"; // Further pairs after M are implicit lines.
            } else {
                assertEquals("only line segments after M", "L", command);
                result.lineTo(x, y);
            }
            lastX = x; lastY = y;
            count++;
        }
        assertTrue("polygon retained", count >= 5);
        assertEquals("source polygon closes in x", firstX, lastX, 0f);
        assertEquals("source polygon closes in y", firstY, lastY, 0f);
        result.close();
        return result;
    }

    private static Texture texture(Bitmap bitmap) {
        Pixmap pixels = new Pixmap(bitmap.getWidth(), bitmap.getHeight(), Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int argb = bitmap.getPixel(x, y);
                pixels.drawPixel(x, y, (argb << 8) | (argb >>> 24));
            }
        Texture result = new Texture(pixels);
        pixels.dispose();
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return result;
    }

    private static Rectangle[] copy(Rectangle[] source) {
        Rectangle[] result = new Rectangle[source.length];
        for (int i = 0; i < source.length; i++) result[i] = new Rectangle(source[i]);
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

    private static final class SourceShape {
        final Texture clipped;
        final Texture bitmap;
        SourceShape(Texture clipped, Texture bitmap) {
            this.clipped = clipped;
            this.bitmap = bitmap;
        }
    }
}
