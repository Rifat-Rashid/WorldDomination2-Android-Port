package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.ScrollPaneState;
import com.example.worlddomination.game.ui.SourceActionInstruction;
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

/** Source infoLine prompts through real Android navigation, stock, map and Cancel touches. */
@RunWith(AndroidJUnit4.class)
public final class SourceActionInstructionsInstrumentedTest {
    @Test public void promptsWarnRearmAndExpireOnlyOnGameplayTicks() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    OriginalFlashAnimationAssets artwork = (OriginalFlashAnimationAssets)
                            field("flashAnimationAssets").get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    while (!artwork.areCenterUiAssetsLoaded() && System.nanoTime() < deadline) {
                        artwork.update();
                        Thread.sleep(10);
                    }
                    assertTrue("Original source menu artwork must load", artwork.areCenterUiAssetsLoaded());
                    assertNotNull(artwork.getCenterUiTimeline());
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameSetup setup = new GameSetup(0L);
                    assertTrue(setup.toggleOpponent(0));
                    assertTrue(setup.toggleOpponent(4));
                    GameState state = new GameState(setup);
                    field("game").set(app, state);
                    field("modalOpen").setBoolean(app, false);
                    // Match the normal setup-to-game initialization before capturing.
                    ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(state);
                    ((BattlefieldFlagRenderer) field("flagRenderer").get(app)).reset();
                    SourceActionInstruction instruction = (SourceActionInstruction)
                            field("sourceActionInstruction").get(app);

                    navigation(app, GameState.Action.PROPAGANDA);
                    assertEquals("propaganda1", menu(app).getKey());
                    assertPrompt(instruction, SourceActionInstruction.SELECT_CITY, 108);
                    render(app);
                    GlyphLayout glyphs = (GlyphLayout) field("instructionLayout").get(app);
                    assertTrue("Wrapped source instruction needs positive glyph height", glyphs.height > 0f);
                    Rectangle viewport = (Rectangle) field("instructionViewport").get(app);
                    Rectangle cancel = ((Rectangle[]) field("actionFooterButtons").get(app))[1];
                    assertTrue("Instruction clip stays above Propaganda Cancel",
                            viewport.height > 0f && viewport.y >= cancel.y + cancel.height + 2f);
                    capture(app, "source-propaganda-instruction.png");
                    city(app, 0, 0);
                    assertPrompt(instruction, SourceActionInstruction.OWN_CITY, 108);
                    assertEquals("propaganda1", menu(app).getKey());
                    capture(app, "source-propaganda-own-city-warning.png");
                    tap(app, cancel);
                    assertNull(state.getSelectedAction());
                    assertPrompt(instruction, SourceActionInstruction.OWN_CITY, 108);
                    advance(app, 10);
                    city(app, 0, 0); // Source citySelect warns even with no action selected.
                    assertPrompt(instruction, SourceActionInstruction.OWN_CITY, 108);

                    assertTrue("Fixture accepts source cease-fire proposal",
                            state.submitDiplomacyProposal(1, 0, 0));
                    assertTrue(state.respondToDiplomacyProposal(true));
                    assertTrue(state.getHuman().hasCeasefireWith(1));
                    navigation(app, GameState.Action.PROPAGANDA);
                    assertPrompt(instruction, SourceActionInstruction.SELECT_CITY, 108);
                    city(app, 1, 0);
                    assertPrompt(instruction, SourceActionInstruction.CEASEFIRE, 108);
                    assertEquals("propaganda1", menu(app).getKey());
                    capture(app, "source-propaganda-ceasefire-warning.png");
                    tap(app, ((Rectangle[]) field("actionFooterButtons").get(app))[1]);
                    assertNull(state.getSelectedAction());
                    assertPrompt(instruction, SourceActionInstruction.CEASEFIRE, 108);

                    navigation(app, GameState.Action.DEFENSE);
                    assertEquals("defenseSelect", menu(app).getKey());
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 108);
                    advance(app, 20);
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 88);
                    double nextRandom = state.createPresentationRandomCopy().nextDouble();
                    render(app); render(app); render(app);
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 88);
                    assertEquals("Drawing must not consume game randomness", nextRandom,
                            state.createPresentationRandomCopy().nextDouble(), 0.0);
                    navigation(app, GameState.Action.PROPAGANDA);
                    assertPrompt(instruction, SourceActionInstruction.SELECT_CITY, 108);
                    navigation(app, GameState.Action.DEFENSE);
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 108);

                    navigation(app, GameState.Action.DEFENSE); // Same navigation row cancels.
                    assertNull(state.getSelectedAction());
                    GameState.WeaponState soldier = state.getHuman().getWeapons().get("soldier1");
                    assertNotNull(soldier);
                    soldier.active = true;
                    // The source stock handler requires inStock - inUse > 0 even
                    // when toggling off; retain a spare to exercise that branch.
                    soldier.inStock = 2;
                    equipment(app, "soldier1"); // Idle stock chooses Attack and its weapon.
                    assertEquals(GameState.Action.ATTACK, state.getSelectedAction());
                    assertEquals("attack1", menu(app).getKey());
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 108);
                    advance(app, 20);
                    equipment(app, "soldier1");
                    assertEquals("attackSelect", menu(app).getKey());
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 88);
                    equipment(app, "soldier1");
                    assertEquals("attack1", menu(app).getKey());
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 108);

                    advance(app, 107);
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 1);
                    render(app);
                    assertPrompt(instruction, SourceActionInstruction.SELECT_WEAPON, 1);
                    advance(app, 1);
                    assertNull("Source infoLine expires on tick 108", instruction.getText());
                    assertEquals(0, instruction.getTicksRemaining());
                    render(app);
                    assertTrue("History layout remains available after transient expires",
                            ((GlyphLayout) field("messageHistoryLayout").get(app)).height > 0f);
                    assertEquals("No GL error after action prompts", GL20.GL_NO_ERROR,
                            Gdx.gl.glGetError());
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }));
            assertTrue("Source action instruction integration timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source action instructions", failure.get());
        }
    }

    private static void assertPrompt(SourceActionInstruction state, String text, int ticks) {
        assertEquals(text, state.getText());
        assertEquals(ticks, state.getTicksRemaining());
    }

    private static SourceActionMenuState menu(WorldDominationGame app) throws Exception {
        return (SourceActionMenuState) field("sourceActionMenu").get(app);
    }

    private static void navigation(WorldDominationGame app, GameState.Action action) throws Exception {
        ScrollPaneState scroll = (ScrollPaneState) field("navigationScroll").get(app);
        scroll.moveBy(-scroll.getOffset());
        render(app);
        Rectangle viewport = (Rectangle) field("navViewport").get(app);
        Rectangle row = ((Rectangle[]) field("navRows").get(app))[action.ordinal()];
        scroll.moveBy(viewport.y + viewport.height / 2f - (row.y + row.height / 2f));
        render(app);
        tap(app, ((Rectangle[]) field("navRows").get(app))[action.ordinal()]);
    }

    private static void equipment(WorldDominationGame app, String name) throws Exception {
        String[] names = (String[]) field("EQUIPMENT_WEAPON_NAMES").get(null);
        int index = -1;
        for (int i = 0; i < names.length; i++) if (name.equals(names[i])) index = i;
        assertTrue("Fixture weapon must have a stock row", index >= 0);
        ScrollPaneState scroll = (ScrollPaneState) field("equipmentScroll").get(app);
        scroll.moveBy(-scroll.getOffset());
        render(app);
        Rectangle viewport = (Rectangle) field("equipmentViewport").get(app);
        Rectangle row = ((Rectangle[]) field("equipmentRows").get(app))[index];
        scroll.moveBy(viewport.y + viewport.height / 2f - (row.y + row.height / 2f));
        render(app);
        row = ((Rectangle[]) field("equipmentRows").get(app))[index];
        assertTrue("Stock row must be in its viewport",
                viewport.contains(row.x + row.width / 2f, row.y + row.height / 2f));
        tap(app, row);
    }

    private static void city(WorldDominationGame app, int player, int city) throws Exception {
        Rectangle map = (Rectangle) field("mapBounds").get(app);
        tap(app, BattlefieldCityLayout.x(map, player, city),
                BattlefieldCityLayout.y(map, player, city));
    }

    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        tap(app, bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f);
    }

    private static void tap(WorldDominationGame app, float x, float y) throws Exception {
        Vector2 screen = ((Viewport) field("viewport").get(app)).project(new Vector2(x, y));
        int sx = Math.round(screen.x), sy = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(sx, sy, 0, 0);
        app.touchUp(sx, sy, 0, 0);
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void advance(WorldDominationGame app, int ticks) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        for (int tick = 0; tick < ticks; tick++) method.invoke(app, 1f / 24f);
    }

    private static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true);
        app.render();
    }

    private static void capture(WorldDominationGame app, String name) throws Exception {
        render(app);
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try { PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image); }
        finally { image.dispose(); }
    }
}
