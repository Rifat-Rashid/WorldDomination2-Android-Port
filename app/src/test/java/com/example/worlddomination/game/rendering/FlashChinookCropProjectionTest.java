package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class FlashChinookCropProjectionTest {
    private static final Rectangle WIDE = new Rectangle(0f, 0f, 1194f, 600f);
    private static final Rectangle ORIGINAL = new Rectangle(0f, 0f, 800f, 600f);

    @Test
    public void sourceRouteReachesTargetCropBeforeDropAndKeepsAllActorsAligned()
            throws Exception {
        FlashChinookTankAnimation flight = new FlashChinookTankAnimation(
                FlashBattleRoutes.createSoldierThreeAttack(0, 2, 0, 2,
                        "blue", loadAnchors()), 0);
        assertEquals(0, flight.getAttackerPlayerId());
        assertEquals(2, flight.getTargetPlayerId());
        assertEquals(0f, flight.getCropProgress(), 0f);
        flight.tick();
        assertEquals(2f / 128.75f, flight.getCropProgress(), 0.000001f);
        assertEquals(634.3102f, projectedX(WIDE, flight, flight.getActor(0)), 0.001f);
        assertEquals(636.3102f, projectedX(WIDE, flight, flight.getActor(1)), 0.001f);
        assertEquals(projectedX(WIDE, flight, flight.getActor(0)),
                projectedX(WIDE, flight, flight.getChinookShadow()), 0.001f);
        assertEquals(434.25f, projectedX(ORIGINAL, flight, flight.getActor(0)), 0.001f);

        for (int tick = 2; tick <= 31; tick++) flight.tick();
        assertEquals(1f, flight.getCropProgress(), 0f);
        assertEquals(948.25f, projectedX(WIDE, flight, flight.getActor(0)), 0.001f);
        flight.tick();
        assertEquals(952.25f, projectedX(WIDE, flight, flight.getActor(0)), 0.001f);
        assertEquals(950.25f, projectedX(WIDE, flight, flight.getActor(1)), 0.001f);
        assertEquals(950.25f, projectedX(WIDE, flight, flight.getActor(2)), 0.001f);
        for (int tick = 33; tick <= 52; tick++) flight.tick();
        assertTrue(flight.isTankDeployed());
        assertEquals(1032.25f, projectedX(WIDE, flight, flight.getActor(0)), 0.001f);
        assertEquals(950.25f, projectedX(WIDE, flight, flight.getActor(1)), 0.001f);
        assertEquals(950.25f, projectedX(WIDE, flight, flight.getActor(2)), 0.001f);
    }

    @Test
    public void shortExactRangeAndZeroRoutesStartAtTargetCrop() throws Exception {
        // The source checks strict +/-25 on each axis before advancing XY.
        for (float distance : new float[] {0f, 12f, 25f}) {
            FlashChinookTankAnimation flight = synthetic(0, 2,
                    new FlashBattleRoutes.Point(100f, 100f),
                    new FlashBattleRoutes.Point(100f + distance, 100f));
            assertEquals(1f, flight.getCropProgress(), 0f);
            assertEquals(197f, FlashBattleUnitRenderer.deploymentCropOffset(
                    WIDE, flight, flight.getActor(0)), 0.001f);
        }
        FlashChinookTankAnimation sameIsland = synthetic(2, 2,
                new FlashBattleRoutes.Point(100f, 100f),
                new FlashBattleRoutes.Point(300f, 100f));
        assertEquals(0f, FlashBattleUnitRenderer.deploymentCropOffset(
                WIDE, sameIsland, sameIsland.getActor(0)), 0.001f);
    }

    @Test
    public void allRecordedRoutesReachFullCropBeforeTheirFirstDropTick() throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();
        int[] targetCities = {2, 0, 3, 1};
        int[] firstDropTicks = {38, 32, 44, 40};
        for (int target = 1; target <= 4; target++) {
            FlashChinookTankAnimation flight = new FlashChinookTankAnimation(
                    FlashBattleRoutes.createSoldierThreeAttack(0, target,
                            targetCities[target - 1], 2, "blue", anchors), 0);
            float previous = flight.getCropProgress();
            for (int tick = 1; tick <= firstDropTicks[target - 1]; tick++) {
                flight.tick();
                assertTrue("Crop reversed for target " + target + " tick " + tick,
                        flight.getCropProgress() >= previous);
                if (tick == firstDropTicks[target - 1] - 1) {
                    assertEquals(1f, flight.getCropProgress(), 0f);
                    assertTrue(!flight.isTankShadowVisible());
                }
                previous = flight.getCropProgress();
            }
            assertTrue(flight.isTankShadowVisible());
            assertEquals(1f, flight.getCropProgress(), 0f);
        }
    }

    @Test
    public void mapOriginAndHeightScalingPreserveProjectedCrop() throws Exception {
        FlashChinookTankAnimation flight = new FlashChinookTankAnimation(
                FlashBattleRoutes.createSoldierThreeAttack(0, 2, 0, 2,
                        "blue", loadAnchors()), 0);
        flight.tick();
        FlashChinookTankAnimation.Actor carrier = flight.getActor(0);
        assertEquals(684.3102f, projectedX(new Rectangle(50f, 30f, 1194f, 600f),
                flight, carrier), 0.001f);
        assertEquals(335.6875f, projectedX(new Rectangle(10f, 20f, 600f, 450f),
                flight, carrier), 0.001f);
        assertEquals(434.25f * 0.92f + 229f * (1f + 2f / 128.75f),
                projectedX(new Rectangle(0f, 0f, 1194f, 552f), flight, carrier), 0.001f);
    }

    @Test
    public void arrivalUsesTargetCropEvenWhenPostMovePositionOvershoots() throws Exception {
        FlashChinookTankAnimation flight = synthetic(0, 2,
                new FlashBattleRoutes.Point(0f, 100f),
                new FlashBattleRoutes.Point(26f, 100f));
        flight.getActor(0).sourceX = 2f; // Inside the strict arrival rectangle before movement.
        Field velocity = FlashChinookTankAnimation.class.getDeclaredField("chinookXStep");
        velocity.setAccessible(true);
        velocity.setFloat(flight, 60f);
        flight.tick();
        assertTrue(flight.isTankShadowVisible());
        assertEquals(62f, flight.getActor(0).sourceX, 0f);
        assertEquals(1f, flight.getCropProgress(), 0f);
        // Nearest-island ownership is now 1; absolute target projection is
        // still 62 + 197 centered margin + 197 target shift.
        assertEquals(456f, projectedX(WIDE, flight, flight.getActor(0)), 0.001f);
    }

    @Test
    public void reverseAndAxisRoutesStayMonotoneAcrossOvershoot() throws Exception {
        FlashChinookTankAnimation reverse = synthetic(2, 1,
                new FlashBattleRoutes.Point(300f, 100f),
                new FlashBattleRoutes.Point(100f, 100f));
        Method update = FlashChinookTankAnimation.class.getDeclaredMethod("updateCropProgress");
        update.setAccessible(true);
        reverse.getActor(0).sourceX = 200f;
        update.invoke(reverse);
        assertEquals(100f / 175f, reverse.getCropProgress(), 0.000001f);
        assertEquals(-394f * (100f / 175f),
                FlashBattleUnitRenderer.deploymentCropOffset(WIDE, reverse,
                        reverse.getActor(0)), 0.001f);
        reverse.getActor(0).sourceX = 400f;
        update.invoke(reverse);
        assertEquals(100f / 175f, reverse.getCropProgress(), 0.000001f);

        FlashChinookTankAnimation vertical = synthetic(0, 2,
                new FlashBattleRoutes.Point(100f, 300f),
                new FlashBattleRoutes.Point(100f, 100f));
        vertical.getActor(0).sourceY = 200f;
        update.invoke(vertical);
        assertEquals(100f / 175f, vertical.getCropProgress(), 0.000001f);
    }

    @Test
    public void horizontalReverseAndVerticalFlightAdvanceCropOnSourceTicks() throws Exception {
        FlashChinookTankAnimation horizontal = synthetic(2, 1,
                new FlashBattleRoutes.Point(300f, 100f),
                new FlashBattleRoutes.Point(100f, 100f));
        horizontal.tick();
        assertEquals(298f, horizontal.getActor(0).sourceX, 0f);
        assertEquals(100f, horizontal.getActor(0).sourceY, 0f);
        assertEquals(2f / 175f, horizontal.getCropProgress(), 0.000001f);
        assertEquals(692f - 394f * (2f / 175f),
                projectedX(WIDE, horizontal, horizontal.getActor(0)), 0.001f);

        FlashChinookTankAnimation vertical = synthetic(0, 2,
                new FlashBattleRoutes.Point(100f, 300f),
                new FlashBattleRoutes.Point(100f, 100f));
        vertical.tick();
        assertEquals(100f, vertical.getActor(0).sourceX, 0f);
        assertEquals(298f, vertical.getActor(0).sourceY, 0f);
        assertEquals(2f / 175f, vertical.getCropProgress(), 0.000001f);
        assertEquals(297f + 197f * (2f / 175f),
                projectedX(WIDE, vertical, vertical.getActor(0)), 0.001f);
    }

    private static float projectedX(Rectangle bounds, FlashChinookTankAnimation flight,
                                    FlashChinookTankAnimation.Actor actor) {
        return BattlefieldCityLayout.sourceX(bounds, actor.sourceX, actor.islandPlayerId)
                + FlashBattleUnitRenderer.deploymentCropOffset(bounds, flight, actor);
    }

    private static FlashChinookTankAnimation synthetic(int from, int to,
                                                        FlashBattleRoutes.Point start,
                                                        FlashBattleRoutes.Point drop)
            throws Exception {
        Class<FlashBattleRoutes.ChinookTankAttack> type =
                FlashBattleRoutes.ChinookTankAttack.class;
        Class<FlashBattleRoutes.Point> point = FlashBattleRoutes.Point.class;
        Constructor<FlashBattleRoutes.ChinookTankAttack> constructor =
                type.getDeclaredConstructor(int.class, int.class, int.class, int.class,
                        point, point, point, point, point, point, point,
                        FlashBattleRoutes.Point[].class, String.class);
        constructor.setAccessible(true);
        FlashBattleRoutes.ChinookTankAttack route = constructor.newInstance(from, to, 0, 1,
                start, start, drop, start, start, drop, drop,
                new FlashBattleRoutes.Point[] {drop}, "tankTop_blue");
        return new FlashChinookTankAnimation(route, 0);
    }

    private static Map<String, FlashBattleRoutes.Point> loadAnchors() throws Exception {
        Path path = Paths.get("app", "src", "main", "assets", "original", "runtime",
                "battle_anchors.json");
        if (!Files.isRegularFile(path)) {
            path = Paths.get("src", "main", "assets", "original", "runtime",
                    "battle_anchors.json");
        }
        return FlashBattleRoutes.parseAnchors(new String(Files.readAllBytes(path),
                StandardCharsets.UTF_8));
    }
}
