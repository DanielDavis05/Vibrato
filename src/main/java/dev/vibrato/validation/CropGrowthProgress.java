package dev.vibrato.validation;

import java.util.HashMap;
import java.util.Map;

/** Counts only age increases observed at the same crop position and type. */
final class CropGrowthProgress {
    private Map<String, Integer> previous = Map.of();
    private long increases;

    long observe(Map<String, Integer> current) {
        for (var entry : current.entrySet()) {
            Integer old = previous.get(entry.getKey());
            if (old != null && entry.getValue() > old) increases += entry.getValue() - old;
        }
        previous = new HashMap<>(current);
        return increases;
    }
}
