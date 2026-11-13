package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real World playback of the UI-reachable seed-zero soldier2 source turn. */
@RunWith(AndroidJUnit4.class)
public final class ApacheTurnPlaybackInstrumentedTest {
    private static final int LAST_TICK = 525;
    private static final int[] NATIVE_BY_SOURCE_ROW = {3, 2, 1, 0};

    @Test public void threeTurnApacheAndMarinesMatchSourceThroughEndBattle()
            throws Exception {
        Observation[][] source = readSource();
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = prepareThirdTurn();
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals(3, game.getTurn());
            double simulationNext = game.createPresentationRandomCopy().nextDouble();
            GameState.CombatEvent attack = null;
            for (GameState.CombatEvent event
                    : game.getLastTurnCombatEventsInSourcePresentationOrder()) {
                if (event.attackerPlayerId == 0 && "soldier2".equals(event.weaponName)) {
                    attack = event;
                }
            }
            assertNotNull("third-turn soldier2 attack", attack);
            assertEquals(3, attack.battleStartNum);

            @SuppressWarnings("unchecked")
            List<FlashApacheMarineAnimation> groups = (List<FlashApacheMarineAnimation>)
                    field(WorldDominationGame.class, "apacheMarineAnimations").get(app);
            assertEquals("one Apache and three marines", 1, groups.size());
            FlashApacheMarineAnimation animation = groups.get(0);
            assertEquals(4, animation.getActorCount());
            SourceStoppedClipQueue queue = (SourceStoppedClipQueue)
                    field(WorldDominationGame.class, "sourceStoppedClips").get(app);
            SourceBattleTicker ticker = (SourceBattleTicker)
                    field(WorldDominationGame.class, "battleTicker").get(app);
            TurnPlaybackSequence sequence = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            SourceBattleUnitPlayback units = (SourceBattleUnitPlayback)
                    field(WorldDominationGame.class, "sourceUnitPlayback").get(app);
            assertEquals("one source-ordered battle cohort", 1, units.size());

            Field marineTracksField = field(FlashApacheMarineAnimation.class, "marines");
            Object[] marineTracks = (Object[]) marineTracksField.get(animation);
            Field shotCountField = field(marineTracks[0].getClass(), "shotCount");
            assertTick(0, source[0], animation, marineTracks, shotCountField, queue);
            for (int tick = 1; tick <= LAST_TICK; tick++) {
                advance(app);
                assertTick(tick, source[tick], animation, marineTracks, shotCountField,
                        queue);
                if (tick == 423 || tick == 424 || tick == 425 || tick == 431
                        || tick == 432 || tick == 462 || tick == 463) {
                    assertEquals("source unit visitation tick " + tick,
                            tick >= 463, animation.isApacheRemoved());
                }
                if (tick == 464) {
                    assertFalse("empty active array stops source ticker", ticker.isRunning());
                    assertEquals(4, queue.size());
                    assertEquals(TurnPlaybackSequence.Phase.RESET_BARRIER,
                            sequence.getPhase());
                }
                if (tick == 524) {
                    assertEquals(TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                            sequence.getPhase());
                    assertEquals("allUnitsDone detaches four retained clips", 4,
                            queue.size());
                }
            }
            assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
            assertFalse(sequence.isBlockingInput());
            assertFalse(ticker.isRunning());
            assertEquals("EndBattle clears deletion list", 0, queue.size());
            assertTrue("turn-end releases render group", groups.isEmpty());
            assertEquals("presentation callbacks must not consume gameplay randomness",
                    simulationNext, game.createPresentationRandomCopy().nextDouble(), 0d);
        });
    }

    private static GameState prepareThirdTurn() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier2"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 100));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier2"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void assertTick(int tick, Observation[] source,
                                   FlashApacheMarineAnimation animation,
                                   Object[] marineTracks, Field shotCountField,
                                   SourceStoppedClipQueue queue) throws Exception {
        int queued = 0;
        FlashApacheMarineAnimation.Actor[] queuedBySourceIndex =
                new FlashApacheMarineAnimation.Actor[4];
        for (int sourceIndex = 0; sourceIndex < 4; sourceIndex++) {
            Observation expected = source[sourceIndex];
            int nativeIndex = NATIVE_BY_SOURCE_ROW[sourceIndex];
            FlashApacheMarineAnimation.Actor actual = animation.getActor(nativeIndex);
            String label = "tick " + tick + " " + expected.name + " ";
            assertEquals(label + "visibility", expected.attached && expected.visible,
                    actual.visible);
            if (expected.hasPosition) {
                assertEquals(label + "X", expected.x, actual.sourceX, 0.001f);
                assertEquals(label + "Y", expected.y, actual.sourceY, 0.001f);
                assertEquals(label + "linkage", expected.linkage, actual.linkage);
                assertEquals(label + "frame", expected.frame, actual.sourceFrame + 1);
            }
            boolean removed = expected.deleteIndex >= 0;
            boolean stopped = "stop".equals(expected.action) || removed;
            if (nativeIndex == 0) {
                assertEquals(label + "Apache stopped", stopped,
                        animation.isApacheStopped());
                assertEquals(label + "Apache removed", removed,
                        animation.isApacheRemoved());
            } else {
                assertEquals(label + "marine stopped", stopped,
                        animation.isMarineStopped(nativeIndex - 1));
                assertEquals(label + "marine removed", removed,
                        animation.isMarineRemoved(nativeIndex - 1));
                assertEquals(label + "shots", expected.shotCount,
                        shotCountField.getInt(marineTracks[nativeIndex - 1]));
            }
            if (removed) queuedBySourceIndex[expected.deleteIndex] = actual;
            if (removed) queued++;
        }
        assertEquals("source deletion count tick " + tick,
                tick == LAST_TICK ? 0 : queued, queue.size());
        if (tick != LAST_TICK) {
            @SuppressWarnings("unchecked")
            List<SourceStoppedClipQueue.Clip> actualQueue =
                    (List<SourceStoppedClipQueue.Clip>)
                            field(SourceStoppedClipQueue.class, "clips").get(queue);
            for (int index = 0; index < queued; index++) {
                assertSame("source deletion order tick " + tick + " index " + index,
                        queuedBySourceIndex[index], actualQueue.get(index));
            }
        }
    }

    private static Observation[][] readSource() throws Exception {
        Observation[][] frames = new Observation[LAST_TICK + 1][4];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source/apache_marine_seed0_three_turns.csv"),
                StandardCharsets.UTF_8))) {
            assertTrue(reader.readLine().startsWith("tick,unitName,mcID,action,"));
            String line;
            int rows = 0;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals("source CSV width", 31, cells.length);
                int tick = Integer.parseInt(cells[0]);
                assertTrue("source tick", tick >= 0 && tick <= LAST_TICK);
                int sourceIndex = Integer.parseInt(cells[1].substring(4)) - 1;
                assertTrue("source unit", sourceIndex >= 0 && sourceIndex < 4);
                assertNull("duplicate tick/unit", frames[tick][sourceIndex]);
                frames[tick][sourceIndex] = new Observation(cells);
                rows++;
            }
            assertEquals("526 ticks of four source wrappers", 526 * 4, rows);
        }
        for (int tick = 0; tick <= LAST_TICK; tick++) {
            for (int actor = 0; actor < 4; actor++) {
                assertNotNull("missing source tick " + tick + " unit " + actor,
                        frames[tick][actor]);
            }
        }
        return frames;
    }

    private static final class Observation {
        final String name;
        final String action;
        final String linkage;
        final boolean attached;
        final boolean visible;
        final boolean hasPosition;
        final float x;
        final float y;
        final int shotCount;
        final int frame;
        final int deleteIndex;

        Observation(String[] cells) {
            name = cells[1];
            action = cells[3];
            hasPosition = !cells[6].isEmpty();
            x = hasPosition ? Float.parseFloat(cells[6]) : 0f;
            y = hasPosition ? Float.parseFloat(cells[7]) : 0f;
            shotCount = Integer.parseInt(cells[8]);
            attached = Boolean.parseBoolean(cells[9]);
            visible = Boolean.parseBoolean(cells[10]);
            frame = Integer.parseInt(cells[11]);
            linkage = cells[12];
            deleteIndex = cells[5].isEmpty() ? -1 : Integer.parseInt(cells[5]);
        }
    }

    private static void advance(WorldDominationGame app) throws Exception {
        invoke(app, "fixedUpdate", float.class, 1f / 24f);
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        invoke(app, name, null, null);
    }

    private static void invoke(WorldDominationGame app, String name,
                               Class<?> parameterType, Object value) throws Exception {
        Method method = parameterType == null
                ? WorldDominationGame.class.getDeclaredMethod(name)
                : WorldDominationGame.class.getDeclaredMethod(name, parameterType);
        method.setAccessible(true);
        if (parameterType == null) method.invoke(app);
        else method.invoke(app, value);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
