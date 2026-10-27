package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutWanderTaskTest {
    @Test
    void interruptionResumesSameAttemptAndExplicitResetStartsANewOne() {
        WanderPolicy.Attempt attempt = new WanderPolicy.Attempt();
        assertTrue(attempt.start(false));
        for (int i = 0; i < 10; i++) assertFalse(attempt.start(false));
        attempt.reset();
        assertTrue(attempt.needsStart());
        assertTrue(attempt.start(false));
        assertFalse(attempt.start(false));
    }

    @Test
    void aCompletedAndStoppedWanderStartsFreshWhenReused() {
        WanderPolicy.Attempt attempt = new WanderPolicy.Attempt();
        assertTrue(attempt.start(false));
        assertTrue(attempt.start(true));
        assertFalse(attempt.start(false));
    }

    @Test
    void distinctExplorePoliciesAreNotTheSameTask() {
        assertFalse(WanderPolicy.equivalent(64, false, false, 64, false, true));
        assertFalse(WanderPolicy.equivalent(10, false, false, 10, true, false));
        assertTrue(WanderPolicy.equivalent(10, true, false, 10, true, false));
        assertFalse(WanderPolicy.equivalent(10, true, false, 11, true, false));
        assertTrue(WanderPolicy.equivalent(Float.POSITIVE_INFINITY, true, false,
                Float.POSITIVE_INFINITY, true, false));
    }
}
