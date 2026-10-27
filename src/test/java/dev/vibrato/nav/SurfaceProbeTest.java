package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vibrato.nav.SurfaceProbe.Cell.AIR;
import static dev.vibrato.nav.SurfaceProbe.Cell.FALLING;
import static dev.vibrato.nav.SurfaceProbe.Cell.HARD;
import static dev.vibrato.nav.SurfaceProbe.Cell.LIQUID;
import static dev.vibrato.nav.SurfaceProbe.Cell.SOFT;
import static dev.vibrato.nav.SurfaceProbe.Cell.STONE;
import static dev.vibrato.nav.SurfaceProbe.Verdict;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceProbeTest {

    @Test
    void emptyOrAirOnlyColumnIsOpenSky() {
        assertEquals(Verdict.OPEN_SKY, SurfaceProbe.decide(List.of(), false));
        assertEquals(Verdict.OPEN_SKY, SurfaceProbe.decide(List.of(AIR, AIR, AIR), false));
    }

    @Test
    void softBlockAboveIsDiggableByHand() {
        assertEquals(Verdict.DIG_SOFT, SurfaceProbe.decide(List.of(SOFT, AIR), false));
    }

    @Test
    void stoneNeedsAPickaxe() {
        assertEquals(Verdict.NEEDS_PICKAXE, SurfaceProbe.decide(List.of(STONE), false));
        assertEquals(Verdict.DIG_STONE, SurfaceProbe.decide(List.of(STONE), true));
    }

    @Test
    void liquidAnywhereAboveIsAHazard() {
        assertEquals(Verdict.HAZARD, SurfaceProbe.decide(List.of(STONE, LIQUID), true));
        assertEquals(Verdict.HAZARD, SurfaceProbe.decide(List.of(LIQUID), true));
    }

    @Test
    void stackOfFallingBlocksIsAHazard() {
        assertEquals(Verdict.HAZARD, SurfaceProbe.decide(List.of(FALLING, FALLING, AIR), true));
    }

    @Test
    void singleFallingBlockCanBeBrokenBeforeItFalls() {
        assertEquals(Verdict.DIG_SOFT, SurfaceProbe.decide(List.of(FALLING, AIR), false));
    }

    @Test
    void hardBlockIsBlocked() {
        assertEquals(Verdict.BLOCKED, SurfaceProbe.decide(List.of(HARD), true));
    }
}
