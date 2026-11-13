package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.File;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.ReferenceRandom;
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

/** Exercises the source record clock through three actual native turns. */
@RunWith(AndroidJUnit4.class)
public final class SourceBattleRecordClockInstrumentedTest {
    @Test public void labelsRecordSlotsAndCarriedCounterGateRealBoatPlayback() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field(WorldDominationGame.class, "splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                            field(WorldDominationGame.class, "flashAnimationAssets").get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!assets.update() && System.nanoTime() < deadline) Thread.sleep(5);
                    assertTrue("Authored battle assets ready", assets.isLoaded());

                    Constructor<GameState> constructor = GameState.class.getDeclaredConstructor(
                            int.class, long.class, boolean.class);
                    constructor.setAccessible(true);
                    GameState game = constructor.newInstance(1, 120L, false);
                    field(WorldDominationGame.class, "game").set(app, game);
                    ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                            "cityRenderer").get(app)).reset(game);
                    SourceBattleRecordClock clock = (SourceBattleRecordClock)
                            field(WorldDominationGame.class, "battleRecordClock").get(app);
                    TurnPlaybackSequence playback = (TurnPlaybackSequence)
                            field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
                    clock.reset();
                    queue(game, GameState.Action.RESOURCES);
                    queue(game, GameState.Action.MASS_MEDIA);
                    invoke(app, "confirmTurnAndPreparePlayback");
                    openBattleGate(app, playback, clock);
                    assertTrue(clock.hasPendingRecords());
                    advance(app);
                    assertEquals("First ordinary building record", 1, clock.getElapsedTicks());
                    assertEquals(TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
                    advance(app);
                    assertEquals("Last wrapper clears before the next source empty check",
                            TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
                    advance(app);
                    assertEquals("Next frame detects that all source entries are gone",
                            TurnPlaybackSequence.Phase.RESET_BARRIER, playback.getPhase());
                    finishTurn(app, playback);

                    stockSoldiers(game, 2);
                    queueAttack(game);
                    queueAttack(game);
                    invoke(app, "confirmTurnAndPreparePlayback");
                    List<FlashSoldierOneAnimation> boats = boats(app);
                    assertEquals(2, boats.size());
                    openBattleGate(app, playback, clock);
                    for (int tick = 0; tick < 20; tick++) advance(app);
                    assertFalse("Carried counter holds first record for twenty ticks",
                            boats.get(1).getActor(0).visible);
                    assertFalse(boats.get(0).getActor(0).visible);
                    advance(app);
                    assertTrue("Slot one activates at eligible tick21",
                            boats.get(1).getActor(0).visible);
                    assertFalse("Slot two waits one further source tick",
                            boats.get(0).getActor(0).visible);
                    capture(app, "source-record-clock-first-boat.png");
                    advance(app);
                    assertTrue(boats.get(0).getActor(0).visible);
                    capture(app, "source-record-clock-second-boat.png");
                    finishTurn(app, playback);

                    stockSoldiers(game, 1);
                    queueAttack(game);
                    queue(game, GameState.Action.RESOURCES);
                    invoke(app, "confirmTurnAndPreparePlayback");
                    boats = boats(app);
                    assertEquals(1, boats.size());
                    openBattleGate(app, playback, clock);
                    advance(app); // Pending building zeroes the carried20.
                    assertFalse(boats.get(0).getActor(0).visible);
                    advance(app); // Resources startNum1 activates.
                    assertFalse(boats.get(0).getActor(0).visible);
                    advance(app); // Soldier startNum2 activates.
                    assertTrue("Pending building resets carry; attack starts on tick3",
                            boats.get(0).getActor(0).visible);
                    capture(app, "source-record-clock-building-reset.png");
                    finishTurn(app, playback);
                    assertEquals(3, game.getTurn());
                    assertEquals(GameState.MatchStatus.ONGOING, game.getMatchStatus());
                } catch (Throwable error) {
                    failure.set(error);
                } finally { done.countDown(); }
            }));
            assertTrue("Three-turn source clock integration timed out",
                    done.await(150, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source record clock", failure.get());
        }
    }

    @Test public void resolvedTurnBuildsSoldierRoutesFromSourceBattleRandomBoundary() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field(WorldDominationGame.class, "splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                            field(WorldDominationGame.class, "flashAnimationAssets").get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!assets.update() && System.nanoTime() < deadline) Thread.sleep(5);
                    assertTrue("Authored battle assets ready", assets.isLoaded());
                    GameSetup setup = new GameSetup(0L);
                    assertTrue(setup.toggleOpponent(0));
                    assertTrue(setup.toggleOpponent(1));
                    GameState game = new GameState(setup);
                    field(WorldDominationGame.class, "game").set(app, game);
                    ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                            "cityRenderer").get(app)).reset(game);
                    game.selectAction(GameState.Action.PROPAGANDA);
                    assertTrue(game.configurePropaganda(1, 1));
                    assertTrue(game.acceptSelectedAction());
                    queueAttack(game);
                    invoke(app, "confirmTurnAndPreparePlayback");
                    assertEquals(1, boats(app).size());
                    FlashSoldierOneAnimation boat = boats(app).get(0);
                    FlashBattleRoutes.SoldierOneAttack route = (FlashBattleRoutes.SoldierOneAttack)
                            field(FlashSoldierOneAnimation.class, "route").get(boat);
                    // Executed source seed-0 turn: physical tailunit22,21,20 order.
                    float[][] sourceFirePoints = {{-25f, 221f}, {-13f, 210f}, {55f, 223f}};
                    for (int soldier = 0; soldier < sourceFirePoints.length; soldier++) {
                        FlashBattleRoutes.Point[] path = route.soldiers[soldier].waypoints;
                        assertEquals(160f, path[0].x, 0f);
                        assertEquals(201f, path[0].y, 0f);
                        assertEquals(sourceFirePoints[soldier][0], path[path.length - 2].x, 0f);
                        assertEquals(sourceFirePoints[soldier][1], path[path.length - 2].y, 0f);
                        assertEquals(29f, path[path.length - 1].x, 0f);
                        assertEquals(242f, path[path.length - 1].y, 0f);
                    }
                    ReferenceRandom routeRandom = (ReferenceRandom)
                            field(FlashSoldierOneAnimation.class, "presentationRandom").get(boat);
                    assertEquals("Exactly six source route draws precede activation",
                            0.47653099200938076, routeRandom.copy().nextDouble(), 0d);
                    TurnPlaybackSequence playback = (TurnPlaybackSequence)
                            field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
                    for (int tick = 0; tick < 240; tick++) advance(app);
                    capture(app, "source-battle-random-soldiers.png");
                    finishTurn(app, playback);
                    assertEquals(1, game.getTurn());
                    assertEquals(GameState.MatchStatus.ONGOING, game.getMatchStatus());
                } catch (Throwable error) {
                    failure.set(error);
                } finally { done.countDown(); }
            }));
            assertTrue("Source battle RNG integration timed out", done.await(150, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source battle random boundary", failure.get());
        }
    }

    private static void openBattleGate(WorldDominationGame app, TurnPlaybackSequence playback,
                                       SourceBattleRecordClock clock) throws Exception {
        int ticks = 0;
        while (playback.isWaitingForActionLabels() && ticks++ < 500) {
            assertEquals("Labels must not consume eligible battle ticks", 0, clock.getElapsedTicks());
            advance(app);
        }
        assertEquals(TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
        assertEquals("Timer opens gate after frame callback; first tick is next frame",
                0, clock.getElapsedTicks());
    }

    private static void finishTurn(WorldDominationGame app, TurnPlaybackSequence playback)
            throws Exception {
        int ticks = 0;
        while (playback.isBlockingInput() && ticks++ < 6200) advance(app);
        assertEquals("Real animations and reset barrier must complete",
                TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
    }

    private static void stockSoldiers(GameState state, int count) {
        GameState.WeaponState soldier = state.getHuman().getWeapons().get("soldier1");
        soldier.active = true;
        soldier.inStock = count;
    }

    private static void queueAttack(GameState state) {
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
    }

    private static void queue(GameState state, GameState.Action action) {
        state.selectAction(action);
        assertTrue(state.acceptSelectedAction());
    }

    @SuppressWarnings("unchecked")
    private static List<FlashSoldierOneAnimation> boats(WorldDominationGame app) throws Exception {
        return (List<FlashSoldierOneAnimation>) field(WorldDominationGame.class,
                "soldierBattleAnimations").get(app);
    }

    private static void capture(WorldDominationGame app, String name) throws Exception {
        field(WorldDominationGame.class, "skipNextFrameDelta").setBoolean(app, true);
        app.render();
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(pixels);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull("Android screenshot directory", directory);
        try {
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }

    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
