package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.ConfirmTurnAnimation;
import com.example.worlddomination.game.ui.EndgameScreenLayout;
import com.example.worlddomination.game.ui.MatchOutcomePresentation;
import com.example.worlddomination.game.ui.MatchOutcomeSequence;
import com.example.worlddomination.game.ui.ProductionScreenLayout;
import com.example.worlddomination.game.ui.ScrollPaneState;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import com.example.worlddomination.testing.GameActivityScenario;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Legal initial setup -> touched actions -> full World playback -> score -> New Game.
 * Existing goldens describe canonical state, not pixel-exact battle presentation.
 */
@RunWith(AndroidJUnit4.class)
public final class SourceLegalTerminalPlaybackInstrumentedTest {
    private static final int MAX_TURN_TICKS = 6200;
    private static final int TICKS_PER_GL_CHUNK = 64;
    private static final long WALL_SECONDS = 600;

    @Test public void legalTwentyNineTurnDefeatPlaysThroughScoreAndNewGame() throws Exception {
        runCampaign(false, "source-campaign-seed1.json",
                "e270960743ffef77cf5e940da5775b706026460dfbbf5f30dcd585bdb05f732c");
    }

    @Test public void legalThirtyOneTurnVictoryPlaysThroughScoreAndNewGame() throws Exception {
        runCampaign(true, "source-campaign-seed0-victory.json",
                "f4f832d6e2950078dd3f2ca111513c3cbbc9c68f9fe60e84bd7f79b18541c6f3");
    }

    private static void runCampaign(boolean victory, String fixture, String expectedHash)
            throws Exception {
        byte[] bytes;
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open(fixture); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            bytes = output.toByteArray();
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            hash.append(String.format(Locale.US, "%02x", value & 255));
        assertEquals("Independent source fixture bytes", expectedHash, hash.toString());
        JsonValue golden = new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        assertEquals(victory ? 31 : 29, golden.get("turns").size);
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            Runner runner = new Runner(victory, golden);
            scenario.onActivity(activity -> Gdx.app.postRunnable(runner));
            if (!runner.done.await(WALL_SECONDS + 5, TimeUnit.SECONDS)) {
                runner.cancelled = true;
                fail("Legal campaign exceeded ten-minute wall bound: " + runner.position);
            }
            if (runner.failure.get() != null)
                throw new AssertionError("Legal terminal campaign: " + runner.position, runner.failure.get());
        }
    }

    /** Reposts after every bounded chunk: Android/assets/audio callbacks are never starved. */
    private static final class Runner implements Runnable {
        final boolean victory;
        final JsonValue golden;
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WALL_SECONDS);
        volatile boolean cancelled;
        volatile String position = "initializing";
        WorldDominationGame app;
        GameState state;
        JsonValue turn;
        int stage;
        int turnTicks;
        int outcomeTicks;
        int renderedBattleTurn = -1;
        long finalScore;

        Runner(boolean victory, JsonValue golden) { this.victory = victory; this.golden = golden; }

        @Override public void run() {
            boolean complete = false;
            try {
                if (cancelled) { done.countDown(); return; }
                assertTrue("Ten-minute campaign wall bound: " + position, System.nanoTime() < deadline);
                if (app == null) {
                    app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    assertNull("Fresh activity must start outside a match", value(app, "game"));
                    invoke(app, "initializeGameplayAssets");
                    // Only seed injection: no canonical state, stock or late-game snapshot injection.
                    field("setup").set(app, new GameSetup(victory ? 0L : 1L));
                    field("setupLeaderListStart").setInt(app, 0);
                }
                render(app);
                switch (stage) {
                    case 0:
                        position = "loading/setup";
                        OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                                value(app, "flashAnimationAssets");
                        if (!assets.isLoaded() || !assets.areCenterUiAssetsLoaded()
                                || !assets.areSetupPortraitsLoaded()) break;
                        // Production requestSplashContinue keeps the splash until setup art
                        // is ready. drawSetupScreen assumes carousel arrows already exist.
                        Object splash = value(app, "splashState");
                        splash.getClass().getMethod("dismiss").invoke(splash);
                        render(app);
                        SetupScreenLayout setup = (SetupScreenLayout) value(app, "setupLayout");
                        tap(app, setup.difficultyButtons[victory ? 0 : 1]);
                        tap(app, setup.leaderCards[0]);
                        if (!victory) tap(app, setup.leaderCards[1]);
                        tap(app, setup.startButton);
                        state = (GameState) value(app, "game");
                        assertNotNull("Real Start touch creates game", state);
                        assertState(state, golden.get("initial"));
                        turn = golden.get("turns").child;
                        stage = 1;
                        break;
                    case 1:
                        position = "turn " + turn.getInt("turn") + " UI controls";
                        assertSame("One legal match instance throughout", state, value(app, "game"));
                        prepareTurn();
                        // Open the hatch through its real 24Hz animation before touching it.
                        for (int tick = 0; tick < 24; tick++) fixedTick(app);
                        tap(app, (Rectangle) value(app, "confirmButton"));
                        assertTrue("Confirm touch begins closing hatch",
                                ((ConfirmTurnAnimation) value(app, "confirmTurnAnimation")).isPending());
                        turnTicks = 0;
                        stage = 2;
                        break;
                    case 2:
                        position = "turn " + turn.getInt("turn") + " playback tick " + turnTicks;
                        for (int i = 0; i < TICKS_PER_GL_CHUNK; i++) {
                            assertTrue("Playback bound: " + position, ++turnTicks <= MAX_TURN_TICKS);
                            fixedTick(app);
                            TurnPlaybackSequence playback = (TurnPlaybackSequence) value(app, "turnPlaybackSequence");
                            if (playback.getPhase() == TurnPlaybackSequence.Phase.BATTLE
                                    && renderedBattleTurn != turn.getInt("turn")) {
                                renderedBattleTurn = turn.getInt("turn");
                                render(app);
                                if (turn.next == null) capture(app, prefix() + "-terminal-battle.png");
                            }
                            if (playback.isBlockingInput()) {
                                assertEquals("Outcome cannot outrun private battle playback",
                                        MatchOutcomeSequence.Phase.INACTIVE, outcome().getPhase());
                            }
                            boolean confirmed = state.getTurn() == turn.getInt("turn");
                            if (confirmed && !playback.isBlockingInput()
                                    && !((ConfirmTurnAnimation) value(app, "confirmTurnAnimation")).isPending()) {
                                assertEquals(TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
                                assertState(state, turn.get("state"));
                                if (turn.next != null) { turn = turn.next; stage = 1; }
                                else {
                                    assertEquals("The captured terminal combat must actually play",
                                            turn.getInt("turn"), renderedBattleTurn);
                                    assertEquals(victory ? GameState.MatchStatus.VICTORY
                                            : GameState.MatchStatus.DEFEAT, state.getMatchStatus());
                                    assertEquals(victory ? 31 : 29, state.getTurn());
                                    assertEquals(victory ? 250000L : 315456L, state.getFinalScore());
                                    if (victory) assertEquals(1, state.getFinalScoreBreakdown().getFlagBonusCount());
                                    finalScore = state.getFinalScore();
                                    assertEquals(MatchOutcomeSequence.Phase.BEFORE_ENDGAME, outcome().getPhase());
                                    outcomeTicks = 0; stage = 3;
                                }
                                break;
                            }
                        }
                        break;
                    case 3:
                        position = "terminal score tick " + outcomeTicks;
                        // Wait for managed endgame artwork without advancing its clock.
                        Object renderer = value(app, "endgameRenderer");
                        if (renderer == null || !(Boolean) renderer.getClass().getMethod("isReady").invoke(renderer)) break;
                        for (int i = 0; i < TICKS_PER_GL_CHUNK; i++) {
                            assertTrue("Bounded terminal score sequence", ++outcomeTicks <= MAX_TURN_TICKS);
                            fixedTick(app);
                            if (outcomeTicks == MatchOutcomeSequence.TICKS_BEFORE_ENDGAME) {
                                assertEquals(MatchOutcomeSequence.Phase.INTRO, outcome().getPhase());
                                capture(app, prefix() + "-intro.png");
                            }
                            if (outcomeTicks == MatchOutcomeSequence.TICKS_BEFORE_ENDGAME
                                    + MatchOutcomeSequence.TICKS_BEFORE_SCORE)
                                assertTrue("Source 72+48 tick handoff", outcome().isScoreStarted());
                            if (outcome().isComplete()) { stage = 4; break; }
                        }
                        break;
                    case 4:
                        assertEquals(finalScore, outcome().getFinalScore());
                        assertEquals(MatchOutcomePresentation.formatScore(finalScore), outcome().getScoreText());
                        capture(app, prefix() + "-score-complete.png");
                        Rectangle bounds = (Rectangle) value(app, "endgameContentBounds");
                        EndgameScreenLayout layout = new EndgameScreenLayout();
                        layout.fit(bounds.x, bounds.y, bounds.width, bounds.height);
                        tap(app, layout.x(553f), layout.y(465f));
                        assertNull("Actual New Game touch returns to setup", value(app, "game"));
                        assertEquals(MatchOutcomeSequence.Phase.INACTIVE, outcome().getPhase());
                        assertFalse("Fresh setup has no old rivals",
                                ((GameSetup) value(app, "setup")).canStart());
                        capture(app, prefix() + "-new-game.png");
                        complete = true;
                        break;
                    default: throw new AssertionError("Unexpected runner stage " + stage);
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                // Never recursively render a failed draw: that can replace the useful
                // first exception with a nested SpriteBatch.begin crash on the next frame.
                Gdx.app.error("LegalTerminalPlayback", "First failure: " + position, error);
                if (app != null) cleanFailedDrawing(app, error);
                complete = true;
            } finally {
                // The normal render after this Runnable is paint-only. The next chunk is
                // already queued, so real frame deltas cannot add uncontrolled gameplay ticks.
                try { if (app != null) field("skipNextFrameDelta").setBoolean(app, true); }
                catch (Throwable error) { failure.compareAndSet(null, error); complete = true; }
                if (complete) done.countDown();
                else if (!cancelled) Gdx.app.postRunnable(this);
            }
        }

        private String prefix() { return victory ? "legal-terminal-victory" : "legal-terminal-defeat"; }
        private MatchOutcomeSequence outcome() throws Exception { return (MatchOutcomeSequence) value(app, "outcomeSequence"); }

        private void prepareTurn() throws Exception {
            if (victory) {
                for (JsonValue control : turn.get("controls")) {
                    if (control.has("declinedPending")) { rejectOffer(null); continue; }
                    queue(control.getString("requested"));
                }
            } else {
                for (JsonValue rejected : turn.get("offersRejected")) rejectOffer(rejected);
                queue("RESOURCES"); queue("MASS_MEDIA");
            }
            assertNull("No unrecorded pending offer", state.getPendingHumanDiplomacyProposal());
            assertEquals("Two legally touched actions", 2, state.getQueuedActionCount());
        }

        private void rejectOffer(JsonValue rejected) throws Exception {
            GameState.DiplomacyProposal proposal = state.getPendingHumanDiplomacyProposal();
            assertNotNull("Recorded source offer", proposal);
            if (rejected != null) {
                assertEquals(rejected.get("sourceProposal").getInt("_leader"), proposal.proposerId);
                assertEquals(rejected.get("sourceProposal").getInt("_option"), proposal.optionIndex);
                assertEquals(rejected.getDouble("rngBefore"), state.createPresentationRandomCopy().nextDouble(), 0d);
            }
            render(app); sourceTap(app, 55f, 258f);
        }

        private void queue(String requested) throws Exception {
            String[] words = requested.split(" ");
            GameState.Action action = GameState.Action.valueOf(words[0]);
            begin(app, action);
            if (action == GameState.Action.RESEARCH) {
                String[] choices = (String[]) value(app, "researchChoiceByCard");
                Rectangle[] cards = (Rectangle[]) value(app, "technologyCards");
                int index = -1;
                for (int i = 0; i < choices.length; i++) if (words[1].equals(choices[i])) index = i;
                assertTrue("Visible source research choice " + requested, index >= 0);
                tap(app, cards[index]);
                assertTrue(state.isResearchTechnologySelected(words[1]));
            } else if (action == GameState.Action.PRODUCTION) {
                int index = -1;
                for (int i = 0; i < ProductionScreenLayout.sliderCount(); i++)
                    if (words[1].equals(ProductionScreenLayout.categoryKey(i))) index = i;
                assertTrue("Source production category", index >= 0);
                int percent = Integer.parseInt(words[2]);
                Rectangle handle = ProductionScreenLayout.setHandleBounds(index, percent,
                        (Rectangle) value(app, "modal"), new Rectangle());
                tap(app, handle);
                assertEquals("Actual slider touch", percent, state.getProductionPercent(words[1]));
            } else if (action == GameState.Action.ATTACK) {
                equipment(app, words[1]);
                Rectangle map = (Rectangle) value(app, "mapBounds");
                int player = Integer.parseInt(words[2]), city = Integer.parseInt(words[3]);
                tap(app, BattlefieldCityLayout.x(map, player, city), BattlefieldCityLayout.y(map, player, city));
            }
            render(app);
            int slot = state.getQueuedActionCount();
            if (action == GameState.Action.RESEARCH || action == GameState.Action.PRODUCTION
                    || action == GameState.Action.RESOURCES) tap(app, (Rectangle) value(app, "modalAccept"));
            else if (action == GameState.Action.MASS_MEDIA || action == GameState.Action.ATTACK)
                tap(app, ((Rectangle[]) value(app, "actionFooterButtons"))[2]);
            else sourceTap(app, 307f, 258f);
            assertEquals("Accepted physical slot: " + requested, action, state.getQueuedAction(slot));
        }
    }

    private static void begin(WorldDominationGame app, GameState.Action action) throws Exception {
        ScrollPaneState scroll = (ScrollPaneState) value(app, "navigationScroll");
        scroll.moveBy(-scroll.getOffset()); render(app);
        Rectangle viewport = (Rectangle) value(app, "navViewport");
        Rectangle row = ((Rectangle[]) value(app, "navRows"))[action.ordinal()];
        scroll.moveBy(viewport.y + viewport.height / 2 - (row.y + row.height / 2)); render(app);
        tap(app, ((Rectangle[]) value(app, "navRows"))[action.ordinal()]); render(app);
        assertEquals(action, ((GameState) value(app, "game")).getSelectedAction());
    }

    private static void equipment(WorldDominationGame app, String name) throws Exception {
        String[] names = (String[]) value(app, "EQUIPMENT_WEAPON_NAMES");
        int index = -1;
        for (int i = 0; i < names.length; i++) if (name.equals(names[i])) index = i;
        assertTrue("Known equipment " + name, index >= 0);
        ScrollPaneState scroll = (ScrollPaneState) value(app, "equipmentScroll");
        scroll.moveBy(-scroll.getOffset()); render(app);
        Rectangle viewport = (Rectangle) value(app, "equipmentViewport");
        Rectangle row = ((Rectangle[]) value(app, "equipmentRows"))[index];
        scroll.moveBy(viewport.y + viewport.height / 2 - (row.y + row.height / 2)); render(app);
        row = ((Rectangle[]) value(app, "equipmentRows"))[index];
        assertTrue("Equipment is within its viewport", viewport.contains(row.x + row.width / 2, row.y + row.height / 2));
        tap(app, row); render(app);
    }

    private static void sourceTap(WorldDominationGame app, float x, float y) throws Exception {
        Rectangle fit = SourceActionMenuRenderer.fit((Rectangle) value(app, "modal"), new Rectangle());
        tap(app, fit.x + x * fit.width / 365f, fit.y + fit.height - y * fit.height / 275f);
    }
    private static void tap(WorldDominationGame app, Rectangle rectangle) throws Exception {
        tap(app, rectangle.x + rectangle.width / 2, rectangle.y + rectangle.height / 2);
    }
    private static void tap(WorldDominationGame app, float x, float y) throws Exception {
        Vector2 screen = ((Viewport) value(app, "viewport")).project(new Vector2(x, y));
        int sx = Math.round(screen.x), sy = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(sx, sy, 0, 0); app.touchUp(sx, sy, 0, 0);
    }
    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object value(WorldDominationGame app, String name) throws Exception { return field(name).get(app); }
    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app);
    }
    private static void fixedTick(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true); method.invoke(app, 1f / 24f);
    }
    private static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true); app.render();
        assertEquals("No GL errors", GL20.GL_NO_ERROR, Gdx.gl.glGetError());
    }
    private static void cleanFailedDrawing(WorldDominationGame app, Throwable first) {
        try {
            SpriteBatch batch = (SpriteBatch) value(app, "batch");
            if (batch != null && batch.isDrawing()) batch.end();
        } catch (Throwable cleanup) { first.addSuppressed(cleanup); }
        try {
            ShapeRenderer shapes = (ShapeRenderer) value(app, "shapes");
            if (shapes != null && shapes.isDrawing()) shapes.end();
        } catch (Throwable cleanup) { first.addSuppressed(cleanup); }
        try { field("renderingFrame").setBoolean(app, false); }
        catch (Throwable cleanup) { first.addSuppressed(cleanup); }
    }
    private static void capture(WorldDominationGame app, String name) throws Exception {
        render(app);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight());
        try {
            com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(
                    new File(directory, name).getAbsolutePath());
            // OpenGL rows start at the bottom; PNG evidence must match the displayed image.
            PixmapIO.writePNG(output, image, -1, true);
            Pixmap decoded = new Pixmap(output);
            try {
                boolean asymmetric = false;
                for (int column = 1; column <= 3; column++) {
                    int x = image.getWidth() * column / 4;
                    for (int row = 1; row <= 3; row++) {
                        int y = image.getHeight() * row / 4;
                        int expected = image.getPixel(x, image.getHeight() - 1 - y);
                        assertEquals("Saved evidence must flip framebuffer rows exactly once",
                                expected, decoded.getPixel(x, y));
                        asymmetric |= expected != image.getPixel(x, y);
                    }
                }
                assertTrue("Evidence must distinguish top from bottom", asymmetric);
            } finally { decoded.dispose(); }
        } finally { image.dispose(); }
    }

    /** Same captured projection as the existing logical campaigns, including exact RNG. */
    private static void assertState(GameState game, JsonValue expected) {
        String where = "turn " + expected.getInt("turn") + " ";
        assertEquals(where, expected.getInt("turn"), game.getTurn());
        assertEquals(where + "canonical RNG", expected.getDouble("canonicalRngNext"),
                game.createPresentationRandomCopy().nextDouble(), 0d);
        String action = expected.get("humanAction").isNull() ? null : expected.getString("humanAction");
        if ("diplomacyPending".equals(action)) assertNotNull(game.getPendingHumanDiplomacyProposal());
        else { assertNull(where + "unexpected source action", action); assertNull(game.getSelectedAction());
            assertNull(where + "unexpected pending offer", game.getPendingHumanDiplomacyProposal()); }
        boolean[] captured = new boolean[5];
        for (JsonValue player : expected.get("players")) {
            int id = player.getInt("id");
            captured[id] = true;
            GameState.PlayerState actual = game.getPlayerState(id);
            String prefix = where + "player " + id + " ";
            assertEquals(prefix + "alive", player.getBoolean("alive"), actual.alive);
            assertEquals(prefix + "money", player.getInt("money"), actual.moneyMillions);
            double budget = player.getDouble("turnMoney");
            if (Double.isNaN(budget)) assertTrue(prefix + "invalid budget", actual.hasInvalidAiTurnMoney());
            else { assertFalse(prefix + "finite budget", actual.hasInvalidAiTurnMoney());
                assertEquals(prefix + "budget", (int) budget, actual.turnMoneyMillions); }
            assertEquals(prefix + "population", player.getInt("population"), actual.populationMillions);
            assertEquals(prefix + "religion", player.getInt("religion"), actual.religion);
            assertEquals(prefix + "score", player.getLong("score"), actual.score);
            for (int city = 0; city < 5; city++) assertEquals(prefix + "city " + city,
                    player.get("cityPopulations").getInt(city), actual.getCity(city).populationMillions);
            Map<String, Integer> stocks = new TreeMap<>(), nativeStocks = new TreeMap<>();
            for (JsonValue stock : player.get("stocks")) stocks.put(stock.name, stock.asInt());
            actual.getWeapons().forEach((name, weapon) -> {
                if (!"anti".equals(weapon.definition.type)) nativeStocks.put(name, weapon.inStock);
            });
            assertEquals(prefix + "stocks", stocks, nativeStocks);
            for (JsonValue stock : player.get("antiStocks"))
                assertEquals(prefix + stock.name, stock.asInt(), actual.getWeaponStockByName(stock.name));
            List<String> technologies = new ArrayList<>(), nativeTechnologies = new ArrayList<>(actual.getInventedTechnologies());
            for (JsonValue tech : player.get("inventedTech")) technologies.add(tech.asString());
            Collections.sort(nativeTechnologies);
            assertEquals(prefix + "technologies", technologies, nativeTechnologies);
            if (player.has("regard")) for (int other = 0; other < 5; other++) {
                assertEquals(prefix + "regard " + other, player.get("regard").getDouble(other), actual.getRegardTowardExact(other), 0d);
                JsonValue ceasefire = player.get("ceasefires").get(other);
                assertEquals(prefix + "ceasefire " + other, ceasefire.getBoolean("_status"), actual.hasCeasefireWith(other));
                assertEquals(prefix + "ceasefire turns " + other, ceasefire.getInt("_turnsLeft"), actual.getCeasefireTurnsWith(other));
            }
        }
        for (int id = 0; id < captured.length; id++) if (!captured[id])
            assertFalse(where + "unused player slot " + id, game.getPlayerState(id).alive);
    }
}
