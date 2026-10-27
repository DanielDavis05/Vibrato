package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutWanderTaskTest {
    @Test
    void distinctExplorePoliciesAreNotTheSameTask() {
        assertNotEquals(new TimeoutWanderTask(), new TimeoutWanderTask(true));
        assertNotEquals(new TimeoutWanderTask(10), new TimeoutWanderTask(10, true));
        assertEquals(new TimeoutWanderTask(10, true), new TimeoutWanderTask(10, true));
    }
}
