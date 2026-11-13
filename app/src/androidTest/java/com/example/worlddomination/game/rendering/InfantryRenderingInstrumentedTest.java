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
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

/** GLES comparison against independently positioned exported leaves and WD palette constants. */
@RunWith(AndroidJUnit4.class)
public final class InfantryRenderingInstrumentedTest {
    private static final int SIZE = 800;
    // WD_colors percentages and byte additions, in source setup-color order.
    private static final float[][] PALETTE = {
            {.15f, 0f, .74f, 0f, 1f, 1f, 1f, 0f},
            {.50f, 1f, 0f, 0f, 0f, 0f, .75f, 0f},
            {1f, 91f / 255f, .62f, 40f / 255f, 0f, 0f, 1f, 0f},
            {.50f, 1f, .50f, 1f, 0f, 0f, .75f, 0f},
            {0f, 0f, .50f, 1f, 0f, 0f, .75f, 0f}
    };
    private static final String[] BODY = {
            "boat001", "boat001_still", "soldier001", "soldier001_shoot"
    };
    private static final String[] TINT = {
            "boat001_tint", "boat001_still_tint", "soldier001_tint", "soldier001_shoot_tint"
    };
    private static final int[] FRAME = {1, 4, 212, 8};
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

    @Test
    public void realInfantryBodiesAndColorOverlaysMatchExportedLeaves() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> verify(app));
    }

    private static void verify(WorldDominationGame app) throws Exception {
        OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class,
                        "flashAnimationAssets").get(app);
        assertTrue(assets.isLoaded());
        for (int linkage = 0; linkage < BODY.length; linkage++) {
            for (String name : new String[]{BODY[linkage], TINT[linkage]}) {
                FlashAnimationTimeline timeline = assets.getTimeline(name);
                assertNotNull(name, timeline);
                for (int frame = 0; frame < timeline.getFrameCount(); frame++) {
                    FlashAnimationTimeline.Frame cached = timeline.getFrame(frame);
                    boolean authoredEmpty = ("soldier001".equals(name) && (frame == 0 || frame == 217))
                            || ("soldier001_tint".equals(name) && frame == 0);
                    assertEquals(name + " authored blank frame " + frame, authoredEmpty, cached == null);
                    if (cached != null) requireLeaves(assets, cached, 0);
                }
            }
        }
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(
                Gdx.files.internal("original/runtime/battle_anchors.json").readString("UTF-8"));
        SpriteBatch batch = new SpriteBatch();
        FlashBattleUnitRenderer renderer = new FlashBattleUnitRenderer(assets);
        ShaderProgram oracleShader = null;
        ShaderProgram callerShader = null;
        FrameBuffer buffer = null;
        boolean bound = false;
        try {
            oracleShader = new ShaderProgram(VERTEX, FRAGMENT);
            callerShader = SpriteBatch.createDefaultShader();
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE * 3 / 4, false);
            assertTrue(oracleShader.getLog(), oracleShader.isCompiled());
            assertTrue(callerShader.getLog(), callerShader.isCompiled());
            Rectangle bounds = new Rectangle(0, 0, 800, 600);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 800, 600));
            for (int color = 1; color <= 5; color++) {
                FlashBattleRoutes.SoldierOneAttack route = FlashBattleRoutes.createSoldierOneAttack(
                        0, 2, 0, anchors, new ReferenceRandom(100 + color));
                FlashSoldierOneAnimation animation = new FlashSoldierOneAnimation(route,
                        new ReferenceRandom(200 + color), null, 0, color);
                for (int pose = 0; pose < BODY.length; pose++) {
                    FlashSoldierOneAnimation.Actor actor = animation.getActor(pose < 2 ? 0 : 1);
                    for (int i = 0; i < animation.getActorCount(); i++)
                        animation.getActor(i).visible = false;
                    actor.visible = true;
                    actor.linkage = BODY[pose];
                    actor.sourceFrame = FRAME[pose];
                    actor.sourceX = 400f;
                    actor.sourceY = 300f;
                    actor.islandPlayerId = 0;
                    FlashAnimationTimeline.Frame body = requiredFrame(assets, BODY[pose], FRAME[pose]);
                    FlashAnimationTimeline tintTimeline = assets.getTimeline(TINT[pose]);
                    assertNotNull(TINT[pose], tintTimeline);
                    // Flash gotoAndStop clamps the four-frame still-boat tint.
                    FlashAnimationTimeline.Frame tint = tintTimeline.getFrame(
                            Math.min(FRAME[pose], tintTimeline.getFrameCount() - 1));
                    assertNotNull(TINT[pose] + " clamped frame", tint);
                    requireLeaves(assets, body, 0);
                    requireLeaves(assets, tint, 0);

                    clear(buffer); bound = true;
                    batch.setShader(callerShader);
                    batch.setColor(.3f, .5f, .7f, .8f);
                    Matrix4 callerTransform = new Matrix4(batch.getTransformMatrix());
                    float callerColor = batch.getPackedColor();
                    batch.begin();
                    renderer.draw(batch, bounds, Collections.singletonList(animation));
                    assertSame(callerShader, batch.getShader());
                    assertEquals(callerColor, batch.getPackedColor(), 0f);
                    assertArrayEquals(callerTransform.val, batch.getTransformMatrix().val, 0f);
                    batch.end();
                    byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, false);
                    buffer.end(); bound = false;

                    clear(buffer); bound = true;
                    batch.begin();
                    batch.setColor(1, 1, 1, 1);
                    drawOracleFrame(batch, oracleShader, assets, actor, body,
                            identity(), identityColor(), 0);
                    drawOracleFrame(batch, oracleShader, assets, actor, tint,
                            identity(), paletteColor(color), 0);
                    batch.setTransformMatrix(new Matrix4());
                    batch.end();
                    byte[] expected = ScreenUtils.getFrameBufferPixels(0, 0, 800, 600, false);
                    buffer.end(); bound = false;
                    int painted = 0;
                    for (int pixel = 0; pixel < actual.length; pixel += 4) {
                        if ((actual[pixel] & 255) < 245 || (actual[pixel + 1] & 255) < 245
                                || (actual[pixel + 2] & 255) < 245) painted++;
                        for (int channel = 0; channel < 3; channel++) {
                            int difference = Math.abs((actual[pixel + channel] & 255)
                                    - (expected[pixel + channel] & 255));
                            if (difference > 3) fail("Infantry RGB color=" + color + " pose=" + pose
                                    + " x=" + ((pixel / 4) % 800) + " y=" + ((pixel / 4) / 800)
                                    + " channel=" + channel + " difference=" + difference);
                        }
                    }
                    assertTrue("Exported infantry is painted", painted > 10);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    if (color == 5 && (pose == 1 || pose == 2)) {
                        save(actual, "infantry-green-" + BODY[pose] + "-actual.png");
                        save(expected, "infantry-green-" + BODY[pose] + "-oracle.png");
                    }
                }
            }
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

    private static FlashAnimationTimeline.Frame requiredFrame(OriginalFlashAnimationAssets assets,
                                                                String linkage, int index) {
        FlashAnimationTimeline timeline = assets.getTimeline(linkage);
        assertNotNull(linkage, timeline);
        assertTrue(linkage + " frame count", timeline.getFrameCount() > index);
        FlashAnimationTimeline.Frame frame = timeline.getFrame(index);
        assertNotNull(linkage + " frame", frame);
        return frame;
    }

    private static void requireLeaves(OriginalFlashAnimationAssets assets,
                                      FlashAnimationTimeline.Frame frame, int depth) {
        assertTrue("Nested infantry depth", depth < 8);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            if (assets.getShape(layer.shapeId) != null) continue;
            FlashAnimationTimeline child = assets.getTimelineBySymbolId(layer.shapeId);
            assertNotNull("Missing infantry leaf " + layer.shapeId, child);
            assertTrue(child.getFrameCount() > 0);
            requireLeaves(assets, child.getFrame(0), depth + 1);
        }
    }

    private static void drawOracleFrame(SpriteBatch batch, ShaderProgram shader,
                                         OriginalFlashAnimationAssets assets,
                                         FlashSoldierOneAnimation.Actor actor,
                                         FlashAnimationTimeline.Frame frame,
                                         float[] parent, float[] color, int depth) {
        assertTrue(depth < 8);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float[] child = multiply(parent, layer);
            float[] childColor = multiplyColor(color, layer.colorTransform);
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region != null) {
                Matrix4 matrix = new Matrix4();
                matrix.val[Matrix4.M00] = child[0];
                matrix.val[Matrix4.M01] = -child[2];
                matrix.val[Matrix4.M10] = -child[1];
                matrix.val[Matrix4.M11] = child[3];
                matrix.val[Matrix4.M03] = actor.sourceX + child[4];
                matrix.val[Matrix4.M13] = 600f - actor.sourceY - child[5];
                batch.setTransformMatrix(matrix);
                batch.flush();
                batch.setShader(shader);
                shader.setUniformf("u_mul", childColor[0], childColor[2], childColor[4], childColor[6]);
                shader.setUniformf("u_add", childColor[1], childColor[3], childColor[5], childColor[7]);
                batch.setColor(1, 1, 1, 1);
                batch.draw(region, assets.getShapeOriginX(layer.shapeId),
                        -assets.getShapeOriginY(layer.shapeId) - region.getRegionHeight(),
                        region.getRegionWidth(), region.getRegionHeight());
            } else {
                FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
                assertNotNull(nested);
                drawOracleFrame(batch, shader, assets, actor, nested.getFrame(0),
                        child, childColor, depth + 1);
            }
        }
    }

    private static float[] identity() { return new float[] {1, 0, 0, 1, 0, 0}; }
    private static float[] identityColor() { return new float[] {1, 0, 1, 0, 1, 0, 1, 0}; }
    private static float[] paletteColor(int color) { return PALETTE[color - 1].clone(); }

    private static float[] multiply(float[] parent, FlashAnimationTimeline.Layer child) {
        return new float[] {
                parent[0] * child.a + parent[2] * child.b,
                parent[1] * child.a + parent[3] * child.b,
                parent[0] * child.c + parent[2] * child.d,
                parent[1] * child.c + parent[3] * child.d,
                parent[0] * child.tx + parent[2] * child.ty + parent[4],
                parent[1] * child.tx + parent[3] * child.ty + parent[5]
        };
    }

    private static float[] multiplyColor(float[] parent, FlashColorTransform child) {
        return new float[] {
                parent[0] * child.redMultiplier, parent[0] * child.redAdd + parent[1],
                parent[2] * child.greenMultiplier, parent[2] * child.greenAdd + parent[3],
                parent[4] * child.blueMultiplier, parent[4] * child.blueAdd + parent[5],
                parent[6] * child.alphaMultiplier, parent[6] * child.alphaAdd + parent[7]
        };
    }

    private static void clear(FrameBuffer buffer) {
        buffer.begin();
        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private static void save(byte[] pixels, String name) {
        File directory = new File(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir(null), "beta-ui-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(800, 600, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(pixels).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, true);
        } finally { image.dispose(); }
    }
}
