package dev.vibrato.tasks.resources;

import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;

/** A pool approach must make durable progress, even when adjacent sources change. */
final class LiquidApproachProgress<T> {
    private final double timeout;
    private final double minimumApproach;
    private T anchor;
    private double bestDistance;
    private double lastProgress;
    private long lastBlocksBroken;
    private boolean terrainCreditUsed;
    private double pausedAt = Double.NaN;

    LiquidApproachProgress(double timeout, double minimumApproach) {
        this.timeout = timeout;
        this.minimumApproach = minimumApproach;
    }

    boolean stalled(T source, BiPredicate<T, T> samePool, ToDoubleFunction<T> distance,
                    long blocksBroken, double now) {
        if (anchor == null || !samePool.test(anchor, source)) {
            anchor = source;
            bestDistance = distance.applyAsDouble(anchor);
            lastBlocksBroken = blocksBroken;
            lastProgress = now;
            terrainCreditUsed = false;
        } else {
            double current = distance.applyAsDouble(anchor);
            if (current <= bestDistance - minimumApproach) {
                bestDistance = current;
                lastProgress = now;
                terrainCreditUsed = false;
            } else if (blocksBroken > lastBlocksBroken && !terrainCreditUsed) {
                // Allow one interval for necessary excavation, not an
                // unbounded stream of pillar or repeated detour breaks.
                lastProgress = now;
                terrainCreditUsed = true;
            }
            lastBlocksBroken = blocksBroken;
        }
        return now - lastProgress >= timeout;
    }

    T anchor() { return anchor; }
    void pause(double now) { if (anchor != null && Double.isNaN(pausedAt)) pausedAt = now; }
    void resume(double now) {
        if (!Double.isNaN(pausedAt)) {
            lastProgress += Math.max(0, now - pausedAt);
            pausedAt = Double.NaN;
        }
    }
    void reset() { anchor = null; pausedAt = Double.NaN; }
}
