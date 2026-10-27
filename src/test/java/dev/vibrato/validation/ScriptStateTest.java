package dev.vibrato.validation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptStateTest {
    @Test void parserAcceptsCommandsAndRejectsBadLines() {
        var steps = ScriptPlan.parse(List.of("# example", "1.5 @goto 1 2 3", "0 /time set day", "0 wait-idle 10", "0 exit"));
        assertEquals(4, steps.size());
        assertEquals(ScriptPlan.Kind.CLIENT, steps.get(0).kind());
        assertEquals(10, steps.get(2).timeoutSeconds());
        for (String line : List.of("NaN exit", "-1 exit", "0 wait-idle infinity", "0 hello", "0 /", "0 exit extra")) {
            assertThrows(IllegalArgumentException.class, () -> ScriptPlan.parse(List.of(line)), line);
        }
    }

    @Test void pausesUntilReadyAndTimeoutRequiresBusy() {
        var state = new ScriptState(ScriptPlan.parse(List.of("1 @goto 1 2 3", "0 wait-idle 2", "0 exit")));
        assertNull(state.tick(100, false, true));
        assertNull(state.tick(.5, true, true));
        assertEquals(ScriptPlan.Kind.CLIENT, state.tick(.5, true, true).step().kind());
        assertNull(state.tick(0, true, false));
        assertNull(state.tick(100, false, false));
        assertTrue(state.tick(2, true, false).timedOut());
        assertEquals(ScriptPlan.Kind.EXIT, state.tick(0, true, true).step().kind());
        assertTrue(state.finished());
    }

    @Test void idleWinsAtDeadlineAndOvershootCounts() {
        var steps = ScriptPlan.parse(List.of("1 wait-idle 2"));
        var idle = new ScriptState(steps);
        assertFalse(idle.tick(3, true, true).timedOut());
        var busy = new ScriptState(steps);
        assertTrue(busy.tick(3, true, false).timedOut());
    }
}
