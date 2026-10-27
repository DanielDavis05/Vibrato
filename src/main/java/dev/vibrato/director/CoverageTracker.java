package dev.vibrato.director;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-bot record of what has been exercised: uses per {@link FeatureArea} and per activity, plus failure cooldowns.
 * The {@link PlanSampler} boosts under-used areas, so this is what makes every feature eventually get hit.
 */
public final class CoverageTracker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<FeatureArea, Integer> areaUses = new EnumMap<>(FeatureArea.class);
    private final Map<String, Integer> activityUses = new TreeMap<>();
    private final Map<String, Integer> cooldowns = new TreeMap<>();

    public void record(Activity activity) {
        areaUses.merge(activity.area(), 1, Integer::sum);
        activityUses.merge(activity.id(), 1, Integer::sum);
    }

    public int uses(FeatureArea area) {
        return areaUses.getOrDefault(area, 0);
    }

    public int uses(String id) {
        return activityUses.getOrDefault(id, 0);
    }

    /** Keeps the activity out of plans for the next {@code cooldownPlans} plans. */
    public void recordFailure(String id, int cooldownPlans) {
        cooldowns.merge(id, cooldownPlans, Math::max);
    }

    public boolean isOnCooldown(String id) {
        return cooldowns.getOrDefault(id, 0) > 0;
    }

    /** Called once per completed plan. */
    public void tickPlan() {
        cooldowns.replaceAll((id, plans) -> plans - 1);
        cooldowns.values().removeIf(plans -> plans <= 0);
    }

    /** Uses per area, including areas that were never used. */
    public Map<FeatureArea, Integer> snapshot() {
        Map<FeatureArea, Integer> result = new EnumMap<>(FeatureArea.class);
        for (FeatureArea area : FeatureArea.values()) {
            result.put(area, uses(area));
        }
        return Collections.unmodifiableMap(result);
    }

    public void save(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        State state = new State(new EnumMap<>(areaUses), new TreeMap<>(activityUses), new TreeMap<>(cooldowns));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
    }

    /** A missing, unreadable or corrupt file yields an empty tracker. */
    public static CoverageTracker load(Path path) {
        CoverageTracker tracker = new CoverageTracker();
        if (path == null || !Files.isRegularFile(path)) {
            return tracker;
        }
        try {
            State state = MAPPER.readValue(path.toFile(), State.class);
            if (state == null) {
                return tracker;
            }
            if (state.areas() != null) {
                state.areas().forEach((area, uses) -> {
                    if (area != null && uses != null) tracker.areaUses.put(area, uses);
                });
            }
            if (state.activities() != null) {
                state.activities().forEach((id, uses) -> {
                    if (id != null && uses != null) tracker.activityUses.put(id, uses);
                });
            }
            if (state.cooldowns() != null) {
                state.cooldowns().forEach((id, plans) -> {
                    if (id != null && plans != null && plans > 0) tracker.cooldowns.put(id, plans);
                });
            }
            return tracker;
        } catch (IOException | RuntimeException e) {
            return new CoverageTracker();
        }
    }

    private record State(Map<FeatureArea, Integer> areas, Map<String, Integer> activities, Map<String, Integer> cooldowns) {}
}
