package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.io.File;
import java.util.Collections;

import static org.junit.Assert.*;

/** GPU oracle from exported satellite leaves, with synthetic poses for frame coverage. */
@RunWith(AndroidJUnit4.class)
public final class SatelliteRenderingInstrumentedTest {
    private static final String[] RELIGIONS = {"mus", "jew", "com", "chr", "bhu"};
    private static final int[] NESTED_FRAMES = {1, 14, 26};
    private static final String VERTEX = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\nattribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\nvarying vec4 v_color;\n"
            + "varying vec2 v_uv;\nvoid main(){v_color=a_color;"
            + "v_color.a*=255.0/254.0;v_uv=a_texCoord0;"
            + "gl_Position=u_projTrans*a_position;}";
    private static final String FRAGMENT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec4 v_color;\nvarying vec2 v_uv;\n"
            + "uniform sampler2D u_texture;\nuniform vec4 u_mul;\nuniform vec4 u_add;\n"
            + "void main(){gl_FragColor=clamp(texture2D(u_texture,v_uv)*u_mul+u_add,0.0,1.0)*v_color;}";

    @Test public void satelliteFramesMatchExportedAffineLeavesAtBothMapWidths() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(SatelliteRenderingInstrumentedTest::verify);
    }

    private static void verify(WorldDominationGame game) throws Exception {
        OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class,
                        "flashAnimationAssets").get(game);
        FlashAnimationTimeline outer = assets.getTimeline("satellite");
        assertNotNull(outer);
        SpriteBatch batch = new SpriteBatch();
        FlashBattleUnitRenderer renderer = new FlashBattleUnitRenderer(assets);
        ShaderProgram oracleShader = null;
        ShaderProgram callerShader = null;
        FrameBuffer buffer = null;
        boolean bound = false;
        try {
            oracleShader = new ShaderProgram(VERTEX, FRAGMENT);
            callerShader = SpriteBatch.createDefaultShader();
            assertTrue(oracleShader.getLog(), oracleShader.isCompiled());
            assertTrue(callerShader.getLog(), callerShader.isCompiled());
            for (int width : new int[]{800, 1194}) {
                Rectangle bounds = new Rectangle(0, 0, width, 600);
                buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, 600, false);
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, 600));
                for (String religion : RELIGIONS) {
                    FlashBattleRoutes.SatelliteStrike route = FlashBattleRoutes.createSatelliteStrike(
                            0, 1, 0, 1, religion);
                    FlashSatelliteAnimation animation = new FlashSatelliteAnimation(route, 0);
                    assertFalse("Source clips exist visibly before activation", animation.isStarted());
                    // Synthetic visible pose isolates exported frame composition from route timing.
                    set(animation, "sourceX", 350f);
                    set(animation, "sourceY", 300f);
                    set(animation, "displaySourceX", 350f);
                    set(animation, "displaySourceY", 300f);
                    FlashAnimationTimeline.Frame body = outer.getFrameByLabel(religion);
                    assertNotNull(religion, body);
                    for (int nestedFrame : NESTED_FRAMES) {
                        set(animation, "sourceClipFrameNumber", nestedFrame);
                        requireLeaves(assets, body, nestedFrame - 1, 0);
                        clear(buffer); bound = true;
                        Matrix4 callerTransform = new Matrix4().setToTranslation(3f, -2f, 0f);
                        batch.setTransformMatrix(callerTransform);
                        batch.setShader(callerShader);
                        batch.setColor(.3f, .5f, .7f, .8f);
                        float callerColor = batch.getPackedColor();
                        batch.begin();
                        renderer.drawSatellites(batch, bounds,
                                Collections.singletonList(animation));
                        assertSame(callerShader, batch.getShader());
                        assertEquals(callerColor, batch.getPackedColor(), 0f);
                        assertArrayEquals(callerTransform.val, batch.getTransformMatrix().val, 0f);
                        batch.end();
                        byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, width, 600, false);
                        buffer.end(); bound = false;

                        clear(buffer); bound = true;
                        batch.begin();
                        drawOracle(batch, oracleShader, assets, body, bounds, route,
                                nestedFrame - 1, identity(), identityColor(), callerTransform, 0);
                        batch.setTransformMatrix(callerTransform);
                        batch.end();
                        byte[] expected = ScreenUtils.getFrameBufferPixels(0, 0, width, 600, false);
                        buffer.end(); bound = false;
                        if ("jew".equals(religion) && nestedFrame == 14) {
                            save(actual, width, "source-satellite-jew-" + width + "-actual.png");
                            save(expected, width, "source-satellite-jew-" + width + "-oracle.png");
                        }
                        assertPixels(actual, expected, width, religion, nestedFrame);
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    }
                }
                buffer.dispose(); buffer = null;
            }

            FlashBattleRoutes.SatelliteStrike route = FlashBattleRoutes.createSatelliteStrike(
                    0, 1, 0, 1, "jew");
            FlashSatelliteAnimation stopped = new FlashSatelliteAnimation(route, 0);
            for (int tick = 0; tick < 300 && !stopped.isFinished(); tick++) stopped.tick();
            assertTrue("Satellite wrapper reaches removal", stopped.isFinished());
            assertTrue("Stopped source body remains attached", stopped.isClipVisible());
            SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
            stopped.registerStoppedClip(queue);
            assertEquals(1, queue.size());
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 800, 600, false);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 800, 600));
            Rectangle bounds = new Rectangle(0, 0, 800, 600);
            clear(buffer); bound = true;
            batch.setTransformMatrix(new Matrix4());
            batch.begin();
            renderer.drawSatellites(batch, bounds, Collections.singletonList(stopped));
            batch.end();
            byte[] retained = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, false);
            buffer.end(); bound = false;
            save(retained, 800, "source-satellite-retained.png");
            assertTrue("Stopped satellite body remains painted", paintedPixels(retained) > 10);
            assertEquals(1, queue.removeAtAllUnitsDone());
            assertFalse(stopped.isClipVisible());
            clear(buffer); bound = true;
            batch.begin();
            renderer.drawSatellites(batch, bounds, Collections.singletonList(stopped));
            batch.end();
            byte[] detached = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, false);
            buffer.end(); bound = false;
            save(detached, 800, "source-satellite-detached.png");
            assertEquals("Detached body has no pixels", 0, paintedPixels(detached));
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            if (batch.isDrawing()) batch.end();
            if (bound && buffer != null) buffer.end();
            if (buffer != null) buffer.dispose();
            if (oracleShader != null) oracleShader.dispose();
            if (callerShader != null) callerShader.dispose();
            renderer.dispose();
            batch.dispose();
        }
    }

    private static void set(FlashSatelliteAnimation animation, String name, Object value)
            throws Exception {
        Field field = FlashSatelliteAnimation.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(animation, value);
    }

    private static void requireLeaves(OriginalFlashAnimationAssets assets,
                                      FlashAnimationTimeline.Frame frame,
                                      int nestedFrame, int depth) {
        assertTrue("Nested satellite depth", depth < 8);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            if (assets.getShape(layer.shapeId) != null) continue;
            FlashAnimationTimeline child = assets.getTimelineBySymbolId(layer.shapeId);
            assertNotNull("Missing satellite child " + layer.shapeId, child);
            assertTrue(child.getFrameCount() > 0);
            FlashAnimationTimeline.Frame childFrame = child.getFrame(
                    Math.min(nestedFrame, child.getFrameCount() - 1));
            assertNotNull(childFrame);
            requireLeaves(assets, childFrame, nestedFrame, depth + 1);
        }
    }

    private static void drawOracle(SpriteBatch batch, ShaderProgram shader,
                                   OriginalFlashAnimationAssets assets,
                                   FlashAnimationTimeline.Frame frame, Rectangle bounds,
                                   FlashBattleRoutes.SatelliteStrike route, int nestedFrame,
                                   float[] parent, float[] color, Matrix4 caller, int depth) {
        assertTrue(depth < 8);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float[] child = multiply(parent, layer);
            float[] childColor = multiplyColor(color, layer.colorTransform);
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region == null) {
                FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
                assertNotNull(nested);
                drawOracle(batch, shader, assets,
                        nested.getFrame(Math.min(nestedFrame, nested.getFrameCount() - 1)),
                        bounds, route,
                        nestedFrame, child, childColor, caller, depth + 1);
                continue;
            }
            float scale = BattlefieldCityLayout.sourceScale(bounds);
            Matrix4 matrix = new Matrix4();
            matrix.val[Matrix4.M00] = scale * child[0];
            matrix.val[Matrix4.M01] = -scale * child[2];
            matrix.val[Matrix4.M10] = -scale * child[1];
            matrix.val[Matrix4.M11] = scale * child[3];
            matrix.val[Matrix4.M03] = BattlefieldCityLayout.sourceX(bounds,
                    350f + child[4], route.attackerPlayerId);
            matrix.val[Matrix4.M13] = BattlefieldCityLayout.sourceY(bounds,
                    300f + child[5]);
            batch.setTransformMatrix(new Matrix4(caller).mul(matrix));
            batch.flush();
            batch.setShader(shader);
            shader.setUniformf("u_mul", childColor[0], childColor[2], childColor[4], childColor[6]);
            shader.setUniformf("u_add", childColor[1], childColor[3], childColor[5], childColor[7]);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(region, assets.getShapeOriginX(layer.shapeId),
                    -assets.getShapeOriginY(layer.shapeId) - region.getRegionHeight(),
                    region.getRegionWidth(), region.getRegionHeight());
        }
    }

    private static float[] identity() { return new float[]{1, 0, 0, 1, 0, 0}; }
    private static float[] identityColor() { return new float[]{1, 0, 1, 0, 1, 0, 1, 0}; }

    private static float[] multiply(float[] p, FlashAnimationTimeline.Layer c) {
        return new float[]{p[0] * c.a + p[2] * c.b, p[1] * c.a + p[3] * c.b,
                p[0] * c.c + p[2] * c.d, p[1] * c.c + p[3] * c.d,
                p[0] * c.tx + p[2] * c.ty + p[4],
                p[1] * c.tx + p[3] * c.ty + p[5]};
    }

    private static float[] multiplyColor(float[] p, FlashColorTransform c) {
        return new float[]{p[0] * c.redMultiplier, p[0] * c.redAdd + p[1],
                p[2] * c.greenMultiplier, p[2] * c.greenAdd + p[3],
                p[4] * c.blueMultiplier, p[4] * c.blueAdd + p[5],
                p[6] * c.alphaMultiplier, p[6] * c.alphaAdd + p[7]};
    }

    private static void clear(FrameBuffer buffer) {
        buffer.begin();
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private static void save(byte[] rgba, int width, String name) {
        File directory = new File(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir(null), "beta-ui-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(width, 600, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, true);
        } finally { image.dispose(); }
    }

    private static int paintedPixels(byte[] pixels) {
        int count = 0;
        for (int i = 0; i < pixels.length; i += 4) {
            if ((pixels[i] & 255) < 245 || (pixels[i + 1] & 255) < 245
                    || (pixels[i + 2] & 255) < 245) count++;
        }
        return count;
    }

    private static void assertPixels(byte[] actual, byte[] expected, int width,
                                     String religion, int nestedFrame) {
        assertEquals(expected.length, actual.length);
        assertTrue("Satellite body painted " + religion + " frame " + nestedFrame,
                paintedPixels(actual) > 10);
        for (int i = 0; i < actual.length; i += 4) {
            for (int channel = 0; channel < 3; channel++) {
                int difference = Math.abs((actual[i + channel] & 255)
                        - (expected[i + channel] & 255));
                if (difference > 3) fail("Satellite RGB width=" + width + " religion="
                        + religion + " frame=" + nestedFrame + " x=" + ((i / 4) % width)
                        + " y=" + ((i / 4) / width) + " channel=" + channel
                        + " difference=" + difference);
            }
        }
    }
}
