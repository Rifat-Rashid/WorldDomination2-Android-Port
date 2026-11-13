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
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.io.File;
import java.util.Map;

import static org.junit.Assert.*;

/** Exported body/tint/shadow leaf oracle. Poses are synthetic and make no timing claim. */
@RunWith(AndroidJUnit4.class)
public final class ApacheRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final String[] COLORS = {"blue", "orange", "green"};
    // WD_colors source multipliers and byte additions.
    private static final float[][] PALETTE = {
            {.15f, 0f, .74f, 0f, 1f, 1f, 1f, 0f},
            {1f, 91f / 255f, .62f, 40f / 255f, 0f, 0f, 1f, 0f},
            {0f, 0f, .50f, 1f, 0f, 0f, .75f, 0f}
    };
    private static final String[] BODY = {
            "apache", "apache", "marine", "soldier001_shoot", "apache_shadow"
    };
    private static final String[] TINT = {
            "apache_tint", "apache_tint", "soldier001_tint", "soldier001_shoot_tint", null
    };
    // Zero-based Actor.sourceFrame/getFrame indices. They are authored Flash
    // frames 8, 32, 6, and 8 respectively. All eight body/tint selections are
    // painted in scene.json.gz; shoot indices 6 and 8 are intentionally blank.
    private static final int[] FRAME_INDEX = {7, 31, 5, 7, 6};
    private static final float[] SCALE = {.5f, 1f, 1f, 1f, .5f};
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

    @Test public void apacheAndMarineBodiesTintsAndShadowMatchExportedAffineLeaves()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(ApacheRenderingInstrumentedTest::verify);
    }

    private static void verify(WorldDominationGame game) throws Exception {
        OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class,
                        "flashAnimationAssets").get(game);
        assertTrue(assets.isLoaded());
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
            assertTrue(oracleShader.getLog(), oracleShader.isCompiled());
            assertTrue(callerShader.getLog(), callerShader.isCompiled());
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
            Rectangle bounds = new Rectangle(0, 0, WIDTH, HEIGHT);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
            for (int color = 0; color < COLORS.length; color++) {
                FlashBattleRoutes.ApacheMarineAttack route =
                        FlashBattleRoutes.createSoldierTwoAttack(0, 2, 0, 1, anchors,
                                new ReferenceRandom(100 + color));
                FlashApacheMarineAnimation animation = new FlashApacheMarineAnimation(route,
                        new ReferenceRandom(200 + color), null, 0, COLORS[color]);
                for (int pose = 0; pose < BODY.length; pose++) {
                    for (int i = 0; i < animation.getActorCount(); i++)
                        animation.getActor(i).visible = false;
                    animation.getApacheShadow().visible = false;
                    FlashApacheMarineAnimation.Actor actor = pose == 4
                            ? animation.getApacheShadow() : animation.getActor(pose < 2 ? 0 : 1);
                    actor.visible = true;
                    actor.linkage = BODY[pose];
                    actor.sourceFrame = FRAME_INDEX[pose];
                    actor.sourceX = 400f;
                    actor.sourceY = 300f;
                    actor.islandPlayerId = 0;
                    actor.scale = SCALE[pose];
                    actor.alpha = 1f;
                    // The shadow parent stays on frame 1; its child seeks the body frame.
                    FlashAnimationTimeline.Frame body = required(assets, BODY[pose],
                            pose == 4 ? 0 : FRAME_INDEX[pose]);
                    FlashAnimationTimeline.Frame tint = TINT[pose] == null ? null
                            : required(assets, TINT[pose], FRAME_INDEX[pose]);
                    requireLeaves(assets, body, FRAME_INDEX[pose], 0);
                    if (tint != null) requireLeaves(assets, tint, FRAME_INDEX[pose], 0);

                    clear(buffer); bound = true;
                    Matrix4 callerTransform = new Matrix4().setToTranslation(3f, -2f, 0f);
                    batch.setTransformMatrix(callerTransform);
                    batch.setShader(callerShader);
                    batch.setColor(.3f, .5f, .7f, .8f);
                    float callerColor = batch.getPackedColor();
                    batch.begin();
                    renderer.drawApacheMarine(batch, bounds, Collections.singletonList(animation));
                    ShaderProgram restoredShader = batch.getShader();
                    float restoredColor = batch.getPackedColor();
                    Matrix4 restoredTransform = new Matrix4(batch.getTransformMatrix());
                    batch.end();
                    byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
                    buffer.end(); bound = false;

                    clear(buffer); bound = true;
                    batch.begin();
                    batch.setColor(1f, 1f, 1f, 1f);
                    float[] root = {SCALE[pose], 0f, 0f, SCALE[pose], 0f, 0f};
                    oracle(batch, oracleShader, assets, bounds, actor, body, FRAME_INDEX[pose],
                            root, identityColor(), callerTransform, 0);
                    batch.end();
                    byte[] bodyOnly = ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
                    buffer.end(); bound = false;

                    clear(buffer); bound = true;
                    batch.begin();
                    oracle(batch, oracleShader, assets, bounds, actor, body, FRAME_INDEX[pose],
                            root, identityColor(), callerTransform, 0);
                    if (tint != null) {
                        oracle(batch, oracleShader, assets, bounds, actor, tint, FRAME_INDEX[pose],
                                root, PALETTE[color].clone(), callerTransform, 0);
                    }
                    batch.end();
                    byte[] expected = ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
                    buffer.end(); bound = false;
                    if ("green".equals(COLORS[color])) {
                        writeEvidence("apache-pose-" + pose + "-actual.png", actual);
                        writeEvidence("apache-pose-" + pose + "-source-leaves.png", expected);
                    }
                    assertPixels(actual, expected, color, pose);
                    assertSame(callerShader, restoredShader);
                    assertEquals(callerColor, restoredColor, 0f);
                    assertArrayEquals(callerTransform.val, restoredTransform.val, 0f);
                    if (tint != null) {
                        assertTrue("Tint contributes pixels " + COLORS[color] + " pose " + pose,
                                changedPixels(bodyOnly, expected) > 10);
                    } else {
                        int shadowPixels = 0;
                        for (int offset = 0; offset < expected.length; offset += 4) {
                            int red = expected[offset] & 255;
                            if (red < 250) {
                                assertTrue("Authored shadow stays translucent", red >= 130);
                                shadowPixels++;
                            }
                        }
                        assertTrue("Authored shadow paints pixels", shadowPixels > 10);
                    }
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                }
            }
            FlashBattleRoutes.ApacheMarineAttack emptyRoute =
                    FlashBattleRoutes.createSoldierTwoAttack(0, 2, 0, 1, anchors,
                            new ReferenceRandom(18));
            FlashApacheMarineAnimation hidden = new FlashApacheMarineAnimation(emptyRoute,
                    new ReferenceRandom(19), null, 0, "green");
            for (int actor = 0; actor < hidden.getActorCount(); actor++) {
                hidden.getActor(actor).visible = false;
            }
            hidden.getApacheShadow().visible = false;
            batch.setShader(null);
            batch.begin();
            batch.draw(assets.getShape(728), 0f, 0f);
            int queuedCalls = batch.totalRenderCalls;
            renderer.drawApacheMarine(batch, new Rectangle(0, 0, WIDTH, HEIGHT),
                    Collections.emptyList());
            assertEquals("Empty Apache list keeps queued sprites batched", queuedCalls,
                    batch.totalRenderCalls);
            renderer.drawApacheMarine(batch, new Rectangle(0, 0, WIDTH, HEIGHT),
                    Collections.singletonList(hidden));
            assertEquals("Detached Apache clips keep queued sprites batched", queuedCalls,
                    batch.totalRenderCalls);
            batch.end();
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

    private static FlashAnimationTimeline.Frame required(OriginalFlashAnimationAssets assets,
                                                          String linkage, int frame) {
        FlashAnimationTimeline timeline = assets.getTimeline(linkage);
        assertNotNull(linkage, timeline);
        assertTrue(linkage + " frame " + frame, timeline.getFrameCount() > frame);
        FlashAnimationTimeline.Frame selected = timeline.getFrame(frame);
        assertNotNull(linkage + " frame " + frame, selected);
        return selected;
    }

    private static void writeEvidence(String name, byte[] rgba) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, true);
        } finally {
            image.dispose();
        }
    }

    private static void requireLeaves(OriginalFlashAnimationAssets assets,
                                      FlashAnimationTimeline.Frame frame, int nestedFrame,
                                      int depth) {
        assertTrue("Nested Apache depth", depth < 8);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            if (assets.getShape(layer.shapeId) != null) continue;
            FlashAnimationTimeline child = assets.getTimelineBySymbolId(layer.shapeId);
            assertNotNull("Missing Apache child " + layer.shapeId, child);
            requireLeaves(assets, child.getFrame(Math.min(nestedFrame,
                    child.getFrameCount() - 1)), nestedFrame, depth + 1);
        }
    }

    private static void oracle(SpriteBatch batch, ShaderProgram shader,
                               OriginalFlashAnimationAssets assets, Rectangle bounds,
                               FlashApacheMarineAnimation.Actor actor,
                               FlashAnimationTimeline.Frame frame, int nestedFrame,
                               float[] parent, float[] color, Matrix4 caller, int depth) {
        assertTrue(depth < 8);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float[] child = multiply(parent, layer);
            float[] childColor = multiplyColor(color, layer.colorTransform);
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region == null) {
                FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
                assertNotNull(nested);
                oracle(batch, shader, assets, bounds, actor,
                        nested.getFrame(Math.min(nestedFrame, nested.getFrameCount() - 1)),
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
                    actor.sourceX + child[4], actor.islandPlayerId);
            matrix.val[Matrix4.M13] = BattlefieldCityLayout.sourceY(bounds,
                    actor.sourceY + child[5]);
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

    private static int changedPixels(byte[] a, byte[] b) {
        int changed = 0;
        for (int i = 0; i < a.length; i += 4) {
            if (Math.abs((a[i] & 255) - (b[i] & 255)) > 3
                    || Math.abs((a[i + 1] & 255) - (b[i + 1] & 255)) > 3
                    || Math.abs((a[i + 2] & 255) - (b[i + 2] & 255)) > 3) changed++;
        }
        return changed;
    }

    private static void assertPixels(byte[] actual, byte[] expected, int color, int pose) {
        assertEquals(expected.length, actual.length);
        int painted = 0;
        for (int i = 0; i < actual.length; i += 4) {
            if ((actual[i] & 255) < 245 || (actual[i + 1] & 255) < 245
                    || (actual[i + 2] & 255) < 245) painted++;
            for (int channel = 0; channel < 3; channel++) {
                int difference = Math.abs((actual[i + channel] & 255)
                        - (expected[i + channel] & 255));
                if (difference > 3) fail("Apache RGB color=" + COLORS[color]
                        + " pose=" + pose + " x=" + ((i / 4) % WIDTH)
                        + " y=" + ((i / 4) / WIDTH) + " channel=" + channel
                        + " difference=" + difference);
            }
        }
        assertTrue("Apache/marine body painted", painted > 10);
    }
}
