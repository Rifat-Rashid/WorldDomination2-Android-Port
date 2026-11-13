package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Golden coordinates/frames from the generated Python Unit + View runtime,
 * executing real SetupMenu playback, not a second movement implementation.
 * Seed 0, difficulty 1, color 5, building 4, leaders [0,1,2,3]; soldier1
 * targets player 1/city 0, then soldier3 targets the address in the filename.
 * Only soldier3's active/stock fields are overridden to make it available.
 * Tick 1 is the first active carrier frame (global source frame 198).
 * MovieClip frames in CSV are one-based; Actor frames are zero-based.
 * Blank base frames reflect an undefined pre-deployment source MovieClip.
 * These fixtures verify movement/counters and authored frame selection, not
 * undefined source clips, shadows, deleted-clip persistence or audible starts.
 */
public final class FlashChinookSourceTraceTest {
    @Test public void northEastDeploymentMatchesEverySourceFrame() throws Exception {
        verifySourceTrace(2, 0);
    }

    @Test public void northWestDeploymentMatchesEverySourceFrame() throws Exception {
        verifySourceTrace(1, 2);
    }

    @Test public void southWestMultiWaypointDriveMatchesEverySourceFrame() throws Exception {
        verifySourceTrace(3, 3);
    }

    @Test public void southEastDeploymentMatchesEverySourceFrame() throws Exception {
        verifySourceTrace(4, 1);
    }

    private static void verifySourceTrace(int target, int city) throws Exception {
        Path anchors = Paths.get("src/main/assets/original/runtime/battle_anchors.json");
        if (!Files.isRegularFile(anchors)) anchors = Paths.get("app").resolve(anchors);
        Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(
                new String(Files.readAllBytes(anchors), StandardCharsets.UTF_8));
        FlashChinookTankAnimation animation = new FlashChinookTankAnimation(
                FlashBattleRoutes.createSoldierThreeAttack(0, target, city, 2, "green", points), 0);
        String resource = "/source/chinook-target" + target + "-city" + city + ".csv";
        InputStream input = FlashChinookSourceTraceTest.class.getResourceAsStream(resource);
        assertNotNull("Missing source fixture " + resource, input);
        boolean carrierStopped = false;
        int frames = 0;
        List<Integer> sourceGunTicks = new ArrayList<>();
        List<Integer> nativeGunTicks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String[] header = reader.readLine().split(",", -1);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split(",", -1);
                int tick = integer(header, row, "active_tick");
                frames++;
                assertEquals("Sequential source frames", frames, tick);
                animation.tick();
                boolean stopped = "stop".equals(value(header, row, "chinook_action"));
                if (!carrierStopped) {
                    actor(header, row, "chinook", animation.getActor(0), tick);
                    assertEquals("Carrier countdown frame " + tick,
                            integer(header, row, "chinook_stopCount"), field(animation, "chinookFlyOffTicks"));
                }
                carrierStopped |= stopped;
                actor(header, row, "base", animation.getActor(1), tick);
                actor(header, row, "top", animation.getActor(2), tick);
                assertEquals("Drop frame " + tick,
                        integer(header, row, "base_dropCount"), field(animation, "dropTicks"));
                assertEquals("Tank waypoint frame " + tick,
                        integer(header, row, "base_waypoint"), field(animation, "tankWaypointIndex"));
                assertEquals("Shot frame " + tick,
                        integer(header, row, "base_shotCount"), field(animation, "shotCount"));
                assertEquals("Preshot frame " + tick,
                        integer(header, row, "top_preshotCount"), field(animation, "tankPreshotCount"));
                if (value(header, row, "audio").contains("tankTop:tank_shoot")) sourceGunTicks.add(tick);
                while (animation.takeAudioCue()) {
                    if (animation.getAudioCue() == OriginalAudio.Cue.TANK_GUN) nativeGunTicks.add(tick);
                }
                boolean allStopped = carrierStopped
                        && "stop".equals(value(header, row, "base_action"))
                        && "stop".equals(value(header, row, "top_action"));
                assertEquals("Source stop frame " + tick, allStopped,
                        animation.isChinookStopped() && animation.isTankStopped()
                                && animation.isTurretStopped());
                // The fixture ends at stop. View splices those wrappers on
                // the following tick, while retaining their MovieClips.
                assertFalse("Stop precedes wrapper removal frame " + tick,
                        animation.isFinished());
                if (allStopped) break;
            }
        }
        assertTrue("Complete source trajectory, including the tank after flyOff", frames > 170);
        assertTrue("Source and native both reach stop", animation.isChinookStopped()
                && animation.isTankStopped() && animation.isTurretStopped());
        animation.tick();
        assertTrue("View removes stopped wrappers on the next tick", animation.isFinished());
        assertTrue("Carrier clip survives until allUnitsDone", animation.getActor(0).visible);
        assertTrue("Tank base clip survives until allUnitsDone", animation.getActor(1).visible);
        assertTrue("Turret clip survives until allUnitsDone", animation.getActor(2).visible);
        assertEquals("Gun action callback ticks (not Sound.start suppression)", sourceGunTicks, nativeGunTicks);
        assertEquals("Two source firing callbacks", 2, sourceGunTicks.size());
    }

    private static void actor(String[] header, String[] row, String prefix,
                              FlashChinookTankAnimation.Actor actor, int tick) {
        assertEquals(prefix + " X frame " + tick,
                Float.parseFloat(value(header, row, prefix + "_x")), actor.sourceX, 0f);
        assertEquals(prefix + " Y frame " + tick,
                Float.parseFloat(value(header, row, prefix + "_y")), actor.sourceY, 0f);
        String frame = value(header, row, prefix + "_frame");
        if (!frame.isEmpty()) {
            assertEquals(prefix + " MovieClip frame " + tick, Integer.parseInt(frame) - 1, actor.sourceFrame);
        }
    }

    private static int field(FlashChinookTankAnimation animation, String name) throws Exception {
        Field field = FlashChinookTankAnimation.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(animation);
    }

    private static int integer(String[] header, String[] row, String name) {
        return Integer.parseInt(value(header, row, name));
    }

    private static String value(String[] header, String[] row, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equals(header[i])) return row[i];
        }
        throw new AssertionError("Missing source column " + name);
    }
}
