package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

/** Real World callbacks must drive the physical building, including dead-unit pulses. */
@RunWith(AndroidJUnit4.class)
public final class SourceCarrierDefenseClockInstrumentedTest {
    @Test public void legalShipTurretMatchesAll483SourceHostStates() throws Exception {
        Oracle oracle = hostFrames();
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceBiologicalCarriersInstrumentedTest.gameReadyForShip(1);
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals(21, game.getTurn());
            @SuppressWarnings("unchecked")
            List<FlashActionBuildingAnimation> buildings =
                    (List<FlashActionBuildingAnimation>) field(WorldDominationGame.class,
                            "actionBuildingAnimations").get(app);
            FlashActionBuildingAnimation turret = null;
            for (FlashActionBuildingAnimation building : buildings)
                if (building.playerId == 1 && building.slot == 2) turret = building;
            assertNotNull("physical source ab1_2", turret);
            assertEquals("antiAir_fire", turret.frameName);
            assertEquals("chr", turret.religionCode);
            @SuppressWarnings("unchecked")
            List<SourceAirDefenseController> controllers =
                    (List<SourceAirDefenseController>) field(WorldDominationGame.class,
                            "sourceAirDefenseControllers").get(app);
            assertEquals("one physical shared turret", 1, controllers.size());
            @SuppressWarnings("unchecked")
            List<SourceAirDefenseController.UnitVisit> visits =
                    (List<SourceAirDefenseController.UnitVisit>) field(
                            SourceAirDefenseController.class, "units").get(controllers.get(0));
            assertEquals(3, visits.size());
            SourceAirDefenseController.UnitVisit[] units =
                    new SourceAirDefenseController.UnitVisit[3];
            for (SourceAirDefenseController.UnitVisit visit : visits)
                units[visit.animation.getRoute().planeIndex] = visit;
            Method step = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
            step.setAccessible(true);
            for (int tick = 0; tick < oracle.frames.length; tick++) {
                if (tick > 0) step.invoke(app, 1f / 24f);
                assertEquals("physical nested5553 at source host tick " + tick,
                        oracle.frames[tick], turret.getNestedFrameIndex(5553, 50) + 1);
                for (int unit = 0; unit < units.length; unit++) {
                    String context = "source tick " + tick + " unit" + (unit + 1);
                    SourceAntiDefensePlayback.UnitState state = units[unit].state;
                    assertEquals(context + " countdown", oracle.countdown[tick][unit], state.getCountdown());
                    assertEquals(context + " offset", oracle.offset[tick][unit], state.getOffset());
                    assertEquals(context + " raw bearing", oracle.bearing[tick][unit], state.getRawBearingDegrees());
                }
                if (tick >= 195) assertEquals("locked Unit raw angle includes final removed visit " + tick,
                        (double) oracle.bearing[tick][2], units[2].animation.getSourcePlayback().getAngle(), 0d);
                if (tick >= 270) assertEquals("stopped turret retained after final visit",
                        17, oracle.frames[tick]);
            }
            assertFalse("source scene handed off", cities.isBattleActive());
            assertTrue("private action buildings cleared at handoff", buildings.isEmpty());
        });
    }

    private static final class Oracle {
        final int[] frames = new int[483];
        final int[][] countdown = new int[483][3];
        final int[][] offset = new int[483][3];
        final int[][] bearing = new int[483][3];
    }

    private static Oracle hostFrames() throws Exception {
        Oracle oracle = new Oracle();
        int[] countdown = {50, 50, 50}, offset = new int[3], bearing = new int[3];
        int count = 0;
        int callbacks = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-anti-defense-host-clock.tsv"), StandardCharsets.UTF_8))) {
            assertTrue(reader.readLine().startsWith("kind\ttick\t"));
            for (String line; (line = reader.readLine()) != null;) {
                String[] c = line.split("\t", -1);
                if ("C".equals(c[0])) {
                    int unit = Integer.parseInt(c[2].substring(4)) - 1;
                    countdown[unit] = Integer.parseInt(c[17]);
                    offset[unit] = Integer.parseInt(c[18]);
                    bearing[unit] = Integer.parseInt(c[19]);
                    callbacks++;
                    continue;
                }
                assertEquals("H", c[0]);
                assertEquals("contiguous source host capture", count, Integer.parseInt(c[1]));
                oracle.frames[count] = Integer.parseInt(c[14]);
                oracle.countdown[count] = countdown.clone();
                oracle.offset[count] = offset.clone();
                oracle.bearing[count++] = bearing.clone();
            }
        }
        assertEquals(483, count);
        assertEquals(155, callbacks);
        return oracle;
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
}
