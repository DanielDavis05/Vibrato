package dev.vibrato.tasks.explore;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Process-wide record of places the bots have found: villages, structures, biomes. Kinds are free-form strings such as
 * {@code village}, {@code structure:desert_temple} or {@code biome:plains}.
 * <p>
 * A discovery within {@link #DEDUPE_RADIUS} blocks of an existing one of the same kind is not stored again.
 */
public final class DiscoveryLog {

    public static final int DEDUPE_RADIUS = 16;

    private static final long DEDUPE_RADIUS_SQUARED = (long) DEDUPE_RADIUS * DEDUPE_RADIUS;

    private static final List<Discovery> ENTRIES = new ArrayList<>();

    private DiscoveryLog() {}

    public record Discovery(String kind, BlockPos pos) {}

    /** @return true if the discovery was new and has been stored */
    public static synchronized boolean add(String kind, BlockPos pos) {
        for (Discovery existing : ENTRIES) {
            if (existing.kind().equals(kind) && squaredDistance(existing.pos(), pos) <= DEDUPE_RADIUS_SQUARED) {
                return false;
            }
        }
        ENTRIES.add(new Discovery(kind, pos.toImmutable()));
        return true;
    }

    public static synchronized Optional<BlockPos> nearest(String kind, BlockPos from) {
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Discovery existing : ENTRIES) {
            if (!existing.kind().equals(kind)) {
                continue;
            }
            long distance = squaredDistance(existing.pos(), from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = existing.pos();
            }
        }
        return Optional.ofNullable(best);
    }

    public static synchronized List<Discovery> all(String kind) {
        List<Discovery> result = new ArrayList<>();
        for (Discovery existing : ENTRIES) {
            if (existing.kind().equals(kind)) {
                result.add(existing);
            }
        }
        return result;
    }

    /** Forgets everything. Intended for tests and for a fresh world. */
    public static synchronized void clear() {
        ENTRIES.clear();
    }

    private static long squaredDistance(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
