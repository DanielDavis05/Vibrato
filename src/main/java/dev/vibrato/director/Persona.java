package dev.vibrato.director;

import java.util.Map;

/**
 * A per-bot bias over {@link FeatureArea}s, so bots with different personas diverge even before their random seeds do.
 * A missing area has weight 1.0; weights must be positive.
 */
public record Persona(String name, Map<FeatureArea, Double> bias) {

    /** No preferences at all. */
    public static final Persona GENERALIST = new Persona("generalist", Map.of());

    public Persona {
        bias = Map.copyOf(bias);
        for (Map.Entry<FeatureArea, Double> entry : bias.entrySet()) {
            if (!(entry.getValue() > 0)) {
                throw new IllegalArgumentException("Persona " + name + " weight for " + entry.getKey() + " must be > 0");
            }
        }
    }

    public double weightFor(FeatureArea area) {
        return bias.getOrDefault(area, 1.0);
    }
}
