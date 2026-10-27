package dev.vibrato.nav;

/** Bounded movement toward the next path node during surface-water recovery. */
final class WaterRecovery {
    static final int FORWARD_TICKS = 20;

    private WaterRecovery() {}

    static boolean shouldMove(boolean hasTarget, boolean inWater, int tick,
                              double x, double z, double targetX, double targetZ) {
        return hasTarget && inWater && tick <= FORWARD_TICKS
                && Math.hypot(targetX - x, targetZ - z) >= 0.2;
    }

    static float yaw(double x, double z, double targetX, double targetZ) {
        return (float) Math.toDegrees(Math.atan2(x - targetX, targetZ - z));
    }
}
