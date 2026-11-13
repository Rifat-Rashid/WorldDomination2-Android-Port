package com.example.worlddomination.game.rendering;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.badlogic.gdx.Gdx;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Confirms two real ship records reach the live battle playback as separate groups. */
@RunWith(AndroidJUnit4.class)
public final class CarrierSortieInstrumentedTest {
    @Test public void humanAndAiShipRecordsKeepIndependentLaunches() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                try {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    invoke(app, "initializeGameplayAssets");
                    Object splash = field(WorldDominationGame.class, "splashState").get(app);
                    splash.getClass().getMethod("dismiss").invoke(splash);

                    GameState state = readyTwoShipGame();
                    field(WorldDominationGame.class, "game").set(app, state);
                    field(WorldDominationGame.class, "modalOpen").setBoolean(app, false);
                    ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                            "cityRenderer").get(app)).reset(state);
                    invoke(app, "confirmTurnAndPreparePlayback");

                    List<GameState.CombatEvent> events =
                            state.getLastTurnCombatEventsInSourcePresentationOrder();
                    @SuppressWarnings("unchecked")
                    List<FlashCarrierPlaneAnimation> animations =
                            (List<FlashCarrierPlaneAnimation>) field(WorldDominationGame.class,
                                    "carrierPlaneAnimations").get(app);
                    assertEquals("Two ship records must produce five actual payload outcomes", 5,
                            events.size());
                    assertEquals(5, animations.size());
                    // Physical source spawn construction is reversed; activation uses record ordinals.
                    int[] attackers = {1, 1, 1, 0, 0};
                    int[] payloadIndices = {0, 1, 2, 0, 1};
                    float[] expectedX = {353.95f, 316.95f, 287.95f, 458.5f, 421.5f};
                    float[] expectedY = {68f, 91f, 105f, 68.75f, 91.75f};
                    int[] delays = new int[5];
                    for (int i = 0; i < events.size(); i++) {
                        GameState.CombatEvent event = events.get(i);
                        assertEquals("Record owner at payload " + i,
                                attackers[i], event.attackerPlayerId);
                        assertEquals("Source-local plane index at payload " + i,
                                payloadIndices[i], event.shipPayloadIndex);
                        assertTrue(FlashBattleRoutes.supportsCarrierPlaneStrike(event));
                        FlashCarrierPlaneAnimation animation = animations.get(i);
                        FlashBattleRoutes.CarrierPlaneStrike route =
                                (FlashBattleRoutes.CarrierPlaneStrike) field(
                                        FlashCarrierPlaneAnimation.class, "route").get(animation);
                        assertEquals(payloadIndices[i], route.planeIndex);
                        assertEquals(expectedX[i], route.start.x, 0.001f);
                        assertEquals(expectedY[i], route.start.y, 0.001f);
                        delays[i] = field(FlashCarrierPlaneAnimation.class,
                                "delayTicks").getInt(animation);
                    }
                    assertEquals(delays[0], delays[1]);
                    assertEquals(delays[0], delays[2]);
                    assertEquals(delays[3], delays[4]);
                    assertEquals("Human resource record precedes its battle record", 2, delays[3]);
                    assertEquals("AI battle follows the human record on the next source tick", 3,
                            delays[0]);
                    assertTrue("Human activates before AI despite reverse construction order",
                            delays[3] < delays[2]);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            }));
            assertTrue("Carrier integration timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Carrier integration", failure.get());
        }
    }

    private static GameState readyTwoShipGame() throws Exception {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameSetup setup = new GameSetup(17L);
            setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0));
            GameState candidate = new GameState(setup, seed);
            GameState.PlayerState opponent = candidate.getPlayerState(1);
            if (!opponent.isSpecialPerson() && !opponent.isSuicidePerson()) {
                state = candidate;
                break;
            }
        }
        assertNotNull("Seed sweep must find a regular opponent", state);
        GameState.PlayerState opponent = state.getPlayerState(1);
        opponent.moneyMillions = 0;
        opponent.turnMoneyMillions = 0;
        opponent.religion = 100;
        for (GameState.WeaponState weapon : opponent.getWeapons().values()) {
            weapon.inStock = 0;
        }
        stock(opponent, "warhead1", 2);
        stock(opponent, "bioload1", 1);
        stock(opponent, "ship", 0);
        setPreparedCarrier(opponent);

        GameState.PlayerState human = state.getHuman();
        stock(human, "ship", 1);
        stock(human, "warhead1", 2);
        setPreparedCarrier(human);
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedShipStrike(1, 0, "warhead1"));
        assertTrue(state.configurePreparedShipStrike(1, 1, "warhead1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        return state;
    }

    private static void stock(GameState.PlayerState player, String name, int count) {
        GameState.WeaponState weapon = player.getWeapons().get(name);
        assertNotNull(name, weapon);
        weapon.active = true;
        weapon.inStock = count;
    }

    private static void setPreparedCarrier(GameState.PlayerState player) throws Exception {
        field(GameState.PlayerState.class, "preparedCarrierName").set(player, "ship");
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }
}
