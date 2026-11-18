package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Validates the original research compositing through Android's actual GLES renderer. */
@RunWith(AndroidJUnit4.class)
public final class TechnologyUiInstrumentedTest {
    @Test public void originalResearchIsClippedWhiteTintedAndTouchAligned() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    OriginalFlashAnimationAssets art = (OriginalFlashAnimationAssets) field("flashAnimationAssets").get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    while (!art.areCenterUiAssetsLoaded() && System.nanoTime() < deadline) {
                        art.update(); Thread.sleep(10);
                    }
                    assertTrue("Load the original research artwork", art.areCenterUiAssetsLoaded());
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameSetup setup = new GameSetup(0L); setup.toggleOpponent(0);
                    GameState game = new GameState(setup);
                    field("game").set(app, game); field("modalOpen").setBoolean(app, false);
                    render(app);
                    Rectangle modal = (Rectangle) field("modal").get(app);
                    float scale = modal.width / 365;
                    float outsideX = modal.x + modal.width / 2;
                    float outsideY = modal.y + modal.height + 8 * scale;
                    int outsideBefore = pixel(app, outsideX, outsideY);
                    SpriteBatch batch = (SpriteBatch) field("batch").get(app);
                    Object previousShader = batch.getShader();
                    Rectangle row = ((Rectangle[]) field("navRows").get(app))[GameState.Action.RESEARCH.ordinal()];
                    tap(app, row);
                    assertEquals(GameState.Action.RESEARCH, game.getSelectedAction());
                    render(app);
                    assertEquals("Oversized source backing cannot overwrite the top HUD", outsideBefore,
                            pixel(app, outsideX, outsideY));
                    assertSame("Restore the caller's shader", previousShader, batch.getShader());
                    assertFalse("Restore scissor state after the menu", Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                    int white = pixel(app, modal.x + 300 * scale,
                            modal.y + modal.height - 132 * scale);
                    int red = white >>> 24 & 255, green = white >>> 16 & 255, blue = white >>> 8 & 255;
                    assertTrue("Source white list bar must not remain orange: " + Integer.toHexString(white),
                            red > 180 && green > 180 && blue > 180
                                    && Math.abs(red - green) < 12 && Math.abs(green - blue) < 12);
                    capture(app, "source-technology-original-empty.png");
                    String[] choices = (String[]) field("researchChoiceByCard").get(app);
                    Rectangle[] cards = (Rectangle[]) field("technologyCards").get(app);
                    int available = -1;
                    for (int i = 0; i < choices.length; i++)
                        if (choices[i] != null && game.isResearchAvailable(choices[i])) { available = i; break; }
                    assertTrue(available >= 0);
                    String technology = choices[available];
                    int cost = game.getTechnologyCost(technology);
                    tap(app, cards[available]);
                    assertTrue(game.isResearchTechnologySelected(technology));
                    capture(app, "source-technology-original-selected.png");
                    assertMinifiedSelectedTextRetainsWhiteInk(app);
                    int funds = game.getTurnMoneyMillions();
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(GameState.Action.RESEARCH, game.getQueuedAction(0));
                    assertEquals(funds - cost, game.getTurnMoneyMillions());
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue("Original research UI timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Original research UI", failure.get());
        }
    }

    /** The source's selected row is light on navy even at a 640x320 back buffer. */
    private static void assertMinifiedSelectedTextRetainsWhiteInk(WorldDominationGame app)
            throws Exception {
        BitmapFont font = (BitmapFont) field("font").get(app);
        Texture texture = font.getRegion().getTexture();
        assertTrue("Source UI font must survive Android GL context recreation",
                texture.isManaged());
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        Color oldColor = new Color(font.getColor());
        boolean oldIntegerPositions = font.usesIntegerPositions();
        SpriteBatch batch = new SpriteBatch();
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 256, 64, false);
        ShaderProgram alphaOnly = new ShaderProgram(
                "attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0;"
                        + "uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_texCoords;"
                        + "void main(){v_color=a_color;v_color.a*=255.0/254.0;"
                        + "v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}",
                "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
                        + "varying vec4 v_color; varying vec2 v_texCoords;"
                        + "uniform sampler2D u_texture;"
                        + "void main(){float coverage=texture2D(u_texture,v_texCoords).a;"
                        + "gl_FragColor=vec4(v_color.rgb,coverage*v_color.a);}");
        try {
            assertTrue("Alpha-only source glyph oracle compiles: " + alphaOnly.getLog(),
                    alphaOnly.isCompiled());
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 256, 64));
            // Seven rendered pixels approximate the source row at the CI 640x320 size.
            font.getData().setScale(7f / 18f);
            font.setColor(Color.WHITE);
            font.setUseIntegerPositions(false);
            int ordinaryPeak = renderTextPeak(buffer, batch, font, null);
            int alphaOnlyPeak = renderTextPeak(buffer, batch, font, alphaOnly);
            assertTrue("Alpha-only glyph oracle must contain visible ink: " + alphaOnlyPeak,
                    alphaOnlyPeak >= 150);
            assertTrue("Mipped glyph RGB must retain source-white ink; normal="
                            + ordinaryPeak + ", alpha-only=" + alphaOnlyPeak,
                    ordinaryPeak >= alphaOnlyPeak - 12);
            assertOriginalCoverage(texture);
            texture.load(texture.getTextureData());
            assertEquals("Managed reload preserves the corrected glyph pixels", ordinaryPeak,
                    renderTextPeak(buffer, batch, font, null));
        } finally {
            font.getData().setScale(oldScaleX, oldScaleY);
            font.setColor(oldColor);
            font.setUseIntegerPositions(oldIntegerPositions);
            alphaOnly.dispose();
            buffer.dispose();
            batch.dispose();
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(),
                    Gdx.graphics.getBackBufferHeight());
        }
    }

    private static void assertOriginalCoverage(Texture texture) {
        TextureData data = texture.getTextureData();
        data.prepare();
        Pixmap prepared = data.consumePixmap();
        Pixmap original = new Pixmap(Gdx.files.internal("original/ui/fonts/resource-arial-48.png"));
        try {
            assertEquals(original.getWidth(), prepared.getWidth());
            assertEquals(original.getHeight(), prepared.getHeight());
            for (int y = 0; y < original.getHeight(); y++) {
                for (int x = 0; x < original.getWidth(); x++) {
                    assertEquals("Glyph alpha must remain byte-identical to the packaged source",
                            original.getPixel(x, y) & 255, prepared.getPixel(x, y) & 255);
                }
            }
        } finally {
            original.dispose();
            prepared.dispose();
        }
    }

    private static int renderTextPeak(FrameBuffer buffer, SpriteBatch batch,
                                      BitmapFont font, ShaderProgram shader) {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(0f, 36f / 255f, 60f / 255f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setShader(shader);
            batch.begin();
            font.draw(batch, "ROCKET (25 MEG)  10.000.000", 8.25f, 35.5f);
            batch.end();
            byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, 256, 64, true);
            int peak = 0;
            for (int index = 0; index < pixels.length; index += 4)
                peak = Math.max(peak, pixels[index] & 255);
            return peak;
        } finally {
            if (batch.isDrawing()) batch.end();
            buffer.end();
        }
    }
    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        Vector2 screen = ((Viewport) field("viewport").get(app))
                .project(new Vector2(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2));
        int x = Math.round(screen.x), y = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(x, y, 0, 0); app.touchUp(x, y, 0, 0);
    }
    private static int pixel(WorldDominationGame app, float x, float y) throws Exception {
        Vector2 screen = ((Viewport) field("viewport").get(app)).project(new Vector2(x, y));
        Pixmap image = ScreenUtils.getFrameBufferPixmap(Math.round(screen.x), Math.round(screen.y), 1, 1);
        try { return image.getPixel(0, 0); } finally { image.dispose(); }
    }
    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app);
    }
    private static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true); app.render();
    }
    private static void capture(WorldDominationGame app, String name) throws Exception {
        render(app);
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try { PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image); }
        finally { image.dispose(); }
    }
}
