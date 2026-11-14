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
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

/** Original symbol-5553 leaf oracle at the first legal standalone anti-air callback. */
@RunWith(AndroidJUnit4.class)
public final class SourceAirfieldDefenseRenderingInstrumentedTest {
    private static final int WIDTH = 800, HEIGHT = 600;
    private static final Rectangle BOUNDS = new Rectangle(0f, 0f, WIDTH, HEIGHT);
    // SWF actionBuildings5658 frame10 antiAir_fire -> religion5625 frame4 chr.
    // ab1_2=(130.75,91.75), parent=(54.7,6.55), child=(-77.5,-61.5).
    private static final float X = 130.75f + 54.7f - 77.5f;
    private static final float Y = 91.75f + 6.55f - 61.5f;
    private static final int TURRET_SYMBOL = 5553;
    private static final int SOURCE_FRAME = 32;
    private static final int SOURCE_SHAPE = 5544;
    private static final int WRONG_SHAPE = 5542; // Authored frame 31.

    @Test public void interceptedPlaneOnePaintsFirstPhysicalTurretFrame() throws Exception {
        verify(false, 196);
    }

    @Test public void interceptedBomberPaintsFirstPhysicalTurretFrame() throws Exception {
        verify(true, 195);
    }

    private static void verify(boolean bomber, int targetTick) throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalAudio audio = (OriginalAudio) field(WorldDominationGame.class, "audio").get(app);
            boolean restoreMuted = !audio.isSoundEnabled();
            if (restoreMuted) audio.toggleSound();
            try {
                GameState game = legalGame(bomber);
                field(WorldDominationGame.class, "game").set(app, game);
                ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                        "cityRenderer").get(app)).reset(game);
                ((SourceBattleRecordClock) field(WorldDominationGame.class,
                        "battleRecordClock").get(app)).reset();
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals(bomber ? 25 : 22, game.getTurn());

                OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(
                        WorldDominationGame.class, "flashAnimationAssets").get(app);
                FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(
                        WorldDominationGame.class, "battleUnitRenderer").get(app);
                assertTrue(assets.isLoaded());
                assertEquals(5658, assets.getTimeline("actionBuildings").symbolId);
                assertEquals(TURRET_SYMBOL, assets.getTimelineBySymbolId(TURRET_SYMBOL).symbolId);
                assertEquals(SOURCE_SHAPE, assets.getTimelineBySymbolId(TURRET_SYMBOL)
                        .getFrame(SOURCE_FRAME - 1).layers[0].shapeId);
                assertEquals(WRONG_SHAPE, assets.getTimelineBySymbolId(TURRET_SYMBOL)
                        .getFrame(SOURCE_FRAME - 2).layers[0].shapeId);

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
                    for (int tick = 1; tick <= targetTick; tick++) advance(app);
                    FlashActionBuildingAnimation building = defenseBuilding(app);
                    assertEquals(1, building.playerId);
                    assertEquals(2, building.slot);
                    assertEquals("antiAir_fire", building.frameName);
                    assertEquals("chr", building.religionCode);
                    assertEquals(1f, building.getAlpha(), 0f);
                    assertEquals("source nested host frame", SOURCE_FRAME - 1,
                            building.getNestedFrameIndex(TURRET_SYMBOL, 50));

                    final ShaderProgram shader = callerShader;
                    byte[] actual = render(buffer, batch, () -> {
                        batch.setShader(shader);
                        batch.setColor(.31f, .47f, .69f, .61f);
                        float color = batch.getPackedColor();
                        Matrix4 transform = new Matrix4(batch.getTransformMatrix());
                        renderer.drawActionBuilding(batch, BOUNDS, building);
                        assertSame("caller shader retained", shader, batch.getShader());
                        assertEquals("caller color restored", color,
                                batch.getPackedColor(), 0f);
                        assertArrayEquals("caller transform retained", transform.val,
                                batch.getTransformMatrix().val, 0f);
                    });
                    byte[] expected = render(buffer, batch,
                            () -> drawOriginalLeaf(batch, assets, SOURCE_SHAPE));
                    byte[] wrong = render(buffer, batch,
                            () -> drawOriginalLeaf(batch, assets, WRONG_SHAPE));
                    String name = bomber ? "plane002-t195" : "plane001-t196";
                    evidence("source-airfield-defense-" + name + "-actual.png", actual);
                    evidence("source-airfield-defense-" + name + "-expected.png", expected);
                    evidence("source-airfield-defense-" + name + "-wrong-frame.png", wrong);
                    int painted = 0, negative = 0;
                    for (int pixel = 0; pixel < expected.length; pixel += 4) {
                        boolean changed = false;
                        for (int channel = 0; channel < 4; channel++) {
                            int real = actual[pixel + channel] & 255;
                            int oracle = expected[pixel + channel] & 255;
                            int substitute = wrong[pixel + channel] & 255;
                            assertEquals(name + " RGBA pixel " + pixel / 4
                                    + " channel " + channel, oracle, real);
                            if (oracle != substitute) changed = true;
                        }
                        if ((expected[pixel] & 255) < 250
                                || (expected[pixel + 1] & 255) < 250
                                || (expected[pixel + 2] & 255) < 250) painted++;
                        if (changed) negative++;
                    }
                    assertTrue("source leaf must paint", painted > 20);
                    assertTrue("authored prior frame must differ", negative > 0);
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                } finally {
                    if (buffer != null) buffer.dispose();
                    if (callerShader != null) callerShader.dispose();
                    batch.dispose();
                }
            } finally {
                if (restoreMuted) audio.toggleSound();
            }
        });
    }

    private static GameState legalGame(boolean bomber) throws Exception {
        if (!bomber) {
            Method method = SourceInterceptedAircraftInstrumentedTest.class
                    .getDeclaredMethod("ready");
            method.setAccessible(true);
            return (GameState) method.invoke(null);
        }
        JsonValue source;
        try (InputStream input = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("source-plane002-intercept.json")) {
            source = new JsonReader().parse(input);
        }
        Method method = SourceBomberPlaybackInstrumentedTest.class
                .getDeclaredMethod("legalGame", JsonValue.class);
        method.setAccessible(true);
        return (GameState) method.invoke(null, source);
    }

    /** Exact exported leaf placement, without the production action-building recursion. */
    private static void drawOriginalLeaf(SpriteBatch batch,
                                         OriginalFlashAnimationAssets assets, int shape) {
        TextureRegion region = assets.getShape(shape);
        assertNotNull(region);
        batch.setShader(null);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(region, X + assets.getShapeOriginX(shape),
                HEIGHT - Y - assets.getShapeOriginY(shape) - region.getRegionHeight(),
                region.getRegionWidth(), region.getRegionHeight());
    }

    private static FlashActionBuildingAnimation defenseBuilding(WorldDominationGame app)
            throws Exception {
        @SuppressWarnings("unchecked") List<FlashActionBuildingAnimation> buildings =
                (List<FlashActionBuildingAnimation>) field(WorldDominationGame.class,
                        "actionBuildingAnimations").get(app);
        for (FlashActionBuildingAnimation building : buildings)
            if (building.playerId == 1 && building.slot == 2) return building;
        throw new AssertionError("Missing physical ab1_2");
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
