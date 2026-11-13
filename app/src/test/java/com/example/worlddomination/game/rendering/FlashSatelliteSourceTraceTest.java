package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** UI-reachable seed-zero SetupMenu turn 28, regenerated from the read-only Python host. */
public final class FlashSatelliteSourceTraceTest {
    private static final int LAST_TICK = 533;
    private static final float SOURCE_TICK_SECONDS = 1f / 24f;

    @Test public void realTurnSatelliteMatchesEverySourceTickAndDelayedCleanup() throws Exception {
        Observation[] source = readSource();
        GameSetup setup = new GameSetup(0L);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);

        int rejectedProposals = 0;
        String[] research = {"rocket2", "rocket3", "rocket4", "warhead2",
                "warhead3", "warhead4", "bioload2", "bioload3", "bioload4",
                "soldier2", "soldier3", "soldier4", "sub", "ship", "space",
                "specialLoad"};
        for (String technology : research) {
            rejectedProposals += rejectPendingProposals(game);
            game.selectAction(GameState.Action.RESEARCH);
            assertTrue("research " + technology, game.selectResearchTechnology(technology));
            assertTrue(game.acceptSelectedAction());
            resources(game);
            game.confirmTurn();
        }
        for (int turn = 17; turn <= 26; turn++) {
            rejectedProposals += rejectPendingProposals(game);
            game.selectAction(GameState.Action.PRODUCTION);
            assertTrue("special production turn " + turn,
                    game.configureProduction("special", 100));
            assertTrue(game.acceptSelectedAction());
            resources(game);
            game.confirmTurn();
        }
        rejectedProposals += rejectPendingProposals(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue("real stock supports preparation", game.configurePreparedCarrier("space"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        assertEquals(27, game.getTurn());
        rejectedProposals += rejectPendingProposals(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue("real stock supports special payload",
                game.configurePreparedLaunch(1, 0, "specialLoad"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        assertEquals(28, game.getTurn());
        assertEquals("source rejected two AI proposals", 2, rejectedProposals);

        GameState.CombatEvent satelliteEvent = null;
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            if (FlashBattleRoutes.supportsSatelliteStrike(event)) {
                assertEquals("one source satellite result", null, satelliteEvent);
                satelliteEvent = event;
            }
        }
        assertNotNull("normal resolved turn produces a satellite", satelliteEvent);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, satelliteEvent.type);
        assertEquals(0, satelliteEvent.attackerPlayerId);
        assertEquals(1, satelliteEvent.defenderPlayerId);
        assertEquals(0, satelliteEvent.cityIndex);
        assertEquals("space", satelliteEvent.weaponName);
        assertEquals("specialLoad", satelliteEvent.payloadName);
        assertEquals(93, satelliteEvent.casualtiesMillions);
        assertEquals("source spawn start number", 2, satelliteEvent.battleStartNum);
        FlashBattleRoutes.SatelliteStrike route = FlashBattleRoutes.createSatelliteStrike(
                satelliteEvent.attackerPlayerId, satelliteEvent.defenderPlayerId,
                satelliteEvent.cityIndex,
                game.getPlayerState(satelliteEvent.attackerPlayerId).getPlayerColor(),
                game.getPlayerState(satelliteEvent.attackerPlayerId).getReligionCode());
        assertEquals(-120f, route.launch.x, 0f);
        assertEquals(158f, route.launch.y, 0f);
        assertEquals(368f, route.orbit.x, 0f);
        assertEquals(75f, route.orbit.y, 0f);
        assertEquals(29f, route.targetCity.x, 0f);
        assertEquals(262f, route.targetCity.y, 0f);
        assertEquals("jew", route.religionCode);

        List<GameState.BattlePresentationRecord> records = game.getLastTurnBattleRecords();
        // Four action records expand into six wrappers: satellite, three
        // Chinook/tank parts, and two invisible building placeholders.
        assertEquals("source action records before wrapper expansion", 4, records.size());
        boolean pendingBuilding = false;
        for (GameState.BattlePresentationRecord record : records) {
            pendingBuilding |= record.pendingBuilding;
        }
        SourceBattleRecordClock clock = new SourceBattleRecordClock();
        clock.begin(records.size(), pendingBuilding);
        int delay = clock.delayForRecord(satelliteEvent.battleStartNum);
        assertEquals(1, delay);
        FlashSatelliteAnimation satellite = new FlashSatelliteAnimation(route, delay);
        SourceStoppedClipQueue deletion = new SourceStoppedClipQueue();
        SourceBattleTicker ticker = new SourceBattleTicker();
        ticker.begin();
        TurnPlaybackSequence sequence = new TurnPlaybackSequence();
        List<GameState.Action> actionLabels = new ArrayList<>();
        for (GameState.ActionBuildingEvent building : game.getLastTurnActionBuildings()) {
            actionLabels.add(building.action);
        }
        sequence.begin(actionLabels);

        int impactCount = 0;
        int flightCueCount = 0;
        int laserCueCount = 0;
        assertFrame(0, source[0], satellite, ticker, sequence, deletion);
        for (int tick = 1; tick <= LAST_TICK; tick++) {
            TurnPlaybackSequence.Phase previous = sequence.getPhase();
            sequence.advance(SOURCE_TICK_SECONDS);
            SourceBattleTicker.FrameDecision decision = SourceBattleTicker.FrameDecision.SUPPRESS;
            if (ticker.isRunning()) {
                // Concurrent real Chinook/tank and placeholders are external to this
                // focused satellite test. Source activeCount supplies their lifetime.
                decision = ticker.advanceFrame(source[tick - 1].activeCount > 0, false);
                if (decision == SourceBattleTicker.FrameDecision.CLEANUP) {
                    sequence.completeBattlePlayback();
                }
            }
            satellite.presentWaitingFrame();
            if (!sequence.isWaitingForActionLabels()
                    && previous != TurnPlaybackSequence.Phase.ACTION_LABELS
                    && !sequence.isCleaningUp()
                    && sequence.isReadyForBattle()
                    && decision == SourceBattleTicker.FrameDecision.ADVANCE) {
                clock.tick();
                satellite.tick();
                assertEquals("stop source frame " + tick, tick == 424,
                        satellite.didStopSourceFrame());
                satellite.registerStoppedClip(deletion);
                if (tick == 425) {
                    assertEquals("satellite queues its own stopped wrapper", 1,
                            deletion.size());
                }
            }
            // The other three stopped wrappers are measured by the source fixture.
            while (deletion.size() < source[tick].deleteCount) deletion.add(() -> { });
            if (satellite.takeImpact()) impactCount++;
            if (satellite.takeFlightCue()) flightCueCount++;
            if (satellite.takeLaserCue()) laserCueCount++;
            if (previous == TurnPlaybackSequence.Phase.RESET_BARRIER
                    && sequence.getPhase() == TurnPlaybackSequence.Phase.CLEANUP_HANDOFF) {
                assertEquals(532, tick);
                assertEquals(4, deletion.removeAtAllUnitsDone());
            }
            assertFrame(tick, source[tick], satellite, ticker, sequence, deletion);
        }
        assertEquals(1, impactCount);
        assertEquals(1, flightCueCount);
        assertEquals(1, laserCueCount);
        assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
        assertEquals(4, deletion.size());
    }

    private static int rejectPendingProposals(GameState game) {
        int rejected = 0;
        GameState.DiplomacyProposal proposal;
        while ((proposal = game.getPendingHumanDiplomacyProposal()) != null) {
            if (game.getTurn() == 12) {
                assertEquals(1, proposal.proposerId);
                assertEquals(5, proposal.optionIndex);
            } else if (game.getTurn() == 26) {
                assertEquals(2, proposal.proposerId);
                assertEquals(1, proposal.optionIndex);
            } else {
                throw new AssertionError("unexpected diplomacy turn " + game.getTurn());
            }
            assertTrue(game.respondToDiplomacyProposal(false));
            rejected++;
        }
        return rejected;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void assertFrame(int tick, Observation expected,
                                    FlashSatelliteAnimation satellite,
                                    SourceBattleTicker ticker,
                                    TurnPlaybackSequence sequence,
                                    SourceStoppedClipQueue deletion) {
        assertEquals("source X tick " + tick, expected.x, satellite.getSourceX(), 0.0001f);
        assertEquals("source Y tick " + tick, expected.y, satellite.getSourceY(), 0.0001f);
        assertEquals("display X tick " + tick, expected.displayX,
                satellite.getDisplaySourceX(), 0.0001f);
        assertEquals("display Y tick " + tick, expected.displayY,
                satellite.getDisplaySourceY(), 0.0001f);
        assertEquals("nested mc frame tick " + tick, expected.nestedFrame,
                satellite.getSourceClipFrameNumber());
        assertEquals("wrapper removal tick " + tick, !expected.active,
                satellite.isFinished());
        assertEquals("attached body tick " + tick, expected.attached,
                satellite.isClipVisible());
        assertEquals("ticker tick " + tick, expected.tickerRunning, ticker.isRunning());
        assertEquals("deletion queue tick " + tick, expected.deleteCount, deletion.size());
        assertEquals("laser alpha tick " + tick, expected.laserAlpha / 100f,
                satellite.getLaserAlpha(), 0.0001f);
        assertEquals("firing tick " + tick,
                tick >= 396 && tick < 424, satellite.isFiring());
        if (tick == LAST_TICK) assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
    }

    private static Observation[] readSource() throws Exception {
        Observation[] frames = new Observation[LAST_TICK + 1];
        InputStream stream = FlashSatelliteSourceTraceTest.class.getResourceAsStream(
                "/source/satellite-seed0-turn28.csv");
        assertNotNull("source satellite fixture", stream);
        try (BufferedReader reader = new BufferedReader(
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
                assertEquals("one satellite ordinal", "0", cells[1]);
                assertEquals("source satellite unit", "satelliteunit1", cells[3]);
                assertEquals("source start number", "2.0", cells[4]);
                assertEquals("source body clip", "satellite", cells[22]);
                assertEquals("source body religion frame", "5", cells[21]);
                assertEquals("duplicate source tick " + tick, null, frames[tick]);
                frames[tick] = new Observation(cells);
            }
        }
        for (int tick = 0; tick <= LAST_TICK; tick++) {
            assertNotNull("missing source tick " + tick, frames[tick]);
        }
        assertEquals(0, frames[424].strokeCount);
        assertEquals(8, frames[423].strokeCount);
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
        final int strokeCount;
        final int deleteCount;
        final int activeCount;

        Observation(String[] cells) {
            x = Float.parseFloat(cells[8]);
            y = Float.parseFloat(cells[9]);
            displayX = Float.parseFloat(cells[23]);
            displayY = Float.parseFloat(cells[24]);
            laserAlpha = cells[11].isEmpty() ? 0f : Float.parseFloat(cells[11]);
            active = !cells[12].isEmpty();
            deleteCount = Integer.parseInt(cells[15]);
            activeCount = Integer.parseInt(cells[14]);
            tickerRunning = Boolean.parseBoolean(cells[16]);
            attached = Boolean.parseBoolean(cells[19]);
            nestedFrame = Integer.parseInt(cells[25]);
            strokeCount = Integer.parseInt(cells[26]);
        }
    }
}
