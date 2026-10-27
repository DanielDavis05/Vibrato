package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutWanderTaskTest {
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
