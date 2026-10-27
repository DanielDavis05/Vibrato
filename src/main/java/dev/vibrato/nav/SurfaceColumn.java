package dev.vibrato.nav;

import java.util.function.IntPredicate;

/** Examines a vertical column without assuming that canopy or shelter is terrain. */
final class SurfaceColumn {
    private SurfaceColumn() {}

    static boolean exposedToSurface(int feetY, int topY, IntPredicate terrainAtY) {
        for (int y = feetY + 1; y < topY; y++) {
            if (terrainAtY.test(y)) return false;
        }
        return true;
    }

    static int targetY(int feetY, int topY, IntPredicate coverOrAirAtY) {
        int y = topY;
        while (y > feetY + 1 && coverOrAirAtY.test(y - 1)) y--;
        return y;
    }
}
