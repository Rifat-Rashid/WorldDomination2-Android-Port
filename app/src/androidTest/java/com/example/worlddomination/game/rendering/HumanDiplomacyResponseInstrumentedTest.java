package com.example.worlddomination.game.rendering;

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
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ConfirmTurnAnimation;
import com.example.worlddomination.testing.GameActivityScenario;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Checks the real pending-diplomacy modal and the next source turn boundary. */
@RunWith(AndroidJUnit4.class)
public final class HumanDiplomacyResponseInstrumentedTest {
    @Test public void modalAnswersWaitForNextTurnWhileDemandReservesMoney() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                            field("flashAnimationAssets").get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!assets.update() && System.nanoTime() < deadline) Thread.sleep(5);
                    assertTrue("Original modal artwork ready", assets.isLoaded());

                    Constructor<GameState> constructor = GameState.class.getDeclaredConstructor(
                            int.class, long.class, boolean.class);
                    constructor.setAccessible(true);
                    GameState game = constructor.newInstance(1, 22L, false);
                    field("game").set(app, game);
                    field("modalOpen").setBoolean(app, false);
                    ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
                    game.getHuman().moneyMillions = 200;
                    game.getHuman().turnMoneyMillions = 200;
                    assertTrue(game.submitDiplomacyProposal(1, 0, 0));
                    assertTrue(game.submitDiplomacyProposal(2, 0, 0));
                    assertTrue(game.submitDiplomacyProposal(3, 0, 5));
                    int firstMoney = game.getPlayerState(1).moneyMillions;
                    int secondMoney = game.getPlayerState(2).moneyMillions;
                    int thirdMoney = game.getPlayerState(3).moneyMillions;
                    double firstRegard = game.getPlayerState(1).getRegardTowardExact(0);
                    double secondRegard = game.getPlayerState(2).getRegardTowardExact(0);
                    double thirdRegard = game.getPlayerState(3).getRegardTowardExact(0);

                    assertEquals(1, game.getPendingHumanDiplomacyProposal().proposerId);
                    capture(app, "source-diplomacy-deferred-offer.png");
                    sourceTap(app, 307, 258); // authored btnAccept
                    assertEquals(2, game.getPendingHumanDiplomacyProposal().proposerId);
                    assertFalse(game.getHuman().hasCeasefireWith(1));
                    assertEquals(200, game.getHuman().moneyMillions);
                    assertEquals(firstMoney, game.getPlayerState(1).moneyMillions);
                    assertEquals(firstRegard,
                            game.getPlayerState(1).getRegardTowardExact(0), 0d);

                    sourceTap(app, 55, 258); // authored btnCancel
                    assertEquals(3, game.getPendingHumanDiplomacyProposal().proposerId);
                    assertEquals(secondMoney, game.getPlayerState(2).moneyMillions);
                    assertEquals(secondRegard,
                            game.getPlayerState(2).getRegardTowardExact(0), 0d);

                    capture(app, "source-diplomacy-deferred-demand.png");
                    sourceTap(app, 307, 258);
                    assertNull("All pending modals close on tap",
                            game.getPendingHumanDiplomacyProposal());
                    assertEquals("Demand reserves only turn spending", 110,
                            game.getTurnMoneyMillions());
                    assertEquals(200, game.getHuman().moneyMillions);
                    assertEquals(thirdMoney, game.getPlayerState(3).moneyMillions);
                    assertEquals(thirdRegard,
                            game.getPlayerState(3).getRegardTowardExact(0), 0d);
                    assertFalse(game.getHuman().hasCeasefireWith(3));
                    capture(app, "source-diplomacy-answers-queued.png");

                    game.selectAction(GameState.Action.RESOURCES);
                    assertTrue(game.acceptSelectedAction());
                    game.selectAction(GameState.Action.MASS_MEDIA);
                    assertTrue(game.acceptSelectedAction());
                    invoke(app, "synchronizeTurnControls");
                    advance(app, 10);
                    Rectangle button = (Rectangle) field("confirmButton").get(app);
                    tap(app, button.x + button.width / 2, button.y + button.height / 2);
                    ConfirmTurnAnimation confirm = (ConfirmTurnAnimation)
                            field("confirmTurnAnimation").get(app);
                    assertTrue("Real confirm touch starts turn closure", confirm.isPending());
                    assertEquals(0, game.getTurn());
                    advance(app, 20);
                    assertEquals(1, game.getTurn());
                    assertTrue(game.getHuman().hasCeasefireWith(1));
                    assertFalse(game.getHuman().hasCeasefireWith(2));
                    assertTrue(game.getHuman().hasCeasefireWith(3));
                    List<String> messages = game.getMessages(0);
                    assertTrue(messages.contains("Nation 0 rejected diplomacy with nation 2."));
                    assertTrue(messages.contains("Nation 0 accepted diplomacy with nation 1."));
                    assertTrue(messages.contains("Nation 0 accepted diplomacy with nation 3."));
                    capture(app, "source-diplomacy-answers-settled.png");
                } catch (Throwable error) {
                    failure.set(error);
                } finally { done.countDown(); }
            }));
            assertTrue("Diplomacy UI integration timed out", done.await(120, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Diplomacy UI integration", failure.get());
        }
    }

    private static void sourceTap(WorldDominationGame app, float x, float y) throws Exception {
        Rectangle modal = (Rectangle) field("modal").get(app);
        Rectangle fit = SourceActionMenuRenderer.fit(modal, new Rectangle());
        tap(app, fit.x + x * fit.width / 365, fit.y + fit.height - y * fit.height / 275);
    }

    private static void tap(WorldDominationGame app, float x, float y) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 screen = viewport.project(new Vector2(x, y));
        int sx = Math.round(screen.x);
        int sy = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(sx, sy, 0, 0);
        app.touchUp(sx, sy, 0, 0);
    }

    private static void capture(WorldDominationGame app, String name) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true);
        app.render();
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }

    private static void advance(WorldDominationGame app, int ticks) throws Exception {
        Method update = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        update.setAccessible(true);
        for (int tick = 0; tick < ticks; tick++) update.invoke(app, 1f / 24f);
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
