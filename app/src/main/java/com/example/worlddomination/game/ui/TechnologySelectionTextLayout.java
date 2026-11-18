package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.TechnologyCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Cached paragraph wrapping for the source research name field. */
public final class TechnologySelectionTextLayout {
    public static final int VISIBLE_LINES = 6;
    public static final float LINE_SPACING = 12f;
    // ceil(94.4 - -2) - 4: original EditText 3329, including its text inset.
    private static final float NAME_WIDTH = 93f;
    private final Map<String, List<String>> lines = new HashMap<>();

    public TechnologySelectionTextLayout(ToDoubleFunction<String> sourceSizeWidth) {
        for (TechnologyCatalog.Definition technology : TechnologyCatalog.all()) {
            List<String> wrapped = new ArrayList<>();
            String line = "";
            for (String word : technology.prettyName.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && sourceSizeWidth.applyAsDouble(candidate) > NAME_WIDTH) {
                    wrapped.add(line);
                    line = word;
                } else {
                    line = candidate;
                }
            }
            wrapped.add(line);
            lines.put(technology.name, Collections.unmodifiableList(wrapped));
        }
    }

    public List<String> nameLines(String technology) {
        List<String> result = lines.get(technology);
        if (result == null) throw new IllegalArgumentException("Unknown technology: " + technology);
        return result;
    }
}
