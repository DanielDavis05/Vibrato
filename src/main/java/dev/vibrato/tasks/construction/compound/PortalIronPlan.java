package dev.vibrato.tasks.construction.compound;

final class PortalIronPlan {
    private PortalIronPlan() { }

    /** Total ingots to hold before crafting missing buckets, including the igniter reserve. */
    static int ingotGoal(int bucketCount, boolean hasIgniter) {
        if (bucketCount >= 2) return 0;
        return 3 * (2 - Math.max(0, bucketCount)) + (hasIgniter ? 0 : 1);
    }
}
