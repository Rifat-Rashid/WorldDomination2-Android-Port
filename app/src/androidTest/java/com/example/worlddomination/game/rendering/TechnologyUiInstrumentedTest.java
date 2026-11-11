package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
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
