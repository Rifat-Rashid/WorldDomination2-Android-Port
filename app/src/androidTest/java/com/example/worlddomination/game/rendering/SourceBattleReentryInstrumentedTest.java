package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Two UI-reachable turns must each construct a fresh source battle parent. */
@RunWith(AndroidJUnit4.class)
public final class SourceBattleReentryInstrumentedTest {
    // Fresh, isolated four-opponent RealTest playback after a soldier2 battle,
    // then human religion (Mass Media) + Resources. Tick zero is post-confirm.
    // Evidence: ignored analysis/depth-oracle/two-consecutive-battles-golden.json.
    private static final int[] SECOND_RNG_TICKS = {0, 1, 60, 94, 98, 116, 154, 192, 200, 243, 261};
    private static final double[] SECOND_RNG_NEXT = {
            0.007184567252270457, 0.007184567252270457,
            0.3691301471700257, 0.6048482375805311,
            0.34444960733861085, 0.34444960733861085,
            0.13015769442868408, 0.9665192604669938,
            0.9665192604669938, 0.47337040276011155,
            0.47337040276011155
    };

    @Test public void secondLegalBattleStartsWithFreshActionBuildingClipsAndSourceRng()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = prepareThirdTurn();
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals(3, game.getTurn());

            BattlefieldDisplayState display = (BattlefieldDisplayState)
                    field(WorldDominationGame.class, "battlefieldDisplay").get(app);
            TurnPlaybackSequence sequence = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            @SuppressWarnings("unchecked")
            List<FlashActionBuildingAnimation> animations =
                    (List<FlashActionBuildingAnimation>) field(WorldDominationGame.class,
                            "actionBuildingAnimations").get(app);
            List<FlashActionBuildingAnimation> firstOwners = new ArrayList<>(animations);
            FlashActionBuildingAnimation firstReligion = owner(display, 4, 2);
            assertNotNull("first source battle shows player 4 religion", firstReligion);
            assertEquals("religion", firstReligion.frameName);
            assertEquals("bhu", firstReligion.religionCode);
            assertNotNull("first player 4 religion flame",
                    flames(app)[4 * 2 + 1]);

            int firstEndTick = -1;
            for (int tick = 1; tick <= 536; tick++) {
                advance(app);
                if (tick == 525) {
                    assertEquals("first source cleanup handoff",
                            TurnPlaybackSequence.Phase.CLEANUP_HANDOFF,
                            sequence.getPhase());
                }
                if (sequence.getPhase() == TurnPlaybackSequence.Phase.IDLE) {
                    firstEndTick = tick;
                    break;
                }
            }
            assertEquals("first source battle ends at tick 526", 526, firstEndTick);
            assertEquals(0, game.getQueuedActionCount());

            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue("human source religion action accepted", game.acceptSelectedAction());
            resources(game);
            assertEquals(2, game.getQueuedActionCount());
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals(4, game.getTurn());
            assertEquals("source all-turn action-building records", 10,
                    game.getLastTurnActionBuildings().size());

            // The first parent is discarded at the new confirm. Even the same
            // physical slot/frame gets a new owner rather than its old phase.
            for (FlashActionBuildingAnimation old : firstOwners) {
                for (FlashActionBuildingAnimation current : animations) {
                    assertNotSame("old battle action clip reused", old, current);
                }
            }
            FlashActionBuildingAnimation secondPlayerFour = owner(display, 4, 2);
            assertNotNull(secondPlayerFour);
            assertEquals("resources", secondPlayerFour.frameName);
            assertNotSame(firstReligion, secondPlayerFour);
            assertNull("old player 4 flame discarded", flames(app)[4 * 2 + 1]);
            assertEquals("unchanged resource slot also has a fresh owner", "resources",
                    owner(display, 0, 1).frameName);
            assertFalse(firstOwners.contains(owner(display, 0, 1)));

            ReferenceRandom secondRandom = (ReferenceRandom)
                    field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            assertNotNull("second private source presentation RNG", secondRandom);
            SourceBattleRecordClock recordClock = (SourceBattleRecordClock)
                    field(WorldDominationGame.class, "battleRecordClock").get(app);
            SourceBattleTicker ticker = (SourceBattleTicker)
                    field(WorldDominationGame.class, "battleTicker").get(app);
            assertSecondRng(0, secondRandom);
            int secondEndTick = -1;
            for (int tick = 1; tick <= 270; tick++) {
                advance(app);
                assertSecondRng(tick, secondRandom);
                if (tick == 192) {
                    assertEquals("source label gate before first building", 0,
                            recordClock.getActivatedRecordCount());
                } else if (tick >= 193 && tick <= 199) {
                    assertEquals("source building start number at tick " + tick,
                            tick - 192, recordClock.getActivatedRecordCount());
                } else if (tick == 200) {
                    assertFalse("source ticker stops after seventh building", ticker.isRunning());
                }
                if (sequence.getPhase() == TurnPlaybackSequence.Phase.IDLE) {
                    secondEndTick = tick;
                    break;
                }
            }
            assertEquals("second source battle ends at tick 261", 261, secondEndTick);
        });
    }

    private static void assertSecondRng(int tick, ReferenceRandom random) {
        for (int index = 0; index < SECOND_RNG_TICKS.length; index++) {
            if (SECOND_RNG_TICKS[index] == tick) {
                assertEquals("source second battle next random tick " + tick,
                        SECOND_RNG_NEXT[index], random.copy().nextDouble(), 0d);
                return;
            }
        }
    }

    private static FlashActionBuildingAnimation owner(BattlefieldDisplayState display,
                                                       int player, int slot) {
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                display.actionBuilding(player, slot);
        assertNotNull("authored action-building slot", entry);
        Object owner = entry.getValue().owner;
        assertTrue("source slot owner type", owner == null
                || owner instanceof FlashActionBuildingAnimation);
        return (FlashActionBuildingAnimation) owner;
    }

    private static Object[] flames(WorldDominationGame app) throws Exception {
        SourceBattleActionFrames frames = (SourceBattleActionFrames)
                field(WorldDominationGame.class, "battleActionFrames").get(app);
        return (Object[]) field(SourceBattleActionFrames.class, "flames").get(frames);
    }

    private static GameState prepareThirdTurn() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        for (int leader = 0; leader < 4; leader++) assertTrue(setup.toggleOpponent(leader));
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
        return game;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void advance(WorldDominationGame app) throws Exception {
        invoke(app, "fixedUpdate", float.class, 1f / 24f);
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        invoke(app, name, null, null);
    }

    private static void invoke(WorldDominationGame app, String name,
                               Class<?> type, Object value) throws Exception {
        Method method = type == null ? WorldDominationGame.class.getDeclaredMethod(name)
                : WorldDominationGame.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        if (type == null) method.invoke(app);
        else method.invoke(app, value);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
