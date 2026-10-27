package dev.vibrato.tasks.movement;

import java.util.Objects;

/** Measures active time without objective progress toward one dropped item. */
final class PickupApproachWatchdog<T> {
    static final long TIMEOUT_NANOS = 45_000_000_000L;
    private static final double MIN_IMPROVEMENT = 0.75;
    private T target;
    private double bestDistance;
    private int bestInventory;
    private long lastSample = -1;
    private long noProgressNanos;

    boolean stalled(T current, long now, double distance, int inventory, boolean suspended, boolean miningProgress) {
        if (!Objects.equals(target, current)) {
            target = current;
            bestDistance = distance;
            bestInventory = inventory;
            noProgressNanos = 0;
            lastSample = now;
            return false;
        }
        if (lastSample < 0) lastSample = now;
        if (!suspended) noProgressNanos += Math.max(0, now - lastSample);
        lastSample = now;
        if (distance <= bestDistance - MIN_IMPROVEMENT || inventory > bestInventory || miningProgress) {
            bestDistance = Math.min(bestDistance, distance);
            bestInventory = Math.max(bestInventory, inventory);
            noProgressNanos = 0;
        }
        return !suspended && noProgressNanos >= TIMEOUT_NANOS;
    }

    void suspend() { lastSample = -1; }
    void clear() { target = null; lastSample = -1; noProgressNanos = 0; }
}
