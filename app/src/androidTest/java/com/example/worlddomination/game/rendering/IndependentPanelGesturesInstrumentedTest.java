package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.ScrollPaneState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises panel gesture ownership through the real landscape viewport and input callbacks. */
@RunWith(AndroidJUnit4.class)
public final class IndependentPanelGesturesInstrumentedTest {
    @Test public void panelDragsStayIndependentAndNeverActivateRows() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    Method initialize = app.getClass().getDeclaredMethod("initializeGameplayAssets");
                    initialize.setAccessible(true);
                    initialize.invoke(app);
                    GameSetup setup = new GameSetup();
                    for (int leader = 0; leader < 4; leader++) setup.toggleOpponent(leader);
                    GameState game = new GameState(setup, 17L);
                    field("game").set(app, game);
                    ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    render(app);

                    ScrollPaneState navigation = (ScrollPaneState) field("navigationScroll").get(app);
                    ScrollPaneState equipment = (ScrollPaneState) field("equipmentScroll").get(app);
                    Rectangle left = (Rectangle) field("navViewport").get(app);
                    Rectangle right = (Rectangle) field("equipmentViewport").get(app);
                    Rectangle map = (Rectangle) field("mapBounds").get(app);
                    assertTrue("Navigation content must scroll", navigation.getLimit() > 0f);
                    assertTrue("Equipment content must scroll", equipment.getLimit() > 0f);

                    float leftX = left.x + left.width / 2f;
                    float rightX = right.x + right.width / 2f;
                    float leftY = left.y + left.height * 0.25f;
                    float rightY = right.y + right.height * 0.25f;
                    float leftTravel = Math.min(70f, left.height * 0.35f);
                    float rightTravel = Math.min(70f, right.height * 0.35f);

                    // A vertical navigation swipe changes only the navigation offset.
                    drag(app, leftX, leftY, leftX, leftY + leftTravel, leftX,
                            leftY + leftTravel);
                    assertTrue(navigation.getOffset() > 0f);
                    assertEquals(0f, equipment.getOffset(), 0.01f);
                    assertIdle(game, app);

                    // Once vertical dragging starts, crossing into equipment keeps the origin.
                    float oldLeft = navigation.getOffset();
                    drag(app, leftX, leftY, leftX, leftY + leftTravel / 2f, rightX,
                            leftY + leftTravel);
                    assertTrue(navigation.getOffset() > oldLeft);
                    assertEquals(0f, equipment.getOffset(), 0.01f);
                    assertIdle(game, app);

                    // Equipment swipes, including one ending over the battlefield, stay right-owned.
                    drag(app, rightX, rightY, rightX, rightY + rightTravel, rightX,
                            rightY + rightTravel);
                    assertTrue(equipment.getOffset() > 0f);
                    float oldRight = equipment.getOffset();
                    float beforeLeft = navigation.getOffset();
                    drag(app, rightX, rightY, rightX, rightY + rightTravel / 2f,
                            map.x + map.width / 2f, rightY + rightTravel);
                    assertTrue(equipment.getOffset() > oldRight);
                    assertEquals(beforeLeft, navigation.getOffset(), 0.01f);
                    assertIdle(game, app);

                    // Canceling a pending row tap does not select it; a new tap still works.
                    navigation.moveBy(-navigation.getOffset());
                    render(app);
                    Rectangle firstRow = ((Rectangle[]) field("navRows").get(app))[0];
                    float tapX = firstRow.x + firstRow.width / 2f;
                    float tapY = firstRow.y + firstRow.height / 2f;
                    assertTrue(left.contains(tapX, tapY));
                    int[] point = screen(app, tapX, tapY);
                    app.touchDown(point[0], point[1], 0, 0);
                    app.touchCancelled(point[0], point[1], 0, 0);
                    assertIdle(game, app);
                    app.touchDown(point[0], point[1], 0, 0);
                    app.touchUp(point[0], point[1], 0, 0);
                    assertEquals("A deliberate Resources tap must select its own row",
                            GameState.Action.RESOURCES, game.getSelectedAction());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            }));
            assertTrue("Panel gesture integration timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Panel gesture integration", failure.get());
        }
    }

    private static void drag(WorldDominationGame app, float startX, float startY,
                             float middleX, float middleY, float endX, float endY)
            throws Exception {
        int[] start = screen(app, startX, startY);
        int[] middle = screen(app, middleX, middleY);
        int[] end = screen(app, endX, endY);
        app.touchDown(start[0], start[1], 0, 0);
        app.touchDragged(middle[0], middle[1], 0);
        app.touchDragged(end[0], end[1], 0);
        app.touchUp(end[0], end[1], 0, 0);
    }

    private static int[] screen(WorldDominationGame app, float x, float y) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 point = viewport.project(new Vector2(x, y));
        return new int[]{Math.round(point.x), Gdx.graphics.getHeight() - Math.round(point.y)};
    }

    private static void assertIdle(GameState game, WorldDominationGame app) throws Exception {
        assertNull(game.getSelectedAction());
        assertEquals(0, game.getQueuedActionCount());
        assertEquals(-1, game.getSelectedIsland());
        assertEquals(-1, game.getSelectedMapCityOwner());
        assertEquals("", field("selectedEquipment").get(app));
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void render(WorldDominationGame app) throws Exception {
        field("skipNextFrameDelta").setBoolean(app, true);
        app.render();
    }
}
