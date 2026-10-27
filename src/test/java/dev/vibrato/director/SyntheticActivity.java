package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Planner-only activity for tests: never builds a task, so no Minecraft code is touched. */
record SyntheticActivity(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides, List<ParamSpec> params)
        implements Activity {

    static SyntheticActivity of(String id, FeatureArea area) {
        return new SyntheticActivity(id, area, Set.of(), Set.of(), List.of());
    }

    static SyntheticActivity of(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides) {
        return new SyntheticActivity(id, area, requires, provides, List.of());
    }

    @Override
    public Task create(Vibrato mod, ParamValues values) {
        return null;
    }

    @Override
    public Duration budget(ParamValues values) {
        return Duration.ofMinutes(1);
    }
}
