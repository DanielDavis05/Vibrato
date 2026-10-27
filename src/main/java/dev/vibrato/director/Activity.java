package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * One unit of play the {@link PlanSampler} can string together with others: "get 12 iron ingots", "sleep through the
 * night", "trade with a villager", "enchant a pickaxe", ...
 * <p>
 * Planning is purely symbolic ({@link #requires} / {@link #provides} over {@link Fact}s) and must not touch the game.
 * Only {@link #create} builds the real in-game {@link Task}, and only at execution time.
 * <p>
 * Implementations must be stateless and thread-safe: one instance is shared by every bot in the JVM.
 */
public interface Activity {

    /** Unique, stable, snake_case. Persisted in coverage files, so never rename one casually. */
    String id();

    FeatureArea area();

    /** Facts that must hold before this activity can run. Unmet facts are filled by inserting provider activities. */
    default Set<Fact> requires() {
        return Set.of();
    }

    /** Facts that hold afterwards. */
    default Set<Fact> provides() {
        return Set.of();
    }

    /** Parameters sampled by the planner; the product of their variant counts is this activity's variant count. */
    default List<ParamSpec> params() {
        return List.of();
    }

    /** Base selection weight for a bot with this persona (before coverage boosting). Must be positive. */
    default double weight(Persona persona) {
        return persona.weightFor(area());
    }

    /** Builds the in-game task for the sampled parameters. */
    Task create(Vibrato mod, ParamValues values);

    /** Hard wall-clock cap; the director abandons the step when it is exceeded. */
    Duration budget(ParamValues values);
}
