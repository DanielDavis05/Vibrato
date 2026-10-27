package dev.vibrato.human.motion;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Lateral wander off the path centreline, as an Ornstein-Uhlenbeck process: a random walk pulled back toward the
 * centre. The caller gives the corridor half-width each tick. The offset is clamped to it, and it is exactly zero when
 * the corridor is zero (bridges, one-wide tunnels).
 * <p>
 * {@link #lookAhead} gives the point to aim at: a few blocks further along the path, pushed sideways by the offset.
 */
public final class PathOffset {

    /** A point on the horizontal plane. */
    public record Point(double x, double z) {}

    private final RandomGenerator rng;
    private final HumanProfile profile;
    private double offset;

    public PathOffset(long seed, HumanProfile profile) {
        this.rng = new SplittableRandom(seed);
        this.profile = profile;
    }

    /**
     * Advances the offset one tick and returns it, in blocks. Positive is to the left of travel.
     *
     * @param corridorHalfWidth largest allowed offset on either side, 0 or less disables the wander
     */
    public double step(double corridorHalfWidth) {
        if (!(corridorHalfWidth > 0)) {
            offset = 0;
            return 0;
        }
        double next = offset * (1 - profile.lateralReversion()) + profile.lateralSigma() * rng.nextGaussian();
        offset = Math.max(-corridorHalfWidth, Math.min(corridorHalfWidth, next));
        return offset;
    }

    /**
     * The offset from the last {@link #step} call, in blocks.
     */
    public double offset() {
        return offset;
    }

    /**
     * The aim point {@code lookAheadBlocks} along the path from {@code from}, following {@code next} and then
     * {@code after} (null when there is no node after). Past the last node the last leg extends straight on. The current
     * offset is applied perpendicular to the leg the aim point lies on.
     */
    public Point lookAhead(Point from, Point next, Point after) {
        List<Point> nodes = after == null ? List.of(from, next) : List.of(from, next, after);
        double remaining = profile.lookAheadBlocks();
        double dx = 0;
        double dz = 0;
        for (int i = 0; i + 1 < nodes.size(); i++) {
            Point a = nodes.get(i);
            Point b = nodes.get(i + 1);
            double lx = b.x() - a.x();
            double lz = b.z() - a.z();
            double length = Math.hypot(lx, lz);
            if (length == 0) {
                continue;
            }
            dx = lx / length;
            dz = lz / length;
            if (remaining <= length) {
                return aim(a.x() + dx * remaining, a.z() + dz * remaining, dx, dz);
            }
            remaining -= length;
        }
        Point end = nodes.get(nodes.size() - 1);
        return aim(end.x() + dx * remaining, end.z() + dz * remaining, dx, dz);
    }

    private Point aim(double x, double z, double dx, double dz) {
        return new Point(x - dz * offset, z + dx * offset);
    }
}
