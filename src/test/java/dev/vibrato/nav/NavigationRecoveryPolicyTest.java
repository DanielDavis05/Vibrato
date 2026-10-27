package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationRecoveryPolicyTest {
    @Test
    void cancellationAndRecoveryMovementDoNotResetTheLadder() {
        MotionProgress progress = new MotionProgress(1.5);
        UnstuckLadder ladder = new UnstuckLadder();
        progress.observe(0, 0, 0, true);
        assertEquals(UnstuckAction.CLIMB_OUT_OF_WATER, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(MotionProgress.Result.IDLE_OR_INITIAL, progress.observe(0, 0, 0, false));
        assertEquals(MotionProgress.Result.IDLE_OR_INITIAL, progress.observe(0.5, 0, 0, false));
        assertEquals(MotionProgress.Result.NONE, progress.observe(0.6, 0, 0, true));
        assertEquals(UnstuckAction.SWIM_TO_SURFACE, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(MotionProgress.Result.MOVED, progress.observe(2.1, 0, 0, true));
        ladder.onProgress();
        assertEquals(UnstuckAction.CLIMB_OUT_OF_WATER, ladder.next(StuckState.WATER_DRIFT));
    }

    @Test
    void planningAheadDoesNotPauseMotionDetection() {
        assertTrue(NavGate.waitingForPath(false, true));
        assertFalse(NavGate.waitingForPath(true, true));
        assertFalse(NavGate.waitingForPath(false, false));
    }

    @Test
    void bankRequiresDrySpaceAndReachableHeight() {
        assertTrue(BankCandidate.usable(0, true, true, true, true, true));
        assertTrue(BankCandidate.usable(-2, true, true, true, true, true));
        assertFalse(BankCandidate.usable(1, true, true, true, true, true));
        assertFalse(BankCandidate.usable(-1, true, true, true, false, true));
        assertFalse(BankCandidate.usable(0, true, true, false, true, true));
        assertFalse(BankCandidate.usable(0, false, true, true, true, true));
    }

    @Test
    void newTaskCanResetCapEvenWhenSerialIsReused() {
        PerStepCap cap = new PerStepCap(1);
        assertTrue(cap.tryUse(0));
        assertFalse(cap.tryUse(0));
        cap.reset();
        assertTrue(cap.tryUse(0));
    }
}
