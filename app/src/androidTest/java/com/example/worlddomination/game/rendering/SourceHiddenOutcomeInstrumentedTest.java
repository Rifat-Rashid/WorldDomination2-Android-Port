package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.assets.OriginalEndgameAssets;
import com.example.worlddomination.game.persistence.PersonalBestStore;
import com.example.worlddomination.game.persistence.ScorePreferenceBackend;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.MatchOutcomeSequence;
import com.example.worlddomination.testing.GameActivityScenario;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A legal terminal GameState exercises World's actual personal-best and cheated-score handoff. */
@RunWith(AndroidJUnit4.class)
public final class SourceHiddenOutcomeInstrumentedTest {
    private static final String FIXTURE = "source-campaign-seed0-victory.json";
    private static final String FIXTURE_SHA256 =
            "f4f832d6e2950078dd3f2ca111513c3cbbc9c68f9fe60e84bd7f79b18541c6f3";

    @Test public void cheatedVictoryShowsAuthoredScoreWithoutSavingPersonalBest() throws Exception {
        JsonValue campaign = readCampaign();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                PersonalBestStore previousStore = null;
                try {
                    invoke(app, "initializeGameplayAssets");
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                            field(WorldDominationGame.class, "flashAnimationAssets").get(app);
                    long assetDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!(assets.isLoaded() && assets.areCenterUiAssetsLoaded()
                            && assets.areSetupPortraitsLoaded())) {
                        assertTrue("Setup artwork did not load before splash dismissal",
                                System.nanoTime() < assetDeadline);
                        assets.update();
                        Thread.sleep(10);
                    }
                    Object splash = field(WorldDominationGame.class, "splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    previousStore = (PersonalBestStore) field(WorldDominationGame.class,
                            "personalBestStore").get(app);
                    RecordingBackend backend = new RecordingBackend();
                    field(WorldDominationGame.class, "personalBestStore")
                            .set(app, new PersonalBestStore(backend));

                    GameState clean = replayLegalVictory(campaign);
                    assertTrue(clean.getFinalScore() > 0);
                    field(WorldDominationGame.class, "game").set(app, clean);
                    invoke(app, "synchronizeOutcomeSequence");
                    assertEquals("Uncheated control records the score", 1, backend.puts);
                    assertEquals("Uncheated control flushes storage", 1, backend.flushes);
                    assertEquals(clean.getFinalScore(), backend.best);

                    field(WorldDominationGame.class, "game").set(app, null);
                    invoke(app, "synchronizeOutcomeSequence");
                    backend.best = 0;
                    backend.puts = backend.flushes = 0;

                    GameState cheated = replayLegalVictory(campaign);
                    assertEquals(clean.getFinalScore(), cheated.getFinalScore());
                    assertFalse(cheated.getHiddenCommands().submit("unknown command").recognized);
                    assertTrue(cheated.getHiddenCommands().isScoreCorrupt());
                    field(WorldDominationGame.class, "game").set(app, cheated);
                    invoke(app, "synchronizeOutcomeSequence");
                    MatchOutcomeSequence sequence = (MatchOutcomeSequence)
                            field(WorldDominationGame.class, "outcomeSequence").get(app);
                    assertEquals(MatchOutcomeSequence.Phase.BEFORE_ENDGAME, sequence.getPhase());
                    assertEquals("", field(WorldDominationGame.class,
                            "outcomePersonalBestText").get(app));
                    assertEquals(0, backend.puts);
                    assertEquals(0, backend.flushes);
                    assertEquals(0, backend.best);
                    assertEquals("Cheat affects presentation, not canonical result",
                            clean.getFinalScore(), cheated.getFinalScore());

                    // Production ignores New Game until the lazy authored renderer is ready.
                    OriginalEndgameAssets endgameAssets = (OriginalEndgameAssets)
                            field(WorldDominationGame.class, "endgameAssets").get(app);
                    assertNotNull(endgameAssets);
                    long endgameDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!endgameAssets.isReady()) {
                        assertTrue("Endgame artwork did not load", System.nanoTime() < endgameDeadline);
                        endgameAssets.update();
                        Thread.sleep(5);
                    }

                    tickOutcome(app, MatchOutcomeSequence.TICKS_BEFORE_ENDGAME);
                    assertEquals(MatchOutcomeSequence.Phase.INTRO, sequence.getPhase());
                    tickOutcome(app, MatchOutcomeSequence.TICKS_BEFORE_SCORE);
                    assertEquals(MatchOutcomeSequence.Phase.CHEATED_SCORE, sequence.getPhase());
                    assertEquals("!!CHEATED!!", sequence.getScoreText());
                    assertEquals(0, sequence.getFinalScore());
                    assertEquals(0, backend.puts);
                    assertEquals(0, backend.flushes);
                    assertEquals(clean.getFinalScore(), cheated.getFinalScore());
                    field(WorldDominationGame.class, "skipNextFrameDelta").setBoolean(app, true);
                    app.render();
                    tapNewGame(app);
                    assertNull("New Game remains usable after cheated score",
                            field(WorldDominationGame.class, "game").get(app));
                    assertEquals(0, backend.puts);
                    assertEquals(0, backend.flushes);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    try {
                        field(WorldDominationGame.class, "game").set(app, null);
                        invoke(app, "synchronizeOutcomeSequence");
                        if (previousStore != null)
                            field(WorldDominationGame.class, "personalBestStore")
                                    .set(app, previousStore);
                    } catch (Throwable restoreError) {
                        if (failure.get() == null) failure.set(restoreError);
                    }
                    done.countDown();
                }
            }));
            assertTrue("Cheated endgame GL work timed out", done.await(120, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Cheated endgame handoff", failure.get());
        }
    }

    private static GameState replayLegalVictory(JsonValue campaign) {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(0);
        assertTrue(setup.toggleOpponent(0));
        GameState state = new GameState(setup);
        JsonValue turns = campaign.get("turns");
        assertEquals(31, turns.size);
        for (JsonValue turn : turns) {
            for (JsonValue control : turn.get("controls")) {
                if (control.has("declinedPending")) {
                    assertNotNull(state.getPendingHumanDiplomacyProposal());
                    assertTrue(state.respondToDiplomacyProposal(false));
                    continue;
                }
                String[] request = control.getString("requested").split(" ");
                switch (request[0]) {
                    case "RESEARCH":
                        state.selectAction(GameState.Action.RESEARCH);
                        assertTrue(state.selectResearchTechnology(request[1]));
                        break;
                    case "PRODUCTION":
                        state.selectAction(GameState.Action.PRODUCTION);
                        assertTrue(state.configureProductionSlider(request[1],
                                Integer.parseInt(request[2])));
                        break;
                    case "ATTACK":
                        state.selectAction(GameState.Action.ATTACK);
                        assertTrue(state.configureAttack(Integer.parseInt(request[2]),
                                Integer.parseInt(request[3]), request[1]));
                        break;
                    case "RESOURCES": state.selectAction(GameState.Action.RESOURCES); break;
                    default: throw new AssertionError("Unexpected legal control " + request[0]);
                }
                assertTrue("Accept turn " + turn.getInt("turn"), state.acceptSelectedAction());
            }
            assertNull(state.getPendingHumanDiplomacyProposal());
            state.confirmTurn();
        }
        assertEquals(GameState.MatchStatus.VICTORY, state.getMatchStatus());
        assertEquals(31, state.getTurn());
        assertEquals(250_000L, state.getFinalScore());
        return state;
    }

    private static JsonValue readCampaign() throws Exception {
        byte[] bytes;
        try (InputStream input = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(FIXTURE);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            bytes = output.toByteArray();
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 255));
        assertEquals("Independent source campaign fixture", FIXTURE_SHA256, hex.toString());
        return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static void tickOutcome(WorldDominationGame app, int count) throws Exception {
        for (int tick = 0; tick < count; tick++) invoke(app, "advanceOutcomeSequence");
    }

    private static void tapNewGame(WorldDominationGame app) throws Exception {
        Rectangle content = (Rectangle) field(WorldDominationGame.class,
                "endgameContentBounds").get(app);
        float scale = Math.min(content.width / 800f, content.height / 600f);
        float left = content.x + (content.width - 800f * scale) / 2f;
        float bottom = content.y + (content.height - 600f * scale) / 2f;
        Viewport viewport = (Viewport) field(WorldDominationGame.class, "viewport").get(app);
        Vector2 screen = viewport.project(new Vector2(left + 553.45f * scale,
                bottom + (600f - 465f) * scale));
        int x = Math.round(screen.x);
        int y = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(x, y, 0, 0);
        app.touchUp(x, y, 0, 0);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static final class RecordingBackend implements ScorePreferenceBackend {
        long best;
        int puts, flushes;
        @Override public long getLong(String key, long fallback) { return best; }
        @Override public void putLong(String key, long value) { puts++; best = value; }
        @Override public void flush() { flushes++; }
    }
}
