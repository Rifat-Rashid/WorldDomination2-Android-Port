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
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.SourceActionMenuState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Original bio SVG against the isolated source city's independently recorded cloud poses. */
@RunWith(AndroidJUnit4.class)
public final class SourceBiologicalRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int CITY = 7;
    private static final int OWNER = 1;
    private static final int CITY_INDEX = 2;
    private static final int CLOUD_SHAPE = OriginalFlashAnimationAssets.BIO_CLOUD_SHAPE_ID;
    private static final Rectangle SOURCE_BOUNDS = new Rectangle(0, 0, WIDTH, HEIGHT);
    // Original nested bioCloud 2429 -> 2428 -> 2427 -> shape 2426, independently
    // recovered from scene.json.gz, not from CityBioloadCloudRenderer.resolveArtwork.
    private static final float NESTED_SCALE = 2.5600128f;
    private static final float NESTED_OFFSET = -12.450064f;

    @Test public void sourceCloudsInheritCityTransformAndPaintBeforeDust() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app ->
                SourceBiologicalPlaybackInstrumentedTest.withSourceSound(app,
                        SourceBiologicalRenderingInstrumentedTest::verify));
    }

    private static void verify(WorldDominationGame app) throws Exception {
        GameState game = SourceBiologicalPlaybackInstrumentedTest.gameReadyForSubmarine();
        field("game").set(app, game);
        BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field("cityRenderer").get(app);
        cities.reset(game);
        ((SourceBattleRecordClock) field("battleRecordClock").get(app)).reset();
        invoke(app, "confirmTurnAndPreparePlayback");

        OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                field("flashAnimationAssets").get(app);
        OriginalCityAssets cityAssets = (OriginalCityAssets) field("cityAssets").get(app);
        assertEquals("source city root scale", 0.8f, cityAssets.getArtworkScale(), 0.00001f);
        TextureRegion cloudLeaf = assets.getShape(CLOUD_SHAPE);
        assertNotNull("original SVG cloud leaf", cloudLeaf);
        FlashAnimationTimeline dust = assets.getTimeline("explosion_dust");
        assertNotNull("original dust timeline", dust);

        SpriteBatch batch = (SpriteBatch) field("batch").get(app);
        Rectangle bounds = (Rectangle) field("mapBounds").get(app);
        Rectangle savedBounds = new Rectangle(bounds);
        Matrix4 savedProjection = new Matrix4(batch.getProjectionMatrix());
        Matrix4 savedTransform = new Matrix4(batch.getTransformMatrix());
        ShaderProgram savedShader = batch.getShader();
        float savedColor = batch.getPackedColor();
        SourceActionMenuState menu = (SourceActionMenuState) field("sourceActionMenu").get(app);
        String selectedEquipment = menu.getSelectedEquipment();
        FrameBuffer buffer = null;
        try {
            assertFalse("snapshot starts outside batch", batch.isDrawing());
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
            bounds.set(SOURCE_BOUNDS);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
            batch.setTransformMatrix(new Matrix4().idt());
            for (int tick = 1; tick <= 641; tick++) {
                invoke(app, "fixedUpdate", float.class, 1f / 24f);
                if (tick != 576 && tick != 577 && tick != 578
                        && tick != 584 && tick != 585 && tick != 641) continue;
                verifyTick(buffer, batch, bounds, game, cities, selectedEquipment,
                        assets, cityAssets, cloudLeaf, dust, tick);
            }
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            if (buffer != null) buffer.dispose();
            bounds.set(savedBounds);
            batch.setProjectionMatrix(savedProjection);
            batch.setTransformMatrix(savedTransform);
            batch.setShader(savedShader);
            batch.setPackedColor(savedColor);
        }
    }

    private static void verifyTick(FrameBuffer buffer, SpriteBatch batch, Rectangle bounds,
                                   GameState game, BattlefieldCityRenderer cities,
                                   String selectedEquipment, OriginalFlashAnimationAssets assets,
                                   OriginalCityAssets cityAssets, TextureRegion cloudLeaf,
                                   FlashAnimationTimeline dust, int tick) throws Exception {
        Cloud[] clouds = tick == 577 || tick == 578 || tick == 584
                ? sourceClouds(tick) : new Cloud[0];
        if (tick == 576) {
            assertFalse(cities.isSourceCloudAttached(CITY));
            assertEquals(0, cities.getSourceCloudCount(CITY));
        } else if (tick == 577 || tick == 578 || tick == 584) {
            assertTrue("source cloud still attached at " + tick,
                    cities.isSourceCloudAttached(CITY));
            assertEquals(17, cities.getSourceCloudCount(CITY));
            assertEquals(30, cities.getSourceCloudDepth(CITY));
            assertEquals(31, cities.getCityDustDepth(CITY));
            if (tick == 584) assertTrue("source city has faded below zero",
                    cities.getSourceCityAlpha(CITY) <= 0);
            else assertEquals(tick == 577 ? 85 : 70,
                    cities.getSourceCityAlpha(CITY));
            assertEquals(tick - 577, cities.getCityDustFrame(CITY));
        } else if (tick == 585) {
            assertFalse("source image seek detached cloud", cities.isSourceCloudAttached(CITY));
            assertEquals("detached cohort stays in bioCloud_arr", 17,
                    cities.getSourceCloudCount(CITY));
            assertEquals(-1, cities.getCityDustFrame(CITY));
            assertEquals(1, cities.getSourceCityFrame(CITY));
        } else {
            assertFalse("canonical phase has no isolated cloud", cities.isSourceCloudAttached(CITY));
            assertEquals("isolated cloud discarded at handoff", 0,
                    cities.getSourceCloudCount(CITY));
            assertFalse("canonical source city flag stays clear",
                    game.getPlayerState(OWNER).getCity(CITY_INDEX).bioloaded);
        }

        byte[] actual = render(buffer, batch, () -> cities.drawCityBody(batch,
                bounds, game, selectedEquipment, CITY));
        byte[] base = render(buffer, batch, () -> drawWithoutEffects(cities, batch,
                bounds, game, selectedEquipment));
        byte[] expected = render(buffer, batch, () -> {
            drawWithoutEffects(cities, batch, bounds, game, selectedEquipment);
            if (tick == 577 || tick == 578) {
                drawSourceClouds(batch, bounds, assets, cityAssets, cloudLeaf,
                        clouds, cities.getSourceCityAlpha(CITY), 1f, true);
                drawSourceDust(batch, bounds, assets, cityAssets, dust,
                        cities.getCityDustFrame(CITY), cities.getSourceCityAlpha(CITY));
            }
        });
        evidence("source-bio-sub-t" + tick + "-actual.png", actual);
        evidence("source-bio-sub-t" + tick + "-expected.png", expected);
        assertEquals("source cloud/city RGBA at tick " + tick, 0,
                differingPixels(actual, expected));
        if (tick == 577) {
            assertTrue("original cloud paints at impact", differingPixels(actual, base) > 0);
            byte[] wrongScale = render(buffer, batch, () -> {
                drawWithoutEffects(cities, batch, bounds, game, selectedEquipment);
                drawSourceClouds(batch, bounds, assets, cityAssets, cloudLeaf,
                        clouds, 85, 1.25f, true);
            });
            byte[] noParentAlpha = render(buffer, batch, () -> {
                drawWithoutEffects(cities, batch, bounds, game, selectedEquipment);
                drawSourceClouds(batch, bounds, assets, cityAssets, cloudLeaf,
                        clouds, 85, 1f, false);
            });
            evidence("source-bio-sub-t577-no-cloud.png", base);
            assertTrue("city root scale changes painted cloud pixels",
                    differingPixels(expected, wrongScale) > 0);
            assertTrue("parent alpha changes painted cloud pixels",
                    differingPixels(expected, noParentAlpha) > 0);
        }
        if (tick == 578) {
            byte[] wrongOrder = render(buffer, batch, () -> {
                drawWithoutEffects(cities, batch, bounds, game, selectedEquipment);
                drawSourceDust(batch, bounds, assets, cityAssets, dust,
                        cities.getCityDustFrame(CITY), 70);
                drawSourceClouds(batch, bounds, assets, cityAssets, cloudLeaf,
                        clouds, 70, 0.8f, true);
            });
            evidence("source-bio-sub-t578-dust-first.png", wrongOrder);
            assertTrue("source cloud depth30 paints below dust depth31",
                    differingPixels(expected, wrongOrder) > 0);
        }
        if (tick == 585 || tick == 641)
            assertEquals("detached/canonical city has no painted cloud", 0,
                    differingPixels(actual, base));
    }

    @SuppressWarnings("unchecked")
    private static void drawWithoutEffects(BattlefieldCityRenderer cities, SpriteBatch batch,
                                           Rectangle bounds, GameState game,
                                           String selectedEquipment) throws Exception {
        Field cloudField = BattlefieldCityRenderer.class.getDeclaredField("bioloadCloudRenderer");
        cloudField.setAccessible(true);
        Object renderer = cloudField.get(cities);
        Field activeField = renderer.getClass().getDeclaredField("activeState");
        activeField.setAccessible(true);
        Object state = activeField.get(renderer);
        Field attachedField = state.getClass().getDeclaredField("sourceAttached");
        attachedField.setAccessible(true);
        boolean[] attached = (boolean[]) attachedField.get(state);
        boolean wasAttached = attached[CITY];
        Field dustField = BattlefieldCityRenderer.class.getDeclaredField("sourceDust");
        dustField.setAccessible(true);
        List<List<Object>> allDust = (List<List<Object>>) dustField.get(cities);
        List<Object> dust = allDust.get(CITY);
        List<Object> savedDust = new ArrayList<>(dust);
        attached[CITY] = false;
        dust.clear();
        try {
            cities.drawCityBody(batch, bounds, game, selectedEquipment, CITY);
        } finally {
            dust.addAll(savedDust);
            attached[CITY] = wasAttached;
        }
    }

    private static void drawSourceClouds(SpriteBatch batch, Rectangle bounds,
                                         OriginalFlashAnimationAssets assets,
                                         OriginalCityAssets cityAssets, TextureRegion leaf,
                                         Cloud[] clouds, int parentAlpha,
                                         float forcedMapScale, boolean inheritParentAlpha) {
        if (parentAlpha <= 0) return;
        float cityScale = cityAssets.getArtworkScale() * forcedMapScale;
        float stageScale = BattlefieldCityLayout.sourceScale(bounds);
        float cityX = BattlefieldCityLayout.sourceCityX(OWNER, CITY_INDEX);
        float cityY = BattlefieldCityLayout.sourceCityY(OWNER, CITY_INDEX);
        float originX = assets.getShapeOriginX(CLOUD_SHAPE);
        float originY = assets.getShapeOriginY(CLOUD_SHAPE);
        float width = leaf.getRegionWidth() * cityScale * stageScale;
        float height = leaf.getRegionHeight() * cityScale * stageScale;
        for (Cloud cloud : clouds) {
            assertEquals("source child depth", cloud.index, cloud.depth);
            assertTrue("cloud child root attachment", cloud.attached);
            float scale = cloud.scale / 100f;
            float localX = cloud.x + scale * (NESTED_OFFSET + NESTED_SCALE * originX);
            float localY = cloud.y + scale * (NESTED_OFFSET + NESTED_SCALE * originY);
            float x = BattlefieldCityLayout.sourceX(bounds, cityX + localX * cityScale, OWNER);
            float top = BattlefieldCityLayout.sourceY(bounds, cityY + localY * cityScale);
            float alpha = cloud.alpha / 100f
                    * (inheritParentAlpha ? parentAlpha / 100f : 1f);
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(leaf, x, top - height, 0f, height, width, height,
                    NESTED_SCALE * scale, NESTED_SCALE * scale, 0f);
        }
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private static void drawSourceDust(SpriteBatch batch, Rectangle bounds,
                                       OriginalFlashAnimationAssets assets,
                                       OriginalCityAssets cityAssets,
                                       FlashAnimationTimeline timeline,
                                       int frameIndex, int parentAlpha) {
        if (parentAlpha <= 0 || frameIndex < 0) return;
        FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
        if (frame == null || frame.layers.length == 0) return;
        assertEquals("one original dust leaf", 1, frame.layers.length);
        int shapeId = frame.layers[0].shapeId;
        TextureRegion leaf = assets.getShape(shapeId);
        assertNotNull("original dust SVG leaf " + shapeId, leaf);
        float originX = assets.getShapeOriginX(shapeId);
        float originY = assets.getShapeOriginY(shapeId);
        float cityScale = cityAssets.getArtworkScale();
        float sourceX = BattlefieldCityLayout.sourceCityX(OWNER, CITY_INDEX)
                + (-10f + 1.3f * originX) * cityScale;
        float sourceY = BattlefieldCityLayout.sourceCityY(OWNER, CITY_INDEX)
                + (-3f + 1.3f * originY) * cityScale;
        float x = BattlefieldCityLayout.sourceX(bounds, sourceX, OWNER);
        float top = BattlefieldCityLayout.sourceY(bounds, sourceY);
        float width = leaf.getRegionWidth() * cityScale
                * BattlefieldCityLayout.sourceScale(bounds);
        float height = leaf.getRegionHeight() * cityScale
                * BattlefieldCityLayout.sourceScale(bounds);
        batch.setColor(1f, 1f, 1f, parentAlpha / 100f);
        batch.draw(leaf, x, top - height, 0f, height, width, height,
                1.3f, 1.3f, 0f);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private static Cloud[] sourceClouds(int tick) throws Exception {
        Cloud[] clouds = new Cloud[17];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-bio-sub-clouds.csv"), StandardCharsets.UTF_8))) {
            assertEquals("tick,index,depth,rootAttached,parentAttached,frame,onEnterFrame,"
                    + "x,y,alpha,xscale,yscale,alphaDecay,sizeDecay,ySpeed,horizontalMax,"
                    + "horizontalOffset,horizontalMultiplier", reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                assertEquals(18, values.length);
                if (Integer.parseInt(values[0]) != tick) continue;
                int index = Integer.parseInt(values[1]);
                assertTrue(index >= 0 && index < clouds.length);
                assertNull("unique source cloud index", clouds[index]);
                clouds[index] = new Cloud(index, Integer.parseInt(values[2]),
                        Boolean.parseBoolean(values[3]),
                        Float.parseFloat(values[7]), Float.parseFloat(values[8]),
                        Float.parseFloat(values[9]), Float.parseFloat(values[10]));
            }
        }
        for (Cloud cloud : clouds) assertNotNull("all 17 source cloud rows", cloud);
        return clouds;
    }

    private static byte[] render(FrameBuffer buffer, SpriteBatch batch, Draw draw)
            throws Exception {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
            Gdx.gl.glStencilMask(0xff);
            Gdx.gl.glClearStencil(0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);
            batch.setShader(null);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.begin();
            try { draw.draw(); }
            finally { batch.end(); }
            return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
        } finally {
            buffer.end();
        }
    }

    private static int differingPixels(byte[] a, byte[] b) {
        assertEquals(a.length, b.length);
        int changed = 0;
        for (int p = 0; p < a.length; p += 4)
            if (a[p] != b[p] || a[p + 1] != b[p + 1]
                    || a[p + 2] != b[p + 2] || a[p + 3] != b[p + 3]) changed++;
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
        } finally {
            image.dispose();
        }
    }

    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void invoke(WorldDominationGame app, String name, Class<?> type, Object value)
            throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(app, value);
    }

    private interface Draw { void draw() throws Exception; }
    private static final class Cloud {
        final int index;
        final int depth;
        final boolean attached;
        final float x;
        final float y;
        final float alpha;
        final float scale;

        Cloud(int index, int depth, boolean attached,
              float x, float y, float alpha, float scale) {
            this.index = index;
            this.depth = depth;
            this.attached = attached;
            this.x = x;
            this.y = y;
            this.alpha = alpha;
            this.scale = scale;
        }
    }
}
