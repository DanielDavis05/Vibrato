package dev.vibrato.tasks.movement;

/** Bounds detours for incidental resource drops; explicit pickup tasks stay unrestricted. */
public final class OpportunisticPickupPolicy {
    private OpportunisticPickupPolicy() {}

    public static boolean eligible(double dx, double dy, double dz, double configuredRange) {
        if (!dev.vibrato.human.HumanComponents.behavior()) return false;
        double horizontalSquared = dx * dx + dz * dz;
        if (Math.abs(dy) > 6 || horizontalSquared > 24 * 24) return false;
        return configuredRange < 0 || horizontalSquared + dy * dy <= configuredRange * configuredRange;
    }
}
