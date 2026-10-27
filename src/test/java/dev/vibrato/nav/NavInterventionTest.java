package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavInterventionTest {

    @Test
    void rateLimiterAllowsOneInterventionPerInterval() {
        ActionRateLimiter limiter = new ActionRateLimiter(400);
        assertTrue(limiter.tryAcquire(0));
        assertFalse(limiter.tryAcquire(399));
        assertTrue(limiter.tryAcquire(400));
        assertFalse(limiter.tryAcquire(500));
    }

    @Test
    void perStepCapStopsAtTheCapAndResetsForANewStep() {
        PerStepCap cap = new PerStepCap(2);
        assertTrue(cap.tryUse(1));
        assertTrue(cap.tryUse(1));
        assertFalse(cap.tryUse(1));
        assertTrue(cap.tryUse(2));
    }

    @Test
    void onlyBackOffAndAbandonCancelThePath() {
        assertTrue(UnstuckAction.BACK_OFF_AND_REPATH.cancelsPath());
        assertTrue(UnstuckAction.ABANDON_TASK.cancelsPath());
        assertFalse(UnstuckAction.JUMP.cancelsPath());
        assertFalse(UnstuckAction.RECENTER_ON_BLOCK.cancelsPath());
        assertFalse(UnstuckAction.SIDESTEP_LEFT.cancelsPath());
        assertFalse(UnstuckAction.SIDESTEP_RIGHT.cancelsPath());
        assertFalse(UnstuckAction.ESCAPE_SURFACE.cancelsPath());
    }
}
