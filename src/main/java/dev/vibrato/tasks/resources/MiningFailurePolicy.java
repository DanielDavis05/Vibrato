package dev.vibrato.tasks.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Short-lived failures for a particular approach to connected logs. */
final class MiningFailurePolicy<P> {
    private static final long RETRY_NANOS = 30_000_000_000L;
    private static final double NEW_APPROACH_DISTANCE_SQ = 16;
    private final List<Failure<P>> failures = new ArrayList<>();

    void record(P pos, double x, double y, double z, long now) {
        expire(x, y, z, now);
        failures.add(new Failure<>(pos, x, y, z, now));
    }

    // An external stuck detector has exhausted its recovery ladder for this
    // approach. Treat that as sufficient evidence to try another trunk.
    void rejectApproach(P pos, double x, double y, double z, long now) {
        record(pos, x, y, z, now);
        record(pos, x, y, z, now);
    }

    boolean suppress(P pos, double x, double y, double z, long now, BiPredicate<P, P> connected) {
        expire(x, y, z, now);
        int related = 0;
        for (Failure<P> failure : failures) {
            if (connected.test(pos, failure.pos) && ++related >= 2) return true;
        }
        return false;
    }

    void clear() { failures.clear(); }

    private void expire(double x, double y, double z, long now) {
        failures.removeIf(f -> now - f.time >= RETRY_NANOS
                || (x - f.x) * (x - f.x) + (y - f.y) * (y - f.y) + (z - f.z) * (z - f.z) >= NEW_APPROACH_DISTANCE_SQ);
    }

    private record Failure<P>(P pos, double x, double y, double z, long time) { }
}
