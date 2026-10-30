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

    /** Source route data for the Apache insertion and its three marines. */
    public static final class ApacheMarineAttack {
        public final int attackerPlayerId;
        public final int targetPlayerId;
        public final int actionBuildingSlot;
        public final Point actionBuilding;
        public final Point apacheStart;
        public final Point unloadPoint;
        public final SoldierPath[] marines;

        private ApacheMarineAttack(int attackerPlayerId, int targetPlayerId,
                                   int actionBuildingSlot, Point actionBuilding,
                                   Point apacheStart, Point unloadPoint,
                                   SoldierPath[] marines) {
            this.attackerPlayerId = attackerPlayerId;
            this.targetPlayerId = targetPlayerId;
            this.actionBuildingSlot = actionBuildingSlot;
            this.actionBuilding = actionBuilding;
            this.apacheStart = apacheStart;
            this.unloadPoint = unloadPoint;
            this.marines = marines;
        }
    }

    /** Source route for the special-unit suicide bomber. */
    public static final class SuicideBomberAttack {
        public final int attackerPlayerId;
        public final int targetPlayerId;
        public final int targetCityIndex;
        public final int actionBuildingSlot;
        public final Point actionBuilding;
        public final Point initialPosition;
        public final Point disappearPosition;
        public final Point spawn;
        public final Point[] waypoints;

        private SuicideBomberAttack(int attackerPlayerId, int targetPlayerId,
                                    int targetCityIndex, int actionBuildingSlot,
                                    Point actionBuilding, Point initialPosition,
                                    Point disappearPosition, Point spawn, Point[] waypoints) {
            this.attackerPlayerId = attackerPlayerId;
            this.targetPlayerId = targetPlayerId;
            this.targetCityIndex = targetCityIndex;
            this.actionBuildingSlot = actionBuildingSlot;
            this.actionBuilding = actionBuilding;
            this.initialPosition = initialPosition;
            this.disappearPosition = disappearPosition;
            this.spawn = spawn;
            this.waypoints = waypoints;
        }
    }

    /** Source route for a Chinook insertion followed by tank base and turret playback. */
    public static final class ChinookTankAttack {
        public final int attackerPlayerId;
        public final int targetPlayerId;
        public final int targetCityIndex;
        public final int actionBuildingSlot;
        public final Point actionBuilding;
        public final Point chinookStart;
        public final Point chinookDrop;
        public final Point tankStart;
        public final Point turretStart;
        public final Point tankFirePoint;
        public final Point[] tankWaypoints;
        public final String turretLinkage;

        private ChinookTankAttack(int attackerPlayerId, int targetPlayerId,
                                  int targetCityIndex, int actionBuildingSlot,
                                  Point actionBuilding, Point chinookStart,
                                  Point chinookDrop, Point tankStart, Point turretStart,
                                  Point tankFirePoint, Point[] tankWaypoints,
                                  String turretLinkage) {
            this.attackerPlayerId = attackerPlayerId;
            this.targetPlayerId = targetPlayerId;
            this.targetCityIndex = targetCityIndex;
            this.actionBuildingSlot = actionBuildingSlot;
            this.actionBuilding = actionBuilding;
            this.chinookStart = chinookStart;
            this.chinookDrop = chinookDrop;
            this.tankStart = tankStart;
            this.turretStart = turretStart;
            this.tankFirePoint = tankFirePoint;
            this.tankWaypoints = tankWaypoints;
            this.turretLinkage = turretLinkage;
        }
    }

    /** Source route for a silo launched rocket strike. */
    public static final class RocketStrike {
        /** Nation that launches the rocket. */
        public final int attackerPlayerId;
        /** Nation whose city is targeted. */
        public final int targetPlayerId;
        /** Zero based target city index. */
        public final int targetCityIndex;
        /** One of the attacker's two source silo placements. */
        public final int actionBuildingSlot;
        /** Whether the source counter system intercepted this rocket. */
        public final boolean intercepted;
        /** Flash stage coordinate of the source silo. */
        public final Point silo;
        /** Flash stage coordinate where the rocket begins. */
        public final Point launch;
        /** Flash stage coordinate of the selected city. */
        public final Point target;
        /** Source final waypoint used by the city or self explosion action. */
        public final Point explosion;
        /** Horizontal center of the Flash ellipse. */
        public final float ellipseCenterX;
        /** Vertical center of the Flash ellipse. */
        public final float ellipseCenterY;
        /** Horizontal radius of the Flash ellipse. */
        public final float ellipseRadiusX;
        /** Vertical radius of the Flash ellipse. */
        public final float ellipseRadiusY;
        /** Initial Flash ellipse angle in degrees. */
        public final float startAngle;
        /** Flash source multiplier used to choose the half ellipse direction. */
        public final float directionMultiplier;
        /** Flash target angle at which the rocket switches to its drop action. */
        public final float targetAngle;

        private RocketStrike(int attackerPlayerId, int targetPlayerId, int targetCityIndex,
                             int actionBuildingSlot, boolean intercepted, Point silo,
                             Point launch, Point target, Point explosion,
                             float ellipseCenterX, float ellipseCenterY,
                             float ellipseRadiusX, float ellipseRadiusY,
                             float startAngle, float directionMultiplier, float targetAngle) {
            this.attackerPlayerId = attackerPlayerId;
            this.targetPlayerId = targetPlayerId;
            this.targetCityIndex = targetCityIndex;
            this.actionBuildingSlot = actionBuildingSlot;
            this.intercepted = intercepted;
            this.silo = silo;
            this.launch = launch;
            this.target = target;
            this.explosion = explosion;
            this.ellipseCenterX = ellipseCenterX;
            this.ellipseCenterY = ellipseCenterY;
            this.ellipseRadiusX = ellipseRadiusX;
            this.ellipseRadiusY = ellipseRadiusY;
            this.startAngle = startAngle;
            this.directionMultiplier = directionMultiplier;
            this.targetAngle = targetAngle;
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
    private static final int[][][] TANK_WAYPOINTS = {
            {{0}, {1, 2}, {1, 2, 3}, {1, 6}, {1}},
            {{1}, {1}, {2}, {2, 3, 5}, {4}},
            {{0}, {1}, {1, 2}, {1, 2, 3}, {4}},
            {{0}, {0}, {1, 2}, {1, 2, 3}, {4}},
            {{0}, {1}, {2, 3}, {2, 3, 4}, {5}}
    };
    private static final String[][] TANK_END_POSITIONS = {
            {"RT", "B", "L", "B", "LT"},
            {"T", "LT", "LT", "LB", "LB"},
            {"B", "RT", "RT", "LT", "T"},
            {"L", "LB", "LB", "R", "R"},
            {"T", "L", "B", "B", "L"}
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
    private static final float[][] CHINOOK_STARTS = {
            {420, 310}, {88, 109}, {708, 91}, {105, 510}, {730, 505}
    };
    private static final String[] TANK_FIRE_POSITION_NAMES = {"LB", "LT", "RT", "R", "B", "L", "T"};
    private static final float[][] TANK_FIRE_OFFSETS = {
            {-55, 20}, {-55, -25}, {55, -25}, {55, -5}, {0, 25}, {-55, -5}, {0, -20}
    };
    private static final float[][] SUICIDE_SPAWNS = {
            {420, 420}, {160, 201}, {563, 108}, {260, 500}, {562, 511}
    };
    private static final float[][] BOAT_STARTS = {
            {410, 474, 7}, {179, 252, 11}, {528, 145, 1},
            {311, 525, 7}, {575, 510, 5}
    };
    // battleSrcMC ab{player}_{slot} root-stage coordinates recovered from scene.json.gz.
    private static final float[][] ACTION_BUILDINGS = {
            {386.25f, 352.25f, 432.25f, 296.75f},
            {85.75f, 146.75f, 130.75f, 91.75f},
            {668.75f, 86.75f, 723.75f, 146.25f},
            {156.75f, 566.25f, 102.25f, 506.75f},
            {667.75f, 556.75f, 722.75f, 496.75f}
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

    public static boolean supportsApacheMarine(GameState.CombatEvent event) {
        return event != null && event.type == GameState.CombatEventType.DIRECT_ATTACK
                && "soldier2".equals(event.weaponName)
                && event.actionBuildingSlot >= 1 && event.actionBuildingSlot <= 2;
    }

    public static boolean supportsSuicideBomber(GameState.CombatEvent event) {
        return event != null && event.type == GameState.CombatEventType.DIRECT_ATTACK
                && "soldier4".equals(event.weaponName);
    }

    public static boolean supportsChinookTank(GameState.CombatEvent event) {
        return event != null && event.type == GameState.CombatEventType.DIRECT_ATTACK
                && "soldier3".equals(event.weaponName)
                && event.actionBuildingSlot >= 1 && event.actionBuildingSlot <= 2;
    }

    /** Returns whether the event has the source carrier and silo data for rocket playback. */
    public static boolean supportsRocketStrike(GameState.CombatEvent event) {
        return event != null
                && (event.type == GameState.CombatEventType.STRIKE_IMPACT
                || event.type == GameState.CombatEventType.STRIKE_INTERCEPTED)
                && event.weaponName != null && event.weaponName.matches("rocket[1-4]")
                && event.actionBuildingSlot >= 1 && event.actionBuildingSlot <= 2;
    }

    /** Recreates Battle.createToSpawnUnitsArr's source silo, arc, and impact coordinates. */
    public static RocketStrike createRocketStrike(int attackerPlayerId, int targetPlayerId,
                                                   int targetCityIndex,
                                                   int actionBuildingSlot,
                                                   boolean intercepted) {
        validatePlayer(attackerPlayerId);
        validatePlayer(targetPlayerId);
        if (targetCityIndex < 0 || targetCityIndex >= BattlefieldCityLayout.CITIES_PER_PLAYER
                || actionBuildingSlot < 1 || actionBuildingSlot > 2) {
            throw new IllegalArgumentException("Invalid rocket presentation data");
        }

        Point silo = actionBuildingPosition(attackerPlayerId, actionBuildingSlot);
        float preStartY = silo.y + 25f;
        float startX = silo.x + 10f;
        float startY = preStartY - 21f;
        Point target = new Point(BattlefieldCityLayout.sourceCityX(targetPlayerId,
                targetCityIndex), BattlefieldCityLayout.sourceCityY(targetPlayerId,
                targetCityIndex));
        float ellipseCenterX = Math.min(startX, target.x)
                + Math.abs(startX - target.x) / 2f;
        float ellipseCenterY = Math.min(target.y, preStartY - 42f) - 40f;
        float startAngle = startX > target.x ? 0f : 180f;
        float directionMultiplier = startX > target.x ? 1f : -1f;
        float targetAngle = startAngle == 0f ? -180f : 360f;
        Point launch = new Point(startX, startY);
        Point explosion = new Point(target.x, intercepted ? target.y - 100f : target.y);
        return new RocketStrike(attackerPlayerId, targetPlayerId, targetCityIndex,
                actionBuildingSlot, intercepted, silo, launch, target, explosion,
                ellipseCenterX, ellipseCenterY, Math.abs(startX - target.x) / 2f,
                170f, startAngle, directionMultiplier, targetAngle);
    }

    /** Builds the source soldier3 Chinook, tank path, and color-specific turret linkage. */
    public static ChinookTankAttack createSoldierThreeAttack(
            int attackerPlayerId, int targetPlayerId, int targetCityIndex,
            int actionBuildingSlot, String attackerColorName,
            Map<String, Point> anchors) {
        validatePlayer(attackerPlayerId);
        validatePlayer(targetPlayerId);
        if (targetCityIndex < 0 || targetCityIndex >= BattlefieldCityLayout.CITIES_PER_PLAYER
                || actionBuildingSlot < 1 || actionBuildingSlot > 2 || anchors == null
                || !isSupportedTankColor(attackerColorName)) {
            throw new IllegalArgumentException("Invalid soldier3 presentation data");
        }

        Point actionBuilding = actionBuildingPosition(attackerPlayerId, actionBuildingSlot);
        // The source stores heliStart at (_x - 15, _y + 25), then applies (+15, -100).
        Point chinookStart = new Point(actionBuilding.x, actionBuilding.y - 75f);
        Point chinookDrop = new Point(LAND_UNIT_POSITIONS[targetPlayerId][0],
                LAND_UNIT_POSITIONS[targetPlayerId][1] - 40f);

        List<Point> tankPath = new ArrayList<>();
        int[] sourceWaypoints = TANK_WAYPOINTS[targetPlayerId][targetCityIndex];
        if (sourceWaypoints[0] != 0) {
            for (int waypoint : sourceWaypoints) {
                Point anchor = anchors.get("twp" + targetPlayerId + "_" + waypoint);
                if (anchor == null) {
                    throw new IllegalArgumentException("Missing source tank waypoint twp"
                            + targetPlayerId + "_" + waypoint);
                }
                tankPath.add(anchor);
            }
        }

        Point fireOffset = tankFireOffset(TANK_END_POSITIONS[targetPlayerId][targetCityIndex]);
        Point city = new Point(BattlefieldCityLayout.sourceCityX(targetPlayerId, targetCityIndex),
                BattlefieldCityLayout.sourceCityY(targetPlayerId, targetCityIndex));
        Point tankFirePoint = new Point(city.x + fireOffset.x, city.y + fireOffset.y);
        tankPath.add(tankFirePoint);

        float[] start = CHINOOK_STARTS[attackerPlayerId];
        Point tankStart = new Point(start[0] - 210f, start[1] - 210f);
        Point turretStart = new Point(start[0] - 40f, start[1] - 40f);
        return new ChinookTankAttack(attackerPlayerId, targetPlayerId, targetCityIndex,
                actionBuildingSlot, actionBuilding, chinookStart, chinookDrop,
                tankStart, turretStart, tankFirePoint, tankPath.toArray(new Point[0]),
                "tankTop_" + attackerColorName);
    }

    /** Builds the source tank-waypoint path used by the soldier4 suicide unit. */
    public static SuicideBomberAttack createSuicideBomberAttack(
            int attackerPlayerId, int targetPlayerId, int targetCityIndex,
            Map<String, Point> anchors) {
        return createSuicideBomberAttack(attackerPlayerId, targetPlayerId, targetCityIndex,
                1, anchors);
    }

    /** Builds the source unit route, including its launch-building fade-out position. */
    public static SuicideBomberAttack createSuicideBomberAttack(
            int attackerPlayerId, int targetPlayerId, int targetCityIndex,
            int actionBuildingSlot, Map<String, Point> anchors) {
        validatePlayer(attackerPlayerId);
        validatePlayer(targetPlayerId);
        if (targetCityIndex < 0 || targetCityIndex >= BattlefieldCityLayout.CITIES_PER_PLAYER
                || actionBuildingSlot < 1 || actionBuildingSlot > 2 || anchors == null) {
            throw new IllegalArgumentException("Invalid suicide-bomber presentation data");
        }

        List<Point> route = new ArrayList<>();
        int[] sourceWaypoints = TANK_WAYPOINTS[targetPlayerId][targetCityIndex];
        if (sourceWaypoints[0] != 0) {
            for (int waypoint : sourceWaypoints) {
                Point anchor = anchors.get("twp" + targetPlayerId + "_" + waypoint);
                if (anchor == null) {
                    throw new IllegalArgumentException("Missing source tank waypoint twp"
                            + targetPlayerId + "_" + waypoint);
                }
                // Battle.createToSpawnUnitsArr omits anchors whose source x coordinate is zero.
                if (anchor.x != 0f) {
                    route.add(anchor);
                }
            }
        }
        Point city = new Point(BattlefieldCityLayout.sourceCityX(targetPlayerId,
                targetCityIndex), BattlefieldCityLayout.sourceCityY(targetPlayerId,
                targetCityIndex));
        route.add(city);
        Point actionBuilding = actionBuildingPosition(attackerPlayerId, actionBuildingSlot);
        // View.showActionBuildingFromObj stores heliStart at (buildingX - 15, buildingY + 25).
        // The suicide unit starts at heliStart + (25, -50) and fades toward (heliStartX,
        // heliStartY - 70) before it disappears and reappears at its home island.
        Point initialPosition = new Point(actionBuilding.x + 10f, actionBuilding.y - 25f);
        Point disappearPosition = new Point(actionBuilding.x - 15f, actionBuilding.y - 45f);
        return new SuicideBomberAttack(attackerPlayerId, targetPlayerId, targetCityIndex,
                actionBuildingSlot, actionBuilding, initialPosition, disappearPosition,
                new Point(SUICIDE_SPAWNS[attackerPlayerId][0],
                        SUICIDE_SPAWNS[attackerPlayerId][1]), route.toArray(new Point[0]));
    }

    /** Returns the source Flash placement for a nation's first or second action building. */
    public static Point actionBuildingPosition(int playerId, int slot) {
        validatePlayer(playerId);
        if (slot < 1 || slot > 2) {
            throw new IllegalArgumentException("Invalid source action-building slot");
        }
        int offset = (slot - 1) * 2;
        return new Point(ACTION_BUILDINGS[playerId][offset],
                ACTION_BUILDINGS[playerId][offset + 1]);
    }

    /** Builds the source soldier2 Apache route and three marine insertion paths. */
    public static ApacheMarineAttack createSoldierTwoAttack(
            int attackerPlayerId, int targetPlayerId, int targetCityIndex,
            int actionBuildingSlot, Map<String, Point> anchors,
            ReferenceRandom presentationRandom) {
        validatePlayer(attackerPlayerId);
        validatePlayer(targetPlayerId);
        if (targetCityIndex < 0 || targetCityIndex >= BattlefieldCityLayout.CITIES_PER_PLAYER
                || actionBuildingSlot < 1 || actionBuildingSlot > 2
                || anchors == null || presentationRandom == null) {
            throw new IllegalArgumentException("Invalid soldier2 presentation data");
        }

        Point actionBuilding = actionBuildingPosition(attackerPlayerId, actionBuildingSlot);
        // View.showActionBuildingFromObj sets heliStart to (_x - 15, _y + 25),
        // then Unit("apache") adds (+25, -25) when waitForHeliStart releases.
        Point apacheStart = new Point(actionBuilding.x + 10f, actionBuilding.y);
        Point marineBase = new Point(LAND_UNIT_POSITIONS[targetPlayerId][0],
                LAND_UNIT_POSITIONS[targetPlayerId][1]);
        Point unloadPoint = new Point(marineBase.x + 35f, marineBase.y - 10f);

        float cityX = BattlefieldCityLayout.sourceCityX(targetPlayerId, targetCityIndex);
        float cityY = BattlefieldCityLayout.sourceCityY(targetPlayerId, targetCityIndex);
        float[][] offsets = fireOffsets(SOLDIER_END_POSITIONS[targetPlayerId][targetCityIndex]);
        SoldierPath[] marines = new SoldierPath[SOLDIER_COUNT];
        for (int marine = 0; marine < SOLDIER_COUNT; marine++) {
            List<Point> path = new ArrayList<>();
            path.add(marineBase);
            for (int waypoint : SOLDIER_WAYPOINTS[targetPlayerId][targetCityIndex]) {
                if (waypoint == 0) {
                    continue;
                }
                Point anchor = anchors.get("swp" + targetPlayerId + "_" + waypoint);
                if (anchor == null) {
                    throw new IllegalArgumentException("Missing source marine waypoint swp"
                            + targetPlayerId + "_" + waypoint);
                }
                path.add(anchor);
            }
            int offsetX = (int) offsets[marine][0];
            int offsetY = (int) offsets[marine][1];
            path.add(new Point(cityX + offsetX + presentationRandom.nextInt(7) - 3,
                    cityY + offsetY + presentationRandom.nextInt(7) - 3));
            path.add(new Point(cityX, cityY));
            marines[marine] = new SoldierPath(path.toArray(new Point[0]), offsetX, offsetY);
        }

        return new ApacheMarineAttack(attackerPlayerId, targetPlayerId,
                actionBuildingSlot, actionBuilding, apacheStart, unloadPoint, marines);
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

    private static Point tankFireOffset(String position) {
        for (int index = 0; index < TANK_FIRE_POSITION_NAMES.length; index++) {
            if (TANK_FIRE_POSITION_NAMES[index].equals(position)) {
                return new Point(TANK_FIRE_OFFSETS[index][0], TANK_FIRE_OFFSETS[index][1]);
            }
        }
        throw new IllegalStateException("Unknown source tank fire position " + position);
    }

    private static boolean isSupportedTankColor(String colorName) {
        return "blue".equals(colorName) || "red".equals(colorName)
                || "orange".equals(colorName) || "yellow".equals(colorName)
                || "green".equals(colorName);
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
