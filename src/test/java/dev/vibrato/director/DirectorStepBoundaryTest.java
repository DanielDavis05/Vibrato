package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.ITaskCanForce;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.tasksystem.TaskRunner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class DirectorStepBoundaryTest {
    private static final class Chain extends TaskChain {
        Chain() { super(new TaskRunner(null)); }
        @Override protected void onStop(Vibrato mod) {}
        @Override public void onInterrupt(Vibrato mod, TaskChain other) {}
        @Override protected void onTick(Vibrato mod) {}
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "boundary test"; }
    }

    private static class EqualStep extends Task {
        Task child;
        int ticks;
        int stops;
        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task onTick(Vibrato mod) { ticks++; return child; }
        @Override protected void onStop(Vibrato mod, Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return other instanceof EqualStep; }
        @Override protected String toDebugString() { return "equal step"; }
    }

    private static final class Forced extends EqualStep implements ITaskCanForce {
        boolean force = true;
        @Override public boolean shouldForce(Vibrato mod, Task candidate) { return force; }
    }

    private static final class Director extends DirectorTask {
        final Task next;
        int creations;
        Director(Task next) { super(new ActivityRegistry(), 1, Personas.GENERALIST, 1, null); this.next = next; }
        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task nextStepTask(Vibrato mod) { creations++; return next; }
    }

    /** Set up the post-completion state: the director cleared its step, but Task still owns the child. */
    private static void attach(Task parent, Task child) throws Exception {
        Field field = Task.class.getDeclaredField("_sub");
        field.setAccessible(true);
        field.set(parent, child);
    }

    @Test
    void equivalentNewStepStartsOnlyAfterOldInstanceStops() throws Exception {
        Chain chain = new Chain();
        EqualStep old = new EqualStep();
        EqualStep next = new EqualStep();
        Director director = new Director(next);
        old.tick(null, chain);
        attach(director, old);

        director.tick(null, chain);
        assertEquals(1, old.stops);
        assertEquals(0, director.creations);
        director.tick(null, chain);
        assertEquals(1, director.creations);
        assertEquals(1, next.ticks);
        assertEquals(1, old.ticks);
    }

    @Test
    void protectedNestedMovementRunsUntilItCanBeReleased() throws Exception {
        Chain chain = new Chain();
        EqualStep old = new EqualStep();
        Forced forced = new Forced();
        old.child = forced;
        EqualStep next = new EqualStep();
        Director director = new Director(next);
        old.tick(null, chain);
        attach(director, old);

        director.tick(null, chain);
        director.tick(null, chain);
        assertEquals(3, forced.ticks);
        assertEquals(0, old.stops);
        assertEquals(0, director.creations);
        forced.force = false;
        director.tick(null, chain);
        assertEquals(1, old.stops);
        assertEquals(1, forced.stops);
        assertEquals(0, director.creations);
        director.tick(null, chain);
        assertEquals(1, director.creations);
        assertEquals(1, next.ticks);
    }
}
