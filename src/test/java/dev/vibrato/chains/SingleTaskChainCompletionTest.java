package dev.vibrato.chains;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.ITaskCanForce;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingleTaskChainCompletionTest {
    private static final class TestChain extends SingleTaskChain {
        int finishes;
        TestChain() { super(new TaskRunner(null)); }
        @Override protected void onTaskFinish(Vibrato mod) { finishes++; _mainTask = null; }
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public String getName() { return "test"; }
    }

    private static class FakeTask extends Task {
        boolean finished;
        Task child;
        int ticks;
        int stops;
        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task onTick(Vibrato mod) { ticks++; return child; }
        @Override protected void onStop(Vibrato mod, Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "fake"; }
        @Override public boolean isFinished(Vibrato mod) { return finished; }
    }

    private static final class ForcedTask extends FakeTask implements ITaskCanForce {
        boolean force = true;
        @Override public boolean shouldForce(Vibrato mod, Task candidate) { return force; }
    }

    @Test
    void finishedRootKeepsTickingUntilProtectedActionEnds() {
        TestChain chain = new TestChain();
        ForcedTask root = new ForcedTask();
        chain.setTask(root);
        chain.tick(null);
        root.finished = true;
        chain.tick(null);
        assertEquals(2, root.ticks);
        assertEquals(0, chain.finishes);
        root.force = false;
        chain.tick(null);
        assertEquals(1, chain.finishes);
        assertEquals(2, root.ticks);
    }

    @Test
    void finishedRootWaitsForForcedDescendant() {
        TestChain chain = new TestChain();
        FakeTask root = new FakeTask();
        ForcedTask child = new ForcedTask();
        root.child = child;
        chain.setTask(root);
        chain.tick(null);
        root.finished = true;
        chain.tick(null);
        assertEquals(2, child.ticks);
        assertEquals(0, chain.finishes);
        child.force = false;
        chain.tick(null);
        assertEquals(1, chain.finishes);
    }

    @Test
    void explicitlyStoppedTaskFinishesWithoutWaitingForForce() {
        TestChain chain = new TestChain();
        ForcedTask root = new ForcedTask();
        chain.setTask(root);
        chain.tick(null);
        root.stop(null);
        chain.tick(null);
        assertEquals(1, root.stops);
        assertEquals(1, chain.finishes);
    }

    @Test
    void explicitChainStopDoesNotWaitForForce() {
        TestChain chain = new TestChain();
        ForcedTask root = new ForcedTask();
        chain.setTask(root);
        chain.tick(null);
        chain.stop(null);
        assertEquals(1, root.stops);
        assertFalse(chain.isActive());
    }
}
