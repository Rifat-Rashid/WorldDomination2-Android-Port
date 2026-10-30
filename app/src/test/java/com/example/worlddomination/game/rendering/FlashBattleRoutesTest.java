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
