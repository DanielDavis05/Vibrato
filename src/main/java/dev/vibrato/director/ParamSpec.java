package dev.vibrato.director;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Describes one sampled parameter of an {@link Activity}. The number of {@link #variants()} multiplies into the size
 * of the plan space (see {@code PlanSpace}), so a wide, meaningful domain is worth more than many near-identical
 * activities.
 */
public sealed interface ParamSpec {

    String name();

    /** How many distinct values this parameter can take; always at least 1. */
    long variants();

    /** Draws one value: a {@link String} for {@link Choice}, an {@link Integer} for {@link IntRange}. */
    Object sample(RandomGenerator rng);

    static ParamSpec choice(String name, String... values) {
        return new Choice(name, List.of(values));
    }

    /** Inclusive on both ends. */
    static ParamSpec ints(String name, int min, int max) {
        return new IntRange(name, min, max);
    }

    record Choice(String name, List<String> values) implements ParamSpec {
        public Choice {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Choice " + name + " needs at least one value");
            }
            values = List.copyOf(values);
        }

        @Override
        public long variants() {
            return values.size();
        }

        @Override
        public Object sample(RandomGenerator rng) {
            return values.get(rng.nextInt(values.size()));
        }
    }

    record IntRange(String name, int min, int max) implements ParamSpec {
        public IntRange {
            if (max < min) {
                throw new IllegalArgumentException("IntRange " + name + " has max < min");
            }
        }

        @Override
        public long variants() {
            return (long) max - min + 1;
        }

        @Override
        public Object sample(RandomGenerator rng) {
            return min + rng.nextInt(max - min + 1);
        }
    }
}
