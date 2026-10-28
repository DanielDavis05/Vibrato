package dev.vibrato.tasks.resources;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Temporarily defers a failed approach to ordinary stone, not ore veins. */
final class TerrainApproachFailures {
    private static final long RETRY_NANOS = 90_000_000_000L;
    private static final double RADIUS_SQ = 16;
    private final List<Failure> failures = new ArrayList<>();

    static boolean sameArea(BlockPos a, BlockPos b) {
        return a.getSquaredDistance(b.getX(), b.getY(), b.getZ()) <= RADIUS_SQ;
    }

    void reject(BlockPos anchor, double x, double y, double z, long now) {
        failures.add(new Failure(anchor, x, y, z, now));
    }

    boolean suppress(BlockPos pos, double x, double y, double z, long now) {
        failures.removeIf(f -> now - f.time >= RETRY_NANOS
                || (x-f.x)*(x-f.x) + (y-f.y)*(y-f.y) + (z-f.z)*(z-f.z) >= RADIUS_SQ);
        return failures.stream().anyMatch(f -> sameArea(f.anchor, pos));
    }

    void clear() { failures.clear(); }
    private record Failure(BlockPos anchor, double x, double y, double z, long time) {}
}
