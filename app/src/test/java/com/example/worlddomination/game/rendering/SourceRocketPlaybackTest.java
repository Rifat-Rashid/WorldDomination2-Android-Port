package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Legal seed-zero submarine attack source rows, independent of rocket implementation. */
public final class SourceRocketPlaybackTest {
    @Test
    public void failedSubmarineUsesOriginGuardAndPreMoveSelfExplosion() {
        FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createSubmarineRocketStrike(
                0, 1, 2, true);
        FlashRocketAnimation animation = FlashRocketAnimation.forSourceSubmarine(
                route, "explosion_bioload_3", OriginalAudio.Cue.EXPLOSION_MEDIUM,
                new ReferenceRandom(0L), 5);
        SourceRocketPlayback core = animation.getSourcePlayback();
        int impactTick = -1;
        int impactCount = 0;
        for (int tick = 235; tick <= 518; tick++) {
            boolean impact = animation.tickSourceFrame(tick >= 424);
            if (tick == 516) {
                assertEquals("rocket_explode_self", core.getAction());
                assertEquals(100.70786948030306d, core.getX(), 0.00001d);
                assertEquals(108.9801612444989d, core.getY(), 0.00001d);
            }
            if (impact) {
                impactTick = tick;
                impactCount++;
            }
            if (tick == 517) {
                assertTrue(core.isSelfImpact());
                assertFalse(core.isBodyAttached());
                assertEquals("explosion003", animation.getImpactLinkage());
                assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3,
                        animation.getImpactCue());
                assertEquals(100.70786948030306d,
                        core.getImpactEffectX(), 0.00001d);
                assertEquals(108.9801612444989d,
                        core.getImpactEffectY(), 0.00001d);
                // killUnit leaves rocket_follow_ellipse true: movement still
                // visits ellipse_step once after detaching the body.
                assertEquals(101.09191525855329d, core.getX(), 0.00001d);
                assertEquals(100.15614094316078d, core.getY(), 0.00001d);
                assertFalse(animation.isFinished());
            }
            if (tick == 518) {
                assertEquals(2, core.getSelfExplosionCount());
                assertEquals(101.09191525855329d,
                        core.getImpactEffectX(), 0.00001d);
                assertEquals(100.15614094316078d,
                        core.getImpactEffectY(), 0.00001d);
                assertEquals(101.62810483876015d, core.getX(), 0.00001d);
                assertEquals(91.40501256098088d, core.getY(), 0.00001d);
            }
        }
        assertEquals(2, impactCount);
        assertEquals(518, impactTick);
        assertTrue(animation.isFinished());
    }

    @Test
    public void sourceUnitMovesThroughWaitAscentEllipseDropAndImpact() throws Exception {
        FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createSubmarineRocketStrike(
                0, 1, 2);
        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        SourceRocketPlayback playback = new SourceRocketPlayback(route, battleRandom);
        playback.setTrailDepthBase(22511);
        Map<Integer, List<String[]>> smokeRows = loadSmokeRows();
        String[] smokeNames = smokeHeader();
        InputStream fixture = getClass().getClassLoader().getResourceAsStream(
                "source-bio-sub-units.csv");
        assertNotNull(fixture);
        int observations = 0;
        int impactTick = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                fixture, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull(header);
            String[] names = header.split(",", -1);
            String line;
            int visitedTick = 234;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split(",", -1);
                int tick = Integer.parseInt(field(names, row, "tick"));
                if (tick < 235 || tick > 577 || !"unit1".equals(field(names, row, "unitName"))) {
                    continue;
                }
                assertEquals("Missing source unit frame", visitedTick + 1, tick);
                visitedTick = tick;
                if (tick == 465) restoreSourceRandomAfterTick464(battleRandom);
                boolean impact = playback.tick(tick >= 424);
                // The non-unit interface draw at 474 follows smoke creation
                // (slot 27 otherwise receives a different alpha). At 510 no
                // smoke is created, but the shared stream still advances.
                if (tick == 474 || tick == 510) battleRandom.nextDouble();
                if (impact) impactTick = tick;
                assertEquals("action at " + tick, field(names, row, "action"),
                        playback.getAction());
                assertEquals("x at " + tick, number(names, row, "curX"),
                        playback.getX(), 0.00001d);
                assertEquals("y at " + tick, number(names, row, "curY"),
                        playback.getY(), 0.00001d);
                assertEquals("paint x at " + tick, number(names, row, "mcX"),
                        playback.getRenderX(), 0.00001d);
                assertEquals("paint y at " + tick, number(names, row, "mcY"),
                        playback.getRenderY(), 0.00001d);
                assertEquals("frame at " + tick, (int) number(names, row, "mcFrame"),
                        playback.getSourceFrame());
                assertEquals("x step at " + tick, number(names, row, "xStep"),
                        playback.getXStep(), 0.00001d);
                assertEquals("y step at " + tick, number(names, row, "yStep"),
                        playback.getYStep(), 0.00001d);
                assertEquals("ellipse angle at " + tick,
                        number(names, row, "ellipseAngle"), playback.getEllipseAngle(), 0.00001d);
                assertEquals("launch timer at " + tick,
                        (int) number(names, row, "subLaunchTimer"), playback.getLaunchTimer());
                assertEquals("attached body at " + tick,
                        Boolean.parseBoolean(field(names, row, "mcRootAttached")),
                        playback.isBodyAttached());
                List<String[]> expectedSmoke = smokeRows.get(tick);
                if (expectedSmoke != null) {
                    assertEquals("attached smoke count at " + tick,
                            expectedSmoke.size(), playback.getAttachedTrailCount());
                    for (String[] smoke : expectedSmoke) {
                        int slot = Integer.parseInt(field(smokeNames, smoke, "name")
                                .substring("unit1trail".length()));
                        assertTrue("smoke slot " + slot + " at " + tick,
                                playback.isTrailAttached(slot));
                        assertEquals("depth of smoke " + slot,
                                (int) number(smokeNames, smoke, "depth"),
                                playback.getTrailDepth(slot));
                        assertEquals("x of smoke " + slot + " at " + tick,
                                number(smokeNames, smoke, "x"),
                                playback.getTrailX(slot), 0.00000001d);
                        assertEquals("y of smoke " + slot + " at " + tick,
                                number(smokeNames, smoke, "y"),
                                playback.getTrailY(slot), 0.00000001d);
                        assertEquals("alpha of smoke " + slot + " at " + tick,
                                number(smokeNames, smoke, "alpha"),
                                playback.getTrailAlpha(slot), 0.00000001d);
                        assertEquals(100d, playback.getTrailScalePercent(slot), 0d);
                    }
                }
                if (tick == 465) assertEquals(0.49603131023446156d,
                        battleRandom.copy().nextDouble(), 0d);
                if (tick == 480) assertEquals(0.8331600837968306d,
                        battleRandom.copy().nextDouble(), 0d);
                if (tick == 576) assertEquals(0.06849191694044987d,
                        battleRandom.copy().nextDouble(), 0d);
                observations++;
            }
        }
        assertEquals(343, observations);
        assertEquals(577, impactTick);
        assertFalse(playback.isRemoved());
        assertFalse(playback.tick(true));
        assertTrue(playback.isRemoved());
    }

    private static String field(String[] names, String[] row, String key) {
        for (int index = 0; index < names.length; index++) {
            if (key.equals(names[index])) return row[index];
        }
        throw new IllegalArgumentException("Missing source fixture column: " + key);
    }

    private static double number(String[] names, String[] row, String key) {
        return Double.parseDouble(field(names, row, key));
    }

    private Map<Integer, List<String[]>> loadSmokeRows() throws Exception {
        Map<Integer, List<String[]>> rows = new HashMap<>();
        InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "source-bio-sub-smoke.csv");
        assertNotNull(stream);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull(header);
            String[] names = header.split(",", -1);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split(",", -1);
                int tick = (int) number(names, row, "tick");
                rows.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(row);
            }
        }
        // Tick 556 has no attached rows; it remains a required zero-occupancy checkpoint.
        rows.put(556, new ArrayList<>());
        return rows;
    }

    private static String[] smokeHeader() {
        return "tick,activeIndex,unitName,name,depth,characterId,linkage,rootAttached,"
                .concat("x,y,alpha,xscale,yscale,parentX,parentY,parentXscale,parentYscale")
                .split(",", -1);
    }

    private void restoreSourceRandomAfterTick464(ReferenceRandom random) throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "source-bio-sub-rng-t464.json");
        assertNotNull(stream);
        String content;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8))) {
            content = reader.readLine();
        }
        assertNotNull(content);
        int start = content.indexOf("\"stateWords\":[") + "\"stateWords\":[".length();
        int end = content.indexOf(']', start);
        String[] words = content.substring(start, end).split(",");
        assertEquals(624, words.length);
        Field stateField = ReferenceRandom.class.getDeclaredField("state");
        stateField.setAccessible(true);
        int[] state = (int[]) stateField.get(random);
        for (int index = 0; index < words.length; index++) {
            state[index] = (int) Long.parseLong(words[index].trim());
        }
        start = content.indexOf("\"stateIndex\":", end) + "\"stateIndex\":".length();
        end = content.indexOf(',', start);
        Field indexField = ReferenceRandom.class.getDeclaredField("stateIndex");
        indexField.setAccessible(true);
        indexField.setInt(random, Integer.parseInt(content.substring(start, end).trim()));
        assertEquals(0.41222669318814775d, random.copy().nextDouble(), 0d);
    }
}
