package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.audio.AircraftAudioPlayback;
import com.example.worlddomination.game.audio.SatelliteAudioPlayback;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameSetup;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the source empty check and watchdog through the real GL game loop. */
@RunWith(AndroidJUnit4.class)
public final class SourceBattleCleanupInstrumentedTest {
    @Test public void allUnitsDoneKeepsTheSourceTwentyOneWrapperLimit() throws Exception {
        runInGame(app -> {
            GameState game = newGame();
            installGame(app, game);
            queue(game, GameState.Action.RESOURCES);
            queue(game, GameState.Action.MASS_MEDIA);
            invoke(app, "confirmTurnAndPreparePlayback");
            SourceStoppedClipQueue clips = (SourceStoppedClipQueue)
                    field(WorldDominationGame.class, "sourceStoppedClips").get(app);
            FlashSoldierOneAnimation.Actor[] stopped = new FlashSoldierOneAnimation.Actor[22];
            for (int index = 0; index < stopped.length; index++) {
                stopped[index] = new FlashSoldierOneAnimation.Actor();
                stopped[index].visible = true;
                clips.add(stopped[index]);
            }
            TurnPlaybackSequence sequence = playback(app);
            int guard = 0;
            while (sequence.getPhase() != TurnPlaybackSequence.Phase.CLEANUP_HANDOFF
                    && guard++ < 1000) {
                for (FlashSoldierOneAnimation.Actor actor : stopped) assertTrue(actor.visible);
                advance(app);
            }
            assertTrue("Source cleanup must run", guard < 1000);
            assertEquals(22, clips.size());
            assertTrue("The oldest wrapper survives the source's 21-item limit", stopped[0].visible);
            for (int index = 1; index < stopped.length; index++) assertFalse(stopped[index].visible);
            advance(app);
            assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
            assertEquals("EndBattle releases deletion-list references", 0, clips.size());
        });
    }

    @Test public void infantryMatchesSourcePosesAndDetachesAtAllUnitsDone() throws Exception {
        runInGame(app -> {
            GameSetup setup = new GameSetup(0L);
            assertTrue(setup.toggleOpponent(0));
            assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup);
            installGame(app, game);
            game.selectAction(GameState.Action.ATTACK);
            assertTrue(game.configureAttack(1, 0, "soldier1"));
            assertTrue(game.acceptSelectedAction());
            queue(game, GameState.Action.RESOURCES);
            invoke(app, "confirmTurnAndPreparePlayback");
            @SuppressWarnings("unchecked")
            List<FlashSoldierOneAnimation> boats = (List<FlashSoldierOneAnimation>)
                    field(WorldDominationGame.class, "soldierBattleAnimations").get(app);
            assertEquals(1, boats.size());
            FlashSoldierOneAnimation animation = boats.get(0);
            TurnPlaybackSequence sequence = playback(app);
            SourceBattleInterfaceFrames interfaceFrames = (SourceBattleInterfaceFrames)
                    field(WorldDominationGame.class, "battleInterfaceFrames").get(app);
            SourceBattleTicker ticker = (SourceBattleTicker)
                    field(WorldDominationGame.class, "battleTicker").get(app);
            double simulationNext = game.createPresentationRandomCopy().nextDouble();
            for (int tick = 1; tick <= 442; tick++) {
                advance(app);
                if (tick == 93) assertEquals(0, interfaceFrames.getSelectedStartFrame(4010));
                if (tick == 94) assertTrue(interfaceFrames.getSelectedStartFrame(4010) > 0);
                if (tick == 98) assertTrue(interfaceFrames.getSelectedStartFrame(4028) > 0);
                if (tick == 154) {
                    assertEquals(TurnPlaybackSequence.Phase.BATTLE, sequence.getPhase());
                    assertEquals(0L, clock(app).getElapsedTicks());
                }
                if (tick == 157) {
                    assertEquals(410f, animation.getActor(0).sourceX, 0f);
                    assertEquals(474f, animation.getActor(0).sourceY, 0f);
                    assertEquals(6, animation.getActor(0).sourceFrame);
                }
                if (tick == 158) {
                    assertEquals(408f, animation.getActor(0).sourceX, 0f);
                    assertEquals(476f, animation.getActor(0).sourceY, 0f);
                }
                if (tick == 215) {
                    assertTrue(animation.getActor(1).visible);
                    assertEquals("soldier001", animation.getActor(1).linkage);
                    assertEquals(165f, animation.getActor(1).sourceX, 0f);
                    assertEquals(206f, animation.getActor(1).sourceY, 0f);
                    assertEquals(162, animation.getActor(1).sourceFrame);
                    capture(app, "source-infantry-first-passenger.png");
                }
                if (tick == 226) {
                    assertTrue(animation.getActor(2).visible);
                    assertEquals(162f, animation.getActor(2).sourceX, 0f);
                    assertEquals(205f, animation.getActor(2).sourceY, 0f);
                    assertEquals(169, animation.getActor(2).sourceFrame);
                }
                if (tick == 237) {
                    assertTrue(animation.getActor(3).visible);
                    assertEquals(165f, animation.getActor(3).sourceX, 0f);
                    assertEquals(206f, animation.getActor(3).sourceY, 0f);
                    assertEquals(160, animation.getActor(3).sourceFrame);
                }
                if (tick == 259 || tick == 260) {
                    assertTrue(animation.getActor(0).visible);
                    assertEquals("boat001_still", animation.getActor(0).linkage);
                    assertEquals(1, animation.getActor(0).sourceFrame);
                    assertEquals(176f, animation.getActor(0).sourceX, 0f);
                    assertEquals(242f, animation.getActor(0).sourceY, 0f);
                }
                if (tick == 362 || tick == 363 || tick == 364) {
                    assertTrue(animation.getActor(3).visible);
                    assertEquals("soldier001_shoot", animation.getActor(3).linkage);
                    assertEquals(2, animation.getActor(3).sourceFrame);
                    assertEquals(51f, animation.getActor(3).sourceX, 0f);
                    assertEquals(229f, animation.getActor(3).sourceY, 0f);
                    assertFalse(animation.isFinished());
                }
                if (tick == 380) {
                    assertTrue("Stopped clips remain in the draw list", boats.contains(animation));
                    assertTrue(animation.isFinished());
                    assertEquals(TurnPlaybackSequence.Phase.BATTLE, sequence.getPhase());
                    capture(app, "source-infantry-stopped-poses.png");
                }
                if (tick == 381) {
                    assertEquals(381L, ticker.getFrameCount());
                    assertFalse(ticker.isRunning());
                    assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, sequence.getPhase());
                }
                if (tick >= 380 && tick <= 440) {
                    for (int actor = 0; actor < 4; actor++) {
                        assertTrue("Pose survives cleanup wait at " + tick,
                                animation.getActor(actor).visible);
                    }
                    assertTrue(boats.contains(animation));
                }
                if (tick == 441) {
                    assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, sequence.getPhase());
                    for (int actor = 0; actor < 4; actor++) {
                        assertFalse("allUnitsDone detaches each stopped clip",
                                animation.getActor(actor).visible);
                    }
                    capture(app, "source-infantry-detached-at-handoff.png");
                }
            }
            assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
            assertTrue(boats.isEmpty());
            assertEquals(simulationNext, game.createPresentationRandomCopy().nextDouble(), 0d);
            assertEquals(1, game.getTurn());
        });
    }

    @Test public void emptyCheckWaitsOneFrameAndSilentShipRecordOverlapsLabels()
            throws Exception {
        runInGame(app -> {
            GameState game = newGame();
            installGame(app, game);
            SourceBattleRecordClock clock = clock(app);
            TurnPlaybackSequence playback = playback(app);

            queue(game, GameState.Action.RESOURCES);
            queue(game, GameState.Action.MASS_MEDIA);
            invoke(app, "confirmTurnAndPreparePlayback");
            openBattleGate(app, playback, clock);
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                    field(WorldDominationGame.class, "flashAnimationAssets").get(app);
            @SuppressWarnings("unchecked")
            List<BattleAnimationSequence> effects = (List<BattleAnimationSequence>)
                    field(WorldDominationGame.class, "battleAnimations").get(app);
            // Source deletion/effect clips are independent of the active-unit empty check.
            BattleAnimationSequence effect = new BattleAnimationSequence(
                    assets.getTimeline("explosion_warhead_4"), 1, 0, 10_000, null);
            effects.add(effect);
            advance(app);
            assertEquals(1, clock.getElapsedTicks());
            advance(app);
            assertEquals("The second wrapper is removed after this frame's empty check",
                    TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
            capture(app, "source-cleanup-last-building-frame.png");
            advance(app);
            assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, playback.getPhase());
            assertTrue(playback.isBlockingInput());
            assertTrue("Effects do not hold the source unit array open", effects.contains(effect));
            assertTrue(app.keyDown(com.badlogic.gdx.Input.Keys.BACK));
            assertEquals("Back stays blocked during cleanup", 0f,
                    field(WorldDominationGame.class, "showExitPromptSeconds").getFloat(app), 0f);
            for (int tick = 1; tick < 60; tick++) {
                advance(app);
                assertEquals("Cleanup frame " + tick,
                        TurnPlaybackSequence.Phase.RESET_BARRIER, playback.getPhase());
                assertTrue(playback.isBlockingInput());
            }
            advance(app);
            assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, playback.getPhase());
            assertTrue(playback.isBlockingInput());
            capture(app, "source-cleanup-handoff.png");
            advance(app);
            assertEquals(TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
            assertFalse(playback.isBlockingInput());
            assertTrue("Handoff clears independent effect clips", effects.isEmpty());

            GameState.WeaponState ship = game.getHuman().getWeapons().get("ship");
            assertNotNull(ship);
            ship.active = true;
            ship.inStock = 1;
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(game.configurePreparedCarrier("ship"));
            assertTrue(game.acceptSelectedAction());
            queue(game, GameState.Action.SKIP);
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals(1, game.getLastTurnBattleRecords().size());
            assertTrue(game.getLastTurnBattleRecords().get(0).prepareCarrier);
            assertFalse(game.getLastTurnBattleRecords().get(0).pendingBuilding);
            assertEquals(0, clock.getElapsedTicks());
            advance(app); // No source unit or building wrapper exists, even while labels run.
            assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, playback.getPhase());
            assertEquals(0, clock.getElapsedTicks());
            assertTrue(playback.isWaitingForActionLabels());
            boolean sawPreparedLabel = false;
            for (int tick = 1; tick < 60; tick++) {
                advance(app);
                sawPreparedLabel |= playback.getCurrentAction()
                        == GameState.Action.PREPARED_ATTACK;
                assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, playback.getPhase());
                assertEquals("Silent record cannot consume a unit tick", 0,
                        clock.getElapsedTicks());
                assertTrue(playback.isBlockingInput());
            }
            assertTrue("The action label interval continues during cleanup", sawPreparedLabel);
            capture(app, "source-cleanup-silent-record.png");
            advance(app);
            assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF, playback.getPhase());
            assertTrue(playback.isBlockingInput());
            advance(app);
            assertEquals(TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
            assertFalse(playback.isBlockingInput());
            assertFalse(clock.hasPendingRecords());
        });
    }

    @Test public void watchdogStopsStalledCarrierAndItsLoopAtHandoff() throws Exception {
        runInGame(app -> {
            GameState game = newGame();
            installGame(app, game);
            TurnPlaybackSequence playback = playback(app);
            SourceBattleTicker ticker = (SourceBattleTicker)
                    field(WorldDominationGame.class, "battleTicker").get(app);
            Field audioField = field(WorldDominationGame.class, "audio");
            OriginalAudio originalAudio = (OriginalAudio) audioField.get(app);
            MusicProbe music = new MusicProbe();
            AircraftAudioPlayback aircraft = new AircraftAudioPlayback(music::create,
                    .8f, Runnable::run);
            Constructor<OriginalAudio> audioConstructor = OriginalAudio.class
                    .getDeclaredConstructor(SatelliteAudioPlayback.class,
                            AircraftAudioPlayback.class);
            audioConstructor.setAccessible(true);
            OriginalAudio audio = audioConstructor.newInstance(
                    new SatelliteAudioPlayback(cue -> null, .8f), aircraft);
            audioField.set(app, audio);
            try {
                GameState.PlayerState human = game.getHuman();
                stock(human, "ship", 1);
                stock(human, "warhead1", 1);
                field(GameState.PlayerState.class, "preparedCarrierName").set(human, "ship");
                game.selectAction(GameState.Action.PREPARED_ATTACK);
                assertTrue(game.configurePreparedShipStrike(1, 0, "warhead1"));
                assertTrue(game.acceptSelectedAction());
                queue(game, GameState.Action.RESOURCES);
                invoke(app, "confirmTurnAndPreparePlayback");
                List<FlashCarrierPlaneAnimation> planes = planes(app);
                assertEquals(1, planes.size());
                FlashCarrierPlaneAnimation originalPlane = planes.get(0);
                GameState.CombatEvent carrierEvent = null;
                for (GameState.CombatEvent event
                        : game.getLastTurnCombatEventsInUnitConstructionOrder()) {
                    if (FlashBattleRoutes.supportsCarrierPlaneStrike(event)) {
                        assertTrue("Watchdog fixture has one carrier event", carrierEvent == null);
                        carrierEvent = event;
                    }
                }
                assertNotNull("Watchdog carrier retains its resolved source event", carrierEvent);
                FlashBattleRoutes.CarrierPlaneStrike route = originalPlane.getRoute();
                // Fixture only: extend a real actor's route beyond the source watchdog.
                // Set it before construction so cached route length remains consistent.
                route.waypoints[0] = new FlashBattleRoutes.Point(50_000f, route.waypoints[0].y);
                FlashCarrierPlaneAnimation plane = new FlashCarrierPlaneAnimation(route,
                        field(FlashCarrierPlaneAnimation.class, "delayTicks").getInt(originalPlane),
                        originalPlane.getImpactLinkage(), originalPlane.getImpactCue());
                planes.set(0, plane);
                @SuppressWarnings("unchecked")
                Map<Object, AircraftAudioPlayback.Owner> owners =
                        (Map<Object, AircraftAudioPlayback.Owner>)
                                field(WorldDominationGame.class,
                                        "aircraftAudioOwners").get(app);
                AircraftAudioPlayback.Owner replaced = owners.remove(originalPlane);
                assertNotNull("original factory owner", replaced);
                replaced.stop();
                owners.put(plane, audio.createAircraftAudio(AircraftAudioPlayback.Kind.FIGHTER));
                // This synthetic stalled actor replaces the real factory
                // actor in both its render list and source update registry.
                SourceBattleUnitPlayback sourceUnits = (SourceBattleUnitPlayback)
                        field(WorldDominationGame.class, "sourceUnitPlayback").get(app);
                assertEquals("Fixture has one registered carrier", 1, sourceUnits.size());
                sourceUnits.reset();
                Method advanceCarrier = WorldDominationGame.class.getDeclaredMethod(
                        "advanceCarrierPlaneUnit", FlashCarrierPlaneAnimation.class,
                        GameState.CombatEvent.class, SourceCarrierPlaneDisplayDepths.class);
                advanceCarrier.setAccessible(true);
                GameState.CombatEvent stalledCarrierEvent = carrierEvent;
                sourceUnits.add(() -> {
                    try {
                        // This deliberately legacy actor has no source core or root binding.
                        return (Boolean) advanceCarrier.invoke(app, plane, stalledCarrierEvent, null);
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError("Stalled carrier source update", error);
                    }
                });
                for (int tick = 1; tick <= 800; tick++) {
                    advance(app);
                    assertEquals("Source frame " + tick, tick, ticker.getFrameCount());
                }
                assertEquals(TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
                assertTrue(plane.getActor().visible);
                MusicProbe.Voice takeoff = music.one(OriginalAudio.Cue.FIGHTER_TAKEOFF);
                MusicProbe.Voice flight = music.one(OriginalAudio.Cue.FIGHTER_FLIGHT);
                assertEquals("one source takeoff request", 1, takeoff.plays);
                assertEquals(0, flight.plays);
                takeoff.complete(); // Explicit completion for the synthetic stalled actor.
                assertEquals("retained fighter flight begins", 1, flight.plays);
                assertEquals(0, takeoff.stops);
                assertEquals(0, flight.stops);
                float frozenX = plane.getActor().sourceX;
                float frozenY = plane.getActor().sourceY;
                capture(app, "source-cleanup-watchdog-before.png");

                advance(app);
                assertEquals(801L, ticker.getFrameCount());
                assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER, playback.getPhase());
                assertFalse(ticker.isRunning());
                assertEquals(frozenX, plane.getActor().sourceX, 0f);
                assertEquals(frozenY, plane.getActor().sourceY, 0f);
                for (int tick = 1; tick < 60; tick++) {
                    advance(app);
                    assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER,
                            playback.getPhase());
                    assertEquals(frozenX, plane.getActor().sourceX, 0f);
                    assertEquals(frozenY, plane.getActor().sourceY, 0f);
                    assertEquals(0, flight.stops);
                    assertEquals(1, planes(app).size());
                }
                advance(app);
                assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                        playback.getPhase());
                assertEquals(0, flight.stops);
                capture(app, "source-cleanup-watchdog-handoff.png");
                advance(app);
                assertEquals(TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
                assertTrue(planes(app).isEmpty());
                assertEquals("only this owner is stopped", 1, takeoff.stops);
                assertEquals("flight stops at handoff", 1, flight.stops);
                assertEquals("takeoff media disposed", 1, takeoff.disposals);
                assertEquals("flight media disposed", 1, flight.disposals);
                assertTrue("discarded battle has no aircraft owners", owners.isEmpty());
                assertFalse(playback.isBlockingInput());
            } finally {
                audioField.set(app, originalAudio);
                audio.dispose();
            }
        });
    }

    @Test public void acceptedTurnWaitsForFullFlashAtlasBeforePresentationClock()
            throws Exception {
        runInGame(app -> {
            GameState game = newGame();
            installGame(app, game);
            TurnPlaybackSequence playback = playback(app);
            SourceBattleRecordClock recordClock = clock(app);
            SourceBattleTicker ticker = (SourceBattleTicker)
                    field(WorldDominationGame.class, "battleTicker").get(app);
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                    field(WorldDominationGame.class, "flashAnimationAssets").get(app);
            Field loaded = field(OriginalFlashAnimationAssets.class, "loaded");
            Method inputBlocked = WorldDominationGame.class
                    .getDeclaredMethod("isTurnInputBlocked");
            inputBlocked.setAccessible(true);
            assertTrue(assets.isLoaded());
            assertEquals(0, game.getTurn());
            queue(game, GameState.Action.RESOURCES);
            queue(game, GameState.Action.MASS_MEDIA);
            loaded.setBoolean(assets, false);
            try {
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals(1, game.getTurn());
                assertEquals(TurnPlaybackSequence.Phase.ACTION_LABELS,
                        playback.getPhase());
                for (int tick = 0; tick < 900; tick++) advance(app);
                assertEquals(1, game.getTurn());
                assertEquals(TurnPlaybackSequence.Phase.ACTION_LABELS,
                        playback.getPhase());
                assertNull(playback.getCurrentAction());
                assertEquals(0L, ticker.getFrameCount());
                assertEquals(0L, recordClock.getElapsedTicks());
                assertTrue((Boolean) inputBlocked.invoke(app));
            } finally {
                loaded.setBoolean(assets, true);
            }
            advance(app);
            assertEquals(1L, ticker.getFrameCount());
            assertTrue(playback.isWaitingForActionLabels());
            assertNull(playback.getCurrentAction());
            boolean sawResetBarrier = false;
            int resetTicks = 0;
            int handoffTicks = 0;
            int totalTicks = 0;
            while (playback.getPhase() != TurnPlaybackSequence.Phase.IDLE
                    && totalTicks++ < 6_200) {
                TurnPlaybackSequence.Phase before = playback.getPhase();
                advance(app);
                if (before == TurnPlaybackSequence.Phase.RESET_BARRIER) {
                    sawResetBarrier = true;
                    resetTicks++;
                }
                if (before == TurnPlaybackSequence.Phase.CLEANUP_HANDOFF) {
                    handoffTicks++;
                }
            }
            assertTrue("Source cleanup barrier occurred", sawResetBarrier);
            assertEquals("60 barrier ticks", 60, resetTicks);
            assertEquals("One handoff tick", 1, handoffTicks);
            assertEquals(TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
            assertFalse(playback.isBlockingInput());
            assertEquals(1, game.getTurn());
        });
    }

    interface GameCheck { void run(WorldDominationGame app) throws Exception; }

    static void runInGame(GameCheck check) throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
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
                    check.run(app);
                } catch (Throwable error) {
                    failure.set(error);
                } finally { done.countDown(); }
            }));
            assertTrue("Source battle cleanup timed out", done.await(150, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source battle cleanup", failure.get());
        }
    }

    private static GameState newGame() throws Exception {
        Constructor<GameState> constructor = GameState.class.getDeclaredConstructor(
                int.class, long.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, 120L, false);
    }

    private static void installGame(WorldDominationGame app, GameState game) throws Exception {
        field(WorldDominationGame.class, "game").set(app, game);
        ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                "cityRenderer").get(app)).reset(game);
        clock(app).reset();
    }

    private static void stock(GameState.PlayerState player, String name, int amount) {
        GameState.WeaponState weapon = player.getWeapons().get(name);
        assertNotNull(name, weapon);
        weapon.active = true;
        weapon.inStock = amount;
    }

    private static void queue(GameState game, GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }

    private static void openBattleGate(WorldDominationGame app,
                                       TurnPlaybackSequence playback,
                                       SourceBattleRecordClock clock) throws Exception {
        int ticks = 0;
        while (playback.isWaitingForActionLabels() && ticks++ < 500) {
            assertEquals(0, clock.getElapsedTicks());
            advance(app);
        }
        assertEquals(TurnPlaybackSequence.Phase.BATTLE, playback.getPhase());
        assertEquals(0, clock.getElapsedTicks());
    }

    private static SourceBattleRecordClock clock(WorldDominationGame app) throws Exception {
        return (SourceBattleRecordClock) field(WorldDominationGame.class,
                "battleRecordClock").get(app);
    }

    private static TurnPlaybackSequence playback(WorldDominationGame app) throws Exception {
        return (TurnPlaybackSequence) field(WorldDominationGame.class,
                "turnPlaybackSequence").get(app);
    }

    @SuppressWarnings("unchecked")
    private static List<FlashCarrierPlaneAnimation> planes(WorldDominationGame app)
            throws Exception {
        return (List<FlashCarrierPlaneAnimation>) field(WorldDominationGame.class,
                "carrierPlaneAnimations").get(app);
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
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image);
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

    static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class MusicProbe {
        final Map<OriginalAudio.Cue, List<Voice>> byCue = new HashMap<>();
        Music create(OriginalAudio.Cue cue) {
            Voice voice = new Voice();
            byCue.computeIfAbsent(cue, ignored -> new ArrayList<>()).add(voice);
            return voice.music;
        }
        Voice one(OriginalAudio.Cue cue) {
            List<Voice> voices = byCue.get(cue);
            assertNotNull("source cue " + cue, voices);
            assertEquals("one live aircraft media pair", 1, voices.size());
            return voices.get(0);
        }
        static final class Voice implements InvocationHandler {
            final Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(),
                    new Class<?>[]{Music.class}, this);
            Music.OnCompletionListener listener;
            int plays, stops, disposals;
            boolean playing;
            void complete() {
                playing = false;
                if (listener != null) listener.onCompletion(music);
            }
            @Override public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "play": plays++; playing = true; return null;
                    case "stop": stops++; playing = false; return null;
                    case "dispose": disposals++; return null;
                    case "pause": playing = false; return null;
                    case "isPlaying": return playing;
                    case "setOnCompletionListener":
                        listener = (Music.OnCompletionListener) args[0]; return null;
                    case "setPan": case "setVolume": case "setLooping":
                    case "setPosition": return null;
                    case "getVolume": case "getPosition": return 0f;
                    case "isLooping": return false;
                    default: throw new AssertionError("Unexpected Music method "
                            + method.getName());
                }
            }
        }
    }
}
