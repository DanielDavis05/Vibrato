package dev.vibrato.tasks.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable plan of blocks relative to an origin. dy=0 is the ground block the origin sits on; dy&gt;=1 is above it.
 * Pure data: no Minecraft registries are referenced, so it can be built and checked in unit tests.
 */
public record Blueprint(String name, List<Placement> placements) {

    public record Placement(int dx, int dy, int dz, BlockKind kind) {}

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }
    }

    public Blueprint {
        placements = List.copyOf(placements);
        Set<List<Integer>> seen = new HashSet<>();
        for (Placement p : placements) {
            if (!seen.add(List.of(p.dx(), p.dy(), p.dz()))) {
                throw new IllegalArgumentException("Blueprint " + name + " places two blocks at " + p);
            }
        }
    }

    public Optional<Placement> at(int dx, int dy, int dz) {
        return placements.stream().filter(p -> p.dx() == dx && p.dy() == dy && p.dz() == dz).findFirst();
    }

    public boolean isEmpty() {
        return placements.isEmpty();
    }

    /** Placements sorted bottom-up, then by x and z, which is the order the executor builds in. */
    public List<Placement> bottomUp() {
        return placements.stream()
                .sorted(Comparator.comparingInt(Placement::dy).thenComparingInt(Placement::dx).thenComparingInt(Placement::dz))
                .toList();
    }

    public Bounds bounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Placement p : placements) {
            minX = Math.min(minX, p.dx());
            minY = Math.min(minY, p.dy());
            minZ = Math.min(minZ, p.dz());
            maxX = Math.max(maxX, p.dx());
            maxY = Math.max(maxY, p.dy());
            maxZ = Math.max(maxZ, p.dz());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Count of each item id needed. Tool-placed kinds are excluded because no item is consumed for them. */
    public Map<String, Integer> materialCount(Palette palette) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Placement p : placements) {
            if (p.kind().isToolPlaced()) {
                continue;
            }
            counts.merge(palette.itemId(p.kind()), 1, Integer::sum);
        }
        return counts;
    }

    /** Placements above the ground that have neither a block below nor a block beside them at the same height. */
    public List<Placement> floating() {
        List<Placement> result = new ArrayList<>();
        for (Placement p : placements) {
            if (p.dy() < 2) {
                continue;
            }
            boolean supported = contains(p.dx(), p.dy() - 1, p.dz())
                    || contains(p.dx() + 1, p.dy(), p.dz()) || contains(p.dx() - 1, p.dy(), p.dz())
                    || contains(p.dx(), p.dy(), p.dz() + 1) || contains(p.dx(), p.dy(), p.dz() - 1);
            if (!supported) {
                result.add(p);
            }
        }
        return result;
    }

    public boolean contains(int dx, int dy, int dz) {
        return at(dx, dy, dz).isPresent();
    }

    /** Same blocks turned a quarter turn around the vertical axis: (x, z) becomes (-z, x). */
    public Blueprint quarterTurned() {
        List<Placement> turned = new ArrayList<>();
        for (Placement p : placements) {
            turned.add(new Placement(-p.dz(), p.dy(), p.dx(), p.kind()));
        }
        return new Blueprint(name, turned);
    }
}
