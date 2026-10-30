package com.example.worlddomination.game.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Technology data recovered from the reference TechTree constructor. */
final class TechnologyCatalog {
    static final class Definition {
        final int id;
        final String name;
        final int costMillions;
        final int group;
        final int level;
        final int strengthWeight;
        final String prerequisite;
        final String prettyName;

        Definition(int id, String name, int costMillions, int group, int level,
                   int strengthWeight, String prerequisite, String prettyName) {
            this.id = id;
            this.name = name;
            this.costMillions = costMillions;
            this.group = group;
            this.level = level;
            this.strengthWeight = strengthWeight;
            this.prerequisite = prerequisite;
            this.prettyName = prettyName;
        }
    }

    private static final Map<String, Definition> BY_NAME = new LinkedHashMap<>();
    private static final List<Definition> ALL;

    static {
        addLine(1, "rocket", 1, new int[]{5, 10, 15, 20});
        addLine(5, "warhead", 2, new int[]{5, 10, 15, 20});
        addLine(9, "bioload", 3, new int[]{5, 10, 15, 20});
        addLine(13, "soldier", 4, new int[]{5, 10, 15, 20});
        addLine(17, "spy", 5, new int[]{5, 10, 15, 20});
        addLine(21, "plane", 6, new int[]{5, 10});
        add(23, "sub", 20, 7, 1, 3, null, "SUBMARINE");
        add(24, "ship", 40, 7, 2, 5, "sub", "AIRCRAFT CARRIER");
        add(25, "space", 50, 7, 3, 8, "ship", "SPACE ROCKET");
        add(26, "antiAir", 20, 8, 1, 8, null, "ANTI AIR");
        add(27, "antiRocket", 20, 8, 1, 8, null, "ANTI ROCKET");
        add(28, "specialLoad", 50, 7, 4, 8, "space", "SATELLITE");
        ALL = Collections.unmodifiableList(new ArrayList<>(BY_NAME.values()));
    }

    private TechnologyCatalog() { }

    private static void addLine(int firstId, String stem, int group, int[] costs) {
        String previous = null;
        for (int index = 0; index < costs.length; index++) {
            String name = stem + (index + 1);
            int weight = index == 0 ? 0 : index == 1 ? ("plane".equals(stem) ? 4 : 3)
                    : index == 2 ? 5 : 8;
            add(firstId + index, name, costs[index], group, index + 1, weight, previous,
                    prettyName(stem, index + 1));
            previous = name;
        }
    }

    private static void add(int id, String name, int cost, int group, int level,
                            int strengthWeight, String prerequisite, String prettyName) {
        BY_NAME.put(name, new Definition(id, name, cost, group, level, strengthWeight, prerequisite,
                prettyName));
    }

    private static String prettyName(String stem, int level) {
        if ("rocket".equals(stem) || "warhead".equals(stem) || "bioload".equals(stem)) {
            String[] sizes = {"10 MEG", "25 MEG", "50 MEG", "95 MEG"};
            return stem.toUpperCase(java.util.Locale.US) + " (" + sizes[level - 1] + ")";
        }
        if ("soldier".equals(stem)) {
            return new String[]{"SOLDIER", "SEALS", "TANK", "SPECIAL"}[level - 1];
        }
        if ("spy".equals(stem)) {
            return "SPY (LEVEL " + level + ")";
        }
        return level == 1 ? "FIGHTER" : "BOMBER";
    }

    static List<Definition> all() {
        return ALL;
    }

    static Definition byName(String name) {
        return BY_NAME.get(name);
    }
}
