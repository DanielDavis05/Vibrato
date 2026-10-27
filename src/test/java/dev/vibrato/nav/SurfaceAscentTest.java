package dev.vibrato.nav;

import org.junit.jupiter.api.Test;
import java.util.List;
import static dev.vibrato.nav.SurfaceProbe.Cell.*;
import static dev.vibrato.nav.SurfaceProbe.Ascent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceAscentTest {
    @Test void shaftNeedsPhysicalClimbBeforeDistantCeiling() {
        assertEquals(CLIMB, SurfaceProbe.ascent(List.of(AIR, AIR, STONE), true, false));
        assertEquals(CLIMB, SurfaceProbe.ascent(List.of(AIR, STONE), false, false));
        assertEquals(CLIMB, SurfaceProbe.ascent(List.of(AIR, HARD), false, false));
        assertEquals(STOP, SurfaceProbe.ascent(List.of(AIR, LIQUID), false, false));
        assertEquals(STOP, SurfaceProbe.ascent(List.of(AIR, FALLING), false, false));
        assertEquals(CLIMB, SurfaceProbe.ascent(List.of(AIR, AIR), false, false));
    }
    @Test void toolAndHazardsBoundTheImmediateStep() {
        assertEquals(DIG, SurfaceProbe.ascent(List.of(STONE, AIR), true, false));
        assertEquals(STOP, SurfaceProbe.ascent(List.of(STONE), false, false));
        assertEquals(STOP, SurfaceProbe.ascent(List.of(SOFT, LIQUID), true, false));
        assertEquals(STOP, SurfaceProbe.ascent(List.of(FALLING, AIR), true, false));
        assertEquals(STOP, SurfaceProbe.ascent(List.of(SOFT), true, true));
    }
}
