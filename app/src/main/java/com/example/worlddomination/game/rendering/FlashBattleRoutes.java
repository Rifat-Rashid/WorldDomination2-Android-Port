package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-authored routes used by the Flash battle unit presenter. */
public final class FlashBattleRoutes {
    public static final class Point {
        public final float x;
        public final float y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class SoldierPath {
        public final Point[] waypoints;
        public final int fireOffsetX;
        public final int fireOffsetY;

        private SoldierPath(Point[] waypoints, int fireOffsetX, int fireOffsetY) {
            this.waypoints = waypoints;
            this.fireOffsetX = fireOffsetX;
            this.fireOffsetY = fireOffsetY;
        }
    }

    public static final class SoldierOneAttack {
        public final int attackerPlayerId;
        public final int targetPlayerId;
        public final Point boatStart;
        public final Point[] boatWaypoints;
        public final int[] boatIslandIds;
        public final SoldierPath[] soldiers;

        private SoldierOneAttack(int attackerPlayerId, int targetPlayerId,
                                 Point boatStart, Point[] boatWaypoints,
                                 int[] boatIslandIds, SoldierPath[] soldiers) {
            this.attackerPlayerId = attackerPlayerId;
            this.targetPlayerId = targetPlayerId;
            this.boatStart = boatStart;
            this.boatWaypoints = boatWaypoints;
            this.boatIslandIds = boatIslandIds;
            this.soldiers = soldiers;
        }
    }

    private static final int SOLDIER_COUNT = 3;
    private static final int[][][] SOLDIER_WAYPOINTS = {
            {{0}, {1, 2}, {1, 2, 3}, {1, 6}, {0}},
            {{1}, {1}, {2}, {2, 3, 5}, {4}},
            {{0}, {1}, {1, 2}, {1, 2, 3}, {4}},
            {{0}, {2}, {1, 2}, {1, 2, 3, 5}, {4}},
            {{0}, {1}, {2, 3}, {2, 3, 4}, {5}}
    };
    private static final String[][] SOLDIER_END_POSITIONS = {
            {"RT", "B", "L", "B", "L"},
            {"T", "LT", "LT", "LB", "LB"},
            {"B", "RT", "RT", "LT", "T"},
            {"L", "L", "LB", "R", "R"},
            {"T", "R", "B", "B", "L"}
    };
    private static final float[][][] FIRE_OFFSETS = {
            {{-55, -5}, {-40, 40}, {-10, 20}}, // LB
            {{-55, -5}, {-40, -20}, {-20, -40}}, // LT
            {{35, -5}, {30, -20}, {-20, -40}}, // RT
            {{55, -5}, {40, 15}, {40, -15}}, // R
            {{-55, -5}, {0, 25}, {35, -5}}, // B
            {{-60, -5}, {-55, 15}, {-55, -15}}, // L
            {{-55, -20}, {-40, -30}, {25, -20}} // T
    };
    private static final String[] FIRE_POSITION_NAMES = {"LB", "LT", "RT", "R", "B", "L", "T"};
    private static final float[][] LAND_UNIT_POSITIONS = {
            {420, 420}, {160, 201}, {563, 108}, {260, 500}, {562, 511}
    };
    private static final float[][] BOAT_STARTS = {
            {410, 474, 7}, {179, 252, 11}, {528, 145, 1},
            {311, 525, 7}, {575, 510, 5}
    };
    private static final float[][] BOAT_WAYPOINTS = {
            {380, 156}, {474, 192}, {578, 240}, {655, 307}, {582, 455},
            {493, 498}, {400, 530}, {337, 504}, {254, 411}, {200, 360},
            {217, 266}, {240, 250}, {295, 194}
    };

    private FlashBattleRoutes() { }

    public static Map<String, Point> parseAnchors(String json) {
        JsonValue root = new JsonReader().parse(json);
        Map<String, Point> points = new LinkedHashMap<>();
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            if (entry.isArray() && entry.size == 2) {
                points.put(entry.name, new Point(entry.getFloat(0), entry.getFloat(1)));
            }
        }
        return Collections.unmodifiableMap(points);
    }

    public static boolean supports(GameState.CombatEvent event) {
        return event != null && event.type == GameState.CombatEventType.DIRECT_ATTACK
                && "soldier1".equals(event.weaponName);
    }

    /** Builds the source soldier1 boat arrival and three infantry firing routes. */
    public static SoldierOneAttack createSoldierOneAttack(
            int attackerPlayerId, int targetPlayerId, int targetCityIndex,
            Map<String, Point> anchors, ReferenceRandom presentationRandom) {
        validatePlayer(attackerPlayerId);
        validatePlayer(targetPlayerId);
        if (targetCityIndex < 0 || targetCityIndex >= BattlefieldCityLayout.CITIES_PER_PLAYER
                || anchors == null || presentationRandom == null) {
            throw new IllegalArgumentException("Invalid soldier1 presentation data");
        }

        int boatTargetIndex = (int) BOAT_STARTS[targetPlayerId][2];
        int boatIndex = (int) BOAT_STARTS[attackerPlayerId][2];
        List<Point> boatRoute = new ArrayList<>();
        List<Integer> islandIds = new ArrayList<>();
        while (boatIndex != boatTargetIndex) {
            float[] coordinate = BOAT_WAYPOINTS[boatIndex];
            boatRoute.add(new Point(coordinate[0], coordinate[1]));
            islandIds.add(nearestIsland(coordinate[0], coordinate[1]));
            boatIndex = (boatIndex + 1) % BOAT_WAYPOINTS.length;
        }
        float[] targetBoat = BOAT_STARTS[targetPlayerId];
        boatRoute.add(new Point(targetBoat[0], targetBoat[1]));
        islandIds.add(targetPlayerId);

        float cityX = BattlefieldCityLayout.sourceCityX(targetPlayerId, targetCityIndex);
        float cityY = BattlefieldCityLayout.sourceCityY(targetPlayerId, targetCityIndex) - 20f;
        float[][] offsets = fireOffsets(SOLDIER_END_POSITIONS[targetPlayerId][targetCityIndex]);
        SoldierPath[] soldiers = new SoldierPath[SOLDIER_COUNT];
        for (int soldier = 0; soldier < SOLDIER_COUNT; soldier++) {
            List<Point> path = new ArrayList<>();
            path.add(new Point(LAND_UNIT_POSITIONS[targetPlayerId][0],
                    LAND_UNIT_POSITIONS[targetPlayerId][1]));
            for (int waypoint : SOLDIER_WAYPOINTS[targetPlayerId][targetCityIndex]) {
                if (waypoint == 0) {
                    continue;
                }
                Point anchor = anchors.get("swp" + targetPlayerId + "_" + waypoint);
                if (anchor == null) {
                    throw new IllegalArgumentException("Missing source soldier waypoint swp"
                            + targetPlayerId + "_" + waypoint);
                }
                path.add(anchor);
            }
            int offsetX = (int) offsets[soldier][0];
            int offsetY = (int) offsets[soldier][1];
            path.add(new Point(cityX + offsetX + presentationRandom.nextInt(7) - 3,
                    cityY + offsetY + presentationRandom.nextInt(7) - 3));
            path.add(new Point(cityX, cityY));
            soldiers[soldier] = new SoldierPath(path.toArray(new Point[0]), offsetX, offsetY);
        }

        int[] boatIslands = new int[islandIds.size()];
        for (int i = 0; i < boatIslands.length; i++) {
            boatIslands[i] = islandIds.get(i);
        }
        float[] boatStart = BOAT_STARTS[attackerPlayerId];
        return new SoldierOneAttack(attackerPlayerId, targetPlayerId,
                new Point(boatStart[0], boatStart[1]), boatRoute.toArray(new Point[0]),
                boatIslands, soldiers);
    }

    private static float[][] fireOffsets(String position) {
        for (int index = 0; index < FIRE_POSITION_NAMES.length; index++) {
            if (FIRE_POSITION_NAMES[index].equals(position)) {
                return FIRE_OFFSETS[index];
            }
        }
        throw new IllegalStateException("Unknown source soldier fire position " + position);
    }

    private static int nearestIsland(float x, float y) {
        int nearest = 0;
        float distance = Float.MAX_VALUE;
        for (int player = 0; player < LAND_UNIT_POSITIONS.length; player++) {
            float dx = x - LAND_UNIT_POSITIONS[player][0];
            float dy = y - LAND_UNIT_POSITIONS[player][1];
            float candidate = dx * dx + dy * dy;
            if (candidate < distance) {
                distance = candidate;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void validatePlayer(int playerId) {
        if (playerId < 0 || playerId >= BattlefieldCityLayout.PLAYER_COUNT) {
            throw new IllegalArgumentException("Invalid battle player");
        }
    }
}
