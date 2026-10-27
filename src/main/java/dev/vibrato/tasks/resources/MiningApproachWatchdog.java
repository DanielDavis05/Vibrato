package dev.vibrato.tasks.resources;

import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;

/** Measures durable progress toward one target, even across task interruptions. */
final class MiningApproachWatchdog<P> {
    private static final long TIMEOUT_NANOS = 45_000_000_000L;
    private static final double MIN_IMPROVEMENT = 1.0;
    private P anchor;
    private double bestDistance;
    private long lastProgress;

    boolean stalled(P target, long now, BiPredicate<P, P> sameApproach, ToDoubleFunction<P> distance) {
        if (anchor == null || !sameApproach.test(anchor, target)) {
            anchor = target;
            bestDistance = distance.applyAsDouble(anchor);
            lastProgress = now;
            return false;
        }
        double current = distance.applyAsDouble(anchor);
        if (current <= bestDistance - MIN_IMPROVEMENT) {
            bestDistance = current;
            lastProgress = now;
        }
        return now - lastProgress >= TIMEOUT_NANOS;
    }

    void excavationCompleted(long now) {
        if (anchor != null) lastProgress = now;
    }

    void clear() { anchor = null; }
}
