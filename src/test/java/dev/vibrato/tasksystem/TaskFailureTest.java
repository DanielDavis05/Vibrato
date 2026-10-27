package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskFailureTest {
    private static class Fake extends Task {
        Task child;
        boolean fail;
        boolean handle;
        int stops;
        int handled;
        @Override protected void onStart(Vibrato mod) { }
        @Override protected Task onTick(Vibrato mod) { if (fail) fail("blocked"); return child; }
        @Override protected void onStop(Vibrato mod, Task interrupt) { stops++; }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "fake"; }
        @Override protected boolean onChildFailure(Vibrato mod, Task child, String reason) { handled++; return handle; }
    }
    private static final class Chain extends TaskChain {
        Chain() { super(new TaskRunner(null)); }
        @Override protected void onStop(Vibrato mod) { }
        @Override public void onInterrupt(Vibrato mod, TaskChain other) { }
        @Override protected void onTick(Vibrato mod) { }
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    }
    private static final class Forced extends Fake implements ITaskCanForce {
        boolean force = true;
        @Override public boolean shouldForce(Vibrato mod, Task candidate) { return force; }
    }
    @Test void failurePropagatesAfterChildCleanup() {
        Fake root = new Fake(), middle = new Fake(), leaf = new Fake();
        root.child = middle; middle.child = leaf; leaf.fail = true;
        root.tick(null, new Chain());
        assertEquals("Fake: blocked", root.failureReason());
        assertEquals(1, leaf.stops);
        assertEquals(1, middle.stops);
    }
    @Test void handledFailureDetachesChildBeforeRetry() {
        Fake root = new Fake(), leaf = new Fake();
        root.handle = true; root.child = leaf; leaf.fail = true;
        Chain chain = new Chain();
        root.tick(null, chain);
        assertNull(root.failureReason());
        assertEquals(1, root.handled);
        assertEquals(1, leaf.stops);
        Fake replacement = new Fake(); root.child = replacement;
        root.tick(null, chain);
        assertTrue(replacement.isActive());
    }

    @Test void failureWaitsForForcedChildBeforeCleanup() {
        Fake root = new Fake(); Forced leaf = new Forced();
        root.child = leaf; leaf.fail = true;
        Chain chain = new Chain();
        root.tick(null, chain);
        assertNull(root.failureReason());
        assertEquals(0, leaf.stops);
        leaf.force = false;
        root.tick(null, chain);
        assertNotNull(root.failureReason());
        assertEquals(1, leaf.stops);
    }
}
