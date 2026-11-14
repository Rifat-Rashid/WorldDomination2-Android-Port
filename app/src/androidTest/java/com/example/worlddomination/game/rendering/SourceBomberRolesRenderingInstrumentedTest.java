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
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** GPU oracle for the first painted frame of the legal source plane002 sortie. */
@RunWith(AndroidJUnit4.class)
public final class SourceBomberRolesRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    // Source tick 272 is at (2, 1): pan the unchanged 800 x 600 source stage
    // into the FBO so the entire first painted plane can be inspected.
    private static final float STAGE_X = 350f;
    private static final float STAGE_Y = -300f;
    private static final Rectangle BOUNDS = new Rectangle(STAGE_X, STAGE_Y,
            WIDTH, HEIGHT);
    private static final int TICK = 272;
    // scene.json.gz: plane002[10] -> shape818; plane002_tint[10] -> 2394;
    // plane002_shadow[1] -> nested plane002, whose mc seeks frame10 in View.f_37.
    private static final int BODY_SHAPE = 818;
    private static final int TINT_SHAPE = 2394;
    private static final int WRONG_SHADOW_SHAPE = 800;
    private static final float SHADOW_SCALE = .6999969482421875f;
    private static final float SHADOW_ALPHA = .69921875f;
    // WD_colors.green, setup seed 0: RGB multiplier/addition, alpha 75%.
    private static final float[] GREEN = {0f, .5f, 0f, .75f, 0f, 1f, 0f, 0f};
    private static final String VERTEX = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\nattribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_uv;\n"
            + "void main(){v_color=a_color;v_color.a*=255.0/254.0;"
            + "v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}";
    private static final String FRAGMENT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec4 v_color;varying vec2 v_uv;uniform sampler2D u_texture;"
            + "uniform vec4 u_mul;uniform vec4 u_add;"
            + "void main(){gl_FragColor=clamp(texture2D(u_texture,v_uv)*u_mul+u_add,0.0,1.0)*v_color;}";

    @Test public void firstVisibleBomberUsesNestedShadowAndSourceTint() throws Exception {
        JsonValue oracle;
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("source-plane002-success.json")) {
            oracle = new JsonReader().parse(input);
        }
        assertEquals("legal unforced plane2", oracle.getString("mode"));
        JsonValue source = oracle.get("rows").get(TICK);
        assertEquals(2d, source.get(2).get(0).asDouble(), 0d);
        assertEquals(1d, source.get(2).get(1).asDouble(), 0d);
        assertEquals(10, source.get(2).get(2).asInt());
        assertEquals(10d, source.get(2).get(3).asDouble(), 0d);
        assertEquals(50d, source.get(2).get(4).asDouble(), 0d);
        assertEquals(1, source.get(4).get(2).asInt());
        assertEquals(10, source.get(6).get(2).asInt());
        assertEquals(75d, source.get(6).get(3).asDouble(), 0d);
        assertEquals(10, source.get(13).asInt());

        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Method builder = SourceBomberPlaybackInstrumentedTest.class.getDeclaredMethod(
                    "legalGame", JsonValue.class);
            builder.setAccessible(true);
            GameState game = (GameState) builder.invoke(null, oracle);
            assertEquals("source setup color", 5, game.getPlayerState(0).getPlayerColor());
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class, "cityRenderer")
                    .get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            for (int tick = 1; tick <= TICK; tick++)
                invoke(app, "fixedUpdate", float.class, 1f / 24f);

            @SuppressWarnings("unchecked")
            List<SourceInterceptedAircraftDisplayDepths> bindings =
                    (List<SourceInterceptedAircraftDisplayDepths>) field(
                            WorldDominationGame.class,
                            "sourceInterceptedAircraftBindings").get(app);
            assertEquals(1, bindings.size());
            SourceInterceptedAircraftDisplayDepths binding = bindings.get(0);
            assertTrue(binding.body().isAttached());
            assertTrue(binding.shadow().isAttached());
            assertTrue(binding.tint().isAttached());
            assertTrue(binding.body().getDepth() < binding.shadow().getDepth());
            assertTrue(binding.shadow().getDepth() < binding.tint().getDepth());
            FlashStandalonePlaneAnimation.Actor actor = binding.animation.getActor();
            assertTrue(actor.visible);
            assertEquals(2f, actor.sourceX, .001f);
            assertEquals(1f, actor.sourceY, .001f);
            assertEquals(9, actor.sourceFrame);
            assertEquals(9, actor.sourceTintFrame);
            assertEquals(.1f, actor.alpha, .0001f);
            assertEquals(.5f, actor.scale, .0001f);
            assertEquals(0f, actor.shadowYOffset, .0001f);
            assertEquals(.75f, binding.animation.getTint().alphaMultiplier, .0001f);
            assertEquals(1, binding.animation.getRoute().targetPlayerId);

            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(
                    WorldDominationGame.class, "flashAnimationAssets").get(app);
            FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(
                    WorldDominationGame.class, "battleUnitRenderer").get(app);
            assertTrue(assets.isLoaded());
            assertEquals(847, assets.getTimeline("plane002").symbolId);
            assertEquals(2423, assets.getTimeline("plane002_tint").symbolId);
            assertEquals(848, assets.getTimeline("plane002_shadow").symbolId);
            assertEquals(BODY_SHAPE,
                    assets.getTimeline("plane002").getFrame(9).layers[0].shapeId);
            assertEquals(TINT_SHAPE,
                    assets.getTimeline("plane002_tint").getFrame(9).layers[0].shapeId);
            FlashAnimationTimeline.Layer shadowLayer = assets.getTimeline("plane002_shadow")
                    .getFrame(0).layers[0];
            assertEquals(847, shadowLayer.shapeId);
            assertEquals(SHADOW_SCALE, shadowLayer.a, 0f);
            assertEquals(SHADOW_SCALE, shadowLayer.d, 0f);
            assertEquals(-1f, shadowLayer.tx, 0f);
            assertEquals(.6f, shadowLayer.ty, 0f);
            assertEquals(-1f, shadowLayer.colorTransform.redMultiplier, 0f);
            assertEquals(SHADOW_ALPHA, shadowLayer.colorTransform.alphaMultiplier, 0f);
            assertEquals(WRONG_SHADOW_SHAPE,
                    assets.getTimelineBySymbolId(847).getFrame(0).layers[0].shapeId);

            SpriteBatch batch = new SpriteBatch();
            FrameBuffer buffer = null;
            ShaderProgram expectedShader = null;
            ShaderProgram callerShader = null;
            try {
                buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
                expectedShader = new ShaderProgram(VERTEX, FRAGMENT);
                callerShader = SpriteBatch.createDefaultShader();
                assertTrue(expectedShader.getLog(), expectedShader.isCompiled());
                assertTrue(callerShader.getLog(), callerShader.isCompiled());
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
                final ShaderProgram oracleShader = expectedShader;
                final ShaderProgram priorShader = callerShader;
                byte[] actual = render(buffer, batch, () -> {
                    batch.setShader(priorShader);
                    batch.setColor(.31f, .47f, .69f, .61f);
                    float priorColor = batch.getPackedColor();
                    Matrix4 priorTransform = new Matrix4(batch.getTransformMatrix());
                    drawActualRole(renderer, batch, binding, BattlefieldDisplayState.Kind.UNIT_BODY,
                            priorShader, priorColor, priorTransform);
                    drawActualRole(renderer, batch, binding, BattlefieldDisplayState.Kind.UNIT_SHADOW,
                            priorShader, priorColor, priorTransform);
                    drawActualRole(renderer, batch, binding, BattlefieldDisplayState.Kind.UNIT_TINT,
                            priorShader, priorColor, priorTransform);
                });
                byte[] expected = render(buffer, batch,
                        () -> drawOracle(batch, oracleShader, assets, BODY_SHAPE, true));
                byte[] noTint = render(buffer, batch,
                        () -> drawOracle(batch, oracleShader, assets, BODY_SHAPE, false));
                byte[] wrongShadow = render(buffer, batch,
                        () -> drawOracle(batch, oracleShader, assets, WRONG_SHADOW_SHAPE, true));
                evidence("source-bomber-t272-roles-actual.png", actual);
                evidence("source-bomber-t272-roles-expected.png", expected);
                evidence("source-bomber-t272-roles-no-tint.png", noTint);
                evidence("source-bomber-t272-roles-wrong-shadow-frame.png", wrongShadow);
                assertPixels(actual, expected);
                assertTrue("omitting source tint must alter painted pixels",
                        changedPixels(expected, noTint) > 0);
                assertTrue("shadow nested frame1 must alter painted pixels",
                        changedPixels(expected, wrongShadow) > 0);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                if (buffer != null) buffer.dispose();
                if (expectedShader != null) expectedShader.dispose();
                if (callerShader != null) callerShader.dispose();
                batch.dispose();
            }
        });
    }

    private static void drawActualRole(FlashBattleUnitRenderer renderer, SpriteBatch batch,
            SourceInterceptedAircraftDisplayDepths binding, BattlefieldDisplayState.Kind role,
            ShaderProgram callerShader, float callerColor, Matrix4 callerTransform) {
        renderer.drawInterceptedAircraftRole(batch, BOUNDS, binding, role);
        assertSame("caller shader " + role, callerShader, batch.getShader());
        assertEquals("caller color " + role, callerColor, batch.getPackedColor(), 0f);
        assertArrayEquals("caller transform " + role, callerTransform.val,
                batch.getTransformMatrix().val, 0f);
    }

    /** Leaf IDs, matrices and colors are literal source observations, not renderer traversal. */
    private static void drawOracle(SpriteBatch batch, ShaderProgram shader,
            OriginalFlashAnimationAssets assets, int shadowShape, boolean includeTint) {
        drawLeaf(batch, shader, assets, BODY_SHAPE, 2f, 1f, .5f, .1f,
                1f, 1f, 1f, 0f, 0f, 0f);
        drawLeaf(batch, shader, assets, shadowShape, 2f, 1f,
                .5f * SHADOW_SCALE, SHADOW_ALPHA,
                0f, 0f, 0f, -1f, .6f, 0f);
        if (includeTint)
            drawLeaf(batch, shader, assets, TINT_SHAPE, 2f, 1f, .5f,
                    GREEN[3], GREEN[0], GREEN[1], GREEN[2],
                    0f, 0f, GREEN[5]);
    }

    private static void drawLeaf(SpriteBatch batch, ShaderProgram shader,
            OriginalFlashAnimationAssets assets, int shapeId,
            float rootX, float rootY, float scale, float alpha,
            float red, float green, float blue, float tx, float ty, float greenAdd) {
        TextureRegion region = assets.getShape(shapeId);
        assertNotNull("exported shape " + shapeId, region);
        float rootScale = .5f;
        // Source stage translation is explicit and independent of the production
        // island projection; source scale, source pose and artwork stay unchanged.
        float x = STAGE_X + rootX + rootScale * tx;
        float y = STAGE_Y + HEIGHT - (rootY + rootScale * ty);
        Matrix4 transform = new Matrix4();
        transform.val[Matrix4.M00] = scale;
        transform.val[Matrix4.M11] = scale;
        transform.val[Matrix4.M03] = x;
        transform.val[Matrix4.M13] = y;
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
                int difference = Math.abs((actual[pixel + channel] & 255)
                        - (expected[pixel + channel] & 255));
                if (difference > 3) fail("bomber pixel (" + ((pixel / 4) % WIDTH)
                        + "," + ((pixel / 4) / WIDTH) + ") channel " + channel
                        + " delta " + difference);
            }
            if ((expected[pixel] & 255) < 250 || (expected[pixel + 1] & 255) < 250
                    || (expected[pixel + 2] & 255) < 250) painted++;
        }
        assertTrue("source bomber must paint a measurable body", painted > 100);
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

    private static void invoke(WorldDominationGame app, String name, Class<?> type,
                               Object value) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(app, value);
    }

    private interface Draw { void draw() throws Exception; }
}
