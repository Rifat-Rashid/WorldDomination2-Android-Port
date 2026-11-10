package com.example.worlddomination.game.ui;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Drives native touch routing and rendering on the Android GL thread. */
@RunWith(AndroidJUnit4.class)
public final class PropagandaUiInstrumentedTest {
    @Test
    public void citySelectionConfirmationCancelAndTurnPlaybackUseTheOriginalPanel() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onGl(scenario, view -> {
                view.touchDown(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, 0, 0);
                view.touchUp(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, 0, 0);
            });
            AtomicBoolean loaded = new AtomicBoolean();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (!loaded.get() && System.nanoTime() < deadline) {
                onGl(scenario, view -> {
                    OriginalFlashAnimationAssets assets = field(view, "flashAnimationAssets");
                    loaded.set(assets != null && assets.areCenterUiAssetsLoaded());
                });
                if (!loaded.get()) Thread.sleep(250);
            }
            assertTrue("Original dialog atlas did not load", loaded.get());
            onGl(scenario, view -> {
                SetupScreenLayout setup = field(view, "setupLayout");
                tap(view, setup.leaderCards[0]);
                tap(view, setup.leaderCards[1]);
                tap(view, setup.startButton);
                assertNotNull(field(view, "game"));
                // Keep the full UI, with a deterministic no-opponent fixture for result assertions.
                java.lang.reflect.Constructor<GameState> fixture = GameState.class
                        .getDeclaredConstructor(int.class, long.class, boolean.class);
                fixture.setAccessible(true);
                setField(view, "game", fixture.newInstance(1, 329L, false));
                setField(view, "accumulator", 0f);
            });
            onGl(scenario, view -> {
                tapNav(view, 0);
                assertTrue(field(view, "modalOpen"));
                ScrollPaneState equipment = field(view, "equipmentScroll");
                float equipmentOffset = equipment.getOffset();
                Rectangle nav = field(view, "navViewport");
                Viewport viewport = field(view, "viewport");
                Vector2 from = viewport.project(new Vector2(nav.x + nav.width / 2f, nav.y + 12f));
                Vector2 to = viewport.project(new Vector2(nav.x + nav.width / 2f,
                        nav.y + nav.height - 12f));
                view.touchDown(Math.round(from.x), Gdx.graphics.getHeight() - Math.round(from.y), 0, 0);
                view.touchDragged(Math.round(to.x), Gdx.graphics.getHeight() - Math.round(to.y), 0);
                view.touchUp(Math.round(to.x), Gdx.graphics.getHeight() - Math.round(to.y), 0, 0);
                assertEquals(GameState.Action.RESOURCES, ((GameState) field(view, "game")).getSelectedAction());
                assertEquals(equipmentOffset, equipment.getOffset(), 0.001f);
                // The left controls continue to work while another action panel is open.
                tapNav(view, 7);
                GameState game = field(view, "game");
                assertEquals(GameState.Action.PROPAGANDA, game.getSelectedAction());
                assertFalse(field(view, "modalOpen"));
                assertFalse(game.canAcceptPropaganda());
                tapCity(view, 0, 0);
                assertFalse(field(view, "modalOpen"));
                assertFalse(game.canAcceptPropaganda());
            });
            capture(scenario, "propaganda-select-city");
            onGl(scenario, view -> {
                tapCity(view, 1, 0);
                GameState game = field(view, "game");
                assertTrue(game.canAcceptPropaganda());
                assertTrue(field(view, "modalOpen"));
                tap(view, field(view, "modal"));
                assertEquals(1, game.getSelectedTargetPlayer());
                assertEquals(0, game.getSelectedTargetCity());
                assertEquals(0, game.getQueuedActionCount());
                OriginalFlashAnimationAssets assets = field(view, "flashAnimationAssets");
                assertNotNull(assets.getShape(3886)); // Authored statue/propaganda illustration.
                assertNotNull(assets.getShape(3880)); // Authored PROPAGANDA title.
            });
            capture(scenario, "propaganda-confirmation");
            onGl(scenario, view -> {
                tap(view, field(view, "modalCancel"));
                GameState game = field(view, "game");
                assertNull(game.getSelectedAction());
                assertEquals(0, game.getQueuedActionCount());
                tapNav(view, 7);
                assertFalse(game.canAcceptPropaganda());
                tapCity(view, 2, 3);
                assertTrue(field(view, "modalOpen"));
                tap(view, field(view, "modalAccept"));
                assertFalse(field(view, "modalOpen"));
                assertEquals(GameState.Action.PROPAGANDA, game.getQueuedAction(0));
                tapNav(view, 0);
                tap(view, field(view, "modalAccept"));
                assertEquals(2, game.getQueuedActionCount());
                // First tick synchronizes the newly filled slots; ten more open the hatch.
                advance(view, 11);
                com.example.worlddomination.game.ui.ConfirmTurnAnimation confirm = field(view, "confirmTurnAnimation");
                assertEquals(11, confirm.getFrame());
                tap(view, field(view, "confirmButton"));
                assertTrue(confirm.isPending());
                assertEquals(0, game.getTurn());
                advance(view, 20);
                assertEquals(1, game.getLastTurnPropagandaEvents().size());
                assertEquals(2, game.getLastTurnPropagandaEvents().get(0).targetPlayerId);
                assertEquals(3, game.getLastTurnPropagandaEvents().get(0).targetCityIndex);
            });
            capture(scenario, "propaganda-queued-turn");
        }
    }

    private static void advance(WorldDominationGame view, int ticks) throws Exception {
        java.lang.reflect.Method update = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        update.setAccessible(true);
        for (int tick = 0; tick < ticks; tick++) update.invoke(view, 1f / 24f);
    }

    private static void tapNav(WorldDominationGame view, int index) throws Exception {
        ScrollPaneState scroll = field(view, "navigationScroll");
        scroll.moveBy(index == 0 ? -scroll.getLimit() : scroll.getLimit());
        // Drawing updates the same hit rectangles used by the native scroll panel.
        java.lang.reflect.Method layout = WorldDominationGame.class.getDeclaredMethod("drawNavigationRows");
        layout.setAccessible(true);
        layout.invoke(view);
        Rectangle[] rows = field(view, "navRows");
        Rectangle viewport = field(view, "navViewport");
        assertTrue("Navigation row must be visible", viewport.contains(
                rows[index].x + rows[index].width / 2f, rows[index].y + rows[index].height / 2f));
        tap(view, rows[index]);
    }

    private static void tapCity(WorldDominationGame view, int player, int city) throws Exception {
        Rectangle map = field(view, "mapBounds");
        tap(view, BattlefieldCityLayout.x(map, player, city), BattlefieldCityLayout.y(map, player, city));
    }

    private static void tap(WorldDominationGame view, Rectangle bounds) throws Exception {
        tap(view, bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f);
    }

    private static void tap(WorldDominationGame view, float x, float y) throws Exception {
        Viewport viewport = field(view, "viewport");
        Vector2 point = viewport.project(new Vector2(x, y));
        int screenY = Gdx.graphics.getHeight() - Math.round(point.y);
        view.touchDown(Math.round(point.x), screenY, 0, 0);
        view.touchUp(Math.round(point.x), screenY, 0, 0);
    }

    private static void capture(ActivityScenario<MainActivity> scenario, String name) throws Exception {
        onGl(scenario, view -> {
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0,
                    Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            try {
                File directory = androidx.test.platform.app.InstrumentationRegistry
                        .getInstrumentation().getTargetContext().getExternalFilesDir("propaganda-evidence");
                PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name + ".png").getAbsolutePath()),
                        pixels, -1, true);
            } finally {
                pixels.dispose();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(object);
    }

    private static void setField(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private interface GlAction { void run(WorldDominationGame view) throws Exception; }

    private static void onGl(ActivityScenario<MainActivity> scenario, GlAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try {
                action.run((WorldDominationGame) activity.getApplicationListener());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        }));
        assertTrue("Android GL thread did not execute touch test", done.await(60, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Propaganda UI regression", failure.get());
    }
}
