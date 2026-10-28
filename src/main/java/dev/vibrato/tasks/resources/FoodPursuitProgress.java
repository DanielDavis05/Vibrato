package dev.vibrato.tasks.resources;

/** A target-scoped chase lease, measured only while its attack task is active. */
final class FoodPursuitProgress {
    private static final int STALL_TICKS = 600;
    private static final double MIN_APPROACH = 0.75;
    private int lastProgress = -1;
    private double bestDistance;
    private float bestHealth;

    boolean stalled(int tick, double distanceSquared, float health) {
        double distance = Math.sqrt(distanceSquared);
        if (lastProgress < 0) {
            lastProgress = tick;
            bestDistance = distance;
            bestHealth = health;
        } else if (distance <= bestDistance - MIN_APPROACH || health < bestHealth) {
            bestDistance = Math.min(bestDistance, distance);
            bestHealth = Math.min(bestHealth, health);
            lastProgress = tick;
        }
        return tick - lastProgress >= STALL_TICKS;
    }

    static boolean retainOnRescan(boolean alive, boolean active) { return alive && active; }

    void reset() { lastProgress = -1; }
}
