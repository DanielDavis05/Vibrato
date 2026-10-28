package dev.vibrato.tasks.resources;

import java.util.Objects;

/** A source approach must get closer or change real terrain, not merely oscillate. */
final class LiquidApproachProgress<T> {
    private final double timeout;
    private final double minimumApproach;
    private T target;
    private double bestDistance;
    private double lastProgress;
    private long lastBlocksBroken;

    LiquidApproachProgress(double timeout, double minimumApproach) {
        this.timeout = timeout;
        this.minimumApproach = minimumApproach;
    }

    boolean stalled(T source, double distance, long blocksBroken, double now) {
        if (!Objects.equals(source, target) || target == null) {
            target = source;
            bestDistance = distance;
            lastBlocksBroken = blocksBroken;
            lastProgress = now;
        } else if (distance <= bestDistance - minimumApproach || blocksBroken != lastBlocksBroken) {
            bestDistance = Math.min(bestDistance, distance);
            lastBlocksBroken = blocksBroken;
            lastProgress = now;
        }
        return now - lastProgress >= timeout;
    }

    void reset() { target = null; }
}
