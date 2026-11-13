package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Source city click cue fires for a hit, even when action eligibility rejects the city. */
@RunWith(AndroidJUnit4.class)
public final class CitySelectionAudioInstrumentedTest {
    @Test public void cityHitsRespectSourceCueTimingAndMute() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                Map<OriginalAudio.Cue, Sound> sounds = null;
                Sound original = null;
                OriginalAudio audio = null;
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    Method initialize = app.getClass().getDeclaredMethod("initializeGameplayAssets");
                    initialize.setAccessible(true);
                    initialize.invoke(app);
                    Object splash = field("splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);
                    audio = (OriginalAudio) field("audio").get(app);
                    Field soundsField = OriginalAudio.class.getDeclaredField("sounds");
                    soundsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<OriginalAudio.Cue, Sound> soundMap =
                            (Map<OriginalAudio.Cue, Sound>) soundsField.get(audio);
                    sounds = soundMap;
                    original = sounds.get(OriginalAudio.Cue.UNIT_SELECT);
                    AtomicInteger plays = new AtomicInteger();
                    Sound recording = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),
                            new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                                if ("play".equals(method.getName())) plays.incrementAndGet();
                                if (method.getReturnType() == long.class) return 1L;
                                if (method.getReturnType() == boolean.class) return false;
                                return null;
                            });
                    sounds.put(OriginalAudio.Cue.UNIT_SELECT, recording);

                    GameState game = newAttackSelection(app);
                    BattlefieldCityRenderer renderer =
                            (BattlefieldCityRenderer) field("cityRenderer").get(app);
                    Rectangle map = (Rectangle) field("mapBounds").get(app);
                    int[] own = cityPoint(app, renderer, game, map, 0);
                    int[] ceasefire = cityPoint(app, renderer, game, map, 1);
                    int[] eligible = cityPoint(app, renderer, game, map, 2);
                    int[] ocean = oceanPoint(app, renderer, game, map);
                    int initialTarget = game.getSelectedTargetPlayer();
                    int initialCity = game.getSelectedTargetCity();
                    int money = game.getTurnMoneyMillions();
                    double nextRandom = game.createPresentationRandomCopy().nextDouble();
                    int[] ceasefireTurns = ceasefireTurns(game.getHuman());
                    ceasefireTurns[1] = 2;
                    ceasefireTurns(game.getPlayerState(1))[0] = 2;
                    assertTrue(game.getHuman().hasCeasefireWith(1));
                    assertTrue(game.getPlayerState(1).hasCeasefireWith(0));

                    tap(app, own);
                    assertEquals("Own visible city still plays the source click", 1, plays.get());
                    assertRejected(game, app, initialTarget, initialCity, money, nextRandom);
                    tap(app, ceasefire);
                    assertEquals("Ceasefire city still plays the source click", 2, plays.get());
                    assertRejected(game, app, initialTarget, initialCity, money, nextRandom);
                    tap(app, ocean);
                    assertEquals("Water has no city click cue", 2, plays.get());
                    assertRejected(game, app, initialTarget, initialCity, money, nextRandom);

                    tap(app, eligible);
                    assertEquals("Eligible city plays one cue without a second acceptance cue",
                            3, plays.get());
                    assertEquals(2, game.getSelectedTargetPlayer());
                    assertEquals(0, game.getQueuedActionCount());
                    assertEquals(money, game.getTurnMoneyMillions());
                    assertEquals(nextRandom, game.createPresentationRandomCopy().nextDouble(), 0d);

                    assertFalse(audio.toggleSound());
                    GameState muted = newAttackSelection(app);
                    renderer = (BattlefieldCityRenderer) field("cityRenderer").get(app);
                    map = (Rectangle) field("mapBounds").get(app);
                    tap(app, cityPoint(app, renderer, muted, map, 0));
                    assertEquals("Muted rejected city is silent", 3, plays.get());
                    assertTrue(((SourceActionMenuState) field("sourceActionMenu").get(app))
                            .isMapSelection());
                    tap(app, cityPoint(app, renderer, muted, map, 2));
                    assertEquals("Muted accepted city is silent", 3, plays.get());
                    assertEquals(2, muted.getSelectedTargetPlayer());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    if (audio != null && !audio.isSoundEnabled()) audio.toggleSound();
                    if (sounds != null) {
                        if (original == null) sounds.remove(OriginalAudio.Cue.UNIT_SELECT);
                        else sounds.put(OriginalAudio.Cue.UNIT_SELECT, original);
                    }
                    done.countDown();
                }
            }));
            assertTrue("City audio integration timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("City selection audio", failure.get());
        }
    }

    private static GameState newAttackSelection(WorldDominationGame app) throws Exception {
        GameSetup setup = new GameSetup();
        for (int leader = 0; leader < 4; leader++) setup.toggleOpponent(leader);
        GameState game = new GameState(setup, 17L);
        GameState.WeaponState soldiers = game.getHuman().getWeapons().get("soldier1");
        soldiers.active = true;
        soldiers.inStock = 1;
        field("game").set(app, game);
        ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
        field("modalOpen").setBoolean(app, false);
        game.selectAction(GameState.Action.ATTACK);
        SourceActionMenuState menu = (SourceActionMenuState) field("sourceActionMenu").get(app);
        menu.begin(game);
        assertTrue(menu.chooseEquipment(game, "soldier1"));
        assertTrue(menu.isMapSelection());
        field("skipNextFrameDelta").setBoolean(app, true);
        app.render();
        return game;
    }

    private static int[] cityPoint(WorldDominationGame app, BattlefieldCityRenderer renderer,
                                   GameState game, Rectangle map, int player) throws Exception {
        OriginalCityAssets assets = (OriginalCityAssets) field("cityAssets").get(app);
        Rectangle bounds = new Rectangle();
        for (int cityIndex = 0; cityIndex < BattlefieldCityLayout.CITIES_PER_PLAYER; cityIndex++) {
            CityState city = game.getPlayerState(player).getCity(cityIndex);
            if (city == null || city.populationMillions <= 0) continue;
            OriginalCityAssets.Frame frame = assets.getFrame(
                    game.getPlayerState(player).getReligionCode(), city.getArtworkFrame());
            if (frame == null) continue;
            CitySpriteHitGeometry.setBounds(bounds, map, player, cityIndex,
                    frame.offsetX, frame.offsetY, frame.region.getRegionWidth(),
                    frame.region.getRegionHeight(), assets.getArtworkScale());
            int address = player * BattlefieldCityLayout.CITIES_PER_PLAYER + cityIndex;
            for (int row = 1; row < 10; row++) {
                for (int column = 1; column < 10; column++) {
                    float x = bounds.x + bounds.width * column / 10f;
                    float y = bounds.y + bounds.height * row / 10f;
                    if (map.contains(x, y) && renderer.hitTest(x, y, map, game) == address)
                        return screen(app, x, y);
                }
            }
        }
        throw new AssertionError("No tappable current city frame for player " + player);
    }

    private static int[] oceanPoint(WorldDominationGame app, BattlefieldCityRenderer renderer,
                                    GameState game, Rectangle map) throws Exception {
        for (int row = 1; row < 12; row++) {
            for (int column = 1; column < 12; column++) {
                float x = map.x + map.width * column / 12f;
                float y = map.y + map.height * row / 12f;
                if (renderer.hitTest(x, y, map, game) == -1) return screen(app, x, y);
            }
        }
        throw new AssertionError("No non-city map point found");
    }

    private static void assertRejected(GameState game, WorldDominationGame app,
                                       int target, int city, int money, double nextRandom)
            throws Exception {
        assertTrue(((SourceActionMenuState) field("sourceActionMenu").get(app))
                .isMapSelection());
        assertEquals(target, game.getSelectedTargetPlayer());
        assertEquals(city, game.getSelectedTargetCity());
        assertEquals(0, game.getQueuedActionCount());
        assertEquals(money, game.getTurnMoneyMillions());
        assertEquals(nextRandom, game.createPresentationRandomCopy().nextDouble(), 0d);
    }

    private static int[] ceasefireTurns(GameState.PlayerState player) throws Exception {
        Field field = player.getClass().getDeclaredField("ceasefireTurns");
        field.setAccessible(true);
        return (int[]) field.get(player);
    }

    private static void tap(WorldDominationGame app, int[] point) {
        app.touchDown(point[0], point[1], 0, 0);
        app.touchUp(point[0], point[1], 0, 0);
    }

    private static int[] screen(WorldDominationGame app, float x, float y) throws Exception {
        Viewport viewport = (Viewport) field("viewport").get(app);
        Vector2 point = viewport.project(new Vector2(x, y));
        return new int[]{Math.round(point.x), Gdx.graphics.getHeight() - Math.round(point.y)};
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
