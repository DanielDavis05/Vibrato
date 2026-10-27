package dev.vibrato.director;

import java.util.List;
import java.util.stream.Collectors;

/** An immutable, ordered list of steps. Provider steps inserted for prerequisites are part of the list. */
public record Plan(List<PlannedStep> steps) {

    public Plan {
        steps = List.copyOf(steps);
    }

    /** Stable text form of the whole plan, {@code id{k=v,...}>id{...}}, used to compare plans. */
    public String signature() {
        return steps.stream().map(PlannedStep::signature).collect(Collectors.joining(">"));
    }
}
