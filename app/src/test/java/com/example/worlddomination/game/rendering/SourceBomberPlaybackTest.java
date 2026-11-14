package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/** Every legal plane002 Unit/View pose, independent of its battle-root owner. */
public final class SourceBomberPlaybackTest {
    @Test public void waitingAircraftDoesNotAdvanceDefenseOrPlayRotation() {
        FlashStandalonePlaneAnimation plane = new FlashStandalonePlaneAnimation(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 2, 1, "plane2", true),
                5, 0, "explosion_warhead_1", OriginalAudio.Cue.EXPLOSION_SMALL3,
                new ReferenceRandom(0L));
        FlashAntiDefenseTrackingAnimation defense = new FlashAntiDefenseTrackingAnimation(
                plane, 1, 2, "chr", FlashAntiDefenseTrackingAnimation.DefenseType.AIR);
        assertTrue("waiting clip remains drawable", plane.getActor().visible);
        for (int tick = 0; tick < 8; tick++) {
            plane.tickSourceFrame(false);
            defense.tick();
            assertFalse(plane.isVisible());
            assertFalse(defense.takeRotationCue());
            assertFalse(defense.takeGunCue());
            assertEquals(FlashAntiDefenseTrackingAnimation.INITIAL_COUNTDOWN_TICKS,
                    defense.getCountdownTicks());
        }
        plane.tickSourceFrame(true);
        defense.tick();
        assertTrue(plane.isVisible());
        assertTrue(defense.takeRotationCue());
        assertEquals(FlashAntiDefenseTrackingAnimation.INITIAL_COUNTDOWN_TICKS - 1,
                defense.getCountdownTicks());
    }

    @Test public void antiBearingFeedsNextTrailWithoutSeekingBodyOrTint() {
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PAIRED_PLANE, new ReferenceRandom(0L));
        SourceAircraftPlayback core = new SourceAircraftPlayback(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 2, 1, "plane2", true), trail);
        core.applySourceAntiDirection(130.75d, 91.75d); // Inactive target cannot acquire a bearing.
        for (int tick = 1; tick < 195; tick++) core.presentWaitingFrame();
        core.tickSourceFrame();
        assertEquals(0d, trail.getX(0), 0d);
        assertEquals(0d, trail.getY(0), 0d);
        int body = core.getFrame(), tint = core.getTintFrame();
        core.applySourceAntiDirection(130.75d, 91.75d);
        assertEquals(body, core.getFrame());
        assertEquals(tint, core.getTintFrame());
        core.tickSourceFrame();
        assertEquals(-2.387893552061204d, trail.getX(2), 1e-10d);
        assertEquals(15.435563588325625d, trail.getY(2), 1e-10d);
    }

    @Test public void firstPairedSmokeUsesUndefinedPriorViewAngleThenRealDirection() {
        SourceAircraftTrail trail = new SourceAircraftTrail(
                SourceAircraftTrail.Origin.PAIRED_PLANE, new ReferenceRandom(0L));
        SourceAircraftPlayback core = new SourceAircraftPlayback(
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 2, 1, "plane2", false), trail);
        for (int tick = 1; tick < 272; tick++) core.presentWaitingFrame();
        core.tickSourceFrame();
        assertEquals(2, trail.getCreatedCount());
        assertEquals(2d, core.getX(), 0d);
        assertEquals(1d, core.getY(), 0d);
        for (int slot = 0; slot < 2; slot++) {
            assertEquals("source first pair X", 0d, trail.getX(slot), 0d);
            assertEquals("source first pair Y", 0d, trail.getY(slot), 0d);
        }
        core.tickSourceFrame();
        assertEquals(4, trail.getCreatedCount());
        assertEquals(12.575567201836162d, trail.getX(2), 1e-10d);
        assertEquals(-10.481910694487505d, trail.getY(2), 1e-10d);
        assertEquals(-.5755672018361659d, trail.getX(3), 1e-10d);
        assertEquals(16.481910694487503d, trail.getY(3), 1e-10d);
    }

    @Test public void successfulBomberMatchesAllSourcePosesAndSoundStop() throws Exception {
        compare("success", false, 272, 381, 508, 509);
    }

    @Test public void interceptedBomberKeepsItsExtraMoveAndPreMoveExplosion() throws Exception {
        compare("intercept", true, 195, 299, 299, 300);
    }

    private static void compare(String name, boolean intercepted, int firstVisit,
                                int impactTick, int stopTick, int removedTick) throws Exception {
        JsonValue fixture;
        try (InputStream input = SourceBomberPlaybackTest.class.getClassLoader()
                .getResourceAsStream("source-plane002-" + name + ".json")) {
            assertNotNull(input);
            fixture = new JsonReader().parse(new String(input.readAllBytes(),
                    StandardCharsets.UTF_8));
        }
        assertEquals("source-plane002-compact-v1", fixture.getString("schema"));
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0, 1, 2, 1, "plane2", intercepted);
        assertEquals("plane002", route.linkage);
        SourceAircraftPlayback core = new SourceAircraftPlayback(route, null);
        JsonValue rows = fixture.get("rows");
        int unitIndex = index(fixture.get("rowFields"), "unit");
        int mcIndex = index(fixture.get("rowFields"), "mc");
        int takes = 0, stops = 0, impacts = 0;
        for (int tick = 0; tick < rows.size; tick++) {
            if (tick > 0 && tick < firstVisit) core.presentWaitingFrame();
            if (tick >= firstVisit) core.tickSourceFrame();
            JsonValue unit = rows.get(tick).get(unitIndex);
            JsonValue mc = rows.get(tick).get(mcIndex);
            JsonValue fields = fixture.get("unitFields");
            assertEquals("action t" + tick, unit.getString(index(fields, "action")), core.getAction());
            assertEquals("status t" + tick, unit.getString(index(fields, "unitStatus")), core.getUnitStatus());
            number("logical X", tick, unit.get(index(fields, "curX")), core.getX());
            number("logical Y", tick, unit.get(index(fields, "curY")), core.getY());
            number("previous X", tick, unit.get(index(fields, "prevX")), core.getPreviousX());
            number("previous Y", tick, unit.get(index(fields, "prevY")), core.getPreviousY());
            number("x step", tick, unit.get(index(fields, "xStep")), core.getXStep());
            number("y step", tick, unit.get(index(fields, "yStep")), core.getYStep());
            number("shadow offset", tick, unit.get(index(fields, "shadowYOffset")), core.getShadowYOffset());
            assertEquals("active frames t" + tick, unit.getInt(index(fields, "framesActive")), core.getActiveFrames());
            assertEquals("waypoint t" + tick, unit.getInt(index(fields, "curWayPoint")), core.getCurrentWaypoint());
            JsonValue clipFields = fixture.get("clipFields");
            number("MC X", tick, mc.get(index(clipFields, "_x")), core.getRenderX());
            number("MC Y", tick, mc.get(index(clipFields, "_y")), core.getRenderY());
            number("MC alpha", tick, mc.get(index(clipFields, "_alpha")), core.getAlpha());
            number("MC scale", tick, mc.get(index(clipFields, "_xscale")), core.getScalePercent());
            assertEquals("MC frame t" + tick, mc.getInt(index(clipFields, "_currentframe")), core.getFrame());
            JsonValue tint = rows.get(tick).get(index(fixture.get("rowFields"), "tint"));
            assertEquals("tint frame t" + tick, tint.getInt(index(clipFields, "_currentframe")),
                    core.getTintFrame());
            assertEquals("wrapper removal t" + tick, tick >= removedTick, core.isRemoved());
            boolean takeoff = core.takeTakeoffCue();
            assertEquals("eight source requests t" + tick,
                    tick >= firstVisit && tick < firstVisit + 8, takeoff);
            if (takeoff) takes++;
            boolean stop = core.takeSoundStop();
            assertEquals("owned sound stop t" + tick, tick == stopTick, stop);
            if (stop) stops++;
            boolean impact = intercepted ? core.takeDeath() : core.takeImpact();
            assertEquals("one source impact t" + tick, tick == impactTick, impact);
            if (impact) impacts++;
            if (intercepted && tick == impactTick) {
                assertEquals(240.3214392303721d, core.getDeathEffectX(), 1e-10d);
                assertEquals(153.4951589928163d, core.getDeathEffectY(), 1e-10d);
                assertFalse(core.isBodyAttached());
            }
            if (!intercepted && tick >= 382 && tick <= 431)
                assertEquals("two-point fade t" + tick, 100 - 2 * (tick - 381),
                        core.getSourceSoundVolumePercent());
        }
        assertEquals(fixture.getInt("lastTick") + 1, rows.size);
        assertEquals(8, takes);
        assertEquals(1, stops);
        assertEquals(1, impacts);
    }

    private static int index(JsonValue fields, String name) {
        for (int i = 0; i < fields.size; i++) if (name.equals(fields.getString(i))) return i;
        throw new AssertionError("Unknown source field " + name);
    }

    private static void number(String label, int tick, JsonValue expected, double actual) {
        if (expected.isNull()) assertTrue(label + " t" + tick, Double.isNaN(actual));
        else assertEquals(label + " t" + tick, expected.asDouble(), actual, 1e-10d);
    }
}
