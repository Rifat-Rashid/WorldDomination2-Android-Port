package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/** Canonical interface callbacks pause while the fresh source battle root advances. */
@RunWith(AndroidJUnit4.class)
public final class SourceBattlePhaseIsolationInstrumentedTest {
    private static final int SIMPLE_LAST_TICK = 219;
    private static final int SUICIDE_LAST_TICK = 364;

    @Test public void twoLegalTurnsFreezeCanonicalClipsWhileBattleChildrenAdvance()
            throws Exception {
        SimpleRow[][] source = readSimple();
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L);
            setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0));
            assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup);
            installGame(app, game);
            BattlefieldFlagRenderer flags = flagRenderer(app);
            BattlefieldCityRenderer cities = cityRenderer(app);
            TurnPlaybackSequence sequence = playback(app);
            for (int turn = 1; turn <= 2; turn++) {
                research(game, turn == 1 ? "soldier2" : "soldier3");
                resources(game);
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals("legal source turn", turn, game.getTurn());
                assertTrue("fresh isolated city root", cities.isBattleActive());
                assertEquals("fresh battle flag phase", 0, flags.getFrameIndex());
                assertEquals("canonical flag reset by source refresh", 0,
                        flags.getCanonicalFrameIndex());
                ReferenceRandom privateRandom = (ReferenceRandom)
                        field(WorldDominationGame.class, "battlePresentationRandom").get(app);
                assertNotNull(privateRandom);
                double simulationNext = game.createPresentationRandomCopy().nextDouble();
                FlashActionBuildingAnimation technology = actionBuilding(app, 0, 1);
                FlashActionBuildingAnimation pump = actionBuilding(app, 0, 2);
                assertNotNull("source technology slot", technology);
                assertNotNull("source resources slot", pump);
                BattlefieldDisplayState display = (BattlefieldDisplayState)
                        field(WorldDominationGame.class, "battlefieldDisplay").get(app);
                assertSame("battle technology parent bound for drawing", technology,
                        display.actionBuilding(0, 1).getValue().owner);
                assertSame("battle resources parent bound for drawing", pump,
                        display.actionBuilding(0, 2).getValue().owner);
                OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                        field(WorldDominationGame.class, "flashAnimationAssets").get(app);
                FlashAnimationTimeline parents = assets.getTimeline(
                        OriginalFlashAnimationAssets.ACTION_BUILDINGS_TIMELINE);
                FlashAnimationTimeline pumpTimeline = assets.getTimelineBySymbolId(
                        FlashActionBuildingAnimation.RESOURCE_PUMP_SYMBOL_ID);
                assertNotNull(parents);
                assertNotNull(pumpTimeline);
                for (int tick = 0; tick <= SIMPLE_LAST_TICK; tick++) {
                    SimpleRow expected = source[turn][tick];
                    if (tick > 0) advance(app);
                    assertEquals("canonical flag stays frozen at tick " + tick,
                            expected.canonicalFlag - 1, flags.getCanonicalFrameIndex());
                    assertEquals("canonical city frame at tick " + tick,
                            expected.canonicalCityFrame, canonicalCity(cities, 0).getFrameNumber());
                    assertEquals("canonical city alpha at tick " + tick,
                            expected.canonicalCityAlpha,
                            canonicalCity(cities, 0).getSourceAlpha(), 0.001f);
                    assertEquals("model random untouched at tick " + tick, simulationNext,
                            game.createPresentationRandomCopy().nextDouble(), 0d);
                    assertEquals("source canonical random at tick " + tick,
                            expected.canonicalRandom, simulationNext, 0d);
                    assertEquals("private battle random at tick " + tick,
                            expected.battleRandom, privateRandom.copy().nextDouble(), 0d);
                    if (tick == SIMPLE_LAST_TICK) {
                        assertEquals("source battle end", TurnPlaybackSequence.Phase.IDLE,
                                sequence.getPhase());
                        assertFalse("canonical cities restored", cities.isBattleActive());
                        assertEquals("canonical flag phase restored", 0, flags.getFrameIndex());
                        assertTrue("battle slot parents discarded", actionBuildings(app).isEmpty());
                        assertNull("battle pump owner removed from display",
                                display.actionBuilding(0, 2).getValue().owner);
                        continue;
                    }
                    assertTrue("battle city bundle active at tick " + tick,
                            cities.isBattleActive());
                    assertEquals("battle flag phase at tick " + tick,
                            expected.battleFlag - 1, flags.getFrameIndex());
                    assertEquals("battle city frame at tick " + tick,
                            expected.battleCityFrame, cities.getSourceCityFrame(0));
                    assertEquals("battle city alpha at tick " + tick,
                            expected.battleCityAlpha, cities.getSourceCityAlpha(0), 0.001f);
                    assertSame("source technology parent frame at tick " + tick,
                            parents.getFrame(expected.battleAb01Frame - 1),
                            parents.getFrameByLabel(technology.frameName));
                    assertSame("source resource parent frame at tick " + tick,
                            parents.getFrame(expected.battleAb02Frame - 1),
                            parents.getFrameByLabel(pump.frameName));
                    assertEquals("source resource pump child at tick " + tick,
                            expected.battlePumpFrame - 1,
                            pump.getNestedFrameIndex(
                                    FlashActionBuildingAnimation.RESOURCE_PUMP_SYMBOL_ID,
                                    pumpTimeline.getFrameCount()));
                }
                advance(app); // First canonical idle callback after source battle ends.
                assertEquals("canonical flag resumes on first idle frame", 1,
                        flags.getFrameIndex());
                assertFalse(cities.isBattleActive());
                assertEquals("canonical city stays settled", source[turn][219].canonicalCityFrame,
                        cities.getSourceCityFrame(0));
            }
        });
    }

    @Test public void suicideImpactStaysInBattleCityAndCanonicalCityResumesClean()
            throws Exception {
        HandoffRow[] source = readHandoff();
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceSuicideDepthsInstrumentedTest.gameReadyForAttack();
            installGame(app, game);
            BattlefieldCityRenderer cities = cityRenderer(app);
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals(4, game.getTurn());
            double simulationNext = game.createPresentationRandomCopy().nextDouble();
            for (int tick = 0; tick <= SUICIDE_LAST_TICK; tick++) {
                HandoffRow expected = source[tick];
                if (tick > 0) advance(app);
                CityShrinkSequence canonical = canonicalCity(cities, 7);
                assertEquals("canonical city7 frame at tick " + tick,
                        expected.canonicalFrame, canonical.getFrameNumber());
                assertEquals("canonical city7 alpha at tick " + tick,
                        expected.canonicalAlpha, canonical.getSourceAlpha(), 0.001f);
                assertFalse("canonical city callback stays idle at tick " + tick,
                        canonical.isAnimating());
                assertEquals("simulation RNG untouched at tick " + tick, simulationNext,
                        game.createPresentationRandomCopy().nextDouble(), 0d);
                if (tick < 363) {
                    assertTrue("impact belongs to battle city at tick " + tick,
                            cities.isBattleActive());
                    assertEquals("battle city7 frame at tick " + tick,
                            expected.battleFrame, cities.getSourceCityFrame(7));
                    assertEquals("battle city7 alpha at tick " + tick,
                            expected.battleAlpha, cities.getSourceCityAlpha(7), 0.001f);
                } else {
                    assertFalse("canonical city restored at source handoff", cities.isBattleActive());
                    assertEquals("no battle dust leaks into canonical city", -1,
                            cities.getCityDustFrame(7));
                    assertEquals("no battle random dust name leaks", -1,
                            cities.getCityDustName(7));
                }
            }
            assertEquals("source first idle frame", TurnPlaybackSequence.Phase.IDLE,
                    playback(app).getPhase());
        });
    }

    private static SimpleRow[][] readSimple() throws Exception {
        SimpleRow[][] rows = new SimpleRow[3][SIMPLE_LAST_TICK + 1];
        try (BufferedReader input = fixture("source-phase-two-turns.csv")) {
            assertEquals("turn,tick,canonicalClock,canonicalFlag0Tint,battleFlag0Tint,"
                    + "canonicalCity0Frame,canonicalCity0Alpha,battleCity0Frame,"
                    + "battleCity0Alpha,battleAb01Frame,battleAb02Frame,battleAb02PumpFrame,"
                    + "canonicalRngNext,battleRngNext", input.readLine());
            String line;
            int count = 0;
            while ((line = input.readLine()) != null) {
                String[] c = line.split(",", -1);
                assertEquals(14, c.length);
                int turn = Integer.parseInt(c[0]), tick = Integer.parseInt(c[1]);
                assertTrue(turn == 1 || turn == 2);
                assertTrue(tick >= 0 && tick <= SIMPLE_LAST_TICK);
                assertNull("duplicate source row", rows[turn][tick]);
                rows[turn][tick] = new SimpleRow(c);
                count++;
            }
            assertEquals("two complete source battles", 440, count);
        }
        for (int turn = 1; turn <= 2; turn++)
            for (int tick = 0; tick <= SIMPLE_LAST_TICK; tick++)
                assertNotNull("missing source turn " + turn + " tick " + tick, rows[turn][tick]);
        return rows;
    }

    private static HandoffRow[] readHandoff() throws Exception {
        HandoffRow[] rows = new HandoffRow[SUICIDE_LAST_TICK + 1];
        try (BufferedReader input = fixture("source-phase-suicide-handoff.csv")) {
            assertEquals("phase,tick,canonicalClock,canonicalCity7Frame,"
                    + "canonicalCity7Alpha,battleCity7Frame,battleCity7Alpha,"
                    + "playbackDone,menuHasPlayback", input.readLine());
            String line;
            int count = 0;
            while ((line = input.readLine()) != null) {
                String[] c = line.split(",", -1);
                assertEquals(9, c.length);
                int tick = Integer.parseInt(c[1]);
                assertTrue(tick >= 0 && tick <= SUICIDE_LAST_TICK);
                assertNull("duplicate suicide source tick", rows[tick]);
                rows[tick] = new HandoffRow(c);
                count++;
            }
            assertEquals("source battle and first idle", SUICIDE_LAST_TICK + 1, count);
        }
        for (int tick = 0; tick <= SUICIDE_LAST_TICK; tick++)
            assertNotNull("missing suicide source tick " + tick, rows[tick]);
        return rows;
    }

    private static BufferedReader fixture(String name) throws Exception {
        return new BufferedReader(new InputStreamReader(InstrumentationRegistry
                .getInstrumentation().getContext().getAssets().open(name),
                StandardCharsets.UTF_8));
    }

    private static final class SimpleRow {
        final int canonicalClock, canonicalFlag, battleFlag;
        final int canonicalCityFrame, battleCityFrame;
        final float canonicalCityAlpha, battleCityAlpha;
        final int battleAb01Frame, battleAb02Frame, battlePumpFrame;
        final double canonicalRandom, battleRandom;
        SimpleRow(String[] c) {
            canonicalClock = Integer.parseInt(c[2]);
            canonicalFlag = Integer.parseInt(c[3]);
            battleFlag = Integer.parseInt(c[4]);
            canonicalCityFrame = Integer.parseInt(c[5]);
            canonicalCityAlpha = Float.parseFloat(c[6]);
            battleCityFrame = Integer.parseInt(c[7]);
            battleCityAlpha = Float.parseFloat(c[8]);
            battleAb01Frame = Integer.parseInt(c[9]);
            battleAb02Frame = Integer.parseInt(c[10]);
            battlePumpFrame = Integer.parseInt(c[11]);
            canonicalRandom = Double.parseDouble(c[12]);
            battleRandom = Double.parseDouble(c[13]);
        }
    }

    private static final class HandoffRow {
        final int canonicalFrame, battleFrame;
        final float canonicalAlpha, battleAlpha;
        HandoffRow(String[] c) {
            canonicalFrame = Integer.parseInt(c[3]);
            canonicalAlpha = Float.parseFloat(c[4]);
            battleFrame = Integer.parseInt(c[5]);
            battleAlpha = Float.parseFloat(c[6]);
        }
    }

    private static void installGame(WorldDominationGame app, GameState game) throws Exception {
        field(WorldDominationGame.class, "game").set(app, game);
        cityRenderer(app).reset(game);
        ((SourceBattleRecordClock) field(WorldDominationGame.class,
                "battleRecordClock").get(app)).reset();
    }

    private static void research(GameState game, String technology) {
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology(technology));
        assertTrue(game.acceptSelectedAction());
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static CityShrinkSequence canonicalCity(BattlefieldCityRenderer renderer,
                                                    int address) throws Exception {
        Object state = field(BattlefieldCityRenderer.class, "canonicalState").get(renderer);
        return ((CityShrinkSequence[]) field(state.getClass(), "citySequences")
                .get(state))[address];
    }

    private static FlashActionBuildingAnimation actionBuilding(WorldDominationGame app,
                                                               int player, int slot)
            throws Exception {
        for (FlashActionBuildingAnimation animation : actionBuildings(app))
            if (animation.playerId == player && animation.slot == slot) return animation;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<FlashActionBuildingAnimation> actionBuildings(WorldDominationGame app)
            throws Exception {
        return (List<FlashActionBuildingAnimation>) field(WorldDominationGame.class,
                "actionBuildingAnimations").get(app);
    }

    private static BattlefieldCityRenderer cityRenderer(WorldDominationGame app)
            throws Exception {
        return (BattlefieldCityRenderer) field(WorldDominationGame.class,
                "cityRenderer").get(app);
    }

    private static BattlefieldFlagRenderer flagRenderer(WorldDominationGame app)
            throws Exception {
        return (BattlefieldFlagRenderer) field(WorldDominationGame.class,
                "flagRenderer").get(app);
    }

    private static TurnPlaybackSequence playback(WorldDominationGame app) throws Exception {
        return (TurnPlaybackSequence) field(WorldDominationGame.class,
                "turnPlaybackSequence").get(app);
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
