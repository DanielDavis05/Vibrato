package dev.vibrato.validation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
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

    @Test void initialScreenBlocksButLaterScreenDoesNotFreezeTimeout() {
        var gate = new WorldReadyGate();
        var state = new ScriptState(ScriptPlan.parse(List.of("0 wait-idle 2")));
        assertFalse(gate.ready(false, false));
        assertFalse(gate.ready(true, true));
        assertNull(state.tick(100, gate.ready(true, true), false));
        assertNull(state.tick(0, gate.ready(true, false), false));
        assertNull(state.tick(1, gate.ready(true, true), false));
        assertFalse(gate.ready(false, true));
        assertTrue(state.tick(1, gate.ready(true, true), false).timedOut());
    }

    @Test void settleProofRequiresCompletedAssignmentIdleBarrierAndPendingFinalExit() {
        var state = new ScriptState(ScriptPlan.parse(List.of("0 @get dirt 1", "0 wait-idle 90", "180 exit")));
        var command = Map.<String,Object>of("action", "@get dirt 1", "status", "completed", "seconds", 1);
        var barrier = Map.<String,Object>of("action", "wait-idle 90", "status", "idle");
        var commands = List.of(command, barrier);
        var outcomes = List.of(Map.<String,Object>of("seconds", 2, "status", "completed"));
        assertTrue(state.settleEvidence("@get dirt 1", 180, commands, outcomes, true).isEmpty());
        state.tick(0, true, false);
        state.tick(0, true, true);
        state.tick(120, true, true);
        assertEquals(60.0, state.settleEvidence("@get dirt 1", 180, commands, outcomes, true).get("settleRemainingSeconds"));
        assertTrue(state.settleEvidence("director", 180, commands, outcomes, true).isEmpty());
        assertTrue(state.settleEvidence("@get dirt 1", 120, commands, outcomes, true).isEmpty());
        assertTrue(state.settleEvidence("@get dirt 1", 180, commands, outcomes, false).isEmpty());
        assertTrue(state.settleEvidence("@get dirt 1", 180, commands, List.of(), true).isEmpty());
        assertTrue(state.settleEvidence("@get dirt 1", 180, commands,
                List.of(Map.of("seconds", 2, "status", "cancelled")), true).isEmpty());
        assertTrue(state.settleEvidence("@get dirt 1", 180,
                List.of(command, Map.of("action", "wait-idle 90", "status", "timeout")), outcomes, true).isEmpty());
        state.tick(60, true, true);
        assertTrue(state.settleEvidence("@get dirt 1", 180, commands, outcomes, true).isEmpty());
    }

    @Test void idleWinsAtDeadlineAndOvershootCounts() {
        var steps = ScriptPlan.parse(List.of("1 wait-idle 2"));
        var idle = new ScriptState(steps);
        assertFalse(idle.tick(3, true, true).timedOut());
        var busy = new ScriptState(steps);
        assertTrue(busy.tick(3, true, false).timedOut());
    }
}
