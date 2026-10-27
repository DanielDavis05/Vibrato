package dev.vibrato.director;

import java.util.Objects;
import java.util.stream.Collectors;

/** One chosen activity with its sampled parameters, as it will run inside a {@link Plan}. */
public record PlannedStep(Activity activity, ParamValues params) {

    public PlannedStep {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(params, "params");
    }

    /** {@code id{k=v,...}} with parameters in name order. */
    public String signature() {
        return activity.id() + params.asMap().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
