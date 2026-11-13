package com.example.worlddomination.game.rendering;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks source alias-probe traces, including Flash's case-insensitive movie-clip linkage.
 * The reference host resets the child offset on a backward frame seek; these traces
 * establish that observed behavior, not a claim about original Flash pixels.
 * Existing game-state golden traces remain independent of this presentation test.
 */
public final class FlashChinookShadowTraceTest {
    @Test public void northEastShadowMatchesEverySourceRow() throws Exception {
        verify(2, 0);
    }

    @Test public void northWestShadowMatchesEverySourceRow() throws Exception {
        verify(1, 2);
    }

    @Test public void southWestShadowMatchesEverySourceRow() throws Exception {
        verify(3, 3);
    }

    @Test public void southEastShadowMatchesEverySourceRow() throws Exception {
        verify(4, 1);
    }

    private static void verify(int target, int city) throws Exception {
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(
                FlashBattleRoutes.createSoldierThreeAttack(0, target, city, 2,
                        "green", loadAnchors()), 0);
        String resource = "/source/chinook-shadow-target" + target + "-city" + city + ".csv";
        InputStream input = FlashChinookShadowTraceTest.class.getResourceAsStream(resource);
        assertNotNull("Missing source shadow trace " + resource, input);
        int rows = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull("Empty shadow trace " + resource, header);
            Map<String, Integer> columns = columns(header);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] row = line.split(",", -1);
                rows++;
                int activeTick = integer(columns, row, "active_tick");
                assertEquals("Sequential active ticks", rows, activeTick);
                assertEquals("Sequential global ticks", 197 + rows,
                        integer(columns, row, "global_tick"));
                animation.tick();

                FlashChinookTankAnimation.Actor carrier = animation.getActor(0);
                FlashChinookTankAnimation.Actor base = animation.getActor(1);
                FlashChinookTankAnimation.Actor shadow = animation.getChinookShadow();
                String at = resource + " tick " + rows;
                assertEquals(at + " base frame", integer(columns, row, "base_frame") - 1,
                        base.sourceFrame);
                assertEquals(at + " base X", number(columns, row, "base_x"),
                        base.sourceX, .0001f);
                assertEquals(at + " base Y", number(columns, row, "base_y"),
                        base.sourceY, .0001f);
                int baseSymbol = integer(columns, row, "base_symbol");
                if (baseSymbol == 714) {
                    assertEquals(at + " predeployment body", "tank_noshade", base.linkage);
                    assertFalse(at + " is still attached", animation.isTankDeployed());
                    assertEquals(at + " nested child Y", number(columns, row, "shadow_y"),
                            -3.15f + animation.getTankShadowOffsetY(), .0001f);
                    assertEquals(at + " nested shadow visibility",
                            Boolean.parseBoolean(value(columns, row, "shadow_visible")),
                            animation.isTankShadowVisible());
                } else {
                    assertEquals(at + " deployed body ID", 3242, baseSymbol);
                    assertEquals(at + " deployed body", "tankBase", base.linkage);
                    assertTrue(at + " deployed", animation.isTankDeployed());
                    assertFalse(at + " drop shadow removed", animation.isTankShadowVisible());
                }

                assertEquals(at + " carrier body frame",
                        integer(columns, row, "carrier_frame") - 1, carrier.sourceFrame);
                assertEquals(at + " carrier shadow inner frame",
                        integer(columns, row, "carrier_shadow_inner_frame") - 1,
                        shadow.sourceFrame);
                assertEquals(at + " shadow X", carrier.sourceX, shadow.sourceX, 0f);
                assertEquals(at + " shadow Y", carrier.sourceY + 70f,
                        shadow.sourceY, 0f);
                assertEquals(at + " shadow visibility", carrier.visible, shadow.visible);
            }
        }
        assertTrue("Trace must include active deployment ticks", rows > 1);
        assertTrue("Trace must end after the body swap", animation.isTankDeployed());
        assertEquals("tankBase", animation.getActor(1).linkage);
    }

    private static Map<String, FlashBattleRoutes.Point> loadAnchors() throws IOException {
        Path path = Paths.get("src/main/assets/original/runtime/battle_anchors.json");
        if (!Files.isRegularFile(path)) {
            path = Paths.get("app").resolve(path);
        }
        return FlashBattleRoutes.parseAnchors(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    private static Map<String, Integer> columns(String header) {
        Map<String, Integer> result = new HashMap<>();
        String[] names = header.split(",", -1);
        for (int index = 0; index < names.length; index++) {
            result.put(names[index].trim(), index);
        }
        return result;
    }

    private static String value(Map<String, Integer> columns, String[] row, String name) {
        Integer index = columns.get(name);
        assertNotNull("Missing trace column " + name, index);
        assertTrue("Short trace row at " + name, index < row.length);
        return row[index].trim();
    }

    private static int integer(Map<String, Integer> columns, String[] row, String name) {
        return Integer.parseInt(value(columns, row, name));
    }

    private static float number(Map<String, Integer> columns, String[] row, String name) {
        return Float.parseFloat(value(columns, row, name));
    }
}
