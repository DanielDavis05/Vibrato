package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationRecoveryPolicyTest {
    @Test
    void cancellationAndRecoveryMovementDoNotResetTheLadder() {
        MotionProgress progress = new MotionProgress(1.5);
        UnstuckLadder ladder = new UnstuckLadder();
        progress.observe(0, 0, 0, true, true);
        assertEquals(UnstuckAction.JUMP, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(MotionProgress.Result.IDLE_OR_INITIAL, progress.observe(0, 0, 0, false, true));
        assertEquals(MotionProgress.Result.IDLE_OR_INITIAL, progress.observe(0.5, 3, 0, false, true));
        assertEquals(MotionProgress.Result.NONE, progress.observe(0.6, 0, 0, true, true));
        assertEquals(UnstuckAction.WATER_FORWARD_SPRINT, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(MotionProgress.Result.MOVED, progress.observe(2.1, 0, 0, true, true));
        ladder.onProgress();
        assertEquals(UnstuckAction.JUMP, ladder.next(StuckState.WATER_DRIFT));
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
    @Test
    void waterBobbingAndRecoveryMovementCannotResetEscalation() {
        MotionProgress progress = new MotionProgress(1.5);
        UnstuckLadder ladder = new UnstuckLadder();
        progress.observe(0, 64, 0, true, true);
        assertEquals(UnstuckAction.JUMP, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(MotionProgress.Result.NONE, progress.observe(0, 67, 0, true, true));
        assertEquals(MotionProgress.Result.IDLE_OR_INITIAL, progress.observe(2, 67, 0, false, true));
        assertEquals(MotionProgress.Result.NONE, progress.observe(2, 64, 0, true, true));
        assertEquals(UnstuckAction.WATER_FORWARD_SPRINT, ladder.next(StuckState.WATER_DRIFT));
        assertEquals(MotionProgress.Result.MOVED, progress.observe(3.6, 64, 0, true, true));
        ladder.onProgress();
        assertEquals(UnstuckAction.JUMP, ladder.next(StuckState.WATER_DRIFT));
    }

    @Test
    void dryPillaringStillCountsAsProgress() {
        MotionProgress progress = new MotionProgress(1.5);
        progress.observe(0, 64, 0, true, false);
        assertEquals(MotionProgress.Result.MOVED, progress.observe(0, 66, 0, true, false));
    }

    @Test
    void forwardWaterRecoveryHasTargetDirectionAndHardStop() {
        assertEquals(-90f, WaterRecovery.yaw(0, 0, 1, 0));
        assertTrue(WaterRecovery.shouldMove(true, true, 20, 0, 0, 1, 0));
        assertFalse(WaterRecovery.shouldMove(true, true, 21, 0, 0, 1, 0));
        assertFalse(WaterRecovery.shouldMove(false, true, 1, 0, 0, 1, 0));
        assertFalse(WaterRecovery.shouldMove(true, false, 1, 0, 0, 1, 0));
        assertFalse(WaterRecovery.shouldMove(true, true, 1, 0.9, 0, 1, 0));
    }

}
