package dev.vibrato.tasks.build;

import java.util.Arrays;
import java.util.Optional;

/**
 * Picks where a footprint should sit on a height map. Lower score is better: flat ground wins, each liquid column
 * costs a fixed penalty, and a footprint that is more than half liquid is rejected.
 */
public final class SiteScorer {

    public record Site(int x, int z, int groundY, double score) {}

    static final int LIQUID_PENALTY = 25;
    static final int ROUGHNESS_WEIGHT = 4;

    private SiteScorer() {}

    /**
     * @param heights ground block y, indexed [x][z]
     * @param liquid  true where the column holds water or lava, indexed [x][z]
     */
    public static Optional<Site> best(int[][] heights, boolean[][] liquid, int width, int depth) {
        Optional<Site> best = Optional.empty();
        int cells = width * depth;
        for (int x = 0; x + width <= heights.length; x++) {
            for (int z = 0; z + depth <= heights[0].length; z++) {
                int[] values = new int[cells];
                int liquidCount = 0;
                int i = 0;
                for (int dx = 0; dx < width; dx++) {
                    for (int dz = 0; dz < depth; dz++) {
                        values[i++] = heights[x + dx][z + dz];
                        if (liquid[x + dx][z + dz]) {
                            liquidCount++;
                        }
                    }
                }
                if (liquidCount * 2 > cells) {
                    continue;
                }
                int[] sorted = values.clone();
                Arrays.sort(sorted);
                int median = sorted[cells / 2];
                int deviation = 0;
                for (int v : values) {
                    deviation += Math.abs(v - median);
                }
                double score = deviation + ROUGHNESS_WEIGHT * (sorted[cells - 1] - sorted[0]) + LIQUID_PENALTY * liquidCount;
                if (best.isEmpty() || score < best.get().score()) {
                    best = Optional.of(new Site(x, z, median, score));
                }
            }
        }
        return best;
    }
}
