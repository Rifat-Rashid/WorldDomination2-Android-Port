package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.ScrollPaneState;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Runs original menu controls through the real phone viewport and touch dispatcher. */
@RunWith(AndroidJUnit4.class)
public final class SourceActionMenusInstrumentedTest {
    @Test public void allNinePhoneNavigationRowsOpenTheirSourceViews() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L);
            setup.setDifficulty(1);
            setup.setPlayerColor(1);
            setup.setStartingBuilding(2);
            for (int leader = 0; leader < 4; leader++) assertTrue(setup.toggleOpponent(leader));
            GameState state = new GameState(setup);
            Method activate = WorldDominationGame.class.getDeclaredMethod("activateGame", GameState.class);
            activate.setAccessible(true);
            activate.invoke(app, state);
            double initialRandom = state.createPresentationRandomCopy().nextDouble();
            int initialFunds = state.getTurnMoneyMillions();
            Rectangle[] footer = (Rectangle[]) field("actionFooterButtons").get(app);
            Rectangle mapButton = (Rectangle) field("actionMapButton").get(app);
            GameState.Action[] rows = GameState.Action.navigationValues();
            assertEquals(9, rows.length);
            // Seed-zero RealTest phone center frames 2, 6, 10, 14, 55, 18, 66, 72 and 79.
            String[] sourceMenus = {null, null, null, "religion-chr", "attackSelect",
                    "preAttackSelect", "defenseSelect", "propaganda1", "diplomacy"};
            String[] mapCaptions = {"RESOURCES", "PRODUCTION", "RESEARCH", "MASS MEDIA", "ATTACK",
                    "PREATTACK", "DEFENSE", "PROPAGANDA", "DIPLOMACY"};
            for (GameState.Action action : rows) {
                begin(app, action);
                if (sourceMenus[action.ordinal()] != null) {
                    assertEquals(action.label, sourceMenus[action.ordinal()], menu(app).getKey());
                }
                boolean opensDetails = action == GameState.Action.RESOURCES
                        || action == GameState.Action.PRODUCTION || action == GameState.Action.RESEARCH
                        || action == GameState.Action.DIPLOMACY;
                assertEquals(action.label, opensDetails, field("modalOpen").getBoolean(app));
                String name = "nav-" + action.name().toLowerCase(java.util.Locale.ROOT);
                capture(app, name + "-entry.png");
                if (!opensDetails) {
                    tap(app, footer[0]);
                    assertTrue(action.label, field("modalOpen").getBoolean(app));
                    capture(app, name + "-details.png");
                }
                if (action != GameState.Action.RESOURCES) {
                    tap(app, mapButton);
                    assertFalse(action.label, field("modalOpen").getBoolean(app));
                    assertEquals(action, state.getSelectedAction());
                    render(app);
                    assertEquals(action.label, mapCaptions[action.ordinal()],
                            field("actionFooterText").get(app).toString());
                    if (action != GameState.Action.MASS_MEDIA) {
                        tap(app, footer[1]);
                        tap(app, footer[2]);
                        assertEquals("No authored callbacks on the empty " + action.label + " frame",
                                action, state.getSelectedAction());
                    }
                }
                assertEquals(0, state.getQueuedActionCount());
                assertEquals(initialFunds, state.getTurnMoneyMillions());
                assertEquals(initialRandom, state.createPresentationRandomCopy().nextDouble(), 0d);
            }
        });
    }

    @Test public void phoneMapAndDetailsKeepDraftsAndRouteFooterActions() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameState state = fresh(app);
                    Rectangle[] footer = (Rectangle[]) field("actionFooterButtons").get(app);
                    Rectangle mapButton = (Rectangle) field("actionMapButton").get(app);
                    int initialFunds = state.getTurnMoneyMillions();
                    double nextRandom = state.createPresentationRandomCopy().nextDouble();

                    begin(app, GameState.Action.MASS_MEDIA);
                    assertFalse(field("modalOpen").getBoolean(app));
                    byte[][] mapPixels = readBattlefieldMargins(app);
                    sourceTap(app, 307, 258);
                    assertEquals("An invisible dialog has no Accept hit target", 0,
                            state.getQueuedActionCount());
                    tap(app, footer[0]);
                    assertTrue(field("modalOpen").getBoolean(app));
                    byte[][] detailsPixels = readBattlefieldMargins(app);
                    assertFalse("DETAILS must cover the left island margin",
                            Arrays.equals(mapPixels[0], detailsPixels[0]));
                    assertFalse("DETAILS must cover the right island margin",
                            Arrays.equals(mapPixels[1], detailsPixels[1]));

                    TurnPlaybackSequence playback = (TurnPlaybackSequence) field("turnPlaybackSequence").get(app);
                    playback.begin(Collections.emptyList());
                    assertBattlefieldMargins("Turn playback takes priority over DETAILS", mapPixels, app);
                    playback.reset();
                    assertBattlefieldMargins("DETAILS returns after playback", detailsPixels, app);

                    tap(app, mapButton);
                    assertFalse(field("modalOpen").getBoolean(app));
                    assertEquals(GameState.Action.MASS_MEDIA, state.getSelectedAction());
                    assertBattlefieldMargins("MAP restores both battlefield margins", mapPixels, app);
                    tap(app, footer[2]);
                    assertEquals(GameState.Action.MASS_MEDIA, state.getQueuedAction(0));

                    begin(app, GameState.Action.RESEARCH);
                    assertTrue(field("modalOpen").getBoolean(app));
                    render(app);
                    Rectangle[] cards = (Rectangle[]) field("technologyCards").get(app);
                    String[] choices = (String[]) field("researchChoiceByCard").get(app);
                    int available = 0;
                    while (available < choices.length && (choices[available] == null
                            || !state.isResearchAvailable(choices[available]))) available++;
                    assertTrue(available < choices.length);
                    tap(app, cards[available]);
                    java.util.List<String> draft = state.getSelectedResearchTechnologies();
                    assertEquals(1, draft.size());
                    int cost = state.getSelectedResearchCostMillions();
                    assertTrue(cost > 0);
                    begin(app, GameState.Action.RESEARCH);
                    assertFalse(field("modalOpen").getBoolean(app));
                    tap(app, (Rectangle) field("modalAccept").get(app));
                    assertEquals(1, state.getQueuedActionCount());
                    assertEquals(draft, state.getSelectedResearchTechnologies());
                    assertEquals(initialFunds, state.getTurnMoneyMillions());

                    begin(app, GameState.Action.RESEARCH);
                    assertTrue(field("modalOpen").getBoolean(app));
                    tap(app, mapButton);
                    assertEquals(draft, state.getSelectedResearchTechnologies());
                    tap(app, footer[0]);
                    assertTrue(field("modalOpen").getBoolean(app));
                    assertEquals(draft, state.getSelectedResearchTechnologies());
                    tap(app, mapButton);
                    tap(app, footer[2]);
                    assertEquals(GameState.Action.RESEARCH, state.getQueuedAction(1));
                    assertEquals(initialFunds - cost, state.getTurnMoneyMillions());
                    assertEquals(nextRandom, state.createPresentationRandomCopy().nextDouble(), 0d);

                    state = fresh(app);
                    begin(app, GameState.Action.PRODUCTION);
                    assertTrue(field("modalOpen").getBoolean(app));
                    tap(app, (Rectangle) field("helpButton").get(app));
                    assertBattlefieldMargins("Help DETAILS uses the phone panel", detailsPixels, app);
                    BattlefieldFlagRenderer flags = (BattlefieldFlagRenderer) field("flagRenderer").get(app);
                    int flagFrame = flags.getFrameIndex();
                    Method fixedUpdate = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
                    fixedUpdate.setAccessible(true);
                    fixedUpdate.invoke(app, 1f / 24f);
                    assertNotEquals("The battlefield clock continues behind DETAILS", flagFrame, flags.getFrameIndex());
                    Rectangle helpMap = (Rectangle) field("helpMapButton").get(app);
                    tap(app, helpMap);
                    byte[][] helpMapPixels = readBattlefieldMargins(app);
                    assertFalse("Help MAP exposes the battlefield over the existing draft",
                            Arrays.deepEquals(detailsPixels, helpMapPixels));
                    tap(app, footer[0]);
                    assertBattlefieldMargins("Reopening Help DETAILS hides the battlefield", detailsPixels, app);
                    tap(app, helpMap);
                    assertBattlefieldMargins("Help MAP restores both battlefield margins", helpMapPixels, app);
                    tap(app, footer[1]);

                    state = fresh(app);
                    stock(state, "soldier1", 1);
                    begin(app, GameState.Action.ATTACK);
                    equipment(app, "soldier1");
                    assertFalse(field("modalOpen").getBoolean(app));
                    tap(app, footer[1]);
                    assertEquals(GameState.Action.ATTACK, state.getSelectedAction());
                    assertEquals("attack1", menu(app).getKey());
                    tap(app, footer[0]);
                    cityTap(app, 1, 0);
                    assertEquals("attack1", menu(app).getKey());
                    assertSelectionDrawerGlass(app);
                    tap(app, mapButton);
                    cityTap(app, 1, 0);
                    assertFalse(field("modalOpen").getBoolean(app));
                    assertEquals("attack2-soldier1", menu(app).getKey());
                    begin(app, GameState.Action.ATTACK);
                    assertTrue(field("modalOpen").getBoolean(app));
                    assertEquals(1, state.getSelectedTargetPlayer());
                    tap(app, mapButton);
                    tap(app, footer[1]);
                    assertNull(state.getSelectedAction());
                    assertEquals(1, state.getAvailableWeaponStock(0, "soldier1"));
                    assertEquals(0, state.getQueuedActionCount());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            }));
            assertTrue("Source phone action navigation timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Source phone action navigation", failure.get());
        }
    }

    @Test public void originalMenusSelectTargetsEquipmentAndQueueAllFiveActions() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    GameState state = fresh(app);
                    begin(app, GameState.Action.MASS_MEDIA);
                    assertEquals("religion-chr", menu(app).getKey());
                    assertEquals(65, state.getHuman().religion);
                    assertEquals(20, state.getMassMediaPreviewGain());
                    showDetails(app);
                    capture(app, "source-mass-media.png");
                    accept(app, state, GameState.Action.MASS_MEDIA);
                    state = fresh(app);
                    begin(app, GameState.Action.DIPLOMACY);
                    sourceTap(app, 50, 80); sourceTap(app, 70, 169);
                    assertTrue(menu(app).hasLeader()); assertTrue(menu(app).hasOption());
                    assertEquals(1, state.getSelectedTargetPlayer());
                    capture(app, "source-diplomacy.png"); accept(app, state, GameState.Action.DIPLOMACY);

                    state = fresh(app); begin(app, GameState.Action.PROPAGANDA);
                    assertEquals("propaganda1", menu(app).getKey());
                    showDetails(app);
                    assertSelectionDrawerGlass(app);
                    tap(app, (Rectangle) field("actionMapButton").get(app));
                    cityTap(app, 1, 2);
                    assertEquals("propaganda2", menu(app).getKey());
                    showDetails(app);
                    capture(app, "source-propaganda.png"); accept(app, state, GameState.Action.PROPAGANDA);

                    state = fresh(app); begin(app, GameState.Action.DEFENSE);
                    equipment(app, "antiAir"); assertEquals("defense-antiAir", menu(app).getKey());
                    showDetails(app);
                    capture(app, "source-defense.png"); accept(app, state, GameState.Action.DEFENSE);

                    state = fresh(app); stock(state, "soldier1", 1); begin(app, GameState.Action.ATTACK);
                    assertEquals("attackSelect", menu(app).getKey()); equipment(app, "soldier1");
                    assertEquals("attack1", menu(app).getKey()); cityTap(app, 1, 0);
                    assertEquals("attack2-soldier1", menu(app).getKey());
                    showDetails(app);
                    capture(app, "source-direct-attack.png"); accept(app, state, GameState.Action.ATTACK);

                    state = fresh(app); begin(app, GameState.Action.ATTACK); equipment(app, "spy1");
                    cityTap(app, 1, 0); showDetails(app); sourceTap(app, 313, 138);
                    assertEquals("factory", state.getSelectedSpyBuilding());
                    assertTrue(menu(app).getKey().startsWith("attack-spy-"));
                    capture(app, "source-spy.png");

                    state = fresh(app); stock(state, "rocket1", 1); stock(state, "warhead1", 1);
                    begin(app, GameState.Action.PREPARED_ATTACK); equipment(app, "rocket1");
                    assertEquals("preAttack-rocket1", menu(app).getKey());
                    showDetails(app);
                    capture(app, "source-prepared-step-one.png");
                    accept(app, state, GameState.Action.PREPARED_ATTACK);
                    state.selectAction(GameState.Action.RESOURCES); assertTrue(state.acceptSelectedAction());
                    state.confirmTurn();
                    while (state.getPendingHumanDiplomacyProposal() != null) assertTrue(state.respondToDiplomacyProposal(false));
                    begin(app, GameState.Action.PREPARED_ATTACK);
                    equipment(app, "warhead1"); assertEquals("preAttack4", menu(app).getKey());
                    showDetails(app);
                    assertSelectionDrawerGlass(app);
                    tap(app, (Rectangle) field("actionMapButton").get(app));
                    cityTap(app, 1, 1); assertEquals("preAttack3-warhead", menu(app).getKey());
                    showDetails(app);
                    capture(app, "source-prepared-step-two.png");
                    accept(app, state, GameState.Action.PREPARED_ATTACK);

                    state = fresh(app); stock(state, "soldier1", 2);
                    equipment(app, "soldier1");
                    assertEquals(GameState.Action.ATTACK, state.getSelectedAction());
                    assertEquals("attack1", menu(app).getKey());
                    equipment(app, "soldier1"); assertEquals("attackSelect", menu(app).getKey());
                    assertNull(menu(app).getSelectedEquipment());
                    assertEquals("", field("selectedEquipment").get(app));
                    app.keyDown(com.badlogic.gdx.Input.Keys.BACK);
                    assertNull(state.getSelectedAction());

                    state = fresh(app); stock(state, "ship", 1); stock(state, "warhead1", 1);
                    begin(app, GameState.Action.PREPARED_ATTACK); equipment(app, "ship");
                    accept(app, state, GameState.Action.PREPARED_ATTACK);
                    state.selectAction(GameState.Action.RESOURCES); assertTrue(state.acceptSelectedAction());
                    state.confirmTurn();
                    while (state.getPendingHumanDiplomacyProposal() != null) assertTrue(state.respondToDiplomacyProposal(false));
                    begin(app, GameState.Action.RESOURCES);
                    Rectangle resourceAccept = (Rectangle) field("modalAccept").get(app);
                    tap(app, resourceAccept.x + resourceAccept.width / 2,
                            resourceAccept.y + resourceAccept.height / 2);
                    assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
                    assertNull(state.getQueuedAction(1));
                    int shipTurnFunds = state.getHuman().moneyMillions;
                    int shipTurnBudget = state.getHuman().turnMoneyMillions;
                    // Match the source callback diagnostic's injection boundaries;
                    // this exercises UI settlement, not legal stock acquisition.
                    stock(state, "warhead1", 2);
                    begin(app, GameState.Action.PREPARED_ATTACK); equipment(app, "warhead1");
                    assertEquals("preAttack4-ship", menu(app).getKey());
                    showDetails(app);
                    assertSelectionDrawerGlass(app);
                    tap(app, (Rectangle) field("actionMapButton").get(app));
                    cityTap(app, 1, 0);
                    assertEquals("ship1-1", menu(app).getKey()); assertTrue(menu(app).isAcceptVisible());
                    showDetails(app);
                    capture(app, "source-ship-one-payload.png");
                    sourceTap(app, 307, 258);
                    assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
                    assertEquals(GameState.Action.PREPARED_ATTACK, state.getQueuedAction(1));
                    assertEquals(1, state.getQueuedShipStrikeCount(1));
                    assertEquals(1, state.getQueuedShipStrikePlayer(1, 0));
                    assertEquals(0, state.getQueuedShipStrikeCity(1, 0));
                    assertEquals(1, state.getAvailableWeaponStock(0, "warhead1"));
                    assertEquals("ship", state.getPreparedCarrierName(0));
                    assertEquals(0, state.getWeaponStock(0, "ship"));
                    assertEquals(2, state.getWeaponStock(0, "warhead1"));
                    assertEquals(shipTurnFunds, state.getHuman().moneyMillions);
                    assertEquals(shipTurnBudget, state.getHuman().turnMoneyMillions);

                    // Use the real viewport/tube hit path, not removeQueuedAction.
                    render(app);
                    Rectangle secondTube = ((Rectangle[]) field("actionSlots").get(app))[1];
                    tap(app, secondTube.x + secondTube.width / 2,
                            secondTube.y + secondTube.height / 2);
                    assertEquals(1, state.getQueuedActionCount());
                    assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
                    assertNull(state.getQueuedAction(1));
                    assertEquals(1, state.getNextActionSlot());
                    assertEquals("ship", state.getPreparedCarrierName(0));
                    assertEquals(0, state.getWeaponStock(0, "ship"));
                    assertEquals(0, state.getSelectedShipStrikeCount());
                    assertEquals(0, state.getQueuedShipStrikeCount(1));
                    assertEquals(2, state.getWeaponStock(0, "warhead1"));
                    assertEquals(2, state.getAvailableWeaponStock(0, "warhead1"));
                    assertEquals(shipTurnFunds, state.getHuman().moneyMillions);
                    assertEquals(shipTurnBudget, state.getHuman().turnMoneyMillions);

                    begin(app, GameState.Action.PREPARED_ATTACK);
                    stock(state, "warhead2", 1);
                    equipment(app, "warhead2"); cityTap(app, 2, 0);
                    assertEquals("ship1-1", menu(app).getKey());
                    assertTrue(menu(app).isAcceptVisible());
                    assertEquals(2, state.getSelectedShipTargetPlayerId());
                    showDetails(app);
                    sourceTap(app, 307, 258);
                    assertEquals(2, state.getQueuedActionCount());
                    assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
                    assertEquals(GameState.Action.PREPARED_ATTACK, state.getQueuedAction(1));
                    assertEquals(1, state.getQueuedShipStrikeCount(1));
                    assertEquals(2, state.getQueuedShipStrikePlayer(1, 0));
                    assertEquals(0, state.getQueuedShipStrikeCity(1, 0));
                    assertEquals("ship", state.getPreparedCarrierName(0));
                    assertEquals(0, state.getWeaponStock(0, "ship"));
                    assertEquals(2, state.getAvailableWeaponStock(0, "warhead1"));
                    assertEquals(1, state.getWeaponStock(0, "warhead2"));
                    assertEquals(0, state.getAvailableWeaponStock(0, "warhead2"));
                    assertEquals(shipTurnFunds, state.getHuman().moneyMillions);
                    assertEquals(shipTurnBudget, state.getHuman().turnMoneyMillions);
                    // Let the real presentation clock reveal both accepted labels.
                    Method tick = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
                    tick.setAccessible(true);
                    for (int frame = 0; frame < 11; frame++) tick.invoke(app, 1f / 24f);
                    com.example.worlddomination.game.ui.ActionSlotAnimation tubes =
                            (com.example.worlddomination.game.ui.ActionSlotAnimation)
                                    field("actionSlotAnimation").get(app);
                    assertEquals(GameState.Action.RESOURCES, tubes.getDisplayedAction(0));
                    assertEquals(GameState.Action.PREPARED_ATTACK, tubes.getDisplayedAction(1));
                    assertEquals(1f, tubes.getLabelAlpha(0), 0f);
                    assertEquals(1f, tubes.getLabelAlpha(1), 0f);
                    capture(app, "source-ship-tube-reselected.png");

                    state = fresh(app); begin(app, GameState.Action.DIPLOMACY);
                    state.getHuman().turnMoneyMillions = 0;
                    render(app);
                    assertEquals("Budget: $ 0", renderedActionText(app, "textFunds"));
                    assertEquals(0, state.getHuman().turnMoneyMillions);
                    capture(app, "source-diplomacy-zero-budget.png");
                    state.getHuman().turnMoneyMillions = -10;
                    render(app);
                    assertEquals("Budget: $ 0", renderedActionText(app, "textFunds"));
                    assertEquals(-10, state.getHuman().turnMoneyMillions);
                    state.getHuman().turnMoneyMillions = 0;
                    sourceTap(app, 50, 80); sourceTap(app, 70, 169);
                    assertEquals("diplomacy_error", menu(app).getKey()); assertFalse(menu(app).hasOption());
                    capture(app, "source-diplomacy-unaffordable-option.png");
                    sourceControlTap(app, "diplomacy_error", "error_window/btnOK");
                    assertEquals("diplomacy", menu(app).getKey()); assertTrue(menu(app).hasLeader());
                    app.keyDown(com.badlogic.gdx.Input.Keys.BACK); assertNull(state.getSelectedAction());

                    state = fresh(app); assertTrue(state.submitDiplomacyProposal(1, 0, 0));
                    capture(app, "source-diplomacy-offer.png"); sourceTap(app, 307, 258);
                    assertNull(state.getPendingHumanDiplomacyProposal());
                    assertFalse("The source settles the answer on the next turn",
                            state.getHuman().hasCeasefireWith(1));
                    state = fresh(app); assertTrue(state.submitDiplomacyProposal(1, 0, 3));
                    state.getHuman().turnMoneyMillions = 0;
                    capture(app, "source-diplomacy-demand.png"); sourceTap(app, 307, 258);
                    assertTrue(menu(app).hasPendingError());
                    capture(app, "source-diplomacy-warning.png");
                    sourceTap(app, 307, 258); assertTrue(menu(app).hasPendingError());
                    sourceControlTap(app, "diplomacy_error", "error_window/btnOK");
                    assertFalse(menu(app).hasPendingError());
                    sourceTap(app, 55, 258); assertNull(state.getPendingHumanDiplomacyProposal());
                } catch (Throwable error) {
                    failure.set(error);
                    android.util.Log.e("SourceMenuTest", "Original failure", error);
                    try {
                        WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                        com.badlogic.gdx.graphics.g2d.SpriteBatch batch =
                                (com.badlogic.gdx.graphics.g2d.SpriteBatch) field("batch").get(app);
                        if (batch.isDrawing()) batch.end();
                        field("game").set(app, null);
                    } catch (Exception cleanup) { error.addSuppressed(cleanup); }
                }
                finally { done.countDown(); }
            }));
            assertTrue("Source menu integration timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Original action menu integration", failure.get());
        }
    }
    private static GameState fresh(WorldDominationGame app) throws Exception {
        GameSetup setup = new GameSetup();
        for (int leader = 0; leader < 4; leader++) {
            setup.toggleOpponent(leader);
        }
        GameState state = new GameState(setup, 17L);
        field("game").set(app, state); field("modalOpen").setBoolean(app, false);
        // Match the production new-game path before dispatching touches to visible frames.
        ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(state);
        return state;
    }
    private static void stock(GameState game, String name, int count) {
        GameState.WeaponState stock = game.getHuman().getWeapons().get(name);
        stock.active = true; stock.inStock = count;
    }
    private static SourceActionMenuState menu(WorldDominationGame app) throws Exception {
        return (SourceActionMenuState) field("sourceActionMenu").get(app);
    }
    private static String renderedActionText(WorldDominationGame app, String name) throws Exception {
        SourceActionMenuRenderer renderer = (SourceActionMenuRenderer)
                field("sourceActionMenuRenderer").get(app);
        Field fields = SourceActionMenuRenderer.class.getDeclaredField("fields");
        fields.setAccessible(true);
        for (Object item : (Object[]) fields.get(renderer)) {
            Field source = item.getClass().getDeclaredField("source");
            source.setAccessible(true);
            if (!name.equals(((JsonValue) source.get(item)).getString("name"))) continue;
            Field glyph = item.getClass().getDeclaredField("glyph");
            glyph.setAccessible(true);
            StringBuilder result = new StringBuilder();
            for (GlyphLayout.GlyphRun run : ((GlyphLayout) glyph.get(item)).runs)
                for (BitmapFont.Glyph character : run.glyphs)
                    result.append((char) character.id);
            return result.toString();
        }
        throw new AssertionError("Missing rendered source field " + name);
    }
    private static void begin(WorldDominationGame app, GameState.Action action) throws Exception {
        ScrollPaneState scroll = (ScrollPaneState) field("navigationScroll").get(app);
        scroll.moveBy(-scroll.getOffset()); render(app);
        Rectangle viewport = (Rectangle) field("navViewport").get(app);
        Rectangle row = ((Rectangle[]) field("navRows").get(app))[action.ordinal()];
        scroll.moveBy(viewport.y + viewport.height / 2 - (row.y + row.height / 2));
        render(app); row = ((Rectangle[]) field("navRows").get(app))[action.ordinal()];
        tap(app, row.x + row.width / 2, row.y + row.height / 2);
        assertEquals(action, ((GameState) field("game").get(app)).getSelectedAction());
    }
    private static void equipment(WorldDominationGame app, String name) throws Exception {
        String[] names = (String[]) field("EQUIPMENT_WEAPON_NAMES").get(null);
        int index = -1;
        for (int i = 0; i < names.length; i++) if (name.equals(names[i])) index = i;
        assertTrue(index >= 0);
        ScrollPaneState scroll = (ScrollPaneState) field("equipmentScroll").get(app);
        scroll.moveBy(-scroll.getOffset()); render(app);
        Rectangle viewport = (Rectangle) field("equipmentViewport").get(app);
        Rectangle row = ((Rectangle[]) field("equipmentRows").get(app))[index];
        scroll.moveBy(viewport.y + viewport.height / 2 - (row.y + row.height / 2));
        render(app); row = ((Rectangle[]) field("equipmentRows").get(app))[index];
        assertTrue(viewport.contains(row.x + row.width / 2, row.y + row.height / 2));
        tap(app, row.x + row.width / 2, row.y + row.height / 2);
    }
    private static void cityTap(WorldDominationGame app, int player, int city) throws Exception {
        Rectangle map = (Rectangle) field("mapBounds").get(app);
        tap(app, BattlefieldCityLayout.x(map, player, city), BattlefieldCityLayout.y(map, player, city));
    }
    private static void sourceControlTap(WorldDominationGame app, String key, String name) throws Exception {
        com.badlogic.gdx.utils.JsonValue controls = new com.badlogic.gdx.utils.JsonReader()
                .parse(Gdx.files.internal("original/ui/action-menus/menus.json")).get(key).get("controls");
        for (com.badlogic.gdx.utils.JsonValue control : controls) {
            if (!control.getString("name").equals(name)) continue;
            com.badlogic.gdx.utils.JsonValue bounds = control.get("bounds");
            sourceTap(app, bounds.getFloat(0) + bounds.getFloat(2) / 2,
                    bounds.getFloat(1) + bounds.getFloat(3) / 2);
            return;
        }
        fail("Missing source control " + name);
    }
    private static void sourceTap(WorldDominationGame app, float x, float y) throws Exception {
        Rectangle modal = (Rectangle) field("modal").get(app);
        Rectangle fit = SourceActionMenuRenderer.fit(modal, new Rectangle());
        tap(app, fit.x + x * fit.width / 365, fit.y + fit.height - y * fit.height / 275);
    }
    private static void accept(WorldDominationGame app, GameState game, GameState.Action action) throws Exception {
        if (!field("modalOpen").getBoolean(app)) showDetails(app);
        sourceTap(app, 307, 258); assertEquals(action, game.getQueuedAction(0));
    }
    private static void showDetails(WorldDominationGame app) throws Exception {
        tap(app, ((Rectangle[]) field("actionFooterButtons").get(app))[0]);
        assertTrue(field("modalOpen").getBoolean(app));
    }
    private static void tap(WorldDominationGame app, float x, float y) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 screen = viewport.project(new Vector2(x, y));
        int sx = Math.round(screen.x), sy = Gdx.graphics.getHeight() - Math.round(screen.y);
        app.touchDown(sx, sy, 0, 0); app.touchUp(sx, sy, 0, 0);
    }
    private static void tap(WorldDominationGame app, Rectangle bounds) throws Exception {
        tap(app, bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f);
    }
    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(app);
    }
    private static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true); app.render();
    }

    private static void assertBattlefieldMargins(String message, byte[][] expected, WorldDominationGame app)
            throws Exception {
        byte[][] pixels = readBattlefieldMargins(app);
        assertArrayEquals(message + " (left)", expected[0], pixels[0]);
        assertArrayEquals(message + " (right)", expected[1], pixels[1]);
    }

    private static byte[][] readBattlefieldMargins(WorldDominationGame app) throws Exception {
        render(app);
        Rectangle map = (Rectangle) field("mapBounds").get(app);
        Viewport viewport = (Viewport) field("viewport").get(app);
        // Read inside the battlefield but outside the authored drawer on either side.
        Vector2 left = viewport.project(new Vector2(map.x + 0.05f * map.width, map.y + 0.68f * map.height));
        Vector2 right = viewport.project(new Vector2(map.x + 0.95f * map.width, map.y + 0.32f * map.height));
        return new byte[][]{
                ScreenUtils.getFrameBufferPixels(Math.round(left.x) - 4, Math.round(left.y) - 4, 8, 8, false),
                ScreenUtils.getFrameBufferPixels(Math.round(right.x) - 4, Math.round(right.y) - 4, 8, 8, false)
        };
    }

    private static void assertSelectionDrawerGlass(WorldDominationGame app) throws Exception {
        render(app);
        String frame = menu(app).getKey();
        Rectangle fit = SourceActionMenuRenderer.fit((Rectangle) field("modal").get(app), new Rectangle());
        Viewport viewport = (Viewport) field("viewport").get(app);
        double rim = drawerBrightness(viewport, fit, 4, 8);
        double interior = drawerBrightness(viewport, fit, 43, 58);

        // The source phone crop gains 57.7 RGB levels across the rim and zero
        // in the body when the authored HUD layers are rendered over these frames.
        Method drawBackdrop = WorldDominationGame.class.getDeclaredMethod("drawPanelsAndMap", boolean.class);
        drawBackdrop.setAccessible(true);
        drawBackdrop.invoke(app, false);
        double rimIncrease = rim - drawerBrightness(viewport, fit, 4, 8);
        assertTrue(frame + " should have a bright glass rim: " + rimIncrease, rimIncrease >= 25);
        assertEquals(frame + " should have a clear body", interior, drawerBrightness(viewport, fit, 43, 58), 2);
        render(app);
    }

    private static double drawerBrightness(Viewport viewport, Rectangle fit, int top, int bottom) {
        float scale = fit.width / 365f;
        Vector2 lower = viewport.project(new Vector2(fit.x + 95 * scale, fit.y + fit.height - bottom * scale));
        Vector2 upper = viewport.project(new Vector2(fit.x + 250 * scale, fit.y + fit.height - top * scale));
        int x = Math.round(lower.x), y = Math.round(lower.y);
        byte[] rgba = ScreenUtils.getFrameBufferPixels(x, y,
                Math.round(upper.x) - x, Math.round(upper.y) - y, false);
        long sum = 0;
        for (int i = 0; i < rgba.length; i += 4) {
            sum += (rgba[i] & 255) + (rgba[i + 1] & 255) + (rgba[i + 2] & 255);
        }
        return sum / (3d * (rgba.length / 4));
    }

    private static void capture(WorldDominationGame app, String name) throws Exception {
        render(app);
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true));
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        try { PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image); }
        finally { image.dispose(); }
    }
}
