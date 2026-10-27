package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceColumnTest {
    @Test
    void canopyOrShelterDoesNotMakeSurfaceUnderground() {
        // Cover is ignored by the terrain predicate even if it is high above the player.
        assertTrue(SurfaceColumn.exposedToSurface(64, 90, y -> false));
    }

    @Test
    void caveRoofBelowCanopyStillMeansUnderground() {
        Set<Integer> stoneAndDirt = Set.of(67, 68, 69);
        assertFalse(SurfaceColumn.exposedToSurface(64, 90, stoneAndDirt::contains));
    }

    @Test
    void escapeTargetDropsPastCanopyAndAirButStopsAboveTerrain() {
        Set<Integer> coverAndAir = Set.of(82, 81, 80, 79, 78, 77);
        assertEquals(77, SurfaceColumn.targetY(40, 83, coverAndAir::contains));
        assertEquals(65, SurfaceColumn.targetY(64, 65, y -> true));
    }
}
