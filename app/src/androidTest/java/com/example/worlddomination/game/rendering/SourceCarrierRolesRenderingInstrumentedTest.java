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
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Independent exported-leaf GLES oracle for the carrier's three overlapping roles. */
@RunWith(AndroidJUnit4.class)
public final class SourceCarrierRolesRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final Rectangle BOUNDS = new Rectangle(0f, 0f, WIDTH, HEIGHT);
    private static final int TICK = 195;
    // Legal seed-0 turn-21 intercepted ship, source unit1 first active record visit.
    private static final float X = 392.5f;
    private static final float Y = 105.75f;
    private static final int AUTHORED_FRAME = 11;
    // scene.json.gz: ship_plane[11] and plane001_shadow.mc[11] -> shape369;
    // plane001_tint[11] -> shape870. Shadow outer frame1 child tx=.35,ty=2.
    private static final int BODY_SHAPE = 369;
    private static final int TINT_SHAPE = 870;
    private static final float SHADOW_ALPHA = .51953125f;
    private static final String VERTEX = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\nattribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_uv;\n"
            + "void main(){v_color=a_color;v_color.a*=255.0/254.0;"
            + "v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}";
    private static final String FRAGMENT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec4 v_color;varying vec2 v_uv;uniform sampler2D u_texture;"
            + "uniform vec4 u_mul;uniform vec4 u_add;"
            + "void main(){gl_FragColor=clamp(texture2D(u_texture,v_uv)*u_mul+u_add,0.0,1.0)*v_color;}";

    @Test public void carrierBodyShadowTintUseOriginalAffinesAndPalette()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceBiologicalCarriersInstrumentedTest.gameReadyForShip(1);
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            for (int tick = 1; tick <= TICK; tick++) advance(app);

            @SuppressWarnings("unchecked")
            List<SourceCarrierPlaneDisplayDepths> bindings =
                    (List<SourceCarrierPlaneDisplayDepths>) field(WorldDominationGame.class,
                            "sourceCarrierPlaneBindings").get(app);
            SourceCarrierPlaneDisplayDepths unit1 = null;
            for (SourceCarrierPlaneDisplayDepths binding : bindings)
                if (binding.animation.getRoute().planeIndex == 0) unit1 = binding;
            assertNotNull("first physical source Unit", unit1);
            SourceCarrierPlanePlayback core = unit1.animation.getSourcePlayback();
            assertEquals(X, core.getBodyRole().getX(), 0d);
            assertEquals(Y, core.getBodyRole().getY(), 0d);
            assertEquals(AUTHORED_FRAME, core.getBodyRole().getFrame());
            assertEquals(AUTHORED_FRAME, core.getTintRole().getFrame());
            assertEquals(AUTHORED_FRAME, core.getShadowRole().getNestedFrame());
            assertEquals(1, core.getShadowRole().getFrame());
            assertEquals(50d, core.getBodyRole().getScale(), 0d);
            assertEquals(50d, core.getShadowRole().getScale(), 0d);
            assertEquals(50d, core.getTintRole().getScale(), 0d);
            assertEquals(100d, core.getBodyRole().getAlpha(), 0d);
            assertEquals(75d, core.getTintRole().getAlpha(), 0d);
            assertEquals(100d, core.getShadowRole().getAlpha(), 0d);
            assertTrue(core.getShadowRole().isVisible());
            assertTrue(unit1.body().isAttached());
            assertTrue(unit1.shadow().isAttached());
            assertTrue(unit1.tint().isAttached());

            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(
                    WorldDominationGame.class, "flashAnimationAssets").get(app);
            FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(
                    WorldDominationGame.class, "battleUnitRenderer").get(app);
            assertTrue(assets.isLoaded());
            assertEquals(396, assets.getTimeline("ship_plane").symbolId);
            assertEquals(400, assets.getTimeline("plane001_shadow").symbolId);
            assertEquals(897, assets.getTimeline("plane001_tint").symbolId);
            assertEquals(BODY_SHAPE, assets.getTimeline("ship_plane")
                    .getFrame(AUTHORED_FRAME - 1).layers[0].shapeId);
            assertEquals(TINT_SHAPE, assets.getTimeline("plane001_tint")
                    .getFrame(AUTHORED_FRAME - 1).layers[0].shapeId);
            FlashAnimationTimeline.Layer shadowLayer = assets.getTimeline("plane001_shadow")
                    .getFrame(0).layers[0];
            assertEquals(399, shadowLayer.shapeId);
            assertEquals(.35f, shadowLayer.tx, 0f);
            assertEquals(2f, shadowLayer.ty, 0f);
            assertEquals(-1f, shadowLayer.colorTransform.redMultiplier, 0f);
            assertEquals(SHADOW_ALPHA,
                    shadowLayer.colorTransform.alphaMultiplier, 0f);
            assertEquals(BODY_SHAPE, assets.getTimelineBySymbolId(399)
                    .getFrame(AUTHORED_FRAME - 1).layers[0].shapeId);
            assertNotNull(assets.getShape(BODY_SHAPE));
            assertNotNull(assets.getShape(TINT_SHAPE));

            SpriteBatch batch = new SpriteBatch();
            FrameBuffer buffer = null;
            ShaderProgram oracleShader = null;
            ShaderProgram callerShader = null;
            try {
                buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
                oracleShader = new ShaderProgram(VERTEX, FRAGMENT);
                callerShader = SpriteBatch.createDefaultShader();
                assertTrue(oracleShader.getLog(), oracleShader.isCompiled());
                assertTrue(callerShader.getLog(), callerShader.isCompiled());
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0,
                        WIDTH, HEIGHT));
                final SourceCarrierPlaneDisplayDepths roles = unit1;
                final ShaderProgram previousShader = callerShader;
                final ShaderProgram expectedShader = oracleShader;
                byte[] actual = render(buffer, batch, () -> {
                    batch.setShader(previousShader);
                    batch.setColor(.31f, .47f, .69f, .61f);
                    float previousColor = batch.getPackedColor();
                    Matrix4 previousTransform = new Matrix4(batch.getTransformMatrix());
                    drawActualRole(renderer, batch, roles,
                            BattlefieldDisplayState.Kind.UNIT_BODY,
                            previousShader, previousColor, previousTransform);
                    drawActualRole(renderer, batch, roles,
                            BattlefieldDisplayState.Kind.UNIT_SHADOW,
                            previousShader, previousColor, previousTransform);
                    drawActualRole(renderer, batch, roles,
                            BattlefieldDisplayState.Kind.UNIT_TINT,
                            previousShader, previousColor, previousTransform);
                });
                byte[] expected = render(buffer, batch,
                        () -> drawOracle(batch, expectedShader, assets, true, true));
                byte[] noTint = render(buffer, batch,
                        () -> drawOracle(batch, expectedShader, assets, false, true));
                byte[] noShadow = render(buffer, batch,
                        () -> drawOracle(batch, expectedShader, assets, true, false));
                evidence("source-carrier-t195-roles-actual.png", actual);
                evidence("source-carrier-t195-roles-expected.png", expected);
                evidence("source-carrier-t195-roles-no-tint.png", noTint);
                evidence("source-carrier-t195-roles-no-shadow.png", noShadow);
                assertPixels(actual, expected);
                assertTrue("source tint must alter visible pixels",
                        changedPixels(expected, noTint) > 0);
                assertTrue("source nested shadow must alter visible pixels",
                        changedPixels(expected, noShadow) > 0);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                if (buffer != null) buffer.dispose();
                if (oracleShader != null) oracleShader.dispose();
                if (callerShader != null) callerShader.dispose();
                batch.dispose();
            }
        });
    }

    private static void drawActualRole(FlashBattleUnitRenderer renderer, SpriteBatch batch,
            SourceCarrierPlaneDisplayDepths binding, BattlefieldDisplayState.Kind kind,
            ShaderProgram shader, float color, Matrix4 transform) {
        renderer.drawCarrierRole(batch, BOUNDS, binding, kind);
        assertSame("caller shader " + kind, shader, batch.getShader());
        assertEquals("caller color " + kind, color, batch.getPackedColor(), 0f);
        assertArrayEquals("caller transform " + kind, transform.val,
                batch.getTransformMatrix().val, 0f);
    }

    /** Exported leaf identities, one affine child and palette; no renderer traversal. */
    private static void drawOracle(SpriteBatch batch, ShaderProgram shader,
            OriginalFlashAnimationAssets assets, boolean tint, boolean shadow) {
        drawLeaf(batch, shader, assets, BODY_SHAPE, 0f, 0f,
                1f, 1f, 1f, 1f, 0f);
        if (shadow)
            drawLeaf(batch, shader, assets, BODY_SHAPE, .35f, 2f,
                    -1f, -1f, -1f, SHADOW_ALPHA, 0f);
        if (tint)
            drawLeaf(batch, shader, assets, TINT_SHAPE, 0f, 0f,
                    0f, .5f, 0f, .75f, 1f);
    }

    private static void drawLeaf(SpriteBatch batch, ShaderProgram shader,
            OriginalFlashAnimationAssets assets, int shapeId, float tx, float ty,
            float red, float green, float blue, float alpha, float greenAdd) {
        TextureRegion region = assets.getShape(shapeId);
        assertNotNull("exported shape " + shapeId, region);
        float scale = .5f;
        Matrix4 transform = new Matrix4();
        transform.val[Matrix4.M00] = scale;
        transform.val[Matrix4.M11] = scale;
        transform.val[Matrix4.M03] = X + scale * tx;
        transform.val[Matrix4.M13] = HEIGHT - (Y + scale * ty);
        batch.flush();
        batch.setTransformMatrix(transform);
        batch.setShader(shader);
        shader.setUniformf("u_mul", red, green, blue, alpha);
        shader.setUniformf("u_add", 0f, greenAdd, 0f, 0f);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(region, assets.getShapeOriginX(shapeId),
                -assets.getShapeOriginY(shapeId) - region.getRegionHeight(),
                region.getRegionWidth(), region.getRegionHeight());
    }

    private static byte[] render(FrameBuffer buffer, SpriteBatch batch, Draw draw)
            throws Exception {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);
            batch.setTransformMatrix(new Matrix4().idt());
            batch.setShader(null);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.begin();
            try { draw.draw(); } finally { batch.end(); }
            return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
        } finally { buffer.end(); }
    }

    private static void assertPixels(byte[] actual, byte[] expected) {
        assertEquals(expected.length, actual.length);
        int painted = 0;
        for (int pixel = 0; pixel < actual.length; pixel += 4) {
            for (int channel = 0; channel < 4; channel++) {
                int delta = Math.abs((actual[pixel + channel] & 255)
                        - (expected[pixel + channel] & 255));
                if (delta > 3) fail("carrier pixel (" + ((pixel / 4) % WIDTH)
                        + "," + ((pixel / 4) / WIDTH) + ") channel " + channel
                        + " delta " + delta);
            }
            if ((expected[pixel] & 255) < 250 || (expected[pixel + 1] & 255) < 250
                    || (expected[pixel + 2] & 255) < 250) painted++;
        }
        assertTrue("source aircraft roles paint measurable art", painted > 100);
    }

    private static int changedPixels(byte[] first, byte[] second) {
        assertEquals(first.length, second.length);
        int changed = 0;
        for (int pixel = 0; pixel < first.length; pixel += 4) {
            for (int channel = 0; channel < 4; channel++) {
                if (Math.abs((first[pixel + channel] & 255)
                        - (second[pixel + channel] & 255)) > 3) {
                    changed++;
                    break;
                }
            }
        }
        return changed;
    }

    private static void evidence(String name, byte[] rgba) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, false);
        } finally { image.dispose(); }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }

    private interface Draw { void draw() throws Exception; }
}
