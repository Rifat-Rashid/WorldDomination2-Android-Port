package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.EconomyWarningText;
import com.example.worlddomination.game.ui.ProductionScreenLayout;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Actual Android touch routing through the source's replacement economy warning screens. */
@RunWith(AndroidJUnit4.class)
public final class EconomyWarningsInstrumentedTest {
    @Test public void originalWarningsBlockAcceptanceAndRetainSelectionsAfterOk() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    java.lang.reflect.Method init = WorldDominationGame.class.getDeclaredMethod("initializeGameplayAssets");
                    init.setAccessible(true); init.invoke(app);
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameState game = fresh(app);
                    List<String> messages = new ArrayList<>(game.getMessages());
                    navigation(app, GameState.Action.RESEARCH);
                    tap(app, ((Rectangle[]) field("technologyCards").get(app))[4]);
                    assertTrue(menu(app).isEconomyWarning(game));
                    assertEquals("research_error-" + game.getHuman().getReligionCode(), menu(app).getKey());
                    assertEquals(EconomyWarningText.RESEARCH_SPECIAL, menu(app).getError());
                    capture(app, "source-technology-special-warning.png");
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(0, game.getQueuedActionCount()); assertTrue(menu(app).isEconomyWarning(game));
                    app.keyDown(com.badlogic.gdx.Input.Keys.BACK);
                    assertFalse(menu(app).isEconomyWarning(game));
                    assertEquals(GameState.Action.RESEARCH, game.getSelectedAction());

                    render(app);
                    Rectangle[] cards = (Rectangle[]) field("technologyCards").get(app);
                    tap(app, cards[0]); assertTrue(game.isResearchTechnologySelected("rocket2"));
                    game.getHuman().turnMoneyMillions = 10;
                    tap(app, cards[1]);
                    assertEquals(EconomyWarningText.RESEARCH_FUNDS, menu(app).getError());
                    capture(app, "source-technology-funds-warning.png");
                    ok(app, menu(app).getKey());
                    assertFalse(menu(app).isEconomyWarning(game));
                    assertEquals(10, game.getSelectedResearchCostMillions());
                    assertEquals(10, game.getTurnMoneyMillions());
                    assertEquals(messages, game.getMessages());
                    render(app); tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(GameState.Action.RESEARCH, game.getQueuedAction(0));
                    assertEquals(0, game.getTurnMoneyMillions());

                    game = fresh(app); messages = new ArrayList<>(game.getMessages());
                    navigation(app, GameState.Action.PRODUCTION);
                    slider(app, 5, 31); assertEquals(30, game.getProductionPercent("soldier"));
                    slider(app, 2, 31);
                    assertEquals("production_error", menu(app).getKey());
                    assertEquals(EconomyWarningText.PRODUCTION_LOCKED, menu(app).getError());
                    capture(app, "source-production-locked-warning.png");
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(0, game.getQueuedActionCount()); assertTrue(menu(app).isEconomyWarning(game));
                    ok(app, "production_error");
                    assertEquals(GameState.Action.PRODUCTION, game.getSelectedAction());
                    assertEquals(30, game.getProductionPercent("soldier"));
                    assertEquals(0, game.getProductionPercent("special"));
                    assertEquals(65, game.getTurnMoneyMillions()); assertEquals(messages, game.getMessages());
                    capture(app, "source-production-warning-retained-allocation.png");
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(GameState.Action.PRODUCTION, game.getQueuedAction(0));
                    assertEquals(45, game.getTurnMoneyMillions());
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue("Economy warning Android test timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Original economy warnings", failure.get());
        }
    }
    private static GameState fresh(WorldDominationGame app) throws Exception {
        GameSetup setup = new GameSetup(0L); setup.toggleOpponent(0);
        GameState game = new GameState(setup); field("game").set(app, game);
        field("modalOpen").setBoolean(app, false); return game;
    }
    private static SourceActionMenuState menu(WorldDominationGame app) throws Exception {
        return (SourceActionMenuState) field("sourceActionMenu").get(app);
    }
    private static void navigation(WorldDominationGame app, GameState.Action action) throws Exception {
        render(app); tap(app, ((Rectangle[]) field("navRows").get(app))[action.ordinal()]); render(app);
        assertEquals(action, ((GameState) field("game").get(app)).getSelectedAction());
    }
    private static void slider(WorldDominationGame app, int index, int percent) throws Exception {
        Rectangle modal = (Rectangle) field("modal").get(app);
        tap(app, ProductionScreenLayout.setHandleBounds(index, percent, modal, new Rectangle()));
    }
    private static void ok(WorldDominationGame app, String key) throws Exception {
        JsonValue controls = new JsonReader().parse(Gdx.files.internal("original/ui/action-menus/menus.json"))
                .get(key).get("controls");
        for (JsonValue control : controls) {
            if (!control.getString("name").endsWith("btnOK")) continue;
            JsonValue bounds = control.get("bounds");
            Rectangle fit = SourceActionMenuRenderer.fit((Rectangle) field("modal").get(app), new Rectangle());
            float scale = fit.width / 365;
            tap(app, new Rectangle(fit.x + bounds.getFloat(0) * scale,
                    fit.y + fit.height - (bounds.getFloat(1) + bounds.getFloat(3)) * scale,
                    bounds.getFloat(2) * scale, bounds.getFloat(3) * scale)); return;
        }
        fail("Missing source OK control");
    }
    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        Vector2 point = ((Viewport) field("viewport").get(app))
                .project(new Vector2(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2));
        int x = Math.round(point.x), y = Gdx.graphics.getHeight() - Math.round(point.y);
        app.touchDown(x, y, 0, 0); app.touchUp(x, y, 0, 0);
    }
    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name); field.setAccessible(true); return field;
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
