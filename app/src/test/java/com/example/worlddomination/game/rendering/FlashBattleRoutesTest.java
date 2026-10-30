package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public final class FlashBattleRoutesTest {
    private static final float[][] BOAT_STARTS = {
            {410f, 474f}, {179f, 252f}, {528f, 145f}, {311f, 525f}, {575f, 510f}
    };
    private static final float[][] LAND_UNIT_POSITIONS = {
            {420f, 420f}, {160f, 201f}, {563f, 108f}, {260f, 500f}, {562f, 511f}
    };

    @Test
    public void sourceBattleAnchorsIncludeAllSoldierWaypoints() throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();

        for (int player = 0; player < BattlefieldCityLayout.PLAYER_COUNT; player++) {
            for (int waypoint = 1; waypoint <= 6; waypoint++) {
                String name = "swp" + player + "_" + waypoint;
                if (hasSourceWaypoint(player, waypoint)) {
                    assertNotNull("Missing source anchor " + name, anchors.get(name));
                }
            }
        }
    }

    @Test
    public void createsBoatAndThreeInfantryRoutesForEveryTargetCity() throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();

        for (int target = 0; target < BattlefieldCityLayout.PLAYER_COUNT; target++) {
            int attacker = (target + 1) % BattlefieldCityLayout.PLAYER_COUNT;
            for (int city = 0; city < BattlefieldCityLayout.CITIES_PER_PLAYER; city++) {
                FlashBattleRoutes.SoldierOneAttack route =
                        FlashBattleRoutes.createSoldierOneAttack(attacker, target, city,
                                anchors, new ReferenceRandom(target * 10L + city));

                assertEquals(attacker, route.attackerPlayerId);
                assertEquals(target, route.targetPlayerId);
                assertEquals(BOAT_STARTS[attacker][0], route.boatStart.x, 0f);
                assertEquals(BOAT_STARTS[attacker][1], route.boatStart.y, 0f);
                assertTrue(route.boatWaypoints.length > 0);
                assertEquals(route.boatWaypoints.length, route.boatIslandIds.length);
                FlashBattleRoutes.Point destination =
                        route.boatWaypoints[route.boatWaypoints.length - 1];
                assertEquals(BOAT_STARTS[target][0], destination.x, 0f);
                assertEquals(BOAT_STARTS[target][1], destination.y, 0f);

                assertEquals(3, route.soldiers.length);
                for (FlashBattleRoutes.SoldierPath soldier : route.soldiers) {
                    assertTrue("Infantry route must include spawn, approach, and firing points",
                            soldier.waypoints.length >= 3);
                    FlashBattleRoutes.Point firingPoint =
                            soldier.waypoints[soldier.waypoints.length - 1];
                    assertEquals(BattlefieldCityLayout.sourceCityX(target, city),
                            firingPoint.x, 0f);
                    assertEquals(BattlefieldCityLayout.sourceCityY(target, city) - 20f,
                            firingPoint.y, 0f);
                }
            }
        }
    }

    @Test
    public void rejectsInvalidPlayersCitiesAndMissingSourceAnchors() throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();

        assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSoldierOneAttack(-1, 1, 0, anchors,
                        new ReferenceRandom(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSoldierOneAttack(0, 1,
                        BattlefieldCityLayout.CITIES_PER_PLAYER, anchors,
                        new ReferenceRandom(1L)));

        Map<String, FlashBattleRoutes.Point> incomplete = new HashMap<>(anchors);
        incomplete.remove("swp1_1");
        assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSoldierOneAttack(0, 1, 1, incomplete,
                        new ReferenceRandom(1L)));
    }

    @Test
    public void createsApacheAndThreeMarineRoutesForEveryTargetCityAndActionSlot()
            throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();

        for (int attacker = 0; attacker < BattlefieldCityLayout.PLAYER_COUNT; attacker++) {
            int target = (attacker + 1) % BattlefieldCityLayout.PLAYER_COUNT;
            for (int slot = 1; slot <= 2; slot++) {
                FlashBattleRoutes.Point actionBuilding =
                        FlashBattleRoutes.actionBuildingPosition(attacker, slot);
                for (int city = 0; city < BattlefieldCityLayout.CITIES_PER_PLAYER; city++) {
                    FlashBattleRoutes.ApacheMarineAttack route =
                            FlashBattleRoutes.createSoldierTwoAttack(attacker, target, city,
                                    slot, anchors, new ReferenceRandom(attacker * 100L
                                            + slot * 10L + city));

                    assertEquals(attacker, route.attackerPlayerId);
                    assertEquals(target, route.targetPlayerId);
                    assertEquals(slot, route.actionBuildingSlot);
                    assertEquals(actionBuilding.x, route.actionBuilding.x, 0f);
                    assertEquals(actionBuilding.y, route.actionBuilding.y, 0f);
                    assertEquals(actionBuilding.x + 10f, route.apacheStart.x, 0f);
                    assertEquals(actionBuilding.y, route.apacheStart.y, 0f);
                    assertEquals(LAND_UNIT_POSITIONS[target][0] + 35f,
                            route.unloadPoint.x, 0f);
                    assertEquals(LAND_UNIT_POSITIONS[target][1] - 10f,
                            route.unloadPoint.y, 0f);
                    assertEquals(3, route.marines.length);
                    for (FlashBattleRoutes.SoldierPath marine : route.marines) {
                        assertTrue(marine.waypoints.length >= 3);
                        FlashBattleRoutes.Point spawn = marine.waypoints[0];
                        assertEquals(LAND_UNIT_POSITIONS[target][0], spawn.x, 0f);
                        assertEquals(LAND_UNIT_POSITIONS[target][1], spawn.y, 0f);
                        FlashBattleRoutes.Point approach =
                                marine.waypoints[marine.waypoints.length - 2];
                        assertTrue(Math.abs(approach.x
                                - BattlefieldCityLayout.sourceCityX(target, city)
                                - marine.fireOffsetX) <= 3.01f);
                        assertTrue("target=" + target + " city=" + city
                                        + " offset=" + marine.fireOffsetY
                                        + " approachY=" + approach.y
                                        + " cityY=" + BattlefieldCityLayout.sourceCityY(target, city),
                                Math.abs(approach.y
                                        - BattlefieldCityLayout.sourceCityY(target, city)
                                        - marine.fireOffsetY) <= 3.01f);
                        FlashBattleRoutes.Point firingPoint =
                                marine.waypoints[marine.waypoints.length - 1];
                        assertEquals(BattlefieldCityLayout.sourceCityX(target, city),
                                firingPoint.x, 0f);
                        assertEquals(BattlefieldCityLayout.sourceCityY(target, city),
                                firingPoint.y, 0f);
                    }
                }
            }
        }
    }

    @Test
    public void actionBuildingSlotsMatchTheOriginalFlashSceneCoordinates() {
        float[][] expected = {
                {386.25f, 352.25f, 432.25f, 296.75f},
                {85.75f, 146.75f, 130.75f, 91.75f},
                {668.75f, 86.75f, 723.75f, 146.25f},
                {156.75f, 566.25f, 102.25f, 506.75f},
                {667.75f, 556.75f, 722.75f, 496.75f}
        };

        for (int player = 0; player < expected.length; player++) {
            for (int slot = 1; slot <= 2; slot++) {
                FlashBattleRoutes.Point actual =
                        FlashBattleRoutes.actionBuildingPosition(player, slot);
                int offset = (slot - 1) * 2;
                assertEquals(expected[player][offset], actual.x, 0f);
                assertEquals(expected[player][offset + 1], actual.y, 0f);
            }
        }
    }

    @Test
    public void rejectsInvalidApacheSlot() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.actionBuildingPosition(0, 0));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.actionBuildingPosition(0, 3));
    }

    @Test
    public void createsSourceSuicideBomberRoutesForEveryAttackerTargetAndCity()
            throws Exception {
        int[][][] tankWaypoints = {
                {{0}, {1, 2}, {1, 2, 3}, {1, 6}, {1}},
                {{1}, {1}, {2}, {2, 3, 5}, {4}},
                {{0}, {1}, {1, 2}, {1, 2, 3}, {4}},
                {{0}, {0}, {1, 2}, {1, 2, 3}, {4}},
                {{0}, {1}, {2, 3}, {2, 3, 4}, {5}}
        };
        float[][] spawnPositions = {
                {420f, 420f}, {160f, 201f}, {563f, 108f}, {260f, 500f}, {562f, 511f}
        };
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();

        for (int attacker = 0; attacker < BattlefieldCityLayout.PLAYER_COUNT; attacker++) {
            for (int target = 0; target < BattlefieldCityLayout.PLAYER_COUNT; target++) {
                for (int city = 0; city < BattlefieldCityLayout.CITIES_PER_PLAYER; city++) {
                    FlashBattleRoutes.SuicideBomberAttack route =
                            FlashBattleRoutes.createSuicideBomberAttack(
                                    attacker, target, city, anchors);
                    assertEquals(attacker, route.attackerPlayerId);
                    assertEquals(target, route.targetPlayerId);
                    assertEquals(city, route.targetCityIndex);
                    assertEquals(spawnPositions[attacker][0], route.spawn.x, 0f);
                    assertEquals(spawnPositions[attacker][1], route.spawn.y, 0f);

                    int[] sourceWaypoints = tankWaypoints[target][city];
                    int routeIndex = 0;
                    if (sourceWaypoints[0] != 0) {
                        for (int waypoint : sourceWaypoints) {
                            FlashBattleRoutes.Point source =
                                    anchors.get("twp" + target + "_" + waypoint);
                            if (source.x != 0f) {
                                assertEquals(source.x, route.waypoints[routeIndex].x, 0f);
                                assertEquals(source.y, route.waypoints[routeIndex].y, 0f);
                                routeIndex++;
                            }
                        }
                    }
                    assertEquals(routeIndex + 1, route.waypoints.length);
                    assertEquals(BattlefieldCityLayout.sourceCityX(target, city),
                            route.waypoints[routeIndex].x, 0f);
                    assertEquals(BattlefieldCityLayout.sourceCityY(target, city),
                            route.waypoints[routeIndex].y, 0f);
                }
            }
        }
    }

    @Test
    public void rejectsInvalidSuicideBomberRouteData() throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSuicideBomberAttack(-1, 0, 0, anchors));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSuicideBomberAttack(0, 5, 0, anchors));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSuicideBomberAttack(0, 1, 5, anchors));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSuicideBomberAttack(0, 1, 1, null));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSuicideBomberAttack(0, 1, 1, 0, anchors));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> FlashBattleRoutes.createSuicideBomberAttack(0, 1, 1, 3, anchors));
    }

    @Test
    public void suicideBomberLaunchPositionUsesItsSourceActionBuildingSlot() throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();
        for (int player = 0; player < BattlefieldCityLayout.PLAYER_COUNT; player++) {
            for (int slot = 1; slot <= 2; slot++) {
                FlashBattleRoutes.SuicideBomberAttack route =
                        FlashBattleRoutes.createSuicideBomberAttack(
                                player, 1, 0, slot, anchors);
                FlashBattleRoutes.Point building =
                        FlashBattleRoutes.actionBuildingPosition(player, slot);
                assertEquals(slot, route.actionBuildingSlot);
                assertEquals(building.x, route.actionBuilding.x, 0f);
                assertEquals(building.y, route.actionBuilding.y, 0f);
                assertEquals(building.x + 10f, route.initialPosition.x, 0f);
                assertEquals(building.y - 25f, route.initialPosition.y, 0f);
                assertEquals(building.x - 15f, route.disappearPosition.x, 0f);
                assertEquals(building.y - 45f, route.disappearPosition.y, 0f);
            }
        }
    }

    @Test
    public void createsChinookAndTankRoutesForEveryPlayerCityAndActionSlot() throws Exception {
        int[][][] tankWaypoints = {
                {{0}, {1, 2}, {1, 2, 3}, {1, 6}, {1}},
                {{1}, {1}, {2}, {2, 3, 5}, {4}},
                {{0}, {1}, {1, 2}, {1, 2, 3}, {4}},
                {{0}, {0}, {1, 2}, {1, 2, 3}, {4}},
                {{0}, {1}, {2, 3}, {2, 3, 4}, {5}}
        };
        String[][] firePositions = {
                {"RT", "B", "L", "B", "LT"},
                {"T", "LT", "LT", "LB", "LB"},
                {"B", "RT", "RT", "LT", "T"},
                {"L", "LB", "LB", "R", "R"},
                {"T", "L", "B", "B", "L"}
        };
        float[][] starts = {
                {420f, 310f}, {88f, 109f}, {708f, 91f}, {105f, 510f}, {730f, 505f}
        };
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();
        String[] colors = {"blue", "red", "orange", "yellow", "green"};

        for (int attacker = 0; attacker < BattlefieldCityLayout.PLAYER_COUNT; attacker++) {
            for (int target = 0; target < BattlefieldCityLayout.PLAYER_COUNT; target++) {
                for (int city = 0; city < BattlefieldCityLayout.CITIES_PER_PLAYER; city++) {
                    for (int slot = 1; slot <= 2; slot++) {
                        FlashBattleRoutes.ChinookTankAttack route =
                                FlashBattleRoutes.createSoldierThreeAttack(attacker, target,
                                        city, slot, colors[attacker], anchors);
                        FlashBattleRoutes.Point building =
                                FlashBattleRoutes.actionBuildingPosition(attacker, slot);
                        assertEquals(building.x, route.actionBuilding.x, 0f);
                        assertEquals(building.x, route.chinookStart.x, 0f);
                        assertEquals(building.y - 75f, route.chinookStart.y, 0f);
                        assertEquals(LAND_UNIT_POSITIONS[target][0], route.chinookDrop.x, 0f);
                        assertEquals(LAND_UNIT_POSITIONS[target][1] - 40f,
                                route.chinookDrop.y, 0f);
                        assertEquals(starts[attacker][0] - 210f, route.tankStart.x, 0f);
                        assertEquals(starts[attacker][1] - 210f, route.tankStart.y, 0f);
                        assertEquals("tankTop_" + colors[attacker], route.turretLinkage);

                        int[] sourceWaypoints = tankWaypoints[target][city];
                        int routeIndex = 0;
                        if (sourceWaypoints[0] != 0) {
                            for (int waypoint : sourceWaypoints) {
                                FlashBattleRoutes.Point anchor =
                                        anchors.get("twp" + target + "_" + waypoint);
                                assertEquals(anchor.x, route.tankWaypoints[routeIndex].x, 0f);
                                assertEquals(anchor.y, route.tankWaypoints[routeIndex].y, 0f);
                                routeIndex++;
                            }
                        }
                        assertEquals(routeIndex + 1, route.tankWaypoints.length);
                        assertEquals(route.tankFirePoint.x,
                                route.tankWaypoints[routeIndex].x, 0f);
                        assertEquals(route.tankFirePoint.y,
                                route.tankWaypoints[routeIndex].y, 0f);
                        assertEquals(BattlefieldCityLayout.sourceCityX(target, city)
                                        + fireOffsetX(firePositions[target][city]),
                                route.tankFirePoint.x, 0f);
                        assertEquals(BattlefieldCityLayout.sourceCityY(target, city)
                                        + fireOffsetY(firePositions[target][city]),
                                route.tankFirePoint.y, 0f);
                    }
                }
            }
        }
    }

    @Test
    public void rejectsInvalidChinookTankRouteData() throws Exception {
        Map<String, FlashBattleRoutes.Point> anchors = loadAnchors();
        assertThrows(IllegalArgumentException.class, () ->
                FlashBattleRoutes.createSoldierThreeAttack(-1, 0, 0, 1, "blue", anchors));
        assertThrows(IllegalArgumentException.class, () ->
                FlashBattleRoutes.createSoldierThreeAttack(0, 5, 0, 1, "blue", anchors));
        assertThrows(IllegalArgumentException.class, () ->
                FlashBattleRoutes.createSoldierThreeAttack(0, 1, 5, 1, "blue", anchors));
        assertThrows(IllegalArgumentException.class, () ->
                FlashBattleRoutes.createSoldierThreeAttack(0, 1, 0, 3, "blue", anchors));
        assertThrows(IllegalArgumentException.class, () ->
                FlashBattleRoutes.createSoldierThreeAttack(0, 1, 0, 1, "purple", anchors));
        assertThrows(IllegalArgumentException.class, () ->
                FlashBattleRoutes.createSoldierThreeAttack(0, 1, 0, 1, "blue", null));
    }

    private static float fireOffsetX(String position) {
        switch (position) {
            case "LB":
            case "LT":
            case "L":
                return -55f;
            case "RT":
            case "R":
                return 55f;
            default:
                return 0f;
        }
    }

    private static float fireOffsetY(String position) {
        switch (position) {
            case "LB": return 20f;
            case "LT": return -25f;
            case "RT": return -25f;
            case "R": return -5f;
            case "B": return 25f;
            case "L": return -5f;
            case "T": return -20f;
            default: throw new AssertionError(position);
        }
    }

    private static boolean hasSourceWaypoint(int player, int waypoint) {
        return (player == 0 && (waypoint == 1 || waypoint == 2 || waypoint == 3
                || waypoint == 4 || waypoint == 5 || waypoint == 6))
                || (player == 1 && waypoint <= 5)
                || (player == 2 && waypoint <= 4)
                || (player == 3 && waypoint <= 5)
                || (player == 4 && waypoint >= 1 && waypoint <= 5);
    }

    private static Map<String, FlashBattleRoutes.Point> loadAnchors() throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        return FlashBattleRoutes.parseAnchors(json);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(moduleAsset)) {
            return moduleAsset;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
