package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Every stored direct role pose from two legal, unforced ship battles. */
public final class SourceCarrierRolePlaybackTest {
    private static final int BODY = 0;
    private static final int SHADOW = 1;
    private static final int TINT = 2;
    private static final int DOT = 3;
    private static final String HEADER = "tick,unit,role,attached,x,y,frame,"
            + "nestedFrame,visible,alpha,scaleX,scaleY";

    @Test public void threeInterceptedUnitsMatchEveryStoredRoleAndCleanupFrame()
            throws Exception {
        assertTrace("intercept", 1, true, 195, 481, 482);
    }

    @Test public void threeSuccessfulUnitsRetainTheirPaintThroughSourceCleanup()
            throws Exception {
        assertTrace("success", 2, false, 234, 564, 565);
    }

    @Test public void tintAndDotUseActualPlayerPaletteAlphaWithoutChangingBody() {
        // WD_colors.py color_blu/color_ora aa=100; red/yellow/green aa=75.
        int[] expected = {100, 75, 100, 75, 75};
        for (int color = 1; color <= 5; color++) {
            double paletteAlpha = FlashColorTransform.forPlayerColor(color)
                    .alphaMultiplier * 100d;
            assertEquals("source WD_colors alpha " + color, expected[color - 1],
                    paletteAlpha, 0d);
            SourceCarrierPlanePlayback core = new SourceCarrierPlanePlayback(
                    FlashBattleRoutes.createCarrierPlaneStrike(0, 1, 2, 2, true),
                    new ReferenceRandom(0L), 3, paletteAlpha);
            assertEquals("tint constructor alpha " + color, paletteAlpha,
                    core.getTintRole().getAlpha(), 0d);
            assertEquals("dot constructor alpha " + color, paletteAlpha,
                    core.getDotRole().getAlpha(), 0d);
            assertEquals(0d, core.getBodyRole().getAlpha(), 0d);
            assertEquals(100d, core.getShadowRole().getAlpha(), 0d);
            core.presentWaitingFrame();
            core.tickSourceFrame(true);
            assertEquals("tint after View visit " + color, paletteAlpha,
                    core.getTintRole().getAlpha(), 0d);
            assertEquals("dot after View visit " + color, paletteAlpha,
                    core.getDotRole().getAlpha(), 0d);
            assertEquals(100d, core.getBodyRole().getAlpha(), 0d);
            core.clearStoppedRoles();
            assertTrue("target dot survives Unit cleanup", core.getDotRole().isAttached());
        }
    }

    private void assertTrace(String caseName, int defender, boolean intercepted,
                             int activationTick, int cleanupTick, int lastTick)
            throws Exception {
        String file = "source-carrier-roles-" + caseName + ".csv";
        JsonValue manifest;
        try (InputStream input = resource("source-carrier-roles-provenance.json")) {
            manifest = new JsonReader().parse(input);
        }
        JsonValue record = manifest.get("records").get(caseName);
        assertEquals("legal source capture tick range", lastTick,
                record.getInt("lastTick"));
        byte[] fixture;
        try (InputStream input = resource(file)) { fixture = input.readAllBytes(); }
        assertEquals("byte-pinned source capture reduction", record.getString("fixtureSHA256"),
                hex(MessageDigest.getInstance("SHA-256").digest(fixture)));
        String[][][][] expected = new String[lastTick + 1][3][4][];
        int rows = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(fixture), StandardCharsets.UTF_8))) {
            assertEquals(HEADER, reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                assertEquals("fixture columns", 12, fields.length);
                int tick = Integer.parseInt(fields[0]);
                assertTrue("captured tick", tick >= 0 && tick <= lastTick);
                int unit = Integer.parseInt(fields[1].substring(4)) - 1;
                assertTrue("physical source unit", unit >= 0 && unit < 3);
                int role = roleIndex(fields[2]);
                assertEquals("no duplicate role at frame " + tick, null,
                        expected[tick][unit][role]);
                expected[tick][unit][role] = fields;
                rows++;
            }
        }
        assertEquals("all three units and four direct roles at every tick",
                (lastTick + 1) * 3 * 4, rows);
        assertEquals(rows, record.getInt("rows"));

        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        SourceCarrierPlanePlayback[] units = new SourceCarrierPlanePlayback[3];
        for (int index = 0; index < 3; index++) {
            units[index] = new SourceCarrierPlanePlayback(
                    FlashBattleRoutes.createCarrierPlaneStrike(0, defender, index,
                            index, intercepted), battleRandom);
        }
        int compared = 0;
        for (int tick = 0; tick <= lastTick; tick++) {
            if (tick > 0) {
                // View.f_37 enumerates the source active Unit array in reverse.
                for (int unit = 2; unit >= 0; unit--)
                    units[unit].tickSourceFrame(tick >= activationTick);
            }
            if (tick == cleanupTick)
                for (SourceCarrierPlanePlayback unit : units) unit.clearStoppedRoles();
            for (int unit = 0; unit < 3; unit++) {
                SourceCarrierPlanePlayback.RolePose[] roles = {
                        units[unit].getBodyRole(), units[unit].getShadowRole(),
                        units[unit].getTintRole(), units[unit].getDotRole()};
                for (int role = 0; role < 4; role++) {
                    String[] row = expected[tick][unit][role];
                    assertNotNull("source role row", row);
                    String label = caseName + " tick " + tick + " unit" + (unit + 1)
                            + " " + row[2];
                    SourceCarrierPlanePlayback.RolePose pose = roles[role];
                    assertEquals(label + " attached", Boolean.parseBoolean(row[3]),
                            pose.isAttached());
                    assertEquals(label + " X", Double.parseDouble(row[4]),
                            pose.getX(), 1e-8d);
                    assertEquals(label + " Y", Double.parseDouble(row[5]),
                            pose.getY(), 1e-8d);
                    assertEquals(label + " authored frame", Integer.parseInt(row[6]),
                            pose.getFrame());
                    if (!row[7].isEmpty())
                        assertEquals(label + " nested frame", Integer.parseInt(row[7]),
                                pose.getNestedFrame());
                    assertEquals(label + " visible", Boolean.parseBoolean(row[8]),
                            pose.isVisible());
                    assertEquals(label + " alpha", Double.parseDouble(row[9]),
                            pose.getAlpha(), 1e-8d);
                    assertEquals(label + " X scale", Double.parseDouble(row[10]),
                            pose.getScale(), 1e-8d);
                    assertEquals(label + " Y scale", Double.parseDouble(row[11]),
                            pose.getScale(), 1e-8d);
                    compared++;
                }
            }
        }
        assertTrue("every legal source role compared", compared > 5700);
    }

    private static int roleIndex(String role) {
        switch (role) {
            case "body": return BODY;
            case "shadow": return SHADOW;
            case "tint": return TINT;
            case "dot": return DOT;
            default: throw new IllegalArgumentException("Unexpected source role " + role);
        }
    }

    private InputStream resource(String name) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(name);
        assertNotNull(name, input);
        return input;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            result.append(Character.forDigit((value >>> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return result.toString();
    }
}
