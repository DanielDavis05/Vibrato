package dev.vibrato.nav;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResourceRecoveryTest {
    @Test
    void choosesDeepestActiveWillingTask() {
        ResourceRecovery recovery = new ResourceRecovery();
        TestTask root = new TestTask(true, true);
        TestTask ancestor = new TestTask(true, true);
        TestTask deepest = new TestTask(true, true);
        TestTask inactive = new TestTask(false, true);
        assertTrue(recovery.attempt(null, root, List.of(root, ancestor, deepest, inactive)));
        assertEquals(1, deepest.calls);
        assertEquals(0, ancestor.calls);
        assertEquals(0, inactive.calls);
    }

    @Test
    void unsuccessfulCandidatesDoNotConsumeBudget() {
        ResourceRecovery recovery = new ResourceRecovery();
        TestTask root = new TestTask(true, false);
        TestTask unwilling = new TestTask(true, false);
        assertFalse(recovery.attempt(null, root, List.of(root, unwilling)));
        TestTask willing = new TestTask(true, true);
        assertTrue(recovery.attempt(null, root, List.of(root, willing)));
        assertTrue(recovery.attempt(null, root, List.of(root, willing)));
        assertFalse(recovery.attempt(null, root, List.of(root, willing)));
        assertEquals(2, willing.calls);
    }

    @Test
    void replacingDescendantsDoesNotResetBudgetButProgressAndRootChangeDo() {
        ResourceRecovery recovery = new ResourceRecovery();
        TestTask root = new TestTask(true, false);
        assertTrue(recovery.attempt(null, root, List.of(root, new TestTask(true, true))));
        assertTrue(recovery.attempt(null, root, List.of(root, new TestTask(true, true))));
        assertFalse(recovery.attempt(null, root, List.of(root, new TestTask(true, true))));
        recovery.reset(); // caller observed genuine resource progress
        assertTrue(recovery.attempt(null, root, List.of(root, new TestTask(true, true))));
        assertTrue(recovery.attempt(null, root, List.of(root, new TestTask(true, true))));
        assertFalse(recovery.attempt(null, root, List.of(root, new TestTask(true, true))));
        TestTask nextRoot = new TestTask(true, false);
        assertTrue(recovery.attempt(null, nextRoot, List.of(nextRoot, new TestTask(true, true))));
    }

    private static class TestTask extends Task {
        private final boolean active;
        private final boolean willing;
        private int calls;
        TestTask(boolean active, boolean willing) { this.active = active; this.willing = willing; }
        @Override public boolean isActive() { return active; }
        @Override public boolean recoverFromStall(Vibrato mod) { calls++; return willing; }
        @Override protected void onStart(Vibrato mod) { }
        @Override protected Task onTick(Vibrato mod) { return null; }
        @Override protected void onStop(Vibrato mod, Task interruptTask) { }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "test"; }
    }
}
