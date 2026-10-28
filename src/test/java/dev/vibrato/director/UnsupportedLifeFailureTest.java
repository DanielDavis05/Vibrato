package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.life.UnsupportedLifeTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.tasksystem.TaskRunner;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class UnsupportedLifeFailureTest {
    private static final class Chain extends TaskChain {
        Chain() { super(new TaskRunner(null)); }
        @Override protected void onStop(Vibrato mod) {}
        @Override public void onInterrupt(Vibrato mod, TaskChain other) {}
        @Override protected void onTick(Vibrato mod) {}
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "failure test"; }
    }
    private static final class Director extends DirectorTask {
        Director() { super(new ActivityRegistry(), 1, Personas.GENERALIST, 1, null); }
        @Override protected void onStart(Vibrato mod) {}
    }
    private static void set(Object instance, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
    @Test void unsupportedChildFailsDirectorStepWithoutSuccessFactsOrCoverage() throws Exception {
        UnsupportedLifeTask child = new UnsupportedLifeTask("farm bad params");
        assertTrue(child.failed());
        assertFalse(child.isFinished(null));
        assertTrue(child.failureReason().contains("farm bad params"));
        Fact provided = Fact.of("test:should_not_be_learned");
        Activity activity = new Activity() {
            @Override public String id() { return "unsupported_fixture"; }
            @Override public FeatureArea area() { return FeatureArea.FARM; }
            @Override public Set<Fact> provides() { return Set.of(provided); }
            @Override public Task create(Vibrato mod, ParamValues params) { return child; }
            @Override public Duration budget(ParamValues params) { return Duration.ofSeconds(1); }
        };
        Director parent = new Director();
        CoverageTracker coverage = new CoverageTracker();
        set(parent, DirectorTask.class, "_coverage", coverage);
        set(parent, DirectorTask.class, "_currentStep", new PlannedStep(activity, ParamValues.EMPTY));
        set(parent, DirectorTask.class, "_stepTask", child);
        set(parent, Task.class, "_sub", child);
        parent.tick(null, new Chain());
        assertNull(parent.failureReason()); // Director handled the failure.
        assertEquals("none", parent.currentStepId());
        assertEquals(0, coverage.uses(activity.id()));
        assertTrue(coverage.isOnCooldown(activity.id()));
        Field facts = DirectorTask.class.getDeclaredField("_facts"); facts.setAccessible(true);
        assertFalse(((Set<?>) facts.get(parent)).contains(provided));
    }
}
