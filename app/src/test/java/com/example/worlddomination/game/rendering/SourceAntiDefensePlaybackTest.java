package com.example.worlddomination.game.rendering;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source: Unit.py:1141-1160,1229-1233; View.py:927-954,1231-1259.
 * Legal seed-0 ship intercept callback capture: ignored
 * analysis/carrier-anti-angle-next/anti-cadence/anti-cadence-unit3.json in the
 * carrier audit worktree. The capture reads raw fields and wraps updateAnti;
 * it does not evaluate the mutating anti_direction getter as an observer. */
public final class SourceAntiDefensePlaybackTest {
    private static final double ANTI_X = 130.75d;
    private static final double ANTI_Y = 91.75d;

    @Test
    public void sourceCallbackOracleHasPinnedBytes() throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream input = getClass().getResourceAsStream("/source-anti-defense-host-clock.tsv")) {
            assertTrue(input != null);
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
        assertEquals("4e2cb2b41cb71f8bd760248cbf82ed89fb0c07fc7fb2855e0854f753f5f8235e", hex.toString());
    }

    @Test
    public void legalShipCallsTwicePerVisitAndUsesEachUnitsCountdown() {
        SourceAntiDefensePlayback model = SourceAntiDefensePlayback.forReligion("chr");
        SourceAntiDefensePlayback.UnitState ship = new SourceAntiDefensePlayback.UnitState();
        assertEquals(0, ship.getOffset()); // ship_plane leaves anti_offset unset in Unit ctor.
        for (int tick = 195; tick <= 220; tick++) {
            if (tick > 195) model.publishHostFrame(18); // separate host autoplay
            model.invoke(ship, true, 458.5d, 68.75d, ANTI_X, ANTI_Y, false);
            assertEquals(185, ship.getRawBearingDegrees());
            assertEquals(17, model.getTurretFrame());
            assertEquals(Math.max(0, 50 - 2 * (tick - 195) - 1), ship.getCountdown());
            model.invoke(ship, true, 458.5d, 68.75d, ANTI_X, ANTI_Y, false);
            assertEquals(Math.max(0, 50 - 2 * (tick - 194)), ship.getCountdown());
            assertEquals(17, model.getTurretFrame());
            assertEquals(100d, model.getTurretAlphaPercent(), 0d);
            assertFalse(model.isShootSoundRequested());
        }
        // Source capture: 50→48 at t195, 2 at t218, 0 at t219 and thereafter.
        assertEquals(0, ship.getCountdown());
    }

    @Test
    public void sourceShipStartsShootingOnCrossingStrict170AndTogglesTwice() {
        SourceAntiDefensePlayback model = SourceAntiDefensePlayback.forReligion("chr");
        model.publishHostFrame(16);
        SourceAntiDefensePlayback.UnitState ship = new SourceAntiDefensePlayback.UnitState();
        // Legal source t260: (299.5,134.75) is 174.142 away; no offset change.
        model.invoke(ship, true, 299.5d, 134.75d, ANTI_X, ANTI_Y, false);
        assertEquals(0, ship.getOffset());
        assertEquals(15, model.getTurretFrame());
        // t261: (291.5,130.75), 165.413 away; first call shoots at frame16.
        model.publishHostFrame(16);
        model.invoke(ship, true, 291.5d, 130.75d, ANTI_X, ANTI_Y, false);
        assertEquals(167, ship.getRawBearingDegrees());
        assertEquals(1, ship.getOffset());
        assertEquals(16, model.getTurretFrame());
        assertTrue(model.isShootSoundRequested());
        assertFalse(model.isTurretPlaying());
        // Same View visit, generic air branch: offset returns to2, final frame15.
        model.invoke(ship, true, 291.5d, 130.75d, ANTI_X, ANTI_Y, false);
        assertEquals(2, ship.getOffset());
        assertEquals(15, model.getTurretFrame());
        assertFalse(model.isShootSoundRequested());
        assertTrue(model.isTurretPlaying());
    }

    @Test
    public void killedNonLockedUnitKeepsItsOwnCountdownButChangesSharedTurret() {
        SourceAntiDefensePlayback model = SourceAntiDefensePlayback.forReligion("chr");
        model.publishHostFrame(18);
        SourceAntiDefensePlayback.UnitState locked = new SourceAntiDefensePlayback.UnitState();
        SourceAntiDefensePlayback.UnitState dying = new SourceAntiDefensePlayback.UnitState();
        model.invoke(locked, true, 458.5d, 68.75d, ANTI_X, ANTI_Y, false);
        dying.markKilled();
        // Source unit1 at t238 calls updateAnti through Unit.killUnit even though
        // defender.anti_inBattle_target still names unit3.
        model.invoke(dying, true, 58.5d, 165.75d, 0d, 0d, true);
        assertEquals(49, dying.getCountdown());
        assertEquals(49, locked.getCountdown());
        assertEquals(2, dying.getOffset());
        assertEquals(110, dying.getRawBearingDegrees());
        assertFalse(model.isShootSoundRequested());
        assertEquals(11, model.getTurretFrame());
        // Locked unit3's own kill pulse at t269 crosses the fire range.
        locked.markKilled();
        model.invoke(locked, true, 235.5d, 96.75d, ANTI_X, ANTI_Y, true);
        assertEquals(1, locked.getOffset());
        assertTrue(model.isShootSoundRequested());
        assertEquals(17, model.getTurretFrame()); // dead even frame gets prevFrame
    }

    @Test
    public void sourceDirectionPreservesNegativeZeroAndUnclampedZeroFrame() {
        SourceAntiDefensePlayback.UnitState unit = new SourceAntiDefensePlayback.UnitState();
        assertEquals(0, SourceAntiDefensePlayback.sourceDirection(unit,
                -1d, 0d, 0d, 0d));
        assertEquals(1, unit.getRawBearingDegrees());
        SourceAntiDefensePlayback model = new SourceAntiDefensePlayback(50, 1, 0d);
        // Same exact-west direction outside the 170-pixel firing range:
        // gotoAndPlay(-1) clamps to frame 1 without a following nextFrame.
        model.invoke(unit, true, -400d, 0d, 0d, 0d, false);
        assertEquals(1, model.getTurretFrame()); // host clamps gotoAndPlay(-1)
        assertEquals(49, unit.getCountdown());
        assertEquals(0d, model.getTurretAlphaPercent(), 0d);
        assertEquals(18, SourceAntiDefensePlayback.sourceDirection(unit,
                -1d, -0.001d, 0d, 0d));
    }

    @Test
    public void alphaChecksBeforeAddingTenAndInactiveCallDoesNothing() {
        SourceAntiDefensePlayback model = new SourceAntiDefensePlayback(50, 1, 95d);
        SourceAntiDefensePlayback.UnitState unit = new SourceAntiDefensePlayback.UnitState();
        model.invoke(unit, false, 1d, 0d, 0d, 0d, false);
        assertEquals(50, unit.getCountdown());
        assertFalse(model.isMoveSoundRequested());
        for (int i = 0; i < 50; i++) model.invoke(unit, true, 1d, 0d, 0d, 0d, false);
        assertEquals(0, unit.getCountdown());
        assertEquals(95d, model.getTurretAlphaPercent(), 0d);
        model.invoke(unit, true, 1d, 0d, 0d, 0d, false);
        assertEquals(105d, model.getTurretAlphaPercent(), 0d);
        model.invoke(unit, true, 1d, 0d, 0d, 0d, false);
        assertEquals(105d, model.getTurretAlphaPercent(), 0d);
    }

    @Test
    public void allAuthoredReligionTimelinesUseTheirOwnLengthAndFrameOneStop() {
        String[] religions = {"mus", "jew", "com", "chr", "bhu"};
        int[] lengths = {55, 50, 50, 50, 50};
        for (int i = 0; i < religions.length; i++) {
            SourceAntiDefensePlayback model = SourceAntiDefensePlayback.forReligion(religions[i]);
            SourceAntiDefensePlayback.UnitState unit = new SourceAntiDefensePlayback.UnitState();
            // Synthetic boundary: a source callback starts playback, and the
            // published final authored frame rolls into its frame-1 stop action.
            model.invoke(unit, true, -400d, 0d, 0d, 0d, false);
            model.publishHostFrame(lengths[i]);
            model.advanceHostFrame();
            assertEquals(religions[i], 1, model.getTurretFrame());
            assertFalse(religions[i], model.isTurretPlaying());
            model.advanceHostFrame();
            assertEquals(religions[i], 1, model.getTurretFrame());
        }
    }

    @Test
    public void legalCarrierTraceMatchesEveryInvocationAndHostStep() throws Exception {
        SourceAntiDefensePlayback model = SourceAntiDefensePlayback.forReligion("chr");
        Map<String, SourceAntiDefensePlayback.UnitState> units = new HashMap<>();
        Set<String> killed = new HashSet<>();
        InputStream stream = getClass().getResourceAsStream("/source-anti-defense-host-clock.tsv");
        assertTrue("Missing compact source oracle", stream != null);
        int callbacks = 0;
        int hostSteps = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8))) {
            assertTrue(reader.readLine().startsWith("kind\ttick\t"));
            for (String line; (line = reader.readLine()) != null; ) {
                String[] c = line.split("\t", -1);
                int tick = Integer.parseInt(c[1]);
                String context = "source tick " + tick + " " + c[0] + " " + c[2];
                if ("C".equals(c[0])) {
                    SourceAntiDefensePlayback.UnitState unit = units.get(c[2]);
                    if (unit == null) {
                        unit = new SourceAntiDefensePlayback.UnitState();
                        units.put(c[2], unit);
                    }
                    boolean dead = "1".equals(c[8]);
                    if (dead && killed.add(c[2])) unit.markKilled();
                    assertEquals(context, Integer.parseInt(c[12]), unit.getCountdown());
                    assertEquals(context, Integer.parseInt(c[13]), unit.getOffset());
                    assertEquals(context, Integer.parseInt(c[9]), model.getTurretFrame());
                    assertEquals(context, Double.parseDouble(c[10]), model.getTurretAlphaPercent(), 0d);
                    assertEquals(context, "1".equals(c[11]), model.isTurretPlaying());
                    model.invoke(unit, "1".equals(c[3]), Double.parseDouble(c[4]),
                            Double.parseDouble(c[5]), Double.parseDouble(c[6]),
                            Double.parseDouble(c[7]), dead);
                    assertEquals(context, Integer.parseInt(c[14]), model.getTurretFrame());
                    assertEquals(context, Double.parseDouble(c[15]), model.getTurretAlphaPercent(), 0d);
                    assertEquals(context, "1".equals(c[16]), model.isTurretPlaying());
                    assertEquals(context, Integer.parseInt(c[17]), unit.getCountdown());
                    assertEquals(context, Integer.parseInt(c[18]), unit.getOffset());
                    assertEquals(context, Integer.parseInt(c[19]), unit.getRawBearingDegrees());
                    callbacks++;
                } else {
                    assertEquals(context, "H", c[0]);
                    model.advanceHostFrame();
                    assertEquals(context, Integer.parseInt(c[14]), model.getTurretFrame());
                    assertEquals(context, Double.parseDouble(c[15]), model.getTurretAlphaPercent(), 0d);
                    assertEquals(context, "1".equals(c[16]), model.isTurretPlaying());
                    if (tick == 482) assertEquals(context, "1", c[20]);
                    hostSteps++;
                }
            }
        }
        assertEquals(155, callbacks);
        assertEquals(483, hostSteps);
        assertEquals(3, units.size()); // Includes two nonlocked death callbacks.
        assertEquals(17, model.getTurretFrame());
        assertFalse(model.isTurretPlaying());
    }
}
