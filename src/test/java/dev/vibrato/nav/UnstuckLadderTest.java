package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static dev.vibrato.nav.UnstuckAction.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnstuckLadderTest {

    @Test
    void edgeJamWalksTheLadderThenRepeatsAbandon() {
        UnstuckLadder ladder = new UnstuckLadder();
        assertEquals(RECENTER_ON_BLOCK, ladder.next(StuckState.EDGE_JAM));
        assertEquals(JUMP, ladder.next(StuckState.EDGE_JAM));
        assertEquals(SIDESTEP_LEFT, ladder.next(StuckState.EDGE_JAM));
        assertEquals(SIDESTEP_RIGHT, ladder.next(StuckState.EDGE_JAM));
        assertEquals(BACK_OFF_AND_REPATH, ladder.next(StuckState.EDGE_JAM));
        assertEquals(BREAK_BLOCKING_BLOCK, ladder.next(StuckState.EDGE_JAM));
        assertEquals(ABANDON_TASK, ladder.next(StuckState.EDGE_JAM));
        assertEquals(ABANDON_TASK, ladder.next(StuckState.EDGE_JAM));
    }

    @Test
    void waterDriftClimbsOutFirst() {
        UnstuckLadder ladder = new UnstuckLadder();
        assertEquals(CLIMB_OUT_OF_WATER, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(SWIM_TO_SURFACE, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(BACK_OFF_AND_REPATH, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(ABANDON_TASK, ladder.next(StuckState.WATER_DRIFT));
    }

    @Test
    void lowAirAlwaysSwimsToSurface() {
        UnstuckLadder ladder = new UnstuckLadder();
        for (int i = 0; i < 5; i++) {
            assertEquals(SWIM_TO_SURFACE, ladder.next(StuckState.LOW_AIR));
        }
    }

    @Test
    void okHasNoRung() {
        assertNull(new UnstuckLadder().next(StuckState.OK));
    }

    @Test
    void statesKeepSeparateRungs() {
        UnstuckLadder ladder = new UnstuckLadder();
        assertEquals(RECENTER_ON_BLOCK, ladder.next(StuckState.EDGE_JAM));
        assertEquals(JUMP, ladder.next(StuckState.STATIONARY_WITH_INTENT));
    }

    @Test
    void progressStartsANewEpisode() {
        UnstuckLadder ladder = new UnstuckLadder();
        ladder.next(StuckState.EDGE_JAM);
        ladder.next(StuckState.EDGE_JAM);
        ladder.onProgress();
        assertEquals(RECENTER_ON_BLOCK, ladder.next(StuckState.EDGE_JAM));
    }

    @Test
    void cooldownIsTenTicksBetweenFirings() {
        UnstuckLadder ladder = new UnstuckLadder();
        assertTrue(ladder.ready(0));
        ladder.markFired(100);
        assertFalse(ladder.ready(105));
        assertFalse(ladder.ready(109));
        assertTrue(ladder.ready(110));
    }

    @Test
    void progressClearsCooldown() {
        UnstuckLadder ladder = new UnstuckLadder();
        ladder.markFired(100);
        ladder.onProgress();
        assertTrue(ladder.ready(101));
    }
}
