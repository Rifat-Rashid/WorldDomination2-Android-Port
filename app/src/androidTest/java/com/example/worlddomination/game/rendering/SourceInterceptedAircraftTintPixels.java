package com.example.worlddomination.game.rendering;

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

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

/** Independent source-green pixel oracle called by the legal tick-313 Android test. */
final class SourceInterceptedAircraftTintPixels {
    private static final int WIDTH = 800, HEIGHT = 600;
    private static final Rectangle BOUNDS = new Rectangle(0, 0, WIDTH, HEIGHT);
    // Source WD_colors.green: a white tint leaf becomes (0,1,0), alpha 75%.
    // The root clip's _alpha=75 is the same channel, not another multiplier.
    private static final float[] GREEN = {0f, 0.5f, 0f, .75f, 0f, 1f, 0f, 0f};
    private static final String VERTEX = "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\nattribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\nvarying vec4 v_color;\nvarying vec2 v_uv;\n"
            + "void main(){v_color=a_color;v_color.a*=255.0/254.0;"
            + "v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}";
    private static final String FRAGMENT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec4 v_color;varying vec2 v_uv;uniform sampler2D u_texture;"
            + "uniform vec4 u_mul;uniform vec4 u_add;"
            + "void main(){gl_FragColor=clamp(texture2D(u_texture,v_uv)*u_mul+u_add,0.0,1.0)*v_color;}";

    private SourceInterceptedAircraftTintPixels() { }

    static void verify(WorldDominationGame app,
                       SourceInterceptedAircraftDisplayDepths binding) throws Exception {
        OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(app,
                "flashAnimationAssets");
        FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(app,
                "battleUnitRenderer");
        assertNotNull(assets);
        assertTrue(assets.isLoaded());
        assertNotNull(binding.tint());
        assertNotNull(binding.dot());
        assertTrue(binding.tint().isAttached());
        assertTrue(binding.dot().isAttached());
        FlashStandalonePlaneAnimation.Actor actor = binding.animation.getActor();
        assertEquals("source tick 313 is authored frame 1", 0, actor.sourceFrame);
        assertEquals("source x", 48.27275153669018, actor.sourceX, .002);
        assertEquals("source y", 254.7482852266179, actor.sourceY, .002);
        assertEquals("source scale", .82f, actor.scale, .0001f);
        assertEquals("source tint alpha already applied by palette", .75f,
                binding.animation.getTint().alphaMultiplier, .0001f);
        assertEquals(1f, binding.animation.getRoute().targetPlayerId, 0f);

        SpriteBatch batch = new SpriteBatch();
        ShaderProgram oracleShader = null;
        ShaderProgram callerShader = null;
        FrameBuffer buffer = null;
        try {
            oracleShader = new ShaderProgram(VERTEX, FRAGMENT);
            callerShader = SpriteBatch.createDefaultShader();
            assertTrue(oracleShader.getLog(), oracleShader.isCompiled());
            assertTrue(callerShader.getLog(), callerShader.isCompiled());
            final ShaderProgram expectedShader = oracleShader;
            final ShaderProgram initialShader = callerShader;
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
            for (BattlefieldDisplayState.Kind role : new BattlefieldDisplayState.Kind[] {
                    BattlefieldDisplayState.Kind.UNIT_TINT, BattlefieldDisplayState.Kind.UNIT_DOT}) {
                String label = role == BattlefieldDisplayState.Kind.UNIT_TINT ? "tint" : "dot";
                byte[] actual = render(buffer, batch, () -> {
                    batch.setShader(initialShader);
                    batch.setColor(.31f, .47f, .69f, .61f);
                    float callerColor = batch.getPackedColor();
                    renderer.drawInterceptedAircraftRole(batch, BOUNDS, binding, role);
                    assertSame("caller shader " + label, initialShader, batch.getShader());
                    assertEquals("caller color " + label, callerColor,
                            batch.getPackedColor(), 0f);
                });
                byte[] expected = render(buffer, batch, () -> drawExpected(batch,
                        expectedShader, assets, label, 1f));
                byte[] wrongAlpha = render(buffer, batch, () -> drawExpected(batch,
                        expectedShader, assets, label, .75f));
                evidence("source-intercepted-plane-t313-" + label + "-actual.png", actual);
                evidence("source-intercepted-plane-t313-" + label + "-expected.png", expected);
                evidence("source-intercepted-plane-t313-" + label + "-wrongalpha.png", wrongAlpha);
                assertPixels(label, actual, expected);
                assertTrue("double alpha must be visible in " + label,
                        differingPixels(expected, wrongAlpha) > 0);
            }
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            if (buffer != null) buffer.dispose();
            if (oracleShader != null) oracleShader.dispose();
            if (callerShader != null) callerShader.dispose();
            batch.dispose();
        }
    }

    private static void drawExpected(SpriteBatch batch, ShaderProgram shader,
                                     OriginalFlashAnimationAssets assets,
                                     String label, float extraAlpha) {
        String linkage = "tint".equals(label) ? "plane001_tint" : "redDot";
        FlashAnimationTimeline timeline = assets.getTimeline(linkage);
        assertNotNull("original " + linkage, timeline);
        float sourceX = "tint".equals(label) ? 48.27275153669018f : 0f;
        float sourceY = "tint".equals(label) ? 254.7482852266179f : 0f;
        float scale = "tint".equals(label) ? .82f : 1f;
        float[] root = {scale, 0f, 0f, scale, 0f, 0f};
        float[] color = GREEN.clone();
        color[3] *= extraAlpha;
        drawFrame(batch, shader, assets, timeline.getFrame(0), 0,
                root, color, sourceX, sourceY, 1, 0);
    }

    private static void drawFrame(SpriteBatch batch, ShaderProgram shader,
                                  OriginalFlashAnimationAssets assets,
                                  FlashAnimationTimeline.Frame frame, int nestedFrame,
                                  float[] parent, float[] parentColor,
                                  float sourceX, float sourceY, int island, int depth) {
        assertTrue("finite source nesting", depth < 8);
        assertNotNull(frame);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float[] transform = multiply(parent, layer);
            float[] color = multiplyColor(parentColor, layer.colorTransform);
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region == null) {
                FlashAnimationTimeline child = assets.getTimelineBySymbolId(layer.shapeId);
                assertNotNull("nested source symbol " + layer.shapeId, child);
                drawFrame(batch, shader, assets,
                        child.getFrame(Math.min(nestedFrame, child.getFrameCount() - 1)),
                        nestedFrame, transform, color, sourceX, sourceY, island, depth + 1);
                continue;
            }
            float scale = BattlefieldCityLayout.sourceScale(BOUNDS);
            Matrix4 matrix = new Matrix4();
            matrix.val[Matrix4.M00] = scale * transform[0];
            matrix.val[Matrix4.M01] = -scale * transform[2];
            matrix.val[Matrix4.M10] = -scale * transform[1];
            matrix.val[Matrix4.M11] = scale * transform[3];
            matrix.val[Matrix4.M03] = BattlefieldCityLayout.sourceX(BOUNDS,
                    sourceX + transform[4], island);
            matrix.val[Matrix4.M13] = BattlefieldCityLayout.sourceY(BOUNDS,
                    sourceY + transform[5]);
            batch.flush();
            batch.setTransformMatrix(matrix);
            batch.setShader(shader);
            shader.setUniformf("u_mul", color[0], color[1], color[2], color[3]);
            shader.setUniformf("u_add", color[4], color[5], color[6], color[7]);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(region, assets.getShapeOriginX(layer.shapeId),
                    -assets.getShapeOriginY(layer.shapeId) - region.getRegionHeight(),
                    region.getRegionWidth(), region.getRegionHeight());
        }
    }

    private static float[] multiply(float[] p, FlashAnimationTimeline.Layer c) {
        return new float[] {p[0] * c.a + p[2] * c.b, p[1] * c.a + p[3] * c.b,
                p[0] * c.c + p[2] * c.d, p[1] * c.c + p[3] * c.d,
                p[0] * c.tx + p[2] * c.ty + p[4],
                p[1] * c.tx + p[3] * c.ty + p[5]};
    }

    private static float[] multiplyColor(float[] p, FlashColorTransform c) {
        return new float[] {p[0] * c.redMultiplier, p[1] * c.greenMultiplier,
                p[2] * c.blueMultiplier, p[3] * c.alphaMultiplier,
                p[0] * c.redAdd + p[4], p[1] * c.greenAdd + p[5],
                p[2] * c.blueAdd + p[6], p[3] * c.alphaAdd + p[7]};
    }

    private static byte[] render(FrameBuffer buffer, SpriteBatch batch, Draw draw)
            throws Exception {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setTransformMatrix(new Matrix4().idt());
            batch.setShader(null);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.begin();
            try { draw.draw(); } finally { batch.end(); }
            return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
        } finally { buffer.end(); }
    }

    private static void assertPixels(String label, byte[] actual, byte[] expected) {
        assertEquals(expected.length, actual.length);
        int painted = 0;
        for (int i = 0; i < actual.length; i += 4) {
            for (int channel = 0; channel < 4; channel++) {
                int delta = Math.abs((actual[i + channel] & 255)
                        - (expected[i + channel] & 255));
                if (delta > 3) fail(label + " pixel (" + (i / 4) % WIDTH + ","
                        + (i / 4) / WIDTH + ") channel " + channel + " delta " + delta);
            }
            if ((expected[i] & 255) < 250 || (expected[i + 1] & 255) < 250
                    || (expected[i + 2] & 255) < 250) painted++;
        }
        assertTrue("source " + label + " must paint", painted > 0);
    }

    private static int differingPixels(byte[] a, byte[] b) {
        assertEquals(a.length, b.length);
        int count = 0;
        for (int i = 0; i < a.length; i += 4)
            if (Math.abs((a[i] & 255) - (b[i] & 255)) > 3
                    || Math.abs((a[i + 1] & 255) - (b[i + 1] & 255)) > 3
                    || Math.abs((a[i + 2] & 255) - (b[i + 2] & 255)) > 3) count++;
        return count;
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

    private static Object field(WorldDominationGame app, String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(app);
    }

    private interface Draw { void draw() throws Exception; }
}
