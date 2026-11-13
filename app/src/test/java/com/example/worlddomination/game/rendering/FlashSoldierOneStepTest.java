package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** Unit.setStep arithmetic and ClipObject's ignored NaN coordinates, from executed source probes. */
public final class FlashSoldierOneStepTest {
    @Test public void keepsSourceDoubleArithmeticOrderAtWholeStepBoundaries() throws Exception {
        for (float targetY : new float[]{0f, 97f}) {
            FlashSoldierOneAnimation animation = animation();
            Object track = track(animation);
            Method move = track.getClass().getDeclaredMethod("moveSoldierStep", FlashBattleRoutes.Point.class);
            move.setAccessible(true);
            move.invoke(track, new FlashBattleRoutes.Point(97f, targetY));
            // floor(.04 * (100/97*97))=3; float(4*97/97) incorrectly gives4.
            assertEquals(3f, animation.getActor(1).sourceX, 0f);
            assertEquals(targetY == 0f ? 0f : 1f, animation.getActor(1).sourceY, 0f);
        }
    }

    @Test public void zeroDistanceKeepsDisplayPoseThenContinuesAlongNextWaypoint() throws Exception {
        FlashSoldierOneAnimation animation = animation();
        FlashSoldierOneAnimation.Actor actor = animation.getActor(1);
        Object track = track(animation);
        actor.visible = true;
        actor.sourceX = 160f;
        actor.sourceY = 201f;
        actor.sourceFrame = 122;
        field(track, "started").setBoolean(track, true);
        field(track, "frameOffset").setInt(track, 5);
        field(track, "range").setInt(track, 5);
        Method tick = track.getClass().getDeclaredMethod("tick");
        tick.setAccessible(true);
        // Source probe: frame123 at (160,201); the zero step makes curX/Y NaN,
        // while the display retains that frame/position and the waypoint advances.
        tick.invoke(track);
        assertEquals(160f, actor.sourceX, 0f);
        assertEquals(201f, actor.sourceY, 0f);
        assertEquals(122, actor.sourceFrame);
        assertEquals(1, field(track, "waypointIndex").getInt(track));
        tick.invoke(track);
        assertEquals(156f, actor.sourceX, 0f);
        assertEquals(201f, actor.sourceY, 0f);
        assertEquals(210, actor.sourceFrame);
        tick.invoke(track);
        assertEquals(154f, actor.sourceX, 0f);
        assertEquals(202f, actor.sourceY, 0f);
        assertEquals(7, actor.sourceFrame);
    }

    private static FlashSoldierOneAnimation animation() throws Exception {
        Path asset = Paths.get("src/main/assets/original/runtime/battle_anchors.json");
        if (!Files.isRegularFile(asset)) asset = Paths.get("app").resolve(asset);
        return new FlashSoldierOneAnimation(FlashBattleRoutes.createSoldierOneAttack(
                0, 1, 0, FlashBattleRoutes.parseAnchors(Files.readString(asset)),
                new ReferenceRandom(0)), new ReferenceRandom(0), null, 0);
    }

    private static Object track(FlashSoldierOneAnimation animation) throws Exception {
        return ((Object[]) field(animation, "soldiers").get(animation))[0];
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
