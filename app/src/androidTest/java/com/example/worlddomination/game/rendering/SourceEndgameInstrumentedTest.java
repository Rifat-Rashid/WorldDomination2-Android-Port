package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalEndgameAssets;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.persistence.PersonalBestStore;
import com.example.worlddomination.game.persistence.ScorePreferenceBackend;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.MatchOutcomePresentation;
import com.example.worlddomination.game.ui.MatchOutcomeSequence;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises actual terminal turns, authored endgame art, score, and touch handoff. */
@RunWith(AndroidJUnit4.class)
public final class SourceEndgameInstrumentedTest {
    @Test public void victoryDrawsOriginalIntroAndFrozenScoreThenNewGame() throws Exception {
        exercise(GameState.MatchStatus.VICTORY);
    }

    @Test public void defeatDrawsOriginalIntroAndAllowsNewGameAtScoreStart() throws Exception {
        exercise(GameState.MatchStatus.DEFEAT);
    }

    @Test public void victoryWaitsForActualTurnPlaybackBeforeEndgameCountdown() throws Exception {
        exercisePlaybackHandoff(GameState.MatchStatus.VICTORY);
    }

    @Test public void defeatWaitsForActualTurnPlaybackBeforeEndgameCountdown() throws Exception {
        exercisePlaybackHandoff(GameState.MatchStatus.DEFEAT);
    }

    private static void exercisePlaybackHandoff(GameState.MatchStatus expected) throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                WorldDominationGame app = null;
                PersonalBestStore previousStore = null;
                try {
                    app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field(WorldDominationGame.class, "splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    OriginalFlashAnimationAssets battleAssets = (OriginalFlashAnimationAssets)
                            field(WorldDominationGame.class, "flashAnimationAssets").get(app);
                    awaitFlashAssets(battleAssets);
                    previousStore = (PersonalBestStore) field(WorldDominationGame.class,
                            "personalBestStore").get(app);
                    MemoryBackend backend = new MemoryBackend();
                    field(WorldDominationGame.class, "personalBestStore")
                            .set(app, new PersonalBestStore(backend));

                    GameState state = expected == GameState.MatchStatus.VICTORY
                            ? decisiveSoldierVictoryBeforeConfirmation()
                            : terminalStateBeforeConfirmation(expected);
                    field(WorldDominationGame.class, "game").set(app, state);
                    field(WorldDominationGame.class, "modalOpen").setBoolean(app, false);
                    ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                            "cityRenderer").get(app)).reset(state);
                    invoke(app, "confirmTurnAndPreparePlayback");
                    assertEquals(expected, state.getMatchStatus());
                    MatchOutcomeSequence sequence = (MatchOutcomeSequence)
                            field(WorldDominationGame.class, "outcomeSequence").get(app);
                    TurnPlaybackSequence playback = (TurnPlaybackSequence)
                            field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
                    assertEquals(TurnPlaybackSequence.Phase.ACTION_LABELS, playback.getPhase());

                    for (int frame = 0; frame < MatchOutcomeSequence.TICKS_BEFORE_ENDGAME + 1;
                            frame++) {
                        fixedTick(app);
                        assertTrue("Source action labels still block the handoff",
                                playback.isBlockingInput());
                        assertEquals(MatchOutcomeSequence.Phase.INACTIVE, sequence.getPhase());
                    }
                    assertNull("Endgame assets remain lazy during playback",
                            field(WorldDominationGame.class, "endgameAssets").get(app));
                    assertEquals("Personal best is recorded at handoff", 0L, backend.best);

                    int playbackTicks = 0;
                    boolean sawCombat = false;
                    while (playback.isBlockingInput() && playbackTicks++ < 6200) {
                        fixedTick(app);
                        if (playback.getPhase() == TurnPlaybackSequence.Phase.BATTLE) {
                            @SuppressWarnings("unchecked")
                            java.util.List<FlashSoldierOneAnimation> soldiers =
                                    (java.util.List<FlashSoldierOneAnimation>)
                                    field(WorldDominationGame.class, "soldierBattleAnimations")
                                            .get(app);
                            if (!soldiers.isEmpty() && !sawCombat) {
                                sawCombat = true;
                                render(app);
                                assertEquals(MatchOutcomeSequence.Phase.INACTIVE,
                                        sequence.getPhase());
                                capture("source-endgame-win-final-attack.png");
                            }
                        }
                        if (playback.isBlockingInput()) {
                            assertEquals(MatchOutcomeSequence.Phase.INACTIVE,
                                    sequence.getPhase());
                        }
                    }
                    if (expected == GameState.MatchStatus.VICTORY)
                        assertTrue("The decisive Soldier1 attack plays before the endgame",
                                sawCombat);
                    assertEquals("Turn labels, battle, and reset barrier finish first",
                            TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
                    assertEquals(MatchOutcomeSequence.Phase.BEFORE_ENDGAME,
                            sequence.getPhase());
                    OriginalEndgameAssets endgameAssets = (OriginalEndgameAssets)
                            field(WorldDominationGame.class, "endgameAssets").get(app);
                    assertNotNull(endgameAssets);
                    awaitAssets(endgameAssets);
                    assertEquals(state.getFinalScore(), backend.best);

                    // Source creates the timer at handoff; it cannot consume that same tick.
                    for (int frame = 1; frame < MatchOutcomeSequence.TICKS_BEFORE_ENDGAME;
                            frame++) fixedTick(app);
                    assertEquals(MatchOutcomeSequence.Phase.BEFORE_ENDGAME, sequence.getPhase());
                    fixedTick(app);
                    assertEquals(MatchOutcomeSequence.Phase.INTRO, sequence.getPhase());
                    render(app);
                    capture("source-endgame-handoff-" + expected.name() + "-intro.png");
                    for (int frame = 0; frame < MatchOutcomeSequence.TICKS_BEFORE_SCORE;
                            frame++) fixedTick(app);
                    assertTrue("The original 48-tick intro precedes score", sequence.isScoreStarted());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    try {
                        if (app != null && previousStore != null)
                            field(WorldDominationGame.class, "personalBestStore")
                                    .set(app, previousStore);
                    } catch (Throwable restoreError) {
                        if (failure.get() == null) failure.set(restoreError);
                    }
                    done.countDown();
                }
            }));
            assertTrue("Endgame handoff GL work timed out", done.await(150, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source endgame handoff", failure.get());
        }
    }

    private static void exercise(GameState.MatchStatus expected) throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                WorldDominationGame app = null;
                PersonalBestStore previousStore = null;
                try {
                    app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field(WorldDominationGame.class, "splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    previousStore = (PersonalBestStore) field(WorldDominationGame.class,
                            "personalBestStore").get(app);
                    MemoryBackend backend = new MemoryBackend();
                    field(WorldDominationGame.class, "personalBestStore")
                            .set(app, new PersonalBestStore(backend));

                    GameState state = terminalState(expected);
                    field(WorldDominationGame.class, "game").set(app, state);
                    field(WorldDominationGame.class, "modalOpen").setBoolean(app, false);
                    ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                            "cityRenderer").get(app)).reset(state);
                    render(app); // Starts the lazy endgame load and the source sequence.
                    MatchOutcomeSequence sequence = (MatchOutcomeSequence)
                            field(WorldDominationGame.class, "outcomeSequence").get(app);
                    assertEquals(expected, sequence.getOutcome());
                    OriginalEndgameAssets assets = (OriginalEndgameAssets)
                            field(WorldDominationGame.class, "endgameAssets").get(app);
                    assertNotNull(assets);
                    awaitAssets(assets);
                    assertManagedLeaves(assets);
                    assertEquals(state.getFinalScore(), backend.best);
                    assertEquals(MatchOutcomePresentation.personalBestLine(backend.best),
                            field(WorldDominationGame.class, "outcomePersonalBestText").get(app));

                    tick(app, MatchOutcomeSequence.TICKS_BEFORE_ENDGAME);
                    assertEquals(MatchOutcomeSequence.Phase.INTRO, sequence.getPhase());
                    render(app);
                    String prefix = expected == GameState.MatchStatus.VICTORY
                            ? "source-endgame-win" : "source-endgame-lost";
                    capture(prefix + "-intro.png");
                    assertIntroMatchesAuthoredLeaf(app, assets,
                            expected == GameState.MatchStatus.VICTORY ? 5884 : 5886);
                    tapSource(app, 553f, 465f);
                    assertSame("New Game is inert during the two-second intro", state,
                            field(WorldDominationGame.class, "game").get(app));

                    tick(app, MatchOutcomeSequence.TICKS_BEFORE_SCORE);
                    assertTrue(sequence.isScoreStarted());
                    render(app);
                    assertStableRepeatedFrame(app);
                    capture(prefix + "-score.png");
                    assertScoreBackgroundMatchesAuthoredLeaf(app, assets);
                    tapSource(app, 630f, 465f); // Safely inside Website's adjacent left edge.
                    assertSame("Website must not activate New Game", state,
                            field(WorldDominationGame.class, "game").get(app));
                    if (expected == GameState.MatchStatus.VICTORY) {
                        for (int i = 0; i < 2500 && !sequence.isComplete(); i++)
                            invoke(app, "advanceOutcomeSequence");
                        assertTrue("Score and source bonuses finish", sequence.isComplete());
                        assertEquals(state.getFinalScoreBreakdown().getFinalScore(),
                                sequence.getScoreCount());
                        assertEquals(MatchOutcomePresentation.formatScore(state.getFinalScore()),
                                sequence.getScoreText());
                        assertEquals(state.getFinalScore(), backend.best);
                        assertEquals(2, state.getFinalScoreBreakdown().getSatelliteCount());
                        assertFalse(sequence.getSatelliteBonusText().isEmpty());
                        assertFalse(sequence.getFlagBonusText().isEmpty());
                        render(app);
                        assertStableRepeatedFrame(app);
                        capture(prefix + "-complete.png");
                        assertScoreBackgroundMatchesAuthoredLeaf(app, assets);
                        assertPhoneScoreVisible(app);
                        assertScoreGlyphColor(app);
                    }
                    tapSource(app, 553f, 465f);
                    assertNull("Authored New Game returns to setup",
                            field(WorldDominationGame.class, "game").get(app));
                    assertNull("New setup disposes the finished match atlas",
                            field(WorldDominationGame.class, "endgameAssets").get(app));
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    try {
                        if (app != null && previousStore != null)
                            field(WorldDominationGame.class, "personalBestStore")
                                    .set(app, previousStore);
                    } catch (Throwable restoreError) {
                        if (failure.get() == null) failure.set(restoreError);
                    }
                    done.countDown();
                }
            }));
            assertTrue("Endgame GL work timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source endgame integration", failure.get());
        }
    }

    private static GameState terminalState(GameState.MatchStatus outcome) throws Exception {
        GameState state = terminalStateBeforeConfirmation(outcome);
        state.confirmTurn();
        assertEquals(outcome, state.getMatchStatus());
        assertNotNull(state.getFinalScoreBreakdown());
        return state;
    }

    private static GameState terminalStateBeforeConfirmation(GameState.MatchStatus outcome)
            throws Exception {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        GameState state = new GameState(setup, outcome == GameState.MatchStatus.VICTORY ? 8L : 12L);
        GameState.PlayerState eliminated = outcome == GameState.MatchStatus.VICTORY
                ? state.getPlayerState(1) : state.getHuman();
        if (outcome == GameState.MatchStatus.VICTORY) {
            // A valid late-match snapshot with previously launched satellites and a flag credit.
            field(GameState.PlayerState.class, "satellitesUsed").setInt(state.getHuman(), 2);
            field(GameState.PlayerState.class, "lastAttackedByPlayerId").setInt(eliminated, 0);
        }
        for (CityState city : eliminated.getCities()) city.populationMillions = 0;
        eliminated.populationMillions = 0;
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        return state;
    }

    private static GameState decisiveSoldierVictoryBeforeConfirmation() throws Exception {
        // The deterministic final-city attack from BattlefieldFlagStateTest.
        Constructor<GameState> constructor = GameState.class.getDeclaredConstructor(
                int.class, long.class, boolean.class);
        constructor.setAccessible(true);
        GameState state = constructor.newInstance(1, 214L, false);
        for (GameState.PlayerState opponent : state.getPlayers()) {
            if (opponent.human) continue;
            for (CityState city : opponent.getCities()) city.populationMillions = 0;
            opponent.populationMillions = 0;
            if (opponent.id != 1) opponent.alive = false;
        }
        GameState.PlayerState finalRival = state.getPlayerState(1);
        finalRival.getCity(0).populationMillions = 1;
        finalRival.populationMillions = 1;
        state.getHuman().getWeapons().get("soldier1").inStock = 1;
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        return state;
    }

    private static void awaitFlashAssets(OriginalFlashAnimationAssets assets) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (!assets.update() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue("Battle atlas loads before playback", assets.isLoaded());
    }

    private static void awaitAssets(OriginalEndgameAssets assets) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (!assets.update() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue("The thirteen authored leaves load within 45 seconds", assets.isReady());
    }

    private static void assertManagedLeaves(OriginalEndgameAssets assets) {
        int[] ids = {103, 3253, 4117, 4484, 4580, 4582, 4584,
                5884, 5886, 5888, 5891, 5894, 5902};
        for (int id : ids) {
            assertNotNull("Missing managed source shape " + id, assets.getShape(id));
            assertTrue("Endgame texture must restore after GL loss: " + id,
                    assets.getShape(id).getTexture().isManaged());
        }
    }

    private static void assertScoreGlyphColor(WorldDominationGame app) throws Exception {
        // At a small surface, glyph strokes are mostly fractional coverage.
        // Render the production
        // renderer at two pixels per source-stage point to test its exact color
        // independently of the device's mipmap/antialiasing coverage.
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 1600, 1200, false);
        SpriteBatch isolated = new SpriteBatch();
        Pixmap pixels = null;
        boolean bufferStarted = false;
        try {
            buffer.begin();
            bufferStarted = true;
            Gdx.gl.glClearColor(0.006f, 0.016f, 0.022f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            isolated.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 800, 600));
            isolated.begin();
            SourceEndgameRenderer renderer = (SourceEndgameRenderer)
                    field(WorldDominationGame.class, "endgameRenderer").get(app);
            assertTrue(renderer.draw(isolated, new Rectangle(0, 0, 800, 600),
                    (MatchOutcomeSequence) field(WorldDominationGame.class,
                            "outcomeSequence").get(app),
                    (String) field(WorldDominationGame.class, "outcomePersonalBestText").get(app),
                    (com.badlogic.gdx.graphics.g2d.BitmapFont)
                            field(WorldDominationGame.class, "font").get(app)));
            isolated.end();
            pixels = ScreenUtils.getFrameBufferPixmap(107, 358, 739, 106);
            int green = 0;
            int pureGreen = 0;
            int highestGreen = 0;
            int[] greenHistogram = new int[256];
            for (int y = 0; y < pixels.getHeight(); y++)
                for (int x = 0; x < pixels.getWidth(); x++) {
                    int value = pixels.getPixel(x, y);
                    int red = value >>> 24 & 255, channel = value >>> 16 & 255;
                    int blue = value >>> 8 & 255;
                    if (red < 8 && blue < 8 && channel > 0) {
                        pureGreen++;
                        highestGreen = Math.max(highestGreen, channel);
                        greenHistogram[channel]++;
                        if (channel >= 196 && channel <= 212) green++;
                    }
                }
            StringBuilder dominant = new StringBuilder();
            for (int rank = 0; rank < 5; rank++) {
                int bestChannel = -1;
                for (int channel = 0; channel < greenHistogram.length; channel++)
                    if (greenHistogram[channel] > 0
                            && (bestChannel < 0
                            || greenHistogram[channel] > greenHistogram[bestChannel]))
                        bestChannel = channel;
                if (bestChannel < 0) break;
                if (dominant.length() > 0) dominant.append(',');
                dominant.append(bestChannel).append(':').append(greenHistogram[bestChannel]);
                greenHistogram[bestChannel] = 0;
            }
            assertTrue("Score uses the authored #00CC00 glyph color: matching=" + green
                    + ", pureGreen=" + pureGreen + ", topGreenChannels=" + dominant
                    + ", bounds=107,358"
                    + ", size=" + pixels.getWidth() + 'x' + pixels.getHeight()
                    + ", framebuffer=1600x1200", green > 20);
            assertTrue("Opaque score glyphs must have the source tint, not brighter green: "
                    + highestGreen, highestGreen >= 196 && highestGreen <= 212);
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            if (pixels != null) pixels.dispose();
            if (bufferStarted) buffer.end();
            isolated.dispose();
            buffer.dispose();
            render(app); // Restore the phone viewport, layout, and font scale.
        }
    }

    private static void assertPhoneScoreVisible(WorldDominationGame app) throws Exception {
        Rectangle content = (Rectangle) field(WorldDominationGame.class,
                "endgameContentBounds").get(app);
        float scale = Math.min(content.width / 800f, content.height / 600f);
        float left = content.x + (content.width - 800f * scale) / 2f;
        float bottom = content.y + (content.height - 600f * scale) / 2f;
        Viewport viewport = (Viewport) field(WorldDominationGame.class, "viewport").get(app);
        Vector2 low = viewport.project(new Vector2(left + 53.45f * scale,
                bottom + (600f - 421f) * scale));
        Vector2 high = viewport.project(new Vector2(left + 423f * scale,
                bottom + (600f - 368f) * scale));
        Pixmap pixels = ScreenUtils.getFrameBufferPixmap(Math.round(low.x), Math.round(low.y),
                Math.max(1, Math.round(high.x - low.x)), Math.max(1, Math.round(high.y - low.y)));
        try {
            int painted = 0;
            for (int y = 0; y < pixels.getHeight(); y++)
                for (int x = 0; x < pixels.getWidth(); x++) {
                    int color = pixels.getPixel(x, y);
                    int red = color >>> 24 & 255, green = color >>> 16 & 255;
                    int blue = color >>> 8 & 255;
                    if (green > red + 20 && green > blue + 20) painted++;
                }
            assertTrue("Score glyphs must be visible on the actual phone surface", painted > 20);
        } finally { pixels.dispose(); }
    }

    private static void assertIntroMatchesAuthoredLeaf(WorldDominationGame app,
                                                        OriginalEndgameAssets assets,
                                                        int shapeId) throws Exception {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] actual = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
        Rectangle content = (Rectangle) field(WorldDominationGame.class,
                "endgameContentBounds").get(app);
        float scale = Math.min(content.width / 800f, content.height / 600f);
        float left = content.x + (content.width - 800f * scale) / 2f;
        float bottom = content.y + (content.height - 600f * scale) / 2f;
        TextureRegion leaf = assets.getShape(shapeId);
        float x = left + (0.45f + assets.getOriginX(shapeId)) * scale;
        float y = bottom + 600f * scale
                - (assets.getOriginY(shapeId) + leaf.getRegionHeight()) * scale;
        Gdx.gl.glClearColor(0.006f, 0.016f, 0.022f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        SpriteBatch isolated = new SpriteBatch();
        try {
            com.badlogic.gdx.graphics.Camera camera = (com.badlogic.gdx.graphics.Camera)
                    field(WorldDominationGame.class, "camera").get(app);
            isolated.setProjectionMatrix(camera.combined);
            isolated.begin();
            isolated.draw(leaf, x, y, 0f, leaf.getRegionHeight() * scale,
                    leaf.getRegionWidth() * scale, leaf.getRegionHeight() * scale,
                    1f, 1f, 0f);
            isolated.end();
            byte[] expected = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
            assertTrue("Intro must render source leaf, not HUD or modal",
                    Arrays.equals(expected, actual));
        } finally {
            isolated.dispose();
        }
        render(app);
    }

    private static void assertScoreBackgroundMatchesAuthoredLeaf(WorldDominationGame app,
                                                               OriginalEndgameAssets assets)
            throws Exception {
        Rectangle content = (Rectangle) field(WorldDominationGame.class,
                "endgameContentBounds").get(app);
        float scale = Math.min(content.width / 800f, content.height / 600f);
        float left = content.x + (content.width - 800f * scale) / 2f;
        float bottom = content.y + (content.height - 600f * scale) / 2f;
        Viewport viewport = (Viewport) field(WorldDominationGame.class, "viewport").get(app);
        // This metal background patch is above all score-frame controls and text.
        Vector2 low = viewport.project(new Vector2(left + 100f * scale,
                bottom + 530f * scale));
        Vector2 high = viewport.project(new Vector2(left + 180f * scale,
                bottom + 570f * scale));
        int x = Math.round(low.x), y = Math.round(low.y);
        int width = Math.max(1, Math.round(high.x - low.x));
        int height = Math.max(1, Math.round(high.y - low.y));
        byte[] actual = ScreenUtils.getFrameBufferPixels(x, y, width, height, false);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        SpriteBatch isolated = new SpriteBatch();
        try {
            isolated.setProjectionMatrix(((com.badlogic.gdx.graphics.Camera)
                    field(WorldDominationGame.class, "camera").get(app)).combined);
            TextureRegion leaf = assets.getShape(5888);
            isolated.begin();
            isolated.draw(leaf,
                    left + (0.45f + assets.getOriginX(5888)) * scale,
                    bottom + (600f - assets.getOriginY(5888) - leaf.getRegionHeight()) * scale,
                    leaf.getRegionWidth() * scale, leaf.getRegionHeight() * scale);
            isolated.end();
            assertArrayEquals("Score must retain the original metallic background",
                    ScreenUtils.getFrameBufferPixels(x, y, width, height, false), actual);
        } finally { isolated.dispose(); }
        render(app);
    }

    private static void tick(WorldDominationGame app, int count) throws Exception {
        for (int i = 0; i < count; i++) invoke(app, "advanceOutcomeSequence");
    }

    private static void fixedTick(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }

    private static void tapSource(WorldDominationGame app, float sourceX,
                                  float sourceY) throws Exception {
        Rectangle content = (Rectangle) field(WorldDominationGame.class,
                "endgameContentBounds").get(app);
        float scale = Math.min(content.width / 800f, content.height / 600f);
        float left = content.x + (content.width - 800f * scale) / 2f;
        float bottom = content.y + (content.height - 600f * scale) / 2f;
        float worldX = left + (sourceX + 0.45f) * scale;
        float worldY = bottom + (600f - sourceY) * scale;
        Viewport viewport = (Viewport) field(WorldDominationGame.class, "viewport").get(app);
        Vector2 screen = viewport.project(new Vector2(worldX, worldY));
        int x = Math.round(screen.x), y = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(x, y, 0, 0);
        app.touchUp(x, y, 0, 0);
    }

    private static void render(WorldDominationGame app) throws Exception {
        field(WorldDominationGame.class, "skipNextFrameDelta").setBoolean(app, true);
        app.render();
        assertEquals("Endgame drawing must not leave an OpenGL error", GL20.GL_NO_ERROR,
                Gdx.gl.glGetError());
    }

    private static void assertStableRepeatedFrame(WorldDominationGame app) throws Exception {
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        byte[] previous = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
        for (int frame = 0; frame < 3; frame++) {
            render(app);
            byte[] next = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
            assertArrayEquals("Stopped score art and cached labels must survive redraw " + frame,
                    previous, next);
            previous = next;
        }
    }

    private static void capture(String name) {
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try { PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image); }
        finally { image.dispose(); }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame app, String methodName) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static final class MemoryBackend implements ScorePreferenceBackend {
        long best;
        @Override public long getLong(String key, long fallback) { return best; }
        @Override public void putLong(String key, long value) { best = value; }
        @Override public void flush() { }
    }
}
