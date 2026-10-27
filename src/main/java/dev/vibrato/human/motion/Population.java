package dev.vibrato.human.motion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Population-level spread of human traits. Each trait is a normal distribution (mean, spread) clamped to the trait's
 * physical range. A {@link HumanProfile} is one draw from this population, so every bot gets its own habits.
 * <p>
 * {@link #load()} reads {@code /human_profile.json} when it is on the classpath, so fitted values can replace the
 * defaults without a rebuild. Missing traits keep their defaults.
 */
public record Population(Map<Trait, Double> means, Map<Trait, Double> spreads) {

    public static final String RESOURCE = "/human_profile.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * One trait: JSON key, default mean, default spread, and the range a sampled value is clamped to.
     */
    public enum Trait {
        REACTION_MEDIAN_TICKS("reactionMedianTicks", 5, 1, 1, 20),
        JUMP_TIMING_BIAS_TICKS("jumpTimingBiasTicks", 0, 0.8, -4, 4),
        JUMP_TIMING_SIGMA_TICKS("jumpTimingSigmaTicks", 1, 0.4, 0, 5),
        JUMP_MISS_CHANCE("jumpMissChance", 0.02, 0.01, 0, 0.5),
        STRAFE_TAP_RATE("strafeTapRate", 0.04, 0.01, 0, 0.5),
        KEY_HOLD_MEDIAN_TICKS("keyHoldMedianTicks", 6, 1.5, 1, 40),
        KEY_HOLD_SIGMA("keyHoldSigma", 0.3, 0.08, 0, 1.5),
        HESITATION_RATE("hesitationRate", 0.002, 0.001, 0, 0.05),
        LATERAL_SIGMA("lateralSigma", 0.03, 0.01, 0, 0.5),
        LATERAL_REVERSION("lateralReversion", 0.1, 0.03, 0.001, 1),
        LOOK_AHEAD_BLOCKS("lookAheadBlocks", 3, 0.8, 0.5, 10),
        PRIMARY_FRACTION("primaryFraction", 0.92, 0.02, 0.85, 0.99),
        CORRECTION_COUNT("correctionCountMean", 1.2, 0.6, 0, 3),
        OVERSHOOT_SCALE("overshootScale", 1, 0.2, 0, 3),
        TURN_PEAK_SPEED_SCALE("turnPeakSpeedScale", 1, 0.15, 0.5, 1.5);

        private final String jsonKey;
        private final double defaultMean;
        private final double defaultSpread;
        private final double min;
        private final double max;

        Trait(String jsonKey, double defaultMean, double defaultSpread, double min, double max) {
            this.jsonKey = jsonKey;
            this.defaultMean = defaultMean;
            this.defaultSpread = defaultSpread;
            this.min = min;
            this.max = max;
        }

        public String jsonKey() {
            return jsonKey;
        }

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }

        double clamp(double value) {
            return Math.max(min, Math.min(max, value));
        }

        static Trait fromJsonKey(String key) {
            for (Trait trait : values()) {
                if (trait.jsonKey.equals(key)) {
                    return trait;
                }
            }
            throw new IllegalArgumentException("unknown human trait: " + key);
        }
    }

    public static final Population DEFAULT = defaults();

    public Population {
        means = complete(means, "mean");
        spreads = complete(spreads, "spread");
        for (Trait trait : Trait.values()) {
            double mean = means.get(trait);
            if (mean < trait.min || mean > trait.max) {
                throw new IllegalArgumentException(trait.jsonKey + " mean " + mean + " outside " + trait.min + ".." + trait.max);
            }
            if (!(spreads.get(trait) >= 0)) {
                throw new IllegalArgumentException(trait.jsonKey + " spread must be >= 0");
            }
        }
    }

    /**
     * The baked-in defaults, used when no profile resource is present.
     */
    public static Population defaults() {
        Map<Trait, Double> means = new EnumMap<>(Trait.class);
        Map<Trait, Double> spreads = new EnumMap<>(Trait.class);
        for (Trait trait : Trait.values()) {
            means.put(trait, trait.defaultMean);
            spreads.put(trait, trait.defaultSpread);
        }
        return new Population(means, spreads);
    }

    /**
     * Loads {@value #RESOURCE} from the classpath, or {@link #DEFAULT} when the resource is absent.
     */
    public static Population load() {
        try (InputStream in = Population.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return DEFAULT;
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parses {@code {"traits": {"<jsonKey>": {"mean": x, "spread": y}}}}. Traits and fields left out keep the defaults.
     * An unknown trait name is an error, so a typo does not silently fall back.
     */
    public static Population parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("bad human profile JSON", e);
        }
        Map<Trait, Double> means = new EnumMap<>(Trait.class);
        Map<Trait, Double> spreads = new EnumMap<>(Trait.class);
        for (Trait trait : Trait.values()) {
            means.put(trait, trait.defaultMean);
            spreads.put(trait, trait.defaultSpread);
        }
        Iterator<Map.Entry<String, JsonNode>> traits = root.path("traits").fields();
        while (traits.hasNext()) {
            Map.Entry<String, JsonNode> entry = traits.next();
            Trait trait = Trait.fromJsonKey(entry.getKey());
            JsonNode body = entry.getValue();
            if (body.has("mean")) {
                means.put(trait, number(body.get("mean"), trait, "mean"));
            }
            if (body.has("spread")) {
                spreads.put(trait, number(body.get("spread"), trait, "spread"));
            }
        }
        return new Population(means, spreads);
    }

    /**
     * One value of {@code trait} drawn from the population, clamped to the trait's range.
     */
    public double draw(Trait trait, RandomGenerator rng) {
        return trait.clamp(means.get(trait) + spreads.get(trait) * rng.nextGaussian());
    }

    public double mean(Trait trait) {
        return means.get(trait);
    }

    public double spread(Trait trait) {
        return spreads.get(trait);
    }

    private static double number(JsonNode node, Trait trait, String field) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException(trait.jsonKey + "." + field + " must be a number");
        }
        return node.doubleValue();
    }

    private static Map<Trait, Double> complete(Map<Trait, Double> source, String what) {
        Map<Trait, Double> copy = new EnumMap<>(Trait.class);
        for (Trait trait : Trait.values()) {
            Double value = source.get(trait);
            if (value == null) {
                throw new IllegalArgumentException("missing " + what + " for " + trait.jsonKey);
            }
            copy.put(trait, value);
        }
        return Collections.unmodifiableMap(copy);
    }
}
