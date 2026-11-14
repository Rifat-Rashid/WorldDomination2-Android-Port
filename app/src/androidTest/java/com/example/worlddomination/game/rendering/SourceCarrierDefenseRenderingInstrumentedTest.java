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

/** Original SWF leaf oracle for the legal carrier's shared, nested defense turret. */
@RunWith(AndroidJUnit4.class)
public final class SourceCarrierDefenseRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final Rectangle BOUNDS = new Rectangle(0f, 0f, WIDTH, HEIGHT);
    private static final int TARGET_PLAYER = 1;
    private static final int ACTION_SLOT = 2;
    private static final int TURRET_SYMBOL = 5553;
    // Original scene: ab1_2=(130.75,91.75), actionBuildings5658
    // antiAir_fire frame10 -> religion5625 chr frame4 -> turret5553.
    // Both placements and frame17/18 shape placements are identity affine.
    private static final float BUILDING_X = 130.75f;
    private static final float BUILDING_Y = 91.75f;
    private static final float PARENT_TX = 54.7f;
    private static final float PARENT_TY = 6.55f;
    private static final float CHILD_TX = -77.5f;
    private static final float CHILD_TY = -61.5f;

    @Test public void legalCarrierTurretPaintsHostFrame18ThenStoppedFrame17()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceBiologicalCarriersInstrumentedTest.gameReadyForShip(1);
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(
                    WorldDominationGame.class, "flashAnimationAssets").get(app);
            FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(
                    WorldDominationGame.class, "battleUnitRenderer").get(app);
            assertTrue(assets.isLoaded());
            assertEquals(5658, assets.getTimeline("actionBuildings").symbolId);
            assertEquals(TURRET_SYMBOL, assets.getTimelineBySymbolId(TURRET_SYMBOL).symbolId);
            assertEquals(5514, assets.getTimelineBySymbolId(TURRET_SYMBOL)
                    .getFrame(16).layers[0].shapeId);
            assertEquals(5516, assets.getTimelineBySymbolId(TURRET_SYMBOL)
                    .getFrame(17).layers[0].shapeId);

            SpriteBatch batch = new SpriteBatch();
            FrameBuffer buffer = null;
            ShaderProgram callerShader = null;
            try {
                buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT,
                        false, true);
                callerShader = SpriteBatch.createDefaultShader();
                assertTrue(callerShader.getLog(), callerShader.isCompiled());
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0,
                        WIDTH, HEIGHT));
                int currentTick = 0;
                for (int targetTick : new int[] {195, 269}) {
                    while (currentTick < targetTick) {
                        advance(app);
                        currentTick++;
                    }
                    FlashActionBuildingAnimation building = defenseBuilding(app);
                    assertEquals(TARGET_PLAYER, building.playerId);
                    assertEquals(ACTION_SLOT, building.slot);
                    assertEquals("antiAir_fire", building.frameName);
                    assertEquals("chr", building.religionCode);
                    assertEquals(1f, building.getAlpha(), 0f);
                    int sourceFrame = targetTick == 195 ? 18 : 17;
                    assertEquals("source nested host frame at tick " + targetTick,
                            sourceFrame - 1,
                            building.getNestedFrameIndex(TURRET_SYMBOL, 50));
                    int shape = targetTick == 195 ? 5516 : 5514;
                    int wrongShape = targetTick == 195 ? 5514 : 5516;
                    assertNotNull(assets.getShape(shape));
                    assertNotNull(assets.getShape(wrongShape));
                    final ShaderProgram shader = callerShader;
                    final FlashActionBuildingAnimation selected = building;
                    byte[] actual = render(buffer, batch, () -> {
                        batch.setShader(shader);
                        batch.setColor(.31f, .47f, .69f, .61f);
                        float color = batch.getPackedColor();
                        Matrix4 transform = new Matrix4(batch.getTransformMatrix());
                        renderer.drawActionBuilding(batch, BOUNDS, selected);
                        assertSame("caller shader retained", shader, batch.getShader());
                        assertEquals("caller color restored", color,
                                batch.getPackedColor(), 0f);
                        assertArrayEquals("caller transform retained", transform.val,
                                batch.getTransformMatrix().val, 0f);
                    });
                    byte[] expected = render(buffer, batch,
                            () -> drawSourceLeaf(batch, assets, shape));
                    byte[] wrong = render(buffer, batch,
                            () -> drawSourceLeaf(batch, assets, wrongShape));
                    evidence("source-carrier-defense-t" + targetTick + "-actual.png", actual);
                    evidence("source-carrier-defense-t" + targetTick + "-expected.png", expected);
                    evidence("source-carrier-defense-t" + targetTick + "-wrong-frame.png", wrong);
                    int painted = 0, negative = 0;
                    for (int pixel = 0; pixel < expected.length; pixel += 4) {
                        boolean changed = false;
                        for (int channel = 0; channel < 4; channel++) {
                            int real = actual[pixel + channel] & 255;
                            int oracle = expected[pixel + channel] & 255;
                            int substituted = wrong[pixel + channel] & 255;
                            assertEquals("RGBA tick " + targetTick + " pixel " + pixel / 4
                                    + " channel " + channel, oracle, real);
                            if (oracle != substituted) changed = true;
                        }
                        if ((expected[pixel] & 255) < 250
                                || (expected[pixel + 1] & 255) < 250
                                || (expected[pixel + 2] & 255) < 250) painted++;
                        if (changed) negative++;
                    }
                    assertTrue("source turret paints visible pixels", painted > 20);
                    assertTrue("wrong nested frame changes painted pixels", negative > 0);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                }
            } finally {
                if (buffer != null) buffer.dispose();
                if (callerShader != null) callerShader.dispose();
                batch.dispose();
            }
        });
    }

    /** One source leaf, independent of the production action-building recursion. */
    private static void drawSourceLeaf(SpriteBatch batch,
                                       OriginalFlashAnimationAssets assets, int shape) {
        TextureRegion region = assets.getShape(shape);
        assertNotNull(region);
        float x = BUILDING_X + (PARENT_TX + CHILD_TX);
        float y = BUILDING_Y + (PARENT_TY + CHILD_TY);
        batch.setShader(null);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(region, x + assets.getShapeOriginX(shape),
                HEIGHT - y - assets.getShapeOriginY(shape) - region.getRegionHeight(),
                region.getRegionWidth(), region.getRegionHeight());
    }

    private static FlashActionBuildingAnimation defenseBuilding(WorldDominationGame app)
            throws Exception {
        @SuppressWarnings("unchecked")
        List<FlashActionBuildingAnimation> buildings =
                (List<FlashActionBuildingAnimation>) field(WorldDominationGame.class,
                        "actionBuildingAnimations").get(app);
        for (FlashActionBuildingAnimation building : buildings)
            if (building.playerId == TARGET_PLAYER && building.slot == ACTION_SLOT)
                return building;
        throw new AssertionError("Missing physical ab1_2 source building");
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
