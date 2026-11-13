package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.audio.SatelliteAudioPlayback;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.testing.GameActivityScenario;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real fixed-update playback of the UI-reachable seed-zero source turn 28. */
@RunWith(AndroidJUnit4.class)
public final class SatelliteTurnPlaybackInstrumentedTest {
    private static final int LAST_TICK = 533;

    @Test public void satelliteAndConcurrentChinookKeepSourceOrderThroughCleanup()
            throws Exception {
        Observation[] source = readSource();
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
                    assertTrue("Authored battle assets ready", assets.isLoaded());

                    GameState game = prepareTurn28();
                    field("game").set(app, game);
                    ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
                    ((SourceBattleRecordClock) field("battleRecordClock").get(app)).reset();
                    invoke(app, "confirmTurnAndPreparePlayback");
                    OriginalAudio audio = (OriginalAudio) field("audio").get(app);
                    SatelliteAudioPlayback soundPlayback = (SatelliteAudioPlayback)
                            SourceBattleCleanupInstrumentedTest.field(OriginalAudio.class,
                                    "satellitePlayback").get(audio);
                    @SuppressWarnings("unchecked")
                    List<Object> soundVoices = (List<Object>) SourceBattleCleanupInstrumentedTest
                            .field(SatelliteAudioPlayback.class, "voices").get(soundPlayback);
                    assertEquals("Prepare both original Music clips before the laser", 1,
                            soundVoices.size());
                    Object satelliteSound = soundVoices.get(0);
                    Field soundUsed = SourceBattleCleanupInstrumentedTest.field(
                            satelliteSound.getClass(), "used");
                    Field soundClosed = SourceBattleCleanupInstrumentedTest.field(
                            satelliteSound.getClass(), "closed");
                    assertNotNull(SourceBattleCleanupInstrumentedTest.field(
                            satelliteSound.getClass(), "laser").get(satelliteSound));
                    assertNotNull(SourceBattleCleanupInstrumentedTest.field(
                            satelliteSound.getClass(), "attack").get(satelliteSound));
                    assertEquals(28, game.getTurn());
                    assertEquals("source action records", 4,
                            game.getLastTurnBattleRecords().size());
                    int satelliteEvents = 0;
                    int chinookEvents = 0;
                    for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
                        if (FlashBattleRoutes.supportsSatelliteStrike(event)) {
                            satelliteEvents++;
                            assertEquals(2, event.battleStartNum);
                            assertEquals(93, event.casualtiesMillions);
                        }
                        if (FlashBattleRoutes.supportsChinookTank(event)) {
                            chinookEvents++;
                            assertEquals(3, event.battleStartNum);
                        }
                    }
                    assertEquals(1, satelliteEvents);
                    assertEquals(1, chinookEvents);
                    @SuppressWarnings("unchecked")
                    List<FlashSatelliteAnimation> satellites = (List<FlashSatelliteAnimation>)
                            field("satelliteBattleAnimations").get(app);
                    @SuppressWarnings("unchecked")
                    List<FlashChinookTankAnimation> tanks = (List<FlashChinookTankAnimation>)
                            field("chinookTankAnimations").get(app);
                    assertEquals("one source satellite wrapper", 1, satellites.size());
                    assertEquals("one source Chinook/tank cohort", 1, tanks.size());
                    FlashSatelliteAnimation satellite = satellites.get(0);
                    FlashChinookTankAnimation chinookTank = tanks.get(0);
                    SourceBattleTicker ticker = (SourceBattleTicker) field("battleTicker").get(app);
                    SourceStoppedClipQueue queue = (SourceStoppedClipQueue)
                            field("sourceStoppedClips").get(app);
                    TurnPlaybackSequence sequence = (TurnPlaybackSequence)
                            field("turnPlaybackSequence").get(app);
                    assertFrame(0, source[0], satellite, ticker, queue);

                    for (int tick = 1; tick <= LAST_TICK; tick++) {
                        advance(app);
                        assertFrame(tick, source[tick], satellite, ticker, queue);
                        assertEquals("Laser sound starts on the source firing visit " + tick,
                                tick >= 397, soundUsed.getBoolean(satelliteSound));
                        assertEquals("Sound remains owned until endBattle " + tick,
                                tick == LAST_TICK, soundClosed.getBoolean(satelliteSound));
                        if (tick < LAST_TICK) {
                            assertEquals("One prepared voice; no per-frame sound allocation " + tick,
                                    1, soundVoices.size());
                        }
                        if (tick == 423 || tick == 424 || tick == 425
                                || tick == 457 || tick == 458) {
                            FlashChinookTankAnimation.Actor chinook = chinookTank.getActor(0);
                            float expectedX = tick == 423 ? 1081.75f
                                    : tick == 424 ? 1087.75f
                                    : tick == 425 ? 1093.75f
                                    : tick == 457 ? 1285.75f : 1291.75f;
                            float expectedY = tick == 423 ? 829.75f
                                    : tick == 424 ? 833.75f
                                    : tick == 425 ? 837.75f
                                    : tick == 457 ? 965.75f : 969.75f;
                            assertEquals("Chinook X tick " + tick, expectedX,
                                    chinook.sourceX, 0.001f);
                            assertEquals("Chinook Y tick " + tick, expectedY,
                                    chinook.sourceY, 0.001f);
                            assertEquals("Chinook stop tick " + tick,
                                    tick >= 457, chinookTank.isChinookStopped());
                            assertEquals("Chinook removal tick " + tick,
                                    tick >= 458, chinookTank.isChinookRemoved());
                        }
                        if (tick == 470 || tick == 471) {
                            assertEquals("tank body stop tick " + tick,
                                    true, chinookTank.isTankStopped());
                            assertEquals("turret stop tick " + tick,
                                    true, chinookTank.isTurretStopped());
                            assertEquals("tank body removal tick " + tick,
                                    tick == 471, chinookTank.isTankRemoved());
                            assertEquals("turret removal tick " + tick,
                                    tick == 471, chinookTank.isTurretRemoved());
                        }
                        if (tick == 424) {
                            assertTrue("source laser stop breaks after Chinook visit",
                                    satellite.didStopSourceFrame());
                            assertEquals(0, queue.size());
                        }
                        if (tick == 472) {
                            assertFalse("source ticker stops after final active wrapper",
                                    ticker.isRunning());
                            assertEquals(4, queue.size());
                        }
                        if (tick == 532) {
                            assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                                    sequence.getPhase());
                            assertFalse(satellite.isClipVisible());
                            assertEquals(4, queue.size());
                        }
                    }
                    assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
                    assertFalse(sequence.isBlockingInput());
                    assertEquals("EndBattle releases deletion-list references", 0, queue.size());
                    assertTrue("EndBattle releases completion-driven voices", soundVoices.isEmpty());
                } catch (Throwable error) {
                    failure.set(error);
                } finally { done.countDown(); }
            }));
            assertTrue("Satellite turn playback timed out", done.await(150, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Satellite turn playback", failure.get());
        }
    }

    private static GameState prepareTurn28() {
        GameSetup setup = new GameSetup(0L);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        String[] research = {"rocket2", "rocket3", "rocket4", "warhead2",
                "warhead3", "warhead4", "bioload2", "bioload3", "bioload4",
                "soldier2", "soldier3", "soldier4", "sub", "ship", "space",
                "specialLoad"};
        int rejected = 0;
        for (String technology : research) {
            rejected += rejectPending(game);
            game.selectAction(GameState.Action.RESEARCH);
            assertTrue("research " + technology, game.selectResearchTechnology(technology));
            assertTrue(game.acceptSelectedAction());
            resources(game);
            game.confirmTurn();
        }
        for (int turn = 17; turn <= 26; turn++) {
            rejected += rejectPending(game);
            game.selectAction(GameState.Action.PRODUCTION);
            assertTrue("special production turn " + turn,
                    game.configureProduction("special", 100));
            assertTrue(game.acceptSelectedAction());
            resources(game);
            game.confirmTurn();
        }
        rejected += rejectPending(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("space"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        assertEquals(27, game.getTurn());
        rejected += rejectPending(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 0, "specialLoad"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, rejected);
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    private static int rejectPending(GameState game) {
        int count = 0;
        GameState.DiplomacyProposal proposal;
        while ((proposal = game.getPendingHumanDiplomacyProposal()) != null) {
            if (game.getTurn() == 12) {
                assertEquals(1, proposal.proposerId);
                assertEquals(5, proposal.optionIndex);
            } else if (game.getTurn() == 26) {
                assertEquals(2, proposal.proposerId);
                assertEquals(1, proposal.optionIndex);
            } else {
                throw new AssertionError("unexpected proposal turn " + game.getTurn());
            }
            assertTrue(game.respondToDiplomacyProposal(false));
            count++;
        }
        return count;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void assertFrame(int tick, Observation expected,
                                    FlashSatelliteAnimation satellite,
                                    SourceBattleTicker ticker, SourceStoppedClipQueue queue) {
        assertEquals("satellite X tick " + tick, expected.x,
                satellite.getSourceX(), 0.0001f);
        assertEquals("satellite Y tick " + tick, expected.y,
                satellite.getSourceY(), 0.0001f);
        assertEquals("display X tick " + tick, expected.displayX,
                satellite.getDisplaySourceX(), 0.0001f);
        assertEquals("display Y tick " + tick, expected.displayY,
                satellite.getDisplaySourceY(), 0.0001f);
        assertEquals("satellite nested frame tick " + tick, expected.nestedFrame,
                satellite.getSourceClipFrameNumber());
        assertEquals("satellite active tick " + tick, expected.active,
                !satellite.isFinished());
        assertEquals("satellite attached tick " + tick, expected.attached,
                satellite.isClipVisible());
        assertEquals("laser alpha tick " + tick, expected.laserAlpha / 100f,
                satellite.getLaserAlpha(), 0.0001f);
        assertEquals("source ticker tick " + tick, expected.tickerRunning,
                ticker.isRunning());
        // EndBattle clears references one tick after allUnitsDone detaches clips.
        assertEquals("source deletion count tick " + tick,
                tick == LAST_TICK ? 0 : expected.deleteCount,
                queue.size());
    }

    private static Observation[] readSource() throws Exception {
        Observation[] frames = new Observation[LAST_TICK + 1];
        try (InputStream stream = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("source/satellite-seed0-turn28.csv");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull(header);
            assertTrue(header.endsWith(",nestedFrame,strokeCount"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source columns", 27, cells.length);
                int tick = Integer.parseInt(cells[0]);
                assertTrue(tick >= 0 && tick <= LAST_TICK);
                assertEquals("source satellite unit", "satelliteunit1", cells[3]);
                assertNull("duplicate source tick " + tick, frames[tick]);
                frames[tick] = new Observation(cells);
            }
        }
        for (int tick = 0; tick <= LAST_TICK; tick++) {
            assertNotNull("missing source tick " + tick, frames[tick]);
        }
        return frames;
    }

    private static final class Observation {
        final float x;
        final float y;
        final float displayX;
        final float displayY;
        final float laserAlpha;
        final boolean active;
        final boolean attached;
        final boolean tickerRunning;
        final int nestedFrame;
        final int deleteCount;

        Observation(String[] cells) {
            x = Float.parseFloat(cells[8]);
            y = Float.parseFloat(cells[9]);
            displayX = Float.parseFloat(cells[23]);
            displayY = Float.parseFloat(cells[24]);
            laserAlpha = cells[11].isEmpty() ? 0f : Float.parseFloat(cells[11]);
            active = !cells[12].isEmpty();
            deleteCount = Integer.parseInt(cells[15]);
            tickerRunning = Boolean.parseBoolean(cells[16]);
            attached = Boolean.parseBoolean(cells[19]);
            nestedFrame = Integer.parseInt(cells[25]);
        }
    }

    private static void advance(WorldDominationGame app) throws Exception {
        Method update = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        update.setAccessible(true);
        update.invoke(app, 1f / 24f);
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
