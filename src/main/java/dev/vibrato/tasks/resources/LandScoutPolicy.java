package dev.vibrato.tasks.resources;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stable, bounded target selection for finding land animals. */
final class LandScoutPolicy {
    private static final int MIN_DISTANCE_SQ = 16 * 16;
    record Candidate(int x, int y, int z, boolean grass, boolean clear, boolean dry, boolean reachable) {
        long chunk() { return key(x >> 4, z >> 4); }
    }

    private final Set<Long> visited = new HashSet<>();
    private Candidate target;

    void visited(int chunkX, int chunkZ) { visited.add(key(chunkX, chunkZ)); }

    Candidate choose(int x, int z, boolean valid, boolean reached, List<Candidate> candidates) {
        if (target != null && valid && !reached) return target;
        if (target != null) visited.add(target.chunk());
        target = null;
        long bestDistance = Long.MAX_VALUE;
        for (Candidate candidate : candidates) {
            long dx = (long) candidate.x - x, dz = (long) candidate.z - z;
            long distance = dx * dx + dz * dz;
            if (distance < MIN_DISTANCE_SQ || visited.contains(candidate.chunk()) || !candidate.grass
                    || !candidate.clear || !candidate.dry || !candidate.reachable) continue;
            if (distance < bestDistance || (distance == bestDistance && (target == null
                    || candidate.x < target.x || candidate.x == target.x && candidate.z < target.z))) {
                target = candidate;
                bestDistance = distance;
            }
        }
        return target;
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
}
