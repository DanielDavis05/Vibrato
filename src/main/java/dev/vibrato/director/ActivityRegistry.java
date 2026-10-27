package dev.vibrato.director;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalog of everything the planner may choose from. Registration order is preserved, which keeps sampling
 * deterministic for a given seed.
 */
public final class ActivityRegistry {

    private final Map<String, Activity> byId = new LinkedHashMap<>();

    /** @throws IllegalArgumentException if the id is already taken */
    public void register(Activity activity) {
        if (byId.putIfAbsent(activity.id(), activity) != null) {
            throw new IllegalArgumentException("Duplicate activity id: " + activity.id());
        }
    }

    public List<Activity> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public Activity get(String id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    public List<Activity> inArea(FeatureArea area) {
        List<Activity> result = new ArrayList<>();
        for (Activity activity : byId.values()) {
            if (activity.area() == area) {
                result.add(activity);
            }
        }
        return result;
    }

    /** Activities whose {@link Activity#provides()} contains the fact. */
    public List<Activity> providersOf(Fact fact) {
        List<Activity> result = new ArrayList<>();
        for (Activity activity : byId.values()) {
            if (activity.provides().contains(fact)) {
                result.add(activity);
            }
        }
        return result;
    }
}
