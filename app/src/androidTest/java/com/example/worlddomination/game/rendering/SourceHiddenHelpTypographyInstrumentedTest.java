package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.ui.SourceHiddenHelpTypography;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.MultipleFailureException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Exact source texel control and bounded production Linear raster comparison;
 * this tests full authored fields, not a full World crop. */
@RunWith(AndroidJUnit4.class)
public final class SourceHiddenHelpTypographyInstrumentedTest {
    @Test public void sourceDynamicFieldsMatchAuthoredFontsAndRejectResourceArial() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            JsonValue oracles = new JsonReader().parse(new String(asset("oracles.json"), java.nio.charset.StandardCharsets.UTF_8));
            BitmapFont command = load("hidden-font-command-120");
            BitmapFont warning = null, wrong = null;
            SpriteBatch batch = null;
            try {
                warning = load("hidden-font-warning-40");
                wrong = new BitmapFont(Gdx.files.internal("original/ui/fonts/resource-arial-48.fnt"), false);
                wrong.setUseIntegerPositions(false);
                batch = new SpriteBatch();
                List<Throwable> failures = new ArrayList<>();
                for (JsonValue oracle : oracles.get("fixtures")) {
                    boolean isCommand = oracle.getString("fontStem").contains("command");
                    BitmapFont font = isCommand ? command : warning;
                    font.getData().setScale(1f); // Real production loader, full source 4x raster.
                    wrong.getData().setScale((isCommand ? 120f : 40f) / 48f);
                    try { checkField(batch, font, wrong, oracle, isCommand); }
                    catch (AssertionError failure) { failures.add(failure); }
                }
                MultipleFailureException.assertEmpty(failures);
            } finally {
                if (batch != null) batch.dispose();
                if (wrong != null) wrong.dispose();
                if (warning != null) warning.dispose();
                command.dispose();
            }
        });
    }

    private static void checkField(SpriteBatch batch, BitmapFont font, BitmapFont wrong,
                                   JsonValue oracle, boolean command) throws Exception {
        String name = oracle.getString("name");
        byte[] frozen = asset(oracle.getString("file"));
        assertEquals("Immutable independent source PNG", oracle.getString("sha256"), sha256(frozen));
        Pixmap expected = new Pixmap(frozen, 0, frozen.length);
        Pixmap actual = null, negative = null, nearest = null;
        try {
            int width = oracle.getInt("width"), height = oracle.getInt("height");
            assertEquals(width, expected.getWidth()); assertEquals(height, expected.getHeight());
            Color tint = command ? new Color(1f, 120f / 255f, 0f, 1f) : Color.WHITE;
            Texture texture = font.getRegion().getTexture();
            Texture.TextureFilter minFilter = texture.getMinFilter(), magFilter = texture.getMagFilter();
            assertEquals("Production font minification remains Linear", Texture.TextureFilter.Linear, minFilter);
            assertEquals("Production font magnification remains Linear", Texture.TextureFilter.Linear, magFilter);
            actual = render(batch, font, oracle, tint);
            float[] firstQuad = Arrays.copyOf(font.getCache().getVertices(), 20);
            try {
                // Isolate source font/content/position from the measured Linear
                // sampler rounding. This control must match every frozen alpha byte.
                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                nearest = render(batch, font, oracle, tint);
            } finally { texture.setFilter(minFilter, magFilter); }
            String glyphDiagnostic = "null";
            if ("bush-blurred".equals(name)) {
                BitmapFont.Glyph glyph = font.getData().getGlyph('I');
                Pixmap isolated = render(batch, font, oracle, tint, true);
                Pixmap expanded = null;
                try {
                    expanded = render(batch, font, oracle, tint, false, firstQuad);
                    int regionLeft = Math.round(SourceHiddenHelpTypography.centeredOffset(width / 4f,
                            SourceHiddenHelpTypography.advance(font.getData(), "I am Bush") / 4f) * 4f);
                    long isolatedVsFull = 0, isolatedVsFrozen = 0;
                    long expandedVsFrozen = 0, expandedVsIsolated = 0;
                    int expandedMaximumDifference = 0, expandedDifferentPixels = 0;
                    for (int y = 0; y < height; y++) for (int x = regionLeft; x < regionLeft + glyph.width; x++) {
                        int coverage = isolated.getPixel(x, y) & 255;
                        isolatedVsFull += Math.abs(coverage - (actual.getPixel(x, y) & 255));
                        isolatedVsFrozen += Math.abs(coverage - (expected.getPixel(x, y) & 255));
                        int expandedAlpha = expanded.getPixel(x, y) & 255;
                        int difference = Math.abs(expandedAlpha - (expected.getPixel(x, y) & 255));
                        expandedVsFrozen += difference;
                        expandedVsIsolated += Math.abs(expandedAlpha - coverage);
                        expandedMaximumDifference = Math.max(expandedMaximumDifference, difference);
                        if (difference != 0) expandedDifferentPixels++;
                    }
                    int textureWidth = font.getRegion().getTexture().getWidth();
                    int textureHeight = font.getRegion().getTexture().getHeight();
                    glyphDiagnostic = "{\"glyphIUV\":" + Arrays.toString(new float[]{glyph.u, glyph.v, glyph.u2, glyph.v2})
                            + ",\"glyphIUVBits\":" + Arrays.toString(new int[]{Float.floatToRawIntBits(glyph.u), Float.floatToRawIntBits(glyph.v), Float.floatToRawIntBits(glyph.u2), Float.floatToRawIntBits(glyph.v2)})
                            + ",\"glyphIAtlasPixels\":" + Arrays.toString(new float[]{glyph.u * textureWidth, glyph.v * textureHeight, glyph.u2 * textureWidth, glyph.v2 * textureHeight})
                            + ",\"glyphISizeOffset\":" + Arrays.toString(new int[]{glyph.width, glyph.height, glyph.xoffset, glyph.yoffset, glyph.xadvance})
                            + ",\"fontScaleAscentCap\":" + Arrays.toString(new float[]{font.getData().scaleX, font.getData().scaleY, font.getData().ascent, font.getData().capHeight})
                            + ",\"firstQuad\":" + Arrays.toString(firstQuad)
                            + ",\"firstQuadXYUV\":" + Arrays.toString(new float[]{firstQuad[0],firstQuad[1],firstQuad[3],firstQuad[4],firstQuad[5],firstQuad[6],firstQuad[8],firstQuad[9],firstQuad[10],firstQuad[11],firstQuad[13],firstQuad[14],firstQuad[15],firstQuad[16],firstQuad[18],firstQuad[19]})
                            + ",\"isolatedBounds\":" + Arrays.toString(bounds(isolated))
                            + ",\"isolatedVsFullIAlphaDifference\":" + isolatedVsFull
                            + ",\"isolatedVsFrozenIAlphaDifference\":" + isolatedVsFrozen
                            + ",\"expanded128Bounds\":" + Arrays.toString(bounds(expanded))
                            + ",\"expanded128VsFrozenIAlphaDifference\":" + expandedVsFrozen
                            + ",\"expanded128VsIsolatedIAlphaDifference\":" + expandedVsIsolated
                            + ",\"expanded128MaximumAlphaDifference\":" + expandedMaximumDifference
                            + ",\"expanded128DifferentPixels\":" + expandedDifferentPixels
                            + ",\"expanded128TopAlpha\":" + (expanded.getPixel(374,16) & 255)
                            + ",\"isolatedTopAlpha\":" + (isolated.getPixel(374,16) & 255)
                            + ",\"fullTopAlpha\":" + (actual.getPixel(374,16) & 255)
                            + ",\"frozenTopAlpha\":" + (expected.getPixel(374,16) & 255) + "}";
                } finally { if (expanded != null) expanded.dispose(); isolated.dispose(); }
            }
            negative = render(batch, wrong, oracle, tint);
            String stem = "hidden-typography-" + name;
            evidence(stem + "-expected.png").writeBytes(frozen, false);
            PixmapIO.writePNG(evidence(stem + "-actual.png"), actual);
            PixmapIO.writePNG(evidence(stem + "-wrong-arial.png"), negative);
            int[] expectedBounds = bounds(expected), actualBounds = bounds(actual);
            int[] wrongBounds = bounds(negative);
            List<int[]> expectedRows = rows(expected), actualRows = rows(actual);
            long absoluteDifference = 0;
            int overTwo = 0, maximumDifference = 0, opaque = 0, tintFailures = 0;
            long nearestDifference = 0;
            int nearestDifferentPixels = 0, nearestMaximumDifference = 0, gradientViolations = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int source = expected.getPixel(x, y) & 255;
                int pixel = actual.getPixel(x, y), alpha = pixel & 255;
                int difference = Math.abs(source - alpha);
                absoluteDifference += difference;
                maximumDifference = Math.max(maximumDifference, difference);
                if (difference > 2) overTwo++;
                int exactDifference = Math.abs(source - (nearest.getPixel(x, y) & 255));
                nearestDifference += exactDifference;
                if (exactDifference != 0) nearestDifferentPixels++;
                nearestMaximumDifference = Math.max(nearestMaximumDifference, exactDifference);
                if (difference > 2 && difference > localGradientBound(expected, x, y)) gradientViolations++;
                if (alpha >= 254) {
                    opaque++;
                    if (Math.abs((pixel >>> 24) - 255) > 2
                            || Math.abs((pixel >>> 16 & 255) - (command ? 120 : 255)) > 2
                            || Math.abs((pixel >>> 8 & 255) - (command ? 0 : 255)) > 2) tintFailures++;
                }
            }
            float[] advances = advances(font, oracle), wrongAdvances = advances(wrong, oracle);
            int shiftedXViolations = boundedAlphaViolations(expected, expected, 1, 0);
            int shiftedYViolations = boundedAlphaViolations(expected, expected, 0, 1);
            int wrongGradientViolations = command ? boundedAlphaViolations(expected, negative, 0, 0) : -1;
            String receipt = "{\"name\":\"" + name + "\",\"fragmentHighpBits\":" + fragmentHighpBits()
                    + ",\"actualBounds\":" + Arrays.toString(actualBounds)
                    + ",\"expectedBounds\":" + Arrays.toString(expectedBounds) + ",\"wrongBounds\":" + Arrays.toString(wrongBounds)
                    + ",\"actualRows\":" + rowJson(actualRows) + ",\"expectedRows\":" + rowJson(expectedRows)
                    + ",\"advances\":" + Arrays.toString(advances) + ",\"wrongAdvances\":" + Arrays.toString(wrongAdvances)
                    + ",\"alphaAbsoluteDifference\":" + absoluteDifference + ",\"alphaDifferencesOverTwo\":" + overTwo
                    + ",\"maximumAlphaDifference\":" + maximumDifference + ",\"opaquePixels\":" + opaque
                    + ",\"opaqueTintFailures\":" + tintFailures
                    + ",\"nearestAlphaDifference\":" + nearestDifference + ",\"nearestDifferentPixels\":" + nearestDifferentPixels
                    + ",\"nearestMaximumAlphaDifference\":" + nearestMaximumDifference
                    + ",\"linearLocalGradientViolations\":" + gradientViolations
                    + ",\"shiftedXLocalGradientViolations\":" + shiftedXViolations
                    + ",\"shiftedYLocalGradientViolations\":" + shiftedYViolations
                    + ",\"wrongArialLocalGradientViolations\":" + wrongGradientViolations
                    + ",\"glyphDiagnostic\":" + glyphDiagnostic + "}";
            evidence(stem + ".json").writeString(receipt, false, "UTF-8");
            assertArrayEquals(name + " frozen bounds", oracle.get("bounds").asIntArray(), expectedBounds);
            assertArrayEquals(name + " full-field ink bounds", expectedBounds, actualBounds);
            assertEquals(name + " line count", expectedRows.size(), actualRows.size());
            for (int line = 0; line < expectedRows.size(); line++) {
                assertArrayEquals(name + " source line " + line, oracle.get("rows").get(line).asIntArray(), expectedRows.get(line));
                assertArrayEquals(name + " native line " + line, expectedRows.get(line), actualRows.get(line));
                assertEquals(name + " advance " + line, oracle.get("advances").get(line).asFloat(), advances[line], .001f);
            }
            assertEquals(name + " exact independent source texel alpha: " + receipt, 0, nearestDifferentPixels);
            assertEquals(name + " production Linear local sampling envelope: " + receipt, 0, gradientViolations);
            assertTrue(name + " one-pixel horizontal shift must fail sampling envelope", shiftedXViolations > 0);
            assertTrue(name + " one-pixel vertical shift must fail sampling envelope", shiftedYViolations > 0);
            assertTrue(name + " must contain opaque glyph interiors", opaque > 20);
            assertEquals(name + " font texture must preserve white luminance and tint", 0, tintFailures);
            if (command) {
                assertFalse(name + " wrong Arial must fail advances", matchingAdvances(wrongAdvances, oracle));
                assertFalse(name + " wrong Arial must fail ink bounds", Arrays.equals(expectedBounds, wrongBounds));
                assertTrue(name + " wrong Arial must fail local sampling envelope", wrongGradientViolations > 0);
            }
        } finally {
            if (negative != null) negative.dispose();
            if (nearest != null) nearest.dispose();
            if (actual != null) actual.dispose();
            expected.dispose();
        }
    }

    /** The acceptance budget is the observed compatible-device 1/16-texel
     * rounding, not a universal OpenGL guarantee. For bilinear alpha, each partial
     * derivative is bounded by adjacent source texel differences. The 3x3 stencil
     * covers both directions of a ≤1/16-texel
     * sampling displacement per axis: |deltaA|≤(Gx+Gy)/16, plus 2 UNORM levels.
     * This bound is local: flat interiors allow 2, not an arbitrary global alpha
     * tolerance. Exact Nearest/geometry and shifted negative controls are separate. */
    private static int localGradientBound(Pixmap source, int x, int y) {
        int horizontal = 0, vertical = 0;
        for (int row = y - 1; row <= y + 1; row++)
            for (int column = x - 1; column <= x; column++)
                horizontal = Math.max(horizontal, Math.abs(alpha(source, column + 1, row) - alpha(source, column, row)));
        for (int row = y - 1; row <= y; row++)
            for (int column = x - 1; column <= x + 1; column++)
                vertical = Math.max(vertical, Math.abs(alpha(source, column, row + 1) - alpha(source, column, row)));
        return (horizontal + vertical + 15) / 16 + 2;
    }

    private static int boundedAlphaViolations(Pixmap expected, Pixmap actual, int shiftX, int shiftY) {
        int violations = 0;
        for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++) {
            int difference = Math.abs(alpha(expected, x, y) - alpha(actual, x - shiftX, y - shiftY));
            if (difference > 2 && difference > localGradientBound(expected, x, y)) violations++;
        }
        return violations;
    }

    private static int alpha(Pixmap image, int x, int y) {
        return x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()
                ? 0 : image.getPixel(x, y) & 255;
    }

    private static Pixmap render(SpriteBatch batch, BitmapFont font, JsonValue oracle, Color tint) {
        return render(batch, font, oracle, tint, false);
    }

    private static Pixmap render(SpriteBatch batch, BitmapFont font, JsonValue oracle, Color tint,
                                  boolean isolateFirstGlyph) {
        return render(batch, font, oracle, tint, isolateFirstGlyph, null);
    }

    private static Pixmap render(SpriteBatch batch, BitmapFont font, JsonValue oracle, Color tint,
                                  boolean isolateFirstGlyph, float[] originalFirstQuad) {
        int width = oracle.getInt("width"), height = oracle.getInt("height");
        // Binary-exact orthographic coordinates avoid adding non-source subpixel
        // phase through a 1272/564-pixel projection. Field dimensions and clip stay
        // exact; the production Linear texture filter remains unchanged.
        int bufferWidth = com.badlogic.gdx.math.MathUtils.nextPowerOfTwo(width);
        int bufferHeight = com.badlogic.gdx.math.MathUtils.nextPowerOfTwo(height);
        int fieldBottom = bufferHeight - height;
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, bufferWidth, bufferHeight, false);
        boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        java.nio.IntBuffer oldScissor = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(4);
        if (scissor) Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, oldScissor);
        buffer.begin();
        try {
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(0, fieldBottom, width, height); // Full field, never an ink-only ROI.
            Gdx.gl.glClearColor(0, 0, 0, 0); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, bufferWidth, bufferHeight));
            batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                    GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.begin();
            if (originalFirstQuad != null) {
                // Diagnostic control: same atlas/Linear filter and one texel per
                // pixel, but a 128-high interpolation primitive. Clip away the extra
                // 40 rows, preserving exactly the original I's 33x88 visible region.
                float[] quad = Arrays.copyOf(originalFirstQuad, 20);
                float extra = 128f - (quad[6] - quad[1]);
                quad[1] -= extra; quad[16] -= extra;
                float extraV = extra / font.getRegion().getTexture().getHeight();
                quad[4] += extraV; quad[19] += extraV;
                Gdx.gl.glScissor(Math.round(originalFirstQuad[0]), Math.round(originalFirstQuad[1]),
                        Math.round(originalFirstQuad[10] - originalFirstQuad[0]),
                        Math.round(originalFirstQuad[6] - originalFirstQuad[1]));
                batch.draw(font.getRegion().getTexture(), quad, 0, 20);
            } else {
                int line = 0;
                for (JsonValue text : oracle.get("lines")) {
                    GlyphLayout layout = new GlyphLayout();
                    String value = text.asString();
                    float advance = SourceHiddenHelpTypography.advance(font.getData(), value);
                    SourceHiddenHelpTypography.layout(layout, font, isolateFirstGlyph ? value.substring(0, 1) : value, tint);
                    float x = SourceHiddenHelpTypography.centeredOffset(width / 4f, advance / 4f) * 4f;
                    float top = bufferHeight - 16f - line++ * 48f;
                    font.draw(batch, layout, x, top - SourceHiddenHelpTypography.topOffset(font.getData(), layout));
                }
            }
            batch.end();
            Pixmap result = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            result.getPixels().put(ScreenUtils.getFrameBufferPixels(0, fieldBottom, width, height, true));
            return result;
        } finally {
            if (batch.isDrawing()) batch.end();
            buffer.end(); buffer.dispose();
            if (scissor) Gdx.gl.glScissor(oldScissor.get(0), oldScissor.get(1), oldScissor.get(2), oldScissor.get(3));
            else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        }
    }

    private static float[] advances(BitmapFont font, JsonValue oracle) {
        float[] values = new float[oracle.get("lines").size];
        int index = 0;
        for (JsonValue line : oracle.get("lines")) values[index++] = SourceHiddenHelpTypography.advance(font.getData(), line.asString());
        return values;
    }
    private static boolean matchingAdvances(float[] values, JsonValue oracle) {
        for (int i = 0; i < values.length; i++)
            if (Math.abs(values[i] - oracle.get("advances").get(i).asFloat()) > .001f) return false;
        return true;
    }
    private static int[] bounds(Pixmap image) {
        int left = image.getWidth(), top = image.getHeight(), right = 0, bottom = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getPixel(x, y) & 255) != 0) { left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1); }
        return new int[]{left, top, right, bottom};
    }
    private static List<int[]> rows(Pixmap image) {
        List<int[]> rows = new ArrayList<>(); int start = -1;
        for (int y = 0; y <= image.getHeight(); y++) {
            boolean ink = false;
            if (y < image.getHeight()) for (int x = 0; x < image.getWidth() && !ink; x++) ink = (image.getPixel(x, y) & 255) != 0;
            if (ink && start < 0) start = y;
            if (!ink && start >= 0) { rows.add(new int[]{start, y}); start = -1; }
        }
        return rows;
    }
    private static String rowJson(List<int[]> rows) { String[] result = new String[rows.size()]; for (int i = 0; i < result.length; i++) result[i] = Arrays.toString(rows.get(i)); return "[" + String.join(",", result) + "]"; }
    private static int fragmentHighpBits() {
        java.nio.IntBuffer range = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(2);
        java.nio.IntBuffer bits = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetShaderPrecisionFormat(GL20.GL_FRAGMENT_SHADER, GL20.GL_HIGH_FLOAT, range, bits);
        return bits.get(0);
    }
    private static BitmapFont load(String stem) throws Exception { Method method = WorldDominationGame.class.getDeclaredMethod("loadHiddenHelpFont", String.class); method.setAccessible(true); return (BitmapFont) method.invoke(null, stem); }
    private static byte[] asset(String name) throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("hidden-help-oracles/" + name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    private static String sha256(byte[] bytes) throws Exception { StringBuilder result = new StringBuilder(); for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); return result.toString(); }
    private static FileHandle evidence(String name) { File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence"); assertNotNull(directory); assertTrue(directory.isDirectory() || directory.mkdirs()); return Gdx.files.absolute(new File(directory, name).getAbsolutePath()); }
}
