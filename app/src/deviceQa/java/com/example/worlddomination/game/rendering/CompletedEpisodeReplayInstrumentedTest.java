package com.example.worlddomination.game.rendering;

import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.ai.DecisionRequest;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.persistence.GameSaveStore;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TurnPlan;
import com.example.worlddomination.game.ui.MatchOutcomeSequence;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Replays logged decisions from a normal preterminal save through the Android end screen. */
@RunWith(AndroidJUnit4.class)
public final class CompletedEpisodeReplayInstrumentedTest {
    @Test public void recordedEpisodeReachesItsNormalEndScreen() throws Exception {
        String encoded = InstrumentationRegistry.getArguments().getString("wd2_episode_fixture");
        assertTrue("An episode fixture is required", encoded != null);
        assertTrue("Use the isolated replay QA application",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName()
                        .endsWith(".replayqa"));
        JsonValue fixture = new JsonReader().parse(new String(Base64.decode(encoded, Base64.DEFAULT),
                StandardCharsets.UTF_8));
        Runner runner = new Runner(fixture);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            runner.load(app);
            Gdx.app.postRunnable(runner);
        }, scenario -> {
            assertTrue("Episode replay timed out", runner.done.await(150, TimeUnit.SECONDS));
            if (runner.failure != null) throw new AssertionError("Episode replay failed", runner.failure);
        });
    }

    private static final class Runner implements Runnable {
        final JsonValue fixture;
        final CountDownLatch done = new CountDownLatch(1);
        final Method captureVersion;
        final Method fixedTick;
        final Method playbackStart;
        WorldDominationGame app;
        GameState game;
        JsonValue turn;
        boolean inPlayback;
        boolean sawBattle;
        int ticks;
        volatile Throwable failure;

        Runner(JsonValue fixture) throws Exception {
            this.fixture = fixture;
            turn = fixture.get("turns").child;
            captureVersion = DecisionRequest.class.getDeclaredMethod("capture", GameState.class,
                    int.class, String.class, int.class);
            captureVersion.setAccessible(true);
            fixedTick = method("fixedUpdate", float.class);
            playbackStart = method("prepareTurnPlayback");
        }

        void load(WorldDominationGame runningGame) throws Exception {
            app = runningGame;
            GameState saved = GameSaveCodec.decode(Base64.decode(fixture.getString("checkpoint"),
                    Base64.DEFAULT));
            assertEquals(GameState.MatchStatus.ONGOING, saved.getMatchStatus());
            assertEquals(turn.getInt("turn"), saved.getTurn());
            GameSaveStore store = (GameSaveStore) value(app, "gameSaveStore");
            store.save(3, saved);
            game = (GameState) method("restoreMatch", int.class).invoke(app, 3);
            assertEquals(GameState.MatchStatus.ONGOING, game.getMatchStatus());
            render();
        }

        @Override public void run() {
            boolean complete = false;
            try {
                TurnPlaybackSequence playback = (TurnPlaybackSequence) value(app, "turnPlaybackSequence");
                MatchOutcomeSequence outcome = (MatchOutcomeSequence) value(app, "outcomeSequence");
                if (turn != null && !inPlayback) {
                    applyTurn();
                    assertTrue("Recorded turn enters source playback", playback.isBlockingInput());
                    inPlayback = true;
                    sawBattle = false;
                    ticks = 0;
                }
                if (turn == null && !inPlayback) {
                    render();
                    SourceEndgameRenderer renderer = (SourceEndgameRenderer) value(app, "endgameRenderer");
                    if (renderer == null || !renderer.isReady()) return;
                }
                for (int i = 0; i < 48; i++) {
                    assertTrue("Source timing did not finish", ++ticks < 6500);
                    fixedTick.invoke(app, 1f / 24f);
                    if (inPlayback) {
                        if (playback.getPhase() == TurnPlaybackSequence.Phase.BATTLE && !sawBattle) {
                            sawBattle = true;
                            render();
                        }
                        if (playback.isBlockingInput()) {
                            assertFalse("Outcome cannot precede battle playback", outcome.isEndgameVisible());
                        } else {
                            assertTrue("Replay visited its normal battle phase", sawBattle);
                            inPlayback = false;
                            turn = turn.next;
                            ticks = 0;
                            break;
                        }
                    } else if (outcome.isComplete()) {
                        assertOutcome(outcome);
                        capture();
                        complete = true;
                        break;
                    }
                }
                if (!complete) render();
            } catch (Throwable error) {
                failure = error;
                complete = true;
            } finally {
                if (complete) done.countDown();
                else Gdx.app.postRunnable(this);
            }
        }

        private void applyTurn() throws Exception {
            assertEquals(turn.getInt("turn"), game.getTurn());
            for (JsonValue answer : turn.get("human_answers")) {
                assertTrue(game.respondToDiplomacyProposal(answer.asBoolean()));
            }
            assertNull(game.getPendingHumanDiplomacyProposal());
            TurnPlan human = request(0, fixture.getString("human_policy"))
                    .selectId(turn.getString("human_selected"));
            assertNotNull(human);
            assertTrue(game.queueHumanPlan(human));
            game.beginOpponentPlanning();
            Map<Integer, TurnPlan> choices = new LinkedHashMap<>();
            for (JsonValue selection : turn.get("selections")) {
                int actor = Integer.parseInt(selection.name);
                TurnPlan plan = request(actor, fixture.getString("preference")).selectId(selection.asString());
                assertNotNull("Recorded plan " + selection.asString() + " for actor " + actor, plan);
                choices.put(actor, plan);
            }
            assertEquals("All logged choices commit", choices.keySet(), game.finishOpponentPlanning(choices));
            assertEquals(turn.getInt("turn") + 1, game.getTurn());
            playbackStart.invoke(app);
        }

        private DecisionRequest request(int actor, String preference) throws Exception {
            return (DecisionRequest) captureVersion.invoke(null, game, actor, preference,
                    fixture.getInt("decision_contract"));
        }

        private void assertOutcome(MatchOutcomeSequence outcome) {
            JsonValue expected = fixture.get("outcome");
            assertTrue(outcome.isEndgameVisible());
            assertEquals(expected.getString("status"), game.getMatchStatus().name());
            assertEquals(expected.getInt("turn"), game.getTurn());
            assertEquals(expected.getLong("human_final_score"), game.getFinalScore());
            assertEquals(game.getFinalScore(), outcome.getFinalScore());
            for (JsonValue nation : expected.get("nations")) {
                GameState.PlayerState actor = game.getPlayerState(nation.getInt("actor"));
                assertEquals(nation.getBoolean("alive"), actor.alive);
                assertEquals(nation.getInt("population"), actor.populationMillions);
                assertEquals(nation.getLong("score"), actor.score);
            }
        }

        private void render() throws Exception {
            field("skipNextFrameDelta").setBoolean(app, true);
            app.render();
            field("skipNextFrameDelta").setBoolean(app, true);
        }

        private void capture() throws Exception {
            render();
            String id = fixture.getString("episode");
            assertTrue(id.matches("[A-Za-z0-9_.-]+"));
            File destination = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getExternalFilesDir("wd2play-evidence");
            assertNotNull(destination);
            Pixmap screenshot = ScreenUtils.getFrameBufferPixmap(0, 0,
                    Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try {
                Set<Integer> colors = new HashSet<>();
                for (int x = 1; x < 32; x++) {
                    for (int y = 1; y < 16; y++) {
                        colors.add(screenshot.getPixel(x * screenshot.getWidth() / 32,
                                y * screenshot.getHeight() / 16));
                    }
                }
                assertTrue("Endgame artwork rendered in the framebuffer", colors.size() > 20);
                PixmapIO.writePNG(Gdx.files.absolute(new File(destination, id + ".png").getAbsolutePath()),
                        screenshot, -1, true);
            } finally {
                screenshot.dispose();
            }
        }
    }

    private static Field field(String name) throws Exception {
        return SourceBattleCleanupInstrumentedTest.field(WorldDominationGame.class, name);
    }

    private static Object value(WorldDominationGame app, String name) throws Exception {
        return field(name).get(app);
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
