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
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
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

/** Runs original menu controls through the real phone viewport and touch dispatcher. */
@RunWith(AndroidJUnit4.class)
public final class SourceActionMenusInstrumentedTest {
    @Test public void originalMenusSelectTargetsEquipmentAndQueueAllFiveActions() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameState state = fresh(app);
                    begin(app, GameState.Action.MASS_MEDIA);
                    assertEquals("religion-chr", menu(app).getKey());
                    assertEquals(65, state.getHuman().religion);
                    assertEquals(20, state.getMassMediaPreviewGain());
                    capture(app, "source-mass-media.png");
                    accept(app, state, GameState.Action.MASS_MEDIA);
                    state = fresh(app);
                    begin(app, GameState.Action.DIPLOMACY);
                    sourceTap(app, 50, 80); sourceTap(app, 70, 169);
                    assertTrue(menu(app).hasLeader()); assertTrue(menu(app).hasOption());
                    assertEquals(1, state.getSelectedTargetPlayer());
                    capture(app, "source-diplomacy.png"); accept(app, state, GameState.Action.DIPLOMACY);

                    state = fresh(app); begin(app, GameState.Action.PROPAGANDA);
                    assertTrue(menu(app).isMapSelection()); cityTap(app, 1, 2);
                    assertEquals("propaganda2", menu(app).getKey());
                    capture(app, "source-propaganda.png"); accept(app, state, GameState.Action.PROPAGANDA);

                    state = fresh(app); begin(app, GameState.Action.DEFENSE);
                    equipment(app, "antiAir"); assertEquals("defense-antiAir", menu(app).getKey());
                    capture(app, "source-defense.png"); accept(app, state, GameState.Action.DEFENSE);

                    state = fresh(app); stock(state, "soldier1", 1); begin(app, GameState.Action.ATTACK);
                    assertEquals("attackSelect", menu(app).getKey()); equipment(app, "soldier1");
                    assertEquals("attack1", menu(app).getKey()); cityTap(app, 1, 0);
                    assertEquals("attack2-soldier1", menu(app).getKey());
                    capture(app, "source-direct-attack.png"); accept(app, state, GameState.Action.ATTACK);

                    state = fresh(app); begin(app, GameState.Action.ATTACK); equipment(app, "spy1");
                    cityTap(app, 1, 0); sourceTap(app, 313, 138);
                    assertEquals("factory", state.getSelectedSpyBuilding());
                    assertTrue(menu(app).getKey().startsWith("attack-spy-"));
                    capture(app, "source-spy.png");

                    state = fresh(app); stock(state, "rocket1", 1); stock(state, "warhead1", 1);
                    begin(app, GameState.Action.PREPARED_ATTACK); equipment(app, "rocket1");
                    assertEquals("preAttack-rocket1", menu(app).getKey());
                    capture(app, "source-prepared-step-one.png");
                    accept(app, state, GameState.Action.PREPARED_ATTACK);
                    state.selectAction(GameState.Action.RESOURCES); assertTrue(state.acceptSelectedAction());
                    state.confirmTurn();
                    while (state.getPendingHumanDiplomacyProposal() != null) assertTrue(state.respondToDiplomacyProposal(false));
                    begin(app, GameState.Action.PREPARED_ATTACK);
                    equipment(app, "warhead1"); assertEquals("preAttack4", menu(app).getKey());
                    cityTap(app, 1, 1); assertEquals("preAttack3-warhead", menu(app).getKey());
                    capture(app, "source-prepared-step-two.png");
                    accept(app, state, GameState.Action.PREPARED_ATTACK);

                    state = fresh(app); stock(state, "soldier1", 2);
                    equipment(app, "soldier1");
                    assertEquals(GameState.Action.ATTACK, state.getSelectedAction());
                    assertEquals("attack1", menu(app).getKey());
                    equipment(app, "soldier1"); assertEquals("attackSelect", menu(app).getKey());
                    assertNull(menu(app).getSelectedEquipment());
                    assertEquals("", field("selectedEquipment").get(app));
                    app.keyDown(com.badlogic.gdx.Input.Keys.BACK);
                    assertNull(state.getSelectedAction());

                    state = fresh(app); stock(state, "ship", 1); stock(state, "warhead1", 1);
                    begin(app, GameState.Action.PREPARED_ATTACK); equipment(app, "ship");
                    accept(app, state, GameState.Action.PREPARED_ATTACK);
                    state.selectAction(GameState.Action.RESOURCES); assertTrue(state.acceptSelectedAction());
                    state.confirmTurn();
                    while (state.getPendingHumanDiplomacyProposal() != null) assertTrue(state.respondToDiplomacyProposal(false));
                    begin(app, GameState.Action.PREPARED_ATTACK); equipment(app, "warhead1"); cityTap(app, 1, 0);
                    assertEquals("ship1-1", menu(app).getKey()); assertTrue(menu(app).isAcceptVisible());
                    capture(app, "source-ship-one-payload.png");
                    accept(app, state, GameState.Action.PREPARED_ATTACK);

                    state = fresh(app); begin(app, GameState.Action.DIPLOMACY);
                    state.getHuman().turnMoneyMillions = 0;
                    sourceTap(app, 50, 80); sourceTap(app, 70, 169);
                    assertEquals("diplomacy_error", menu(app).getKey()); assertFalse(menu(app).hasOption());
                    capture(app, "source-diplomacy-unaffordable-option.png");
                    sourceControlTap(app, "diplomacy_error", "error_window/btnOK");
                    assertEquals("diplomacy", menu(app).getKey()); assertTrue(menu(app).hasLeader());
                    app.keyDown(com.badlogic.gdx.Input.Keys.BACK); assertNull(state.getSelectedAction());

                    state = fresh(app); assertTrue(state.submitDiplomacyProposal(1, 0, 0));
                    capture(app, "source-diplomacy-offer.png"); sourceTap(app, 307, 258);
                    assertNull(state.getPendingHumanDiplomacyProposal());
                    assertTrue(state.getHuman().hasCeasefireWith(1));
                    state = fresh(app); assertTrue(state.submitDiplomacyProposal(1, 0, 3));
                    state.getHuman().turnMoneyMillions = 0;
                    capture(app, "source-diplomacy-demand.png"); sourceTap(app, 307, 258);
                    assertTrue(menu(app).hasPendingError());
                    capture(app, "source-diplomacy-warning.png");
                    sourceTap(app, 307, 258); assertTrue(menu(app).hasPendingError());
                    sourceControlTap(app, "diplomacy_error", "error_window/btnOK");
                    assertFalse(menu(app).hasPendingError());
                    sourceTap(app, 55, 258); assertNull(state.getPendingHumanDiplomacyProposal());
                } catch (Throwable error) {
                    failure.set(error);
                    android.util.Log.e("SourceMenuTest", "Original failure", error);
                    try {
                        WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                        com.badlogic.gdx.graphics.g2d.SpriteBatch batch =
                                (com.badlogic.gdx.graphics.g2d.SpriteBatch) field("batch").get(app);
                        if (batch.isDrawing()) batch.end();
                        field("game").set(app, null);
                    } catch (Exception cleanup) { error.addSuppressed(cleanup); }
                }
                finally { done.countDown(); }
            }));
            assertTrue("Source menu integration timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Original action menu integration", failure.get());
        }
    }
    private static GameState fresh(WorldDominationGame app) throws Exception {
        GameSetup setup = new GameSetup();
        for (int leader = 0; leader < 4; leader++) {
            setup.toggleOpponent(leader);
        }
        GameState state = new GameState(setup, 17L);
        field("game").set(app, state); field("modalOpen").setBoolean(app, false);
        return state;
    }
    private static void stock(GameState game, String name, int count) {
        GameState.WeaponState stock = game.getHuman().getWeapons().get(name);
        stock.active = true; stock.inStock = count;
    }
    private static SourceActionMenuState menu(WorldDominationGame app) throws Exception {
        return (SourceActionMenuState) field("sourceActionMenu").get(app);
    }
    private static void begin(WorldDominationGame app, GameState.Action action) throws Exception {
        ScrollPaneState scroll = (ScrollPaneState) field("navigationScroll").get(app);
        scroll.moveBy(-scroll.getOffset()); render(app);
        Rectangle viewport = (Rectangle) field("navViewport").get(app);
        Rectangle row = ((Rectangle[]) field("navRows").get(app))[action.ordinal()];
        scroll.moveBy(viewport.y + viewport.height / 2 - (row.y + row.height / 2));
        render(app); row = ((Rectangle[]) field("navRows").get(app))[action.ordinal()];
        tap(app, row.x + row.width / 2, row.y + row.height / 2);
        assertEquals(action, ((GameState) field("game").get(app)).getSelectedAction());
    }
    private static void equipment(WorldDominationGame app, String name) throws Exception {
        String[] names = (String[]) field("EQUIPMENT_WEAPON_NAMES").get(null);
        int index = -1;
        for (int i = 0; i < names.length; i++) if (name.equals(names[i])) index = i;
        assertTrue(index >= 0);
        ScrollPaneState scroll = (ScrollPaneState) field("equipmentScroll").get(app);
        scroll.moveBy(-scroll.getOffset()); render(app);
        Rectangle viewport = (Rectangle) field("equipmentViewport").get(app);
        Rectangle row = ((Rectangle[]) field("equipmentRows").get(app))[index];
        scroll.moveBy(viewport.y + viewport.height / 2 - (row.y + row.height / 2));
        render(app); row = ((Rectangle[]) field("equipmentRows").get(app))[index];
        assertTrue(viewport.contains(row.x + row.width / 2, row.y + row.height / 2));
        tap(app, row.x + row.width / 2, row.y + row.height / 2);
    }
    private static void cityTap(WorldDominationGame app, int player, int city) throws Exception {
        Rectangle map = (Rectangle) field("mapBounds").get(app);
        tap(app, BattlefieldCityLayout.x(map, player, city), BattlefieldCityLayout.y(map, player, city));
    }
    private static void sourceControlTap(WorldDominationGame app, String key, String name) throws Exception {
        com.badlogic.gdx.utils.JsonValue controls = new com.badlogic.gdx.utils.JsonReader()
                .parse(Gdx.files.internal("original/ui/action-menus/menus.json")).get(key).get("controls");
        for (com.badlogic.gdx.utils.JsonValue control : controls) {
            if (!control.getString("name").equals(name)) continue;
            com.badlogic.gdx.utils.JsonValue bounds = control.get("bounds");
            sourceTap(app, bounds.getFloat(0) + bounds.getFloat(2) / 2,
                    bounds.getFloat(1) + bounds.getFloat(3) / 2);
            return;
        }
        fail("Missing source control " + name);
    }
    private static void sourceTap(WorldDominationGame app, float x, float y) throws Exception {
        Rectangle modal = (Rectangle) field("modal").get(app);
        Rectangle fit = SourceActionMenuRenderer.fit(modal, new Rectangle());
        tap(app, fit.x + x * fit.width / 365, fit.y + fit.height - y * fit.height / 275);
    }
    private static void accept(WorldDominationGame app, GameState game, GameState.Action action) throws Exception {
        sourceTap(app, 307, 258); assertEquals(action, game.getQueuedAction(0));
    }
    private static void tap(WorldDominationGame app, float x, float y) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 screen = viewport.project(new Vector2(x, y));
        int sx = Math.round(screen.x), sy = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(sx, sy, 0, 0); app.touchUp(sx, sy, 0, 0);
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
