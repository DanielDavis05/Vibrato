package dev.vibrato.tasks;

import java.util.HashMap;
import java.util.Map;

/** Local to a resource task; a stalled container becomes eligible on entering reach. */
final class DeferredContainers<K> {
    private static final double INTERACTION_RANGE_SQ = 16;
    // Value is true once the player has been outside interaction range.
    private final Map<K, Boolean> deferred = new HashMap<>();

    void defer(K key, double distanceSq) { deferred.put(key, distanceSq > INTERACTION_RANGE_SQ); }

    boolean eligible(K key, double distanceSq) {
        Boolean wasOutside = deferred.get(key);
        if (wasOutside == null) return true;
        if (distanceSq > INTERACTION_RANGE_SQ) {
            deferred.put(key, true);
            return false;
        }
        if (wasOutside) {
            deferred.remove(key);
            return true;
        }
        return false;
    }
}
