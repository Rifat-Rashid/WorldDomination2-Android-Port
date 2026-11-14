package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Source playback observations for SetupMenu(0), normal/btn2, two or four
 * leaders, one soldier1 attack plus resources, and btn46. Fixture tick 0
 * precedes the first host clock step. Actors 1..3 follow the physical source
 * reverse-spawn order; source clip frames are zero-based in each fixture.
 */
public final class FlashSoldierOneSourceTraceTest {
    private static final float SOURCE_TICK_SECONDS = 1f / 24f;
    private static final int FINAL_SOURCE_TICK = 442;
    private static final String TWO_OPPONENT_FIXTURE =
            "/source/infantry-seed0-resources-lifecycle.csv";

    @Test public void allFourActorsMatchEverySourceFrameAndLifecycle() throws Exception {
        verifySourceCase(1, 0, 2, FINAL_SOURCE_TICK, TWO_OPPONENT_FIXTURE);
    }

    @Test public void fourOpponentNorthEastLandingMatchesEverySourceFrame() throws Exception {
        verifySourceCase(2, 1, 4, 476,
                "/source/infantry-seed0-4op-target2-city1.csv");
    }

    @Test public void fourOpponentSouthWestLandingMatchesEverySourceFrame() throws Exception {
        verifySourceCase(3, 3, 4, 518,
                "/source/infantry-seed0-4op-target3-city3.csv");
    }

    @Test public void fourOpponentSouthEastLandingMatchesEverySourceFrame() throws Exception {
        verifySourceCase(4, 1, 4, 596,
                "/source/infantry-seed0-4op-target4-city1.csv");
    }

    private static void verifySourceCase(int target, int city, int opponentCount,
                                         int finalTick, String resource) throws Exception {
        Harness harness = new Harness(target, city, opponentCount);
        Observation[][] fixture = loadFixture(resource, finalTick);
        assertActors(harness.animation, fixture[0], 0);
        for (int tick = 1; tick <= finalTick; tick++) {
            harness.tick();
            assertEquals(tick, harness.globalTick);
            assertActors(harness.animation, fixture[tick], tick);
            assertEquals("source active wrappers at tick " + tick,
                    hasActiveWrapper(fixture[tick]), !harness.animation.isFinished());
            boolean removedInfantry = false;
            for (int actor = 1; actor < 4; actor++) {
                removedInfantry |= fixture[tick - 1][actor].active
                        && !fixture[tick][actor].active;
            }
            assertEquals("source stopped-infantry early return at tick " + tick,
                    removedInfantry, harness.earlyReturn);
            if (tick == 93) assertEquals(0,
                    harness.interfaceFrames.getSelectedStartFrame(4010));
            if (tick == 94) assertTrue(
                    harness.interfaceFrames.getSelectedStartFrame(4010) > 0);
            if (tick == 98) assertTrue(
                    harness.interfaceFrames.getSelectedStartFrame(4028) > 0);
        }
        assertEquals(TurnPlaybackSequence.Phase.IDLE, harness.sequence.getPhase());
        assertEquals(finalTick, harness.interfaceFrames.getElapsedTicks());
        assertEquals(finalTick - 61, harness.cleanupTick);
        assertEquals(finalTick - 1, harness.handoffTick);
        assertEquals(finalTick, harness.idleTick);
        assertEquals(3, harness.gunCueCount);
    }

    @Test public void stoppedClipCanBeClearedAfterFirstRemovalWithoutClearingActivePeers()
            throws Exception {
        Harness harness = new Harness();
        Observation[][] fixture = loadFixture(TWO_OPPONENT_FIXTURE, FINAL_SOURCE_TICK);
        harness.runUntil(364);
        assertTrue(harness.earlyReturn);
        assertTrue(harness.animation.getActor(3).visible);
        assertTrue(harness.animation.getActor(1).visible);
        assertTrue(harness.animation.getActor(2).visible);
        float firstX = harness.animation.getActor(3).sourceX;
        float firstY = harness.animation.getActor(3).sourceY;
        int firstFrame = harness.animation.getActor(3).sourceFrame;
        harness.animation.clearStoppedActors();
        assertFalse(harness.animation.getActor(3).visible);
        assertTrue(harness.animation.getActor(1).visible);
        assertTrue(harness.animation.getActor(2).visible);
        assertEquals(firstX, harness.animation.getActor(3).sourceX, 0f);
        assertEquals(firstY, harness.animation.getActor(3).sourceY, 0f);
        assertEquals(firstFrame, harness.animation.getActor(3).sourceFrame);
        // The source fixture still has this stopped clip attached until handoff.
        assertTrue(fixture[364][3].visible);
        assertFalse(fixture[364][3].active);
    }

    @Test public void allFinishedActorsFreezeUntilSourceCleanupHandoff() throws Exception {
        Harness harness = new Harness();
        Observation[][] fixture = loadFixture(TWO_OPPONENT_FIXTURE, FINAL_SOURCE_TICK);
        harness.runUntil(380);
        assertTrue(harness.animation.isFinished());
        FlashSoldierOneAnimation.Actor[] actors = new FlashSoldierOneAnimation.Actor[4];
        int[] frames = new int[4];
        float[] x = new float[4];
        float[] y = new float[4];
        for (int actor = 0; actor < 4; actor++) {
            actors[actor] = harness.animation.getActor(actor);
            frames[actor] = actors[actor].sourceFrame;
            x[actor] = actors[actor].sourceX;
            y[actor] = actors[actor].sourceY;
        }
        ReferenceRandom expected = harness.presentationRandom.copy();
        int cues = harness.gunCueCount;
        harness.runUntil(440);
        for (int actor = 0; actor < 4; actor++) {
            assertTrue(actors[actor].visible);
            assertEquals(frames[actor], actors[actor].sourceFrame);
            assertEquals(x[actor], actors[actor].sourceX, 0f);
            assertEquals(y[actor], actors[actor].sourceY, 0f);
            assertTrue(fixture[440][actor].visible);
        }
        assertEquals(expected.nextDouble(), harness.presentationRandom.nextDouble(), 0);
        assertEquals(cues, harness.gunCueCount);
        assertFalse(harness.animation.takeAudioCue());
        harness.tick();
        assertEquals(441, harness.globalTick);
        for (FlashSoldierOneAnimation.Actor actor : actors) assertFalse(actor.visible);
    }

    private static final class Harness {
        final GameState game;
        final ReferenceRandom presentationRandom;
        final FlashSoldierOneAnimation animation;
        final SourceBattleInterfaceFrames interfaceFrames = new SourceBattleInterfaceFrames();
        final TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        final SourceBattleTicker ticker = new SourceBattleTicker();
        final SourceBattleRecordClock recordClock = new SourceBattleRecordClock();
        int globalTick;
        int cleanupTick;
        int handoffTick;
        int idleTick;
        int gunCueCount;
        boolean earlyReturn;

        Harness() throws Exception {
            this(1, 0, 2);
        }

        Harness(int target, int city, int opponentCount) throws Exception {
            GameSetup setup = new GameSetup(0L);
            assertEquals(1, setup.getDifficulty());
            assertEquals(5, setup.getPlayerColor());
            assertEquals(4, setup.getStartingBuilding());
            for (int opponent = 0; opponent < opponentCount; opponent++) {
                assertTrue(setup.toggleOpponent(opponent));
            }
            game = new GameState(setup);
            game.selectAction(GameState.Action.ATTACK);
            assertTrue(game.configureAttack(target, city, "soldier1"));
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            game.confirmTurn();

            presentationRandom = game.createBattlePresentationRandomCopy();
            assertEquals(opponentCount == 2 ? 0.5756510141648885 : 0.8159130965336595,
                    presentationRandom.copy().nextDouble(), 0);
            Path anchors = Paths.get("src/main/assets/original/runtime/battle_anchors.json");
            if (!Files.isRegularFile(anchors)) anchors = Paths.get("app").resolve(anchors);
            Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(
                    new String(Files.readAllBytes(anchors), StandardCharsets.UTF_8));
            FlashBattleRoutes.SoldierOneAttack route = FlashBattleRoutes.createSoldierOneAttack(
                    0, target, city, points, presentationRandom);
            assertEquals(opponentCount == 2 ? 0.47653099200938076 : 0.9100160146990397,
                    presentationRandom.copy().nextDouble(), 0);
            List<GameState.BattlePresentationRecord> records = game.getLastTurnBattleRecords();
            boolean pendingBuilding = false;
            for (GameState.BattlePresentationRecord record : records) {
                pendingBuilding |= record.pendingBuilding;
            }
            recordClock.begin(records.size(), pendingBuilding);
            int soldierStartNum = 0;
            for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
                if (event.attackerPlayerId == 0 && event.defenderPlayerId == target
                        && event.cityIndex == city && "soldier1".equals(event.weaponName)
                        && FlashBattleRoutes.supports(event)) {
                    assertEquals("Only one human soldier1 battle record is expected",
                            0, soldierStartNum);
                    soldierStartNum = event.battleStartNum;
                }
            }
            assertEquals("Source human soldier1 start number",
                    opponentCount == 2 ? 4 : 6, soldierStartNum);
            int recordDelay = recordClock.delayForRecord(soldierStartNum);
            assertEquals(opponentCount == 2 ? 3 : 5, recordDelay);
            animation = new FlashSoldierOneAnimation(route, presentationRandom,
                    OriginalAudio.Cue.SOLDIER_GUN,
                    recordDelay, setup.getPlayerColor());

            List<GameState.Action> actions = new ArrayList<>();
            for (GameState.ActionBuildingEvent building : game.getLastTurnActionBuildings()) {
                actions.add(building.action);
            }
            sequence.begin(actions);
            interfaceFrames.begin();
            ticker.begin();
        }

        void runUntil(int targetTick) {
            while (globalTick < targetTick) tick();
        }

        void tick() {
            globalTick++;
            earlyReturn = false;
            TurnPlaybackSequence.Phase previousPhase = sequence.getPhase();
            interfaceFrames.tick(presentationRandom);
            sequence.advance(SOURCE_TICK_SECONDS);
            SourceBattleTicker.FrameDecision decision = SourceBattleTicker.FrameDecision.ADVANCE;
            if (ticker.isRunning()) {
                decision = ticker.advanceFrame(!animation.isFinished(), false);
                if (decision == SourceBattleTicker.FrameDecision.CLEANUP) {
                    cleanupTick = globalTick;
                    sequence.completeBattlePlayback();
                }
            }
            animation.presentWaitingFrame();
            if (!sequence.isWaitingForActionLabels()
                    && previousPhase != TurnPlaybackSequence.Phase.ACTION_LABELS
                    && !sequence.isCleaningUp() && sequence.isReadyForBattle()
                    && decision == SourceBattleTicker.FrameDecision.ADVANCE) {
                recordClock.tick();
                earlyReturn = animation.tickSourceFrame();
            }
            if (previousPhase == TurnPlaybackSequence.Phase.RESET_BARRIER
                    && sequence.getPhase() == TurnPlaybackSequence.Phase.CLEANUP_HANDOFF) {
                handoffTick = globalTick;
                animation.clearStoppedActors();
            }
            if (previousPhase == TurnPlaybackSequence.Phase.CLEANUP_HANDOFF
                    && sequence.getPhase() == TurnPlaybackSequence.Phase.IDLE) {
                idleTick = globalTick;
                recordClock.finishBattle();
            }
            while (animation.takeAudioCue()) gunCueCount++;
        }
    }

    private static void assertActors(FlashSoldierOneAnimation animation,
                                     Observation[] expected, int tick) {
        assertEquals(4, animation.getActorCount());
        for (int actor = 0; actor < expected.length; actor++) {
            Observation row = expected[actor];
            assertNotNull("missing actor " + actor + " tick " + tick, row);
            FlashSoldierOneAnimation.Actor actual = animation.getActor(actor);
            assertEquals("visible actor " + actor + " tick " + tick,
                    row.visible, actual.visible);
            // Hidden pre-activation clips autoplay in Flash. Their native actor
            // becomes observable when activation selects the exact source pose.
            if (row.visible || row.hasPosition) {
                assertEquals("frame actor " + actor + " tick " + tick,
                        row.frame, actual.sourceFrame);
                assertEquals("linkage actor " + actor + " tick " + tick,
                        row.linkage, actual.linkage);
            }
            if (row.hasPosition) {
                // The compact captures record Unit.curX/Y. The new depth
                // oracle separately records MC._x/y: at construction only,
                // the boat's logical route start and painted origin differ.
                assertEquals("x actor " + actor + " tick " + tick,
                        row.x, actor == 0 ? animation.getBoatUnitSourceX() : actual.sourceX,
                        0.0001f);
                assertEquals("y actor " + actor + " tick " + tick,
                        row.y, actor == 0 ? animation.getBoatUnitSourceY() : actual.sourceY,
                        0.0001f);
                if (actor == 0 && tick == 0) {
                    assertEquals("initial painted boat X", 0f, actual.sourceX, 0f);
                    assertEquals("initial painted boat Y", 0f, actual.sourceY, 0f);
                }
            }
        }
    }

    private static boolean hasActiveWrapper(Observation[] observations) {
        for (Observation observation : observations) {
            if (observation.active) return true;
        }
        return false;
    }

    private static Observation[][] loadFixture(String resource, int finalTick) throws Exception {
        Observation[][] rows = new Observation[finalTick + 1][4];
        InputStream input = FlashSoldierOneSourceTraceTest.class.getResourceAsStream(
                resource);
        assertNotNull("Source infantry fixture is missing", input);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            assertEquals("tick,actor,visible,frame,linkage,sourceX,sourceY,active",
                    reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals(line, 8, cells.length);
                int tick = Integer.parseInt(cells[0]);
                int actor = Integer.parseInt(cells[1]);
                assertTrue(tick >= 0 && tick <= finalTick);
                assertTrue(actor >= 0 && actor < 4);
                assertEquals("duplicate actor " + actor + " tick " + tick,
                        null, rows[tick][actor]);
                rows[tick][actor] = new Observation(cells);
            }
        }
        for (int tick = 0; tick <= finalTick; tick++) {
            for (int actor = 0; actor < 4; actor++) {
                assertNotNull("missing fixture actor " + actor + " tick " + tick,
                        rows[tick][actor]);
            }
        }
        return rows;
    }

    private static final class Observation {
        final boolean visible;
        final int frame;
        final String linkage;
        final float x;
        final float y;
        final boolean hasPosition;
        final boolean active;

        Observation(String[] cells) {
            visible = Boolean.parseBoolean(cells[2]);
            frame = Integer.parseInt(cells[3]);
            linkage = cells[4];
            hasPosition = !cells[5].isEmpty() && !cells[6].isEmpty();
            x = hasPosition ? Float.parseFloat(cells[5]) : 0f;
            y = hasPosition ? Float.parseFloat(cells[6]) : 0f;
            active = Boolean.parseBoolean(cells[7]);
        }
    }
}
