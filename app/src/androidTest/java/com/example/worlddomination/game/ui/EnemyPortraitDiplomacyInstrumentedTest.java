package com.example.worlddomination.game.ui;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.testing.GameActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class EnemyPortraitDiplomacyInstrumentedTest {
    @Test public void tapsUseDrawnRivalCardsAndRespectGesturesAndOverlays() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    checkPortraits((WorldDominationGame) activity.getApplicationListener());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            }));
            assertTrue("Android GL thread did not finish the portrait tap test", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Enemy portrait tap routing", failure.get());
        }
    }

    private static void checkPortraits(WorldDominationGame view) throws Exception {
        GameSetup setup = new GameSetup(17L);
        assertTrue(setup.toggleOpponent(9));
        assertTrue(setup.toggleOpponent(14));
        GameState game = new GameState(setup);
        SplashScreenState splash = field(view, "splashState");
        splash.dismiss();
        Method activateGame = WorldDominationGame.class.getDeclaredMethod("activateGame", GameState.class);
        activateGame.setAccessible(true);
        activateGame.invoke(view, game);
        view.render();

        Rectangle[] cards = field(view, "portraitCards");
        assertTrue(cards[0].width > 0f);
        assertTrue(cards[1].width > 0f);
        assertEquals(0f, cards[2].width, 0f);
        SourceActionMenuState actionMenu = field(view, "sourceActionMenu");

        tap(view, field(view, "settingsButton"));
        GameSettingsMenu settings = field(view, "settingsMenu");
        assertTrue(settings.isOpen());
        tap(view, cards[1]);
        assertNull(game.getSelectedAction());
        tap(view, settings.close);
        assertFalse(settings.isOpen());

        float x = cards[1].x + cards[1].width / 2f;
        float y = cards[1].y + cards[1].height / 2f;
        Vector2 start = screen(view, x, y);
        Vector2 drag = screen(view, x + 30f, y);
        view.touchDown((int) start.x, (int) start.y, 0, 0);
        view.touchDragged((int) drag.x, (int) drag.y, 0);
        view.touchUp((int) start.x, (int) start.y, 0, 0);
        assertNull(game.getSelectedAction());

        tap(view, cards[1]);
        assertEquals(GameState.Action.DIPLOMACY, game.getSelectedAction());
        assertTrue(actionMenu.hasLeader());
        assertEquals(2, game.getSelectedTargetPlayer());
        assertEquals(14, game.getPlayerState(game.getSelectedTargetPlayer()).getLeaderId());

        assertTrue((boolean) field(view, "modalOpen"));
        tap(view, field(view, "actionMapButton"));
        assertFalse((boolean) field(view, "modalOpen"));
        tap(view, cards[0]);
        assertEquals(1, game.getSelectedTargetPlayer());
        assertFalse("Retargeting the same action keeps the map open", (boolean) field(view, "modalOpen"));

        tap(view, field(view, "helpButton"));
        SourceHelpState help = field(view, "helpState");
        assertTrue(help.isOpen());
        tap(view, cards[0]);
        assertFalse(help.isOpen());
        assertEquals(GameState.Action.DIPLOMACY, game.getSelectedAction());
        assertTrue(actionMenu.hasLeader());
        assertEquals(1, game.getSelectedTargetPlayer());

        assertTrue(view.keyDown(Input.Keys.BACK));
        assertTrue(game.submitDiplomacyProposal(1, 0, 3));
        tap(view, cards[1]);
        assertNull(game.getSelectedAction());
        assertNotNull(game.getPendingHumanDiplomacyProposal());
    }

    private static void tap(WorldDominationGame view, Rectangle bounds) throws Exception {
        Vector2 point = screen(view, bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f);
        view.touchDown((int) point.x, (int) point.y, 0, 0);
        view.touchUp((int) point.x, (int) point.y, 0, 0);
    }

    private static Vector2 screen(WorldDominationGame view, float x, float y) throws Exception {
        Viewport viewport = field(view, "viewport");
        Vector2 point = viewport.project(new Vector2(x, y));
        return point.set(Math.round(point.x), Gdx.graphics.getHeight() - Math.round(point.y));
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(WorldDominationGame view, String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(view);
    }
}
