package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
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
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** GLES pixels versus explicit exported-leaf composition, not a screenshot of Flash execution. */
@RunWith(AndroidJUnit4.class)
public final class ChinookDeploymentArtInstrumentedTest {
    private enum Check { LEAVES, STATE, OPACITY }

    @Test public void originalDeploymentLeavesMatchAtTwoLandscapeWidths() throws Exception {
        run(Check.LEAVES);
    }

    @Test public void deploymentRestoresCallerShaderColorAndTransform() throws Exception {
        run(Check.STATE);
    }

    @Test public void shadowColorTransformPreservesAuthoredOpacity() throws Exception {
        run(Check.OPACITY);
    }

    private static void run(Check check) throws Exception {
        boolean stateTest = check == Check.STATE;
        boolean alphaTest = check == Check.OPACITY;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                SpriteBatch batch = null;
                FrameBuffer buffer = null;
                FlashBattleUnitRenderer renderer = null;
                ShaderProgram callerShader = null;
                boolean bufferBound = false;
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    Method initialize = WorldDominationGame.class.getDeclaredMethod("initializeGameplayAssets");
                    initialize.setAccessible(true);
                    initialize.invoke(app);
                    Field assetField = WorldDominationGame.class.getDeclaredField("flashAnimationAssets");
                    assetField.setAccessible(true);
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) assetField.get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!assets.update() && System.nanoTime() < deadline) Thread.sleep(5);
                    assertTrue("Complete battle atlas is ready", assets.isLoaded());
                    assertNotNull("Tank shadow is cached", assets.getTimelineBySymbolId(677));
                    assertNotNull("Animated rotor shadow is cached", assets.getTimelineBySymbolId(2373));
                    assertNotNull(assets.getShape(676));
                    Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(
                            Gdx.files.internal("original/runtime/battle_anchors.json").readString("UTF-8"));
                    FlashBattleRoutes.ChinookTankAttack route = FlashBattleRoutes.createSoldierThreeAttack(
                            0, 2, 0, 2, "green", anchors);
                    batch = new SpriteBatch();
                    renderer = new FlashBattleUnitRenderer(assets);
                    for (int width : stateTest || alphaTest ? new int[]{800} : new int[]{800, 1194}) {
                        int height = 600;
                        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
                        Rectangle bounds = new Rectangle(0, 0, width, height);
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
                        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, 0);
                        int elapsed = 0;
                        int[] ticks = stateTest ? new int[]{32}
                                : alphaTest ? new int[]{1} : new int[]{1, 31, 32, 51, 52};
                        for (int tick : ticks) {
                            while (elapsed < tick) {
                                animation.tick();
                                elapsed++;
                            }
                            if (alphaTest) {
                                for (int actor = 0; actor < 3; actor++) animation.getActor(actor).visible = false;
                                batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                                        GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
                            }
                            clear(buffer, alphaTest);
                            bufferBound = true;
                            batch.begin();
                            if (stateTest) {
                                callerShader = SpriteBatch.createDefaultShader();
                                assertTrue(callerShader.getLog(), callerShader.isCompiled());
                                batch.setShader(callerShader);
                                batch.setColor(.25f, .5f, .75f, .6f);
                                float packed = batch.getPackedColor();
                                Matrix4 transform = new Matrix4(batch.getTransformMatrix());
                                renderer.drawChinookTank(batch, bounds, Collections.singletonList(animation));
                                assertSame("Caller shader survives shadow color uniforms",
                                        callerShader, batch.getShader());
                                assertEquals(packed, batch.getPackedColor(), 0f);
                                assertArrayEquals(transform.val, batch.getTransformMatrix().val, 0f);
                            } else {
                                renderer.drawChinookTank(batch, bounds, Collections.singletonList(animation));
                            }
                            batch.end();
                            byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
                            buffer.end();
                            bufferBound = false;
                            if (stateTest) continue;
                            if (alphaTest) {
                                save(actual, width, height, "source-chinook-shadow-opacity.png");
                                int maxAlpha = 0;
                                int visible = 0;
                                for (int pixel = 0; pixel < actual.length; pixel += 4) {
                                    int alpha = actual[pixel + 3] & 255;
                                    maxAlpha = Math.max(maxAlpha, alpha);
                                    if (alpha > 0) {
                                        visible++;
                                        assertEquals("Black shadow red", 0, actual[pixel] & 255);
                                        assertEquals("Black shadow green", 0, actual[pixel + 1] & 255);
                                        assertEquals("Black shadow blue", 0, actual[pixel + 2] & 255);
                                    }
                                }
                                // Source alpha is 51/128; opaque source texels must
                                // produce round((51/128)*255)=102, not 101.
                                assertEquals("Authored 0.3984375 opacity survives packed vertex alpha",
                                        102, maxAlpha);
                                assertTrue("Actual rotor silhouette is visible", visible > 100);
                                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                                continue;
                            }
                            clear(buffer, false);
                            bufferBound = true;
                            batch.begin();
                            reference(batch, bounds, assets, animation);
                            batch.end();
                            byte[] expected = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
                            buffer.end();
                            bufferBound = false;
                            save(actual, width, height, "source-chinook-" + width + "-tick" + tick + ".png");
                            save(expected, width, height, "source-chinook-" + width + "-tick" + tick + "-oracle.png");
                            int nonWhite = 0;
                            for (int pixel = 0; pixel < actual.length; pixel += 4) {
                                if ((actual[pixel] & 255) < 250) nonWhite++;
                                for (int channel = 0; channel < 3; channel++) {
                                    int difference = Math.abs((actual[pixel + channel] & 255)
                                            - (expected[pixel + channel] & 255));
                                    if (difference > 3) fail("Exported-leaf RGB mismatch width=" + width
                                            + " tick=" + tick + " at " + (pixel / 4 % width)
                                            + "," + (pixel / 4 / width) + " channel=" + channel
                                            + " actual=" + (actual[pixel + channel] & 255)
                                            + " expected=" + (expected[pixel + channel] & 255));
                                }
                            }
                            assertTrue("Body and shadow art produce actual pixels", nonWhite > 500);
                            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                        }
                        buffer.dispose();
                        buffer = null;
                    }
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    if (batch != null && batch.isDrawing()) batch.end();
                    if (buffer != null && bufferBound) buffer.end();
                    if (buffer != null) buffer.dispose();
                    if (renderer != null) renderer.dispose();
                    if (batch != null) batch.dispose();
                    if (callerShader != null) callerShader.dispose();
                    done.countDown();
                }
            }));
            assertTrue("Deployment pixel checks complete", done.await(120, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Native deployment art", failure.get());
        }
    }

    private static void clear(FrameBuffer buffer, boolean transparent) {
        buffer.begin();
        if (transparent) Gdx.gl.glClearColor(0, 0, 0, 0);
        else Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    /** Independent oracle: explicit source leaves via SpriteBatch's matrix transform API. */
    private static void reference(SpriteBatch batch, Rectangle bounds,
                                   OriginalFlashAnimationAssets assets,
                                   FlashChinookTankAnimation animation) {
        FlashChinookTankAnimation.Actor base = animation.getActor(1);
        if (animation.isTankShadowVisible()) {
            FlashAnimationTimeline.Layer placement = assets.getTimeline("tank_noshade").getFrame(0).layers[0];
            FlashAnimationTimeline.Layer leaf = assets.getTimelineBySymbolId(677).getFrame(0).layers[0];
            leaf(batch, bounds, assets, animation, base, leaf, placement.tx,
                    placement.ty + animation.getTankShadowOffsetY(), false, .69921875f);
        }
        body(batch, bounds, assets, animation, base);
        body(batch, bounds, assets, animation, animation.getActor(2));
        body(batch, bounds, assets, animation, animation.getActor(0));
        FlashChinookTankAnimation.Actor shadow = animation.getChinookShadow();
        if (shadow.visible) {
            FlashAnimationTimeline.Layer leaf = assets.getTimelineBySymbolId(2373)
                    .getFrame(shadow.sourceFrame).layers[0];
            leaf(batch, bounds, assets, animation, shadow, leaf, -.15f, .25f, true, .3984375f);
        }
        batch.setTransformMatrix(new Matrix4());
        batch.setColor(1, 1, 1, 1);
    }

    private static void body(SpriteBatch batch, Rectangle bounds,
                              OriginalFlashAnimationAssets assets,
                              FlashChinookTankAnimation animation,
                              FlashChinookTankAnimation.Actor actor) {
        if (!actor.visible) return;
        for (FlashAnimationTimeline.Layer layer : assets.getTimeline(actor.linkage)
                .getFrame(actor.sourceFrame).layers) {
            if (assets.getShape(layer.shapeId) != null) {
                leaf(batch, bounds, assets, animation, actor, layer, 0, 0, false, 1f);
            }
        }
    }

    private static void leaf(SpriteBatch batch, Rectangle bounds,
                              OriginalFlashAnimationAssets assets,
                              FlashChinookTankAnimation animation,
                              FlashChinookTankAnimation.Actor actor,
                              FlashAnimationTimeline.Layer layer,
                              float parentX, float parentY, boolean black, float alpha) {
        TextureRegion region = assets.getShape(layer.shapeId);
        assertNotNull(region);
        float scale = BattlefieldCityLayout.sourceScale(bounds);
        Matrix4 matrix = new Matrix4();
        matrix.val[Matrix4.M00] = layer.a * scale;
        matrix.val[Matrix4.M01] = -layer.c * scale;
        matrix.val[Matrix4.M10] = -layer.b * scale;
        matrix.val[Matrix4.M11] = layer.d * scale;
        // Independent expected progress for the source 0->2 route used above.
        // Start=(432.25,221.75), drop=(563,68), source arrival range=25.
        FlashChinookTankAnimation.Actor carrier = animation.getActor(0);
        float remaining = Math.max(Math.abs(carrier.sourceX - 563f),
                Math.abs(carrier.sourceY - 68f));
        float progress = animation.isTankShadowVisible() || animation.isTankDeployed()
                ? 1f : Math.max(0f, Math.min(1f, (153.75f - remaining) / 128.75f));
        matrix.val[Matrix4.M03] = BattlefieldCityLayout.sourceXBetween(bounds,
                actor.sourceX + parentX + layer.tx, 0, 2, progress);
        matrix.val[Matrix4.M13] = BattlefieldCityLayout.sourceY(bounds,
                actor.sourceY + parentY + layer.ty);
        batch.setTransformMatrix(matrix);
        batch.setColor(black ? 0 : 1, black ? 0 : 1, black ? 0 : 1, alpha);
        batch.draw(region, assets.getShapeOriginX(layer.shapeId),
                -assets.getShapeOriginY(layer.shapeId) - region.getRegionHeight(),
                region.getRegionWidth(), region.getRegionHeight());
    }

    private static void save(byte[] pixels, int width, int height, String name) {
        File directory = new File(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir(null), "beta-ui-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(pixels).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, true);
        } finally { image.dispose(); }
    }
}
