package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepGuardTest {

    @Test
    void factoryThatThrowsYieldsNullAndReportsTheError() {
        List<RuntimeException> errors = new ArrayList<>();
        Object built = StepGuard.call(() -> {
            throw new IllegalArgumentException("bad crop");
        }, errors::add);

        assertNull(built);
        assertEquals(1, errors.size());
        assertEquals("bad crop", errors.get(0).getMessage());
    }

    @Test
    void factoryThatWorksReturnsItsValue() {
        List<RuntimeException> errors = new ArrayList<>();
        assertEquals("ok", StepGuard.call(() -> "ok", errors::add));
        assertTrue(errors.isEmpty());
    }

    @Test
    void taskThatThrowsMidTickIsReportedNotPropagated() {
        List<RuntimeException> errors = new ArrayList<>();
        boolean completed = StepGuard.run(() -> {
            throw new IllegalStateException("tick blew up");
        }, errors::add);

        assertFalse(completed);
        assertEquals("tick blew up", errors.get(0).getMessage());
    }

    @Test
    void cleanTickCompletes() {
        List<RuntimeException> errors = new ArrayList<>();
        assertTrue(StepGuard.run(() -> { }, errors::add));
        assertTrue(errors.isEmpty());
    }
}
