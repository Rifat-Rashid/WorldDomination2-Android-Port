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

/** Exported-shape GLES oracle for a legal intercepted ship payload's painted effect. */
@RunWith(AndroidJUnit4.class)
public final class SourceCarrierExplosionRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final float STAGE_X = 300f;
    private static final float STAGE_Y = -100f;
    private static final Rectangle BOUNDS = new Rectangle(STAGE_X, STAGE_Y,
            WIDTH, HEIGHT);
    private static final int BIRTH_TICK = 238;
    private static final int EFFECT_TICK = 242;
    // Legal seed-0 turn-21 ship intercept: source unit1 at tick237 has action
    // explode and logical (58.5,165.75); killUnit attaches before its tick238 move.
    private static final float EXPLOSION_X = 58.5f;
    private static final float EXPLOSION_Y = 165.75f;
    // scene.json.gz: explosion001 symbol442 frame5 -> shape411, while
    // rocket_self explosion003 symbol1851 frame5 -> shape1818.
    private static final int SOURCE_SHAPE = 411;
    private static final int WRONG_SHAPE = 1818;

    @Test public void interceptedShipExplosionUsesPreMovePoseAndPaintedFrameFive()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceBiologicalCarriersInstrumentedTest.gameReadyForShip(1);
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            for (int tick = 1; tick <= BIRTH_TICK; tick++) advance(app);

            @SuppressWarnings("unchecked")
            List<SourceCarrierPlaneDisplayDepths> bindings =
                    (List<SourceCarrierPlaneDisplayDepths>) field(WorldDominationGame.class,
                            "sourceCarrierPlaneBindings").get(app);
            assertEquals("three source ship payload wrappers", 3, bindings.size());
            SourceCarrierPlaneDisplayDepths unit1 = null;
            for (SourceCarrierPlaneDisplayDepths binding : bindings)
                if (binding.animation.getRoute().planeIndex == 0) unit1 = binding;
            assertNotNull("physical unit1 effect owner", unit1);
            assertNotNull(unit1.explosion());
            assertTrue(unit1.explosion().isAttached());
            assertEquals("explosion001", unit1.getExplosionLinkage());
            assertEquals("new explosion starts on authored frame1", 0,
                    unit1.getExplosionFrameIndex());
            assertEquals(EXPLOSION_X, unit1.getExplosionX(), 0d);
            assertEquals(EXPLOSION_Y, unit1.getExplosionY(), 0d);
            assertTrue("killUnit has detached its body", !unit1.body().isAttached());
            for (int tick = BIRTH_TICK + 1; tick <= EFFECT_TICK; tick++) advance(app);
            assertTrue("retained effect remains attached", unit1.explosion().isAttached());
            assertEquals("four autoplay visits reach authored frame5", 4,
                    unit1.getExplosionFrameIndex());
            assertEquals(EXPLOSION_X, unit1.getExplosionX(), 0d);
            assertEquals(EXPLOSION_Y, unit1.getExplosionY(), 0d);

            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(
                    WorldDominationGame.class, "flashAnimationAssets").get(app);
            FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(
                    WorldDominationGame.class, "battleUnitRenderer").get(app);
            assertTrue(assets.isLoaded());
            assertEquals(442, assets.getTimeline("explosion001").symbolId);
            assertEquals(21, assets.getTimeline("explosion001").getFrameCount());
            assertEquals(SOURCE_SHAPE,
                    assets.getTimeline("explosion001").getFrame(4).layers[0].shapeId);
            assertEquals(WRONG_SHAPE,
                    assets.getTimeline("explosion003").getFrame(4).layers[0].shapeId);
            assertNotNull(assets.getShape(SOURCE_SHAPE));
            assertNotNull(assets.getShape(WRONG_SHAPE));

            SpriteBatch batch = new SpriteBatch();
            FrameBuffer buffer = null;
            ShaderProgram callerShader = null;
            try {
                buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
                callerShader = SpriteBatch.createDefaultShader();
                assertTrue(callerShader.getLog(), callerShader.isCompiled());
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0,
                        WIDTH, HEIGHT));
                final SourceCarrierPlaneDisplayDepths effect = unit1;
                final ShaderProgram previousShader = callerShader;
                byte[] actual = render(buffer, batch, () -> {
                    batch.setShader(previousShader);
                    batch.setColor(.31f, .47f, .69f, .61f);
                    float previousColor = batch.getPackedColor();
                    Matrix4 previousTransform = new Matrix4(batch.getTransformMatrix());
                    renderer.drawCarrierExplosion(batch, BOUNDS, effect);
                    assertSame("caller shader restored", previousShader, batch.getShader());
                    assertEquals("caller color restored", previousColor,
                            batch.getPackedColor(), 0f);
                    assertArrayEquals("caller matrix restored", previousTransform.val,
                            batch.getTransformMatrix().val, 0f);
                });
                byte[] expected = render(buffer, batch,
                        () -> drawExportedShape(batch, assets, SOURCE_SHAPE));
                byte[] wrong = render(buffer, batch,
                        () -> drawExportedShape(batch, assets, WRONG_SHAPE));
                evidence("source-carrier-t242-explosion-actual.png", actual);
                evidence("source-carrier-t242-explosion-expected.png", expected);
                evidence("source-carrier-t242-explosion-wrong003.png", wrong);
                int painted = 0;
                int wrongPixels = 0;
                for (int pixel = 0; pixel < expected.length; pixel += 4) {
                    boolean wrongHere = false;
                    for (int channel = 0; channel < 4; channel++) {
                        int expectedByte = expected[pixel + channel] & 255;
                        int actualByte = actual[pixel + channel] & 255;
                        int wrongByte = wrong[pixel + channel] & 255;
                        assertTrue("source frame5 RGBA at pixel " + pixel / 4
                                + " channel " + channel + " delta "
                                + Math.abs(actualByte - expectedByte),
                                Math.abs(actualByte - expectedByte) <= 3);
                        if (Math.abs(wrongByte - expectedByte) > 3) wrongHere = true;
                    }
                    if ((expected[pixel] & 255) < 250
                            || (expected[pixel + 1] & 255) < 250
                            || (expected[pixel + 2] & 255) < 250) painted++;
                    if (wrongHere) wrongPixels++;
                }
                assertTrue("original explosion001 must paint visible pixels", painted > 50);
                assertTrue("substituting explosion003 must change visible pixels",
                        wrongPixels > 0);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            } finally {
                if (buffer != null) buffer.dispose();
                if (callerShader != null) callerShader.dispose();
                batch.dispose();
            }
        });
    }

    /** One exported frame5 leaf, using source registration and no renderer traversal. */
    private static void drawExportedShape(SpriteBatch batch,
                                           OriginalFlashAnimationAssets assets, int shapeId) {
        TextureRegion region = assets.getShape(shapeId);
        assertNotNull(region);
        batch.setShader(null);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(region,
                STAGE_X + EXPLOSION_X + assets.getShapeOriginX(shapeId),
                STAGE_Y + HEIGHT - EXPLOSION_Y
                        - assets.getShapeOriginY(shapeId) - region.getRegionHeight(),
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
