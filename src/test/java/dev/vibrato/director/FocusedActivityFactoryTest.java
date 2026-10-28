package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.travel.BoatTravelTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FocusedActivityFactoryTest {
    private static final Task WALKING = new Task() {
        @Override protected void onStart(Vibrato mod) {}
        @Override protected Task onTick(Vibrato mod) { return null; }
        @Override protected void onStop(Vibrato mod, Task task) {}
        @Override protected boolean isEqual(Task task) { return task == this; }
        @Override protected String toDebugString() { return "walking alternative"; }
    };
    private static Activity activity(String id, AtomicInteger calls) {
        return new Activity() {
            @Override public String id() { return id; }
            @Override public FeatureArea area() { return FeatureArea.EXPLORE; }
            @Override public Task create(Vibrato mod, ParamValues params) { calls.incrementAndGet(); return WALKING; }
            @Override public Duration budget(ParamValues params) { return Duration.ofMinutes(5); }
        };
    }
    @Test void focusedBoatGoalCreatesARealBoatTaskWithoutCallingTheWalkingAlternative() {
        AtomicInteger calls = new AtomicInteger();
        Task task = FocusedActivityFactory.create(activity("travel_boat_cross", calls), ParamValues.EMPTY, true, null,
                () -> new BlockPos(40, 64, 0));
        assertInstanceOf(BoatTravelTask.class, task); assertEquals(0, calls.get());
    }
    @Test void providerStepsAndOtherGoalsPreserveTheirOriginalActivityFactories() {
        AtomicInteger calls = new AtomicInteger();
        assertSame(WALKING, FocusedActivityFactory.create(activity("travel_boat_cross", calls), ParamValues.EMPTY, false, null,
                () -> { throw new AssertionError("focused target requested for provider"); }));
        assertSame(WALKING, FocusedActivityFactory.create(activity("organize_inventory", calls), ParamValues.EMPTY, true, null,
                () -> { throw new AssertionError("boat target requested for another goal"); }));
        assertEquals(2, calls.get());
    }
}
