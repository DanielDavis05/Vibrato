package dev.vibrato.tasks.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Temporary per-villager rejection survives interruptions while allowing later state changes. */
final class VillagerRetryTracker {
    private final Map<UUID, Long> rejected = new HashMap<>();
    private final long retryNanos;

    VillagerRetryTracker(long retryNanos) { this.retryNanos = retryNanos; }
    void reject(UUID id, long now) { if (id != null) rejected.put(id, now); }
    boolean available(UUID id, long now) {
        Long since = rejected.get(id);
        if (since == null) return true;
        if (now - since >= retryNanos) { rejected.remove(id); return true; }
        return false;
    }
}
