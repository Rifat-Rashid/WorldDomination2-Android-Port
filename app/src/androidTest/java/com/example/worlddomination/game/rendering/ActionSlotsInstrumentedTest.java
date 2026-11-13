package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
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
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.ConfirmTurnAnimation;
import com.example.worlddomination.game.ui.ScrollPaneState;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real viewport/touch routing for source empty-tube Skip and fixed-slot cancellation. */
@RunWith(AndroidJUnit4.class)
public final class ActionSlotsInstrumentedTest {
    @Test public void tubesOfferSkipKeepSecondActionAndResolveOnlyAcceptedActions() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    awaitCenterArtwork(app);
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameSetup setup = new GameSetup(0L);
                    setup.toggleOpponent(0);
                    GameState state = new GameState(setup);
                    field("game").set(app, state); field("modalOpen").setBoolean(app, false);
                    render(app);
                    assertEquals(9, ((Rectangle[]) field("navRows").get(app)).length);

                    tube(app, 1);
                    assertEquals(GameState.Action.SKIP, state.getSelectedAction());
                    assertEquals("skip_action", menu(app).getKey());
                    assertEquals(0, state.getNextActionSlot());
                    capture(app, "source-skip-action.png");
                    sourceTap(app, 50, 258);
                    assertNull(state.getSelectedAction()); assertEquals(0, state.getQueuedActionCount());

                    queueEconomy(app, state);
                    tube(app, 0);
                    assertNull(state.getQueuedAction(0));
                    assertEquals(GameState.Action.MASS_MEDIA, state.getQueuedAction(1));
                    assertNull(state.getSelectedAction());
                    assertEquals(1, state.getQueuedActionCount());
                    // A queued navigation button also invokes its associated tube cancellation.
                    navigation(app, GameState.Action.MASS_MEDIA);
                    assertNull(state.getQueuedAction(1)); assertEquals(0, state.getQueuedActionCount());

                    queueEconomy(app, state); tube(app, 0); tube(app, 0);
                    assertEquals(GameState.Action.SKIP, state.getSelectedAction());
                    sourceTap(app, 307, 258);
                    assertEquals(GameState.Action.SKIP, state.getQueuedAction(0));
                    assertEquals(GameState.Action.MASS_MEDIA, state.getQueuedAction(1));
                    assertEquals(65, state.getTurnMoneyMillions());
                    invoke(app, "synchronizeTurnControls"); advance(app, 11);
                    ConfirmTurnAnimation confirm = (ConfirmTurnAnimation) field("confirmTurnAnimation").get(app);
                    assertEquals(11, confirm.getFrame());
                    capture(app, "source-fixed-action-slots.png");
                    tap(app, (Rectangle) field("confirmButton").get(app));
                    assertTrue(confirm.isPending()); assertEquals(0, state.getTurn());
                    tube(app, 0);
                    assertEquals(2, state.getQueuedActionCount());
                    advance(app, 20);
                    assertEquals(1, state.getTurn());
                    assertEquals(65, state.getHuman().moneyMillions);
                    assertEquals(85, state.getHuman().religion);
                    assertEquals(0, state.getQueuedActionCount());
                    for (GameState.ActionBuildingEvent event : state.getLastTurnActionBuildings())
                        assertNotEquals(GameState.Action.SKIP, event.action);
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue("Action-slot Android integration timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source action tubes", failure.get());
        }
    }

    @Test public void economyMenusRouteTouchesReserveBudgetsAndRefundTheirOwnSlots() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    awaitCenterArtwork(app);
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameSetup setup = new GameSetup(0L); setup.toggleOpponent(0);
                    GameState state = new GameState(setup);
                    field("game").set(app, state); field("modalOpen").setBoolean(app, false);
                    int funds = state.getTurnMoneyMillions();
                    assertEquals(65, funds);

                    navigation(app, GameState.Action.RESOURCES);
                    capture(app, "source-resources.png");
                    tap(app, (Rectangle) field("modalCancel").get(app));
                    assertNull(state.getSelectedAction()); assertEquals(0, state.getQueuedActionCount());
                    navigation(app, GameState.Action.RESOURCES);
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
                    assertEquals(funds, state.getTurnMoneyMillions()); tube(app, 0);

                    navigation(app, GameState.Action.PRODUCTION);
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(0, state.getQueuedActionCount());
                    assertEquals(GameState.Action.PRODUCTION, state.getSelectedAction());
                    Rectangle modal = (Rectangle) field("modal").get(app);
                    Rectangle start = com.example.worlddomination.game.ui.ProductionScreenLayout
                            .setHandleBounds(5, 0, modal, new Rectangle());
                    Rectangle end = com.example.worlddomination.game.ui.ProductionScreenLayout
                            .setHandleBounds(5, 61, modal, new Rectangle());
                    drag(app, start.x + start.width / 2, start.y + start.height / 2,
                            end.x + end.width / 2, end.y + end.height / 2);
                    assertEquals(60, state.getProductionPercent("soldier"));
                    assertEquals(39, state.getSelectedProductionTotalBudgetMillions());
                    capture(app, "source-production.png");
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(GameState.Action.PRODUCTION, state.getQueuedAction(0));
                    assertEquals(funds - 39, state.getTurnMoneyMillions());

                    navigation(app, GameState.Action.RESEARCH);
                    render(app);
                    String[] choices = (String[]) field("researchChoiceByCard").get(app);
                    Rectangle[] cards = (Rectangle[]) field("technologyCards").get(app);
                    int available = -1;
                    for (int index = 0; index < choices.length; index++)
                        if (choices[index] != null && state.isResearchAvailable(choices[index])) {
                            available = index; break;
                        }
                    assertTrue("At least one source technology is affordable", available >= 0);
                    String technology = choices[available];
                    int cost = state.getTechnologyCost(technology);
                    tap(app, cards[available]); assertTrue(state.isResearchTechnologySelected(technology));
                    tap(app, cards[available]); assertFalse(state.isResearchTechnologySelected(technology));
                    tap(app, cards[available]); assertEquals(cost, state.getSelectedResearchCostMillions());
                    capture(app, "source-technology.png");
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(GameState.Action.RESEARCH, state.getQueuedAction(1));
                    assertEquals(funds - 39 - cost, state.getTurnMoneyMillions());
                    tube(app, 0);
                    assertEquals(funds - cost, state.getTurnMoneyMillions());
                    assertNull(state.getQueuedAction(0));
                    assertEquals(GameState.Action.RESEARCH, state.getQueuedAction(1));
                    tube(app, 1);
                    assertEquals(funds, state.getTurnMoneyMillions());
                    assertEquals(0, state.getQueuedActionCount());
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue("Economy menu Android integration timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source economy menus", failure.get());
        }
    }

    private static void awaitCenterArtwork(WorldDominationGame app) throws Exception {
        com.example.worlddomination.game.assets.OriginalFlashAnimationAssets artwork =
                (com.example.worlddomination.game.assets.OriginalFlashAnimationAssets)
                        field("flashAnimationAssets").get(app);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!artwork.areCenterUiAssetsLoaded() && System.nanoTime() < deadline) {
            artwork.update();
            Thread.sleep(10);
        }
        assertTrue("Original center-screen artwork must be loaded", artwork.areCenterUiAssetsLoaded());
        assertNotNull("Original source center-screen timeline must exist", artwork.getCenterUiTimeline());
    }

    private static void drag(WorldDominationGame app, float x1, float y1, float x2, float y2)
            throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 start = viewport.project(new Vector2(x1, y1));
        Vector2 end = viewport.project(new Vector2(x2, y2));
        int sx = Math.round(start.x), sy = Gdx.graphics.getHeight() - Math.round(start.y);
        int ex = Math.round(end.x), ey = Gdx.graphics.getHeight() - Math.round(end.y);
        app.touchDown(sx, sy, 0, 0); app.touchDragged(ex, ey, 0); app.touchUp(ex, ey, 0, 0);
    }

    private static void queueEconomy(WorldDominationGame app, GameState state) throws Exception {
        navigation(app, GameState.Action.RESOURCES);
        tap(app, (Rectangle) field("modalAccept").get(app));
        assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
        navigation(app, GameState.Action.MASS_MEDIA); sourceTap(app, 307, 258);
        assertEquals(GameState.Action.MASS_MEDIA, state.getQueuedAction(1));
    }
    private static SourceActionMenuState menu(WorldDominationGame app) throws Exception {
        return (SourceActionMenuState) field("sourceActionMenu").get(app);
    }
    private static void navigation(WorldDominationGame app, GameState.Action action) throws Exception {
        ScrollPaneState scroll = (ScrollPaneState) field("navigationScroll").get(app);
        scroll.moveBy(-scroll.getOffset()); render(app);
        Rectangle viewport = (Rectangle) field("navViewport").get(app);
        Rectangle row = ((Rectangle[]) field("navRows").get(app))[action.ordinal()];
        scroll.moveBy(viewport.y + viewport.height / 2 - (row.y + row.height / 2)); render(app);
        tap(app, ((Rectangle[]) field("navRows").get(app))[action.ordinal()]);
    }
    private static void tube(WorldDominationGame app, int slot) throws Exception {
        tap(app, ((Rectangle[]) field("actionSlots").get(app))[slot]);
    }
    private static void sourceTap(WorldDominationGame app, float x, float y) throws Exception {
        Rectangle fit = SourceActionMenuRenderer.fit((Rectangle) field("modal").get(app), new Rectangle());
        tap(app, fit.x + x * fit.width / 365, fit.y + fit.height - y * fit.height / 275);
    }
    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        tap(app, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }
    private static void tap(WorldDominationGame app, float x, float y) throws Exception {
        Vector2 position = ((Viewport) field("viewport").get(app)).project(new Vector2(x, y));
        int sx = Math.round(position.x), sy = Gdx.graphics.getHeight() - Math.round(position.y);
        app.touchDown(sx, sy, 0, 0); app.touchUp(sx, sy, 0, 0);
    }
    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app);
    }
    private static void advance(WorldDominationGame app, int ticks) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class); method.setAccessible(true);
        for (int tick = 0; tick < ticks; tick++) method.invoke(app, 1f / 24f);
    }
    private static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true); app.render();
    }
    private static void capture(WorldDominationGame app, String name) throws Exception {
        render(app);
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try { PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), pixmap); }
        finally { pixmap.dispose(); }
    }
}
