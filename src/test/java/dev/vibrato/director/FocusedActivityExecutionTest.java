package dev.vibrato.director;

import com.google.gson.JsonParser;
import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FocusedActivityExecutionTest {
    @TempDir Path directory;
    private static final class Chain extends TaskChain {
        Chain() { super(new TaskRunner(null)); }
        @Override protected void onStop(Vibrato mod) {}
        @Override public void onInterrupt(Vibrato mod, TaskChain other) {}
        @Override protected void onTick(Vibrato mod) {}
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "focused test"; }
    }
    private static final class Child extends Task {
        int ticks; boolean broken; boolean finishes = true;
        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task onTick(Vibrato mod) { ticks++; if (broken) fail("dependency failed"); return null; }
        @Override protected void onStop(Vibrato mod, Task task) {}
        @Override public boolean isFinished(Vibrato mod) { return finishes && ticks > 0; }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "focused child"; }
    }
    private static Activity activity(String id, Set<Fact> requires, Set<Fact> provides, Child task) {
        return new Activity() {
            @Override public String id() { return id; }
            @Override public FeatureArea area() { return FeatureArea.INVENTORY; }
            @Override public Set<Fact> requires() { return requires; }
            @Override public Set<Fact> provides() { return provides; }
            @Override public Task create(Vibrato mod, ParamValues values) { return task; }
            @Override public Duration budget(ParamValues values) { return Duration.ofSeconds(2); }
        };
    }
    private final class Focused extends FocusedActivityTask {
        final AtomicLong clock;
        FocusedActivityProof.Inventory items = new FocusedActivityProof.Inventory(Map.of(), List.of("empty"), Map.of(), Map.of());
        Map<String, Long> packets = Map.of();
        Focused(ActivityRegistry registry) { this(registry, new AtomicLong(1_000_000_000L)); }
        Focused(ActivityRegistry registry, AtomicLong clock) { super(registry, "organize_inventory", 7, Personas.GENERALIST,
                directory.resolve("activity-result.json"), clock::get); this.clock = clock; }
        @Override protected boolean packetEvidenceEnabled() { return true; }
        @Override protected double clientSeconds(Vibrato mod) { return 1; }
        @Override protected boolean playerAlive(Vibrato mod) { return true; }
        @Override protected Set<Fact> observeFacts(Vibrato mod) { return Set.of(); }
        @Override protected FocusedActivityProof.Inventory inventory(Vibrato mod) { return items; }
        @Override protected void collectProgress(Vibrato mod) {}
        @Override protected Map<String, Long> packetCounts() { return packets; }
    }
    @Test void childFailureFailsWholeGoalAndNeverSkipsToRequestedMechanic() throws Exception {
        ActivityRegistry registry = new ActivityRegistry(); Child provider = new Child(); provider.broken = true; Child goal = new Child();
        registry.register(activity("provider", Set.of(), Set.of(Fact.of("ready")), provider));
        registry.register(activity("organize_inventory", Set.of(Fact.of("ready")), Set.of(), goal));
        Focused task = new Focused(registry); Chain chain = new Chain(); task.tick(null, chain);
        assertNotNull(task.failureReason()); assertFalse(task.isFinished(null)); assertEquals(0, goal.ticks);
        var report = JsonParser.parseString(Files.readString(directory.resolve("activity-result.json"))).getAsJsonObject();
        assertEquals("failed", report.get("status").getAsString());
        assertTrue(report.get("failureReason").getAsString().contains("dependency failed"));
    }
    @Test void completionRequiresNewGoalScopedActionAndOutcome() throws Exception {
        ActivityRegistry registry = new ActivityRegistry(); Child goal = new Child();
        registry.register(activity("organize_inventory", Set.of(), Set.of(), goal));
        Focused task = new Focused(registry); Chain chain = new Chain(); task.tick(null, chain);
        assertFalse(task.isFinished(null));
        task.items = new FocusedActivityProof.Inventory(Map.of("dirt", 1), List.of("dirt"), Map.of(), Map.of());
        task.packets = Map.of("SERVERBOUND:minecraft:container_click", 1L, "CLIENTBOUND:minecraft:container_set_slot", 1L);
        task.tick(null, chain); task.tick(null, chain);
        assertTrue(task.isFinished(null)); assertNull(task.failureReason());
        var report = JsonParser.parseString(Files.readString(directory.resolve("activity-result.json"))).getAsJsonObject();
        assertEquals("completed", report.get("status").getAsString());
        assertTrue(report.getAsJsonObject("evidence").get("actionObserved").getAsBoolean());
        assertEquals(1, goal.ticks);
    }
    @Test void cancellingBeforeProofCannotBecomeSuccessful() {
        ActivityRegistry registry = new ActivityRegistry();
        registry.register(activity("organize_inventory", Set.of(), Set.of(), new Child()));
        Focused task = new Focused(registry); task.tick(null, new Chain()); task.stop(null);
        assertFalse(task.isFinished(null)); assertTrue(task.failureReason().contains("cancelled"));
    }
    @Test void finishedNoOpFailsAfterBoundedProofWindow() throws Exception {
        ActivityRegistry registry = new ActivityRegistry();
        registry.register(activity("organize_inventory", Set.of(), Set.of(), new Child()));
        Focused task = new Focused(registry); Chain chain = new Chain();
        task.tick(null, chain); task.tick(null, chain);
        task.clock.addAndGet(6_000_000_000L); task.tick(null, chain);
        assertFalse(task.isFinished(null)); assertTrue(task.failureReason().contains("without sufficient"));
        var report = JsonParser.parseString(Files.readString(directory.resolve("activity-result.json"))).getAsJsonObject();
        assertEquals("failed", report.get("status").getAsString());
    }
    @Test void unfinishedChildCannotRunBeyondTheActivityBudget() {
        ActivityRegistry registry = new ActivityRegistry(); Child child = new Child(); child.finishes = false;
        registry.register(activity("organize_inventory", Set.of(), Set.of(), child));
        Focused task = new Focused(registry); Chain chain = new Chain(); task.tick(null, chain);
        task.clock.addAndGet(3_000_000_000L); task.tick(null, chain);
        assertFalse(task.isFinished(null)); assertTrue(task.failureReason().contains("budget exceeded"));
    }
}
