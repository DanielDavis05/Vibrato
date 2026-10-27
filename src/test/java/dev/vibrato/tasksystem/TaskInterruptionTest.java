package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.resources.CollectRecipeCataloguedResourcesTask;
import dev.vibrato.util.RecipeTarget;
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
        boolean craftingOwnsGrid() { return hasActiveCraftingGridUser(); }

        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task onTick(Vibrato mod) { ticks++; return next; }
        @Override protected void onStop(Vibrato mod, Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "fake"; }
    }

    private static final class ObjectiveTask extends FakeTask {
        final String objective;
        ObjectiveTask(String objective) { this.objective = objective; }
        @Override protected boolean isEqual(Task other) {
            return other instanceof ObjectiveTask task && objective.equals(task.objective);
        }
        @Override protected String toDebugString() { return objective; }
    }

    private static final class GridTask extends FakeTask implements ITaskUsesCraftingGrid {}

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
    void activeNestedCraftingKeepsGridOwnershipUntilStopped() {
        TestChain chain = new TestChain();
        FakeTask gatherLogs = new FakeTask();
        FakeTask acquireAxe = new FakeTask();
        GridTask craftPlanks = new GridTask();
        gatherLogs.next = acquireAxe;
        acquireAxe.next = craftPlanks;
        assertFalse(gatherLogs.craftingOwnsGrid());
        gatherLogs.tick(null, chain);
        assertTrue(gatherLogs.craftingOwnsGrid());
        gatherLogs.tick(null, chain);
        assertEquals(2, craftPlanks.ticks);
        acquireAxe.next = null;
        gatherLogs.tick(null, chain);
        assertEquals(1, craftPlanks.stops);
        assertFalse(gatherLogs.craftingOwnsGrid());
        acquireAxe.next = craftPlanks;
        gatherLogs.tick(null, chain);
        assertTrue(gatherLogs.craftingOwnsGrid());
        gatherLogs.stop(null);
        // Stopped children remain attached for possible reuse, but do not own the grid.
        assertFalse(gatherLogs.craftingOwnsGrid());
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
    @Test
    void repeatedSemanticObjectiveAndInstanceCycleAreRejectedWithoutAttaching() {
        TestChain chain = new TestChain();
        ObjectiveTask table = new ObjectiveTask("craft wooden axe");
        ObjectiveTask ingredients = new ObjectiveTask("collect axe ingredients");
        ObjectiveTask sticks = new ObjectiveTask("collect sticks");
        ObjectiveTask bush = new ObjectiveTask("mine dead bush");
        ObjectiveTask repeatedTable = new ObjectiveTask("craft wooden axe");
        table.next = ingredients;
        ingredients.next = sticks;
        sticks.next = bush;
        bush.next = repeatedTable;
        table.tick(null, chain);
        assertEquals(4, chain.getTasks().size());
        assertEquals(0, repeatedTable.ticks);
        bush.next = table;
        table.tick(null, chain);
        assertEquals(0, repeatedTable.ticks);
        table.stop(null);
        assertEquals(1, table.stops);
        assertEquals(1, bush.stops);
    }

    @Test
    void distinctNestedObjectivesCanBootstrapWoodenAxeAndReleaseProtectedChild() {
        TestChain chain = new TestChain();
        ObjectiveTask logs = new ObjectiveTask("bulk logs");
        ObjectiveTask axe = new ObjectiveTask("wooden axe");
        ObjectiveTask planks = new ObjectiveTask("planks for axe");
        ObjectiveTask bootstrapLog = new ObjectiveTask("one log for planks");
        ForcedTask child = new ForcedTask();
        logs.next = axe;
        axe.next = planks;
        planks.next = bootstrapLog;
        bootstrapLog.next = child;
        logs.tick(null, chain);
        assertEquals(1, bootstrapLog.ticks);
        axe.next = null;
        logs.tick(null, chain);
        assertEquals(2, child.ticks);
        child.force = false;
        logs.tick(null, chain);
        assertEquals(1, child.stops);
    }

    @Test
    void uniqueRecursiveObjectivesAreBoundedAndCanBeStopped() {
        TestChain chain = new TestChain();
        class GrowingTask extends FakeTask {
            @Override protected Task onTick(Vibrato mod) { ticks++; return new GrowingTask(); }
        }
        GrowingTask root = new GrowingTask();
        root.tick(null, chain);
        assertEquals(128, chain.getTasks().size());
        root.stop(null);
        assertEquals(1, root.stops);
    }

    @Test
    void realRecipeCollectorRejectsReentryButAllowsOtherRecipe() {
        TestChain chain = new TestChain();
        RecipeTarget axe = new RecipeTarget(null, 1, null);
        RecipeTarget planks = new RecipeTarget(null, 4, null);
        Task collectingAxe = new CollectRecipeCataloguedResourcesTask(false, axe);
        chain.enter(collectingAxe);
        try {
            assertFalse(chain.canEnter(new CollectRecipeCataloguedResourcesTask(false, axe)));
            assertTrue(chain.canEnter(new CollectRecipeCataloguedResourcesTask(false, planks)));
        } finally {
            chain.leave();
        }
        assertTrue(chain.canEnter(collectingAxe));
    }

}
