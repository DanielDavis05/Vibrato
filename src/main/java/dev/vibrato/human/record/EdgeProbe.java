package dev.vibrato.human.record;

/**
 * Pure geometry for the trace {@code edge} field: how far ahead of the player the ground drops away. No Minecraft
 * types here, so it can be unit-tested. The world is reached through {@link GroundLookup}.
 */
public final class EdgeProbe {

    /**
     * Returned by a {@link GroundLookup} when no solid block is found at or above the search floor for that column.
     */
    public static final int NONE = Integer.MIN_VALUE;

    /**
     * Returned by a {@link GroundLookup} when the column has no data (for example an unloaded chunk). Such columns
     * are skipped: they are neither an edge nor flat ground.
     */
    public static final int UNKNOWN = Integer.MAX_VALUE;

    /**
     * Blocks between samples along the probe ray.
     */
    static final double STEP = 0.25;
    /**
     * Furthest distance probed, in blocks.
     */
    static final double RANGE = 6.0;
    /**
     * Below this horizontal speed (blocks per tick) the heading comes from yaw instead of velocity.
     */
    static final double MIN_SPEED = 0.01;

    private EdgeProbe() {}

    /**
     * Highest solid ground block y in the given column, {@link #NONE} when there is none in the search window, or
     * {@link #UNKNOWN} when the column is not loaded.
     */
    @FunctionalInterface
    public interface GroundLookup {
        int highestSolidY(int x, int z);
    }

    /**
     * Whether an edge probe is meaningful this tick. Only a player standing on the ground in open air qualifies:
     * while airborne the feet level rises above the ground, so the player's own column would look like a drop, and
     * in water, lava, a vehicle or on a ladder the feet level says nothing about the walkable surface.
     */
    static boolean canProbe(boolean onGround, boolean inWater, boolean inLava, boolean riding, boolean climbing) {
        return onGround && !inWater && !inLava && !riding && !climbing;
    }

    /**
     * Horizontal distance in blocks from {@code (x, z)} along the unit direction {@code (dirX, dirZ)} to the first
     * column whose standing surface is a gap or drop, sampled every {@link #STEP} blocks from 0 up to {@link #RANGE}.
     * {@code standingFeetY} is the integer feet level of the last grounded tick (the standing surface is the block
     * just below it), not the player's current height. A column is an edge when it has no ground
     * ({@link #NONE}) or its highest solid block is at least two below the standing surface, i.e.
     * {@code ground top + 1 < standingFeetY - 1}. A one-block step or a slab top is therefore not an edge.
     * Returns {@code null} when no edge is found in range.
     *
     * @throws IllegalArgumentException when the direction has zero length
     */
    public static Double distanceToEdge(double x, int standingFeetY, double z, double dirX, double dirZ,
                                        GroundLookup ground) {
        double length = Math.hypot(dirX, dirZ);
        if (length < 1e-9) {
            throw new IllegalArgumentException("direction must not be zero");
        }
        double ux = dirX / length;
        double uz = dirZ / length;
        int steps = (int) Math.round(RANGE / STEP);
        for (int i = 0; i <= steps; i++) {
            double d = i * STEP;
            int columnX = (int) Math.floor(x + ux * d);
            int columnZ = (int) Math.floor(z + uz * d);
            int top = ground.highestSolidY(columnX, columnZ);
            if (top == UNKNOWN) {
                continue;
            }
            if (top == NONE || top + 1 < standingFeetY - 1) {
                return d;
            }
        }
        return null;
    }

    /**
     * Unit horizontal heading: the velocity direction when moving, otherwise the yaw direction (Minecraft convention,
     * yaw 0 faces +z and yaw 90 faces -x). Returns {@code {x, z}}.
     */
    public static double[] heading(double vx, double vz, double yaw) {
        double speed = Math.hypot(vx, vz);
        if (speed >= MIN_SPEED) {
            return new double[] {vx / speed, vz / speed};
        }
        double radians = Math.toRadians(yaw);
        return new double[] {-Math.sin(radians), Math.cos(radians)};
    }
}
