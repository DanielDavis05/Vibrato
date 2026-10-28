package dev.vibrato.tasks.movement;

/** Detect a vertical pillar/dig cycle even when individual movements or breaks succeed. */
final class WanderHorizontalProgress {
    private double x, z, since;
    void reset(double x, double z, double now) { this.x = x; this.z = z; since = now; }
    boolean stalled(double x, double z, double now) {
        double dx = x - this.x, dz = z - this.z;
        if (dx * dx + dz * dz >= 9) {
            reset(x, z, now);
            return false;
        }
        return now - since >= 30;
    }
}
