package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ConfirmTurnAnimation;
import com.example.worlddomination.game.ui.TurnActionDisplay;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Deterministic GL-thread integration of real touch routing, turn state, and cached artwork. */
@RunWith(AndroidJUnit4.class)
public final class TurnControlsInstrumentedTest {
    @Test public void confirmClosesBeforeResolutionThenShowsAllNationsSourceActions() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    GameState state = new GameState(1, 17L);
                    field("game").set(app, state);
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    ConfirmTurnAnimation confirm = (ConfirmTurnAnimation) field("confirmTurnAnimation").get(app);
                    confirm.reset();
                    invoke(app, "synchronizeTurnControls");
                    assertEquals(1, confirm.getFrame());
                    capture(app, "confirm-closed.png");
                    state.selectAction(GameState.Action.RESOURCES);
                    assertTrue(state.acceptSelectedAction());
                    invoke(app, "synchronizeTurnControls");
                    assertFalse(confirm.isOpening());
                    state.selectAction(GameState.Action.MASS_MEDIA);
                    assertTrue(state.acceptSelectedAction());
                    invoke(app, "synchronizeTurnControls");
                    advance(app, 10);
                    assertEquals(11, confirm.getFrame());
                    capture(app, "confirm-open.png");

                    Rectangle button = (Rectangle) field("confirmButton").get(app);
                    Viewport viewport = (Viewport) field("viewport").get(app);
                    Vector2 screen = viewport.project(new Vector2(button.x + button.width / 2,
                            button.y + button.height / 2));
                    app.touchDown(Math.round(screen.x), Gdx.graphics.getHeight() - Math.round(screen.y), 0, 0);
                    app.touchUp(Math.round(screen.x), Gdx.graphics.getHeight() - Math.round(screen.y), 0, 0);
                    assertTrue("Real confirm touch must start closure", confirm.isPending());
                    assertEquals(12, confirm.getFrame());
                    assertEquals(0, state.getTurn());
                    advance(app, 6);
                    capture(app, "confirm-closing.png");
                    assertEquals(6, confirm.getFrame());
                    assertEquals(0, state.getTurn());
                    advance(app, 14);
                    assertEquals(1, state.getTurn());
                    assertEquals(1, confirm.getFrame());
                    assertEquals(0, state.getQueuedActionCount());
                    advance(app, 40);
                    TurnActionDisplay display = (TurnActionDisplay) field("turnActionDisplay").get(app);
                    assertEquals(GameState.Action.RESOURCES, display.getAction());
                    assertTrue(display.includesPlayer(0));
                    capture(app, "source-action-announcement.png");
                    boolean includesAi = false;
                    for (int action = 0; action < 9; action++) {
                        for (int player = 1; player < 5; player++) includesAi |= display.includesPlayer(player);
                        advance(app, 39);
                    }
                    assertTrue("AI actions must participate in the visible source reveal", includesAi);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            }));
            assertTrue("Android GL integration timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Confirm/action display integration", failure.get());
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

    private static void advance(WorldDominationGame app, int ticks) throws Exception {
        Method update = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        update.setAccessible(true);
        for (int tick = 0; tick < ticks; tick++) update.invoke(app, 1f / 24f);
    }

    private static void capture(WorldDominationGame app, String name) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true);
        app.render();
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(pixels);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        if (directory == null) throw new AssertionError("Android screenshot directory unavailable");
        try { PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image); }
        finally { image.dispose(); }
    }
}
