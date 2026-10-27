package dev.vibrato.tasks.build;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SiteScorerTest {

    private static int[][] flat(int size, int y) {
        int[][] heights = new int[size][size];
        for (int[] column : heights) {
            java.util.Arrays.fill(column, y);
        }
        return heights;
    }

    @Test
    void prefersFlatGroundOverHill() {
        int[][] heights = flat(12, 64);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                heights[x][z] = 64 + (x + z) * 2;
            }
        }
        SiteScorer.Site site = SiteScorer.best(heights, new boolean[12][12], 4, 4).orElseThrow();
        assertEquals(64, site.groundY());
        assertTrue(site.x() >= 4 || site.z() >= 4, "site should avoid the hill at " + site);
    }

    @Test
    void avoidsWaterColumns() {
        int[][] heights = flat(12, 60);
        boolean[][] liquid = new boolean[12][12];
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                liquid[x][z] = true;
            }
        }
        SiteScorer.Site site = SiteScorer.best(heights, liquid, 5, 5).orElseThrow();
        assertTrue(site.x() >= 6 || site.z() >= 6, "site should avoid the lake at " + site);
        assertEquals(0, SiteScorer.best(heights, liquid, 5, 5).orElseThrow().score() - site.score());
    }

    @Test
    void mostlyLiquidFootprintIsRejected() {
        int[][] heights = flat(5, 60);
        boolean[][] liquid = new boolean[5][5];
        for (boolean[] column : liquid) {
            java.util.Arrays.fill(column, true);
        }
        Optional<SiteScorer.Site> site = SiteScorer.best(heights, liquid, 3, 3);
        assertTrue(site.isEmpty());
    }

    @Test
    void footprintLargerThanMapHasNoSite() {
        assertTrue(SiteScorer.best(flat(4, 60), new boolean[4][4], 5, 5).isEmpty());
    }
}
