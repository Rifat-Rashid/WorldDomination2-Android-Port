package com.example.worlddomination.game.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Weapon stock and production metadata recovered from WeaponStock/WeaponSlot. */
public final class WeaponCatalog {
    public static final class Definition {
        public final String name;
        public final String technology;
        public final String type;
        /** Counter weapon name recovered from WeaponStock.as, or empty when none exists. */
        public final String counteredBy;
        public final int level;
        public final int costMillions;
        public final int productionGroup;
        public final int initialStock;
        public final int maxKillsMillions;

        Definition(String name, String technology, String type, String counteredBy, int level,
                   int costMillions, int productionGroup, int initialStock,
                   int maxKillsMillions) {
            this.name = name;
            this.technology = technology;
            this.type = type;
            this.counteredBy = counteredBy;
            this.level = level;
            this.costMillions = costMillions;
            this.productionGroup = productionGroup;
            this.initialStock = initialStock;
            this.maxKillsMillions = maxKillsMillions;
        }
    }

    private static final Map<String, Definition> BY_NAME = new LinkedHashMap<>();
    private static final List<Definition> ALL;

    static {
        addLine("rocket", "rocket", "rocket", "antiRocket", 1, 0,
                new int[]{5, 10, 15, 20}, new int[]{0, 0, 0, 0});
        addLine("warhead", "warhead", "rocketload", "", 2, 0,
                new int[]{5, 10, 15, 20}, new int[]{15, 40, 90, 150});
        addLine("bioload", "bioload", "rocketload", "", 3, 0,
                new int[]{5, 10, 15, 20}, new int[]{10, 35, 60, 80});
        addLine("soldier", "soldier", "soldier", "", 4, 0,
                new int[]{5, 10, 15, 20}, new int[]{4, 10, 21, 35});
        addLine("spy", "spy", "spy", "", 5, 1,
                new int[]{5, 10, 15, 20}, new int[]{0, 0, 0, 0});
        add("plane1", "plane1", "plane", "antiAir", 2, 5, 6, 0, 0);
        add("plane2", "plane2", "plane", "antiAir", 4, 10, 6, 0, 0);
        add("sub", "sub", "sub", "", 3, 50, 7, 0, 0);
        add("ship", "ship", "ship", "antiAir", 3, 65, 7, 0, 0);
        add("space", "space", "space", "", 3, 70, 7, 0, 0);
        add("specialLoad", "specialLoad", "specialLoad", "", 4, 80, 7, 0, 200000);
        add("antiAir", "antiAir", "anti", "", 1, 15, 8, 2, 0);
        add("antiRocket", "antiRocket", "anti", "", 1, 15, 8, 2, 0);
        ALL = Collections.unmodifiableList(new ArrayList<>(BY_NAME.values()));
    }

    private WeaponCatalog() { }

    private static void addLine(String stem, String technologyStem, String type,
                                String counteredBy,
                                int productionGroup, int firstLevelInitialStock,
                                int[] costs, int[] maxKills) {
        for (int index = 0; index < costs.length; index++) {
            String name = stem + (index + 1);
            add(name, technologyStem + (index + 1), type, counteredBy, index + 1,
                    costs[index], productionGroup,
                    index == 0 ? firstLevelInitialStock : 0, maxKills[index]);
        }
    }

    private static void add(String name, String technology, String type,
                            String counteredBy, int level,
                            int cost, int productionGroup, int initialStock,
                            int maxKillsMillions) {
        BY_NAME.put(name, new Definition(name, technology, type, counteredBy, level, cost,
                productionGroup, initialStock, maxKillsMillions));
    }

    public static List<Definition> all() {
        return ALL;
    }

    public static Definition byName(String name) {
        return BY_NAME.get(name);
    }
}
