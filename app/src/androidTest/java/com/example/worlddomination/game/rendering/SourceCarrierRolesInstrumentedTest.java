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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Full legal World visits against observer-safe Unit direct-clip captures. */
@RunWith(AndroidJUnit4.class)
public final class SourceCarrierRolesInstrumentedTest {
    private static final String HEADER = "tick,unit,role,attached,x,y,frame,"
            + "nestedFrame,visible,alpha,scaleX,scaleY";
    private static final int[][] DEPTH = {
            {22509, 22510, 22511, 22512},
            {800011, 800012, 800013, 800014},
            {800017, 800018, 800019, 800020}
    };
    private static final BattlefieldDisplayState.Kind[] KINDS = {
            BattlefieldDisplayState.Kind.UNIT_BODY,
            BattlefieldDisplayState.Kind.UNIT_SHADOW,
            BattlefieldDisplayState.Kind.UNIT_TINT,
            BattlefieldDisplayState.Kind.UNIT_DOT
    };
    private static final String[] ROLE_NAMES = {"unit", "shadow_unit", "tintunit",
            "targetDotunit"};

    @Test public void interceptedShipRolesMatchSourceThroughQueueCleanup()
            throws Exception {
        compare("intercept", 1, 481);
    }

    @Test public void successfulShipKeepsItsStoppedPaintUntilQueueCleanup()
            throws Exception {
        compare("success", 2, 564);
    }

    private static void compare(String scenario, int defender, int cleanupTick)
            throws Exception {
        String[][][][] source = rows("source-carrier-roles-" + scenario + ".csv",
                cleanupTick);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = SourceBiologicalCarriersInstrumentedTest.gameReadyForShip(defender);
            field(WorldDominationGame.class, "game").set(app, game);
            ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                    "cityRenderer").get(app)).reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("legal source ship turn", 21, game.getTurn());

            @SuppressWarnings("unchecked")
            List<SourceCarrierPlaneDisplayDepths> bindings =
                    (List<SourceCarrierPlaneDisplayDepths>) field(WorldDominationGame.class,
                            "sourceCarrierPlaneBindings").get(app);
            assertEquals("three source payload Unit wrappers", 3, bindings.size());
            SourceCarrierPlaneDisplayDepths[] physical = new SourceCarrierPlaneDisplayDepths[3];
            for (SourceCarrierPlaneDisplayDepths binding : bindings) {
                int index = binding.animation.getRoute().planeIndex;
                assertTrue(index >= 0 && index < 3);
                assertEquals(null, physical[index]);
                physical[index] = binding;
                assertEquals("attacker palette from source setup", .75f,
                        binding.animation.getTint().alphaMultiplier, 0f);
            }
            BattlefieldDisplayState display = (BattlefieldDisplayState)
                    field(WorldDominationGame.class, "battlefieldDisplay").get(app);
            int observations = 0;
            for (int tick = 0; tick <= cleanupTick; tick++) {
                if (tick > 0) advance(app);
                for (int unit = 0; unit < 3; unit++) {
                    SourceCarrierPlaneDisplayDepths binding = physical[unit];
                    assertNotNull(binding);
                    SourceCarrierPlanePlayback core = binding.animation.getSourcePlayback();
                    SourceCarrierPlanePlayback.RolePose[] poses = {
                            core.getBodyRole(), core.getShadowRole(),
                            core.getTintRole(), core.getDotRole()};
                    @SuppressWarnings("unchecked")
                    SourceDisplayList.Entry<BattlefieldDisplayState.Item>[] entries =
                            new SourceDisplayList.Entry[]{binding.body(), binding.shadow(),
                                    binding.tint(), binding.dot()};
                    for (int role = 0; role < 4; role++) {
                        String[] row = source[tick][unit][role];
                        assertNotNull("source role row", row);
                        String label = scenario + " t" + tick + " unit" + (unit + 1)
                                + " " + row[2];
                        SourceCarrierPlanePlayback.RolePose pose = poses[role];
                        SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = entries[role];
                        assertSame(label + " physical owner", binding, entry.getValue().owner);
                        assertEquals(label + " physical kind", KINDS[role],
                                entry.getValue().kind);
                        assertEquals(label + " physical name", ROLE_NAMES[role] + (unit + 1),
                                entry.getValue().name);
                        assertEquals(label + " physical depth", DEPTH[unit][role],
                                entry.getDepth());
                        boolean attached = Boolean.parseBoolean(row[3]);
                        assertEquals(label + " attached in core", attached, pose.isAttached());
                        assertEquals(label + " attached in display", attached, entry.isAttached());
                        assertEquals(label + " source X", Double.parseDouble(row[4]),
                                pose.getX(), 1e-8d);
                        assertEquals(label + " source Y", Double.parseDouble(row[5]),
                                pose.getY(), 1e-8d);
                        assertEquals(label + " authored frame", Integer.parseInt(row[6]),
                                pose.getFrame());
                        if (!row[7].isEmpty())
                            assertEquals(label + " nested shadow frame",
                                    Integer.parseInt(row[7]), pose.getNestedFrame());
                        assertEquals(label + " visible", Boolean.parseBoolean(row[8]),
                                pose.isVisible());
                        assertEquals(label + " alpha", Double.parseDouble(row[9]),
                                pose.getAlpha(), 1e-8d);
                        assertEquals(label + " X scale", Double.parseDouble(row[10]),
                                pose.getScale(), 1e-8d);
                        assertEquals(label + " Y scale", Double.parseDouble(row[11]),
                                pose.getScale(), 1e-8d);
                        observations++;
                    }
                }
                if (tick == cleanupTick - 1 && defender == 2) {
                    for (SourceCarrierPlaneDisplayDepths binding : physical) {
                        assertTrue("stopped successful body retained before cleanup",
                                binding.body().isAttached());
                        assertTrue(binding.shadow().isAttached());
                        assertTrue(binding.tint().isAttached());
                    }
                }
            }
            assertTrue("full World role observations", observations > 5700);
            for (SourceCarrierPlaneDisplayDepths binding : physical) {
                assertFalse("source cleanup detaches body", binding.body().isAttached());
                assertFalse("source cleanup detaches shadow", binding.shadow().isAttached());
                assertFalse("source cleanup detaches tint", binding.tint().isAttached());
                assertTrue("source dot remains until battle root discard",
                        binding.dot().isAttached());
            }
            assertTrue("root stays in source battle at cleanup boundary",
                    display.clips().size() > 0);
        });
    }

    private static String[][][][] rows(String name, int cleanupTick) throws Exception {
        String[][][][] result = new String[cleanupTick + 1][3][4][];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext()
                        .getAssets().open(name), StandardCharsets.UTF_8))) {
            assertEquals(HEADER, reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                assertEquals(12, values.length);
                int tick = Integer.parseInt(values[0]);
                if (tick > cleanupTick) continue;
                int unit = Integer.parseInt(values[1].substring(4)) - 1;
                int role;
                switch (values[2]) {
                    case "body": role = 0; break;
                    case "shadow": role = 1; break;
                    case "tint": role = 2; break;
                    case "dot": role = 3; break;
                    default: throw new IllegalArgumentException(values[2]);
                }
                assertEquals("one role observation", null, result[tick][unit][role]);
                result[tick][unit][role] = values;
            }
        }
        return result;
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

    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }
}
