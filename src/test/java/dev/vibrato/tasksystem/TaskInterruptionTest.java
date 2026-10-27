package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskInterruptionTest {

    private static final class TestChain extends TaskChain {
        TestChain() { super(new TaskRunner(null)); }
        @Override protected void onStop(Vibrato mod) {}
        @Override public void onInterrupt(Vibrato mod, TaskChain other) {}
        @Override protected void onTick(Vibrato mod) {}
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    }

    private static class FakeTask extends Task {
        Task next;
        int ticks;
        int stops;

        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task onTick(Vibrato mod) { ticks++; return next; }
        @Override protected void onStop(Vibrato mod, Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "fake"; }
    }

    private static final class FailingStartTask extends FakeTask {
        boolean fail = true;
        int starts;
        @Override protected void onStart(Vibrato mod) {
            starts++;
            if (fail) throw new IllegalStateException("start failed");
        }
    }

    private static final class ForcedTask extends FakeTask implements ITaskCanForce {
        boolean force = true;
        Task lastCandidate;
        @Override public boolean shouldForce(Vibrato mod, Task candidate) {
            lastCandidate = candidate;
            return force;
        }
    }

    @Test
    void nestedForcedTaskVetoesReplacementUntilItReleases() {
        TestChain chain = new TestChain();
        FakeTask parent = new FakeTask();
        FakeTask wrapper = new FakeTask();
        ForcedTask protectedChild = new ForcedTask();
        FakeTask replacement = new FakeTask();
        parent.next = wrapper;
        wrapper.next = protectedChild;
        parent.tick(null, chain);

        parent.next = replacement;
        parent.tick(null, chain);
        assertSame(replacement, protectedChild.lastCandidate);
        assertEquals(2, protectedChild.ticks);
        assertEquals(0, wrapper.stops);
        assertEquals(0, replacement.ticks);

        protectedChild.force = false;
        parent.tick(null, chain);
        assertEquals(1, wrapper.stops);
        assertEquals(1, protectedChild.stops);
        assertEquals(1, replacement.ticks);
    }

    @Test
    void nullCandidateKeepsForcedChildRunningThenStopsIt() {
        TestChain chain = new TestChain();
        FakeTask parent = new FakeTask();
        FakeTask wrapper = new FakeTask();
        ForcedTask protectedChild = new ForcedTask();
        parent.next = wrapper;
        wrapper.next = protectedChild;
        parent.tick(null, chain);
        parent.next = null;
        parent.tick(null, chain);
        assertNull(protectedChild.lastCandidate);
        assertEquals(2, protectedChild.ticks);
        assertEquals(0, wrapper.stops);

        protectedChild.force = false;
        parent.tick(null, chain);
        assertEquals(2, protectedChild.ticks);
        assertEquals(1, protectedChild.stops);
        assertEquals(1, wrapper.stops);
    }

    @Test
    void failedStartDoesNotCallStopForAnUninitializedTaskAndCanRestart() {
        TestChain chain = new TestChain();
        FailingStartTask task = new FailingStartTask();
        assertThrows(IllegalStateException.class, () -> task.tick(null, chain));
        assertFalse(task.isActive());
        task.stop(null);
        assertEquals(0, task.stops);
        task.fail = false;
        task.tick(null, chain);
        task.stop(null);
        task.stop(null);
        assertEquals(2, task.starts);
        assertEquals(1, task.stops);
    }

    @Test
    void failedRestartReleasesPreviouslyInterruptedProtectedChild() {
        TestChain chain = new TestChain();
        FailingStartTask parent = new FailingStartTask();
        ForcedTask child = new ForcedTask();
        parent.fail = false;
        parent.next = child;
        parent.tick(null, chain);
        parent.interrupt(null, null);
        assertEquals(1, child.stops);
        parent.fail = true;
        assertThrows(IllegalStateException.class, () -> parent.tick(null, chain));
        assertFalse(child.isActive());
        assertEquals(1, child.stops);
        parent.fail = false;
        parent.tick(null, chain);
        assertEquals(2, child.ticks);
        parent.stop(null);
        assertEquals(2, child.stops);
    }

    @Test
    void ordinaryChildCanBeReplaced() {
        TestChain chain = new TestChain();
        FakeTask parent = new FakeTask();
        FakeTask first = new FakeTask();
        FakeTask second = new FakeTask();
        parent.next = first;
        parent.tick(null, chain);
        parent.next = second;
        parent.tick(null, chain);
        assertEquals(1, first.stops);
        assertEquals(1, second.ticks);
    }
}
